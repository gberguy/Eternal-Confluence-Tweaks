package com.gberguy.ecpatches.runtime;

import com.google.common.collect.ImmutableSet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.JarFile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class AstralPerkNetworkTest {
    public enum TestSide { CLIENT, SERVER }
    public interface Text { }
    public static class Message implements Text {
        public final String text;
        public Message(String text) { this.text = text; }
    }
    public static class Manager {
        public boolean closed;
        public void closeChannel(Text message) { closed = true; }
        public void func_150718_a(Text message) { closeChannel(message); }
    }
    public static class ModLoader {
        public static boolean isModLoaded(String mod) { return false; }
    }
    public static class Buffer {
        public final ByteBuf data;
        public Buffer(ByteBuf data) { this.data = data; }
    }
    public static class Packet {
        private final ByteBuf data;
        public Packet(Buffer buffer, String channel) { data = buffer.data; }
        public ByteBuf payload() { return data; }
    }
    public static class Dispatcher {
        public final Manager manager;
        public boolean rejected;
        public Packet sent;
        public Dispatcher(Manager manager) { this.manager = manager; }
        public void sendProxy(Packet packet) { sent = packet; }
        public void rejectHandshake(String message) { rejected = true; }
    }
    public static class Handshake {
        public final TestSide side = TestSide.SERVER;
        public final Dispatcher dispatcher;
        public Handshake(Dispatcher dispatcher) { this.dispatcher = dispatcher; }
    }
    public static class CustomEvent {
        private final Object wrapped;
        public CustomEvent(Object wrapped) { this.wrapped = wrapped; }
        public Object getWrappedEvent() { return wrapped; }
    }
    public static class Registration {
        private final Manager manager;
        private final TestSide side;
        private final boolean supported;
        public Registration(Manager manager, TestSide side, boolean supported) { this.manager = manager; this.side = side; this.supported = supported; }
        public String getOperation() { return "REGISTER"; }
        public ImmutableSet<String> getRegistrations() { return supported ? ImmutableSet.of("ect_astral_v1") : ImmutableSet.of("another_channel"); }
        public TestSide getSide() { return side; }
        public Manager getManager() { return manager; }
    }
    public static class ClientPacket {
        private final Manager manager;
        private final Packet packet;
        public ClientPacket(Manager manager, Packet packet) { this.manager = manager; this.packet = packet; }
        public Manager getManager() { return manager; }
        public Packet getPacket() { return packet; }
    }
    public static class ServerPacket extends ClientPacket {
        public ServerPacket(Manager manager, Packet packet) { super(manager, packet); }
    }
    public static class Connected {
        private final Manager manager;
        public Connected(Manager manager) { this.manager = manager; }
        public Manager getManager() { return manager; }
    }
    public static class Disconnected extends Connected { public Disconnected(Manager manager) { super(manager); } }
    public static class ServerDisconnected extends Connected { public ServerDisconnected(Manager manager) { super(manager); } }
    public static class Bus { public void register(Object events) { } }
    public static class Forge { public static Bus EVENT_BUS = new Bus(); }
    public static class Channel { public void register(Object events) { } }
    public static class Registry {
        public static Registry INSTANCE = new Registry();
        public Channel newEventDrivenChannel(String name) { return new Channel(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sourceAndReleaseHandlersNegotiateBeforeLoginAndRejectMissingOrInvalidState(boolean release) throws Exception {
        Fixture f = new Fixture(release);
        f.network.getMethod("initialize").invoke(null);
        AstralPerkCurve curve = AstralPerkCurve.calculate(AstralFormula.DEFAULT, 100);
        Field server = f.network.getDeclaredField("serverCurve"); server.setAccessible(true); server.set(null, curve);
        Object manager = f.make(Manager.class);
        Object dispatcher = f.make(Dispatcher.class, manager);
        Object handshake = f.make(CustomEvent.class, f.make(Handshake.class, dispatcher));
        f.call("handshake", CustomEvent.class, handshake);
        assertTrue(f.type(Dispatcher.class).getField("rejected").getBoolean(dispatcher));
        f.type(Dispatcher.class).getField("rejected").setBoolean(dispatcher, false);
        Object serverSide = f.type(TestSide.class).getField("SERVER").get(null);
        Object clientSide = f.type(TestSide.class).getField("CLIENT").get(null);
        f.call("registration", Registration.class, f.make(Registration.class, manager, serverSide, true));
        f.call("registration", Registration.class, f.make(Registration.class, manager, serverSide, false));
        f.call("handshake", CustomEvent.class, handshake);
        assertFalse(f.type(Dispatcher.class).getField("rejected").getBoolean(dispatcher));
        Object packet = f.type(Dispatcher.class).getField("sent").get(dispatcher);
        assertNotNull(packet);
        ByteBuf payload = (ByteBuf) f.type(Packet.class).getMethod("payload").invoke(packet);
        byte[] wire = new byte[payload.readableBytes()]; payload.getBytes(0, wire);
        assertEquals(curve.thresholds, AstralPerkCurve.decode(wire).thresholds);
        f.call("registration", Registration.class, f.make(Registration.class, manager, clientSide, true));
        f.call("receive", ClientPacket.class, f.make(ClientPacket.class, manager, packet));
        assertEquals(0, payload.refCnt());
        f.call("connected", Connected.class, f.make(Connected.class, manager));
        assertFalse(f.type(Manager.class).getField("closed").getBoolean(manager));
        assertEquals(curve.thresholds, AstralPerkHooks.CLIENT.curve().thresholds);
        Object next = f.make(Manager.class);
        f.call("registration", Registration.class, f.make(Registration.class, next, clientSide, true));
        assertNull(AstralPerkHooks.CLIENT.curve());
        f.call("disconnected", Disconnected.class, f.make(Disconnected.class, manager));
        f.call("connected", Connected.class, f.make(Connected.class, next));
        assertTrue(f.type(Manager.class).getField("closed").getBoolean(next));
        Object invalid = f.make(Manager.class);
        f.call("registration", Registration.class, f.make(Registration.class, invalid, clientSide, true));
        ByteBuf corrupt = Unpooled.wrappedBuffer(new byte[12]);
        f.call("receive", ClientPacket.class, f.make(ClientPacket.class, invalid, f.make(Packet.class, f.make(Buffer.class, corrupt), "ect_astral_v1")));
        assertTrue(f.type(Manager.class).getField("closed").getBoolean(invalid));
        assertNull(AstralPerkHooks.CLIENT.curve());
        assertEquals(0, corrupt.refCnt());
        Object ordinary = f.make(Manager.class);
        f.call("connected", Connected.class, f.make(Connected.class, ordinary));
        assertFalse(f.type(Manager.class).getField("closed").getBoolean(ordinary));
        f.call("disconnected", Disconnected.class, f.make(Disconnected.class, ordinary));
    }

    private static final class Fixture extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();
        private final Map<String, String> names = new HashMap<>();
        private final Class<?> network;
        private final Object events;
        Fixture(boolean release) throws Exception {
            names.put(Type.getInternalName(TestSide.class), "net/minecraftforge/fml/relauncher/Side");
            names.put(Type.getInternalName(Text.class), "net/minecraft/util/text/ITextComponent");
            names.put(Type.getInternalName(Message.class), "net/minecraft/util/text/TextComponentString");
            names.put(Type.getInternalName(Manager.class), "net/minecraft/network/NetworkManager");
            names.put(Type.getInternalName(ModLoader.class), "net/minecraftforge/fml/common/Loader");
            names.put(Type.getInternalName(Buffer.class), "net/minecraft/network/PacketBuffer");
            names.put(Type.getInternalName(Packet.class), "net/minecraftforge/fml/common/network/internal/FMLProxyPacket");
            names.put(Type.getInternalName(Dispatcher.class), "net/minecraftforge/fml/common/network/handshake/NetworkDispatcher");
            names.put(Type.getInternalName(Handshake.class), "net/minecraftforge/fml/common/network/NetworkHandshakeEstablished");
            names.put(Type.getInternalName(CustomEvent.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$CustomNetworkEvent");
            names.put(Type.getInternalName(Registration.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$CustomPacketRegistrationEvent");
            names.put(Type.getInternalName(ClientPacket.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$ClientCustomPacketEvent");
            names.put(Type.getInternalName(ServerPacket.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$ServerCustomPacketEvent");
            names.put(Type.getInternalName(Connected.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$ClientConnectedToServerEvent");
            names.put(Type.getInternalName(Disconnected.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$ClientDisconnectionFromServerEvent");
            names.put(Type.getInternalName(ServerDisconnected.class), "net/minecraftforge/fml/common/network/FMLNetworkEvent$ServerDisconnectionFromClientEvent");
            names.put(Type.getInternalName(Bus.class), "net/minecraftforge/fml/common/eventhandler/EventBus");
            names.put(Type.getInternalName(Forge.class), "net/minecraftforge/common/MinecraftForge");
            names.put(Type.getInternalName(Channel.class), "net/minecraftforge/fml/common/network/FMLEventChannel");
            names.put(Type.getInternalName(Registry.class), "net/minecraftforge/fml/common/network/NetworkRegistry");
            for (Class<?> type : new Class<?>[]{TestSide.class, Text.class, Message.class, Manager.class, ModLoader.class, Buffer.class,
                    Packet.class, Dispatcher.class, Handshake.class, CustomEvent.class, Registration.class, ClientPacket.class,
                    ServerPacket.class, Connected.class, Disconnected.class, ServerDisconnected.class, Bus.class, Forge.class, Channel.class, Registry.class}) {
                ClassReader reader = new ClassReader(read(type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")));
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassRemapper(writer, new SimpleRemapper(names)), 0);
                definitions.put(names.get(Type.getInternalName(type)).replace('/', '.'), writer.toByteArray());
            }
            String name = "com/gberguy/ecpatches/runtime/AstralPerkNetwork";
            byte[] code;
            if (release) {
                String path = System.getProperty("ecpatches.releaseJar");
                assumeTrue(path != null, "Standalone release artifact not supplied");
                try (JarFile jar = new JarFile(path)) { code = read(jar.getInputStream(jar.getJarEntry(name + ".class"))); }
            } else code = read(getClass().getResourceAsStream("/" + name + ".class"));
            definitions.put(name.replace('/', '.'), code);
            network = loadClass(name.replace('/', '.'));
            Constructor<?> constructor = network.getDeclaredConstructor(); constructor.setAccessible(true); events = constructor.newInstance();
        }
        Class<?> type(Class<?> fixture) throws Exception { return loadClass(names.get(Type.getInternalName(fixture)).replace('/', '.')); }
        Object make(Class<?> fixture, Object... args) throws Exception { return type(fixture).getConstructors()[0].newInstance(args); }
        void call(String name, Class<?> event, Object argument) throws Exception { network.getMethod(name, type(event)).invoke(events, argument); }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!definitions.containsKey(name)) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) { byte[] data = definitions.get(name); type = defineClass(name, data, 0, data.length); }
            if (resolve) resolveClass(type);
            return type;
        }
    }
    private static byte[] read(InputStream input) throws Exception {
        try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }
}
