package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.visual.servermixin.LoginConnectionAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class CityVisualMode implements ModInitializer {
    private record Proof(CityVisualProtocol.Offer offer, boolean ready) {}
    private static final Map<ClientConnection, Proof> PROOFS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Pending> PENDING = new IdentityHashMap<>();
    private static final class Pending {
        final boolean alt;
        int ticks = 100;
        boolean committed;
        Pending(boolean alt) { this.alt = alt; }
    }
    private static ClientConnection connection(ServerLoginNetworkHandler handler) {
        return ((LoginConnectionAccessor) handler).dw$getConnection();
    }
    private static CityVisualProtocol.Offer current(MinecraftServer server) {
        CityVisualModeState state = CityVisualModeState.get(server);
        return new CityVisualProtocol.Offer(state.alt, state.revision, CityVisualResources.fingerprint(state.alt));
    }
    private static Text rejection() { return Text.literal("Внешний вид города изменён или переключается. Подключитесь повторно."); }
    public static boolean admit(MinecraftServer server, ClientConnection connection) {
        Proof proof = PROOFS.remove(connection);
        if (proof != null && proof.ready() && !PENDING.containsKey(server) && proof.offer().equals(current(server)) && connection.isOpen()) return true;
        Text reason = rejection();
        connection.send(new DisconnectS2CPacket(reason), PacketCallbacks.always(() -> connection.disconnect(reason)));
        return false;
    }
    @Override public void onInitialize() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
            synchronizer.waitFor(server.submit(() -> {
                if (!handler.isConnectionOpen()) return;
                if (PENDING.containsKey(server)) { handler.disconnect(rejection()); return; }
                CityVisualProtocol.Offer offer = current(server);
                PROOFS.put(connection(handler), new Proof(offer, false));
                PacketByteBuf buf = PacketByteBufs.create(); CityVisualProtocol.writeOffer(buf, offer);
                sender.sendPacket(CityVisualProtocol.LOGIN, buf);
            })));
        ServerLoginNetworking.registerGlobalReceiver(CityVisualProtocol.LOGIN, (server, handler, understood, buf, synchronizer, sender) -> {
            CityVisualProtocol.Response response = null;
            if (understood) {
                try { response = CityVisualProtocol.readResponse(buf); } catch (RuntimeException ignored) { }
            }
            CityVisualProtocol.Response received = response;
            synchronizer.waitFor(server.submit(() -> {
                ClientConnection connection = connection(handler);
                Proof proof = PROOFS.get(connection);
                if (!handler.isConnectionOpen()) { PROOFS.remove(connection); return; }
                if (received == null || proof == null || !proof.offer().equals(received.offer())) {
                    PROOFS.remove(connection);
                    handler.disconnect(Text.literal("Несовместимая версия визуала. Обновите мод клиента.")); return;
                }
                if (PENDING.containsKey(server) || !proof.offer().equals(current(server))) {
                    PROOFS.remove(connection); handler.disconnect(rejection()); return;
                }
                if (!received.ready()) {
                    PROOFS.remove(connection);
                    handler.disconnect(Text.literal("Подготовка внешнего вида города. Повторный вход после загрузки ресурсов.")); return;
                }
                PROOFS.put(connection, new Proof(proof.offer(), true));
            }));
        });
        ServerLoginConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> PROOFS.remove(connection(handler))));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { PENDING.remove(server); PROOFS.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(CityVisualMode::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
            CommandManager.literal("dwvisual").requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> status(ctx.getSource()))
                .then(CommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(CommandManager.literal("original").executes(ctx -> change(ctx.getSource(), false)))
                .then(CommandManager.literal("alt").executes(ctx -> change(ctx.getSource(), true)))));
    }
    private static void tick(MinecraftServer server) {
        Pending pending = PENDING.get(server);
        if (pending == null) return;
        if (pending.committed) {
            for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) player.networkHandler.disconnect(rejection());
            PENDING.remove(server); return;
        }
        if (--pending.ticks > 0) return;
        CityVisualModeState state = CityVisualModeState.get(server);
        state.alt = pending.alt; state.revision = Math.addExact(state.revision, 1); state.markDirty();
        server.getOverworld().getPersistentStateManager().save();
        CityVisualProtocol.Offer offer = current(server);
        for (ServerPlayerEntity player : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (ServerPlayNetworking.canSend(player, CityVisualProtocol.MODE)) {
                PacketByteBuf buf = PacketByteBufs.create(); CityVisualProtocol.writeOffer(buf, offer);
                ServerPlayNetworking.send(player, CityVisualProtocol.MODE, buf);
            }
        }
        pending.committed = true;
    }
    private static int status(ServerCommandSource source) {
        CityVisualModeState state = CityVisualModeState.get(source.getServer());
        Pending pending = PENDING.get(source.getServer());
        source.sendFeedback(() -> Text.literal("Внешний вид города: " + (state.alt ? "alt" : "original") + " (версия " + state.revision + ")"
            + (pending == null ? "" : "; переключение на " + (pending.alt ? "alt" : "original"))), false); return 1;
    }
    private static int change(ServerCommandSource source, boolean alt) {
        MinecraftServer server = source.getServer();
        Pending pending = PENDING.get(server);
        if (pending != null) {
            if (pending.alt == alt) { source.sendFeedback(() -> Text.literal("Переключение уже запланировано."), false); return 1; }
            source.sendError(Text.literal("Дождитесь завершения текущего переключения.")); return 0;
        }
        CityVisualModeState state = CityVisualModeState.get(server);
        if (state.alt == alt) { source.sendFeedback(() -> Text.literal("Этот внешний вид уже выбран."), false); return 1; }
        if (state.revision == Long.MAX_VALUE) { source.sendError(Text.literal("Достигнут предел версий внешнего вида.")); return 0; }
        PENDING.put(server, new Pending(alt));
        server.getPlayerManager().broadcast(Text.literal("Через 5 секунд внешний вид города сменится на " + (alt ? "альтернативный" : "оригинальный")
            + ". Игроки будут отключены для загрузки; мод выполнит повторный вход."), false);
        return 1;
    }
}
