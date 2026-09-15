package com.coraxberg.dwmagicconnect.config;

import com.coraxberg.dwmagicconnect.DwMagicConnectMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DwMagicConnectConfig {
    public static final int DEFAULT_RELAY_RADIUS = 15;
    public static final int MAX_RELAY_RADIUS = 10_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("dw_magic_connect.json");

    private static volatile ConfigData current = ConfigData.defaults();

    private DwMagicConnectConfig() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> load());
    }

    public static int relayRadius() {
        return current.relayRadius;
    }

    private static synchronized void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                ConfigData loaded = GSON.fromJson(json, ConfigData.class);
                current = loaded == null ? ConfigData.defaults() : loaded.normalized();
            } else {
                current = ConfigData.defaults();
            }

            save();
            DwMagicConnectMod.LOGGER.info("Loaded DW Magic Connect config from {}", CONFIG_PATH);
        } catch (Exception exception) {
            current = ConfigData.defaults();
            DwMagicConnectMod.LOGGER.error("Could not load {}; using defaults", CONFIG_PATH, exception);
        }
    }

    private static void save() throws IOException {
        Files.writeString(
                CONFIG_PATH,
                GSON.toJson(current),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static final class ConfigData {
        private int relayRadius = DEFAULT_RELAY_RADIUS;

        private static ConfigData defaults() {
            return new ConfigData();
        }

        private ConfigData normalized() {
            if (relayRadius < 0) relayRadius = DEFAULT_RELAY_RADIUS;
            if (relayRadius > MAX_RELAY_RADIUS) relayRadius = MAX_RELAY_RADIUS;
            return this;
        }
    }
}
