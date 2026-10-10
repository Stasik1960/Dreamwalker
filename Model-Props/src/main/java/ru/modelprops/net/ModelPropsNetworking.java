package ru.modelprops.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import ru.modelprops.ModelProps;
import ru.modelprops.model.ModelTransform;
import ru.modelprops.server.ServerModelCatalog;
import ru.modelprops.server.ServerModelEntry;
import ru.modelprops.server.upload.ServerUploadManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

public final class ModelPropsNetworking {
    // The base catalog packets are unchanged from 1.1. Optional 1.2 assets use
    // their own capability-checked channels, so old clients can still sync models.
    public static final int PROTOCOL = 1;
    public static final int MAX_STRING = 262_144;
    public static final int CHUNK_SIZE = 24 * 1024;
    public static final int MAX_UPLOAD_JSON_BYTES = 256 * 1024;
    public static final int MAX_UPLOAD_PNG_BYTES = 4 * 1024 * 1024;

    public static final Identifier SYNC_BEGIN = ModelProps.id("sync_begin");
    public static final Identifier SYNC_MODEL = ModelProps.id("sync_model");
    public static final Identifier TEXTURE_BEGIN = ModelProps.id("texture_begin");
    public static final Identifier TEXTURE_CHUNK = ModelProps.id("texture_chunk");
    public static final Identifier TEXTURE_END = ModelProps.id("texture_end");
    public static final Identifier SYNC_TRANSFORM = ModelProps.id("sync_transform");
    public static final Identifier SYNC_END = ModelProps.id("sync_end");
    public static final Identifier SAVE_TRANSFORM = ModelProps.id("save_transform");
    public static final Identifier SERVER_MESSAGE = ModelProps.id("server_message");
    public static final Identifier UPLOAD_BEGIN = ModelProps.id("upload_begin");
    public static final Identifier UPLOAD_CHUNK = ModelProps.id("upload_chunk");
    public static final Identifier UPLOAD_COMMIT = ModelProps.id("upload_commit");
    public static final Identifier UPLOAD_STATUS = ModelProps.id("upload_status");

    public static final byte UPLOAD_READY = 0;
    public static final byte UPLOAD_SUCCESS = 1;
    public static final byte UPLOAD_ERROR = 2;

    private ModelPropsNetworking() {
    }

    public static void initializeServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_TRANSFORM, (server, player, handler, buffer, responseSender) -> {
            String id = buffer.readString(128);
            int baseRevision = buffer.readVarInt();
            ModelTransform proposed = new ModelTransform(
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), baseRevision
            );
            server.execute(() -> saveTransform(server, player, id, baseRevision, proposed));
        });
        ServerUploadManager.initialize();
    }

    private static void saveTransform(MinecraftServer server, ServerPlayerEntity player, String id,
                                      int baseRevision, ModelTransform proposed) {
        ServerModelCatalog catalog = ServerModelCatalog.INSTANCE;
        if (!player.hasPermissionLevel(2)) {
            sendMessage(player, "Only server operators can edit Model Props transforms.");
            sendTransform(player, id, catalog.transforms().get(id));
            return;
        }
        if (!catalog.contains(id)) {
            sendMessage(player, "Unknown model: " + id);
            sendTransform(player, id, catalog.transforms().get(id));
            return;
        }
        if (!proposed.isValid()) {
            sendMessage(player, "The transform contains an invalid or out-of-range value.");
            sendTransform(player, id, catalog.transforms().get(id));
            return;
        }
        ModelTransform current = catalog.transforms().get(id);
        if (current.revision() != baseRevision) {
            sendMessage(player, "This model changed while the editor was open; the newest values were restored.");
            sendTransform(player, id, current);
            return;
        }
        try {
            ModelTransform saved = catalog.transforms().update(id, proposed);
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                sendTransform(online, id, saved);
            }
            sendMessage(player, "Transform saved for all players.");
        } catch (IOException | IllegalArgumentException exception) {
            ModelProps.LOGGER.error("Could not save transform for {}", id, exception);
            sendMessage(player, "Could not save transforms.json: " + exception.getMessage());
            sendTransform(player, id, catalog.transforms().get(id));
        }
    }

    public static void sendFullSync(ServerPlayerEntity player) {
        ServerModelCatalog catalog = ServerModelCatalog.INSTANCE;
        int generation = catalog.generation();
        PacketByteBuf begin = PacketByteBufs.create();
        begin.writeVarInt(PROTOCOL);
        begin.writeVarInt(generation);
        begin.writeBoolean(player.hasPermissionLevel(2));
        begin.writeVarInt(catalog.entries().size());
        ServerPlayNetworking.send(player, SYNC_BEGIN, begin);

        Set<String> sentTextures = new HashSet<>();
        for (ServerModelEntry entry : catalog.entries().values()) {
            PacketByteBuf model = PacketByteBufs.create();
            model.writeVarInt(generation);
            model.writeString(entry.id(), 128);
            model.writeString(entry.displayName(), 128);
            model.writeString(entry.json(), MAX_STRING);
            ServerPlayNetworking.send(player, SYNC_MODEL, model);

            for (Map.Entry<String, byte[]> texture : entry.textures().entrySet()) {
                if (sentTextures.add(texture.getKey())) {
                    sendTexture(player, generation, texture.getKey(), texture.getValue());
                }
            }
            if (!entry.animationJson().isBlank()) {
                sendAnimation(player, generation, entry.id(),
                        entry.animationJson().getBytes(StandardCharsets.UTF_8));
            }
            for (Map.Entry<String, byte[]> sound : entry.sounds().entrySet()) {
                if (ModelPropsAssetProtocol.validAction(sound.getKey())) {
                    sendLegacySound(player, generation, entry.id(), sound.getKey(), sound.getValue());
                } else {
                    sendNamedSound(player, generation, entry.id(), sound.getKey(), sound.getValue());
                }
            }
            sendTransform(player, entry.id(), catalog.transforms().get(entry.id()));
        }

        PacketByteBuf end = PacketByteBufs.create();
        end.writeVarInt(generation);
        ServerPlayNetworking.send(player, SYNC_END, end);
    }

    public static void broadcastFullSync(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendFullSync(player);
        }
    }

    public static void sendUploadStatus(ServerPlayerEntity player, java.util.UUID requestId,
                                        byte status, String modelId, String message) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeUuid(requestId);
        buffer.writeByte(status);
        buffer.writeString(modelId == null ? "" : modelId, 128);
        buffer.writeString(message == null ? "" : message, 512);
        ServerPlayNetworking.send(player, UPLOAD_STATUS, buffer);
    }

    private static void sendTexture(ServerPlayerEntity player, int generation, String key, byte[] data) {
        String digest = sha256(data);
        PacketByteBuf begin = PacketByteBufs.create();
        begin.writeVarInt(generation);
        begin.writeString(key, 256);
        begin.writeVarInt(data.length);
        begin.writeString(digest, 64);
        ServerPlayNetworking.send(player, TEXTURE_BEGIN, begin);

        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int size = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[size];
            System.arraycopy(data, offset, chunk, 0, size);
            PacketByteBuf part = PacketByteBufs.create();
            part.writeVarInt(generation);
            part.writeString(key, 256);
            part.writeVarInt(offset);
            part.writeByteArray(chunk);
            ServerPlayNetworking.send(player, TEXTURE_CHUNK, part);
        }

        PacketByteBuf end = PacketByteBufs.create();
        end.writeVarInt(generation);
        end.writeString(key, 256);
        ServerPlayNetworking.send(player, TEXTURE_END, end);
    }

    private static void sendAnimation(ServerPlayerEntity player, int generation, String modelId, byte[] data) {
        if (!ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.ANIMATION_BEGIN)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.ANIMATION_CHUNK)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.ANIMATION_END)) {
            return;
        }
        PacketByteBuf begin = PacketByteBufs.create();
        begin.writeVarInt(generation);
        begin.writeString(modelId, 128);
        begin.writeVarInt(data.length);
        begin.writeString(sha256(data), 64);
        ServerPlayNetworking.send(player, ModelPropsAssetProtocol.ANIMATION_BEGIN, begin);

        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int size = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[size];
            System.arraycopy(data, offset, chunk, 0, size);
            PacketByteBuf part = PacketByteBufs.create();
            part.writeVarInt(generation);
            part.writeString(modelId, 128);
            part.writeVarInt(offset);
            part.writeByteArray(chunk);
            ServerPlayNetworking.send(player, ModelPropsAssetProtocol.ANIMATION_CHUNK, part);
        }

        PacketByteBuf end = PacketByteBufs.create();
        end.writeVarInt(generation);
        end.writeString(modelId, 128);
        ServerPlayNetworking.send(player, ModelPropsAssetProtocol.ANIMATION_END, end);
    }

    private static void sendLegacySound(ServerPlayerEntity player, int generation, String modelId,
                                        String action, byte[] data) {
        if (!ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.SOUND_BEGIN)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.SOUND_CHUNK)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.SOUND_END)) {
            return;
        }
        PacketByteBuf begin = PacketByteBufs.create();
        begin.writeVarInt(generation);
        begin.writeString(modelId, 128);
        begin.writeString(action, 16);
        begin.writeVarInt(data.length);
        begin.writeString(sha256(data), 64);
        ServerPlayNetworking.send(player, ModelPropsAssetProtocol.SOUND_BEGIN, begin);

        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int size = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[size];
            System.arraycopy(data, offset, chunk, 0, size);
            PacketByteBuf part = PacketByteBufs.create();
            part.writeVarInt(generation);
            part.writeString(modelId, 128);
            part.writeString(action, 16);
            part.writeVarInt(offset);
            part.writeByteArray(chunk);
            ServerPlayNetworking.send(player, ModelPropsAssetProtocol.SOUND_CHUNK, part);
        }

        PacketByteBuf end = PacketByteBufs.create();
        end.writeVarInt(generation);
        end.writeString(modelId, 128);
        end.writeString(action, 16);
        ServerPlayNetworking.send(player, ModelPropsAssetProtocol.SOUND_END, end);
    }

    private static void sendNamedSound(ServerPlayerEntity player, int generation, String modelId,
                                       String soundKey, byte[] data) {
        if (!ModelPropsAssetProtocol.validSoundKey(soundKey)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.SOUND_V2_BEGIN)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.SOUND_V2_CHUNK)
                || !ServerPlayNetworking.canSend(player, ModelPropsAssetProtocol.SOUND_V2_END)) {
            return;
        }
        PacketByteBuf begin = PacketByteBufs.create();
        begin.writeVarInt(generation);
        begin.writeString(modelId, 128);
        begin.writeString(soundKey, ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH);
        begin.writeVarInt(data.length);
        begin.writeString(sha256(data), 64);
        ServerPlayNetworking.send(player, ModelPropsAssetProtocol.SOUND_V2_BEGIN, begin);

        for (int offset = 0; offset < data.length; offset += CHUNK_SIZE) {
            int size = Math.min(CHUNK_SIZE, data.length - offset);
            byte[] chunk = new byte[size];
            System.arraycopy(data, offset, chunk, 0, size);
            PacketByteBuf part = PacketByteBufs.create();
            part.writeVarInt(generation);
            part.writeString(modelId, 128);
            part.writeString(soundKey, ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH);
            part.writeVarInt(offset);
            part.writeByteArray(chunk);
            ServerPlayNetworking.send(player, ModelPropsAssetProtocol.SOUND_V2_CHUNK, part);
        }

        PacketByteBuf end = PacketByteBufs.create();
        end.writeVarInt(generation);
        end.writeString(modelId, 128);
        end.writeString(soundKey, ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH);
        ServerPlayNetworking.send(player, ModelPropsAssetProtocol.SOUND_V2_END, end);
    }

    public static void sendTransform(ServerPlayerEntity player, String id, ModelTransform transform) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeString(id, 128);
        writeTransform(buffer, transform);
        ServerPlayNetworking.send(player, SYNC_TRANSFORM, buffer);
    }

    public static void writeTransform(PacketByteBuf buffer, ModelTransform transform) {
        buffer.writeFloat(transform.scale());
        buffer.writeFloat(transform.offsetX());
        buffer.writeFloat(transform.offsetY());
        buffer.writeFloat(transform.offsetZ());
        buffer.writeFloat(transform.rotationX());
        buffer.writeFloat(transform.rotationY());
        buffer.writeFloat(transform.rotationZ());
        buffer.writeVarInt(transform.revision());
    }

    public static ModelTransform readTransform(PacketByteBuf buffer) {
        return new ModelTransform(
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readVarInt()
        );
    }

    public static void sendMessage(ServerPlayerEntity player, String text) {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeString(text, 512);
        ServerPlayNetworking.send(player, SERVER_MESSAGE, buffer);
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
