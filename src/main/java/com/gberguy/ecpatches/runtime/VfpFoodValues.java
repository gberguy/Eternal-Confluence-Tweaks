package com.gberguy.ecpatches.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;

public final class VfpFoodValues {
    private static final ThreadLocal<Serving> CURRENT = new ThreadLocal<>();
    private static boolean warned;

    private VfpFoodValues() {
    }

    public static int maximumHunger(EntityPlayer player) {
        if (Loader.isModLoaded("applecore")) {
            try {
                if (AppleCore.MAXIMUM != null && AppleCore.ACCESSOR != null) {
                    Object accessor = AppleCore.ACCESSOR.get(null);
                    if (accessor != null) return Math.max(0, (Integer) AppleCore.MAXIMUM.invoke(accessor, player));
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
            }
        }
        return 20;
    }

    public static boolean validServing(int amount, int total, int used) {
        return amount > 0 && used >= 0 && used < total;
    }

    public static int hungerNeeded(EntityPlayer player, int portion, boolean allowed) {
        int needed = maximumHunger(player) - player.getFoodStats().getFoodLevel();
        return needed <= 0 && allowed ? Math.max(0, portion) : needed;
    }

    public static ItemFood portion(ItemStack stack) {
        Serving serving = CURRENT.get();
        return serving != null && serving.stack == stack ? serving.food : null;
    }

    public static void consume(ItemStack stack, int hunger, float saturation, World world, EntityPlayer player) {
        if (!Loader.isModLoaded("applecore")) {
            player.getFoodStats().addStats(hunger, saturation);
            return;
        }
        Serving previous = CURRENT.get();
        Serving serving = new Serving(stack, new ItemFood(hunger, saturation, false));
        CURRENT.set(serving);
        try {
            player.getFoodStats().addStats(serving.food, stack);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    private static synchronized void warn(Throwable error) {
        if (!warned) {
            warned = true;
            LogManager.getLogger("EternalConfluenceTweaks").warn(
                    "vanillaFoodPantryServings: AppleCore maximum hunger unavailable; using 20", error);
        }
    }

    private static final class Serving {
        final ItemStack stack;
        final ItemFood food;

        Serving(ItemStack stack, ItemFood food) {
            this.stack = stack;
            this.food = food;
        }
    }

    private static final class AppleCore {
        static final Field ACCESSOR;
        static final Method MAXIMUM;

        static {
            Field accessor = null;
            Method maximum = null;
            try {
                ClassLoader loader = VfpFoodValues.class.getClassLoader();
                accessor = Class.forName("squeek.applecore.api.AppleCoreAPI", false, loader).getField("accessor");
                maximum = Class.forName("squeek.applecore.api.IAppleCoreAccessor", false, loader)
                        .getMethod("getMaxHunger", EntityPlayer.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
            }
            ACCESSOR = accessor;
            MAXIMUM = maximum;
        }
    }
}
