package com.gberguy.ecpatches.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class PatchSettingsTest {
    @TempDir Path directory;

    @Test
    void generatesExplainedDefaultsAndDoesNotRewriteThem() throws Exception {
        Path file = directory.resolve("config/patches.cfg");
        PatchSettings settings = PatchSettings.read(file.toFile());
        String contents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        for (PatchId id : PatchId.values()) {
            assertTrue(settings.enabled(id));
            assertTrue(contents.contains(id.key + "=true"));
            assertTrue(contents.contains(id.modName));
            assertTrue(contents.contains(id.description));
        }
        assertTrue(contents.startsWith("# Eternal Confluence Tweaks\n"));
        assertEquals(100, settings.villageWaystoneWeight());
        assertTrue(contents.contains("villageWaystoneWeight=100\n"));
        PatchSettings.read(file.toFile());
        assertEquals(contents, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void preservesDisabledOptionsAndAddsMissingOnes() throws Exception {
        Path file = directory.resolve("patches.cfg");
        String initial = "# custom\n" + PatchId.ROOTS.key + "=false";
        Files.write(file, initial.getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(file.toFile());
        assertFalse(settings.enabled(PatchId.ROOTS));
        assertTrue(settings.enabled(PatchId.MORECHIDS));
        assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("# custom"));
        assertFalse(PatchSettings.read(file.toFile()).enabled(PatchId.ROOTS));
    }

    @Test
    void invalidValueDisablesOnlyItsOption() throws Exception {
        Path file = directory.resolve("patches.cfg");
        Files.write(file, (PatchId.ROOTS.key + "=maybe\n").getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(file.toFile());
        assertFalse(settings.enabled(PatchId.ROOTS));
        assertTrue(settings.enabled(PatchId.MORECHIDS));
    }

    @Test
    void migratesOldConfigIntoSectionsWithoutLosingUserChoices() throws Exception {
        Path file = directory.resolve("old.cfg");
        String old = "# Eternal Confluence Tweaks\n"
                + "# Set each fix to true or false. Changes require a full game/server restart.\n"
                + "# Target: Lycanites Mobs / Corail Tombstone\n"
                + "# Prevents targeting players with tombstone:ghostly_shape. Does nothing special when that potion is absent. Tested with Lycanites Mobs 1.12.2-2.0.8.10.\n"
                + "# Target: Nutrition UEL / Spice of Life\n"
                + "# Fixes missing nutrition when the last food in a Spice of Life lunchbox or lunchbag stack is consumed.\n"
                + "# Makes mobs from enabled mods ignore players affected by Ghostly Shape, including players they already targeted.\n"
                + "# villageWaystoneWeight accepts positive integer weights from 1 to 1000000. Higher weights make village Waystones more common. Waystones default is 3.\n"
                + "lycanitesGhostlyShape=false\nrootsAirStateMatcher=false\n# My custom note\nfutureOption=keep\n";
        Files.write(file, old.getBytes(StandardCharsets.UTF_8));
        PatchSettings options = PatchSettings.read(file.toFile());
        assertFalse(options.enabled(PatchId.LYCANITES_GHOST));
        assertFalse(options.enabled(PatchId.ROOTS));
        assertTrue(options.enabled(PatchId.WIZARDRY_GHOST));
        String rendered = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int section = rendered.indexOf("# Corail Tombstone - Ghostly Shape Compatibility");
        assertTrue(section > rendered.indexOf("# General Fixes"));
        for (PatchId id : PatchId.values()) {
            assertEquals(id.ghostly(), rendered.indexOf(id.key + "=") > section);
            assertEquals(1, rendered.split(id.key + "=", -1).length - 1);
        }
        assertTrue(rendered.contains("# My custom note\nfutureOption=keep"));
        assertFalse(rendered.contains("Tested with"));
        assertFalse(rendered.contains("Set each fix"));
        assertFalse(rendered.contains("# Target: Nutrition UEL / Spice of Life"));
        assertFalse(rendered.contains("# Fixes missing nutrition when the last food in a Spice of Life lunchbox or lunchbag stack is consumed."));
        assertFalse(rendered.contains("including players they already targeted"));
        assertFalse(rendered.contains("villageWaystoneWeight accepts"));
        PatchSettings.read(file.toFile());
        assertEquals(rendered, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void unreadableConfigDisablesAll() {
        PatchSettings settings = PatchSettings.read(directory.toFile());
        for (PatchId id : PatchId.values()) {
            assertFalse(settings.enabled(id));
        }
    }

    @Test
    void absentTargetsNeverResolveTheirClasses() {
        PatchTransformer transformer = new PatchTransformer(PatchSettings.read(directory.resolve("patches.cfg").toFile()));
        byte[] bytes = {1, 2, 3};
        assertSame(bytes, transformer.transform("unrelated.Class", "unrelated.Class", bytes));
        for (MethodPatch patch : PatchTransformer.patches().values()) {
            assertNull(transformer.transform(patch.className, patch.className, null));
            assertSame(bytes, transformer.transform(patch.className, patch.className, bytes));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 100, 250, 1000000})
    void preservesConfiguredVillageWeight(int weight) throws Exception {
        Path file = directory.resolve("weight.cfg");
        Files.write(file, ("villageWaystoneWeight=" + weight + "\nrootsAirStateMatcher=false\n").getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(file.toFile());
        assertEquals(weight, settings.villageWaystoneWeight());
        assertTrue(settings.enabled(PatchId.WAYSTONES_VILLAGE));
        assertFalse(settings.enabled(PatchId.ROOTS));
        String rendered = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertEquals(1, rendered.split("villageWaystoneWeight=", -1).length - 1);
        assertEquals(weight, PatchSettings.read(file.toFile()).villageWaystoneWeight());
        assertEquals(rendered, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.5", "no", "1000001", "2147483647", "999999999999999999"})
    void invalidVillageWeightDisablesOnlyWaystones(String weight) throws Exception {
        Path file = directory.resolve("invalid.cfg");
        Files.write(file, ("villageWaystoneWeight=" + weight + "\n").getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(file.toFile());
        for (PatchId id : PatchId.values()) {
            assertEquals(id != PatchId.WAYSTONES_VILLAGE, settings.enabled(id));
        }
        assertEquals(100, settings.villageWaystoneWeight());
        assertFalse(PatchSettings.read(file.toFile()).enabled(PatchId.WAYSTONES_VILLAGE));
    }
}
