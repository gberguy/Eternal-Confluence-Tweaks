package com.gberguy.ecpatches.runtime;

import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.LogManager;

public final class AstralPerkHooks {
    public static final AstralPerkSession CLIENT = new AstralPerkSession();
    private static final String MANAGER = "hellfirepvp.astralsorcery.common.constellation.perk.PerkLevelManager";
    private static volatile AstralPerkCurve server;
    private static volatile AstralPerkCurve activeServer;

    private AstralPerkHooks() { }

    private static boolean client(EntityPlayer player) {
        if (player != null) return player.world.isRemote;
        FMLCommonHandler handler = FMLCommonHandler.instance();
        return handler.getSide().isClient() && handler.getEffectiveSide().isClient();
    }

    public static Map<Integer, Long> table(Map<Integer, Long> original, int cap, EntityPlayer player) {
        AstralPerkCurve curve = client(player) ? CLIENT.curve() : activeServer;
        if (curve == null && !client(player)) curve = localCurve(cap);
        return curve == null ? original : curve.thresholds;
    }

    public static int globalCap(int original, EntityPlayer player) {
        AstralPerkCurve curve = client(player) ? CLIENT.curve() : activeServer;
        return curve == null ? original : curve.cap;
    }

    private static AstralPerkCurve localCurve(int cap) {
        PatchSettings settings = PatchSettings.current();
        if (!settings.enabled(PatchId.ASTRAL_PERK_FORMULA)) return null;
        AstralPerkCurve curve = server;
        if (curve == null || curve.cap != cap) {
            synchronized (AstralPerkHooks.class) {
                curve = server;
                if (curve == null || curve.cap != cap) {
                    curve = AstralPerkCurve.calculate(settings.astralPerkLevelFormula(), cap);
                    server = curve;
                    LogManager.getLogger("EternalConfluenceTweaks").info("Astral perk formula effective: {}; cap={}; first threshold={}; last threshold={}",
                            settings.astralPerkLevelFormula(), cap, curve.thresholds.get(1), curve.thresholds.get(cap));
                }
            }
        }
        return curve;
    }

    public static void verifyInstallation() {
        try {
            Class<?> type = Class.forName(MANAGER);
            for (String method : new String[]{"getLevel", "getExpForLevel", "getNextLevelPercent", "getLevelCapFor", "resolveLevelCap"}) {
                if (!type.getField("ect$astral$" + method).getBoolean(null)) throw new IllegalStateException("Missing Astral hook " + method);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ECT Astral perk formula hooks unavailable. Expected Astral Sorcery 1.12.2-1.10.27; cannot safely synchronize leveling.", error);
        }
    }

    public static AstralPerkCurve prepareServer() {
        try {
            Class<?> type = Class.forName(MANAGER);
            Field field = type.getDeclaredField("LEVEL_CAP");
            field.setAccessible(true);
            int cap = field.getInt(null);
            Object instance = type.getField("INSTANCE").get(null);
            Method ensure = type.getDeclaredMethod("ensureLevels");
            ensure.setAccessible(true);
            ensure.invoke(instance);
            AstralPerkCurve custom = localCurve(cap);
            if (custom != null) return custom;
            Field table = type.getDeclaredField("totalExpLevelRequired");
            table.setAccessible(true);
            Map<?, ?> original = (Map<?, ?>) table.get(instance);
            long[] thresholds = new long[cap];
            for (int i = 1; i <= cap; i++) thresholds[i - 1] = ((Number) original.get(i)).longValue();
            return new AstralPerkCurve(false, thresholds);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot prepare server Astral perk thresholds", error);
        }
    }

    public static void activateServer(AstralPerkCurve curve) { activeServer = curve; }

    public static void stopServer() { activeServer = null; server = null; }
}
