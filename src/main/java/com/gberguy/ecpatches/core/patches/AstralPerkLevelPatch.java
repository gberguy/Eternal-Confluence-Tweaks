package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import org.objectweb.asm.tree.*;

public final class AstralPerkLevelPatch extends MethodPatch {
    public static final String TARGET = "hellfirepvp.astralsorcery.common.constellation.perk.PerkLevelManager";
    private static final String OWNER = TARGET.replace('.', '/');
    private static final String HOOK = "com/gberguy/ecpatches/runtime/AstralPerkHooks";
    private static final String PLAYER = "Lnet/minecraft/entity/player/EntityPlayer;";
    private final int playerSlot;

    public static MethodPatch[] create() {
        return new MethodPatch[] {
            new AstralPerkLevelPatch("getLevel", "(J" + PLAYER + ")I", 3, "9afa598e3aab613427b8880048df22a148b8bf1b666f97f5dcec8358aed97bd6", "b2f0f7861f2ec3457d2511ea989eaa5a27f541de0ee796516e75c9c409126a04"),
            new AstralPerkLevelPatch("getExpForLevel", "(I" + PLAYER + ")J", 2, "4cb332a205989016f531bff101aaee22b29e2cefd5ad8945a583c38b509fc4b5", "a27464f92f76230f9e7c67bc82df1ec35b68d94dc39fe96df827bd733a0670fd"),
            new AstralPerkLevelPatch("getNextLevelPercent", "(D" + PLAYER + ")F", 3, "8ad5053ab1038a9e86fa4d731eb63e4ab6715c30b8678c518b61579e423ee5dc", "bffe61df885380df7905a7d8e4171037b5ddec01b9fd54eeb07f544bf4cc714c"),
            new AstralPerkLevelPatch("getLevelCapFor", "(" + PLAYER + ")I", 0, "439da5fa3ac9d89be5cd3c4b7b5b2824fbfbd0aeec1be27f54d493f8c121e644", "ee25165636cccfde5f7820f004678d8fcceb736929ff22fd080ae31137f49835"),
            new AstralPerkLevelPatch("resolveLevelCap", "(" + PLAYER + ")I", 0, "f8993b29637d58d27d82db6e96f3c25d0cc67e40c96a8aabd620362d743868ae", "c3e637aed95f090f09631cd98ea573b1ccd3c8c96257e0983653bf59a16262a4")
        };
    }

    private AstralPerkLevelPatch(String name, String descriptor, int slot, String original, String fixed) {
        super(PatchId.ASTRAL_PERK_FORMULA, TARGET, name, descriptor, original, fixed);
        this.playerSlot = slot;
    }

    @Override
    public boolean enabled(PatchSettings settings) { return true; }

    @Override
    protected boolean compatible(ClassNode owner, boolean fixed) {
        boolean marker = false, map = false, cap = false;
        for (Object value : owner.fields) {
            FieldNode field = (FieldNode) value;
            if (field.name.equals("ect$astral$" + methodName)) marker = field.desc.equals("Z") && Integer.valueOf(1).equals(field.value);
            if (field.name.equals("totalExpLevelRequired")) map = field.desc.equals("Ljava/util/Map;");
            if (field.name.equals("LEVEL_CAP")) cap = field.desc.equals("I") && (field.access & ACC_STATIC) != 0;
        }
        return map && cap && marker == fixed;
    }

    @Override
    protected void edit(ClassNode owner, MethodNode method, boolean deobfuscated) {
        edit(method, deobfuscated);
        owner.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                "ect$astral$" + methodName, "Z", null, 1));
    }

    @Override
    protected void edit(MethodNode method, boolean deobfuscated) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (!field.owner.equals(OWNER)) continue;
            InsnList hook = new InsnList();
            if (field.getOpcode() == GETFIELD && field.name.equals("totalExpLevelRequired")) {
                hook.add(new FieldInsnNode(GETSTATIC, OWNER, "LEVEL_CAP", "I"));
                hook.add(new VarInsnNode(ALOAD, playerSlot));
                hook.add(new MethodInsnNode(INVOKESTATIC, HOOK, "table", "(Ljava/util/Map;I" + PLAYER + ")Ljava/util/Map;", false));
            } else if (field.getOpcode() == GETSTATIC && field.name.equals("LEVEL_CAP")) {
                hook.add(new VarInsnNode(ALOAD, playerSlot));
                hook.add(new MethodInsnNode(INVOKESTATIC, HOOK, "globalCap", "(I" + PLAYER + ")I", false));
            }
            method.instructions.insert(instruction, hook);
        }
    }
}
