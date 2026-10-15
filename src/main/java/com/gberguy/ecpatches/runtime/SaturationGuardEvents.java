package com.gberguy.ecpatches.runtime;

import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class SaturationGuardEvents {
    private final PatchSettings settings;

    public SaturationGuardEvents(PatchSettings settings) {
        this.settings = settings;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !settings.enabled(PatchId.PLAYER_SATURATION)) return;
        FoodStats stats = event.player.getFoodStats();
        if (stats.getSaturationLevel() < 0.0F) stats.setFoodSaturationLevel(0.0F);
    }

    public static boolean shouldDrawSaturation(EntityPlayer player) {
        return !(player.getFoodStats().getSaturationLevel() < 0.0F);
    }
}
