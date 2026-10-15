package com.gberguy.ecpatches;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import com.gberguy.ecpatches.runtime.GhostlyEvents;
import com.gberguy.ecpatches.runtime.SaturationGuardEvents;
import com.gberguy.ecpatches.runtime.WitcheryVillagesToroGuardEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public class EternalConfluenceTweaks {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    /**
     * <a href="https://cleanroommc.com/wiki/forge-mod-development/event#overview">
     *     Take a look at how many FMLStateEvents you can listen to via the @Mod.EventHandler annotation here
     * </a>
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PatchSettings settings = PatchSettings.current();
        MinecraftForge.EVENT_BUS.register(new GhostlyEvents(settings));
        if (settings.enabled(PatchId.PLAYER_SATURATION)) {
            MinecraftForge.EVENT_BUS.register(new SaturationGuardEvents(settings));
        }
        if (settings.enabled(PatchId.WITCHERY_VILLAGES_TORO_GUARDS)
                && Loader.isModLoaded("witcherywalls") && Loader.isModLoaded("toroquest")) {
            MinecraftForge.EVENT_BUS.register(new WitcheryVillagesToroGuardEvents(settings));
        }
        for (PatchId patch : PatchId.values()) {
            boolean targetPresent = patch == PatchId.WITCHERY_VILLAGES_TORO_GUARDS
                    || patch == PatchId.WITCHERY_VILLAGES_TORO_WALLS
                    ? Loader.isModLoaded("witcherywalls") && Loader.isModLoaded("toroquest")
                    : Loader.isModLoaded(patch.modId);
            LOGGER.info("{}: {}, target {}", patch.key, settings.enabled(patch) ? "enabled" : "disabled",
                    targetPresent ? "present" : "absent");
        }
    }

}
