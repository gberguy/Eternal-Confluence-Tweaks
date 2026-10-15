package com.gberguy.ecpatches.core;

import com.gberguy.ecpatches.core.patches.*;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public final class PatchTransformer implements IClassTransformer {
    private static final Logger LOG = LogManager.getLogger("EternalConfluenceTweaks");
    private static final Map<String, MethodPatch> PATCHES;
    private static final Map<String, List<MethodPatch>> TARGETS;
    private final PatchSettings settings;

    static {
        Map<String, MethodPatch> patches = new LinkedHashMap<>();
        Map<String, List<MethodPatch>> targets = new LinkedHashMap<>();
        List<MethodPatch> modules = new ArrayList<>(Arrays.asList(new BloodArsenalPatch(), new RootsPatch(), new LycanitesMeleePatch(), new LycanitesGhostPatch(), new MorechidsPatch(), new WaystonesVillagePatch(), new WitcheryVillagesToroWallsPatch()));
        modules.addAll(Arrays.asList(GhostlyPatches.create()));
        modules.add(new NutritionLunchboxPatch());
        modules.add(new ScalingFeastSaturationHudPatch());
        for (MethodPatch patch : modules) {
            String key = patch.className + "#" + patch.methodName + patch.descriptor;
            if (patches.put(key, patch) != null) {
                throw new IllegalStateException("Duplicate patch target " + key);
            }
            targets.computeIfAbsent(patch.className, owner -> new ArrayList<>()).add(patch);
        }
        PATCHES = Collections.unmodifiableMap(patches);
        TARGETS = Collections.unmodifiableMap(targets);
    }

    public PatchTransformer() {
        this.settings = null;
    }

    public PatchTransformer(PatchSettings settings) {
        this.settings = settings;
    }

    public static Map<String, MethodPatch> patches() {
        return PATCHES;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        List<MethodPatch> patches = TARGETS.get(transformedName);
        if (patches == null) {
            patches = TARGETS.get(name);
        }
        if (patches == null) {
            return basicClass;
        }
        byte[] result = basicClass;
        for (MethodPatch patch : patches) {
            result = apply(patch, result);
        }
        return result;
    }

    private byte[] apply(MethodPatch patch, byte[] basicClass) {
        try {
            PatchSettings options = settings == null ? PatchSettings.current() : settings;
            if (!patch.enabled(options)) {
                LOG.info("{}: disabled", patch.id.key);
                return basicClass;
            }
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            MethodPatch.Result result = patch.apply(node);
            if (result == MethodPatch.Result.APPLIED) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                byte[] output = writer.toByteArray();
                LOG.info("{}: applied to {}", patch.id.key, patch.className);
                return output;
            }
            if (result == MethodPatch.Result.ALREADY_PRESENT) {
                LOG.info("{}: recognized existing fix; skipped", patch.id.key);
            } else {
                LOG.warn("{}: unrecognized target code; skipped without changing {}", patch.id.key, patch.className);
            }
        } catch (RuntimeException | LinkageError error) {
            LOG.error("{}: transformation failed; retaining original class {}", patch.id.key, patch.className, error);
        }
        return basicClass;
    }
}
