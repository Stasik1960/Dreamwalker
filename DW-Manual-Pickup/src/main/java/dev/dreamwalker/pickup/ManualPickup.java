package dev.dreamwalker.pickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public final class ManualPickup implements ModInitializer {
    public static final Identifier REQUEST = new Identifier("dw_manual_pickup", "pick_up");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, response) -> {
            // The client chooses neither an entity nor a distance. The server owns both.
            if (buf.readableBytes() != 0) return;
            server.execute(() -> {
                if (player.networkHandler != handler || !handler.isConnectionOpen()) return;
                if (PickupService.acceptRequest(player)) PickupService.pickUpNearest(player);
            });
        });
    }
}
