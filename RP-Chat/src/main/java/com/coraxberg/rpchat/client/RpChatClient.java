package com.coraxberg.rpchat.client;

import net.fabricmc.api.ClientModInitializer;

public class RpChatClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Server controls chat routing. This entrypoint keeps the mod valid on both client and server.
    }
}
