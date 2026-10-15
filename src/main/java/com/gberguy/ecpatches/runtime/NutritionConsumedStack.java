package com.gberguy.ecpatches.runtime;

import net.minecraft.item.ItemStack;

public final class NutritionConsumedStack {
    private NutritionConsumedStack() {
    }

    public static ItemStack snapshot(ItemStack stack) {
        if (stack == null || stack == ItemStack.EMPTY || stack.getCount() != 0) {
            return stack;
        }
        ItemStack copy;
        try {
            stack.setCount(1);
            copy = stack.copy();
        } finally {
            stack.setCount(0);
        }
        return copy == ItemStack.EMPTY || copy == stack || copy.isEmpty() ? stack : copy;
    }
}
