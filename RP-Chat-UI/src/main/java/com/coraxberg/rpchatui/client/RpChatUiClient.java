package com.coraxberg.rpchatui.client;

import com.coraxberg.rpchatui.RpChatUiConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import java.lang.reflect.Field;

public class RpChatUiClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChatUiState.loadConfig();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChatUiState.resetConnection());

        ClientPlayNetworking.registerGlobalReceiver(RpChatUiConstants.MAX_LENGTH_PACKET, (client, handler, buf, responseSender) -> {
            int maxLength = buf.readVarInt();
            client.execute(() -> ChatUiState.setServerMaxChatLength(maxLength));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof ChatScreen && !(client.currentScreen instanceof RpChatScreen)) {
                ChatUiState.captureInitialVanillaChatText(captureChatText((ChatScreen) client.currentScreen));
                client.setScreen(new RpChatScreen());
            }
        });
    }

    private static String captureChatText(ChatScreen screen) {
        try {
            for (Field field : ChatScreen.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(screen);
                if (value instanceof TextFieldWidget widget) {
                    return widget.getText();
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
