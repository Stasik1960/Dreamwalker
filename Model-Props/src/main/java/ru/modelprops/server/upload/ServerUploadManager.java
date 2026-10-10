package ru.modelprops.server.upload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ru.modelprops.ModelProps;
import ru.modelprops.net.ModelPropsAssetProtocol;
import ru.modelprops.net.ModelPropsNetworking;
import ru.modelprops.server.ServerModelCatalog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerUploadManager {
    private static final ServerUploadManager INSTANCE = new ServerUploadManager();
    private static final int MAX_ACTIVE_UPLOADS = 4;
    private static final long MAX_ACTIVE_BYTES = 48L * 1024L * 1024L;
    private static final long TIMEOUT_MILLIS = 60_000L;

    private final Map<UUID, UploadSession> sessions = new LinkedHashMap<>();
    private final Map<UUID, UUID> activeRequestIds = new ConcurrentHashMap<>();

    private ServerUploadManager() {
    }

    public static void initialize() {
        registerLegacyReceivers();
        registerV2Receivers();
        registerV3Receivers();
        ServerTickEvents.END_SERVER_TICK.register(INSTANCE::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                INSTANCE.removeSession(handler.player.getUuid()));
    }

    private static void registerLegacyReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.UPLOAD_BEGIN,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    String modelId = buffer.readString(128);
                    int jsonSize = buffer.readVarInt();
                    int pngSize = buffer.readVarInt();
                    String jsonHash = buffer.readString(64);
                    String pngHash = buffer.readString(64);
                    server.execute(() -> INSTANCE.beginLegacy(player, requestId, modelId,
                            jsonSize, pngSize, jsonHash, pngHash));
                });

        ServerPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.UPLOAD_CHUNK,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    if (!requestId.equals(INSTANCE.activeRequestIds.get(player.getUuid()))) {
                        return;
                    }
                    byte part = buffer.readByte();
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    server.execute(() -> INSTANCE.acceptChunk(player, requestId, 1, part, "", offset, data));
                });

        ServerPlayNetworking.registerGlobalReceiver(ModelPropsNetworking.UPLOAD_COMMIT,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    if (requestId.equals(INSTANCE.activeRequestIds.get(player.getUuid()))) {
                        server.execute(() -> INSTANCE.commit(server, player, requestId));
                    }
                });
    }

    private static void registerV2Receivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.UPLOAD_BEGIN_V2,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    String modelId = buffer.readString(128);
                    int jsonSize = buffer.readVarInt();
                    int pngSize = buffer.readVarInt();
                    int animationSize = buffer.readVarInt();
                    String jsonHash = buffer.readString(64);
                    String pngHash = buffer.readString(64);
                    String animationHash = buffer.readString(64);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > ModelPropsAssetProtocol.MAX_LEGACY_SOUNDS_PER_MODEL) {
                        server.execute(() -> INSTANCE.rejectBegin(player, requestId, modelId,
                                "A model can contain at most 8 action sounds"));
                        return;
                    }
                    List<SoundSpec> sounds = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        sounds.add(new SoundSpec(buffer.readString(16), buffer.readVarInt(), buffer.readString(64)));
                    }
                    server.execute(() -> INSTANCE.beginV2(player, requestId, modelId,
                            jsonSize, pngSize, animationSize, jsonHash, pngHash, animationHash, sounds));
                });

        ServerPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.UPLOAD_CHUNK_V2,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    if (!requestId.equals(INSTANCE.activeRequestIds.get(player.getUuid()))) {
                        return;
                    }
                    byte part = buffer.readByte();
                    String action = part == ModelPropsAssetProtocol.PART_SOUND_OGG
                            ? buffer.readString(16) : "";
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    server.execute(() -> INSTANCE.acceptChunk(player, requestId, 2, part, action, offset, data));
                });

        ServerPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.UPLOAD_COMMIT_V2,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    if (requestId.equals(INSTANCE.activeRequestIds.get(player.getUuid()))) {
                        server.execute(() -> INSTANCE.commit(server, player, requestId));
                    }
                });
    }

    private static void registerV3Receivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.UPLOAD_BEGIN_V3,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    String modelId = buffer.readString(128);
                    int jsonSize = buffer.readVarInt();
                    int pngSize = buffer.readVarInt();
                    int animationSize = buffer.readVarInt();
                    String jsonHash = buffer.readString(64);
                    String pngHash = buffer.readString(64);
                    String animationHash = buffer.readString(64);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL) {
                        server.execute(() -> INSTANCE.rejectBegin(player, requestId, modelId,
                                "A model can contain at most 32 named sounds"));
                        return;
                    }
                    List<SoundSpec> sounds = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) {
                        sounds.add(new SoundSpec(buffer.readString(ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH),
                                buffer.readVarInt(), buffer.readString(64)));
                    }
                    server.execute(() -> INSTANCE.beginV3(player, requestId, modelId,
                            jsonSize, pngSize, animationSize, jsonHash, pngHash, animationHash, sounds));
                });

        ServerPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.UPLOAD_CHUNK_V3,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    if (!requestId.equals(INSTANCE.activeRequestIds.get(player.getUuid()))) {
                        return;
                    }
                    byte part = buffer.readByte();
                    String soundKey = part == ModelPropsAssetProtocol.PART_SOUND_OGG
                            ? buffer.readString(ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH) : "";
                    int offset = buffer.readVarInt();
                    byte[] data = buffer.readByteArray(ModelPropsNetworking.CHUNK_SIZE);
                    server.execute(() -> INSTANCE.acceptChunk(player, requestId, 3, part, soundKey, offset, data));
                });

        ServerPlayNetworking.registerGlobalReceiver(ModelPropsAssetProtocol.UPLOAD_COMMIT_V3,
                (server, player, handler, buffer, responseSender) -> {
                    UUID requestId = buffer.readUuid();
                    if (requestId.equals(INSTANCE.activeRequestIds.get(player.getUuid()))) {
                        server.execute(() -> INSTANCE.commit(server, player, requestId));
                    }
                });
    }

    private void beginLegacy(ServerPlayerEntity player, UUID requestId, String modelId,
                             int jsonSize, int pngSize, String jsonHash, String pngHash) {
        String commonFailure = validateCommon(player, modelId, jsonSize, pngSize, jsonHash, pngHash);
        if (commonFailure != null) {
            rejectBegin(player, requestId, modelId, commonFailure);
            return;
        }
        UploadSession session = new UploadSession(requestId, modelId, 1,
                new AssetBuffer(jsonSize, jsonHash), new AssetBuffer(pngSize, pngHash),
                null, Map.of(), expiresAt());
        acceptBegin(player, session);
    }

    private void beginV2(ServerPlayerEntity player, UUID requestId, String modelId,
                         int jsonSize, int pngSize, int animationSize,
                         String jsonHash, String pngHash, String animationHash,
                         List<SoundSpec> specs) {
        beginExtended(player, requestId, modelId, jsonSize, pngSize, animationSize,
                jsonHash, pngHash, animationHash, specs, 2);
    }

    private void beginV3(ServerPlayerEntity player, UUID requestId, String modelId,
                         int jsonSize, int pngSize, int animationSize,
                         String jsonHash, String pngHash, String animationHash,
                         List<SoundSpec> specs) {
        beginExtended(player, requestId, modelId, jsonSize, pngSize, animationSize,
                jsonHash, pngHash, animationHash, specs, 3);
    }

    private void beginExtended(ServerPlayerEntity player, UUID requestId, String modelId,
                               int jsonSize, int pngSize, int animationSize,
                               String jsonHash, String pngHash, String animationHash,
                               List<SoundSpec> specs, int protocolVersion) {
        String commonFailure = validateCommon(player, modelId, jsonSize, pngSize, jsonHash, pngHash);
        if (commonFailure != null) {
            rejectBegin(player, requestId, modelId, commonFailure);
            return;
        }
        if (animationSize < 0 || animationSize > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES
                || !validHash(animationHash)) {
            rejectBegin(player, requestId, modelId, "Animation JSON must not exceed 512 KiB");
            return;
        }
        LinkedHashMap<String, AssetBuffer> sounds = new LinkedHashMap<>();
        Set<String> foldedKeys = new HashSet<>();
        long soundBytes = 0L;
        for (SoundSpec spec : specs) {
            boolean validKey = protocolVersion == 2
                    ? ModelPropsAssetProtocol.validAction(spec.key())
                    : ModelPropsAssetProtocol.validSoundKey(spec.key());
            if (!validKey || !foldedKeys.add(spec.key().toLowerCase(Locale.ROOT))) {
                rejectBegin(player, requestId, modelId, protocolVersion == 2
                        ? "Duplicate or unsupported sound action"
                        : "Duplicate or invalid named sound key");
                return;
            }
            if (spec.size() < 1 || spec.size() > ModelPropsAssetProtocol.MAX_SOUND_BYTES
                    || !validHash(spec.hash())
                    || (soundBytes += spec.size()) > ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES) {
                rejectBegin(player, requestId, modelId,
                        "Named sounds exceed the 2 MiB/file or 8 MiB/model limit");
                return;
            }
            sounds.put(spec.key(), new AssetBuffer(spec.size(), spec.hash()));
        }
        UploadSession session = new UploadSession(requestId, modelId, protocolVersion,
                new AssetBuffer(jsonSize, jsonHash), new AssetBuffer(pngSize, pngHash),
                new AssetBuffer(animationSize, animationHash), sounds, expiresAt());
        acceptBegin(player, session);
    }

    private String validateCommon(ServerPlayerEntity player, String modelId, int jsonSize, int pngSize,
                                  String jsonHash, String pngHash) {
        cleanupExpired();
        removeSession(player.getUuid());
        if (!player.hasPermissionLevel(2)) {
            return "Only server operators can upload models";
        }
        if (!ServerModelInstaller.isValidRequestedId(modelId)) {
            return "Invalid model ID; use lowercase namespace:path";
        }
        if (jsonSize < 1 || jsonSize > ServerModelCatalog.MAX_MODEL_BYTES) {
            return "JSON must be between 1 byte and 256 KiB";
        }
        if (pngSize < 1 || pngSize > ServerModelCatalog.MAX_TEXTURE_BYTES) {
            return "PNG must be between 1 byte and 4 MiB";
        }
        if (!validHash(jsonHash) || !validHash(pngHash)) {
            return "Invalid upload checksum";
        }
        return null;
    }

    private void acceptBegin(ServerPlayerEntity player, UploadSession session) {
        long activeBytes = sessions.values().stream().mapToLong(UploadSession::allocatedBytes).sum();
        if (sessions.size() >= MAX_ACTIVE_UPLOADS
                || activeBytes + session.allocatedBytes() > MAX_ACTIVE_BYTES) {
            error(player, session.requestId, session.modelId,
                    "The server is already processing too many uploads; try again shortly");
            return;
        }
        sessions.put(player.getUuid(), session);
        activeRequestIds.put(player.getUuid(), session.requestId);
        ModelPropsNetworking.sendUploadStatus(player, session.requestId,
                ModelPropsNetworking.UPLOAD_READY, session.modelId, "");
    }

    private void rejectBegin(ServerPlayerEntity player, UUID requestId, String modelId, String message) {
        removeSession(player.getUuid());
        error(player, requestId, modelId, message);
    }

    private void acceptChunk(ServerPlayerEntity player, UUID requestId, int protocolVersion, byte part,
                             String soundKey, int offset, byte[] data) {
        UploadSession session = sessions.get(player.getUuid());
        if (session == null || !session.requestId.equals(requestId)) {
            return;
        }
        if (session.protocolVersion != protocolVersion) {
            removeSession(player.getUuid());
            error(player, requestId, session.modelId, "Upload protocol changed during transfer");
            return;
        }
        if (!player.hasPermissionLevel(2)) {
            removeSession(player.getUuid());
            error(player, requestId, session.modelId, "Upload permission was revoked");
            return;
        }
        String failure = session.accept(part, soundKey, offset, data);
        if (failure != null) {
            removeSession(player.getUuid());
            error(player, requestId, session.modelId, failure);
            return;
        }
        session.expiresAt = expiresAt();
    }

    private void commit(MinecraftServer server, ServerPlayerEntity player, UUID requestId) {
        UploadSession session = sessions.get(player.getUuid());
        if (session == null || !session.requestId.equals(requestId)) {
            return;
        }
        removeSession(player.getUuid());
        if (!player.hasPermissionLevel(2)) {
            error(player, requestId, session.modelId, "Upload permission was revoked");
            return;
        }
        if (!session.complete()) {
            error(player, requestId, session.modelId, "The upload is incomplete");
            return;
        }
        if (!session.checksumsMatch()) {
            error(player, requestId, session.modelId, "Upload checksum mismatch");
            return;
        }

        try {
            ServerModelInstaller.InstallResult installed;
            if (session.protocolVersion == 3) {
                installed = ServerModelInstaller.installV3(session.modelId, session.json.data, session.png.data,
                        session.animation.data, session.soundBytes(), requestId);
            } else if (session.protocolVersion == 2) {
                installed = ServerModelInstaller.install(session.modelId, session.json.data, session.png.data,
                        session.animation.data, session.soundBytes(), requestId);
            } else {
                installed = ServerModelInstaller.install(session.modelId, session.json.data, session.png.data,
                        requestId);
            }
            ModelPropsNetworking.sendUploadStatus(player, requestId,
                    ModelPropsNetworking.UPLOAD_SUCCESS, installed.id(), "");
            ModelPropsNetworking.broadcastFullSync(server);
            ModelProps.LOGGER.info("{} uploaded Model Props model {}",
                    player.getGameProfile().getName(), installed.id());
        } catch (IOException | RuntimeException exception) {
            ModelProps.LOGGER.warn("Could not install model upload {} from {}",
                    requestId, player.getGameProfile().getName(), exception);
            error(player, requestId, session.modelId, safeMessage(exception));
        }
    }

    private void tick(MinecraftServer server) {
        cleanupExpired();
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, UploadSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UploadSession> entry = iterator.next();
            if (entry.getValue().expiresAt < now) {
                activeRequestIds.remove(entry.getKey(), entry.getValue().requestId);
                iterator.remove();
            }
        }
    }

    private void removeSession(UUID playerId) {
        UploadSession removed = sessions.remove(playerId);
        if (removed != null) {
            activeRequestIds.remove(playerId, removed.requestId);
        } else {
            activeRequestIds.remove(playerId);
        }
    }

    private static long expiresAt() {
        return System.currentTimeMillis() + TIMEOUT_MILLIS;
    }

    private static boolean validHash(String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private static void error(ServerPlayerEntity player, UUID requestId, String modelId, String message) {
        ModelPropsNetworking.sendUploadStatus(player, requestId,
                ModelPropsNetworking.UPLOAD_ERROR, modelId, message);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "The server could not install the uploaded files";
        }
        message = message.replace(ServerModelCatalog.INSTANCE.root().toString(), "config/modelprops")
                .replace('\r', ' ').replace('\n', ' ');
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private record SoundSpec(String key, int size, String hash) {
    }

    private static final class AssetBuffer {
        private final byte[] data;
        private final String hash;
        private int offset;

        private AssetBuffer(int size, String hash) {
            this.data = new byte[size];
            this.hash = hash;
        }

        private String accept(int incomingOffset, byte[] chunk) {
            if (incomingOffset != offset || (long) incomingOffset + chunk.length > data.length) {
                return "Upload chunks arrived out of order";
            }
            System.arraycopy(chunk, 0, data, incomingOffset, chunk.length);
            offset += chunk.length;
            return null;
        }

        private boolean complete() {
            return offset == data.length;
        }

        private boolean checksumMatches() {
            return ModelPropsNetworking.sha256(data).equalsIgnoreCase(hash);
        }
    }

    private static final class UploadSession {
        private final UUID requestId;
        private final String modelId;
        private final int protocolVersion;
        private final AssetBuffer json;
        private final AssetBuffer png;
        private final AssetBuffer animation;
        private final Map<String, AssetBuffer> sounds;
        private long expiresAt;

        private UploadSession(UUID requestId, String modelId, int protocolVersion,
                              AssetBuffer json, AssetBuffer png, AssetBuffer animation,
                              Map<String, AssetBuffer> sounds, long expiresAt) {
            this.requestId = requestId;
            this.modelId = modelId;
            this.protocolVersion = protocolVersion;
            this.json = json;
            this.png = png;
            this.animation = animation;
            this.sounds = new LinkedHashMap<>(sounds);
            this.expiresAt = expiresAt;
        }

        private long allocatedBytes() {
            long result = (long) json.data.length + png.data.length;
            if (animation != null) {
                result += animation.data.length;
            }
            for (AssetBuffer sound : sounds.values()) {
                result += sound.data.length;
            }
            return result;
        }

        private String accept(byte part, String soundKey, int offset, byte[] data) {
            if (data.length < 1 || data.length > ModelPropsNetworking.CHUNK_SIZE) {
                return "Upload chunk size is invalid";
            }
            AssetBuffer target;
            if (part == ModelPropsAssetProtocol.PART_MODEL_JSON) {
                target = json;
            } else if (part == ModelPropsAssetProtocol.PART_TEXTURE_PNG) {
                target = png;
            } else if (part == ModelPropsAssetProtocol.PART_ANIMATION_JSON && protocolVersion >= 2) {
                target = animation;
            } else if (part == ModelPropsAssetProtocol.PART_SOUND_OGG && protocolVersion >= 2) {
                target = sounds.get(soundKey);
            } else {
                return "Upload chunk type is invalid";
            }
            return target == null ? "Unknown upload asset" : target.accept(offset, data);
        }

        private boolean complete() {
            if (!json.complete() || !png.complete() || (animation != null && !animation.complete())) {
                return false;
            }
            return sounds.values().stream().allMatch(AssetBuffer::complete);
        }

        private boolean checksumsMatch() {
            if (!json.checksumMatches() || !png.checksumMatches()
                    || (animation != null && !animation.checksumMatches())) {
                return false;
            }
            return sounds.values().stream().allMatch(AssetBuffer::checksumMatches);
        }

        private Map<String, byte[]> soundBytes() {
            LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
            sounds.forEach((soundKey, sound) -> result.put(soundKey, sound.data));
            return result;
        }
    }
}
