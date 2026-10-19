package com.gberguy.ecpatches.core;

import java.io.InputStream;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

public class CookingSortLifecycleTest implements Opcodes {
    private static final String MOD = "com/gberguy/ecpatches/EternalConfluenceTweaks";
    private static final String SETTINGS = "com/gberguy/ecpatches/core/PatchSettings";
    private static final String LOADER = "net/minecraftforge/fml/common/Loader";
    private static final String SIDE = "net/minecraftforge/fml/relauncher/Side";
    private static final String EVENT = "net/minecraftforge/fml/common/event/FMLPostInitializationEvent";

    @ParameterizedTest
    @CsvSource({"false,true,true,false,0", "false,false,false,false,0", "true,false,true,false,0",
            "true,true,false,false,0", "true,true,true,false,1", "true,true,true,true,1"})
    void commonLifecycleAvoidsOptionalAndClientClassesAndOnlyRegistersEnabledClients(
            boolean client, boolean enabled, boolean loaded, boolean missingApi, int calls) throws Exception {
        Map<String, byte[]> definitions = new HashMap<>();
        ClassNode mod;
        try (InputStream input = getClass().getResourceAsStream("/" + MOD + ".class")) {
            mod = new ClassNode();
            new ClassReader(input).accept(mod, 0);
        }
        mod.methods.removeIf(value -> !((MethodNode) value).name.equals("postInit"));
        mod.fields.removeIf(value -> !((FieldNode) value).name.equals("LOGGER"));
        ((FieldNode) mod.fields.get(0)).access = ACC_PUBLIC | ACC_STATIC;
        mod.methods.add(constructor());
        definitions.put(MOD, bytes(mod));

        ClassNode settings = base(SETTINGS);
        bool(settings, "enabled", enabled);
        MethodNode current = method(ACC_PUBLIC | ACC_STATIC, "current", "()L" + SETTINGS + ";");
        construct(current, SETTINGS);
        current.instructions.add(new InsnNode(ARETURN));
        settings.methods.add(current);
        MethodNode isEnabled = method(ACC_PUBLIC, "enabled", "(Lcom/gberguy/ecpatches/core/PatchId;)Z");
        isEnabled.instructions.add(new FieldInsnNode(GETSTATIC, SETTINGS, "enabled", "Z"));
        isEnabled.instructions.add(new InsnNode(IRETURN));
        settings.methods.add(isEnabled);
        definitions.put(SETTINGS, bytes(settings));

        ClassNode loader = base(LOADER);
        bool(loader, "loaded", loaded);
        MethodNode isLoaded = method(ACC_PUBLIC | ACC_STATIC, "isModLoaded", "(Ljava/lang/String;)Z");
        isLoaded.instructions.add(new FieldInsnNode(GETSTATIC, LOADER, "loaded", "Z"));
        isLoaded.instructions.add(new InsnNode(IRETURN));
        loader.methods.add(isLoaded);
        definitions.put(LOADER, bytes(loader));

        ClassNode side = base(SIDE);
        bool(side, "client", client);
        MethodNode isClient = method(ACC_PUBLIC, "isClient", "()Z");
        isClient.instructions.add(new FieldInsnNode(GETSTATIC, SIDE, "client", "Z"));
        isClient.instructions.add(new InsnNode(IRETURN));
        side.methods.add(isClient);
        definitions.put(SIDE, bytes(side));

        ClassNode event = base(EVENT);
        event.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null));
        MethodNode getSide = method(ACC_PUBLIC, "getSide", "()L" + SIDE + ";");
        construct(getSide, SIDE);
        getSide.instructions.add(new InsnNode(ARETURN));
        event.methods.add(getSide);
        MethodNode register = method(ACC_PUBLIC, "buildSoftDependProxy",
                "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)Ljava/util/Optional;");
        register.instructions.add(new FieldInsnNode(GETSTATIC, EVENT, "calls", "I"));
        register.instructions.add(new InsnNode(ICONST_1));
        register.instructions.add(new InsnNode(IADD));
        register.instructions.add(new FieldInsnNode(PUTSTATIC, EVENT, "calls", "I"));
        if (missingApi) {
            construct(register, "java/lang/NoClassDefFoundError");
            register.instructions.add(new InsnNode(ATHROW));
        } else {
            register.instructions.add(new FieldInsnNode(GETSTATIC, "java/lang/Boolean", "TRUE", "Ljava/lang/Boolean;"));
            register.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/util/Optional", "of", "(Ljava/lang/Object;)Ljava/util/Optional;", false));
            register.instructions.add(new InsnNode(ARETURN));
        }
        event.methods.add(register);
        definitions.put(EVENT, bytes(event));

        String network = "com/gberguy/ecpatches/runtime/AstralPerkNetwork";
        ClassNode astral = base(network);
        MethodNode initialize = method(ACC_PUBLIC | ACC_STATIC, "initialize", "()V");
        initialize.instructions.add(new InsnNode(RETURN));
        astral.methods.add(initialize);
        definitions.put(network, bytes(astral));

        ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] code = definitions.get(name.replace('.', '/'));
                if (code == null) {
                    if (name.startsWith("net.blay09.") || name.startsWith("net.minecraft.client.")
                            || name.startsWith("com.gberguy.ecpatches.compat.")) throw new AssertionError("Unexpected optional/client linkage: " + name);
                    return super.loadClass(name, resolve);
                }
                Class<?> result = findLoadedClass(name);
                if (result == null) result = defineClass(name, code, 0, code.length);
                if (resolve) resolveClass(result);
                return result;
            }
        };
        Class<?> modType = isolated.loadClass(MOD.replace('/', '.'));
        modType.getField("LOGGER").set(null, LogManager.getLogger("CookingSortLifecycleTest"));
        Class<?> eventType = isolated.loadClass(EVENT.replace('/', '.'));
        modType.getMethod("postInit", eventType).invoke(modType.newInstance(), eventType.newInstance());
        assertEquals(calls, eventType.getField("calls").getInt(null));
    }

    private static void bool(ClassNode owner, String name, boolean value) {
        owner.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, name, "Z", null, value ? 1 : 0));
    }

    private static ClassNode base(String name) {
        ClassNode node = new ClassNode();
        node.version = V1_8;
        node.access = ACC_PUBLIC;
        node.name = name;
        node.superName = "java/lang/Object";
        node.methods.add(constructor());
        return node;
    }

    private static MethodNode constructor() {
        MethodNode method = method(ACC_PUBLIC, "<init>", "()V");
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(RETURN));
        return method;
    }

    private static MethodNode method(int access, String name, String descriptor) {
        return new MethodNode(access, name, descriptor, null, null);
    }

    private static void construct(MethodNode method, String name) {
        method.instructions.add(new TypeInsnNode(NEW, name));
        method.instructions.add(new InsnNode(DUP));
        method.instructions.add(new MethodInsnNode(INVOKESPECIAL, name, "<init>", "()V", false));
    }

    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
