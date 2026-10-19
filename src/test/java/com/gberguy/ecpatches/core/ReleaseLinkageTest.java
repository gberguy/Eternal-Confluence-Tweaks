package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.GhostlyPatches;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ReleaseLinkageTest {
    @TempDir Path directory;

    @Test
    void astralHooksResolveReleaseWorldFieldsWithoutAstralOrClientClasses() throws Exception {
        String main = System.getProperty("ecpatches.releaseJar");
        String test = System.getProperty("ecpatches.releaseTestJar");
        assumeTrue(main != null && test != null, "Release artifacts not supplied");
        FixtureLoader loader = new FixtureLoader();
        loader.add(test, false);
        loader.add(main, true);
        Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object serverPlayer = playerType.newInstance(), clientPlayer = playerType.newInstance();
        Field world = loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p");
        world.get(clientPlayer).getClass().getField("field_72995_K").setBoolean(world.get(clientPlayer), true);
        Class<?> curveType = loader.loadClass("com.gberguy.ecpatches.runtime.AstralPerkCurve");
        Method calculate = curveType.getMethod("calculate", String.class, int.class);
        Object serverCurve = calculate.invoke(null, "prev+1000*i*i", 100);
        Object remoteCurve = calculate.invoke(null, "prev+500*i", 40);
        Class<?> hooks = loader.loadClass("com.gberguy.ecpatches.runtime.AstralPerkHooks");
        hooks.getMethod("activateServer", curveType).invoke(null, serverCurve);
        Object session = hooks.getField("CLIENT").get(null), connection = new Object();
        session.getClass().getMethod("begin", Object.class).invoke(session, connection);
        session.getClass().getMethod("accept", Object.class, curveType).invoke(session, connection, remoteCurve);
        Method table = hooks.getMethod("table", Map.class, int.class, playerType);
        assertEquals(curveType.getField("thresholds").get(serverCurve), table.invoke(null, Collections.emptyMap(), 30, serverPlayer));
        assertEquals(curveType.getField("thresholds").get(remoteCurve), table.invoke(null, Collections.emptyMap(), 30, clientPlayer));
        assertEquals(40, hooks.getMethod("globalCap", int.class, playerType).invoke(null, 30, clientPlayer));
        assertEquals(100, hooks.getMethod("globalCap", int.class, playerType).invoke(null, 30, serverPlayer));
        session.getClass().getMethod("end", Object.class).invoke(session, connection);
        hooks.getMethod("stopServer").invoke(null);
    }

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

        void useServerFoodStats(String path) throws Exception {
            try (JarFile jar = new JarFile(path);
                 java.io.InputStream input = jar.getInputStream(jar.getJarEntry("net/minecraft/util/FoodStats.class"))) {
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, 0);
                assertTrue(node.methods.stream().noneMatch(value -> ((MethodNode) value).name.equals("func_75119_b")));
                node.methods.removeIf(value -> {
                    MethodNode method = (MethodNode) value;
                    return !method.name.equals("<init>") && !method.name.equals("func_75115_e");
                });
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                definitions.put("net.minecraft.util.FoodStats", writer.toByteArray());
            }
        }

        void changeFoodStats(boolean clientSetter, String fieldName) {
            ClassNode node = new ClassNode();
            new ClassReader(definitions.get("net.minecraft.util.FoodStats")).accept(node, 0);
            for (Object value : node.fields) {
                FieldNode field = (FieldNode) value;
                if (field.name.equals("field_75125_b")) field.name = fieldName;
            }
            for (Object value : node.methods) {
                for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray()) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode field = (FieldInsnNode) instruction;
                        if (field.name.equals("field_75125_b")) field.name = fieldName;
                    }
                }
            }
            if (clientSetter) {
                MethodNode setter = new MethodNode(Opcodes.ACC_PUBLIC, "func_75119_b", "(F)V", null, null);
                setter.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                setter.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
                setter.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, fieldName, "F"));
                setter.instructions.add(new InsnNode(Opcodes.RETURN));
                node.methods.add(setter);
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            definitions.put("net.minecraft.util.FoodStats", writer.toByteArray());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void saturationGuardResolvesReleaseNamesWithoutOptionalMods(boolean clientSetter) throws Exception {
        FixtureLoader loader = saturationLoader();
        loader.changeFoodStats(clientSetter, "field_75125_b");
        if (!clientSetter) assertOldSetterFails(loader);
        verifySaturationGuard(loader);
    }

    @Test
    void saturationGuardRunsAgainstReleasedDedicatedServerFoodStats() throws Exception {
        String server = System.getProperty("ecpatches.minecraftServerSrg");
        assumeTrue(server != null, "Dedicated server binary not supplied");
        FixtureLoader loader = saturationLoader();
        loader.useServerFoodStats(server);
        assertOldSetterFails(loader);
        verifySaturationGuard(loader);
    }

    @Test
    void unavailableSaturationFieldDoesNotBreakTickOrReadOnlyHud() throws Exception {
        FixtureLoader loader = saturationLoader();
        loader.changeFoodStats(false, "unrecognizedSaturation");
        Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object player = playerType.newInstance();
        Object stats = playerType.getMethod("func_71024_bL").invoke(player);
        Field field = stats.getClass().getDeclaredField("unrecognizedSaturation");
        field.setAccessible(true);
        field.setFloat(stats, -40.0F);
        Class<?> guard = loader.loadClass("com.gberguy.ecpatches.runtime.SaturationGuardEvents");
        Object handler = guard.getConstructor(PatchSettings.class)
                .newInstance(PatchSettings.read(directory.resolve("missing-field.cfg").toFile()));
        Class<?> tickType = loader.loadClass("net.minecraftforge.fml.common.gameevent.TickEvent$PlayerTickEvent");
        Class<?> phase = loader.loadClass("net.minecraftforge.fml.common.gameevent.TickEvent$Phase");
        Object tick = tickType.getConstructor(phase, playerType).newInstance(phase.getEnumConstants()[1], player);
        for (int i = 0; i < 2; i++) guard.getMethod("onPlayerTick", tickType).invoke(handler, tick);
        assertEquals(-40.0F, field.getFloat(stats));
        assertEquals(false, guard.getMethod("shouldDrawSaturation", playerType).invoke(null, player));
        Field cached = loader.loadClass(guard.getName() + "$SaturationField").getDeclaredField("field");
        cached.setAccessible(true);
        assertNull(cached.get(null));
    }

    private FixtureLoader saturationLoader() throws Exception {
        String main = System.getProperty("ecpatches.releaseJar");
        String test = System.getProperty("ecpatches.releaseTestJar");
        assumeTrue(main != null && test != null, "Release artifacts not supplied");
        FixtureLoader loader = new FixtureLoader();
        loader.add(test, false);
        loader.add(main, true);
        return loader;
    }

    private static void assertOldSetterFails(FixtureLoader loader) throws Exception {
        Class<?> stats = loader.loadClass("net.minecraft.util.FoodStats");
        assertThrows(NoSuchMethodException.class, () -> stats.getMethod("func_75119_b", float.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "OldSaturationSetter", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "clear",
                "(Lnet/minecraft/util/FoodStats;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.FCONST_0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/util/FoodStats", "func_75119_b", "(F)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        Method clear = loader.define(writer.toByteArray()).getMethod("clear", stats);
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> clear.invoke(null, stats.newInstance()));
        assertTrue(error.getCause() instanceof NoSuchMethodError);
    }

    private void verifySaturationGuard(FixtureLoader loader) throws Exception {
        Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
        Object player = playerType.newInstance();
        Object stats = playerType.getMethod("func_71024_bL").invoke(player);
        Field saturation = stats.getClass().getDeclaredField("field_75125_b");
        saturation.setAccessible(true);
        Method get = stats.getClass().getMethod("func_75115_e");
        Map<Field, Object> before = new HashMap<>();
        for (Field field : stats.getClass().getDeclaredFields()) {
            if (field.equals(saturation)) continue;
            field.setAccessible(true);
            before.put(field, field.get(stats));
        }
        Class<?> guard = loader.loadClass("com.gberguy.ecpatches.runtime.SaturationGuardEvents");
        Method shouldDraw = guard.getMethod("shouldDrawSaturation", playerType);
        saturation.setFloat(stats, -40.0F);
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
        saturation.setFloat(stats, 100.0F);
        onTick.invoke(handler, tick);
        assertEquals(100.0F, get.invoke(stats));
        assertEquals(true, shouldDraw.invoke(null, player));
        for (Map.Entry<Field, Object> entry : before.entrySet()) assertEquals(entry.getValue(), entry.getKey().get(stats));
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
