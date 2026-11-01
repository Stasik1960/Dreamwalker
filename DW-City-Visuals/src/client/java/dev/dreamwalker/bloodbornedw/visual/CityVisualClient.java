package dev.dreamwalker.bloodbornedw.visual;

import dev.dreamwalker.bloodbornedw.visual.mixin.ClientLoginConnectionAccessor;
import dev.dreamwalker.bloodbornedw.visual.mixin.ConnectionCloseStateAccessor;
import dev.dreamwalker.bloodbornedw.visual.mixin.IntegratedVisualReloadAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.GameMode;
import net.minecraft.server.integrated.IntegratedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CityVisualClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("dw-city-visuals");
    // The provider may be constructed before client entrypoints: initialize its hint here.
    private static volatile boolean current = CityVisualPreferences.lastAlt(), snapshot = current;
    private static boolean resourcesValid = true, running, automaticLogin, automaticCall;
    private static long serial;
    private static Preparation preparation;
    private static ReturnTarget automaticTarget;
    private static volatile ClientLoginNetworkHandler latestLogin;
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    public static boolean isAlt() { return current; }
    public static void registerReloadListener(Runnable listener) { LISTENERS.add(listener); }
    static boolean packSnapshot() { return snapshot; }
    /** Called at both remote-connect and local-save-open entrypoints, including after cancellation. */
    public static void connectionStarting() {
        if (!automaticCall) { automaticLogin = false; automaticTarget = null; }
    }

    private record ReturnTarget(ServerInfo remote, String save, int lanPort, GameMode lanMode, boolean cheats) {
        static ReturnTarget capture(MinecraftClient client, ServerInfo loginInfo) {
            var server = client.getServer();
            if (server != null) return new ReturnTarget(null, server.getSavePath(WorldSavePath.ROOT).normalize().getFileName().toString(),
                server.isRemote() ? server.getServerPort() : -1, server.getForcedGameMode(), server.getPlayerManager().areCheatsAllowed());
            ServerInfo info = loginInfo != null ? loginInfo : client.getCurrentServerEntry();
            return info == null ? null : new ReturnTarget(info, null, -1, null, false);
        }
        Screen menu() { return save == null ? new MultiplayerScreen(new TitleScreen()) : new SelectWorldScreen(new TitleScreen()); }
        void connect(MinecraftClient client) {
            // This exact save already passed the normal experimental/backup prompt before capture.
            if (save != null) ((IntegratedVisualReloadAccessor)client.createIntegratedServerLoader()).dw$reopenAcceptedSave(menu(), save, false, false);
            else ConnectScreen.connect(menu(), client, ServerAddress.parse(remote.address), remote, false);
        }
    }
    private static final class Preparation {
        final long serial;
        final CityVisualProtocol.Offer offer;
        final ClientConnection connection;
        final ReturnTarget target;
        final IntegratedServer integrated;
        CityVisualPreparationScreen screen;
        boolean started, closed, stopRequested, cancelled;
        Preparation(long serial, CityVisualProtocol.Offer offer, ClientConnection connection, ReturnTarget target, IntegratedServer integrated) {
            this.serial = serial; this.offer = offer; this.connection = connection; this.target = target;
            this.integrated = integrated;
        }
    }
    @Override public void onInitializeClient() {
        ClientLoginConnectionEvents.INIT.register((handler, client) -> latestLogin = handler);
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            if (latestLogin == handler && preparation == null) {
                latestLogin = null; automaticLogin = false; automaticTarget = null;
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Preparation job = preparation;
            if (job != null) advance(client, job);
        });
        ClientLoginNetworking.registerGlobalReceiver(CityVisualProtocol.LOGIN, (client, handler, buf, listenerAdder) -> {
            final CityVisualProtocol.Offer offer;
            try { offer = CityVisualProtocol.readOffer(buf); }
            catch (RuntimeException failure) { return CompletableFuture.completedFuture(null); }
            ClientConnection connection = ((ClientLoginConnectionAccessor) handler).dw$getConnection();
            CompletableFuture<PacketByteBuf> response = new CompletableFuture<>();
            client.execute(() -> {
                try {
                    if (latestLogin != handler || !connection.isOpen()) { response.complete(null); return; }
                    if (!installed(offer)) {
                        response.complete(null);
                        connection.disconnect(Text.literal("Версии визуала клиента и сервера отличаются. Обновите мод.")); return;
                    }
                    boolean ready = resourcesValid && !running && current == offer.alt() && snapshot == offer.alt();
                    if (!ready && automaticLogin) {
                        response.complete(null);
                        connection.disconnect(Text.literal("Визуал сервера снова изменился. Подключитесь повторно.")); return;
                    }
                    ReturnTarget target = ReturnTarget.capture(client, ((ClientLoginConnectionAccessor) handler).dw$getServerInfo());
                    if (!ready && target == null) {
                        response.complete(null);
                        connection.disconnect(Text.literal("Не удалось определить мир для повторного входа.")); return;
                    }
                    if (ready) CityVisualPreferences.save(current);
                    PacketByteBuf reply = PacketByteBufs.create();
                    CityVisualProtocol.writeResponse(reply, new CityVisualProtocol.Response(ready, offer));
                    response.complete(reply);
                    // No delayed write callback can restart a cancelled or replaced attempt.
                    // Closing now is safe: the server never received a positive readiness proof.
                    if (!ready) prepare(client, offer, connection, target);
                    LOG.info("City visual login: {} revision {}, resources {}", offer.alt() ? "alt" : "original", offer.revision(), ready ? "ready" : "prepare outside world");
                } catch (RuntimeException failure) { response.completeExceptionally(failure); }
            });
            return response;
        });
        ClientPlayNetworking.registerGlobalReceiver(CityVisualProtocol.MODE, (client, handler, buf, sender) -> {
            final CityVisualProtocol.Offer offer;
            try { offer = CityVisualProtocol.readOffer(buf); }
            catch (RuntimeException failure) {
                client.execute(() -> handler.getConnection().disconnect(Text.literal("Несовместимая версия переключения визуала."))); return;
            }
            client.execute(() -> {
                if (client.getNetworkHandler() != handler) return;
                if (!installed(offer)) { handler.getConnection().disconnect(Text.literal("Обновите мод: версии визуала отличаются.")); return; }
                prepare(client, offer, handler.getConnection(), ReturnTarget.capture(client, null));
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ReturnTarget target = automaticTarget;
            latestLogin = null; automaticLogin = false; automaticTarget = null;
            // A local host reopens the same save, including its previous LAN port/settings.
            if (target != null && target.save() != null && target.lanPort() >= 0 && client.getServer() != null) {
                if (!client.getServer().openToLan(target.lanMode(), target.cheats(), target.lanPort()))
                    LOG.warn("Could not reopen the previous city LAN port {}", target.lanPort());
            }
        });
        // Disconnect never selects another profile or starts a resource reload.
    }
    private static boolean installed(CityVisualProtocol.Offer offer) {
        return CityVisualResources.fingerprint(offer.alt()).equals(offer.fingerprint());
    }
    private static void prepare(MinecraftClient client, CityVisualProtocol.Offer offer, ClientConnection connection, ReturnTarget target) {
        if (preparation != null || running) return;
        if (target == null) { connection.disconnect(Text.literal("Визуал изменён. Подключитесь повторно.")); return; }
        Preparation job = new Preparation(++serial, offer, connection, target, client.getServer());
        preparation = job;
        job.screen = new CityVisualPreparationScreen(() -> cancel(client, job));
        snapshot = offer.alt();
        connection.disconnect(Text.literal("Подготовка внешнего вида города"));
        client.setScreen(job.screen);
    }
    private static void advance(MinecraftClient client, Preparation job) {
        if (preparation != job || job.serial != serial || job.started || job.connection.isOpen()) return;
        if (!job.closed) {
            // A pack clear during disconnect must also select the target profile.
            snapshot = job.offer.alt();
            if (!((ConnectionCloseStateAccessor)job.connection).dw$closeHandled()) job.connection.handleDisconnection();
            if (job.integrated != null && job.integrated.getThread().isAlive()) {
                if (!job.stopRequested) { job.integrated.stop(false); job.stopRequested = true; }
                job.screen.status("Сохранение и закрытие мира…");
                client.setScreen(job.screen);
                return; // Keep pumping the client instead of blocking it with a thread join.
            }
            client.disconnect(job.screen);
            client.setScreen(job.screen);
            job.closed = true;
        }
        if (job.cancelled) { finishCancel(client, job); return; }
        if (client.getOverlay() != null) return; // Finish any vanilla pack-clear reload first.
        job.started = true;
        automaticLogin = false; automaticTarget = null;
        if (resourcesValid && NativeWallVisuals.loadingProfileIsAlt() == job.offer.alt()) {
            current = job.offer.alt(); CityVisualPreferences.save(current); reconnect(client, job); return;
        }
        running = true; resourcesValid = false; snapshot = job.offer.alt();
        job.screen.status("Загрузка " + (snapshot ? "альтернативного" : "оригинального") + " вида…");
        long started = System.nanoTime();
        LOG.info("Preparing city visuals outside world: {}", snapshot ? "alt" : "original");
        try {
            client.reloadResources().whenComplete((ignored, failure) -> client.execute(() -> {
                running = false;
                if (failure != null) { failed(client, job, failure); return; }
                if (NativeWallVisuals.loadingProfileIsAlt() != job.offer.alt()) {
                    failed(client, job, new IllegalStateException("Resource reload selected another visual profile")); return;
                }
                current = job.offer.alt();
                try {
                    for (Runnable listener : List.copyOf(LISTENERS)) listener.run();
                    resourcesValid = true; CityVisualPreferences.save(current);
                } catch (RuntimeException problem) { failed(client, job, problem); return; }
                LOG.info("City visuals ready outside world: {} in {} ms", current ? "alt" : "original", (System.nanoTime() - started) / 1_000_000);
                reconnect(client, job);
            }));
        } catch (RuntimeException failure) { running = false; failed(client, job, failure); }
    }
    private static void reconnect(MinecraftClient client, Preparation job) {
        if (preparation != job || job.serial != serial) return;
        preparation = null; automaticLogin = true; automaticTarget = job.target;
        automaticCall = true;
        try { job.target.connect(client); } // Login revalidates before any world admission.
        finally { automaticCall = false; }
    }
    private static void cancel(MinecraftClient client, Preparation job) {
        if (preparation != job) return;
        job.cancelled = true; automaticLogin = false; automaticTarget = null;
        job.connection.disconnect(Text.literal("Отменено"));
        if (job.closed) finishCancel(client, job);
    }
    private static void finishCancel(MinecraftClient client, Preparation job) {
        serial++; preparation = null;
        Screen menu = job.target.menu(); client.setScreen(menu);
    }
    private static void failed(MinecraftClient client, Preparation job, Throwable failure) {
        resourcesValid = false;
        LOG.error("City visual preparation failed", failure);
        if (preparation != job || job.serial != serial) return;
        serial++; preparation = null; automaticLogin = false; automaticTarget = null;
        client.setScreen(new DisconnectedScreen(job.target.menu(), Text.literal("Смена внешнего вида"),
            Text.literal("Не удалось загрузить визуал. Проверьте журнал клиента и перезапустите игру.")));
    }
}
