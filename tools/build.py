import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build" / "standalone"
DEPENDENCIES = {
    "forge": ("https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-universal.jar", "bd8314729d787e35164476d75afb337afb9783917e4f6fa1f77f049adba22f2d"),
    "guava": ("https://repo.maven.apache.org/maven2/com/google/guava/guava/21.0/guava-21.0.jar", "972139718abc8a4893fa78cba8cf7b2c903f35c97aaf44fa3031b0669948b480"),
    "netty": ("https://repo.maven.apache.org/maven2/io/netty/netty-all/4.1.9.Final/netty-all-4.1.9.Final.jar", "fd5ad66cdd0a496d281b26c699f4511da7c3f95083659ebb7bae22dab876581a"),
    "asm": ("https://repo.maven.apache.org/maven2/org/ow2/asm/asm-debug-all/5.2/asm-debug-all-5.2.jar", "254b82bec9da4f8efbc8b1f93ab2b87f7465227a82b36cf3d05d9e77a0e8dd2e"),
    "launchwrapper": ("https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar", "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f"),
    "log4j-api": ("https://repo.maven.apache.org/maven2/org/apache/logging/log4j/log4j-api/2.8.1/log4j-api-2.8.1.jar", "1205ab764b1326f7d96d99baa4a4e12614599bf3d735790947748ee116511fa2"),
    "junit": ("https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.7.1/junit-platform-console-standalone-1.7.1.jar", "e588d4dab5c8898b241c2a25938bf0b933ab77518626f14028cce403df7ce33d"),
    "minecraft": ("https://piston-data.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar", "8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f"),
    "srg": ("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.12.2/mcp-1.12.2-srg.zip", "bea27218818448851594889bf7d984df614ffc698bbe41599dd6237d627e3782"),
    "stable": ("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/39-1.12/mcp_stable-39-1.12.zip", "13a31f28c11f8f395ffe7e8563ade459f5a0ee46493abbbde3ce6e9493ac4152"),
    "toroquest": ("https://curse.cleanroommc.com/curse/maven/toroquest-revamped-382059/4085781/toroquest-revamped-382059-4085781.jar", "46170074021b879db92ae31c1b77affa24e3bb8aa2a854df05b98c3aef5a847a"),
    "witchery-villages": ("https://curse.cleanroommc.com/curse/maven/witchery-villages-1545448/8765330/witchery-villages-1545448-8765330.jar", "bb59f6a32726fdc8881d15be98654b23bdbedf4d9a70e3c01bffc9a151102eab"),
    "cooking-for-blockheads": ("https://curse.cleanroommc.com/curse/maven/cooking-for-blockheads-231484/2862651/cooking-for-blockheads-231484-2862651.jar", "3a8ed073a9d84213d6a1f2947bf9e8a4e70730fa123e3ca53769fdc99ef4db9b"),
}
MOD_DEPENDENCIES = ("toroquest", "witchery-villages", "cooking-for-blockheads")
MODERN_ASM = {
    "asm": "8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281",
    "asm-tree": "9929881f59eb6b840e86d54570c77b59ce721d104e6dfd7a40978991c2d3b41f",
    "asm-util": "f885be71b5c90556f5f1ad1c4f9276b29b96057c497d46666fe4ddbec3cb43c6",
    "asm-analysis": "85b29371884ba31bb76edf22323c2c24e172c3267a67152eba3d1ccc2e041ef2",
    "asm-commons": "9a579b54d292ad9be171d4313fd4739c635592c2b5ac3a459bbd1049cddec6a0",
}


def download(name):
    url, checksum = DEPENDENCIES[name]
    path = BUILD / "dependencies" / (name + ".jar")
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
        request = urllib.request.Request(url, headers={"User-Agent": "EternalConfluenceTweaks-build/0.4.6"})
        for attempt in range(1, 4):
            print(f"Downloading {name} (attempt {attempt}/3): {url}", flush=True)
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read()
                break
            except (urllib.error.URLError, TimeoutError, ConnectionError, http.client.IncompleteRead) as error:
                retryable = not isinstance(error, urllib.error.HTTPError) or error.code in (
                    404, 408, 429, 500, 502, 503, 504)
                if not retryable or attempt == 3:
                    raise RuntimeError(
                        f"Failed to download dependency {name} from {url} (attempt {attempt}/3): {error}"
                    ) from error
                delay = 2 ** attempt
                print(f"Download failed for {name}: {error}; retrying in {delay} seconds.", flush=True)
                time.sleep(delay)
        if hashlib.sha256(data).hexdigest() != checksum:
            raise RuntimeError(f"Dependency checksum mismatch: {name} from {url}")
        path.write_bytes(data)
    return path


def compile_java(java, sources, destination, dependencies):
    destination.mkdir(parents=True, exist_ok=True)
    subprocess.run([java, "-m", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8",
                    "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, dependencies)),
                    "-d", str(destination)] + list(map(str, sources)), check=True, cwd=ROOT)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--astral-jar", type=Path, default=ROOT / "dev-libs/mods/astralsorcery-1.12.2-1.10.27.jar")
    parser.add_argument("--original-mods", type=Path)
    parser.add_argument("--patched-mods", type=Path)
    parser.add_argument("--ghostly-mods", type=Path)
    parser.add_argument("--waystones-jar", type=Path)
    parser.add_argument("--nutrition-jar", type=Path, default=ROOT / "dev-libs/Nutrition-UEL-4.16.0.jar")
    parser.add_argument("--spiceoflife-jar", type=Path, default=ROOT / "dev-libs/SpiceOfLife-mc1.12-1.3.12.jar")
    parser.add_argument("--scalingfeast-jar", type=Path, default=ROOT / "dev-libs/scalingfeast-1.10.4.jar")
    parser.add_argument("--vfp-jar", type=Path, default=ROOT / "dev-libs/vanillafoodpantry-mc1.12.2-4.3.1.jar")
    parser.add_argument("--applecore-jar", type=Path, default=ROOT / "dev-libs/AppleCore-mc1.12.2-3.4.0.jar")
    parser.add_argument("--solcarrot-jar", type=Path, default=ROOT / "dev-libs/solcarrot-1.12.2-1.8.4.jar")
    args = parser.parse_args()
    if bool(args.original_mods) != bool(args.patched_mods):
        parser.error("Provide both --original-mods and --patched-mods for fixture tests")
    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if "JAVA_HOME" in os.environ else shutil.which("java")
    if not java:
        parser.error("A JDK 17 or newer is required")
    properties = dict(re.findall(r"^[ \t]*([\w.]+)[ \t]*=[ \t]*(.*?)[ \t]*$", (ROOT / "gradle.properties").read_text(), re.M))
    dependencies = {name: download(name) for name in DEPENDENCIES}
    mapping_tools = BUILD / "mapping-tools"
    compile_java(java, [ROOT / "tools/BuildMappings.java"], mapping_tools, [dependencies["asm"]])

    def remap(mode, source, target, hierarchy):
        temporary = target.with_suffix(".tmp.jar")
        try:
            subprocess.run([java, "-Xmx512m", "-cp", os.pathsep.join(map(str, [mapping_tools, dependencies["asm"]])),
                            "BuildMappings", mode, str(dependencies["srg"]), str(dependencies["stable"]),
                            str(source), str(temporary)] + list(map(str, hierarchy)), check=True, cwd=ROOT)
            with zipfile.ZipFile(temporary) as mapped:
                if mapped.testzip() is not None:
                    raise RuntimeError("Invalid mapped archive: " + str(target))
            os.replace(temporary, target)
        finally:
            temporary.unlink(missing_ok=True)

    def archive(directory, target):
        with zipfile.ZipFile(target, "w") as output:
            for path in sorted(directory.rglob("*.class")):
                output.write(path, path.relative_to(directory).as_posix())

    minecraft_mcp = BUILD / "minecraft-mcp.jar"
    forge_mcp = BUILD / "forge-mcp.jar"
    minecraft_srg = BUILD / "minecraft-srg.jar"
    remap("mcp", dependencies["minecraft"], minecraft_mcp, [])
    remap("mcp", dependencies["forge"], forge_mcp, [dependencies["minecraft"]])
    remap("srg", minecraft_mcp, minecraft_srg, [])
    mod_mcp = []
    for name in MOD_DEPENDENCIES:
        target = BUILD / (name + "-mcp.jar")
        remap("mcp", dependencies[name], target, [dependencies["minecraft"], dependencies["forge"]])
        mod_mcp.append(target)
    compile_dependencies = [minecraft_mcp, forge_mcp] + mod_mcp + [p for n, p in dependencies.items() if n not in ("minecraft", "forge", "srg", "stable", *MOD_DEPENDENCIES)]
    if args.ghostly_mods:
        ghostly_mcp = BUILD / "ghostly-mcp"
        ghostly_mcp.mkdir(exist_ok=True)
        for source in sorted(args.ghostly_mods.glob("*.jar")):
            remap("mcp", source, ghostly_mcp / source.name, [dependencies["minecraft"]])
    classes = BUILD / "classes"
    tests = BUILD / "test-classes"
    for directory in (classes, tests):
        if directory.exists():
            shutil.rmtree(directory)
    generated = BUILD / "generated" / "com" / "gberguy" / "ecpatches" / "Tags.java"
    generated.parent.mkdir(parents=True, exist_ok=True)
    generated.write_text("package com.gberguy.ecpatches;\npublic final class Tags {\n" + "".join(
        "    public static final String " + key + " = " + json.dumps(properties[value]) + ";\n"
        for key, value in (("MOD_ID", "mod_id"), ("MOD_NAME", "mod_name"), ("VERSION", "mod_version"))) + "}\n")
    compile_java(java, sorted((ROOT / "src/main/java").rglob("*.java")) + [generated], classes, compile_dependencies)
    for resource in sorted((ROOT / "src/main/resources").rglob("*")):
        if resource.is_file():
            relative = resource.relative_to(ROOT / "src/main/resources")
            target = classes / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            data = resource.read_bytes()
            if resource.name in ("mcmod.info", "pack.mcmeta"):
                data = re.sub(r"\$\{(\w+)\}", lambda m: properties[m[1]], data.decode()).encode()
                json.loads(data)
            target.write_bytes(data)
    test_sources = sorted((ROOT / "src/test/java").rglob("*.java")) + sorted((ROOT / "src/testFixtures/java").rglob("*.java"))
    compile_java(java, test_sources, tests, [classes] + compile_dependencies)
    main_mcp = BUILD / "main-mcp.jar"
    main_srg = BUILD / "main-srg.jar"
    archive(classes, main_mcp)
    remap("srg", main_mcp, main_srg, [minecraft_mcp, forge_mcp] + mod_mcp)
    test_mcp = BUILD / "test-mcp.jar"
    test_srg = BUILD / "test-srg.jar"
    archive(tests, test_mcp)
    remap("srg", test_mcp, test_srg, [minecraft_mcp, forge_mcp] + mod_mcp)
    test_dependencies = [classes, tests] + [path for name, path in dependencies.items() if name not in ("forge", "minecraft", "srg", "stable", *MOD_DEPENDENCIES)]
    command = [java, "--add-opens", "java.base/java.lang=ALL-UNNAMED",
               "-Dorg.apache.logging.log4j.simplelog.StatusLogger.level=OFF",
               "-Dorg.apache.logging.log4j.simplelog.level=OFF"]
    for name, path in (("originalMods", args.original_mods), ("patchedMods", args.patched_mods),
                       ("ghostlyMods", args.ghostly_mods), ("waystonesJar", args.waystones_jar), ("minecraftMcp", minecraft_mcp), ("minecraftSrg", minecraft_srg),
                       ("releaseJar", main_srg), ("releaseTestJar", test_srg), ("ghostlyMcp", ghostly_mcp if args.ghostly_mods else None),
                       ("witcheryWallsJar", dependencies["witchery-villages"]), ("forgeJar", forge_mcp),
                       ("cookingJar", dependencies["cooking-for-blockheads"])):
        if path:
            command.append("-Decpatches." + name + "=" + str(path.resolve()))
    for name, path in (("astralJar", args.astral_jar), ("nutritionJar", args.nutrition_jar), ("spiceOfLifeJar", args.spiceoflife_jar),
                       ("scalingFeastJar", args.scalingfeast_jar), ("vfpJar", args.vfp_jar),
                       ("appleCoreJar", args.applecore_jar), ("solCarrotJar", args.solcarrot_jar)):
        if path.is_file():
            command.append("-Decpatches." + name + "=" + str(path.resolve()))
    modern = []
    for artifact, checksum in MODERN_ASM.items():
        key = artifact + "9"
        DEPENDENCIES[key] = ("https://repo.maven.apache.org/maven2/org/ow2/asm/" + artifact + "/9.7.1/" + artifact + "-9.7.1.jar", checksum)
        modern.append(download(key))
    for label, classpath in (("asm5", test_dependencies),
                             ("asm9", [p for p in test_dependencies if p != dependencies["asm"]] + modern)):
        print("Testing " + label, flush=True)
        subprocess.run(command + ["-cp", os.pathsep.join(map(str, classpath)), "org.junit.platform.console.ConsoleLauncher",
                                  "--scan-class-path", "--disable-banner", "--details=summary", "--reports-dir", str(BUILD / "test-results" / label)],
                       check=True, cwd=ROOT)
    output = ROOT / "build/libs" / (properties["mod_id"] + "-" + properties["mod_version"] + ".jar")
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = ("Manifest-Version: 1.0\r\nFMLCorePlugin: com.gberguy.ecpatches.core.LoadingPlugin\r\n"
                "FMLCorePluginContainsFMLMod: true\r\nForceLoadAsMod: true\r\n"
                "Implementation-Version: " + properties["mod_version"] + "\r\n\r\n")
    entries = {"META-INF/MANIFEST.MF": manifest.encode(), "META-INF/LICENSE": (ROOT / "LICENSE").read_bytes()}
    entries.update({p.relative_to(classes).as_posix(): p.read_bytes() for p in sorted(classes.rglob("*")) if p.is_file()})
    with zipfile.ZipFile(main_srg) as release:
        entries.update({name: release.read(name) for name in release.namelist()})
    for name, data in entries.items():
        if name.endswith(".class") and int.from_bytes(data[6:8], "big") != 52:
            raise RuntimeError("Non-Java-8 class: " + name)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for name, data in entries.items():
            info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            jar.writestr(info, data)
    print(str(output))
    print("SHA-256 " + hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
