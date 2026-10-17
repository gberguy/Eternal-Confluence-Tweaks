package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.VfpFoodPatch;
import com.gberguy.ecpatches.core.patches.VfpFoodValuesPatch;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.jupiter.api.*;
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

public class VfpFoodTest implements Opcodes {
    private static final VfpFoodPatch PATCH = new VfpFoodPatch();
    private static final VfpFoodValuesPatch VALUES_PATCH = new VfpFoodValuesPatch();
    private static final String OWNER = PATCH.className.replace('.', '/');
    private static final String HELPER = "com/gberguy/ecpatches/runtime/VfpFoodValues";
    @TempDir Path directory;

    @BeforeEach
    void reset() {
        Mods.applecore = true;
        Glue.effects = 0;
        LegacyPortion.values = null;
    }

    @Test
    void exactReleaseTransformsEachFixOnceAndPreservesUnrelatedMethods() throws Exception {
        byte[] input = fixture("vfpJar", OWNER);
        ClassNode original = node(input);
        byte[] output = transformer("").transform(PATCH.className, PATCH.className, input);
        assertNotSame(input, output);
        new ClassReader(output).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, PATCH.apply(node(output)));
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, VALUES_PATCH.apply(node(output)));
        assertSame(output, transformer("").transform(PATCH.className, PATCH.className, output));
        assertSame(input, transformer("vanillaFoodPantryServings=false\n").transform(PATCH.className, PATCH.className, input));
        for (Object value : original.methods) {
            MethodNode method = (MethodNode) value;
            if (affected(method.name)) continue;
            MethodNode match = null;
            for (Object candidate : node(output).methods) {
                MethodNode other = (MethodNode) candidate;
                if (method.name.equals(other.name) && method.desc.equals(other.desc)) match = other;
            }
            assertNotNull(match);
            assertEquals(MethodFingerprint.of(method), MethodFingerprint.of(match));
        }
        for (String name : Arrays.asList("func_77654_b", "getFoodValues", "onPortionConsumed")) {
            ClassNode changed = node(input);
            find(changed, name).instructions.insert(new InsnNode(NOP));
            byte[] bytes = bytes(changed);
            MethodPatch patch = name.equals("func_77654_b") ? PATCH : VALUES_PATCH;
            assertEquals(MethodPatch.Result.UNSUPPORTED, patch.apply(changed));
            assertArrayEquals(bytes, bytes(changed));
            changed.methods.remove(find(changed, name));
            bytes = bytes(changed);
            assertEquals(MethodPatch.Result.UNSUPPORTED, patch.apply(changed));
            assertArrayEquals(bytes, bytes(changed));
        }
    }

    @Test
    void developmentNamesAndConfigurationKeepThePatchIndependent() throws Exception {
        ClassNode source = node(fixture("vfpJar", OWNER));
        development(source);
        assertEquals(MethodPatch.Result.APPLIED, PATCH.apply(source));
        assertEquals(MethodPatch.Result.APPLIED, VALUES_PATCH.apply(source));
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, PATCH.apply(source));
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, VALUES_PATCH.apply(source));
        new ClassReader(bytes(source)).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        Path config = directory.resolve("options.cfg");
        Files.write(config, "vanillaFoodPantryServings=false\nclampNegativeSaturation=false\n".getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(config.toFile());
        assertFalse(settings.enabled(PatchId.VFP_FOOD));
        assertFalse(settings.enabled(PatchId.VFP_FOOD_VALUES));
        assertFalse(settings.enabled(PatchId.PLAYER_SATURATION));
        assertTrue(settings.enabled(PatchId.NUTRITION_LUNCHBOX));
        assertTrue(settings.enabled(PatchId.SCALING_FEAST_SATURATION_HUD));
        assertFalse(PatchSettings.read(config.toFile()).enabled(PatchId.VFP_FOOD));
    }

    @Test
    void allFourToggleCombinationsTransformOnlyTheirOwnMethodsAndSurviveConfigRewrite() throws Exception {
        byte[] original = fixture("vfpJar", OWNER);
        for (boolean hunger : new boolean[]{false, true}) {
            for (boolean values : new boolean[]{false, true}) {
                String text = "vanillaFoodPantryServings=" + hunger + "\nvanillaFoodPantryFoodValues=" + values + "\n";
                Path config = directory.resolve("matrix-" + hunger + "-" + values + ".cfg");
                Files.write(config, text.getBytes(StandardCharsets.UTF_8));
                PatchSettings settings = PatchSettings.read(config.toFile());
                PatchSettings reread = PatchSettings.read(config.toFile());
                assertEquals(hunger, reread.enabled(PatchId.VFP_FOOD));
                assertEquals(values, reread.enabled(PatchId.VFP_FOOD_VALUES));
                byte[] output = new PatchTransformer(settings).transform(PATCH.className, PATCH.className, original);
                ClassNode result = node(output);
                for (String name : Arrays.asList("func_77654_b", "getFoodValues", "onPortionConsumed")) {
                    boolean changed = !MethodFingerprint.of(find(node(original), name)).equals(MethodFingerprint.of(find(result, name)));
                    assertEquals(name.equals("func_77654_b") ? hunger : values, changed, text + name);
                }
                assertSame(output, new PatchTransformer(settings).transform(PATCH.className, PATCH.className, output));
                Harness harness = harness(hunger, values, true);
                Player player = new Player();
                player.stats.level = 24;
                player.maximum = 30;
                harness.finish(food(harness.pantry, 14, 5, 0), player);
                assertEquals(hunger ? 6 : -4, player.stats.lastHunger);
                Player smallServing = new Player();
                smallServing.stats.level = 19;
                ItemStack history = food(harness.pantry, 14, 5, 0);
                smallServing.stats.during = (food, consumed) -> assertEquals(values, harness.hearty(history));
                harness.finish(food(harness.pantry, 14, 5, 0), smallServing);
                assertEquals(1, smallServing.stats.lastHunger);
            }
        }
    }

    @Test
    void unsupportedNutritionPairDoesNotPreventHungerFixAndViceVersa() throws Exception {
        for (String name : Arrays.asList("func_77654_b", "getFoodValues", "onPortionConsumed")) {
            ClassNode changed = node(fixture("vfpJar", OWNER));
            find(changed, name).instructions.insert(new InsnNode(NOP));
            boolean hungerChanged = name.equals("func_77654_b");
            assertEquals(hungerChanged ? MethodPatch.Result.UNSUPPORTED : MethodPatch.Result.APPLIED, PATCH.apply(changed));
            assertEquals(hungerChanged ? MethodPatch.Result.APPLIED : MethodPatch.Result.UNSUPPORTED, VALUES_PATCH.apply(changed));
        }
    }

    @Test
    void legacyDisabledSwitchKeepsBothFixesDisabledUntilExplicitlyConfigured() throws Exception {
        Path config = directory.resolve("legacy.cfg");
        Files.write(config, ("# Target: Vanilla Food Pantry\n"
                + "# Fixes multi-use servings with expanded hunger and isolates temporary serving nutrition from other food queries.\n"
                + "vanillaFoodPantryServings=false\n").getBytes(StandardCharsets.UTF_8));
        PatchSettings settings = PatchSettings.read(config.toFile());
        assertFalse(settings.enabled(PatchId.VFP_FOOD));
        assertFalse(settings.enabled(PatchId.VFP_FOOD_VALUES));
        String rendered = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(rendered.contains("vanillaFoodPantryFoodValues=false"));
        assertFalse(rendered.contains("expanded hunger and isolates"));
        PatchSettings.read(config.toFile());
        assertEquals(rendered, new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
    }

    @Test
    void namedFoodsInheritSharedFinishAndFoodValuesWhileKeepingTheirOwnEffects() throws Exception {
        for (String name : Arrays.asList("wheat/Breads", "wheat/DessertPies", "wheat/PocketSandwiches",
                "misc/Sandwiches", "misc/DeckerSandwiches", "misc/Tatoes", "misc/StewedItems", "eggs/EggPies", "meats/FishPies")) {
            String current = "org/jwaresoftware/mcmods/vfp/" + name;
            while (!current.equals(OWNER)) {
                ClassNode type = node(fixture("vfpJar", current));
                for (Object value : type.methods) {
                    String method = ((MethodNode) value).name;
                    assertFalse(affected(method), current + " overrides " + method);
                }
                current = type.superName;
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void expandedHungerUsesPositiveServingsAndTracksStoredFoodOnBothSides(boolean client) throws Exception {
        Harness harness = harness(true);
        Player player = new Player();
        player.world.isRemote = client;
        player.maximum = 30;
        player.stats.level = 24;
        ItemStack stack = food(harness.pantry, 14, 5, 0);
        assertSame(stack, harness.finish(stack, player));
        assertEquals(30, player.stats.level);
        assertEquals(6, player.stats.lastHunger);
        assertEquals(client ? 0 : 6, used(stack));
        assertEquals(1, harness.pantry.eaten);
        assertEquals(1, player.stats.events);
        assertNull(harness.portion(stack));
    }

    @Test
    void originalNegativeServingIsReproducedAndFullHungerBecomesANoop() throws Exception {
        Harness original = harness(false);
        Player player = new Player();
        player.maximum = 30;
        player.stats.level = 24;
        ItemStack stack = food(original.pantry, 14, 5, 0);
        original.finish(stack, player);
        assertEquals(20, player.stats.level);
        assertEquals(-4, player.stats.lastHunger);
        assertEquals(0, used(stack));
        Harness fixed = harness(true);
        for (int current : new int[]{20, 24, 30, 35}) {
            player = new Player();
            player.maximum = current == 20 ? 20 : 24;
            player.stats.level = current;
            stack = food(fixed.pantry, 14, 5, 0);
            int effects = Glue.effects;
            int eaten = fixed.pantry.eaten;
            assertSame(stack, fixed.finish(stack, player));
            assertEquals(current, player.stats.level);
            assertEquals(0, player.stats.events);
            assertEquals(0, used(stack));
            assertEquals(effects, Glue.effects);
            assertEquals(eaten, fixed.pantry.eaten);
        }
    }

    @Test
    void partialLastServingsContainersLowHealthAndCreativeKeepOriginalRules() throws Exception {
        Harness fixed = harness(true);
        Player player = new Player();
        player.stats.level = 10;
        ItemStack last = food(fixed.pantry, 14, 5, 12);
        ItemStack result = fixed.finish(last, player);
        assertEquals(2, player.stats.lastHunger);
        assertEquals(0, last.getCount());
        assertSame(fixed.pantry.container, result);
        assertEquals(1, fixed.pantry.finished);
        player = new Player();
        player.health = 10;
        player.stats.level = 19;
        ItemStack stack = food(fixed.pantry, 40, 5, 0);
        fixed.finish(stack, player);
        assertEquals(5, player.stats.lastHunger);
        assertEquals(5, used(stack));
        player = new Player();
        player.health = 10;
        player.stats.level = 0;
        fixed.finish(food(fixed.pantry, 40, 5, 0), player);
        assertEquals(16, player.stats.lastHunger);
        player = new Player();
        player.creative = true;
        player.stats.level = 10;
        stack = food(fixed.pantry, 14, 5, 12);
        assertSame(stack, fixed.finish(stack, player));
        assertEquals(1, stack.getCount());
        assertEquals(2, player.stats.lastHunger);
    }

    @Test
    void invalidFoodSupplyIsInertAndOrdinaryFoodsKeepTheirConsumptionPath() throws Exception {
        Harness fixed = harness(true);
        for (int[] tags : new int[][]{{0, 5, 0}, {14, 5, 14}, {14, 5, 18}, {14, 5, -1}}) {
            Player player = new Player();
            player.stats.level = 10;
            ItemStack stack = food(fixed.pantry, tags[0], tags[1], tags[2]);
            assertSame(stack, fixed.finish(stack, player));
            assertEquals(0, player.stats.events);
            assertEquals(tags[2], used(stack));
        }
        Player player = new Player();
        player.stats.level = 10;
        ItemStack ordinary = new ItemStack(fixed.pantry, 2, 7);
        fixed.finish(ordinary, player);
        assertEquals(1, ordinary.getCount());
        assertEquals(5, player.stats.lastHunger);
        assertEquals(1, player.stats.events);
    }

    @Test
    void alwaysEdibleAndThirstEligibleFoodsGetRealServingsAtFullHunger() throws Exception {
        Harness fixed = harness(true);
        for (boolean always : new boolean[]{false, true}) {
            fixed.pantry.always = always;
            fixed.pantry.thirsty = !always;
            Player player = new Player();
            ItemStack stack = food(fixed.pantry, 14, 5, 0);
            fixed.finish(stack, player);
            assertEquals(5, player.stats.lastHunger);
            assertEquals(5, used(stack));
            assertEquals(1, player.stats.events);
        }
    }

    @Test
    void consumptionValuesAreScopedByIdentityAndPreserveDietaryModifiers() throws Exception {
        Harness fixed = harness(true);
        Player player = new Player();
        player.maximum = 30;
        player.stats.level = 29;
        player.stats.modifier = 0.5F;
        ItemStack stack = food(fixed.pantry, 14, 5, 0);
        ItemStack historyCopy = stack.copy();
        ItemStack other = food(fixed.pantry, 50, 9, 0);
        player.stats.during = (food, consumed) -> {
            assertSame(stack, consumed);
            assertEquals(1, fixed.hunger(consumed));
            assertEquals(5, fixed.hunger(historyCopy));
            assertEquals(9, fixed.hunger(other));
            assertNull(fixed.portion(historyCopy));
            assertNotNull(fixed.portion(consumed));
        };
        fixed.finish(stack, player);
        assertEquals(0, player.stats.lastHunger);
        assertEquals(1, used(stack));
        assertEquals(1, player.stats.events);
        assertEquals(5, fixed.hunger(stack));
        assertNull(fixed.portion(stack));
    }

    @Test
    void nestedConsumptionsExceptionsAndOtherThreadsCannotLeakServingValues() throws Exception {
        Harness fixed = harness(true);
        Player outer = new Player();
        outer.stats.level = 19;
        ItemStack stack = food(fixed.pantry, 14, 5, 0);
        ItemStack nested = food(fixed.pantry, 40, 8, 0);
        Player inner = new Player();
        inner.stats.level = 18;
        inner.stats.during = (food, consumed) -> {
            assertEquals(2, fixed.hunger(nested));
            assertEquals(5, fixed.hunger(stack));
            throw new IllegalStateException("listener failure");
        };
        outer.stats.during = (food, consumed) -> {
            assertEquals(1, fixed.hunger(stack));
            assertThrows(InvocationTargetException.class, () -> fixed.finish(nested, inner));
            assertEquals(1, fixed.hunger(stack));
            java.util.concurrent.FutureTask<Integer> query = new java.util.concurrent.FutureTask<>(() -> fixed.hunger(stack));
            new Thread(query).start();
            try { assertEquals(5, query.get().intValue()); }
            catch (Exception error) { throw new AssertionError(error); }
        };
        fixed.finish(stack, outer);
        assertNull(fixed.portion(stack));
        assertNull(fixed.portion(nested));
        outer.stats.level = 19;
        outer.stats.during = (food, consumed) -> { throw new IllegalStateException("outer failure"); };
        assertThrows(InvocationTargetException.class, () -> fixed.finish(stack, outer));
        assertNull(fixed.portion(stack));
    }

    @Test
    void absentAppleCoreAndUnavailableApiFallBackWithoutOptionalClassLoading() throws Exception {
        Mods.applecore = false;
        Harness fixed = harness(true, false);
        Player player = new Player();
        player.stats.level = 19;
        ItemStack stack = food(fixed.pantry, 14, 5, 0);
        fixed.finish(stack, player);
        assertEquals(20, player.stats.level);
        assertEquals(1, used(stack));
        assertEquals(0, player.stats.events);
        Mods.applecore = true;
        assertEquals(20, fixed.maximum(player));
    }

    @Test
    void solCarrotReleasedEligibilityNoLongerSeesAnotherFoodsSmallServing() throws Exception {
        for (boolean patched : new boolean[]{false, true}) {
            Harness harness = harness(patched);
            Player player = new Player();
            player.stats.level = 19;
            ItemStack stack = food(harness.pantry, 14, 5, 0);
            ItemStack history = food(harness.pantry, 14, 5, 0);
            player.stats.during = (food, consumed) -> assertEquals(patched, harness.hearty(history));
            harness.finish(stack, player);
            assertTrue(harness.hearty(history));
        }
    }

    @Test
    void productionHelperResolvesAgainstReleasedClientAndDedicatedServerApis() throws Exception {
        ClassNode helper = node(fixture("releaseJar", HELPER));
        for (String binary : Arrays.asList("minecraftSrg", "minecraftServerSrg")) {
            assumeTrue(System.getProperty("ecpatches." + binary) != null, binary + " fixture not supplied");
            for (Object value : helper.methods) {
                for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (!call.owner.startsWith("net/minecraft/")) continue;
                    ClassNode owner = node(fixture(binary, call.owner));
                    assertTrue(owner.methods.stream().anyMatch(candidate -> {
                        MethodNode method = (MethodNode) candidate;
                        return method.name.equals(call.name) && method.desc.equals(call.desc);
                    }), binary + ": missing " + call.owner + "." + call.name + call.desc);
                }
            }
        }
    }

    private PatchTransformer transformer(String text) throws Exception {
        Path file = directory.resolve(UUID.randomUUID() + ".cfg");
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return new PatchTransformer(PatchSettings.read(file.toFile()));
    }

    private static boolean affected(String name) {
        return name.equals("func_77654_b") || name.equals("onItemUseFinish") || name.equals("getFoodValues") || name.equals("onPortionConsumed");
    }

    private static byte[] fixture(String property, String owner) throws Exception {
        String path = System.getProperty("ecpatches." + property);
        assumeTrue(path != null, property + " fixture not supplied");
        try (JarFile jar = new JarFile(path); InputStream input = jar.getInputStream(jar.getJarEntry(owner + ".class"))) {
            return read(input);
        }
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
        return bytes.toByteArray();
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static MethodNode find(ClassNode node, String name) {
        for (Object value : node.methods) if (((MethodNode) value).name.equals(name)) return (MethodNode) value;
        throw new AssertionError("Missing " + name);
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void development(ClassNode node) {
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            method.name = MinecraftNames.runtime(method.name, true);
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    call.name = MinecraftNames.runtime(call.name, true);
                } else if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    field.name = MinecraftNames.runtime(field.name, true);
                }
            }
        }
    }

    private Harness harness(boolean patched) throws Exception { return harness(patched, true); }

    private Harness harness(boolean patched, boolean api) throws Exception {
        return harness(patched, patched, api);
    }

    private Harness harness(boolean hunger, boolean values, boolean api) throws Exception {
        Map<String, String> types = new HashMap<>();
        types.put(OWNER, "VfpPantryHarness");
        types.put("net/minecraft/entity/player/EntityPlayer", Type.getInternalName(Player.class));
        types.put("net/minecraft/util/FoodStats", Type.getInternalName(Stats.class));
        types.put("net/minecraft/item/ItemFood", Type.getInternalName(Food.class));
        types.put("net/minecraft/world/IBlockAccess", Type.getInternalName(World.class));
        types.put("net/minecraftforge/fml/common/Loader", Type.getInternalName(Mods.class));
        types.put("org/jwaresoftware/mcmods/vfp/common/MinecraftGlue", Type.getInternalName(Glue.class));
        types.put("org/jwaresoftware/mcmods/vfp/common/MinecraftGlue$Effects", Type.getInternalName(Glue.class));
        types.put("org/jwaresoftware/mcmods/vfp/common/FoodPortion", Type.getInternalName(LegacyPortion.class));
        types.put("squeek/applecore/api/food/FoodValues", Type.getInternalName(Values.class));
        types.put("net/minecraft/stats/StatList", Type.getInternalName(Glue.class));
        types.put("net/minecraft/stats/StatBase", "java/lang/Object");
        FixtureLoader loader = new FixtureLoader(types);
        for (String suffix : new String[]{"", "$Serving", "$AppleCore"}) {
            try (InputStream input = getClass().getResourceAsStream("/" + HELPER + suffix + ".class")) {
                loader.add(node(read(input)));
            }
        }
        if (api) {
            ClassNode accessor = node(fixture("appleCoreJar", "squeek/applecore/api/IAppleCoreAccessor"));
            accessor.methods.removeIf(value -> {
                String name = ((MethodNode) value).name;
                return !name.equals("getMaxHunger") && !name.equals("getFoodValues");
            });
            loader.add(accessor);
            ClassNode apiType = node(fixture("appleCoreJar", "squeek/applecore/api/AppleCoreAPI"));
            apiType.methods.clear();
            apiType.fields.removeIf(value -> !((FieldNode) value).name.equals("accessor"));
            loader.add(apiType);
        }
        ClassNode source = node(fixture("vfpJar", OWNER));
        if (hunger) assertEquals(MethodPatch.Result.APPLIED, PATCH.apply(source));
        if (values) assertEquals(MethodPatch.Result.APPLIED, VALUES_PATCH.apply(source));
        development(source);
        source.methods.removeIf(value -> !affected(((MethodNode) value).name));
        source.fields.clear();
        source.interfaces.clear();
        source.innerClasses.clear();
        source.superName = Type.getInternalName(Pantry.class);
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, source.superName, "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(RETURN));
        source.methods.add(constructor);
        loader.add(source);
        ClassNode eligibility = node(fixture("solCarrotJar", "com/cazsius/solcarrot/tracking/ProgressInfo$ConfigInfo"));
        eligibility.methods.removeIf(value -> !((MethodNode) value).name.equals("isHearty"));
        eligibility.fields.removeIf(value -> !((FieldNode) value).name.equals("minimumFoodValue"));
        eligibility.innerClasses.clear();
        MethodNode init = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(ALOAD, 0));
        init.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new VarInsnNode(ALOAD, 0));
        init.instructions.add(new InsnNode(ICONST_3));
        init.instructions.add(new FieldInsnNode(PUTFIELD, eligibility.name, "minimumFoodValue", "I"));
        init.instructions.add(new InsnNode(RETURN));
        eligibility.methods.add(init);
        loader.add(eligibility);
        Class<?> helper = loader.loadClass(HELPER.replace('/', '.'));
        Pantry pantry = (Pantry) loader.loadClass("VfpPantryHarness").newInstance();
        Harness harness = new Harness(pantry, helper, loader);
        Class<?> config = loader.loadClass(eligibility.name.replace('/', '.'));
        harness.config = config.newInstance();
        harness.hearty = config.getDeclaredMethod("isHearty", ItemStack.class);
        harness.hearty.setAccessible(true);
        if (api) {
            Class<?> accessor = loader.loadClass("squeek.applecore.api.IAppleCoreAccessor");
            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{accessor}, (instance, method, arguments) -> {
                if (method.getName().equals("getMaxHunger")) return ((Player) arguments[0]).maximum;
                return harness.values((ItemStack) arguments[0]);
            });
            loader.loadClass("squeek.applecore.api.AppleCoreAPI").getField("accessor").set(null, proxy);
        }
        return harness;
    }

    private static final class FixtureLoader extends ClassLoader {
        final Map<String, byte[]> definitions = new HashMap<>();
        final Map<String, String> types;
        FixtureLoader(Map<String, String> types) { super(VfpFoodTest.class.getClassLoader()); this.types = types; }
        void add(ClassNode source) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            new ClassReader(bytes(source)).accept(new ClassRemapper(writer, new SimpleRemapper(types)), ClassReader.EXPAND_FRAMES);
            byte[] bytes = writer.toByteArray();
            definitions.put(new ClassReader(bytes).getClassName().replace('/', '.'), bytes);
        }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                byte[] bytes = definitions.get(name);
                type = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    private static final class Harness {
        final Pantry pantry;
        final Class<?> helper;
        final ClassLoader loader;
        Method hearty;
        Object config;
        Harness(Pantry pantry, Class<?> helper, ClassLoader loader) { this.pantry = pantry; this.helper = helper; this.loader = loader; }
        ItemStack finish(ItemStack stack, Player player) throws Exception {
            player.stats.lookup = this::values;
            return (ItemStack) pantry.getClass().getMethod("onItemUseFinish", ItemStack.class, World.class, EntityLivingBase.class)
                    .invoke(pantry, stack, player.world, player);
        }
        Object portion(ItemStack stack) {
            try { return helper.getMethod("portion", ItemStack.class).invoke(null, stack); }
            catch (Exception error) { throw new AssertionError(error); }
        }
        int maximum(Player player) throws Exception { return (Integer) helper.getMethod("maximumHunger", Player.class).invoke(null, player); }
        Values values(ItemStack stack) {
            try { return (Values) pantry.getClass().getMethod("getFoodValues", ItemStack.class).invoke(pantry, stack); }
            catch (Exception error) { throw new AssertionError(error); }
        }
        int hunger(ItemStack stack) { return values(stack).hunger; }
        boolean hearty(ItemStack stack) {
            try { return (Boolean) hearty.invoke(config, stack); }
            catch (Exception error) { throw new AssertionError(error); }
        }
    }

    private static ItemStack food(Item item, int total, int portion, int used) {
        ItemStack stack = new ItemStack(item, 1, 407);
        stack.tag.put("FoodTotal", String.valueOf(total));
        stack.tag.put("FoodPortion", String.valueOf(portion));
        stack.tag.put("FoodUsed", String.valueOf(used));
        return stack;
    }
    private static int used(ItemStack stack) { return Integer.parseInt(stack.tag.get("FoodUsed")); }

    public static class Food extends Item {
        final int hunger;
        final float saturation;
        public Food(int hunger, float saturation, boolean wolf) { this.hunger = hunger; this.saturation = saturation; }
        public int getHealAmount(ItemStack stack) { return hunger; }
        public float getSaturationModifier(ItemStack stack) { return saturation; }
    }
    public static class Pantry extends Food {
        int eaten, finished;
        boolean always, thirsty;
        final ItemStack container = new ItemStack(new Item(), 1, 0);
        public Pantry() { super(5, 0.5F, false); }
        public boolean isMultiuse(ItemStack stack) { return stack.tag.containsKey("FoodTotal"); }
        public int getPortionFoodPoints(ItemStack stack) { return Integer.parseInt(stack.tag.getOrDefault("FoodPortion", "5")); }
        public int getFoodPointsUsed(ItemStack stack) { return used(stack); }
        public int getTotalFoodPoints(ItemStack stack) { return Integer.parseInt(stack.tag.get("FoodTotal")); }
        public boolean setFoodPointsUsed(ItemStack stack, int amount) {
            stack.tag.put("FoodUsed", String.valueOf(Math.max(0, Math.min(getTotalFoodPoints(stack), amount))));
            return false;
        }
        @Override public int getHealAmount(ItemStack stack) {
            return isMultiuse(stack) ? Math.min(getPortionFoodPoints(stack), getTotalFoodPoints(stack) - used(stack)) : 5;
        }
        public boolean canConsume(ItemStack stack, World world, Player player) { return always || thirsty || player.stats.level < player.maximum; }
        public void onFoodEaten(ItemStack original, ItemStack result, World world, Player player) { eaten++; }
        public ItemStack onItemConsumedFully(ItemStack original, ItemStack result, World world, Player player) {
            finished++;
            return player.creative ? result : container;
        }
    }
    public static class Player extends EntityLivingBase {
        final Stats stats = new Stats(this);
        int maximum = 20;
        float health = 20;
        boolean creative;
        public Stats getFoodStats() { return stats; }
        public float getHealth() { return health; }
        public float getMaxHealth() { return 20; }
        public void addStat(Object stat) {}
    }
    public static class Stats {
        final Player player;
        int level = 20, events, lastHunger;
        float modifier = 1;
        java.util.function.Function<ItemStack, Values> lookup;
        BiConsumer<Food, ItemStack> during;
        Stats(Player player) { this.player = player; }
        public int getFoodLevel() { return level; }
        public void addStats(int hunger, float saturation) { lastHunger = hunger; level = Math.min(player.maximum, level + hunger); }
        public void addStats(Food food, ItemStack stack) {
            Values values = lookup.apply(stack);
            addStats((int) (values.hunger * modifier), values.saturationModifier * modifier);
            events++;
            if (during != null) during.accept(food, stack);
        }
    }
    public static final class Values {
        public final int hunger;
        public final float saturationModifier;
        public Values(int hunger, float saturation) { this.hunger = hunger; this.saturationModifier = saturation; }
    }
    public static final class Mods {
        static boolean applecore;
        public static boolean isModLoaded(String mod) { return mod.equals("applecore") && applecore; }
    }
    public static final class LegacyPortion {
        static Values values;
        public static Values getCurrentThreadFoodValues() { return values; }
        public static void onConsumed(ItemStack stack, int hunger, float saturation, World world, Player player) {
            values = new Values(hunger, saturation);
            try { player.stats.addStats(new Food(hunger, saturation, false), stack); }
            finally { values = null; }
        }
    }
    public static final class Glue {
        static int effects;
        public static Player getPlayerOrNull(Entity entity) { return entity instanceof Player ? (Player) entity : null; }
        public static boolean ItemStacks_decrBy(ItemStack stack, int count) { stack.setCount(stack.getCount() - count); return stack.isEmpty(); }
        public static ItemStack ItemStacks_NULLSTACK() { return ItemStack.EMPTY; }
        public static ItemStack ItemStacks_copyItemStackSingle(ItemStack stack) { ItemStack copy = stack.copy(); copy.setCount(1); return copy; }
        public static boolean getPlayerInSurvival(Player player) { return !player.creative; }
        public static boolean isaServerWorld(World world) { return !world.isRemote; }
        public static void playFoodConsumed(World world, Player player) { effects++; }
        public static Object getObjectUseStats(Item item) { return item; }
    }
}
