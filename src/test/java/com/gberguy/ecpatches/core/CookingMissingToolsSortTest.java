package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.CookingSortRefreshPatch;
import com.gberguy.ecpatches.runtime.CookingSortRefresh;
import java.io.InputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class CookingMissingToolsSortTest implements Opcodes {
    private static final String API = "net.blay09.mods.cookingforblockheads.api.";
    private static final String CONTAINER = "net.blay09.mods.cookingforblockheads.container.ContainerRecipeBook";
    private static final String BUTTON = "com.gberguy.ecpatches.compat.CookingMissingToolsSortButton";
    @TempDir Path directory;
    private byte[] original;

    @BeforeEach
    void loadFixture() throws Exception {
        String path = System.getProperty("ecpatches.cookingJar");
        assumeTrue(path != null, "Cooking for Blockheads 6.5.0 fixture not supplied");
        try (JarFile source = new JarFile(path)) {
            original = read(source.getInputStream(source.getJarEntry(CONTAINER.replace('.', '/') + ".class")));
        }
    }

    private PatchTransformer transformer(boolean enabled) throws Exception {
        Path config = directory.resolve("ect.cfg");
        Files.write(config, (PatchId.CFB_MISSING_TOOLS_SORT.key + "=" + enabled + "\n").getBytes(StandardCharsets.UTF_8));
        return new PatchTransformer(PatchSettings.read(config.toFile()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transformsReleasedAndDevelopmentCodeOnceAndPreservesOtherMethods(boolean development) throws Exception {
        byte[] input = development ? development(original) : original;
        PatchTransformer transformer = transformer(true);
        byte[] patched = transformer.transform(CONTAINER, CONTAINER, input);
        assertNotSame(input, patched);
        new ClassReader(patched).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        assertEquals(otherMethods(node(input)), otherMethods(node(patched)));
        assertSame(patched, transformer.transform(CONTAINER, CONTAINER, patched));
        assertEquals(MethodPatch.Result.ALREADY_PRESENT, new CookingSortRefreshPatch().apply(node(patched)));
        assertSame(input, transformer(false).transform(CONTAINER, CONTAINER, input));
    }

    @Test
    void rejectsUnknownAndMissingMethodWithoutChangingClass() throws Exception {
        PatchTransformer transformer = transformer(true);
        ClassNode changed = node(original);
        method(changed, "setItemList").instructions.insert(new InsnNode(NOP));
        byte[] input = bytes(changed);
        assertSame(input, transformer.transform(CONTAINER, CONTAINER, input));
        changed.methods.remove(method(changed, "setItemList"));
        input = bytes(changed);
        assertSame(input, transformer.transform(CONTAINER, CONTAINER, input));
    }

    @Test
    void registersViaReleasedApiAndReturnsSameFixedComparatorOnEveryClick() throws Exception {
        Harness loader = harness();
        Object button = loader.button();
        assertEquals(Collections.singletonList(button), loader.registered);
        Method comparator = button.getClass().getMethod("getComparator", net.minecraft.entity.player.EntityPlayer.class);
        assertSame(button, comparator.invoke(button, new Object[]{null}));
        assertSame(button, comparator.invoke(button, new Object[]{null}));
        ResourceLocation icon = (ResourceLocation) button.getClass().getMethod("getIcon").invoke(button);
        assertEquals("eternalconfluencetweaks", icon.getNamespace());
        assertEquals("textures/gui/cfb_ec_wrench.png", icon.getPath());
        assertEquals(0, button.getClass().getMethod("getIconTextureX").invoke(button));
        assertEquals(0, button.getClass().getMethod("getIconTextureY").invoke(button));
        String tooltip = (String) button.getClass().getMethod("getTooltip").invoke(button);
        try (InputStream input = getClass().getResourceAsStream("/assets/eternalconfluencetweaks/lang/en_us.lang")) {
            String language = new String(read(input), StandardCharsets.UTF_8);
            assertTrue(language.contains(tooltip + "=Sort: Missing Tools Last"));
        }
        try (InputStream input = getClass().getResourceAsStream("/assets/eternalconfluencetweaks/textures/gui/cfb_ec_wrench.png")) {
            java.awt.image.BufferedImage image = ImageIO.read(input);
            assertEquals(256, image.getWidth());
            assertEquals(256, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            for (int y : new int[]{0, 20, 40}) {
                assertNotEquals(0, image.getSubimage(0, y, 20, 20).getRGB(10, 10) >>> 24);
            }
        }
    }

    @Test
    void groupsByCurrentStatusThenCaseInsensitiveNameWithoutDroppingEntries() throws Exception {
        Harness loader = harness();
        Comparator<Object> comparator = loader.comparator();
        Object availableZ = loader.recipe("zucchini", "AVAILABLE", 1);
        Object availableA = loader.recipe("Apple", "AVAILABLE", 2);
        Object missingZ = loader.recipe("Zesty soup", "MISSING_TOOLS", 3);
        Object missingA = loader.recipe("apple pie", "MISSING_TOOLS", 4);
        Object ingredients = loader.recipe("Banana", "MISSING_INGREDIENTS", 5);
        List<Object> entries = new ArrayList<>(Arrays.asList(missingZ, availableZ, ingredients, missingA, availableA));
        entries.sort(comparator);
        assertEquals(Arrays.asList(availableA, availableZ, ingredients, missingA, missingZ), entries);
        assertEquals(0, comparator.compare(availableA, loader.recipe("aPPLe", "AVAILABLE", 6)));
        Collections.reverse(entries);
        entries.sort(comparator);
        assertEquals(Arrays.asList(availableA, availableZ, ingredients, missingA, missingZ), entries);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void releasedRefreshReproducesSlotPinningAndPatchRestoresOrderWhenToolAvailabilityChanges(boolean gainsTool) throws Exception {
        Harness loader = harness();
        Comparator<Object> comparator = loader.comparator();
        String before = gainsTool ? "MISSING_TOOLS" : "AVAILABLE";
        String after = gainsTool ? "AVAILABLE" : "MISSING_TOOLS";
        Object selected = loader.recipe("Apple", before, 1);
        Object replacement = loader.recipe("Apple", after, 1);
        Object other = loader.recipe("Zucchini", "AVAILABLE", 2);
        List<Object> initial = new ArrayList<>(Arrays.asList(selected, other));
        initial.sort(comparator);
        List<Object> updated = Arrays.asList(replacement, other);
        List<Object> expected = new ArrayList<>(updated);
        expected.sort(comparator);
        byte[] patched = transformer(true).transform(CONTAINER, CONTAINER, original);
        assertNotEquals(expected, refresh(loader, development(original), comparator, initial, selected, updated));
        assertEquals(expected, refresh(loader, development(patched), comparator, initial, selected, updated));
    }

    @Test
    void refreshHookLeavesOtherSortsAndTheirNullPlaceholdersUntouched() {
        List<String> entries = new ArrayList<>(Arrays.asList("z", null, "a"));
        CookingSortRefresh.restoreOrder(entries, Comparator.naturalOrder());
        assertEquals(Arrays.asList("z", null, "a"), entries);
    }

    @Test
    void selectedRecipeDisappearingDoesNotLeaveNullSlotsInWrenchOrder() throws Exception {
        Harness loader = harness();
        Comparator<Object> comparator = loader.comparator();
        Object selected = loader.recipe("Apple", "MISSING_TOOLS", 1);
        Object other = loader.recipe("Zucchini", "AVAILABLE", 2);
        byte[] patched = transformer(true).transform(CONTAINER, CONTAINER, original);
        assertEquals(Collections.singletonList(other), refresh(loader, development(patched), comparator,
                Arrays.asList(other, selected), selected, Collections.singletonList(other)));
    }

    @Test
    void releasedComparatorAndRefreshResolveSrgNames() throws Exception {
        assumeTrue(System.getProperty("ecpatches.releaseJar") != null
                && System.getProperty("ecpatches.releaseTestJar") != null, "Release artifacts not supplied");
        Harness loader = harness(true);
        Comparator<Object> comparator = loader.comparator();
        Object selected = loader.recipe("Apple", "AVAILABLE", 1);
        Object replacement = loader.recipe("Apple", "MISSING_TOOLS", 1);
        Object other = loader.recipe("Zucchini", "AVAILABLE", 2);
        Object another = loader.recipe("Banana", "AVAILABLE", 3);
        assertTrue(comparator.compare(another, other) < 0);
        byte[] patched = transformer(true).transform(CONTAINER, CONTAINER, original);
        assertEquals(Arrays.asList(other, replacement), refresh(loader, patched, comparator,
                Arrays.asList(selected, other), selected, Arrays.asList(replacement, other)));
    }

    private List<?> refresh(Harness loader, byte[] code, Comparator<Object> comparator,
            List<Object> initial, Object selected, Collection<Object> updated) throws Exception {
        ClassNode container = node(code);
        container.superName = "java/lang/Object";
        container.interfaces.clear();
        container.methods.removeIf(value -> !((MethodNode) value).name.equals("setItemList"));
        container.fields.removeIf(value -> !Arrays.asList("itemList", "filteredItems", "selectedRecipe", "currentSorting", "currentSearch")
                .contains(((FieldNode) value).name));
        for (Object value : container.fields) ((FieldNode) value).access = ACC_PUBLIC;
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(RETURN));
        container.methods.add(constructor);
        MethodNode search = new MethodNode(ACC_PUBLIC, "search", "(Ljava/lang/String;)V", null, null);
        search.instructions.add(new VarInsnNode(ALOAD, 0));
        search.instructions.add(new FieldInsnNode(GETFIELD, container.name, "filteredItems", "Ljava/util/List;"));
        search.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "clear", "()V", true));
        search.instructions.add(new VarInsnNode(ALOAD, 0));
        search.instructions.add(new FieldInsnNode(GETFIELD, container.name, "filteredItems", "Ljava/util/List;"));
        search.instructions.add(new VarInsnNode(ALOAD, 0));
        search.instructions.add(new FieldInsnNode(GETFIELD, container.name, "itemList", "Ljava/util/List;"));
        search.instructions.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/List", "addAll", "(Ljava/util/Collection;)Z", true));
        search.instructions.add(new InsnNode(POP));
        search.instructions.add(new InsnNode(RETURN));
        container.methods.add(search);
        for (String signature : new String[]{"populateRecipeSlots", "setDirty"}) {
            MethodNode noOp = new MethodNode(ACC_PUBLIC, signature, signature.equals("setDirty") ? "(Z)V" : "()V", null, null);
            noOp.instructions.add(new InsnNode(RETURN));
            container.methods.add(noOp);
        }
        Class<?> type = new ClassLoader(loader) {
            Class<?> define(byte[] code) { return defineClass(CONTAINER, code, 0, code.length); }
        }.define(bytes(container));
        Object instance = type.newInstance();
        type.getField("itemList").set(instance, new ArrayList<>());
        type.getField("filteredItems").set(instance, new ArrayList<>(initial));
        type.getField("currentSorting").set(instance, comparator);
        type.getField("selectedRecipe").set(instance, selected);
        type.getMethod("setItemList", Collection.class).invoke(instance, updated);
        Object selection = type.getField("selectedRecipe").get(instance);
        assertTrue(selection == null || updated.contains(selection));
        return (List<?>) type.getField("filteredItems").get(instance);
    }

    private Harness harness() throws Exception { return harness(false); }

    private Harness harness(boolean release) throws Exception {
        Harness loader = new Harness();
        try (JarFile source = new JarFile(System.getProperty("ecpatches.cookingJar"))) {
            for (String name : new String[]{"ISortButton", "FoodRecipeWithStatus", "RecipeStatus", "CookingForBlockheadsAPI", "IInternalMethods"}) {
                ClassNode api = node(read(source.getInputStream(source.getJarEntry((API + name).replace('.', '/') + ".class"))));
                if (name.equals("CookingForBlockheadsAPI")) {
                    api.fields.removeIf(value -> !((FieldNode) value).name.equals("internalMethods"));
                    api.methods.removeIf(value -> !((MethodNode) value).name.equals("addSortButton"));
                } else if (name.equals("IInternalMethods")) {
                    api.methods.removeIf(value -> !((MethodNode) value).name.equals("addSortButton"));
                }
                loader.definitions.put(API + name, bytes(api));
            }
        }
        try (InputStream input = getClass().getResourceAsStream("/" + BUTTON.replace('.', '/') + ".class")) {
            loader.definitions.put(BUTTON, read(input));
        }
        if (release) {
            try (JarFile compiled = new JarFile(System.getProperty("ecpatches.releaseJar"));
                 JarFile fixtures = new JarFile(System.getProperty("ecpatches.releaseTestJar"))) {
                for (String name : new String[]{BUTTON, CookingSortRefresh.class.getName(), CookingSortRefresh.Ordered.class.getName()}) {
                    loader.definitions.put(name, read(compiled.getInputStream(compiled.getJarEntry(name.replace('.', '/') + ".class"))));
                }
                for (String name : new String[]{"net.minecraft.item.Item", "net.minecraft.item.ItemStack"}) {
                    loader.definitions.put(name, read(fixtures.getInputStream(fixtures.getJarEntry(name.replace('.', '/') + ".class"))));
                }
            }
        }
        Class<?> internal = loader.loadClass(API + "IInternalMethods");
        Object delegate = Proxy.newProxyInstance(loader, new Class<?>[]{internal}, (proxy, method, args) -> {
            loader.registered.add(args[0]);
            return null;
        });
        Field field = loader.loadClass(API + "CookingForBlockheadsAPI").getDeclaredField("internalMethods");
        field.setAccessible(true);
        field.set(null, delegate);
        return loader;
    }

    private static class Harness extends ClassLoader {
        final Map<String, byte[]> definitions = new HashMap<>();
        final List<Object> registered = new ArrayList<>();
        private Object item;

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!definitions.containsKey(name)) {
                if (name.startsWith("net.blay09.") || name.startsWith("net.minecraft.client.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
            Class<?> result = findLoadedClass(name);
            if (result == null) {
                byte[] bytes = definitions.get(name);
                result = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) resolveClass(result);
            return result;
        }

        Object button() throws Exception { return loadClass(BUTTON).newInstance(); }

        Comparator<Object> comparator() throws Exception { return (Comparator<Object>) button(); }

        Object recipe(String name, String status, int metadata) throws Exception {
            Class<?> itemType = loadClass("net.minecraft.item.Item");
            Class<?> stackType = loadClass("net.minecraft.item.ItemStack");
            if (item == null) item = itemType.newInstance();
            Object stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, metadata);
            ((Map<String, String>) stackType.getField("tag").get(stack)).put("displayName", name);
            Class<?> statusType = loadClass(API + "RecipeStatus");
            Object state = Enum.valueOf((Class) statusType, status);
            return loadClass(API + "FoodRecipeWithStatus").getConstructor(stackType, statusType).newInstance(stack, state);
        }
    }

    private static Map<String, String> otherMethods(ClassNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!method.name.equals("setItemList")) result.put(method.name + method.desc, MethodFingerprint.of(method));
        }
        return result;
    }

    private static MethodNode method(ClassNode node, String name) {
        for (Object value : node.methods) if (((MethodNode) value).name.equals(name)) return (MethodNode) value;
        throw new AssertionError(name);
    }

    private static byte[] development(byte[] code) {
        ClassNode node = node(code);
        for (Object value : node.methods) for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.equals("func_77973_b")) call.name = "getItem";
                if (call.name.equals("func_77952_i")) call.name = "getItemDamage";
            }
        }
        return bytes(node);
    }

    private static ClassNode node(byte[] code) {
        ClassNode node = new ClassNode();
        new ClassReader(code).accept(node, 0);
        return node;
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] read(InputStream input) throws Exception {
        try (InputStream source = input; java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}
