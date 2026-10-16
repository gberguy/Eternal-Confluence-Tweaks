package com.gberguy.ecpatches.core;

import java.util.Arrays;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public abstract class MethodPatch implements Opcodes {
    public enum Result { APPLIED, ALREADY_PRESENT, UNSUPPORTED }

    public final PatchId id;
    public final String className;
    public final String methodName;
    public final String descriptor;
    private final String[] originalFingerprints;
    private final String[] fixedFingerprints;

    protected MethodPatch(PatchId id, String className, String methodName, String descriptor,
            String originalFingerprint, String... fixedFingerprints) {
        this(id, className, methodName, descriptor, new String[]{originalFingerprint}, fixedFingerprints);
    }

    protected MethodPatch(PatchId id, String className, String methodName, String descriptor,
            String[] originalFingerprints, String... fixedFingerprints) {
        this.id = id;
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.originalFingerprints = originalFingerprints;
        this.fixedFingerprints = fixedFingerprints;
    }

    public final Result apply(ClassNode owner) {
        if (!className.replace('.', '/').equals(owner.name)) {
            return Result.UNSUPPORTED;
        }
        MethodNode method = null;
        for (Object value : owner.methods) {
            MethodNode candidate = (MethodNode) value;
            if (methodName.equals(MinecraftNames.canonical(candidate.name)) && descriptor.equals(candidate.desc)) {
                if (method != null) {
                    return Result.UNSUPPORTED;
                }
                method = candidate;
            }
        }
        if (method == null) {
            return Result.UNSUPPORTED;
        }
        String fingerprint = MethodFingerprint.of(method);
        if (Arrays.asList(fixedFingerprints).contains(fingerprint)) {
            return compatible(owner, true) ? Result.ALREADY_PRESENT : Result.UNSUPPORTED;
        }
        if (!Arrays.asList(originalFingerprints).contains(fingerprint) || !compatible(owner, false)) {
            return Result.UNSUPPORTED;
        }
        edit(owner, method, MinecraftNames.isDeobfuscated(method));
        if (!Arrays.asList(fixedFingerprints).contains(MethodFingerprint.of(method)) || !compatible(owner, true)) {
            throw new IllegalStateException("Unexpected patch output for " + id.key);
        }
        return Result.APPLIED;
    }

    protected abstract void edit(MethodNode method, boolean deobfuscated);

    protected boolean compatible(ClassNode owner, boolean fixed) {
        return true;
    }

    protected void edit(ClassNode owner, MethodNode method, boolean deobfuscated) {
        edit(method, deobfuscated);
    }

    public boolean enabled(PatchSettings settings) {
        return settings.enabled(id);
    }

    protected static AbstractInsnNode next(AbstractInsnNode instruction) {
        AbstractInsnNode result = instruction.getNext();
        while (result != null && result.getOpcode() < 0) {
            result = result.getNext();
        }
        return result;
    }

    protected static String name(String srg, boolean deobfuscated) {
        return MinecraftNames.runtime(srg, deobfuscated);
    }
}
