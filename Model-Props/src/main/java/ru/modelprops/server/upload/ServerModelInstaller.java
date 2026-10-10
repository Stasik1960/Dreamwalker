package ru.modelprops.server.upload;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import ru.modelprops.animation.AnimationParser;
import ru.modelprops.animation.AnimationSet;
import ru.modelprops.animation.ModelAnimationTargets;
import ru.modelprops.net.ModelPropsAssetProtocol;
import ru.modelprops.server.ServerModelCatalog;
import ru.modelprops.server.ServerModelEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerModelInstaller {
    private static final Gson GSON = new Gson();
    private static final Set<String> FACE_NAMES = Set.of("down", "up", "north", "south", "west", "east");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    );
    private static final String TEXTURE_ALIAS = "modelprops_upload";

    private ServerModelInstaller() {
    }

    public static boolean isValidRequestedId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            return false;
        }
        Identifier id = Identifier.tryParse(value);
        if (id == null || !safeSegment(id.getNamespace())) {
            return false;
        }
        for (String segment : id.getPath().split("/", -1)) {
            if (!safeSegment(segment)) {
                return false;
            }
        }
        return true;
    }

    public static synchronized InstallResult install(String requestedId, byte[] originalJson,
                                                     byte[] png, UUID requestId) throws IOException {
        return installInternal(requestedId, originalJson, png, null, null, 1, requestId);
    }

    public static synchronized InstallResult install(String requestedId, byte[] originalJson,
                                                      byte[] png, byte[] animationJson,
                                                      Map<String, byte[]> sounds,
                                                      UUID requestId) throws IOException {
        if (animationJson == null || sounds == null) {
            throw new IOException("Animation and sound upload metadata is missing");
        }
        return installInternal(requestedId, originalJson, png, animationJson, sounds, 2, requestId);
    }

    public static synchronized InstallResult installV3(String requestedId, byte[] originalJson,
                                                        byte[] png, byte[] animationJson,
                                                        Map<String, byte[]> sounds,
                                                        UUID requestId) throws IOException {
        if (animationJson == null || sounds == null) {
            throw new IOException("Animation and sound upload metadata is missing");
        }
        return installInternal(requestedId, originalJson, png, animationJson, sounds, 3, requestId);
    }

    private static InstallResult installInternal(String requestedId, byte[] originalJson,
                                                 byte[] png, byte[] uploadedAnimation,
                                                 Map<String, byte[]> uploadedSounds,
                                                 int protocolVersion,
                                                 UUID requestId) throws IOException {
        if (!isValidRequestedId(requestedId)) {
            throw new IOException("Invalid model ID; use lowercase namespace:path");
        }
        if (originalJson.length < 1 || originalJson.length > ServerModelCatalog.MAX_MODEL_BYTES) {
            throw new IOException("JSON size must be between 1 byte and 256 KiB");
        }
        if (png.length < 1 || png.length > ServerModelCatalog.MAX_TEXTURE_BYTES) {
            throw new IOException("PNG size must be between 1 byte and 4 MiB");
        }
        PngValidator.validate(png);

        ServerModelCatalog catalog = ServerModelCatalog.INSTANCE;
        Path root = catalog.root().toAbsolutePath().normalize();
        ChosenId chosen = chooseId(catalog, root, requestedId);
        Identifier identifier = Identifier.tryParse(chosen.id());
        if (identifier == null) {
            throw new IOException("Could not create a safe model ID");
        }
        String textureReference = "modelprops:uploaded/" + identifier.getNamespace() + "/" + identifier.getPath();
        String normalizedJson = normalizeJson(originalJson, chosen.id(), textureReference);
        byte[] normalizedBytes = normalizedJson.getBytes(StandardCharsets.UTF_8);
        if (normalizedBytes.length > ServerModelCatalog.MAX_MODEL_BYTES) {
            throw new IOException("Normalized JSON is larger than 256 KiB");
        }

        ServerModelEntry previous = chosen.overwrite() ? catalog.get(chosen.id()) : null;
        String animationJson;
        Map<String, byte[]> sounds;
        if (uploadedAnimation == null) {
            animationJson = previous == null ? "" : previous.animationJson();
            sounds = previous == null ? Map.of() : previous.sounds();
        } else {
            validateUploadedSounds(uploadedSounds, protocolVersion);
            animationJson = uploadedAnimation.length == 0 && previous != null
                    ? previous.animationJson()
                    : normalizeAnimation(uploadedAnimation, normalizedJson);
            AnimationSet animation;
            try {
                animation = AnimationParser.parse(animationJson, ModelAnimationTargets.fromModelJson(normalizedJson));
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid animation: " + exception.getMessage(), exception);
            }
            LinkedHashMap<String, byte[]> mergedSounds = new LinkedHashMap<>();
            if (previous != null) {
                // A replacement bundle may remove or rename clips. Keep legacy
                // action sounds, but silently discard now-orphaned clip sounds
                // so an otherwise valid re-upload is not permanently blocked.
                previous.sounds().forEach((soundKey, bytes) -> {
                    if (ModelPropsAssetProtocol.validAction(soundKey) || animation.hasClip(soundKey)) {
                        mergedSounds.put(soundKey, bytes);
                    }
                });
            }
            mergedSounds.putAll(uploadedSounds);
            sounds = validateSounds(mergedSounds, animation);
        }
        byte[] animationBytes = animationJson.getBytes(StandardCharsets.UTF_8);

        checkCatalogCapacity(catalog, chosen, normalizedBytes.length, png.length,
                animationBytes.length, sounds);

        Path modelTarget = modelPath(root, chosen.id());
        Path textureTarget = texturePath(root, chosen.id());
        Path animationTarget = animationPath(root, chosen.id());
        Path soundsTarget = soundsPath(root, chosen.id());
        prepareTarget(root, modelTarget);
        prepareTarget(root, textureTarget);
        prepareTarget(root, animationTarget);
        prepareDirectoryTarget(root, soundsTarget);
        publishAndReload(catalog, chosen.id(), normalizedJson, normalizedBytes, png,
                animationJson, animationBytes, sounds, textureReference,
                modelTarget, textureTarget, animationTarget, soundsTarget, requestId);
        return new InstallResult(chosen.id(), chosen.overwrite());
    }

    private static ChosenId chooseId(ServerModelCatalog catalog, Path root, String requested) throws IOException {
        Identifier base = Identifier.tryParse(requested);
        if (base == null) {
            throw new IOException("Invalid model ID");
        }
        for (int index = 1; index <= 999; index++) {
            String suffix = index == 1 ? "" : "_" + index;
            int maximumPathLength = 128 - base.getNamespace().length() - 1 - suffix.length();
            if (maximumPathLength < 1) {
                break;
            }
            String basePath = base.getPath();
            if (basePath.length() > maximumPathLength) {
                basePath = basePath.substring(0, maximumPathLength);
            }
            while (basePath.endsWith("/") || basePath.endsWith(".")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            if (basePath.isBlank()) {
                continue;
            }
            String candidate = base.getNamespace() + ":" + basePath + suffix;
            if (!isValidRequestedId(candidate)) {
                continue;
            }
            Path model = modelPath(root, candidate);
            Path texture = texturePath(root, candidate);
            Path animation = animationPath(root, candidate);
            Path sounds = soundsPath(root, candidate);
            boolean managed = Files.isRegularFile(model, LinkOption.NOFOLLOW_LINKS)
                    && isManagedUpload(model, candidate);
            if (index == 1 && managed) {
                return new ChosenId(candidate, true);
            }
            if (!catalog.contains(candidate)
                    && !Files.exists(model, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(texture, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(animation, LinkOption.NOFOLLOW_LINKS)
                    && !Files.exists(sounds, LinkOption.NOFOLLOW_LINKS)) {
                return new ChosenId(candidate, false);
            }
        }
        throw new IOException("Could not find a free model ID after 999 attempts");
    }

    private static boolean isManagedUpload(Path model, String id) {
        try {
            if (Files.size(model) > ServerModelCatalog.MAX_MODEL_BYTES) {
                return false;
            }
            JsonObject root = JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8)).getAsJsonObject();
            return root.has("modelprops_uploaded") && root.get("modelprops_uploaded").getAsBoolean()
                    && root.has("modelprops_upload_id") && id.equals(root.get("modelprops_upload_id").getAsString());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeJson(byte[] bytes, String id, String textureReference) throws IOException {
        String json = decodeUtf8(bytes, "JSON");
        validateDepth(json);

        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException | ClassCastException exception) {
            throw new IOException("The selected JSON root must be an object", exception);
        } catch (StackOverflowError error) {
            throw new IOException("The selected JSON is nested too deeply", error);
        }

        JsonObject textures = new JsonObject();
        root.add("textures", textures);
        textures.addProperty(TEXTURE_ALIAS, textureReference);

        boolean hasElements = false;
        int rewrittenFaces = 0;
        if (root.has("elements")) {
            if (!root.get("elements").isJsonArray()) {
                throw new IOException("elements must be a JSON array");
            }
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements.size() > 1024) {
                throw new IOException("Model has more than 1024 elements");
            }
            hasElements = !elements.isEmpty();
            for (JsonElement elementValue : elements) {
                if (!elementValue.isJsonObject()) {
                    continue;
                }
                JsonObject element = elementValue.getAsJsonObject();
                if (!element.has("faces") || !element.get("faces").isJsonObject()) {
                    continue;
                }
                for (var faceEntry : element.getAsJsonObject("faces").entrySet()) {
                    if (FACE_NAMES.contains(faceEntry.getKey().toLowerCase(Locale.ROOT))
                            && faceEntry.getValue().isJsonObject()) {
                        faceEntry.getValue().getAsJsonObject().addProperty("texture", "#" + TEXTURE_ALIAS);
                        rewrittenFaces++;
                    }
                }
            }
        }
        if (hasElements && rewrittenFaces == 0) {
            throw new IOException("The model contains elements but no renderable faces");
        }
        if (!hasElements) {
            textures.addProperty("layer0", "#" + TEXTURE_ALIAS);
        }
        root.addProperty("modelprops_uploaded", true);
        root.addProperty("modelprops_upload_id", id);
        return GSON.toJson(root);
    }

    private static String normalizeAnimation(byte[] bytes, String modelJson) throws IOException {
        if (bytes.length == 0) {
            return "";
        }
        if (bytes.length > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES) {
            throw new IOException("Animation JSON is larger than 512 KiB");
        }
        String json = decodeUtf8(bytes, "Animation JSON");
        validateDepth(json);
        try {
            AnimationParser.parse(json, ModelAnimationTargets.fromModelJson(modelJson));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid animation: " + exception.getMessage(), exception);
        }
        return json;
    }

    private static void validateUploadedSounds(Map<String, byte[]> source, int protocolVersion) throws IOException {
        int maximum = protocolVersion == 2
                ? ModelPropsAssetProtocol.MAX_LEGACY_SOUNDS_PER_MODEL
                : ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL;
        if (source.size() > maximum) {
            throw new IOException(protocolVersion == 2
                    ? "A v2 upload can contain at most 8 action sounds"
                    : "A model can contain at most 32 named sounds");
        }
        Set<String> foldedKeys = new HashSet<>();
        for (String soundKey : source.keySet()) {
            boolean valid = protocolVersion == 2
                    ? ModelPropsAssetProtocol.validAction(soundKey)
                    : ModelPropsAssetProtocol.validSoundKey(soundKey);
            if (!valid) {
                throw new IOException(protocolVersion == 2
                        ? "Unsupported sound action '" + soundKey + "'"
                        : "Invalid named sound key '" + soundKey + "'");
            }
            if (!foldedKeys.add(soundKey.toLowerCase(Locale.ROOT))) {
                throw new IOException("Named sound keys must be unique ignoring case");
            }
        }
    }

    private static Map<String, byte[]> validateSounds(Map<String, byte[]> source,
                                                       AnimationSet animation) throws IOException {
        if (source.size() > ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL) {
            throw new IOException("A model can contain at most 32 named sounds");
        }
        LinkedHashMap<String, byte[]> validated = new LinkedHashMap<>();
        Set<String> foldedKeys = new HashSet<>();
        long total = 0L;
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            String soundKey = entry.getKey();
            byte[] bytes = entry.getValue();
            if (!ModelPropsAssetProtocol.validSoundKey(soundKey)) {
                throw new IOException("Invalid named sound key '" + soundKey + "'");
            }
            if (!foldedKeys.add(soundKey.toLowerCase(Locale.ROOT))) {
                throw new IOException("Named sound keys must be unique ignoring case");
            }
            if (!ModelPropsAssetProtocol.validAction(soundKey) && !animation.hasClip(soundKey)) {
                throw new IOException("Named sound '" + soundKey + "' does not match an animation clip");
            }
            if (bytes == null || bytes.length < 1 || bytes.length > ModelPropsAssetProtocol.MAX_SOUND_BYTES
                    || total + bytes.length > ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES) {
                throw new IOException("Named sounds exceed the 2 MiB/file or 8 MiB/model limit");
            }
            OggVorbisValidator.validate(bytes);
            validated.put(soundKey, bytes.clone());
            total += bytes.length;
        }
        return Collections.unmodifiableMap(validated);
    }

    private static String decodeUtf8(byte[] bytes, String label) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException(label + " must use valid UTF-8", exception);
        }
    }

    private static void validateDepth(String json) throws IOException {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
                continue;
            }
            if (character == '"') {
                quoted = true;
            } else if (character == '{' || character == '[') {
                if (++depth > 64) {
                    throw new IOException("JSON nesting is deeper than 64 levels");
                }
            } else if (character == '}' || character == ']') {
                if (--depth < 0) {
                    throw new IOException("JSON brackets are unbalanced");
                }
            }
        }
        if (quoted || depth != 0) {
            throw new IOException("JSON is incomplete");
        }
    }

    private static void checkCatalogCapacity(ServerModelCatalog catalog, ChosenId chosen,
                                             int jsonBytes, int textureBytes, int animationBytes,
                                             Map<String, byte[]> sounds) throws IOException {
        if (!chosen.overwrite() && catalog.entries().size() >= ServerModelCatalog.MAX_MODELS) {
            throw new IOException("The server catalog already contains 512 models");
        }
        long current = 0L;
        for (ServerModelEntry entry : catalog.entries().values()) {
            current += entrySize(entry);
        }
        ServerModelEntry replaced = chosen.overwrite() ? catalog.get(chosen.id()) : null;
        if (replaced != null) {
            current -= entrySize(replaced);
        }
        long soundBytes = sounds.values().stream().mapToLong(value -> value.length).sum();
        long projected = current + jsonBytes + textureBytes + animationBytes + soundBytes + 512L;
        if (projected > ServerModelCatalog.MAX_CATALOG_BYTES) {
            throw new IOException("The upload would make the synchronized catalog larger than 64 MiB");
        }
    }

    private static long entrySize(ServerModelEntry entry) {
        long bytes = entry.id().getBytes(StandardCharsets.UTF_8).length
                + entry.displayName().getBytes(StandardCharsets.UTF_8).length
                + entry.json().getBytes(StandardCharsets.UTF_8).length;
        for (byte[] texture : entry.textures().values()) {
            bytes += texture.length;
        }
        bytes += entry.animationJson().getBytes(StandardCharsets.UTF_8).length;
        for (byte[] sound : entry.sounds().values()) {
            bytes += sound.length;
        }
        return bytes;
    }

    private static void publishAndReload(ServerModelCatalog catalog, String id, String normalizedJson,
                                         byte[] json, byte[] png, String animationJson, byte[] animation,
                                         Map<String, byte[]> sounds, String textureReference,
                                         Path modelTarget, Path textureTarget, Path animationTarget,
                                         Path soundsTarget, UUID requestId) throws IOException {
        Path modelTemp = sibling(modelTarget, ".upload-" + requestId + ".tmp");
        Path textureTemp = sibling(textureTarget, ".upload-" + requestId + ".tmp");
        Path animationTemp = sibling(animationTarget, ".upload-" + requestId + ".tmp");
        Path soundsTemp = sibling(soundsTarget, ".upload-" + requestId + ".tmp");
        Path modelBackup = sibling(modelTarget, ".upload-" + requestId + ".bak");
        Path textureBackup = sibling(textureTarget, ".upload-" + requestId + ".bak");
        Path animationBackup = sibling(animationTarget, ".upload-" + requestId + ".bak");
        Path soundsBackup = sibling(soundsTarget, ".upload-" + requestId + ".bak");
        boolean modelBackedUp = false;
        boolean textureBackedUp = false;
        boolean animationBackedUp = false;
        boolean soundsBackedUp = false;
        boolean modelPublished = false;
        boolean texturePublished = false;
        boolean animationPublished = false;
        boolean soundsPublished = false;
        try {
            writeDurably(modelTemp, json);
            writeDurably(textureTemp, png);
            if (animation.length > 0) {
                writeDurably(animationTemp, animation);
            }
            if (!sounds.isEmpty()) {
                deleteTreeQuietly(soundsTemp);
                Files.createDirectory(soundsTemp);
                for (Map.Entry<String, byte[]> sound : sounds.entrySet()) {
                    writeDurably(soundsTemp.resolve(sound.getKey() + ".ogg"), sound.getValue());
                }
            }
            if (Files.exists(modelTarget, LinkOption.NOFOLLOW_LINKS)) {
                move(modelTarget, modelBackup, false);
                modelBackedUp = true;
            }
            if (Files.exists(textureTarget, LinkOption.NOFOLLOW_LINKS)) {
                move(textureTarget, textureBackup, false);
                textureBackedUp = true;
            }
            if (Files.exists(animationTarget, LinkOption.NOFOLLOW_LINKS)) {
                move(animationTarget, animationBackup, false);
                animationBackedUp = true;
            }
            if (Files.exists(soundsTarget, LinkOption.NOFOLLOW_LINKS)) {
                move(soundsTarget, soundsBackup, false);
                soundsBackedUp = true;
            }
            move(textureTemp, textureTarget, true);
            texturePublished = true;
            if (animation.length > 0) {
                move(animationTemp, animationTarget, true);
                animationPublished = true;
            }
            if (!sounds.isEmpty()) {
                move(soundsTemp, soundsTarget, true);
                soundsPublished = true;
            }
            move(modelTemp, modelTarget, true);
            modelPublished = true;

            catalog.reload();
            ServerModelEntry installed = catalog.get(id);
            byte[] installedTexture = installed == null ? null : installed.textures().get(textureReference);
            if (installed == null || !normalizedJson.equals(installed.json())
                    || installedTexture == null || !Arrays.equals(png, installedTexture)
                    || !animationJson.equals(installed.animationJson())
                    || !equalBytes(sounds, installed.sounds())) {
                throw new IOException("The server could not load the uploaded model");
            }
            deleteTreeQuietly(modelBackup);
            deleteTreeQuietly(textureBackup);
            deleteTreeQuietly(animationBackup);
            deleteTreeQuietly(soundsBackup);
        } catch (Exception exception) {
            IOException failure = exception instanceof IOException io
                    ? io : new IOException("Could not install the uploaded model", exception);
            if (modelPublished) {
                try {
                    Files.deleteIfExists(modelTarget);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (texturePublished) {
                try {
                    Files.deleteIfExists(textureTarget);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (animationPublished) {
                try {
                    Files.deleteIfExists(animationTarget);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (soundsPublished) {
                try {
                    deleteTree(soundsTarget);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (modelBackedUp && Files.exists(modelBackup, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    move(modelBackup, modelTarget, true);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (textureBackedUp && Files.exists(textureBackup, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    move(textureBackup, textureTarget, true);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (animationBackedUp && Files.exists(animationBackup, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    move(animationBackup, animationTarget, true);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            if (soundsBackedUp && Files.exists(soundsBackup, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    move(soundsBackup, soundsTarget, true);
                } catch (Exception rollbackError) {
                    failure.addSuppressed(rollbackError);
                }
            }
            try {
                catalog.reload();
            } catch (Exception rollbackError) {
                failure.addSuppressed(rollbackError);
            }
            throw failure;
        } finally {
            deleteTreeQuietly(modelTemp);
            deleteTreeQuietly(textureTemp);
            deleteTreeQuietly(animationTemp);
            deleteTreeQuietly(soundsTemp);
        }
    }

    private static boolean equalBytes(Map<String, byte[]> first, Map<String, byte[]> second) {
        if (!first.keySet().equals(second.keySet())) {
            return false;
        }
        for (String key : first.keySet()) {
            if (!Arrays.equals(first.get(key), second.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static void prepareTarget(Path root, Path target) throws IOException {
        prepareParent(root, target);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedTarget)
                || (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(normalizedTarget, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Upload target is not a regular file");
        }
    }

    private static void prepareDirectoryTarget(Path root, Path target) throws IOException {
        prepareParent(root, target);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedTarget)
                || (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(normalizedTarget, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Sound upload target is not a regular directory");
        }
    }

    private static void prepareParent(Path root, Path target) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IOException("Upload path leaves config/modelprops");
        }
        Files.createDirectories(normalizedRoot);
        Path current = normalizedRoot;
        Path relativeParent = normalizedRoot.relativize(normalizedTarget.getParent());
        for (Path segment : relativeParent) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Upload directory contains a symbolic link or non-directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
        Path realRoot = normalizedRoot.toRealPath();
        if (!normalizedTarget.getParent().toRealPath().startsWith(realRoot)) {
            throw new IOException("Upload path resolves outside config/modelprops");
        }
    }

    private static void writeDurably(Path target, byte[] bytes) throws IOException {
        Files.deleteIfExists(target);
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void move(Path source, Path target, boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException ignored) {
            if (replace) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        }
    }

    private static Path sibling(Path target, String suffix) {
        return target.resolveSibling(target.getFileName().toString() + suffix);
    }

    private static void deleteTreeQuietly(Path path) {
        try {
            deleteTree(path);
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static Path modelPath(Path root, String idValue) throws IOException {
        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            throw new IOException("Invalid model ID");
        }
        return root.resolve("models").resolve(id.getNamespace()).resolve(id.getPath() + ".json").normalize();
    }

    private static Path texturePath(Path root, String idValue) throws IOException {
        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            throw new IOException("Invalid model ID");
        }
        return root.resolve("textures/modelprops/uploaded").resolve(id.getNamespace())
                .resolve(id.getPath() + ".png").normalize();
    }

    private static Path animationPath(Path root, String idValue) throws IOException {
        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            throw new IOException("Invalid model ID");
        }
        return root.resolve("animations").resolve(id.getNamespace())
                .resolve(id.getPath() + ".json").normalize();
    }

    private static Path soundsPath(Path root, String idValue) throws IOException {
        Identifier id = Identifier.tryParse(idValue);
        if (id == null) {
            throw new IOException("Invalid model ID");
        }
        return root.resolve("sounds").resolve(id.getNamespace())
                .resolve(ModelPropsAssetProtocol.encodeSoundPath(id.getPath())).normalize();
    }

    private static boolean safeSegment(String segment) {
        if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.endsWith(".")) {
            return false;
        }
        String base = segment.toLowerCase(Locale.ROOT).split("\\.", 2)[0];
        return !WINDOWS_RESERVED.contains(base);
    }

    public record InstallResult(String id, boolean replacedExistingUpload) {
    }

    private record ChosenId(String id, boolean overwrite) {
    }
}
