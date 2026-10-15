package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import org.objectweb.asm.tree.*;

public final class NutritionLunchboxPatch extends MethodPatch {
    public NutritionLunchboxPatch() {
        super(PatchId.NUTRITION_LUNCHBOX, "ca.wescook.nutrition.events.EventEatFood", "finishUsingItem",
                "(Lnet/minecraftforge/event/entity/living/LivingEntityUseItemEvent$Finish;)V",
                "a084ae61559540e8e00f8aa21b6e0ef8c50898005f325ed9317cf8d91ec39145",
                "6ed858f2ccb2b400557cba415b76d6b44669ef62d2313d16a0736b557221e0e8");
    }

    @Override
    protected void edit(MethodNode method, boolean deobfuscated) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == INVOKEVIRTUAL
                        && "net/minecraftforge/event/entity/living/LivingEntityUseItemEvent$Finish".equals(call.owner)
                        && "getItem".equals(call.name) && "()Lnet/minecraft/item/ItemStack;".equals(call.desc)) {
                    method.instructions.insert(call, new MethodInsnNode(INVOKESTATIC,
                            "com/gberguy/ecpatches/runtime/NutritionConsumedStack", "snapshot",
                            "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", false));
                    return;
                }
            }
        }
        throw new IllegalStateException("Missing Nutrition consumed food stack");
    }
}
