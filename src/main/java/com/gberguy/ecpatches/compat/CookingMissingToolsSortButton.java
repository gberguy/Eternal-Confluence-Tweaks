package com.gberguy.ecpatches.compat;

import com.gberguy.ecpatches.Tags;
import com.gberguy.ecpatches.runtime.CookingSortRefresh;
import java.util.Comparator;
import net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI;
import net.blay09.mods.cookingforblockheads.api.FoodRecipeWithStatus;
import net.blay09.mods.cookingforblockheads.api.ISortButton;
import net.blay09.mods.cookingforblockheads.api.RecipeStatus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

public final class CookingMissingToolsSortButton implements ISortButton,
        Comparator<FoodRecipeWithStatus>, CookingSortRefresh.Ordered {
    private static final ResourceLocation ICON = new ResourceLocation(Tags.MOD_ID, "textures/gui/cfb_ec_wrench.png");

    public CookingMissingToolsSortButton() {
        CookingForBlockheadsAPI.addSortButton(this);
    }

    @Override
    public ResourceLocation getIcon() { return ICON; }

    @Override
    public String getTooltip() { return "eternalconfluencetweaks:sort.missing_tools_last"; }

    @Override
    public Comparator<FoodRecipeWithStatus> getComparator(EntityPlayer player) { return this; }

    @Override
    public int getIconTextureX() { return 0; }

    @Override
    public int getIconTextureY() { return 0; }

    @Override
    public int compare(FoodRecipeWithStatus left, FoodRecipeWithStatus right) {
        int group = Integer.compare(rank(left.getStatus()), rank(right.getStatus()));
        return group != 0 ? group : String.CASE_INSENSITIVE_ORDER.compare(
                left.getOutputItem().getDisplayName(), right.getOutputItem().getDisplayName());
    }

    private static int rank(RecipeStatus status) {
        if (status == RecipeStatus.AVAILABLE) return 0;
        return status == RecipeStatus.MISSING_TOOLS ? 2 : 1;
    }
}
