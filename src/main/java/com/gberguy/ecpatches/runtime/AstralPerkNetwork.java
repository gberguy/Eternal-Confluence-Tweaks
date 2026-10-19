package com.gberguy.ecpatches.runtime;

import com.gberguy.ecpatches.core.PatchId;
import com.gberguy.ecpatches.core.PatchSettings;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkHandshakeEstablished;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;

public final class AstralPerkNetwork {
    private static final String CHANNEL = "ect_astral_v1";
    private static volatile AstralPerkCurve serverCurve;
    private static boolean installed;
    private final Set<NetworkManager> supportedClients = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<NetworkManager, Boolean>()));

    private AstralPerkNetwork() { }

    public static void initialize() {
        if (!Loader.isModLoaded("astralsorcery")) return;
        if (Loader.isModLoaded("astral-level-nerf")) {
            if (PatchSettings.current().enabled(PatchId.ASTRAL_PERK_FORMULA)) {
                throw new IllegalStateException("Remove Astral Level Nerf before enabling ECT astralPerkLevelFormulaEnabled; both change perk leveling.");
            }
            LogManager.getLogger("EternalConfluenceTweaks").info("Astral formula disabled; leaving Astral Level Nerf in control of leveling without ECT synchronization");
            return;
        }
        try {
            AstralPerkHooks.verifyInstallation();
        } catch (IllegalStateException unsupported) {
            if (PatchSettings.current().enabled(PatchId.ASTRAL_PERK_FORMULA)) throw unsupported;
            LogManager.getLogger("EternalConfluenceTweaks").warn("Astral formula disabled and synchronization hooks unavailable; leaving Astral progression unchanged", unsupported);
            return;
        }
        FMLEventChannel channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNEL);
        AstralPerkNetwork events = new AstralPerkNetwork();
        channel.register(events);
        MinecraftForge.EVENT_BUS.register(events);
        installed = true;
    }

    public static void startServer() {
        if (installed) {
            serverCurve = AstralPerkHooks.prepareServer();
            AstralPerkHooks.activateServer(serverCurve);
        }
    }

    public static void stopServer() { serverCurve = null; AstralPerkHooks.stopServer(); }

    @SubscribeEvent
    public void registration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        if (!event.getOperation().equals("REGISTER")) return;
        boolean supported = event.getRegistrations().contains(CHANNEL);
        if (event.getSide() == Side.CLIENT) {
            AstralPerkHooks.CLIENT.observe(event.getManager(), supported);
        } else if (supported) supportedClients.add(event.getManager());
    }

    @SubscribeEvent
    public void handshake(FMLNetworkEvent.CustomNetworkEvent event) {
        if (!(event.getWrappedEvent() instanceof NetworkHandshakeEstablished)) return;
        NetworkHandshakeEstablished handshake = (NetworkHandshakeEstablished) event.getWrappedEvent();
        if (handshake.side != Side.SERVER) return;
        AstralPerkCurve curve = serverCurve;
        if (curve == null) {
            handshake.dispatcher.rejectHandshake("ECT Astral thresholds are not ready on the server.");
            return;
        }
        boolean supported = supportedClients.contains(handshake.dispatcher.manager);
        if (curve.custom && !supported) {
            handshake.dispatcher.rejectHandshake("This server requires ECT with Astral perk formula synchronization.");
            return;
        }
        if (!supported) return;
        handshake.dispatcher.sendProxy(new FMLProxyPacket(new PacketBuffer(Unpooled.wrappedBuffer(curve.encode())), CHANNEL));
    }

    @SubscribeEvent
    public void receive(FMLNetworkEvent.ClientCustomPacketEvent event) {
        try {
            int length = event.getPacket().payload().readableBytes();
            if (length < 12 || length > 812) throw new IllegalArgumentException("Invalid Astral curve packet size");
            byte[] data = new byte[length];
            event.getPacket().payload().readBytes(data);
            AstralPerkCurve curve = AstralPerkCurve.decode(data);
            if (curve.custom && Loader.isModLoaded("astral-level-nerf")) throw new IllegalArgumentException("Remove Astral Level Nerf to join this server");
            if (!AstralPerkHooks.CLIENT.accept(event.getManager(), curve)) throw new IllegalArgumentException("Unexpected or stale Astral curve packet");
            LogManager.getLogger("EternalConfluenceTweaks").info("Using server Astral perk thresholds: custom={}, cap={}, first={}, last={}; local formula ignored",
                    curve.custom, curve.cap, curve.thresholds.get(1), curve.thresholds.get(curve.cap));
        } catch (IllegalArgumentException error) {
            close(event.getManager(), "ECT Astral threshold synchronization failed: " + error.getMessage());
        } finally {
            event.getPacket().payload().release();
        }
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        AstralPerkHooks.CLIENT.observe(event.getManager(), false);
        if (AstralPerkHooks.CLIENT.waiting(event.getManager())) {
            close(event.getManager(), "Server did not supply ECT Astral perk thresholds before login. Update ECT on both sides.");
        }
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        AstralPerkHooks.CLIENT.end(event.getManager());
    }

    @SubscribeEvent
    public void serverDisconnected(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) {
        supportedClients.remove(event.getManager());
    }

    @SubscribeEvent
    public void unexpectedClientPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
        event.getPacket().payload().release();
        close(event.getManager(), "ECT Astral perk thresholds are server-authoritative; client updates are not accepted.");
    }

    private static void close(NetworkManager manager, String message) {
        LogManager.getLogger("EternalConfluenceTweaks").error(message);
        manager.closeChannel(new TextComponentString(message));
        AstralPerkHooks.CLIENT.end(manager);
    }
}
