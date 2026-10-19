package com.gberguy.ecpatches.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import com.gberguy.ecpatches.core.patches.GhostlyTargetPatch;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PatchFixtureTest {
    @TempDir Path directory;
    private PatchTransformer transformer;

    @BeforeEach
    void setup() {
        assumeTrue(System.getProperty("ecpatches.originalMods") != null, "Original mod fixtures not supplied");
        assumeTrue(System.getProperty("ecpatches.patchedMods") != null, "Bansoukou fixtures not supplied");
        transformer = new PatchTransformer(PatchSettings.read(directory.resolve("patches.cfg").toFile()));
    }

    public static byte[] fixture(MethodPatch patch, String property) throws Exception {
        String jar = patch.id == PatchId.BLOOD_ARSENAL ? "BloodArsenal-1.12.2-2.2.2-31.jar"
                : patch.id == PatchId.ROOTS ? "Roots-1.12.2-3.1.9.2.jar"
                : patch.id == PatchId.MORECHIDS ? "morechids-1.3.0.jar" : "lycanitesmobs-1.12.2-2.0.8.10.jar";
        try (JarFile archive = new JarFile(Paths.get(System.getProperty(property), jar).toFile());
             InputStream input = archive.getInputStream(archive.getJarEntry(patch.className.replace('.', '/') + ".class"))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodNode target(ClassNode node, MethodPatch patch) {
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (MinecraftNames.canonical(method.name).equals(patch.methodName) && method.desc.equals(patch.descriptor)) return method;
        }
        throw new AssertionError("Missing target method");
    }

    private static Map<String, String> otherMethods(ClassNode node, MethodPatch patch) {
        Map<String, String> methods = new LinkedHashMap<>();
        MethodNode target = target(node, patch);
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (method != target) methods.put(method.name + method.desc, MethodFingerprint.of(method));
        }
        return methods;
    }

    @TestFactory
    Stream<DynamicTest> reproducesAllFiveFixesAndPreservesOtherMethods() {
        return PatchTransformer.patches().values().stream().filter(p -> !(p instanceof GhostlyTargetPatch) && p.id != PatchId.ASTRAL_PERK_FORMULA && p.id != PatchId.WAYSTONES_VILLAGE && p.id != PatchId.NUTRITION_LUNCHBOX && p.id != PatchId.SCALING_FEAST_SATURATION_HUD && p.id != PatchId.VFP_FOOD && p.id != PatchId.VFP_FOOD_VALUES && p.id != PatchId.CFB_MISSING_TOOLS_SORT).map(patch -> DynamicTest.dynamicTest(patch.id.name(), () -> {
            byte[] original = fixture(patch, "ecpatches.originalMods");
            byte[] replacement = fixture(patch, "ecpatches.patchedMods");
            byte[] output = transformer.transform(patch.className, patch.className, original);
            assertNotSame(original, output);
            new ClassReader(output).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
            assertEquals(MethodFingerprint.of(target(node(replacement), patch)), MethodFingerprint.of(target(node(output), patch)));
            assertEquals(otherMethods(node(original), patch), otherMethods(node(output), patch));
            assertSame(output, transformer.transform(patch.className, patch.className, output));
            assertSame(replacement, transformer.transform(patch.className, patch.className, replacement));
        }));
    }

    @TestFactory
    Stream<DynamicTest> skipsChangedOrRemovedMethods() {
        return PatchTransformer.patches().values().stream().filter(p -> !(p instanceof GhostlyTargetPatch) && p.id != PatchId.ASTRAL_PERK_FORMULA && p.id != PatchId.WAYSTONES_VILLAGE && p.id != PatchId.NUTRITION_LUNCHBOX && p.id != PatchId.SCALING_FEAST_SATURATION_HUD && p.id != PatchId.VFP_FOOD && p.id != PatchId.VFP_FOOD_VALUES && p.id != PatchId.CFB_MISSING_TOOLS_SORT).map(patch -> DynamicTest.dynamicTest(patch.id.name(), () -> {
            ClassNode changed = node(fixture(patch, "ecpatches.originalMods"));
            target(changed, patch).instructions.insert(new InsnNode(Opcodes.NOP));
            byte[] input = bytes(changed);
            assertSame(input, transformer.transform(patch.className, patch.className, input));
            changed.methods.remove(target(changed, patch));
            input = bytes(changed);
            assertSame(input, transformer.transform(patch.className, patch.className, input));
        }));
    }

    @TestFactory
    Stream<DynamicTest> switchesAreIndependent() {
        return PatchTransformer.patches().values().stream().filter(p -> !(p instanceof GhostlyTargetPatch) && p.id != PatchId.ASTRAL_PERK_FORMULA && p.id != PatchId.WAYSTONES_VILLAGE && p.id != PatchId.NUTRITION_LUNCHBOX && p.id != PatchId.SCALING_FEAST_SATURATION_HUD && p.id != PatchId.VFP_FOOD && p.id != PatchId.VFP_FOOD_VALUES && p.id != PatchId.CFB_MISSING_TOOLS_SORT).map(disabled -> DynamicTest.dynamicTest(disabled.id.name(), () -> {
            Path file = directory.resolve(disabled.id.name() + ".cfg");
            Files.write(file, (disabled.id.key + "=false\n").getBytes(StandardCharsets.UTF_8));
            PatchTransformer configured = new PatchTransformer(PatchSettings.read(file.toFile()));
            for (MethodPatch patch : PatchTransformer.patches().values()) {
                if (patch instanceof GhostlyTargetPatch || patch.id == PatchId.ASTRAL_PERK_FORMULA || patch.id == PatchId.WAYSTONES_VILLAGE || patch.id == PatchId.NUTRITION_LUNCHBOX || patch.id == PatchId.SCALING_FEAST_SATURATION_HUD || patch.id == PatchId.VFP_FOOD || patch.id == PatchId.VFP_FOOD_VALUES || patch.id == PatchId.CFB_MISSING_TOOLS_SORT) continue;
                byte[] input = fixture(patch, "ecpatches.originalMods");
                byte[] output = configured.transform(patch.className, patch.className, input);
                if (patch == disabled) assertSame(input, output);
                else assertNotSame(input, output);
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> supportsDevelopmentNames() {
        return PatchTransformer.patches().values().stream().filter(p -> !(p instanceof GhostlyTargetPatch) && p.id != PatchId.ASTRAL_PERK_FORMULA && p.id != PatchId.WAYSTONES_VILLAGE && p.id != PatchId.NUTRITION_LUNCHBOX && p.id != PatchId.SCALING_FEAST_SATURATION_HUD && p.id != PatchId.VFP_FOOD && p.id != PatchId.VFP_FOOD_VALUES && p.id != PatchId.CFB_MISSING_TOOLS_SORT).map(patch -> DynamicTest.dynamicTest(patch.id.name(), () -> {
            Properties mappings = new Properties();
            try (InputStream input = getClass().getResourceAsStream("/ecpatches/minecraft-names.properties")) {
                mappings.load(input);
            }
            ClassNode original = node(fixture(patch, "ecpatches.originalMods"));
            for (Object value : original.methods) {
                MethodNode method = (MethodNode) value;
                method.name = mappings.getProperty(method.name, method.name);
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        call.name = mappings.getProperty(call.name, call.name);
                    } else if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        field.name = mappings.getProperty(field.name, field.name);
                    }
                }
            }
            byte[] input = bytes(original);
            byte[] output = transformer.transform(patch.className, patch.className, input);
            assertNotSame(input, output);
            new ClassReader(output).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
            assertEquals(MethodFingerprint.of(target(node(fixture(patch, "ecpatches.patchedMods")), patch)),
                    MethodFingerprint.of(target(node(output), patch)));
            for (AbstractInsnNode instruction : target(node(output), patch).instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) assertFalse(mappings.containsKey(((MethodInsnNode) instruction).name));
                if (instruction instanceof FieldInsnNode) assertFalse(mappings.containsKey(((FieldInsnNode) instruction).name));
            }
        }));
    }
}
