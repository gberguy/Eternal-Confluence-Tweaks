package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import org.objectweb.asm.tree.*;

public final class VfpFoodPatch extends MethodPatch {
    private static final String OWNER = "org/jwaresoftware/mcmods/vfp/common/VfpPantryItem";
    private static final String HELPER = "com/gberguy/ecpatches/runtime/VfpFoodValues";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";

    public VfpFoodPatch() {
        super(PatchId.VFP_FOOD, OWNER.replace('/', '.'), "func_77654_b",
                "(" + STACK + "Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)" + STACK,
                "b870a3cdf048f8cc55ea2e1fefd79ba74182991b6a0fe8579ac6f45d70325d42",
                "ca2444381cdddda1df7ebda3d8cc31357ae34596dc1c04b02c978ec741002d55");
    }

    @Override
    protected void edit(MethodNode method, boolean deobfuscated) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof IntInsnNode && ((IntInsnNode) instruction).operand == 20) {
                AbstractInsnNode remove = next(instruction);
                for (int i = 0; i < 4; i++) {
                    AbstractInsnNode following = next(remove);
                    method.instructions.remove(remove);
                    remove = following;
                }
                InsnList needed = new InsnList();
                needed.add(new VarInsnNode(ALOAD, 5));
                needed.add(new VarInsnNode(ILOAD, 9));
                needed.add(new VarInsnNode(ALOAD, 0));
                needed.add(new VarInsnNode(ALOAD, 1));
                needed.add(new VarInsnNode(ALOAD, 2));
                needed.add(new VarInsnNode(ALOAD, 5));
                needed.add(new MethodInsnNode(INVOKEVIRTUAL, OWNER, "canConsume",
                        "(" + STACK + "Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)Z", false));
                needed.add(new MethodInsnNode(INVOKESTATIC, HELPER, "hungerNeeded",
                        "(Lnet/minecraft/entity/player/EntityPlayer;IZ)I", false));
                method.instructions.insertBefore(instruction, needed);
                method.instructions.remove(instruction);
            }
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("getFoodPointsUsed")) {
                AbstractInsnNode start = instruction.getPrevious().getPrevious();
                InsnList guard = new InsnList();
                LabelNode valid = new LabelNode();
                guard.add(new VarInsnNode(ILOAD, 11));
                guard.add(new VarInsnNode(ALOAD, 0));
                guard.add(new VarInsnNode(ALOAD, 1));
                guard.add(new MethodInsnNode(INVOKEVIRTUAL, OWNER, "getTotalFoodPoints", "(" + STACK + ")I", false));
                guard.add(new VarInsnNode(ALOAD, 0));
                guard.add(new VarInsnNode(ALOAD, 1));
                guard.add(new MethodInsnNode(INVOKEVIRTUAL, OWNER, "getFoodPointsUsed", "(" + STACK + ")I", false));
                guard.add(new MethodInsnNode(INVOKESTATIC, HELPER, "validServing", "(III)Z", false));
                guard.add(new JumpInsnNode(IFNE, valid));
                guard.add(new VarInsnNode(ALOAD, 1));
                guard.add(new InsnNode(ARETURN));
                guard.add(valid);
                guard.add(new FrameNode(F_SAME, 0, null, 0, null));
                method.instructions.insertBefore(start, guard);
            }
        }
    }
}
