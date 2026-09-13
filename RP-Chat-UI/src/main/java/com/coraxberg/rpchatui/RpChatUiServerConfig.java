package com.coraxberg.rpchatui;

import java.util.LinkedHashMap;
import java.util.Map;

public class RpChatUiServerConfig {
    public int globalMaxLength = 512;
    public Map<String, Integer> playerMaxLengths = new LinkedHashMap<>();

    public static RpChatUiServerConfig defaults() {
        RpChatUiServerConfig config = new RpChatUiServerConfig();
        config.normalize();
        return config;
    }

    public void normalize() {
        globalMaxLength = clamp(globalMaxLength);

        if (playerMaxLengths == null) {
            playerMaxLengths = new LinkedHashMap<>();
            return;
        }

        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : playerMaxLengths.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            normalized.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), clamp(entry.getValue()));
        }
        playerMaxLengths = normalized;
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(4096, value));
    }
}
