package dev.dreamwalker.bloodbornedw.composite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** One lantern strike per physical attack press, including rebound attack keys. */
public final class StandardLanternClient implements ClientModInitializer {
    private static boolean attackLatched;

    public static void releaseAttack() { attackLatched = false; }

    private static boolean claimAttack() {
        if (attackLatched) return false;
        attackLatched = true;
        return true;
    }

    @Override public void onInitializeClient() {
        StandardLanternGameplay.setClientAttackGate(StandardLanternClient::claimAttack);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> releaseAttack());
        // Also recover from GUI focus changes that clear bindings without a release event.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (attackLatched && (client.currentScreen != null || !client.options.attackKey.isPressed())) releaseAttack();
        });
    }
}
