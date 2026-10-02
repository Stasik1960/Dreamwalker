package ru.modelprops.client.upload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.modelprops.animation.AnimationParser;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.client.ModelUploadScreen;
import ru.modelprops.net.ModelPropsAssetProtocol;
import ru.modelprops.net.ModelPropsNetworking;
import ru.modelprops.server.upload.OggVorbisValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClientUploadManager {
    private static final int CHUNKS_PER_TICK = 4;
    private static final int RESPONSE_TIMEOUT_TICKS = 2_400;
    private static final int PROTOCOL_LEGACY = 1;
    private static final int PROTOCOL_V2 = 2;
    private static final int PROTOCOL_V3 = 3;

    private static UUID requestId;
    private static String requestedModelId;
    private static List<UploadAsset> assets = List.of();
    private static int assetIndex;
    private static int totalBytes;
    private static int protocol;
    private static int timeoutTicks;
    private static Phase phase;

    private ClientUploadManager() {
    }

    /** Legacy model + texture upload. Existing callers and 1.1 servers keep working. */
    public static UUID start(String modelId, byte[] jsonBytes, byte[] pngBytes) {
        ensureAvailable(PROTOCOL_LEGACY);
        validateBaseFiles(jsonBytes, pngBytes);
        List<UploadAsset> upload = List.of(
                new UploadAsset(ModelPropsAssetProtocol.PART_MODEL_JSON, "", jsonBytes.clone()),
                new UploadAsset(ModelPropsAssetProtocol.PART_TEXTURE_PNG, "", pngBytes.clone())
        );
        return begin(modelId, upload, PROTOCOL_LEGACY);
    }

    /**
     * Extended atomic upload. Empty optional parts leave existing sidecars intact
     * when replacing a model previously uploaded by this menu.
     */
    public static UUID start(String modelId, byte[] jsonBytes, byte[] pngBytes,
                             byte[] animationBytes, Map<String, byte[]> soundBytes) {
        validateBaseFiles(jsonBytes, pngBytes);
        byte[] animation = animationBytes == null ? new byte[0] : animationBytes.clone();
        if (animation.length > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES) {
            throw new IllegalArgumentException("Animation JSON is larger than 512 KiB");
        }
        AnimationSet animationSet = AnimationSet.EMPTY;
        if (animation.length > 0) {
            try {
                animationSet = AnimationParser.parse(
                        new String(animation, java.nio.charset.StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid animation: " + exception.getMessage(), exception);
            }
        }

        Map<String, byte[]> incomingSounds = soundBytes == null ? Map.of() : soundBytes;
        if (incomingSounds.size() > ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL) {
            throw new IllegalArgumentException("A model can contain at most 32 sounds");
        }
        boolean useV3 = incomingSounds.size() > ModelPropsAssetProtocol.MAX_LEGACY_SOUNDS_PER_MODEL
                || incomingSounds.keySet().stream().anyMatch(key -> !ModelPropsAssetProtocol.validAction(key));
        int selectedProtocol = useV3 ? PROTOCOL_V3 : PROTOCOL_V2;
        ensureAvailable(selectedProtocol);
        ArrayList<UploadAsset> upload = new ArrayList<>(3 + incomingSounds.size());
        upload.add(new UploadAsset(ModelPropsAssetProtocol.PART_MODEL_JSON, "", jsonBytes.clone()));
        upload.add(new UploadAsset(ModelPropsAssetProtocol.PART_TEXTURE_PNG, "", pngBytes.clone()));
        upload.add(new UploadAsset(ModelPropsAssetProtocol.PART_ANIMATION_JSON, "", animation));

        long soundTotal = 0L;
        Set<String> foldedSoundKeys = new HashSet<>();
        List<Map.Entry<String, byte[]>> sortedSounds = incomingSounds.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey)).toList();
        for (Map.Entry<String, byte[]> entry : sortedSounds) {
            String action = entry.getKey();
            byte[] sound = entry.getValue();
            if (!ModelPropsAssetProtocol.validSoundKey(action)
                    || (!ModelPropsAssetProtocol.validAction(action)
                    && !animationSet.clips().containsKey(action))) {
                throw new IllegalArgumentException("Sound key must be an action or a merged animation clip: "
                        + action);
            }
            if (!foldedSoundKeys.add(action.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Sound keys must be unique ignoring case");
            }
            if (sound == null || sound.length < 1 || sound.length > ModelPropsAssetProtocol.MAX_SOUND_BYTES
                    || (soundTotal += sound.length) > ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES) {
                throw new IllegalArgumentException("Action sounds exceed the 2 MiB/file or 8 MiB/model limit");
            }
            try {
                OggVorbisValidator.validate(sound);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Invalid " + action + " OGG: " + exception.getMessage(), exception);
            }
            upload.add(new UploadAsset(ModelPropsAssetProtocol.PART_SOUND_OGG, action, sound.clone()));
        }
        return begin(modelId, upload, selectedProtocol);
    }

    public static boolean supportsExtendedUpload() {
        return ClientPlayNetworking.canSend(ModelPropsAssetProtocol.UPLOAD_BEGIN_V2)
                && ClientPlayNetworking.canSend(ModelPropsAssetProtocol.UPLOAD_CHUNK_V2)
                && ClientPlayNetworking.canSend(ModelPropsAssetProtocol.UPLOAD_COMMIT_V2);
    }

    public static boolean supportsBundleUpload() {
        return ClientPlayNetworking.canSend(ModelPropsAssetProtocol.UPLOAD_BEGIN_V3)
                && ClientPlayNetworking.canSend(ModelPropsAssetProtocol.UPLOAD_CHUNK_V3)
                && ClientPlayNetworking.canSend(ModelPropsAssetProtocol.UPLOAD_COMMIT_V3);
    }

    private static void ensureAvailable(int selectedProtocol) {
        if (isBusy()) {
            throw new IllegalStateException("Another model upload is already running");
        }
        boolean available = selectedProtocol == PROTOCOL_V3 ? supportsBundleUpload()
                : selectedProtocol == PROTOCOL_V2 ? supportsExtendedUpload()
                : ClientPlayNetworking.canSend(ModelPropsNetworking.UPLOAD_BEGIN)
                && ClientPlayNetworking.canSend(ModelPropsNetworking.UPLOAD_CHUNK)
                && ClientPlayNetworking.canSend(ModelPropsNetworking.UPLOAD_COMMIT);
        if (!available) {
            throw new IllegalStateException(selectedProtocol == PROTOCOL_V3
                    ? "This server does not support clip-linked sound bundles"
                    : selectedProtocol == PROTOCOL_V2
                    ? "This server does not support animation and sound uploads"
                    : "This server does not support the Model Props upload menu");
        }
    }

    private static void validateBaseFiles(byte[] jsonBytes, byte[] pngBytes) {
        if (jsonBytes == null || pngBytes == null
                || jsonBytes.length < 1 || jsonBytes.length > ModelPropsNetworking.MAX_UPLOAD_JSON_BYTES
                || pngBytes.length < 1 || pngBytes.length > ModelPropsNetworking.MAX_UPLOAD_PNG_BYTES) {
            throw new IllegalArgumentException("Selected files are outside the supported size limits");
        }
    }

    private static UUID begin(String modelId, List<UploadAsset> upload, int selectedProtocol) {
        requestId = UUID.randomUUID();
        requestedModelId = modelId;
        assets = List.copyOf(upload);
        assetIndex = 0;
        totalBytes = assets.stream().mapToInt(asset -> asset.data.length).sum();
        protocol = selectedProtocol;
        timeoutTicks = RESPONSE_TIMEOUT_TICKS;
        phase = Phase.WAITING_READY;

        PacketByteBuf packet = PacketByteBufs.create();
        packet.writeUuid(requestId);
        packet.writeString(modelId, 128);
        UploadAsset json = assets.get(0);
        UploadAsset png = assets.get(1);
        packet.writeVarInt(json.data.length);
        packet.writeVarInt(png.data.length);
        if (selectedProtocol >= PROTOCOL_V2) {
            UploadAsset animation = assets.get(2);
            packet.writeVarInt(animation.data.length);
            packet.writeString(ModelPropsNetworking.sha256(json.data), 64);
            packet.writeString(ModelPropsNetworking.sha256(png.data), 64);
            packet.writeString(ModelPropsNetworking.sha256(animation.data), 64);
            packet.writeVarInt(assets.size() - 3);
            for (int index = 3; index < assets.size(); index++) {
                UploadAsset sound = assets.get(index);
                packet.writeString(sound.action, selectedProtocol == PROTOCOL_V3
                        ? ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH : 16);
                packet.writeVarInt(sound.data.length);
                packet.writeString(ModelPropsNetworking.sha256(sound.data), 64);
            }
        } else {
            packet.writeString(ModelPropsNetworking.sha256(json.data), 64);
            packet.writeString(ModelPropsNetworking.sha256(png.data), 64);
        }
        try {
            Identifier channel = beginChannel(selectedProtocol);
            ClientPlayNetworking.send(channel, packet);
        } catch (RuntimeException exception) {
            clear();
            throw exception;
        }
        return requestId;
    }

    public static void tick(MinecraftClient client) {
        if (phase == null) {
            return;
        }
        if (--timeoutTicks <= 0) {
            failLocally(client, "The server did not answer the upload request in time");
            return;
        }
        if (phase != Phase.SENDING) {
            return;
        }
        try {
            for (int sent = 0; sent < CHUNKS_PER_TICK; sent++) {
                while (assetIndex < assets.size() && assets.get(assetIndex).complete()) {
                    assetIndex++;
                }
                if (assetIndex >= assets.size()) {
                    PacketByteBuf commit = PacketByteBufs.create();
                    commit.writeUuid(requestId);
                    ClientPlayNetworking.send(commitChannel(protocol), commit);
                    assets = List.of();
                    phase = Phase.WAITING_RESULT;
                    timeoutTicks = RESPONSE_TIMEOUT_TICKS;
                    break;
                }
                UploadAsset asset = assets.get(assetIndex);
                int end = Math.min(asset.data.length, asset.offset + ModelPropsNetworking.CHUNK_SIZE);
                byte[] chunk = java.util.Arrays.copyOfRange(asset.data, asset.offset, end);
                PacketByteBuf packet = PacketByteBufs.create();
                packet.writeUuid(requestId);
                packet.writeByte(asset.part);
                if (protocol >= PROTOCOL_V2 && asset.part == ModelPropsAssetProtocol.PART_SOUND_OGG) {
                    packet.writeString(asset.action, protocol == PROTOCOL_V3
                            ? ModelPropsAssetProtocol.MAX_SOUND_KEY_LENGTH : 16);
                }
                packet.writeVarInt(asset.offset);
                packet.writeByteArray(chunk);
                ClientPlayNetworking.send(chunkChannel(protocol), packet);
                asset.offset = end;
            }
        } catch (RuntimeException exception) {
            failLocally(client, exception.getMessage() == null ? "Could not send upload data" : exception.getMessage());
        }
    }

    public static void handleStatus(MinecraftClient client, UUID incomingId, byte status,
                                    String modelId, String message) {
        if (requestId == null || !requestId.equals(incomingId)) {
            return;
        }
        if (status == ModelPropsNetworking.UPLOAD_READY) {
            if (phase == Phase.WAITING_READY) {
                phase = Phase.SENDING;
                timeoutTicks = RESPONSE_TIMEOUT_TICKS;
            }
            return;
        }

        boolean success = status == ModelPropsNetworking.UPLOAD_SUCCESS;
        String resultId = modelId.isBlank() ? requestedModelId : modelId;
        clear();
        Text result = success
                ? Text.translatable("message.modelprops.upload.success", resultId)
                : Text.translatable("message.modelprops.upload.failed", message);
        if (client.currentScreen instanceof ModelUploadScreen screen) {
            screen.acceptUploadResult(success, result);
        }
        if (client.player != null) {
            client.player.sendMessage(result, false);
        }
    }

    public static boolean isBusy() {
        return phase != null;
    }

    public static double progress() {
        if (phase == null) {
            return 0.0;
        }
        if (phase == Phase.WAITING_RESULT) {
            return 1.0;
        }
        if (totalBytes <= 0) {
            return 0.0;
        }
        long sent = assets.stream().mapToLong(asset -> asset.offset).sum();
        return Math.min(1.0, sent / (double) totalBytes);
    }

    public static Text statusText() {
        if (phase == Phase.WAITING_READY) {
            return Text.translatable("screen.modelprops.upload.waiting_ready");
        }
        if (phase == Phase.SENDING) {
            return Text.translatable("screen.modelprops.upload.progress", (int) Math.round(progress() * 100.0));
        }
        if (phase == Phase.WAITING_RESULT) {
            return Text.translatable("screen.modelprops.upload.waiting_result");
        }
        return Text.empty();
    }

    public static void reset() {
        clear();
    }

    private static void failLocally(MinecraftClient client, String message) {
        clear();
        Text result = Text.translatable("message.modelprops.upload.failed", message);
        if (client.currentScreen instanceof ModelUploadScreen screen) {
            screen.acceptUploadResult(false, result);
        } else if (client.player != null) {
            client.player.sendMessage(result, false);
        }
    }

    private static void clear() {
        requestId = null;
        requestedModelId = null;
        assets = List.of();
        assetIndex = 0;
        totalBytes = 0;
        protocol = 0;
        timeoutTicks = 0;
        phase = null;
    }

    private static Identifier beginChannel(int selectedProtocol) {
        return selectedProtocol == PROTOCOL_V3 ? ModelPropsAssetProtocol.UPLOAD_BEGIN_V3
                : selectedProtocol == PROTOCOL_V2 ? ModelPropsAssetProtocol.UPLOAD_BEGIN_V2
                : ModelPropsNetworking.UPLOAD_BEGIN;
    }

    private static Identifier chunkChannel(int selectedProtocol) {
        return selectedProtocol == PROTOCOL_V3 ? ModelPropsAssetProtocol.UPLOAD_CHUNK_V3
                : selectedProtocol == PROTOCOL_V2 ? ModelPropsAssetProtocol.UPLOAD_CHUNK_V2
                : ModelPropsNetworking.UPLOAD_CHUNK;
    }

    private static Identifier commitChannel(int selectedProtocol) {
        return selectedProtocol == PROTOCOL_V3 ? ModelPropsAssetProtocol.UPLOAD_COMMIT_V3
                : selectedProtocol == PROTOCOL_V2 ? ModelPropsAssetProtocol.UPLOAD_COMMIT_V2
                : ModelPropsNetworking.UPLOAD_COMMIT;
    }

    private static final class UploadAsset {
        private final byte part;
        private final String action;
        private final byte[] data;
        private int offset;

        private UploadAsset(byte part, String action, byte[] data) {
            this.part = part;
            this.action = action;
            this.data = data;
        }

        private boolean complete() {
            return offset == data.length;
        }
    }

    private enum Phase {
        WAITING_READY,
        SENDING,
        WAITING_RESULT
    }
}
