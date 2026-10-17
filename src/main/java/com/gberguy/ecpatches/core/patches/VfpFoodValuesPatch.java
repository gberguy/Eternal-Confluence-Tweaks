package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodFingerprint;
import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import org.objectweb.asm.tree.*;

public final class VfpFoodValuesPatch extends MethodPatch {
    private static final String OWNER = "org/jwaresoftware/mcmods/vfp/common/VfpPantryItem";
    private static final String HELPER = "com/gberguy/ecpatches/runtime/VfpFoodValues";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String VALUES = "Lsqueek/applecore/api/food/FoodValues;";
    private static final String CONSUME = "(" + STACK + "IFLnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;)V";
    private static final String VALUES_ORIGINAL = "ee469a28e6a05f8496fe55506bfd503f543171bf3cb701f95bc4f9c2872cb5f0";
    private static final String VALUES_FIXED = "5c35f806a03c8db945fed60a5e447a6f595655eccb1840e577c99698628053cb";
    private static final String CONSUME_ORIGINAL = "c4baaecab519cc4b2b3c2c9ae31a001a2df3eaf84682b4d228453e67dcd29048";
    private static final String CONSUME_FIXED = "44b1761246c84f89b2594188a2edbe2764e0290f0ffe8360784daf7bd131d6d6";

    public VfpFoodValuesPatch() {
        super(PatchId.VFP_FOOD_VALUES, OWNER.replace('/', '.'), "onPortionConsumed", CONSUME,
                CONSUME_ORIGINAL, CONSUME_FIXED);
    }

    @Override
    protected boolean compatible(ClassNode owner, boolean fixed) {
        MethodNode values = find(owner, "getFoodValues", "(" + STACK + ")" + VALUES);
        return values != null && (fixed ? VALUES_FIXED : VALUES_ORIGINAL).equals(MethodFingerprint.of(values));
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
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("onConsumed")) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                call.owner = HELPER;
                call.name = "consume";
            }
        }
    }
}
