package dev.nightshift.skate.client;

import dev.nightshift.skate.BoardSetup;
import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkatePart;
import dev.nightshift.skate.SkateParts;
import dev.nightshift.skate.SkateboardEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import static dev.nightshift.skate.SkateControls.*;

public final class NightShiftClient implements ClientModInitializer {
    private KeyBinding trickKey;
    private KeyBinding grabKey;
    private SkateboardEntity lastBoard;
    private int lastInput;
    private int ticksSinceSend;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NightShiftSkate.SKATEBOARD_ENTITY, SkateboardRenderer::new);
        BoardMesh.Library.initialize();
        registerBoardItem(NightShiftSkate.SKATEBOARD);
        registerBoardItem(NightShiftSkate.IRON_VESPERS);
        registerBoardItem(NightShiftSkate.GRAVE_PROCESSION);
        SkateParts.all().forEach(NightShiftClient::registerPartItem);
        HandledScreens.register(SkateParts.SCREEN, SkateWorkbenchScreen::new);
        trickKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.night_shift_skate.trick",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.night_shift_skate"));
        grabKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.night_shift_skate.grab",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.night_shift_skate"));
        KeyBinding helpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.night_shift_skate.guide",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.night_shift_skate"));
        SkateHud.initialize(new SkateGuide(helpKey, trickKey, grabKey));
        ClientTickEvents.END_CLIENT_TICK.register(this::sendControls);
    }

    private static void registerBoardItem(Item item) {
        BuiltinItemRendererRegistry.INSTANCE.register(item,
                (stack, mode, matrices, vertexConsumers, light, overlay) -> {
                    BoardSetup setup = BoardSetup.fromStack(stack);
                    matrices.push();
                    matrices.translate(0.5, 0.5, 0.5);
                    boolean showCoffinLid = setup.deck() == SkatePart.Deck.GRAVE_PROCESSION
                            && mode != ModelTransformationMode.GROUND;
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(showCoffinLid ? 90 : -90));
                    matrices.translate(0, -setup.riderHeight() * 0.5, 0);
                    SkateboardRenderer.renderBoard(setup, matrices, vertexConsumers, light);
                    matrices.pop();
                });
    }

    private static void registerPartItem(SkatePart part) {
        BuiltinItemRendererRegistry.INSTANCE.register(SkateParts.item(part),
                (stack, mode, matrices, vertices, light, overlay) -> {
                    matrices.push();
                    matrices.translate(0.5, 0.5, 0.5);
                    switch (part.type()) {
                        case DECK -> {
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-38));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(103));
                        }
                        case TRUCKS -> {
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-16));
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25));
                            matrices.scale(2.25f, 2.25f, 2.25f);
                            matrices.translate(0, 0.03, 0);
                        }
                        case WHEELS -> {
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-61));
                            matrices.scale(8.0f, 8.0f, 8.0f);
                        }
                    }
                    SkateboardRenderer.renderPart(part, matrices, vertices, light);
                    matrices.pop();
                });
    }

    private void sendControls(MinecraftClient client) {
        int action = 0;
        while (trickKey.wasPressed()) {
            action |= TRICK;
        }
        while (grabKey.wasPressed()) {
            action |= GRAB;
        }
        if (client.player == null || !(client.player.getVehicle() instanceof SkateboardEntity board)
                || !ClientPlayNetworking.canSend(NightShiftSkate.CONTROLS)) {
            lastBoard = null;
            return;
        }

        int input = client.currentScreen == null ? readControls(client.options) | action : 0;
        if (board != lastBoard || input != lastInput || ++ticksSinceSend >= 5) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeByte(input);
            ClientPlayNetworking.send(NightShiftSkate.CONTROLS, buf);
            lastBoard = board;
            lastInput = input & CONTINUOUS;
            ticksSinceSend = 0;
        }
    }

    private int readControls(GameOptions options) {
        int input = 0;
        if (options.forwardKey.isPressed()) input |= ACCELERATE;
        if (options.backKey.isPressed()) input |= BRAKE;
        if (options.leftKey.isPressed()) input |= LEFT;
        if (options.rightKey.isPressed()) input |= RIGHT;
        if (options.jumpKey.isPressed()) input |= JUMP;
        if (options.sneakKey.isPressed()) input |= CROUCH;
        return input;
    }
}
