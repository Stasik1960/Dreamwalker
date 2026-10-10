package ru.modelprops.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import ru.modelprops.client.upload.AnimationBundleBuilder;
import ru.modelprops.client.upload.ClientUploadManager;
import ru.modelprops.net.ModelPropsAssetProtocol;
import ru.modelprops.net.ModelPropsNetworking;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** One F7 workflow for a model, texture, CPM-style animation and action sounds. */
public final class ModelUploadScreen extends Screen {
    private static final List<String> SOUND_ACTIONS = List.of("equip", "swing", "use", "attack", "custom");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    );

    private Path modelPath;
    private Path texturePath;
    private List<Path> animationPaths = List.of();
    private AnimationBundleBuilder.Bundle animationBundle = AnimationBundleBuilder.empty();
    private final Map<String, Path> soundPaths = new LinkedHashMap<>();
    private final Map<String, Long> soundSizes = new LinkedHashMap<>();
    private long modelSize = -1L;
    private long textureSize = -1L;
    private String modelId = "modelprops:uploaded_model";
    private String selectedSoundTarget = SOUND_ACTIONS.get(0);
    private Tab tab = Tab.MODEL;

    private TextFieldWidget idField;
    private ButtonWidget uploadButton;
    private ButtonWidget clearAnimationButton;
    private ButtonWidget clearSoundButton;
    private final List<ButtonWidget> selectionButtons = new ArrayList<>();
    private boolean preparing;
    private Text status = Text.translatable("screen.modelprops.upload.hint");
    private int statusColor = 0xA0A0A0;
    private int panelX;
    private int panelWidth;
    private int top;

    public ModelUploadScreen() {
        super(Text.translatable("screen.modelprops.upload.title"));
    }

    @Override
    protected void init() {
        // clearAndInit() removes widgets from the screen, but not our Java references.
        // Reset them so an inactive tab cannot keep ticking or editing a detached field.
        idField = null;
        uploadButton = null;
        clearAnimationButton = null;
        clearSoundButton = null;
        selectionButtons.clear();
        panelWidth = Math.min(440, width - 24);
        panelX = (width - panelWidth) / 2;
        top = Math.max(5, height / 2 - 115);
        int half = (panelWidth - 4) / 2;

        ButtonWidget modelTab = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.upload.tab_model"), button -> switchTab(Tab.MODEL))
                .dimensions(panelX, top + 18, half, 20).build());
        modelTab.active = tab != Tab.MODEL;
        ButtonWidget mediaTab = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.upload.tab_media"), button -> switchTab(Tab.MEDIA))
                .dimensions(panelX + half + 4, top + 18, panelWidth - half - 4, 20).build());
        mediaTab.active = tab != Tab.MEDIA;

        if (tab == Tab.MODEL) {
            initModelTab();
        } else {
            initMediaTab();
        }

        uploadButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.upload.submit_all"), button -> upload())
                .dimensions(panelX, top + 151, panelWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.modelprops.upload.close"),
                        button -> close())
                .dimensions(panelX + (panelWidth - 120) / 2, top + 177, 120, 20).build());
        refreshValidationStatus();
        updateButtons();
    }

    private void initModelTab() {
        int buttonWidth = Math.min(170, panelWidth / 2);
        int buttonX = panelX + panelWidth - buttonWidth;
        addSelectionButton(ButtonWidget.builder(Text.translatable("screen.modelprops.upload.choose_json"),
                        button -> chooseModel())
                .dimensions(buttonX, top + 48, buttonWidth, 20).build());
        addSelectionButton(ButtonWidget.builder(Text.translatable("screen.modelprops.upload.choose_png"),
                        button -> chooseTexture())
                .dimensions(buttonX, top + 77, buttonWidth, 20).build());

        idField = addDrawableChild(new TextFieldWidget(textRenderer, panelX, top + 119,
                panelWidth, 20, Text.translatable("screen.modelprops.upload.id")));
        idField.setMaxLength(128);
        idField.setText(modelId);
        idField.setChangedListener(value -> {
            modelId = value;
            refreshValidationStatus();
            updateButtons();
        });
    }

    private void initMediaTab() {
        int small = Math.min(92, Math.max(66, panelWidth / 4));
        int choose = Math.min(160, panelWidth - small - 4);
        addSelectionButton(ButtonWidget.builder(Text.translatable("screen.modelprops.upload.choose_animation"),
                        button -> chooseAnimation())
                .dimensions(panelX + panelWidth - choose - small - 4, top + 48, choose, 20).build());
        clearAnimationButton = addSelectionButton(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.upload.clear"), button -> clearAnimation())
                .dimensions(panelX + panelWidth - small, top + 48, small, 20).build());

        int actionWidth = Math.max(105, panelWidth / 3);
        addSelectionButton(ButtonWidget.builder(soundTargetButtonText(), button -> cycleSoundTarget())
                .dimensions(panelX, top + 89, actionWidth, 20).build());
        int remaining = panelWidth - actionWidth - 8;
        int clearWidth = Math.min(74, Math.max(56, remaining / 3));
        addSelectionButton(ButtonWidget.builder(Text.translatable("screen.modelprops.upload.choose_ogg"),
                        button -> chooseSound())
                .dimensions(panelX + actionWidth + 4, top + 89,
                        remaining - clearWidth, 20).build());
        clearSoundButton = addSelectionButton(ButtonWidget.builder(
                        Text.translatable("screen.modelprops.upload.clear"), button -> clearSound())
                .dimensions(panelX + panelWidth - clearWidth, top + 89, clearWidth, 20).build());
    }

    private ButtonWidget addSelectionButton(ButtonWidget button) {
        selectionButtons.add(button);
        return addDrawableChild(button);
    }

    private void switchTab(Tab next) {
        if (tab != next) {
            tab = next;
            clearAndInit();
        }
    }

    private void chooseModel() {
        if (selectionLocked()) {
            return;
        }
        String selected = openFileDialog(Text.translatable("screen.modelprops.upload.dialog_json").getString(),
                modelPath, "*.json", "JSON (*.json)");
        if (selected == null) {
            return;
        }
        Path path = Path.of(selected).toAbsolutePath().normalize();
        if (!hasExtension(path, ".json")) {
            setError(Text.translatable("screen.modelprops.upload.error_json_extension"));
            return;
        }
        modelPath = path;
        modelSize = fileSize(path);
        modelId = defaultId(path);
        if (idField != null) {
            idField.setText(modelId);
        }
        refreshValidationStatus();
        updateButtons();
    }

    private void chooseTexture() {
        if (selectionLocked()) {
            return;
        }
        String selected = openFileDialog(Text.translatable("screen.modelprops.upload.dialog_png").getString(),
                texturePath, "*.png", "PNG (*.png)");
        if (selected != null) {
            Path path = Path.of(selected).toAbsolutePath().normalize();
            if (!hasExtension(path, ".png")) {
                setError(Text.translatable("screen.modelprops.upload.error_png_extension"));
                return;
            }
            texturePath = path;
            textureSize = fileSize(path);
            refreshValidationStatus();
            updateButtons();
        }
    }

    private void chooseAnimation() {
        if (selectionLocked()) {
            return;
        }
        List<Path> selected = openFileDialogs(
                Text.translatable("screen.modelprops.upload.dialog_animation").getString(),
                animationPaths.isEmpty() ? null : animationPaths.get(animationPaths.size() - 1),
                "*.json", "Animation JSON (*.json)");
        if (!selected.isEmpty()) {
            addAnimationPaths(selected, List.of());
        }
    }

    private void clearAnimation() {
        if (selectionLocked()) {
            return;
        }
        animationPaths = List.of();
        animationBundle = AnimationBundleBuilder.empty();
        soundPaths.keySet().removeIf(key -> !SOUND_ACTIONS.contains(key));
        soundSizes.keySet().removeIf(key -> !SOUND_ACTIONS.contains(key));
        if (!soundTargets().contains(selectedSoundTarget)) {
            selectedSoundTarget = SOUND_ACTIONS.get(0);
        }
        clearAndInit();
    }

    private void chooseSound() {
        if (selectionLocked()) {
            return;
        }
        String target = currentSoundTarget();
        Text targetName = soundTargetName(target);
        String selected = openFileDialog(Text.translatable("screen.modelprops.upload.dialog_ogg", targetName).getString(),
                soundPaths.get(target), "*.ogg", "Ogg Vorbis (*.ogg)");
        if (selected != null) {
            Path path = Path.of(selected).toAbsolutePath().normalize();
            if (!hasExtension(path, ".ogg")) {
                setError(Text.translatable("screen.modelprops.upload.error_ogg_extension"));
                return;
            }
            soundPaths.put(target, path);
            soundSizes.put(target, fileSize(path));
            clearAndInit();
        }
    }

    private void clearSound() {
        if (selectionLocked()) {
            return;
        }
        String target = currentSoundTarget();
        soundPaths.remove(target);
        soundSizes.remove(target);
        clearAndInit();
    }

    private void cycleSoundTarget() {
        if (selectionLocked()) {
            return;
        }
        List<String> targets = soundTargets();
        int current = targets.indexOf(selectedSoundTarget);
        selectedSoundTarget = targets.get((current + 1) % targets.size());
        clearAndInit();
    }

    private Text soundTargetButtonText() {
        return Text.translatable("screen.modelprops.upload.sound_target", soundTargetName(currentSoundTarget()));
    }

    private String currentSoundTarget() {
        if (!soundTargets().contains(selectedSoundTarget)) {
            selectedSoundTarget = SOUND_ACTIONS.get(0);
        }
        return selectedSoundTarget;
    }

    private List<String> soundTargets() {
        LinkedHashSet<String> targets = new LinkedHashSet<>(SOUND_ACTIONS);
        animationBundle.clipNames().stream()
                .filter(ModelPropsAssetProtocol::validSoundKey)
                .forEach(targets::add);
        return List.copyOf(targets);
    }

    private static Text soundTargetName(String target) {
        return SOUND_ACTIONS.contains(target)
                ? Text.translatable("screen.modelprops.upload.action_name." + target)
                : Text.literal(target);
    }

    private static String openFileDialog(String title, Path current, String pattern, String description) {
        String start = current != null && current.getParent() != null ? current.getParent().toString() : "";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.pointers(stack.UTF8(pattern));
            return TinyFileDialogs.tinyfd_openFileDialog(title, start, filters, description, false);
        }
    }

    private static List<Path> openFileDialogs(String title, Path current, String pattern, String description) {
        String start = current != null && current.getParent() != null ? current.getParent().toString() : "";
        String selected;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.pointers(stack.UTF8(pattern));
            selected = TinyFileDialogs.tinyfd_openFileDialog(title, start, filters, description, true);
        }
        if (selected == null || selected.isBlank()) {
            return List.of();
        }
        return Arrays.stream(selected.split("\\|", -1))
                .filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .toList();
    }

    private void addAnimationPaths(List<Path> additions, List<Path> soundsAfter) {
        LinkedHashSet<Path> combined = new LinkedHashSet<>(animationPaths);
        for (Path path : additions) {
            Path normalized = path.toAbsolutePath().normalize();
            if (!hasExtension(normalized, ".json")) {
                setError(Text.translatable("screen.modelprops.upload.error_json_extension"));
                return;
            }
            combined.add(normalized);
        }
        if (combined.size() > AnimationBundleBuilder.MAX_FILES) {
            setError(Text.translatable("screen.modelprops.upload.error_animation_count",
                    AnimationBundleBuilder.MAX_FILES));
            return;
        }
        MinecraftClient game = client;
        if (game == null) {
            setError(Text.translatable("screen.modelprops.upload.error_connection"));
            return;
        }
        List<Path> selected = List.copyOf(combined);
        preparing = true;
        setInfo(Text.translatable("screen.modelprops.upload.reading_animations"));
        updateButtons();
        CompletableFuture.supplyAsync(() -> readAnimationBundle(selected), Util.getIoWorkerExecutor())
                .whenComplete((bundle, throwable) -> game.execute(() -> {
                    preparing = false;
                    if (throwable != null) {
                        setError(Text.translatable("message.modelprops.upload.failed", rootMessage(throwable)));
                        updateButtons();
                        return;
                    }
                    animationPaths = selected;
                    animationBundle = bundle;
                    if (!soundTargets().contains(selectedSoundTarget)) {
                        selectedSoundTarget = SOUND_ACTIONS.get(0);
                    }
                    boolean allSoundsAssigned = assignDroppedSounds(soundsAfter);
                    clearAndInit();
                    if (!allSoundsAssigned) {
                        setError(Text.translatable("screen.modelprops.upload.error_sound_filename"));
                    }
                }));
    }

    private static AnimationBundleBuilder.Bundle readAnimationBundle(List<Path> paths) {
        try {
            ArrayList<AnimationBundleBuilder.Source> sources = new ArrayList<>(paths.size());
            for (Path path : paths) {
                byte[] bytes = readLimited(path, ModelPropsAssetProtocol.MAX_ANIMATION_BYTES,
                        "Animation JSON must be between 1 byte and 512 KiB");
                sources.add(new AnimationBundleBuilder.Source(path.getFileName().toString(), bytes));
            }
            return AnimationBundleBuilder.build(sources);
        } catch (IOException | RuntimeException exception) {
            throw new CompletionException(exception);
        }
    }

    private void upload() {
        if (modelPath == null || texturePath == null || preparing || ClientUploadManager.isBusy()) {
            return;
        }
        if (!ClientModelCatalog.INSTANCE.canEdit()) {
            setError(Text.translatable("screen.modelprops.upload.no_permission"));
            return;
        }
        if (!validId(modelId)) {
            setError(Text.translatable("screen.modelprops.upload.error_id"));
            return;
        }
        MinecraftClient game = client;
        if (game == null || game.getNetworkHandler() == null) {
            setError(Text.translatable("screen.modelprops.upload.error_connection"));
            return;
        }

        Path selectedModel = modelPath;
        Path selectedTexture = texturePath;
        List<Path> selectedAnimations = List.copyOf(animationPaths);
        Map<String, Path> selectedSounds = Map.copyOf(soundPaths);
        String selectedId = modelId;
        preparing = true;
        setInfo(Text.translatable("screen.modelprops.upload.reading"));
        updateButtons();

        CompletableFuture.supplyAsync(
                        () -> readFiles(selectedModel, selectedTexture, selectedAnimations, selectedSounds),
                        Util.getIoWorkerExecutor())
                .whenComplete((files, throwable) -> game.execute(() -> {
                    preparing = false;
                    if (throwable != null) {
                        setError(Text.translatable("message.modelprops.upload.failed", rootMessage(throwable)));
                        updateButtons();
                        return;
                    }
                    try {
                        ClientUploadManager.start(selectedId, files.json(), files.png(),
                                files.animation(), files.sounds());
                        setInfo(ClientUploadManager.statusText());
                    } catch (RuntimeException exception) {
                        setError(Text.translatable("message.modelprops.upload.failed", rootMessage(exception)));
                    }
                    updateButtons();
                }));
    }

    private static SelectedFiles readFiles(Path model, Path texture, List<Path> animations,
                                           Map<String, Path> sounds) {
        try {
            byte[] json = readLimited(model, ModelPropsNetworking.MAX_UPLOAD_JSON_BYTES,
                    "JSON must be between 1 byte and 256 KiB");
            byte[] png = readLimited(texture, ModelPropsNetworking.MAX_UPLOAD_PNG_BYTES,
                    "PNG must be between 1 byte and 4 MiB");
            byte[] animationBytes = readAnimationBundle(animations).jsonBytes();
            LinkedHashMap<String, byte[]> soundBytes = new LinkedHashMap<>();
            long total = 0L;
            if (sounds.size() > ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL) {
                throw new IOException("A model can contain at most 32 sounds");
            }
            for (Map.Entry<String, Path> entry : sounds.entrySet()) {
                byte[] bytes = readLimited(entry.getValue(), ModelPropsAssetProtocol.MAX_SOUND_BYTES,
                        "Each OGG must be between 1 byte and 2 MiB");
                total += bytes.length;
                if (total > ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES) {
                    throw new IOException("Action sounds are larger than 8 MiB in total");
                }
                soundBytes.put(entry.getKey(), bytes);
            }
            return new SelectedFiles(json, png, animationBytes, Map.copyOf(soundBytes));
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private static byte[] readLimited(Path path, int maximumBytes, String sizeError) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("One of the selected files no longer exists");
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maximumBytes + 1);
            if (bytes.length < 1 || bytes.length > maximumBytes) {
                throw new IOException(sizeError);
            }
            return bytes;
        }
    }

    public void acceptUploadResult(boolean success, Text result) {
        status = result;
        statusColor = success ? 0x55FF55 : 0xFF5555;
        updateButtons();
    }

    @Override
    public void tick() {
        if (idField != null) {
            idField.tick();
        }
        if (ClientUploadManager.isBusy()) {
            setInfo(ClientUploadManager.statusText());
        }
        updateButtons();
    }

    @Override
    public void filesDragged(List<Path> paths) {
        if (selectionLocked()) {
            return;
        }
        boolean changedModel = false;
        ArrayList<Path> addedAnimations = new ArrayList<>();
        ArrayList<Path> addedSounds = new ArrayList<>();
        for (Path raw : paths) {
            Path path = raw.toAbsolutePath().normalize();
            if (hasExtension(path, ".png")) {
                texturePath = path;
                textureSize = fileSize(path);
            } else if (hasExtension(path, ".ogg")) {
                addedSounds.add(path);
            } else if (hasExtension(path, ".json")) {
                if (tab == Tab.MEDIA || looksLikeAnimation(path)) {
                    addedAnimations.add(path);
                } else {
                    modelPath = path;
                    modelSize = fileSize(path);
                    changedModel = true;
                }
            }
        }
        if (changedModel) {
            modelId = defaultId(modelPath);
            if (idField != null) {
                idField.setText(modelId);
            }
        }
        if (!addedAnimations.isEmpty()) {
            addAnimationPaths(addedAnimations, addedSounds);
        } else {
            boolean allSoundsAssigned = assignDroppedSounds(addedSounds);
            clearAndInit();
            if (!allSoundsAssigned) {
                setError(Text.translatable("screen.modelprops.upload.error_sound_filename"));
            }
        }
    }

    private boolean assignDroppedSounds(List<Path> paths) {
        List<String> targets = soundTargets();
        Set<String> assignedThisBatch = new java.util.HashSet<>();
        boolean allAssigned = true;
        for (Path path : paths) {
            if (!hasExtension(path, ".ogg")) {
                continue;
            }
            String filename = path.getFileName().toString();
            String base = filename.substring(0, filename.length() - 4);
            String target = targets.stream().filter(base::equals).findFirst().orElse(null);
            if (target == null) {
                List<String> caseInsensitive = targets.stream()
                        .filter(value -> value.equalsIgnoreCase(base)).toList();
                target = caseInsensitive.size() == 1 ? caseInsensitive.get(0) : null;
            }
            if (target == null && paths.size() == 1) {
                target = currentSoundTarget();
            }
            if (target == null || !assignedThisBatch.add(target)) {
                allAssigned = false;
                continue;
            }
            soundPaths.put(target, path);
            soundSizes.put(target, fileSize(path));
        }
        return allAssigned;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top + 2, 0xFFFFFF);
        if (tab == Tab.MODEL) {
            renderModelTab(context);
        } else {
            renderMediaTab(context);
        }
        if (ClientUploadManager.isBusy()) {
            int barY = top + 202;
            context.fill(panelX, barY, panelX + panelWidth, barY + 4, 0xFF303030);
            context.fill(panelX, barY,
                    panelX + (int) Math.round(panelWidth * ClientUploadManager.progress()), barY + 4, 0xFF55AAFF);
        }
        context.drawCenteredTextWithShadow(textRenderer, trim(status, panelWidth),
                width / 2, top + 211, statusColor);
    }

    private void renderModelTab(DrawContext context) {
        int fileTextWidth = panelWidth - Math.min(170, panelWidth / 2) - 8;
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.modelprops.upload.json"),
                panelX, top + 43, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, trim(fileDescription(modelPath, modelSize), fileTextWidth),
                panelX, top + 58, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.modelprops.upload.png"),
                panelX, top + 72, 0xA0A0A0);
        context.drawTextWithShadow(textRenderer, trim(fileDescription(texturePath, textureSize), fileTextWidth),
                panelX, top + 87, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.modelprops.upload.id"),
                panelX, top + 108, 0xA0A0A0);
        context.drawCenteredTextWithShadow(textRenderer,
                trim(Text.translatable("screen.modelprops.upload.texture_note"), panelWidth),
                width / 2, top + 142, 0xA0A0A0);
    }

    private void renderMediaTab(DrawContext context) {
        int fileTextWidth = Math.max(70, panelWidth - Math.min(252, panelWidth * 2 / 3) - 8);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.modelprops.upload.animations"),
                panelX, top + 43, 0xA0A0A0);
        Text animationSummary = animationPaths.isEmpty()
                ? Text.translatable("screen.modelprops.upload.not_selected")
                : Text.translatable("screen.modelprops.upload.animation_summary",
                animationBundle.fileCount(), animationBundle.clipCount());
        context.drawTextWithShadow(textRenderer, trim(animationSummary, fileTextWidth),
                panelX, top + 58, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.modelprops.upload.action_sound"),
                panelX, top + 78, 0xA0A0A0);
        Path sound = soundPaths.get(currentSoundTarget());
        long size = soundSizes.getOrDefault(currentSoundTarget(), -1L);
        context.drawTextWithShadow(textRenderer,
                trim(fileDescription(sound, size), panelWidth), panelX, top + 114, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                trim(Text.translatable("screen.modelprops.upload.media_note", soundPaths.size()), panelWidth),
                width / 2, top + 136, 0xA0A0A0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(null);
        }
    }

    private void updateButtons() {
        boolean selectionEnabled = !selectionLocked();
        for (ButtonWidget button : selectionButtons) {
            button.active = selectionEnabled;
        }
        if (clearAnimationButton != null) {
            clearAnimationButton.active = selectionEnabled && !animationPaths.isEmpty();
        }
        if (clearSoundButton != null) {
            clearSoundButton.active = selectionEnabled && soundPaths.containsKey(currentSoundTarget());
        }
        if (idField != null) {
            idField.setEditable(selectionEnabled);
        }
        if (uploadButton != null) {
            uploadButton.active = modelPath != null && texturePath != null && validId(modelId)
                    && validSize(modelSize, ModelPropsNetworking.MAX_UPLOAD_JSON_BYTES)
                    && validSize(textureSize, ModelPropsNetworking.MAX_UPLOAD_PNG_BYTES)
                    && animationBundle.byteLength() <= ModelPropsAssetProtocol.MAX_ANIMATION_BYTES
                    && validSoundSizes() && !preparing && !ClientUploadManager.isBusy()
                    && client != null && client.getNetworkHandler() != null
                    && ClientModelCatalog.INSTANCE.canEdit()
                    && supportsSelectedUpload();
        }
    }

    private boolean validSoundSizes() {
        if (soundSizes.size() > ModelPropsAssetProtocol.MAX_SOUNDS_PER_MODEL
                || !soundSizes.keySet().equals(soundPaths.keySet())) {
            return false;
        }
        long total = 0L;
        Set<String> foldedKeys = new java.util.HashSet<>();
        for (Map.Entry<String, Long> entry : soundSizes.entrySet()) {
            String key = entry.getKey();
            if (!ModelPropsAssetProtocol.validSoundKey(key)
                    || (!SOUND_ACTIONS.contains(key) && !animationBundle.clipNames().contains(key))
                    || !foldedKeys.add(key.toLowerCase(Locale.ROOT))) {
                return false;
            }
            long size = entry.getValue();
            if (!validSize(size, ModelPropsAssetProtocol.MAX_SOUND_BYTES)) {
                return false;
            }
            total += size;
        }
        return total <= ModelPropsAssetProtocol.MAX_SOUND_TOTAL_BYTES;
    }

    private boolean requiresBundleUpload() {
        return soundPaths.size() > ModelPropsAssetProtocol.MAX_LEGACY_SOUNDS_PER_MODEL
                || soundPaths.keySet().stream().anyMatch(key -> !ModelPropsAssetProtocol.validAction(key));
    }

    private boolean supportsSelectedUpload() {
        return requiresBundleUpload()
                ? ClientUploadManager.supportsBundleUpload()
                : ClientUploadManager.supportsExtendedUpload();
    }

    private void setInfo(Text message) {
        status = message;
        statusColor = 0xA0A0A0;
    }

    private void setError(Text message) {
        status = message;
        statusColor = 0xFF5555;
    }

    private void refreshValidationStatus() {
        if (preparing || ClientUploadManager.isBusy()) {
            return;
        }
        if (modelPath != null && !validSize(modelSize, ModelPropsNetworking.MAX_UPLOAD_JSON_BYTES)) {
            setError(Text.translatable("screen.modelprops.upload.error_json_size"));
        } else if (texturePath != null && !validSize(textureSize, ModelPropsNetworking.MAX_UPLOAD_PNG_BYTES)) {
            setError(Text.translatable("screen.modelprops.upload.error_png_size"));
        } else if (!animationPaths.isEmpty()
                && (animationBundle.clipCount() > ru.modelprops.animation.AnimationParser.MAX_CLIPS
                || animationBundle.byteLength() > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES)) {
            setError(Text.translatable("screen.modelprops.upload.error_animation_size"));
        } else if (!validSoundSizes()) {
            setError(Text.translatable("screen.modelprops.upload.error_ogg_size"));
        } else if (!validId(modelId)) {
            setError(Text.translatable("screen.modelprops.upload.error_id"));
        } else if (client == null || client.getNetworkHandler() == null) {
            setError(Text.translatable("screen.modelprops.upload.error_connection"));
        } else if (!ClientModelCatalog.INSTANCE.canEdit()) {
            setError(Text.translatable("screen.modelprops.upload.no_permission"));
        } else if (!supportsSelectedUpload()) {
            setError(Text.translatable(requiresBundleUpload()
                    ? "screen.modelprops.upload.server_unsupported_v3"
                    : "screen.modelprops.upload.server_unsupported_v2"));
        } else {
            setInfo(modelPath != null && texturePath != null
                    ? Text.translatable("screen.modelprops.upload.files_ready_all", soundPaths.size())
                    : Text.translatable("screen.modelprops.upload.hint"));
        }
    }

    private static boolean validSize(long size, long maximum) {
        return size > 0L && size <= maximum;
    }

    private static boolean validId(String value) {
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

    private static boolean safeSegment(String segment) {
        if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.endsWith(".")) {
            return false;
        }
        String base = segment.toLowerCase(Locale.ROOT).split("\\.", 2)[0];
        return !WINDOWS_RESERVED.contains(base);
    }

    private static boolean hasExtension(Path path, String extension) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static boolean looksLikeAnimation(Path path) {
        try {
            long size = Files.size(path);
            if (size < 1L || size > ModelPropsAssetProtocol.MAX_ANIMATION_BYTES) {
                return false;
            }
            JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            return root.isJsonObject()
                    && root.getAsJsonObject().has("format_version")
                    && root.getAsJsonObject().has("clips");
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private boolean selectionLocked() {
        return preparing || ClientUploadManager.isBusy();
    }

    private static String defaultId(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+|[._-]+$", "");
        if (slug.isBlank()) {
            slug = "uploaded_model";
        }
        if (!safeSegment(slug)) {
            slug = "model_" + slug;
        }
        int maximumPathLength = 128 - "modelprops:".length();
        if (slug.length() > maximumPathLength) {
            slug = slug.substring(0, maximumPathLength).replaceAll("[._-]+$", "");
        }
        return "modelprops:" + slug;
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return -1L;
        }
    }

    private static Text fileDescription(Path path, long size) {
        if (path == null) {
            return Text.translatable("screen.modelprops.upload.not_selected");
        }
        String name = path.getFileName().toString();
        if (name.length() > 34) {
            name = name.substring(0, 31) + "...";
        }
        if (size < 0L) {
            return Text.literal(name);
        }
        String formatted = size < 1024L ? size + " B"
                : size < 1024L * 1024L
                ? String.format(Locale.ROOT, "%.1f KiB", size / 1024.0)
                : String.format(Locale.ROOT, "%.1f MiB", size / (1024.0 * 1024.0));
        return Text.literal(name + " (" + formatted + ")");
    }

    private Text trim(Text value, int maximumWidth) {
        String text = value.getString();
        if (textRenderer.getWidth(text) <= maximumWidth) {
            return value;
        }
        String suffix = "...";
        return Text.literal(textRenderer.trimToWidth(text,
                Math.max(0, maximumWidth - textRenderer.getWidth(suffix))) + suffix);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current.getMessage() == null)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum Tab {
        MODEL,
        MEDIA
    }

    private record SelectedFiles(byte[] json, byte[] png, byte[] animation, Map<String, byte[]> sounds) {
    }
}
