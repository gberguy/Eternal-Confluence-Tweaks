package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodFingerprint;
import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import org.objectweb.asm.tree.*;

public final class VfpFoodPatch extends MethodPatch {
    private static final String OWNER = "org/jwaresoftware/mcmods/vfp/common/VfpPantryItem";
    private static final String HELPER = "com/gberguy/ecpatches/runtime/VfpFoodValues";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String VALUES = "Lsqueek/applecore/api/food/FoodValues;";
    private static final String CONSUME = "(" + STACK + "IFLnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)V";
    private static final String VALUES_ORIGINAL = "ee469a28e6a05f8496fe55506bfd503f543171bf3cb701f95bc4f9c2872cb5f0";
    private static final String VALUES_FIXED = "5c35f806a03c8db945fed60a5e447a6f595655eccb1840e577c99698628053cb";
    private static final String CONSUME_ORIGINAL = "c4baaecab519cc4b2b3c2c9ae31a001a2df3eaf84682b4d228453e67dcd29048";
    private static final String CONSUME_FIXED = "44b1761246c84f89b2594188a2edbe2764e0290f0ffe8360784daf7bd131d6d6";

    public VfpFoodPatch() {
        super(PatchId.VFP_FOOD, OWNER.replace('/', '.'), "func_77654_b",
                "(" + STACK + "Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;)" + STACK,
                "b870a3cdf048f8cc55ea2e1fefd79ba74182991b6a0fe8579ac6f45d70325d42",
                "ca2444381cdddda1df7ebda3d8cc31357ae34596dc1c04b02c978ec741002d55");
    }

    @Override
    protected boolean compatible(ClassNode owner, boolean fixed) {
        MethodNode values = find(owner, "getFoodValues", "(" + STACK + ")" + VALUES);
        MethodNode consume = find(owner, "onPortionConsumed", CONSUME);
        return values != null && consume != null
                && (fixed ? VALUES_FIXED : VALUES_ORIGINAL).equals(MethodFingerprint.of(values))
                && (fixed ? CONSUME_FIXED : CONSUME_ORIGINAL).equals(MethodFingerprint.of(consume));
    }

    private static MethodNode find(ClassNode owner, String name, String descriptor) {
        MethodNode found = null;
        for (Object value : owner.methods) {
            MethodNode method = (MethodNode) value;
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                if (found != null) return null;
                found = method;
            }
        }
        return found;
    }

    @Override
    protected void edit(ClassNode owner, MethodNode method, boolean deobfuscated) {
        edit(method, deobfuscated);
        MethodNode consume = find(owner, "onPortionConsumed", CONSUME);
        for (AbstractInsnNode instruction : consume.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("onConsumed")) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                call.owner = HELPER;
                call.name = "consume";
            }
        }
        MethodNode values = find(owner, "getFoodValues", "(" + STACK + ")" + VALUES);
        values.instructions.clear();
        values.tryCatchBlocks.clear();
        if (values.localVariables != null) values.localVariables.clear();
        InsnList code = values.instructions;
        LabelNode ordinary = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKESTATIC, HELPER, "portion", "(" + STACK + ")Lnet/minecraft/item/ItemFood;", false));
        code.add(new VarInsnNode(ASTORE, 2));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new JumpInsnNode(IFNONNULL, ordinary));
        code.add(new VarInsnNode(ALOAD, 0));
        code.add(new VarInsnNode(ASTORE, 2));
        code.add(ordinary);
        code.add(new FrameNode(F_APPEND, 1, new Object[]{"net/minecraft/item/ItemFood"}, 0, null));
        code.add(new TypeInsnNode(NEW, "squeek/applecore/api/food/FoodValues"));
        code.add(new InsnNode(DUP));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/item/ItemFood", name("func_150905_g", deobfuscated), "(" + STACK + ")I", false));
        code.add(new VarInsnNode(ALOAD, 2));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/item/ItemFood", name("func_150906_h", deobfuscated), "(" + STACK + ")F", false));
        code.add(new MethodInsnNode(INVOKESPECIAL, "squeek/applecore/api/food/FoodValues", "<init>", "(IF)V", false));
        code.add(new InsnNode(ARETURN));
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
