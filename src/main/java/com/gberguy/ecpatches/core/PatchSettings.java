package com.gberguy.ecpatches.core;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.EnumMap;
import java.util.Properties;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PatchSettings {
    private static final Logger LOG = LogManager.getLogger("EternalConfluenceTweaks");
    private static volatile PatchSettings current;
    private final EnumMap<PatchId, Boolean> enabled;
    private final int villageWaystoneWeight;
    private final String astralPerkLevelFormula;
    private static final String[] ASTRAL_FORMULA_HELP = {
        "# i begins at 1; prev is 0 for the first threshold and then the preceding whole-XP threshold.",
        "# Cumulative thresholds: level 2 starts at threshold i=1. Earned fractional XP is preserved.",
        "# Grammar (case-sensitive): i, prev, decimal/scientific numbers, + - * / ^, parentheses; floor(x), ceil(x), sqrt(x), min(a,b), max(a,b). Multiplication requires *.",
        "# Division is floating point; ^ is right-associative and precedes unary minus: -2^2=-4, 2^3^2=512. Whitespace is ignored.",
        "# Results are floored to whole XP, must be positive, strictly increasing, at most 9007199254740991, and allow nonzero Astral XP awards. Invalid active formulas stop server startup.",
        "# Limits: 2048 characters, 512 expression nodes, 64 nesting levels. Multiplayer clients ignore their local toggle and formula."
    };

    private PatchSettings(EnumMap<PatchId, Boolean> enabled, int villageWaystoneWeight, String astralPerkLevelFormula) {
        this.enabled = enabled;
        this.villageWaystoneWeight = villageWaystoneWeight;
        this.astralPerkLevelFormula = astralPerkLevelFormula;
    }

    public static synchronized void initialize(File gameDirectory) {
        if (current == null) {
            current = read(new File(gameDirectory, "config/eternalconfluencetweaks.cfg"));
        }
    }

    public static PatchSettings current() {
        if (current == null) {
            File home = Launch.minecraftHome;
            initialize(home == null ? new File(System.getProperty("user.dir")) : home);
        }
        return current;
    }

    public static PatchSettings read(File file) {
        EnumMap<PatchId, Boolean> values = new EnumMap<>(PatchId.class);
        Properties properties = new Properties();
        int villageWaystoneWeight = 100;
        try {
            String original = file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
            properties.load(new StringReader(original));
            if (!properties.containsKey(PatchId.VFP_FOOD_VALUES.key) && properties.containsKey(PatchId.VFP_FOOD.key)) {
                properties.setProperty(PatchId.VFP_FOOD_VALUES.key, properties.getProperty(PatchId.VFP_FOOD.key));
            }
            for (PatchId id : PatchId.values()) {
                String value = properties.getProperty(id.key);
                if (value == null) {
                    values.put(id, true);
                } else if ("true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim())) {
                    values.put(id, Boolean.parseBoolean(value.trim()));
                } else {
                    values.put(id, false);
                    LOG.warn("Invalid value for {} in {}; disabling this patch. Use true or false.", id.key, file);
                }
            }
            try {
                villageWaystoneWeight = Integer.parseInt(properties.getProperty("villageWaystoneWeight", "100").trim());
                if (villageWaystoneWeight < 1 || villageWaystoneWeight > 1000000) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException invalid) {
                villageWaystoneWeight = 100;
                values.put(PatchId.WAYSTONES_VILLAGE, false);
                LOG.warn("Invalid villageWaystoneWeight in {}; disabling the Waystones weight tweak. Use an integer from 1 to 1000000.", file);
            }
            String rendered = render(properties, original);
            if (!rendered.equals(original)) {
                Path parent = file.toPath().toAbsolutePath().getParent();
                Files.createDirectories(parent);
                Path temporary = Files.createTempFile(parent, "ecpatches-", ".tmp");
                try {
                    Files.write(temporary, rendered.getBytes(StandardCharsets.UTF_8));
                    try {
                        Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            LOG.error("Cannot load patch configuration {}; disabling all patches for this launch.", file, error);
            for (PatchId id : PatchId.values()) {
                values.put(id, false);
            }
        }
        return new PatchSettings(values, villageWaystoneWeight, properties.getProperty("astralPerkLevelFormula", "prev + 1000 * i * i").trim());
    }

    private static String render(Properties properties, String original) throws IOException {
        String ghostHeader = "# Corail Tombstone - Ghostly Shape Compatibility";
        String ghostDescription = "# Makes mobs from enabled mods ignore players affected by Ghostly Shape, including players they already targeted.";
        String weightDescription = "# Sets the village Waystone selection weight used by the tweak above. Higher values make Waystones more likely to be selected. Range: 1-1000000. Default: 100. Original Waystones weight: 3.";
        Set<String> generated = new HashSet<>(Arrays.asList(
                "# Eternal Confluence Tweaks", "# General Fixes", ghostHeader, ghostDescription, weightDescription,
                "# Uses a configurable cumulative perk XP formula in local worlds and on the server. Clients use server thresholds regardless of local settings. Remove Astral Level Nerf. Changes require a full restart.",
                "# Limits: 2048 characters, 512 expression nodes, 64 nesting levels. Changes require a full restart. Multiplayer clients ignore their local toggle and formula.",
                "# villageWaystoneWeight accepts positive integer weights from 1 to 1000000. Higher weights make village Waystones more common. Waystones default is 3.",
                "# Target: Nutrition UEL / Spice of Life",
                "# Target: Ancient Spellcraft",
                "# Target: ToroQuest",
                "# Target: Electroblob's Wizardry",
                "# Fixes compressed slate variants having incorrect names, variant handling, and crafting recipe behavior.",
                "# Fixes Wildwood disappearing instead of converting correctly to Runed Wildwood.",
                "# Fixes missing nutrition when the last food in a Spice of Life lunchbox or lunchbag stack is consumed.",
                "# Resets negative player saturation to zero at the end of each player tick on the server and client, preserving positive values above 20.",
                "# Skips the saturation bar when player saturation is negative, preventing invalid colour indexing without disabling the saturation display.",
                "# Prevents Lycanites mobs from performing melee attacks through walls and other solid obstacles.",
                "# Fixes a startup crash when MoreChids generates custom Orechid classes.",
                "# Set waystonesVillageWeight to false to disable this tweak.",
                "# Replaces Witchery Villages guards with ToroQuest guards in active Provinces and suppresses them in Provinces without a Lord.",
                "# Expands Witchery Villages wall footprints around ToroQuest village structures when both mods are installed.",
                "# Set each fix to true or false. Changes require a full game/server restart.",
                "# Target: Lycanites Mobs / Corail Tombstone",
                "# Target: Vanilla Food Pantry",
                "# Fixes multi-use servings with expanded hunger and isolates temporary serving nutrition from other food queries.",
                "# Target: Vanilla Food Pantry / expanded hunger",
                "# Target: Vanilla Food Pantry / AppleCore food values",
                "# Fixes multi-use serving amounts with expanded hunger using AppleCore's player-specific maximum. Preserves VFP's serving and consumption rules.",
                "# Isolates temporary serving nutrition from other food queries, including Spice of Life: Carrot Edition progress calculations. Independent of expanded hunger.",
                "# Target: Vanilla Food Pantry / hunger restoration",
                "# Target: Vanilla Food Pantry / food tracking",
                "# Prevents bites of VFP bread, sandwiches, pies, and other multi-use foods from removing hunger and saturation when your hunger is above 20. Uses your increased hunger limit to work out how much food to eat. For AppleCore with mods that increase maximum hunger.",
                "# Prevents eating VFP food from making your Spice of Life: Carrot Edition food count and bonus hearts incorrectly drop or jump. Also stops other mods from mistaking the hunger and saturation of your current bite for the values of other VFP foods. Useful even without increased maximum hunger.",
                "# Target: Vanilla Food Pantry + AppleCore / hunger restoration",
                "# Target: Vanilla Food Pantry + AppleCore + Spice of Life: Carrot Edition / tasted foods progress",
                "# Uses AppleCore to find your maximum hunger. Prevents VFP multi-use foods from removing hunger and saturation when your hunger is above 20, and calculates how much food to eat using your increased hunger limit. This tweak needs Vanilla Food Pantry, AppleCore, and a mod that increases maximum hunger.",
                "# Fixes incorrect food values reported through VFP's AppleCore integration. Prevents eating VFP food from making your Spice of Life: Carrot Edition \"tasted foods\" count and bonus hearts incorrectly drop or jump. Ensures previously eaten foods are counted using their own hunger values. This tweak needs Vanilla Food Pantry, AppleCore, and Spice of Life: Carrot Edition.",
                "# Uses BlockSlate's existing subtype-aware item instead of a generic ItemBlock. Tested with 1.12.2-2.2.2-31.",
                "# Rejects an air block returned by the block registry when resolving a state matcher. Tested with 1.12.2-3.1.9.2.",
                "# Requires the melee target to be visible before a melee attack can proceed. Tested with 1.12.2-2.0.8.10.",
                "# Prevents targeting players with tombstone:ghostly_shape. Does nothing special when that potion is absent. Tested with Lycanites Mobs 1.12.2-2.0.8.10.",
                "# Fixes invalid ASM descriptors when generating custom Orechid classes by using internal-name-aware type construction. Tested with 1.3.0."));
        generated.addAll(Arrays.asList(ASTRAL_FORMULA_HELP));
        Set<String> keys = new HashSet<>();
        keys.add("astralPerkLevelFormula");
        keys.add("villageWaystoneWeight");
        for (PatchId id : PatchId.values()) {
            keys.add(id.key);
            generated.add("# Target: " + id.modName);
            generated.add("# Tested ver: " + id.testedVersions);
            if (!id.description.isEmpty()) generated.add("# " + id.description);
        }
        StringBuilder retained = new StringBuilder();
        StringBuilder logical = new StringBuilder();
        for (String line : original.split("\r?\n")) {
            logical.append(line).append('\n');
            int slashes = 0;
            for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) slashes++;
            if ((slashes & 1) != 0 && !line.trim().startsWith("#") && !line.trim().startsWith("!")) continue;
            Properties entry = new Properties();
            entry.load(new StringReader(logical.toString()));
            boolean known = entry.size() == 1 && keys.contains(entry.stringPropertyNames().iterator().next());
            if (!known && !generated.contains(logical.toString().trim())) retained.append(logical);
            logical.setLength(0);
        }
        if (logical.length() > 0) retained.append(logical);
        StringBuilder output = new StringBuilder("# Eternal Confluence Tweaks\n\n# General Fixes\n");
        for (boolean ghostly : new boolean[]{false, true}) {
            if (ghostly) output.append('\n').append(ghostHeader).append('\n');
            for (PatchId id : PatchId.values()) {
                if (id.ghostly() != ghostly) continue;
                output.append("\n# Target: ").append(id.modName).append('\n');
                output.append("# Tested ver: ").append(id.testedVersions).append('\n');
                if (!id.description.isEmpty()) output.append("# ").append(id.description).append('\n');
                String value = properties.getProperty(id.key, "true").trim().replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
                if (id == PatchId.ASTRAL_PERK_FORMULA) {
                    for (String line : ASTRAL_FORMULA_HELP) output.append(line).append('\n');
                }
                output.append(id.key).append('=').append(value).append('\n');
                if (id == PatchId.ASTRAL_PERK_FORMULA) {
                    String formula = properties.getProperty("astralPerkLevelFormula", "prev + 1000 * i * i").trim()
                            .replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
                    output.append("astralPerkLevelFormula=").append(formula).append('\n');
                }
                if (id == PatchId.WAYSTONES_VILLAGE) {
                    String weight = properties.getProperty("villageWaystoneWeight", "100").trim()
                            .replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
                    output.append('\n').append(weightDescription).append('\n');
                    output.append("villageWaystoneWeight=").append(weight).append('\n');
                }
            }
        }
        String extra = retained.toString().trim();
        if (!extra.isEmpty()) output.append('\n').append(extra).append('\n');
        return output.toString();
    }

    public boolean enabled(PatchId id) {
        return Boolean.TRUE.equals(enabled.get(id));
    }

    public String astralPerkLevelFormula() { return astralPerkLevelFormula; }

    public int villageWaystoneWeight() {
        return villageWaystoneWeight;
    }
}
