package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.AstralPerkLevelPatch;
import com.gberguy.ecpatches.runtime.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.entity.player.EntityPlayer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class AstralPerkLevelTest implements Opcodes {
    @TempDir Path directory;
    private static final String TARGET = AstralPerkLevelPatch.TARGET;
    private byte[] original;
    private Object connection;

    public static class Entry {
        public enum Section { PERK_LEVELS }
        public Entry(Section section, String key) { }
        public String getKey() { return "level"; }
        public String getConfigurationSection() { return "general"; }
    }
    public static class Mods {
        public static Mods GAMESTAGES = new Mods(), CRAFTTWEAKER = new Mods();
        public boolean present;
        public boolean isPresent() { return present; }
    }
    public interface StageData { Collection<String> getStages(); }
    public static class Stages implements StageData {
        public Collection<String> getStages() { return Arrays.asList("tier1", "tier2"); }
    }
    public static class StageHelper {
        public static StageData getPlayerData(EntityPlayer player) { return new Stages(); }
    }
    public static class StageTweaks {
        public static int getMaxCap(String stage) { return stage.equals("tier1") ? 10 : 25; }
    }
    public static class MathHelper {
        public static long func_76124_d(double value) { return (long) Math.floor(value); }
        public static long lfloor(double value) { return func_76124_d(value); }
    }
    public static class Configuration {
        public int cap = 100;
        public int getInt(String key, String section, int fallback, int min, int max, String help) { return cap; }
    }

    @BeforeEach
    void load() throws Exception {
        String path = System.getProperty("ecpatches.astralJar");
        assumeTrue(path != null, "Astral Sorcery 1.10.27 fixture not supplied");
        try (JarFile jar = new JarFile(path)) { original = read(jar.getInputStream(jar.getJarEntry(TARGET.replace('.', '/') + ".class"))); }
        connection = new Object();
        AstralPerkHooks.stopServer();
    }

    @AfterEach
    void clear() {
        AstralPerkHooks.CLIENT.end(connection);
        AstralPerkHooks.stopServer();
    }

    private static byte[] read(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
    private static ClassNode node(byte[] bytes) { ClassNode n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    private static byte[] bytes(ClassNode node) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(w); return w.toByteArray(); }
    private PatchTransformer transformer(boolean enabled) throws Exception {
        Path file = directory.resolve("settings.cfg");
        Files.write(file, ("astralPerkLevelFormulaEnabled=" + enabled + "\nastralPerkLevelFormula=invalid client formula\n").getBytes(StandardCharsets.UTF_8));
        return new PatchTransformer(PatchSettings.read(file.toFile()));
    }
    private static MethodNode method(ClassNode n, MethodPatch patch) {
        for (Object item : n.methods) { MethodNode m = (MethodNode) item; if (m.name.equals(patch.methodName) && m.desc.equals(patch.descriptor)) return m; }
        throw new AssertionError();
    }

    @Test
    void patchesExactBinaryOnceAndPreservesAllOtherMethods() throws Exception {
        PatchTransformer t = transformer(true);
        byte[] output = t.transform(TARGET, TARGET, original);
        assertNotSame(original, output);
        new ClassReader(output).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        assertSame(output, t.transform(TARGET, TARGET, output));
        ClassNode before = node(original), after = node(output);
        Set<String> changed = new HashSet<>();
        for (MethodPatch patch : AstralPerkLevelPatch.create()) {
            changed.add(patch.methodName + patch.descriptor);
            assertEquals(MethodPatch.Result.ALREADY_PRESENT, patch.apply(after));
            assertNotEquals(MethodFingerprint.of(method(before, patch)), MethodFingerprint.of(method(after, patch)));
            ClassNode unsupported = node(original);
            method(unsupported, patch).instructions.insert(new InsnNode(NOP));
            assertEquals(MethodPatch.Result.UNSUPPORTED, patch.apply(unsupported));
            unsupported.methods.remove(method(unsupported, patch));
            assertEquals(MethodPatch.Result.UNSUPPORTED, patch.apply(unsupported));
        }
        for (Object item : before.methods) {
            MethodNode m = (MethodNode) item;
            if (changed.contains(m.name + m.desc)) continue;
            MethodNode found = null;
            for (Object candidate : after.methods) { MethodNode c = (MethodNode) candidate; if (c.name.equals(m.name) && c.desc.equals(m.desc)) found = c; }
            assertNotNull(found);
            assertEquals(MethodFingerprint.of(m), MethodFingerprint.of(found));
        }
    }

    @Test
    void realLevelAndProgressQueriesUseServerValuesEvenWithDisabledOrInvalidClientConfig() throws Exception {
        Loader loader = loader(transformer(false).transform(TARGET, TARGET, original));
        Object manager = manager(loader);
        configure(loader, manager, 30);
        AstralPerkCurve curve = AstralPerkCurve.calculate(AstralFormula.DEFAULT, 100);
        AstralPerkHooks.activateServer(AstralPerkCurve.calculate("prev+500*i", 40));
        AstralPerkHooks.CLIENT.begin(connection);
        assertTrue(AstralPerkHooks.CLIENT.accept(connection, curve));
        EntityPlayer client = new EntityPlayer(); client.world.isRemote = true;
        assertEquals(1, level(manager, 999.9999, client));
        assertEquals(2, level(manager, 1000, client));
        assertEquals(2, level(manager, 4999.9999, client));
        assertEquals(3, level(manager, 5000, client));
        assertEquals(4, level(manager, 22306.117609824985, client));
        assertEquals(100, level(manager, 1e12, client));
        assertEquals(100, manager.getClass().getMethod("getLevelCapFor", EntityPlayer.class).invoke(null, client));
        assertEquals((22306.117609824985 - 14000) / 16000,
                ((Number) manager.getClass().getMethod("getNextLevelPercent", double.class, EntityPlayer.class).invoke(manager, 22306.117609824985, client)).doubleValue(), 1e-6);
        EntityPlayer server = new EntityPlayer();
        assertEquals(3, level(manager, 2000, server));
        assertEquals(40, level(manager, 1e12, server));
        assertEquals(2, level(manager, 2000, client));
        configure(loader, manager, 20);
        assertEquals(100, level(manager, 1e12, client));
        assertEquals(40, level(manager, 1e12, server));
        AstralPerkHooks.CLIENT.end(connection);
        assertEquals(16, level(manager, 6396, client));
    }

    @Test
    void preservesActualGameStageResolutionAndXpAwardMechanicsWithoutDeletingAllocatedPerks() throws Exception {
        Loader loader = loader(transformer(true).transform(TARGET, TARGET, original));
        Object manager = manager(loader);
        configure(loader, manager, 100);
        AstralPerkHooks.activateServer(AstralPerkCurve.calculate(AstralFormula.DEFAULT, 100));
        EntityPlayer player = new EntityPlayer();
        Class<?> mods = loader.loadClass("hellfirepvp.astralsorcery.common.base.Mods");
        for (String name : new String[]{"GAMESTAGES", "CRAFTTWEAKER"}) mods.getField("present").setBoolean(mods.getField(name).get(null), true);
        assertEquals(25, level(manager, 1e12, player));
        Class<?> progressType = loader.loadClass("hellfirepvp.astralsorcery.common.data.research.PlayerProgress");
        Object progress = progressType.newInstance();
        Field xp = progressType.getDeclaredField("perkExp"); xp.setAccessible(true);
        xp.setDouble(progress, 22306.117609824985);
        Method modify = progressType.getDeclaredMethod("modifyExp", double.class, EntityPlayer.class); modify.setAccessible(true);
        modify.invoke(progress, 0.117609824985, player);
        assertEquals(22306.23521964997, xp.getDouble(progress), 1e-10);
        modify.invoke(progress, 1e6, player);
        assertEquals(22306.23521964997 + 2000, xp.getDouble(progress), 1e-10);
        Field applied = progressType.getDeclaredField("appliedPerks"); applied.setAccessible(true);
        Set<Object> perks = (Set<Object>) applied.get(progress);
        for (int i = 0; i < 15; i++) perks.add(new Object());
        int count = perks.size();
        assertTrue(((Number) progressType.getMethod("getAvailablePerkPoints", EntityPlayer.class).invoke(progress, player)).intValue() < 0);
        assertEquals(false, progressType.getMethod("hasFreeAllocationPoint", EntityPlayer.class).invoke(progress, player));
        assertEquals(count, perks.size());
        xp.setDouble(progress, 1e12);
        modify.invoke(progress, 1000, player);
        assertEquals(1e12, xp.getDouble(progress));
    }

    @Test
    void disabledServerSendsVanillaAndClientsDoNotUseTheirLocalCurve() throws Exception {
        Loader loader = loader(transformer(false).transform(TARGET, TARGET, original));
        Object manager = manager(loader);
        configure(loader, manager, 100);
        Method ensure = manager.getClass().getDeclaredMethod("ensureLevels"); ensure.setAccessible(true); ensure.invoke(manager);
        Field map = manager.getClass().getDeclaredField("totalExpLevelRequired"); map.setAccessible(true);
        Map<Integer, Long> defaults = (Map<Integer, Long>) map.get(manager);
        long[] thresholds = new long[100];
        for (int i = 1; i <= 100; i++) thresholds[i - 1] = defaults.get(i);
        AstralPerkCurve vanilla = new AstralPerkCurve(false, thresholds);
        AstralPerkHooks.activateServer(vanilla);
        AstralPerkHooks.CLIENT.begin(connection);
        AstralPerkHooks.CLIENT.accept(connection, AstralPerkCurve.decode(vanilla.encode()));
        EntityPlayer client = new EntityPlayer(); client.world.isRemote = true;
        assertEquals(3, level(manager, 380.3300207387656, client));
        assertEquals(5, level(manager, 743.9650483131409, client));
        assertEquals(16, level(manager, 6396, client));
    }

    private static int level(Object manager, double xp, EntityPlayer player) throws Exception {
        return ((Number) manager.getClass().getMethod("getLevel", double.class, EntityPlayer.class).invoke(manager, xp, player)).intValue();
    }
    private static Object manager(Loader loader) throws Exception { return loader.loadClass(TARGET).getField("INSTANCE").get(null); }
    private static void configure(Loader loader, Object manager, int cap) throws Exception {
        Class<?> type = loader.loadClass("net.minecraftforge.common.config.Configuration"); Object cfg = type.newInstance(); type.getField("cap").setInt(cfg, cap);
        manager.getClass().getMethod("loadFromConfig", type).invoke(manager, cfg);
    }

    private Loader loader(byte[] target) throws Exception {
        Loader loader = new Loader();
        Map<String, String> names = new HashMap<>();
        names.put(Type.getInternalName(Entry.class), "hellfirepvp/astralsorcery/common/data/config/entry/ConfigEntry");
        names.put(Type.getInternalName(Entry.Section.class), "hellfirepvp/astralsorcery/common/data/config/entry/ConfigEntry$Section");
        names.put(Type.getInternalName(Mods.class), "hellfirepvp/astralsorcery/common/base/Mods");
        names.put(Type.getInternalName(StageData.class), "net/darkhax/gamestages/data/IStageData");
        names.put(Type.getInternalName(Stages.class), "TestStages");
        names.put(Type.getInternalName(StageHelper.class), "net/darkhax/gamestages/GameStageHelper");
        names.put(Type.getInternalName(StageTweaks.class), "hellfirepvp/astralsorcery/common/integrations/mods/crafttweaker/tweaks/GameStageTweaks");
        names.put(Type.getInternalName(MathHelper.class), "net/minecraft/util/math/MathHelper");
        names.put(Type.getInternalName(Configuration.class), "net/minecraftforge/common/config/Configuration");
        for (Class<?> type : new Class<?>[]{Entry.class, Entry.Section.class, Mods.class, StageData.class, Stages.class, StageHelper.class, StageTweaks.class, MathHelper.class, Configuration.class}) {
            ClassReader reader = new ClassReader(read(type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")));
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, new SimpleRemapper(names)), 0);
            loader.definitions.put(names.get(Type.getInternalName(type)).replace('/', '.'), writer.toByteArray());
        }
        loader.definitions.put(TARGET, target);
        try (JarFile jar = new JarFile(System.getProperty("ecpatches.astralJar"))) {
            ClassNode progress = node(read(jar.getInputStream(jar.getJarEntry("hellfirepvp/astralsorcery/common/data/research/PlayerProgress.class"))));
            progress.methods.removeIf(v -> !Arrays.asList("getPerkExp", "modifyExp", "getAvailablePerkPoints", "hasFreeAllocationPoint").contains(((MethodNode) v).name));
            progress.fields.removeIf(v -> !Arrays.asList("perkExp", "appliedPerks", "freePointTokens").contains(((FieldNode) v).name));
            MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.instructions.add(new VarInsnNode(ALOAD, 0));
            constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
            for (String field : new String[]{"appliedPerks", "freePointTokens"}) {
                boolean set = field.equals("appliedPerks");
                String type = set ? "java/util/HashSet" : "java/util/ArrayList";
                constructor.instructions.add(new VarInsnNode(ALOAD, 0));
                constructor.instructions.add(new TypeInsnNode(NEW, type));
                constructor.instructions.add(new InsnNode(DUP));
                constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, type, "<init>", "()V", false));
                constructor.instructions.add(new FieldInsnNode(PUTFIELD, progress.name, field, set ? "Ljava/util/Set;" : "Ljava/util/List;"));
            }
            constructor.instructions.add(new InsnNode(RETURN));
            progress.methods.add(constructor);
            loader.definitions.put(progress.name.replace('/', '.'), bytes(progress));
        }
        return loader;
    }

    private static class Loader extends ClassLoader {
        final Map<String, byte[]> definitions = new HashMap<>();
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) { byte[] data = definitions.get(name); type = defineClass(name, data, 0, data.length); }
            if (resolve) resolveClass(type);
            return type;
        }
    }
}
