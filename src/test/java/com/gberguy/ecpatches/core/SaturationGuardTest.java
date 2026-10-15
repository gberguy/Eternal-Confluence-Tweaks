package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.ScalingFeastSaturationHudPatch;
import com.gberguy.ecpatches.runtime.SaturationGuardEvents;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SaturationGuardTest implements Opcodes {
    private static final ScalingFeastSaturationHudPatch PATCH = new ScalingFeastSaturationHudPatch();
    private static final String HUD = "yeelp/scalingfeast/hud/";
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repairsNegativeStateOnBothLogicalSidesAtTickEnd(boolean client) throws Exception {
        EntityPlayer player = new EntityPlayer();
        player.world.isRemote = client;
        FoodStats stats = player.getFoodStats();
        SaturationGuardEvents handler = new SaturationGuardEvents(settings(""));
        for (float saturation : new float[]{-Float.MIN_VALUE, -0.5F, -20, -40, -60, -Float.MAX_VALUE}) {
            stats.setFoodSaturationLevel(saturation);
            int writes = stats.saturationWrites;
            handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
            assertEquals(saturation, stats.getSaturationLevel());
            assertEquals(writes, stats.saturationWrites);
            handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            assertEquals(0.0F, stats.getSaturationLevel());
            assertEquals(writes + 1, stats.saturationWrites);
            handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            assertEquals(writes + 1, stats.saturationWrites);
        }
    }

    @Test
    void preservesNonnegativeSaturationWithoutWritingOrCappingIt() throws Exception {
        EntityPlayer player = new EntityPlayer();
        SaturationGuardEvents handler = new SaturationGuardEvents(settings(""));
        for (float saturation : new float[]{-0.0F, 0, Float.MIN_VALUE, 0.5F, 20, 25, 100, Float.MAX_VALUE}) {
            player.getFoodStats().setFoodSaturationLevel(saturation);
            int writes = player.getFoodStats().saturationWrites;
            handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            assertEquals(Float.floatToIntBits(saturation), Float.floatToIntBits(player.getFoodStats().getSaturationLevel()));
            assertEquals(writes, player.getFoodStats().saturationWrites);
            assertTrue(SaturationGuardEvents.shouldDrawSaturation(player));
        }
    }

    @Test
    void switchesAreIndependentInAllFourCombinationsAndSurviveConfigRewrite() throws Exception {
        byte[] original = fixture(PATCH.className.replace('.', '/'));
        for (boolean clamp : new boolean[]{false, true}) {
            for (boolean hud : new boolean[]{false, true}) {
                PatchSettings settings = settings("clampNegativeSaturation=" + clamp
                        + "\nscalingFeastSafeSaturationHud=" + hud + "\n");
                assertEquals(clamp, settings.enabled(PatchId.PLAYER_SATURATION));
                assertEquals(hud, settings.enabled(PatchId.SCALING_FEAST_SATURATION_HUD));
                PatchSettings rewritten = PatchSettings.read(directory.resolve("patches.cfg").toFile());
                assertEquals(clamp, rewritten.enabled(PatchId.PLAYER_SATURATION));
                assertEquals(hud, rewritten.enabled(PatchId.SCALING_FEAST_SATURATION_HUD));
                byte[] output = new PatchTransformer(settings).transform(PATCH.className, PATCH.className, original);
                assertEquals(hud, output != original);
                EntityPlayer player = new EntityPlayer();
                player.getFoodStats().setFoodSaturationLevel(-40);
                new SaturationGuardEvents(settings).onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                assertEquals(clamp ? 0.0F : -40.0F, player.getFoodStats().getSaturationLevel());
            }
        }
    }

    @Test
    void disabledCorrectionLeavesNegativeStateUntouchedWithoutOptionalMods() throws Exception {
        EntityPlayer player = new EntityPlayer();
        player.getFoodStats().setFoodSaturationLevel(-60);
        SaturationGuardEvents handler = new SaturationGuardEvents(settings("clampNegativeSaturation=false\n"));
        handler.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        assertEquals(-60.0F, player.getFoodStats().getSaturationLevel());
        assertFalse(SaturationGuardEvents.shouldDrawSaturation(player));
        assertEquals(-60.0F, player.getFoodStats().getSaturationLevel());
    }

    @Test
    void exactReleaseTransformsOncePreservesOtherMethodsAndVerifies() throws Exception {
        byte[] original = fixture(PATCH.className.replace('.', '/'));
        PatchTransformer transformer = new PatchTransformer(settings(""));
        byte[] output = transformer.transform(PATCH.className, PATCH.className, original);
        assertNotSame(original, output);
        new ClassReader(output).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        assertEquals(otherMethods(node(original)), otherMethods(node(output)));
        assertSame(output, transformer.transform(PATCH.className, PATCH.className, output));
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, PATCH.apply(node(output)));
    }

    @Test
    void unknownMissingAndAbsentTargetsAreLeftUntouched() throws Exception {
        PatchTransformer transformer = new PatchTransformer(settings(""));
        ClassNode changed = node(fixture(PATCH.className.replace('.', '/')));
        method(changed, "shouldDraw").instructions.insert(new InsnNode(NOP));
        byte[] input = bytes(changed);
        assertSame(input, transformer.transform(PATCH.className, PATCH.className, input));
        changed.methods.remove(method(changed, "shouldDraw"));
        input = bytes(changed);
        assertSame(input, transformer.transform(PATCH.className, PATCH.className, input));
        assertNull(transformer.transform(PATCH.className, PATCH.className, null));
        assertSame(input, transformer.transform("unrelated.Class", "unrelated.Class", input));
    }

    @Test
    void releasedHudDispatchReproducesNegativeIndexesAndSkipsThemBeforeAnyTickRepair() throws Exception {
        Class<?> original = harness(false);
        Class<?> fixed = harness(true);
        EntityPlayer player = new EntityPlayer();
        for (float saturation : new float[]{-40, -60}) {
            player.getFoodStats().setFoodSaturationLevel(saturation);
            int[] draws = {0};
            assertThrows(IndexOutOfBoundsException.class, () -> dispatch(original, player, draws));
            assertEquals(1, draws[0]);
            draws[0] = 0;
            dispatch(fixed, player, draws);
            assertEquals(0, draws[0]);
            assertEquals(saturation, player.getFoodStats().getSaturationLevel());
            assertEquals(false, invoke(fixed.getMethod("shouldDrawLower", Object.class, EntityPlayer.class), null, player));
        }
        for (float saturation : new float[]{-Float.MIN_VALUE, -0.5F, -20, -Float.MAX_VALUE}) {
            player.getFoodStats().setFoodSaturationLevel(saturation);
            int[] draws = {0};
            dispatch(fixed, player, draws);
            assertEquals(0, draws[0]);
        }
    }

    @Test
    void releasedPositiveColourRotationAndAmountsRemainUnchangedIncludingAboveTwenty() throws Exception {
        Class<?> original = harness(false);
        Class<?> fixed = harness(true);
        List<?> palette = (List<?>) original.getField("SAT_COLOURS").get(null);
        fixed.getField("SAT_COLOURS").set(null, palette);
        EntityPlayer player = new EntityPlayer();
        for (float saturation : new float[]{0, 0.5F, 19.9F, 20, 20.01F, 25, 40, 100, 140, 160}) {
            player.getFoodStats().setFoodSaturationLevel(saturation);
            int[] draws = {0};
            dispatch(fixed, player, draws);
            assertEquals(1, draws[0]);
            assertSame(invoke(original.getMethod("getSaturationColour", float.class), saturation),
                    invoke(fixed.getMethod("getSaturationColour", float.class), saturation));
            assertEquals(invoke(original.getMethod("amount", Object.class, EntityPlayer.class, FoodStats.class), null, player, player.getFoodStats()),
                    invoke(fixed.getMethod("amount", Object.class, EntityPlayer.class, FoodStats.class), null, player, player.getFoodStats()));
        }
    }

    public interface Drawable {
        boolean shouldDraw(EntityPlayer player);
        void draw(Object minecraft, EntityPlayer player, int x, int y);
    }

    private static void dispatch(Class<?> harness, EntityPlayer player, int[] draws) throws Exception {
        Drawable drawable = new Drawable() {
            public boolean shouldDraw(EntityPlayer value) {
                return (Boolean) invokeUnchecked(harness, "shouldDraw", new Class<?>[]{Object.class, EntityPlayer.class}, null, value);
            }
            public void draw(Object minecraft, EntityPlayer value, int x, int y) {
                draws[0]++;
                invokeUnchecked(harness, "getSaturationColour", new Class<?>[]{float.class}, value.getFoodStats().getSaturationLevel());
            }
        };
        invoke(harness.getMethod("dispatch", EntityPlayer.class, Object.class, int.class, int.class, Drawable.class), player, null, 0, 0, drawable);
    }

    private static Object invokeUnchecked(Class<?> type, String name, Class<?>[] parameters, Object... values) {
        try {
            return invoke(type.getMethod(name, parameters), values);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static Object invoke(Method method, Object... values) throws Exception {
        try {
            return method.invoke(null, values);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception) throw (Exception) error.getCause();
            throw (Error) error.getCause();
        }
    }

    private static Class<?> harness(boolean patched) throws Exception {
        ClassNode upper = node(fixture(PATCH.className.replace('.', '/')));
        if (patched) assertEquals(MethodPatch.Result.APPLIED, PATCH.apply(upper));
        ClassNode harness = new ClassNode();
        harness.version = V1_8;
        harness.access = ACC_PUBLIC;
        harness.name = "SaturationHudHarness";
        harness.superName = "java/lang/Object";
        harness.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "SAT_COLOURS", "Ljava/util/List;", null, null));
        MethodNode shouldDraw = method(upper, "shouldDraw");
        shouldDraw.desc = "(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayer;)Z";
        harness.methods.add(shouldDraw);
        MethodNode amount = method(upper, "getAmountToDraw");
        amount.name = "amount";
        amount.desc = "(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/FoodStats;)F";
        harness.methods.add(amount);
        MethodNode lower = method(node(fixture(HUD + "ScalingFeastLowerSaturationBarDrawable")), "shouldDraw");
        lower.name = "shouldDrawLower";
        lower.desc = shouldDraw.desc;
        harness.methods.add(lower);
        ClassNode colours = node(fixture(HUD + "DrawUtils"));
        harness.methods.add(method(colours, "getColourForBar"));
        harness.methods.add(method(colours, "getSaturationColour"));
        MethodNode dispatch = method(node(fixture("yeelp/scalingfeast/handlers/HUDOverlayHandler")), "lambda$drawDrawables$0");
        dispatch.name = "dispatch";
        harness.methods.add(dispatch);
        for (Object value : harness.methods) {
            MethodNode method = (MethodNode) value;
            method.access = ACC_PUBLIC | ACC_STATIC;
            method.localVariables = null;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.equals("func_71024_bL")) call.name = "getFoodStats";
                if (call.name.equals("func_75115_e")) call.name = "getSaturationLevel";
            }
        }
        Map<String, String> names = new HashMap<>();
        names.put(HUD + "DrawUtils", harness.name);
        names.put("yeelp/scalingfeast/util/Colour", "java/lang/Object");
        names.put(HUD + "IDrawable", Type.getInternalName(Drawable.class));
        names.put("net/minecraft/client/Minecraft", "java/lang/Object");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        harness.accept(new ClassRemapper(writer, new SimpleRemapper(names)));
        Class<?> result = new ClassLoader(SaturationGuardTest.class.getClassLoader()) {
            Class<?> define() { byte[] code = writer.toByteArray(); return defineClass(null, code, 0, code.length); }
        }.define();
        result.getField("SAT_COLOURS").set(null, Arrays.asList(new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object()));
        return result;
    }

    private PatchSettings settings(String contents) throws Exception {
        Path path = directory.resolve("patches.cfg");
        Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        return PatchSettings.read(path.toFile());
    }

    private static byte[] fixture(String name) throws Exception {
        String path = System.getProperty("ecpatches.scalingFeastJar");
        assumeTrue(path != null, "Scaling Feast release fixture not supplied");
        try (JarFile jar = new JarFile(path); InputStream input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
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

    private static MethodNode method(ClassNode node, String name) {
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (method.name.equals(name)) return method;
        }
        throw new AssertionError("Missing method " + name);
    }

    private static Map<String, String> otherMethods(ClassNode node) {
        Map<String, String> methods = new LinkedHashMap<>();
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!method.name.equals("shouldDraw")) methods.put(method.name + method.desc, MethodFingerprint.of(method));
        }
        return methods;
    }
}
