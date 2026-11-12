package dev.nightwatch.carriage.client;

import dev.nightwatch.carriage.CarriageEntity;
import dev.nightwatch.carriage.CarriagePackets;
import dev.nightwatch.carriage.NightWatchCarriage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public final class CarriageClient implements ClientModInitializer {
    static KeyBinding panelKey;
    private int lastVehicle = -1;
    private int lastKeys;
    private int ticksSinceSend;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NightWatchCarriage.CARRIAGE, CarriageRenderer::new);
        panelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.nightwatchcarriage.panel",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.nightwatchcarriage"));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudRenderCallback.EVENT.register(CarriageClient::renderHud);
    }

    private void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            lastVehicle = -1;
            return;
        }
        CarriageEntity mounted = client.player.getVehicle() instanceof CarriageEntity carriage ? carriage : null;
        while (panelKey.wasPressed()) {
            if (client.currentScreen == null && mounted != null && !mounted.isRemoved() &&
                mounted.seatOf(client.player) != null) {
                client.setScreen(new CarriageScreen(mounted));
            }
        }
        if (mounted == null || !mounted.controls(client.player) || !ClientPlayNetworking.canSend(CarriagePackets.DRIVE)) {
            lastVehicle = -1;
            return;
        }
        int keys = 0;
        if (client.currentScreen == null && client.isWindowFocused()) {
            if (client.options.forwardKey.isPressed()) keys |= 1;
            if (client.options.backKey.isPressed()) keys |= 2;
            if (client.options.leftKey.isPressed()) keys |= 4;
            if (client.options.rightKey.isPressed()) keys |= 8;
        }
        if (mounted.getId() != lastVehicle || keys != lastKeys || keys != 0 && ++ticksSinceSend >= 2) {
            PacketByteBuf message = PacketByteBufs.create();
            message.writeByte(keys);
            ClientPlayNetworking.send(CarriagePackets.DRIVE, message);
            lastVehicle = mounted.getId();
            lastKeys = keys;
            ticksSinceSend = 0;
        }
    }

    static void action(CarriageEntity entity, CarriagePackets.Action action) {
        if (entity.seatOf(MinecraftClient.getInstance().player) != null && ClientPlayNetworking.canSend(CarriagePackets.ACTION)) {
            PacketByteBuf message = PacketByteBufs.create();
            message.writeInt(entity.getId());
            message.writeByte(action.ordinal());
            ClientPlayNetworking.send(CarriagePackets.ACTION, message);
        }
    }

    private static void renderHud(DrawContext context, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null ||
            !(client.player.getVehicle() instanceof CarriageEntity carriage) || carriage.seatOf(client.player) == null) return;
        var layout = CarriageDashboard.layout(client, context.getScaledWindowWidth(), context.getScaledWindowHeight());
        CarriageDashboard.shell(context, client.textRenderer, layout, carriage, false);
        for (var control : CarriageDashboard.Control.values()) {
            CarriageDashboard.button(context, client.textRenderer, layout.button(control),
                CarriageDashboard.state(client, carriage, control), false);
        }
    }
}
