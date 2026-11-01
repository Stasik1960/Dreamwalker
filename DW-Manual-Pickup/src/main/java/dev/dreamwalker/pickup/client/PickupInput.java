package dev.dreamwalker.pickup.client;

import dev.dreamwalker.pickup.ManualPickup;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class PickupInput {
    private PickupInput() {}

    public static void onKeyboard(long window, int key, int scanCode, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS && client.options.sneakKey.matchesKey(key, scanCode)) {
            send(client, window);
        }
    }

    public static void onMouse(long window, int button, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS && client.options.sneakKey.matchesMouse(button)) {
            send(client, window);
        }
    }

    private static void send(MinecraftClient client, long window) {
        if (window != client.getWindow().getHandle() || !client.isWindowFocused()
                || client.currentScreen != null || client.player == null || client.world == null
                || !client.player.isAlive() || client.player.isSpectator()
                || !ClientPlayNetworking.canSend(ManualPickup.REQUEST)) return;
        ClientPlayNetworking.send(ManualPickup.REQUEST, PacketByteBufs.empty());
    }
}
