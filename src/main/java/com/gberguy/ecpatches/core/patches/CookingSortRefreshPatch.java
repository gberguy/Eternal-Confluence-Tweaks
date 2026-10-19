package com.gberguy.ecpatches.core.patches;

import com.gberguy.ecpatches.core.MethodPatch;
import com.gberguy.ecpatches.core.PatchId;
import org.objectweb.asm.tree.*;

public final class CookingSortRefreshPatch extends MethodPatch {
    public CookingSortRefreshPatch() {
        super(PatchId.CFB_MISSING_TOOLS_SORT,
                "net.blay09.mods.cookingforblockheads.container.ContainerRecipeBook", "setItemList",
                "(Ljava/util/Collection;)V",
                new String[]{"1585ac55d2ccf5966db7429bc97df24751c85c6ebcd53d1fd11ba44107347836",
                        "f9b3a5b1255d3af8aafcdb7c619900cdf18bd86092bcfb2b6e301cf7b7179742"},
                "99fdcd2a960c1e4d0279679ae831bed4af68923d42a390d6889a89ffb30e588d",
                "e0e72641af854cb848bd23736908afd50e9cd569fbee16d1b926534f7b703dc3");
    }

    @Override
    protected void edit(MethodNode method, boolean deobfuscated) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(className.replace('.', '/')) && call.name.equals("populateRecipeSlots")
                        && call.desc.equals("()V")) {
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(ALOAD, 0));
                    hook.add(new FieldInsnNode(GETFIELD, call.owner, "filteredItems", "Ljava/util/List;"));
                    hook.add(new VarInsnNode(ALOAD, 0));
                    hook.add(new FieldInsnNode(GETFIELD, call.owner, "currentSorting", "Ljava/util/Comparator;"));
                    hook.add(new MethodInsnNode(INVOKESTATIC, "com/gberguy/ecpatches/runtime/CookingSortRefresh",
                            "restoreOrder", "(Ljava/util/List;Ljava/util/Comparator;)V", false));
                    method.instructions.insertBefore(call, hook);
                    return;
                }
            }
        }
        throw new IllegalStateException("Missing Cooking for Blockheads recipe slot refresh");
    }
}
