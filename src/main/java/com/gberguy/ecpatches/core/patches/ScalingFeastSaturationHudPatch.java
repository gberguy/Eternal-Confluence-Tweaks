package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import org.objectweb.asm.tree.*;

public final class ScalingFeastSaturationHudPatch extends MethodPatch {
    public ScalingFeastSaturationHudPatch() {
        super(PatchId.SCALING_FEAST_SATURATION_HUD,
                "yeelp.scalingfeast.hud.ScalingFeastUpperSaturationBarDrawable", "shouldDraw",
                "(Lnet/minecraft/entity/player/EntityPlayer;)Z",
                "e612c917a2a2bb307d89eefb18b6519d72e0aaa2669ac1023cf6ec9e3701c76a",
                "c5a9b0d61ff6eeea59df64ce911fc4646fdbe01169d267dafa2e6e456c48108a");
    }

    @Override
    protected void edit(MethodNode method, boolean deobfuscated) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == ICONST_1) {
                method.instructions.insertBefore(instruction, new VarInsnNode(ALOAD, 1));
                method.instructions.set(instruction, new MethodInsnNode(INVOKESTATIC,
                        "com/gberguy/ecpatches/runtime/SaturationGuardEvents", "shouldDrawSaturation",
                        descriptor, false));
                return;
            }
        }
        throw new IllegalStateException("Missing Scaling Feast saturation draw decision");
    }
}
