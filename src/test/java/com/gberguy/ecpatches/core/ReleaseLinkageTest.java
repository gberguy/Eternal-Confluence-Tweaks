package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.GhostlyPatches;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ReleaseLinkageTest {
    @TempDir Path directory;

    private static class FixtureLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();

        void add(String path, boolean runtime) throws Exception {
            try (JarFile jar = new JarFile(path)) {
                Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) continue;
                    if (!(runtime ? name.startsWith("com/gberguy/ecpatches/runtime/")
                            : name.startsWith("net/minecraft/") || name.startsWith("net/minecraftforge/event/")
                            || name.startsWith("net/minecraftforge/fml/common/gameevent/TickEvent"))) continue;
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    try (java.io.InputStream input = jar.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    definitions.put(name.substring(0, name.length() - 6).replace('/', '.'), output.toByteArray());
                }
            }
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                byte[] bytes = definitions.get(name);
                result = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(result);
            return result;
        }

        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }

    @Test
    void saturationGuardResolvesReleaseNamesWithoutOptionalMods() throws Exception {
        String main = System.getProperty("ecpatches.releaseJar");
        String test = System.getProperty("ecpatches.releaseTestJar");
        assumeTrue(main != null && test != null, "Release artifacts not supplied");
        FixtureLoader loader = new FixtureLoader();
        loader.add(test, false);
        loader.add(main, true);
        Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object player = playerType.newInstance();
        Object stats = playerType.getMethod("func_71024_bL").invoke(player);
        Method set = stats.getClass().getMethod("func_75119_b", float.class);
        Method get = stats.getClass().getMethod("func_75115_e");
        Class<?> guard = loader.loadClass("com.gberguy.ecpatches.runtime.SaturationGuardEvents");
        Method shouldDraw = guard.getMethod("shouldDrawSaturation", playerType);
        set.invoke(stats, -40.0F);
        assertEquals(false, shouldDraw.invoke(null, player));
        assertEquals(-40.0F, get.invoke(stats));
        Class<?> tickType = loader.loadClass("net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent");
        Class<?> phase = loader.loadClass("net.minecraftforge.fml.common.gameevent.TickEvent$Phase");
        Object tick = tickType.getConstructor(phase, playerType).newInstance(phase.getEnumConstants()[1], player);
        Object handler = guard.getConstructor(PatchSettings.class)
                .newInstance(PatchSettings.read(directory.resolve("saturation.cfg").toFile()));
        Method onTick = guard.getMethod("onPlayerTick", tickType);
        onTick.invoke(handler, tick);
        assertEquals(0.0F, get.invoke(stats));
        assertEquals(true, shouldDraw.invoke(null, player));
        set.invoke(stats, 100.0F);
        onTick.invoke(handler, tick);
        assertEquals(100.0F, get.invoke(stats));
        assertEquals(true, shouldDraw.invoke(null, player));
    }

    @Test
    void nutritionSnapshotResolvesReleaseNamesWithoutEitherOptionalMod() throws Exception {
        String main = System.getProperty("ecpatches.releaseJar");
        String test = System.getProperty("ecpatches.releaseTestJar");
        assumeTrue(main != null && test != null, "Release artifacts not supplied");
        FixtureLoader loader = new FixtureLoader();
        loader.add(test, false);
        loader.add(main, true);
        Class<?> item = loader.loadClass("net.minecraft.item.Item");
        Class<?> stack = loader.loadClass("net.minecraft.item.ItemStack");
        Object original = stack.getConstructor(item, int.class, int.class).newInstance(item.newInstance(), 0, 4);
        Class<?> hooks = loader.loadClass("com.gberguy.ecpatches.runtime.NutritionConsumedStack");
        Method snapshot = hooks.getMethod("snapshot", stack);
        Object result = snapshot.invoke(null, original);
        assertNotSame(original, result);
        assertEquals(0, stack.getMethod("func_190916_E").invoke(original));
        assertEquals(1, stack.getMethod("func_190916_E").invoke(result));
        assertEquals(4, stack.getMethod("func_77960_j").invoke(result));
        Object empty = stack.getField("field_190927_a").get(null);
        assertSame(empty, snapshot.invoke(null, empty));
    }

    @Test
    void releaseNamesResolveAndVanillaGuardRunsOnTheJvm() throws Exception {
        String main = System.getProperty("ecpatches.releaseJar");
        String test = System.getProperty("ecpatches.releaseTestJar");
        assumeTrue(main != null && test != null, "Release artifacts not supplied");
        PatchSettings.initialize(directory.toFile());
        PatchSettings settings = PatchSettings.read(directory.resolve("options.cfg").toFile());
        FixtureLoader loader = new FixtureLoader();
        loader.add(test, false);
        loader.add(main, true);
        Class<?> entity = loader.loadClass("net.minecraft.entity.Entity");
        Class<?> livingBase = loader.loadClass("net.minecraft.entity.EntityLivingBase");
        Class<?> living = loader.loadClass("net.minecraft.entity.EntityLiving");
        Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Class<?> resource = loader.loadClass("net.minecraft.util.ResourceLocation");
        Class<?> potionType = loader.loadClass("net.minecraft.potion.Potion");
        Object mob = living.newInstance(), player = playerType.newInstance(), potion = potionType.newInstance();
        entity.getField("registryKey").set(mob, resource.getConstructor(String.class).newInstance("toroquest:monolitheye"));
        potionType.getField("registered").set(null, potion);
        livingBase.getField("activePotion").set(player, potion);
        Class<?> hooks = loader.loadClass("com.gberguy.ecpatches.runtime.GhostlyShape");
        Method ignore = hooks.getMethod("shouldIgnore", Object.class, Object.class, PatchSettings.class);
        assertEquals(true, ignore.invoke(null, mob, player, settings));
        Class<?> eventsType = loader.loadClass("com.gberguy.ecpatches.runtime.GhostlyEvents");
        Object events = eventsType.getConstructor(PatchSettings.class).newInstance(settings);
        living.getMethod("func_70624_b", livingBase).invoke(mob, player);
        Class<?> targetEvent = loader.loadClass("net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent");
        eventsType.getMethod("onTarget", targetEvent).invoke(events, targetEvent.getConstructor(livingBase, livingBase).newInstance(mob, player));
        assertNull(living.getMethod("func_70638_az").invoke(mob));
        Class<?> updateEvent = loader.loadClass("net.minecraftforge.event.entity.living.LivingEvent$LivingUpdateEvent");
        living.getMethod("func_70624_b", livingBase).invoke(mob, player);
        eventsType.getMethod("onUpdate", updateEvent).invoke(events, updateEvent.getConstructor(livingBase).newInstance(mob));
        assertNull(living.getMethod("func_70638_az").invoke(mob));
        MethodPatch patch = GhostlyPatches.create()[0];
        ClassNode target = GhostlyFixtureTest.node(GhostlyFixtureTest.fixture(patch, false));
        assertEquals(MethodPatch.Result.APPLIED, patch.apply(target));
        MethodNode guarded = GhostlyFixtureTest.method(target, patch);
        ClassNode harness = new ClassNode();
        harness.version = Opcodes.V1_8;
        harness.access = Opcodes.ACC_PUBLIC;
        harness.name = "GhostlyVanillaHarness";
        harness.superName = "java/lang/Object";
        harness.methods.add(guarded);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        harness.accept(writer);
        Method suitable = loader.define(writer.toByteArray()).getMethod("func_179445_a", living, livingBase, boolean.class, boolean.class);
        assertEquals(false, suitable.invoke(null, mob, player, false, true));
        livingBase.getField("activePotion").set(player, null);
        assertEquals(true, suitable.invoke(null, mob, player, false, true));
        livingBase.getField("activePotion").set(player, potion);
        entity.getField("registryKey").set(mob, resource.getConstructor(String.class).newInstance("minecraft:zombie"));
        assertEquals(true, suitable.invoke(null, mob, player, false, true));
        assertEquals(false, suitable.invoke(null, mob, null, false, true));
    }
}
