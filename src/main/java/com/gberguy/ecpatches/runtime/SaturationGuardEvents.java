package com.gberguy.ecpatches.runtime;

import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.FoodStats;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SaturationGuardEvents {
    private final PatchSettings settings;

    public SaturationGuardEvents(PatchSettings settings) {
        this.settings = settings;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !settings.enabled(PatchId.PLAYER_SATURATION)) return;
        FoodStats stats = event.player.getFoodStats();
        if (stats.getSaturationLevel() < 0.0F) SaturationField.clear(stats);
    }

    public static boolean shouldDrawSaturation(EntityPlayer player) {
        return !(player.getFoodStats().getSaturationLevel() < 0.0F);
    }

    private static final class SaturationField {
        private static final Logger LOG = LogManager.getLogger("EternalConfluenceTweaks");
        private static volatile Field field = find();

        private static Field find() {
            for (String name : new String[]{"foodSaturationLevel", "field_75125_b"}) {
                try {
                    Field candidate = FoodStats.class.getDeclaredField(name);
                    if (candidate.getType() != float.class || Modifier.isStatic(candidate.getModifiers())) {
                        throw new IllegalStateException("Unexpected saturation field type: " + candidate);
                    }
                    candidate.setAccessible(true);
                    return candidate;
                } catch (NoSuchFieldException missing) {
                    continue;
                } catch (RuntimeException error) {
                    LOG.error("clampNegativeSaturation: cannot access the FoodStats saturation field; correction disabled for this launch.", error);
                    return null;
                }
            }
            LOG.error("clampNegativeSaturation: FoodStats saturation field not found; correction disabled for this launch.");
            return null;
        }

        private static void clear(FoodStats stats) {
            Field target = field;
            if (target == null) return;
            try {
                target.setFloat(stats, 0.0F);
            } catch (IllegalAccessException | RuntimeException error) {
                field = null;
                LOG.error("clampNegativeSaturation: cannot write the FoodStats saturation field; correction disabled for this launch.", error);
            }
        }
    }
}
