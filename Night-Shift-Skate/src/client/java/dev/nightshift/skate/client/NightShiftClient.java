package dev.nightshift.skate.client;

import dev.nightshift.skate.BoardStyle;
import dev.nightshift.skate.NightShiftSkate;
import dev.nightshift.skate.SkateboardEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
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
    private SkateboardEntity lastBoard;
    private int lastInput;
    private int ticksSinceSend;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(NightShiftSkate.SKATEBOARD_ENTITY, SkateboardRenderer::new);
        BoardMesh.Library.initialize();
        registerBoardItem(NightShiftSkate.SKATEBOARD, BoardStyle.NIGHT_SHIFT);
        registerBoardItem(NightShiftSkate.IRON_VESPERS, BoardStyle.IRON_VESPERS);
        registerBoardItem(NightShiftSkate.GRAVE_PROCESSION, BoardStyle.GRAVE_PROCESSION);
        trickKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.night_shift_skate.trick",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.night_shift_skate"));
        SkateHud.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(this::sendControls);
    }

    private static void registerBoardItem(Item item, BoardStyle style) {
        BuiltinItemRendererRegistry.INSTANCE.register(item,
                (stack, mode, matrices, vertexConsumers, light, overlay) -> {
                    matrices.push();
                    matrices.translate(0.5, 0.5, 0.5);
                    boolean showCoffinLid = style == BoardStyle.GRAVE_PROCESSION && mode != ModelTransformationMode.GROUND;
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(showCoffinLid ? 90 : -90));
                    matrices.translate(0, -style.riderHeight() * 0.5, 0);
                    SkateboardRenderer.renderBoard(style, matrices, vertexConsumers, light);
                    matrices.pop();
                });
    }

    private void sendControls(MinecraftClient client) {
        boolean trick = false;
        while (trickKey.wasPressed()) {
            trick = true;
        }
        if (client.player == null || !(client.player.getVehicle() instanceof SkateboardEntity board)
                || !ClientPlayNetworking.canSend(NightShiftSkate.CONTROLS)) {
            lastBoard = null;
            return;
        }

        int input = client.currentScreen == null ? readControls(client.options, trick) : 0;
        if (board != lastBoard || input != lastInput || (input & TRICK) != 0 || ++ticksSinceSend >= 5) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeByte(input);
            ClientPlayNetworking.send(NightShiftSkate.CONTROLS, buf);
            lastBoard = board;
            lastInput = input & CONTINUOUS;
            ticksSinceSend = 0;
        }
    }

    private int readControls(GameOptions options, boolean trick) {
        int input = trick ? TRICK : 0;
        if (options.forwardKey.isPressed()) input |= ACCELERATE;
        if (options.backKey.isPressed()) input |= BRAKE;
        if (options.leftKey.isPressed()) input |= LEFT;
        if (options.rightKey.isPressed()) input |= RIGHT;
        if (options.jumpKey.isPressed()) input |= JUMP;
        if (options.sneakKey.isPressed()) input |= CROUCH;
        return input;
    }
}
