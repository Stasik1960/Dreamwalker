package dev.yharnamdice.client;

import com.google.gson.JsonParseException;
import dev.yharnamdice.DiceMod;
import dev.yharnamdice.Protocol;
import dev.yharnamdice.client.mixin.ChatScreenOpener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;

public final class DiceClient implements ClientModInitializer {
    private static Screen chatReturn;
    private TableScreen table;
    @Override public void onInitializeClient() {
        for (int i=0; i<DiceMod.DICE.size(); i++) {
            final int skin = i;
            BuiltinItemRendererRegistry.INSTANCE.register(DiceMod.DICE.get(i), (item, mode, matrices, consumers, light, overlay) -> {
                matrices.push();
                matrices.translate(.5,.5,.5);
                DiceMesh.draw(consumers.getBuffer(DiceMesh.LAYER),matrices.peek(),skin,light,overlay);
                matrices.pop();
            });
        }
        HandledScreens.register(DiceMod.POUCH_MENU, PouchScreen::new);
        EntityRendererRegistry.register(DiceMod.SEAT, EmptyEntityRenderer::new);
        WorldRenderEvents.AFTER_ENTITIES.register(DiceWorldRenderer::render);
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) ->
                !(net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof TableScreen));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DiceWorldRenderer.clear();
            clearTable(client);
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                if (table != null) clearTable(client);
            } else if (chatReturn != null && client.currentScreen == null) {
                Screen returnTo = chatReturn;
                chatReturn = null;
                if (table != null) client.setScreen(returnTo);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(Protocol.VIEW, (client, handler, buf, sender) -> {
            Protocol.View view;
            try {
                view = buf.readBoolean() ? Protocol.GSON.fromJson(buf.readString(Protocol.MAX_JSON), Protocol.View.class) : null;
            } catch (JsonParseException invalid) {
                return;
            }
            client.execute(() -> {
                if (view == null) {
                    clearTable(client);
                } else if (table != null && table.sameTable(view)) {
                    table.update(view);
                } else if (client.player != null) {
                    chatReturn = null;
                    table = new TableScreen(view);
                    client.inGameHud.setOverlayMessage(net.minecraft.text.Text.empty(), false);
                    client.setScreen(table);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Protocol.DICE, (client, handler, buf, sender) -> {
            var pos = buf.readBlockPos();
            int rotation = buf.readUnsignedByte();
            int count = buf.readVarInt();
            if (rotation > 3 || count < 0 || count > 80 || buf.readableBytes() != 35 * count) return;
            var dice = new ArrayList<Protocol.Visual>(count);
            for (int i=0; i<count; i++) {
                dice.add(new Protocol.Visual(buf.readUnsignedByte(),buf.readUnsignedByte(),buf.readUnsignedByte(),
                    buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat()));
            }
            client.execute(() -> DiceWorldRenderer.update(pos, rotation, dice));
        });

        ClientPlayNetworking.registerGlobalReceiver(Protocol.DISK_OPEN, (client, handler, buf, sender) -> {
            int type = buf.readUnsignedByte();
            long token = buf.readLong();
            String input = buf.readString(type == 0 ? Protocol.MAX_JSON : 300);
            client.execute(() -> {
                if (type == 0 && client.player != null) client.setScreen(new DiskScreen(token, input));
                else if (client.currentScreen instanceof DiskScreen disk) disk.result(token, type == 1, input);
            });
        });
    }

    private void clearTable(MinecraftClient client) {
        table = null;
        chatReturn = null;
        if (client.currentScreen instanceof TableScreen || client.currentScreen instanceof TableRulesScreen) {
            client.setScreen(null);
        }
    }

    static boolean openChatFromTable(MinecraftClient client, Screen parent, int key, int scan) {
        if (client == null || client.currentScreen != parent) return false;
        boolean command = client.options.commandKey.matchesKey(key, scan);
        if (!command && !client.options.chatKey.matchesKey(key, scan)) return false;

        // Use Minecraft's normal chat entry point, including privacy settings and replacement chat UIs.
        ((ChatScreenOpener) client).yharnamDice$openChat(command ? "/" : "");
        if (client.currentScreen != parent) chatReturn = parent;
        return true;
    }

    static void action(long table, int action, int argument) {
        if (!ClientPlayNetworking.canSend(Protocol.ACT)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(table).writeByte(action).writeInt(argument);
        ClientPlayNetworking.send(Protocol.ACT, buf);
    }

    static void write(long token, String input) {
        if (!ClientPlayNetworking.canSend(Protocol.DISK_WRITE)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(token);
        buf.writeString(input, Protocol.MAX_JSON);
        ClientPlayNetworking.send(Protocol.DISK_WRITE, buf);
    }
}
