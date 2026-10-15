package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.NutritionLunchboxPatch;
import com.gberguy.ecpatches.runtime.NutritionConsumedStack;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class NutritionLunchboxTest {
    private static final NutritionLunchboxPatch PATCH = new NutritionLunchboxPatch();
    private static final String OWNER = PATCH.className.replace('.', '/');
    @TempDir Path directory;

    @BeforeEach
    void reset() {
        FoodHints.phases.clear();
        Adapters.adapters.clear();
        NutritionApi.attempts = 0;
        NutritionApi.awards = 0;
        NutritionApi.lastFood = null;
        Effects.calls = 0;
    }

    @Test
    void exactReleaseTransformsOnceAndPreservesEveryOtherMethod() throws Exception {
        byte[] original = fixture("nutritionJar", OWNER);
        byte[] output = transformer("").transform(PATCH.className, PATCH.className, original);
        assertNotSame(original, output);
        new ClassReader(output).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        assertEquals(otherMethods(node(original)), otherMethods(node(output)));
        assertSame(output, transformer("").transform(PATCH.className, PATCH.className, output));
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, PATCH.apply(node(output)));
        assertSame(original, transformer("nutritionSpiceOfLifeLastItem=false\n")
                .transform(PATCH.className, PATCH.className, original));
    }

    @Test
    void unsupportedAndMissingHandlersAreLeftUntouched() throws Exception {
        ClassNode changed = node(fixture("nutritionJar", OWNER));
        target(changed).instructions.insert(new InsnNode(Opcodes.NOP));
        byte[] bytes = bytes(changed);
        assertSame(bytes, transformer("").transform(PATCH.className, PATCH.className, bytes));
        changed.methods.remove(target(changed));
        bytes = bytes(changed);
        assertSame(bytes, transformer("").transform(PATCH.className, PATCH.className, bytes));
        assertNull(transformer("").transform(PATCH.className, PATCH.className, null));
        assertSame(bytes, transformer("").transform("unrelated.Class", "unrelated.Class", bytes));
    }

    @Test
    void supportsDevelopmentNames() throws Exception {
        ClassNode development = node(fixture("nutritionJar", OWNER));
        developmentNames(development);
        assertEquals(MethodPatch.Result.APPLIED, PATCH.apply(development));
        new ClassReader(bytes(development)).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, PATCH.apply(development));
    }

    @Test
    void threeBreadReproductionUsesReleasedHandlerAndValidityLogic() throws Exception {
        Item bread = new Food();
        ItemStack stack = new ItemStack(bread, 3, 0);
        Handler original = handler(false);
        for (int i = 0; i < 3; i++) {
            stack.setCount(stack.getCount() - 1);
            original.finish(stack);
        }
        assertEquals(3, NutritionApi.attempts);
        assertEquals(2, NutritionApi.awards);
        assertEquals(0, stack.getCount());
        reset();
        Handler fixed = handler(true);
        stack = new ItemStack(bread, 3, 0);
        for (int i = 0; i < 3; i++) {
            stack.setCount(stack.getCount() - 1);
            fixed.finish(stack);
            assertEquals(i + 1, NutritionApi.awards);
            assertEquals(2 - i, stack.getCount());
        }
        assertEquals(3, NutritionApi.attempts);
        assertEquals(0, stack.getCount());
        assertTrue(stack.isEmpty());
    }

    @Test
    void singleFoodKeepsMetadataAndTagsWithoutChangingEventOrInventoryStack() throws Exception {
        Handler fixed = handler(true);
        ItemStack food = new ItemStack(new Food(), 0, 7);
        food.tag.put("flavour", "chocolate");
        Finish event = new Finish(new EntityPlayer(), food);
        fixed.finish.invoke(fixed.instance, event);
        assertSame(food, event.getItem());
        assertEquals(0, food.getCount());
        assertEquals(1, NutritionApi.awards);
        assertEquals(1, NutritionApi.attempts);
        ItemStack snapshot = NutritionApi.lastFood;
        assertNotSame(food, snapshot);
        assertEquals(1, snapshot.getCount());
        assertEquals(7, snapshot.getMetadata());
        assertEquals(food.tag, snapshot.tag);
        assertNotSame(food.tag, snapshot.tag);
        assertTrue(snapshot.getItem() instanceof Food);
    }

    @Test
    void hintsAndAdaptersStillChooseTheirOwnApplicationPhase() throws Exception {
        Handler fixed = handler(true);
        Item hinted = new Food();
        Item adapted = new Item();
        FoodHints.phases.put(hinted, Phase.ON_RIGHT_CLICK);
        Adapters.adapters.put(adapted, stack -> Phase.ON_RIGHT_CLICK);
        fixed.finish(new ItemStack(hinted, 0, 0));
        fixed.finish(new ItemStack(adapted, 0, 0));
        assertEquals(0, NutritionApi.attempts);
        FoodHints.phases.put(hinted, Phase.FINISH_USING);
        Adapters.adapters.put(adapted, stack -> Phase.FINISH_USING);
        fixed.finish(new ItemStack(hinted, 0, 0));
        fixed.finish(new ItemStack(adapted, 0, 0));
        assertEquals(2, NutritionApi.attempts);
        assertEquals(2, NutritionApi.awards);
    }

    @Test
    void ordinaryFoodMilkAndNonPlayersKeepTheirOriginalPaths() throws Exception {
        Handler fixed = handler(true);
        ItemStack ordinary = new ItemStack(new Food(), 2, 0);
        fixed.finish(ordinary);
        assertSame(ordinary, NutritionApi.lastFood);
        assertEquals(2, ordinary.getCount());
        fixed.finish(new ItemStack(new Milk(), 0, 0));
        assertEquals(2, NutritionApi.awards);
        assertEquals(1, Effects.calls);
        EntityPlayer client = new EntityPlayer();
        client.world.isRemote = true;
        fixed.finish.invoke(fixed.instance, new Finish(client, new ItemStack(new Milk(), 1, 0)));
        assertEquals(1, Effects.calls);
        int attempts = NutritionApi.attempts;
        fixed.finish.invoke(fixed.instance, new Finish(new EntityLivingBase(), new ItemStack(new Food(), 0, 0)));
        assertEquals(attempts, NutritionApi.attempts);
    }

    @Test
    void restoresInputAfterCopyFailuresAndAvoidsEmptySingleton() {
        assertNull(NutritionConsumedStack.snapshot(null));
        assertSame(ItemStack.EMPTY, NutritionConsumedStack.snapshot(ItemStack.EMPTY));
        assertEquals(0, ItemStack.EMPTY.getCount());
        ItemStack unknown = new ItemStack(null, 0, 0);
        assertSame(unknown, NutritionConsumedStack.snapshot(unknown));
        ItemStack failing = new ItemStack(new Food(), 0, 0) {
            @Override public ItemStack copy() {
                assertEquals(1, getCount());
                throw new IllegalStateException("copy failed");
            }
        };
        assertThrows(IllegalStateException.class, () -> NutritionConsumedStack.snapshot(failing));
        assertEquals(0, failing.getCount());
        ItemStack singletonCopy = new ItemStack(new Food(), 0, 0) {
            @Override public ItemStack copy() { return ItemStack.EMPTY; }
        };
        assertSame(singletonCopy, NutritionConsumedStack.snapshot(singletonCopy));
        assertEquals(0, ItemStack.EMPTY.getCount());
    }

    @Test
    void forgeItemStackTransformerMakesCopyingBeforeRestoringCountLoseIdentity() throws Exception {
        String forgeJar = System.getProperty("ecpatches.forgeJar");
        assumeTrue(forgeJar != null || getClass().getResource(
                "/net/minecraftforge/fml/common/asm/transformers/ItemStackTransformer.class") != null,
                "Forge transformer fixture not supplied");
        byte[] original;
        try (InputStream input = ItemStack.class.getResourceAsStream("/net/minecraft/item/ItemStack.class")) {
            original = read(input);
        }
        ClassNode raw = node(original);
        MethodNode copy = null;
        for (MethodNode method : raw.methods) {
            if (method.name.equals("copy")) copy = method;
        }
        assertNotNull(copy);
        for (AbstractInsnNode instruction : copy.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("getItem")) {
                copy.instructions.set(instruction, new FieldInsnNode(Opcodes.GETFIELD,
                        "net/minecraft/item/ItemStack", "item", "Lnet/minecraft/item/Item;"));
            }
        }
        byte[] transformed;
        try (java.net.URLClassLoader forge = new java.net.URLClassLoader(
                forgeJar == null ? new java.net.URL[0]
                        : new java.net.URL[]{java.nio.file.Paths.get(forgeJar).toUri().toURL()}, getClass().getClassLoader())) {
            net.minecraft.launchwrapper.IClassTransformer redirect = (net.minecraft.launchwrapper.IClassTransformer)
                    forge.loadClass("net.minecraftforge.fml.common.asm.transformers.ItemStackTransformer").newInstance();
            transformed = redirect.transform("net.minecraft.item.ItemStack", "net.minecraft.item.ItemStack", bytes(raw));
        }
        Map<String, byte[]> definitions = new HashMap<>();
        definitions.put("net.minecraft.item.ItemStack", transformed);
        try (InputStream input = NutritionConsumedStack.class.getResourceAsStream(
                "/com/gberguy/ecpatches/runtime/NutritionConsumedStack.class")) {
            definitions.put(NutritionConsumedStack.class.getName(), read(input));
        }
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    byte[] code = definitions.get(name);
                    type = defineClass(name, code, 0, code.length);
                }
                if (resolve) resolveClass(type);
                return type;
            }
        };
        Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
        Item food = new Food();
        Object depleted = stackType.getConstructor(Item.class, int.class, int.class).newInstance(food, 0, 9);
        Object brokenCopy = stackType.getMethod("copy").invoke(depleted);
        stackType.getMethod("setCount", int.class).invoke(brokenCopy, 1);
        assertSame(Item.AIR, stackType.getMethod("getItem").invoke(brokenCopy));
        Object fixedCopy = loader.loadClass(NutritionConsumedStack.class.getName())
                .getMethod("snapshot", stackType).invoke(null, depleted);
        assertSame(food, stackType.getMethod("getItem").invoke(fixedCopy));
        assertEquals(1, stackType.getMethod("getCount").invoke(fixedCopy));
        assertEquals(9, stackType.getMethod("getMetadata").invoke(fixedCopy));
        assertEquals(0, stackType.getMethod("getCount").invoke(depleted));
        assertEquals(true, stackType.getMethod("isEmpty").invoke(depleted));
    }

    @Test
    void spiceOfLifeReleaseForwardsTheSameStackAfterConsumptionAndKeepsResultSeparate() throws Exception {
        ClassNode container = node(fixture("spiceOfLifeJar", "squeek/spiceoflife/items/ItemFoodContainer"));
        MethodNode finish = (MethodNode) container.methods.stream()
                .filter(m -> ((MethodNode) m).name.equals("func_77654_b")).findFirst().get();
        MethodInsnNode consume = null, dispatch = null, inventoryWrite = null;
        for (AbstractInsnNode instruction : finish.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.name.equals("func_77950_b")) consume = call;
            if (call.owner.equals("net/minecraftforge/event/ForgeEventFactory") && call.name.equals("onItemUseFinish")) dispatch = call;
            if (call.name.equals("setStackInSlot")) inventoryWrite = call;
        }
        assertNotNull(consume);
        assertNotNull(dispatch);
        assertNotNull(inventoryWrite);
        assertTrue(finish.instructions.indexOf(consume) < finish.instructions.indexOf(dispatch));
        assertTrue(finish.instructions.indexOf(dispatch) < finish.instructions.indexOf(inventoryWrite));
        AbstractInsnNode resultLoad = previous(dispatch);
        assertEquals(8, ((VarInsnNode) resultLoad).var);
        AbstractInsnNode foodLoad = previous(previous(resultLoad));
        assertEquals(7, ((VarInsnNode) foodLoad).var);
        assertEquals(7, ((VarInsnNode) previous(previous(previous(consume)))).var);
    }

    private PatchTransformer transformer(String contents) throws Exception {
        Path config = directory.resolve("patches.cfg");
        Files.write(config, contents.getBytes(StandardCharsets.UTF_8));
        return new PatchTransformer(PatchSettings.read(config.toFile()));
    }

    private static byte[] fixture(String property, String name) throws Exception {
        String path = System.getProperty("ecpatches." + property);
        assumeTrue(path != null, property + " fixture not supplied");
        try (JarFile jar = new JarFile(path);
             InputStream input = jar.getInputStream(jar.getJarEntry(name + ".class"))) {
            return read(input);
        }
    }

    private static byte[] read(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
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

    private static MethodNode target(ClassNode node) {
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (method.name.equals(PATCH.methodName) && method.desc.equals(PATCH.descriptor)) return method;
        }
        throw new AssertionError("Missing Nutrition handler");
    }

    private static Map<String, String> otherMethods(ClassNode node) {
        Map<String, String> result = new TreeMap<>();
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (method != target(node)) result.put(method.name + method.desc, MethodFingerprint.of(method));
        }
        return result;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }

    private static void developmentNames(ClassNode node) {
        Map<String, String> names = new HashMap<>();
        names.put("func_77973_b", "getItem");
        names.put("func_130014_f_", "getEntityWorld");
        names.put("func_179223_d", "getBlock");
        names.put("field_72995_K", "isRemote");
        for (Object value : node.methods) {
            for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    call.name = names.getOrDefault(call.name, call.name);
                } else if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    field.name = names.getOrDefault(field.name, field.name);
                }
            }
        }
    }

    private Handler handler(boolean patched) throws Exception {
        ClassNode source = node(fixture("nutritionJar", OWNER));
        if (patched) assertEquals(MethodPatch.Result.APPLIED, PATCH.apply(source));
        source.fields.clear();
        source.methods.removeIf(value -> !Arrays.asList("<init>", "finishUsingItem", "toINutritionFood", "reapplyEffectsFromMilk")
                .contains(((MethodNode) value).name));
        ClassNode validation = node(fixture("nutritionJar", "ca/wescook/nutrition/nutrients/NutritionUtilImpl"));
        for (Object value : validation.methods) {
            if (((MethodNode) value).name.equals("isValidFood")) source.methods.add((MethodNode) value);
        }
        developmentNames(source);
        Map<String, String> types = new HashMap<>();
        types.put(OWNER, "NutritionFinishHarness");
        types.put("ca/wescook/nutrition/nutrients/FoodHintList", Type.getInternalName(FoodHints.class));
        types.put("ca/wescook/nutrition/nutrients/NutritionAdapterManager", Type.getInternalName(Adapters.class));
        types.put("ca/wescook/nutrition/api/INutritionFood", Type.getInternalName(NutritionFood.class));
        types.put("ca/wescook/nutrition/api/NutrientApplicationPhase", Type.getInternalName(Phase.class));
        types.put("ca/wescook/nutrition/api/NutritionUtil", Type.getInternalName(NutritionApi.class));
        types.put("ca/wescook/nutrition/effects/EffectsManager", Type.getInternalName(Effects.class));
        types.put("net/minecraft/item/ItemFood", Type.getInternalName(Food.class));
        types.put("net/minecraft/item/ItemBucketMilk", Type.getInternalName(Milk.class));
        types.put("net/minecraft/item/ItemBlock", Type.getInternalName(BlockItem.class));
        types.put("net/minecraft/item/ItemBlockSpecial", Type.getInternalName(SpecialBlockItem.class));
        types.put("net/minecraft/block/Block", Type.getInternalName(Block.class));
        types.put("net/minecraft/block/BlockCake", Type.getInternalName(Cake.class));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        source.accept(new ClassRemapper(writer, new SimpleRemapper(types)));
        byte[] bytes = writer.toByteArray();
        new ClassReader(bytes).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        Class<?> harness = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        NutritionApi.validFood = harness.getMethod("isValidFood", ItemStack.class);
        return new Handler(harness.newInstance(), harness.getMethod("finishUsingItem", Finish.class));
    }

    private static final class Handler {
        final Object instance;
        final Method finish;
        Handler(Object instance, Method finish) { this.instance = instance; this.finish = finish; }
        void finish(ItemStack stack) throws Exception { finish.invoke(instance, new Finish(new EntityPlayer(), stack)); }
    }

    public enum Phase { ON_RIGHT_CLICK, FINISH_USING }
    public interface NutritionFood { Phase getNutrientApplicationPhase(ItemStack stack); }
    public static class Food extends Item {}
    public static class Milk extends Item {}
    public static class Block {}
    public static class Cake extends Block {}
    public static class BlockItem extends Item { public Block getBlock() { return new Block(); } }
    public static class SpecialBlockItem extends Item { public Block getBlock() { return new Block(); } }

    public static final class FoodHints {
        static final Map<Item, Phase> phases = new IdentityHashMap<>();
        public static Phase getNutrientApplicationPhase(ItemStack stack) { return phases.get(stack.getItem()); }
        public static Boolean isValidFood(ItemStack stack) { return null; }
    }

    public static final class Adapters {
        static final Map<Item, NutritionFood> adapters = new IdentityHashMap<>();
        public static NutritionFood apply(ItemStack stack) { return adapters.get(stack.getItem()); }
    }

    public static final class Effects {
        static int calls;
        public static void reapplyEffects(EntityPlayer player) { calls++; }
    }

    public static final class NutritionApi {
        static Method validFood;
        static int attempts, awards;
        static ItemStack lastFood;
        public static boolean addNutrientsToPlayer(EntityPlayer player, ItemStack stack) {
            attempts++;
            lastFood = stack;
            try {
                boolean valid = (Boolean) validFood.invoke(null, stack);
                if (valid) awards++;
                return valid;
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }
    }
}
