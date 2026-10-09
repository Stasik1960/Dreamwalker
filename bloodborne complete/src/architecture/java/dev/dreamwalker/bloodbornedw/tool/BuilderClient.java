package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client-only context controls and bounded, cached editor graphics. Changes stay server-authoritative. */
public final class BuilderClient {
    private static final int MAX_BOXES = 512, MAX_CONNECTIONS = 64, MAX_PENDING = 4;
    private static final int MAX_REQUEST_BYTES = 32000;
    private static final double VISIBLE_DISTANCE_SQUARED = 96 * 96;
    private static final String CATEGORY = "DreamWalker — инструмент 90009";
    private static boolean initialized, attackPressed, usePressed, wasHeld, undoPressed, cancelPressed, geometry;
    private static long nextRequestId, lastAck;
    private static int ticks;
    private static double wheelRemainder;
    private static String wheelOperation = "";
    private static Object previousWorld;
    private static JsonObject latest = new JsonObject();
    private static final Map<Long, Long> pending = new LinkedHashMap<>();
    private static KeyBinding modeModifier, stepModifier, reverseModifier, undoKey, cancelKey;
    private static boolean eventModifierPressed;
    private static InputUtil.Key eventModifierBinding;
    private static List<String> conflictCache = List.of();
    private static String localMessage = "";
    private static long localMessageUntil;
    private static Visual selected, source, hover;
    private static List<Connection> connections = List.of();

    private record Visual(String key, String instance, String dimension, String name, String id,
                          Vec3d anchor, List<Box> selection, List<Box> collision, int entityId, boolean loaded) {}
    private record Connection(Visual target, String direction, String label, boolean loaded) {}
    private record WorldLabel(Vec3d point, String text, int color) {}

    private BuilderClient() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        // Read the actual bound keys, rather than KeyBinding.isPressed(): vanilla permits several
        // actions to share Ctrl/Shift and its key-to-binding lookup otherwise favours only one.
        modeModifier = binding("Действие колесом / модификатор отмены", GLFW.GLFW_KEY_LEFT_CONTROL);
        stepModifier = binding("Шаг высоты колесом", GLFW.GLFW_KEY_LEFT_ALT);
        reverseModifier = binding("Обратное действие", GLFW.GLFW_KEY_LEFT_SHIFT);
        undoKey = binding("Отмена (с модификатором действия)", GLFW.GLFW_KEY_Z);
        cancelKey = binding("Снять рабочий выбор / источник", GLFW.GLFW_KEY_X);
        ClientPlayNetworking.registerGlobalReceiver(BuilderServer.VIEW, (client, handler, buf, sender) -> {
            int bytes = buf.readableBytes();
            final String raw;
            try { raw = buf.readString(BuilderServer.MAX_VIEW); }
            catch (RuntimeException malformed) { error("Malformed bounded menu packet", malformed); return; }
            DwDiagnostics.network("client", "receive", BuilderServer.VIEW.toString(), bytes);
            client.execute(() -> {
                try {
                    JsonObject incoming = JsonParser.parseString(raw).getAsJsonObject();
                    long ack = number(incoming, "ackRequestId", 0);
                    if (ack > 0 && ack < lastAck) return;
                    if (ack > 0) { lastAck = ack; pending.keySet().removeIf(id -> id <= ack); }
                    latest = incoming;
                    rebuildVisuals();
                    if (client.currentScreen instanceof BuilderScreen screen) screen.accept(latest);
                    else if (bool(latest, "openMenu")) client.setScreen(new BuilderScreen(latest));
                } catch (RuntimeException invalid) { error("Malformed server menu view", invalid); }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(BuilderClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            latest = new JsonObject(); selected = source = hover = null; connections = List.of();
            pending.clear(); lastAck = 0; wasHeld = false; wheelRemainder = 0;
            BuilderScreen.clearSessionDrafts();
        });
        WorldRenderEvents.AFTER_ENTITIES.register(BuilderClient::renderWorld);
        HudRenderCallback.EVENT.register(BuilderClient::renderHud);
    }

    private static KeyBinding binding(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    private static void tick(MinecraftClient client) {
        ticks++;
        if (client.world != previousWorld) {
            previousWorld = client.world;
            latest = new JsonObject(); selected = source = hover = null; connections = List.of();
            pending.clear(); lastAck = 0; wasHeld = false; wheelRemainder = 0;
        }
        if (client.player == null) return;
        if (!client.options.attackKey.isPressed()) attackPressed = false;
        if (!client.options.useKey.isPressed()) usePressed = false;
        boolean held = BuildingTool.isHeld(client.player), context = held && client.currentScreen == null;
        if (held && !wasHeld) {
            refreshConflicts(client);
            if (BuildingTool.mainHeld(client.player)) send(request("refresh"));
        }
        wasHeld = held;
        boolean undoDown = down(undoKey), cancelDown = down(cancelKey);
        if (context && BuildingTool.mainHeld(client.player)) {
            if (undoDown && !undoPressed && down(modeModifier)) send(request("undo"));
            if (cancelDown && !cancelPressed) send(request("cancel_selection"));
        }
        undoPressed = undoDown; cancelPressed = cancelDown;
        if (!context) { hover = null; wheelRemainder = 0; wheelOperation = ""; }
        else if ((ticks & 1) == 0) updateHover(client);
        if (!down(modeModifier) && !down(stepModifier)) { wheelRemainder = 0; wheelOperation = ""; }
        if (held && ticks % 100 == 0) refreshConflicts(client);
        // Selection remains persistent. No entire-world entity/ledger scan is performed here.
        if (held) { selected = liveRp(client, selected); source = liveRp(client, source); }
        pending.entrySet().removeIf(entry -> System.nanoTime() - entry.getValue() > 10_000_000_000L);
    }

    /** A screen can reserve an ID before recording its draft snapshot. IDs are monotonic, not UUIDs. */
    public static long reserveRequestId() { return ++nextRequestId; }

    public static long send(JsonObject request) {
        if (!ClientPlayNetworking.canSend(BuilderServer.REQUEST)) { notice("Нет соединения с инструментом на сервере."); return -1; }
        JsonObject q = request.deepCopy();
        if (!q.has("requestId")) q.addProperty("requestId", reserveRequestId());
        JsonObject target = object(latest, "target");
        // Explicit form expectations belong to that form's captured UUID/version and must survive
        // incoming view updates. Supplying a fresh expectation here would overwrite another editor.
        if (target.size() > 0) {
            if (!q.has("expectedKey")) q.addProperty("expectedKey", string(target, "key", ""));
            if (!q.has("expectedVersion") && target.has("version")) q.add("expectedVersion", target.get("version").deepCopy());
        }
        if (!q.has("expectedRuleId") && q.has("op") && q.get("op").getAsString().startsWith("rule_") && latest.has("rule")) q.addProperty("expectedRuleId", string(object(latest,"rule"),"id",""));
        if (!q.has("expectedFormVersion") && latest.has("formVersion")) q.add("expectedFormVersion", latest.get("formVersion").deepCopy());
        String json = q.toString();
        // CustomPayloadC2SPacket (1.20.1) rejects payloads above 32767 bytes, whereas
        // writeString's argument limits characters. Reserve three bytes for the UTF-8 length VarInt.
        if (json.length() > BuilderServer.MAX_REQUEST || json.getBytes(StandardCharsets.UTF_8).length + 3 > MAX_REQUEST_BYTES) {
            String message = "Форма не отправлена: слишком большой объём текста. Сократите команды; черновик сохранён.";
            notice(message);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) client.player.sendMessage(Text.literal(message), false);
            return -1;
        }
        long id = number(q, "requestId", 0);
        if (pending.size() >= MAX_PENDING) { notice("Сервер ещё обрабатывает действия. Дождитесь ответа."); return -1; }
        var buf = PacketByteBufs.create();
        buf.writeString(json, BuilderServer.MAX_REQUEST);
        pending.put(id, System.nanoTime());
        if (string(q, "op", "").equals("diagnostics") && string(q, "action", "").equals("export"))
            dev.dreamwalker.bloodbornedw.diagnostics.DwClientDiagnostics.exportCurrent();
        DwDiagnostics.network("client", "send", BuilderServer.REQUEST.toString(), buf.readableBytes());
        ClientPlayNetworking.send(BuilderServer.REQUEST, buf);
        return id;
    }

    public static JsonObject request(String op) { JsonObject q = new JsonObject(); q.addProperty("op", op); return q; }
    public static boolean awaitingResponse() { return !pending.isEmpty(); }
    public static boolean showGeometry() { return geometry; }
    public static void showGeometry(boolean value) { geometry = value; }

    /** One mouse press gives one edit, even in Creative or while waiting for the server. */
    public static boolean attack() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !BuildingTool.isHeld(client.player)) return false;
        if (!attackPressed) {
            attackPressed = true;
            if (!BuildingTool.mainHeld(client.player)) notice("Для редактирования перенесите 90009 в основную руку.");
            else { JsonObject q = request("click"); q.addProperty("reverse", down(reverseModifier)); send(q); }
        }
        return true;
    }

    public static boolean use() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !BuildingTool.isHeld(client.player)) return false;
        if (!usePressed) {
            usePressed = true;
            if (BuildingTool.mainHeld(client.player)) send(request("open"));
            else notice("Для настроек перенесите 90009 в основную руку.");
        }
        return true;
    }

    /** The vanilla pick binding remains remappable and keeps its normal behavior without the tool. */
    public static boolean pick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || !BuildingTool.isHeld(client.player)) return false;
        if (BuildingTool.mainHeld(client.player)) send(request("select_cycle"));
        else notice("Для выбора перенесите 90009 в основную руку.");
        return true;
    }

    /** Capture short taps from native callbacks; tick polling alone can miss a press between frames. */
    public static void keyboardEvent(long window, int key, int scanCode, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || modeModifier == null || action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
        if (modeModifier.matchesKey(key, scanCode)) rememberModifier(action);
        shortcutEvent(cancelKey.matchesKey(key, scanCode), undoKey.matchesKey(key, scanCode), action, modifiers);
    }

    /** The same event capture supports mouse-remapped shortcuts without changing vanilla mouse input. */
    public static void mouseEvent(long window, int button, int action, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || modeModifier == null || action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;
        if (modeModifier.matchesMouse(button)) rememberModifier(action);
        shortcutEvent(cancelKey.matchesMouse(button), undoKey.matchesMouse(button), action, modifiers);
    }

    private static void rememberModifier(int action) {
        eventModifierPressed = action == GLFW.GLFW_PRESS;
        eventModifierBinding = KeyBindingHelper.getBoundKeyOf(modeModifier);
    }

    private static boolean eventModifierDown(int modifiers) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(modeModifier);
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            int mask = switch (key.getCode()) {
                case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_MOD_CONTROL;
                case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_MOD_ALT;
                case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_MOD_SHIFT;
                case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> GLFW.GLFW_MOD_SUPER;
                default -> 0;
            };
            if (mask != 0) return (modifiers & mask) != 0;
        }
        // Ordered callback state also preserves a remapped ordinary-key modifier during a short tap.
        return eventModifierPressed && key.equals(eventModifierBinding) || down(modeModifier);
    }

    private static void shortcutEvent(boolean cancelMatches, boolean undoMatches, int action, int modifiers) {
        boolean previousCancel = cancelPressed, previousUndo = undoPressed;
        if (cancelMatches) cancelPressed = action == GLFW.GLFW_PRESS;
        if (undoMatches) undoPressed = action == GLFW.GLFW_PRESS;
        MinecraftClient client = MinecraftClient.getInstance();
        if (action != GLFW.GLFW_PRESS || client.currentScreen != null || !BuildingTool.mainHeld(client.player)) return;
        if (cancelMatches && !previousCancel) send(request("cancel_selection"));
        else if (undoMatches && !previousUndo && eventModifierDown(modifiers)) send(request("undo"));
    }

    public static boolean scroll(long window, double horizontal, double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle() || client.currentScreen != null || !BuildingTool.mainHeld(client.player)
                || vertical == 0 || !down(modeModifier) && !down(stepModifier)) return false;
        String operation = down(modeModifier) ? "cycle_mode" : "step_cycle";
        if (!operation.equals(wheelOperation)) { wheelRemainder = 0; wheelOperation = operation; }
        wheelRemainder += vertical;
        int delta = (int) wheelRemainder;
        if (delta != 0) {
            wheelRemainder -= delta;
            JsonObject q = request(operation);
            q.addProperty("delta", Math.max(-8, Math.min(8, delta))); send(q);
        }
        // Consume even partial trackpad movement, so an editor gesture never changes hotbar slots.
        return true;
    }

    private static boolean down(KeyBinding binding) {
        if (binding == null) return false;
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key.getCode() < 0) return false;
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) return GLFW.glfwGetMouseButton(handle, key.getCode()) == GLFW.GLFW_PRESS;
        if (key.getCategory() == InputUtil.Type.KEYSYM) {
            if (InputUtil.isKeyPressed(handle, key.getCode())) return true;
            // The default modifier means either side; a remapped ordinary key keeps its exact value.
            int opposite = switch (key.getCode()) {
                case GLFW.GLFW_KEY_LEFT_CONTROL -> GLFW.GLFW_KEY_RIGHT_CONTROL;
                case GLFW.GLFW_KEY_LEFT_ALT -> GLFW.GLFW_KEY_RIGHT_ALT;
                case GLFW.GLFW_KEY_LEFT_SHIFT -> GLFW.GLFW_KEY_RIGHT_SHIFT;
                default -> -1;
            };
            return opposite >= 0 && InputUtil.isKeyPressed(handle, opposite);
        }
        return binding.isPressed();
    }

    private static String keyName(KeyBinding binding) {
        if (binding == null) return "—";
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key.getCode() < 0) return "не назначено";
        if (key.getCategory() == InputUtil.Type.MOUSE) return switch (key.getCode()) {
            case 0 -> "ЛКМ"; case 1 -> "ПКМ"; case 2 -> "Средняя кнопка"; default -> "Мышь " + (key.getCode() + 1);
        };
        return switch (key.getCode()) {
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_SPACE -> "Пробел";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            default -> key.getLocalizedText().getString();
        };
    }
    public static List<String> controlHints() {
        MinecraftClient c = MinecraftClient.getInstance();
        return List.of(keyName(c.options.attackKey) + ": выбор / один шаг", keyName(reverseModifier) + " + " + keyName(c.options.attackKey) + ": обратный шаг",
                keyName(modeModifier) + " + колесо: доступное действие", keyName(stepModifier) + " + колесо: шаг высоты",
                keyName(c.options.useKey) + ": настройки", keyName(c.options.pickItemKey) + ": следующий объект под прицелом",
                keyName(modeModifier) + " + " + keyName(undoKey) + ": отмена изменения", keyName(cancelKey) + ": снять выбор и источник",
                "Переназначение: Настройки → Управление → «" + CATEGORY + "».",
                "ЛКМ / ПКМ / средняя кнопка: обычные настройки атаки, использования и выбора блока.",
                "Для обычного разрушения / использования уберите инструмент из обеих рук.");
    }
    public static List<String> conflicts() { refreshConflicts(MinecraftClient.getInstance()); return conflictCache; }
    private static void refreshConflicts(MinecraftClient client) {
        if (modeModifier == null) return;
        List<String> result = new ArrayList<>();
        for (KeyBinding own : List.of(modeModifier, stepModifier, reverseModifier, undoKey, cancelKey)) {
            InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(own);
            if (bound.getCode() < 0) continue;
            for (KeyBinding other : client.options.allKeys) if (other != own && bound.equals(KeyBindingHelper.getBoundKeyOf(other))) {
                if (result.size() >= 12) break;
                result.add(keyName(own) + ": «" + Text.translatable(own.getTranslationKey()).getString() + "» / «" + Text.translatable(other.getTranslationKey()).getString() + "»");
            }
        }
        conflictCache = List.copyOf(result);
    }

    private static void rebuildVisuals() {
        selected = visual(object(latest, "target")); source = visual(object(latest, "source"));
        List<Connection> result = new ArrayList<>();
        for (JsonElement e : array(latest, "connections")) {
            if (result.size() >= MAX_CONNECTIONS || !e.isJsonObject()) break;
            JsonObject connection = e.getAsJsonObject(); Visual target = visual(object(connection, "target"));
            if (target != null) result.add(new Connection(target, string(connection, "direction", "out"), string(connection, "label", "связь"), connection.has("loaded") ? bool(connection, "loaded") : target.loaded));
        }
        connections = List.copyOf(result);
    }

    private static Visual visual(JsonObject t) {
        if (t.size() == 0) return null;
        Vec3d anchor = point(array(t, "anchor"));
        if (anchor == null) anchor = point(array(t, "position"));
        if (anchor == null) anchor = parsePosition(string(t, "position", ""));
        if (anchor == null) return null;
        return new Visual(string(t, "key", ""), string(t, "instance", ""), string(t, "dimension", ""),
                string(t, "name", "Объект"), string(t, "id", "?????"), anchor, boxes(array(t, "selectionBoxes")),
                boxes(array(t, "collisionBoxes")), (int) number(t, "entityId", -1), !t.has("loaded") || bool(t, "loaded"));
    }

    private static Visual liveRp(MinecraftClient client, Visual visual) {
        if (visual == null || client.world == null || visual.entityId < 0) return visual;
        var entity = client.world.getEntityById(visual.entityId);
        if (!(entity instanceof RpObjectEntity rp) || !rp.getUuidAsString().equals(visual.instance)) return visual;
        return new Visual(visual.key, visual.instance, visual.dimension, visual.name, visual.id, rp.getPos(),
                limited(rp.selectionBoxes()), limited(rp.activePhysicalBoxes()), visual.entityId, true);
    }

    private static void updateHover(MinecraftClient client) {
        hover = null;
        if (client.world == null || client.player == null || client.crosshairTarget == null) return;
        String dimension = client.world.getRegistryKey().getValue().toString();
        if (client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof RpObjectEntity rp) {
            var entry = DebugCatalogue.entry(new Identifier("bloodborne_rp", rp.assetId()));
            hover = new Visual("", rp.getUuidAsString(), dimension, entry == null ? rp.assetId() : entry.name(),
                    entry == null ? "?????" : entry.temporaryId(), rp.getPos(), limited(rp.selectionBoxes()), List.of(), rp.getId(), true);
        } else if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) {
            BlockPos pos = hit.getBlockPos(); var owner = CompositeRuntime.targetReadOnly(client.world, pos, client.player);
            List<Box> selection = new ArrayList<>();
            var entry = owner == null ? DebugCatalogue.entry(client.world.getBlockState(pos)) : DebugCatalogue.entry(new Identifier(owner.registryId()));
            if (entry == null || entry.temporaryId().equals("90008") || entry.temporaryId().equals("90009")) return;
            if (owner != null) {
                for (var contribution : CompositeLedger.get(client.world).at(CompositeData.cell(pos))) if (contribution.owner().equals(owner))
                    for (Box box : CompositeRuntime.shape(contribution.shape().selection()).getBoundingBoxes()) {
                        if (selection.size() >= MAX_BOXES) break;
                        selection.add(box.offset(pos));
                    }
            } else selection.addAll(client.world.getBlockState(pos).getOutlineShape(client.world, pos).getBoundingBoxes().stream().limit(MAX_BOXES).map(box -> box.offset(pos)).toList());
            hover = new Visual("", owner == null ? "" : owner.instanceId().toString(), dimension, entry.name(), entry.temporaryId(),
                    owner == null ? Vec3d.ofCenter(pos) : Vec3d.ofCenter(CompositeData.pos(owner.root())), List.copyOf(selection), List.of(), -1, true);
        }
    }

    private static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !BuildingTool.isHeld(client.player) || context.consumers() == null || context.matrixStack() == null) return;
        String dimension = client.world.getRegistryKey().getValue().toString();
        Vec3d camera = context.camera().getPos(); var matrices = context.matrixStack();
        matrices.push(); matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        List<WorldLabel> labels = new ArrayList<>(MAX_CONNECTIONS + 8);
        if (visible(selected, dimension, camera)) {
            outline(matrices, lines, selected.selection, .95f, .73f, .22f);
            queueLabel(labels, selected.anchor.add(0, 1.25, 0), "Выбрано [" + selected.id + "]" + (geometry ? " · область выбора" : ""), 0xFFE3A0);
            if (geometry) {
                outline(matrices, lines, selected.collision, .3f, .92f, .45f);
                queueLabel(labels, selected.anchor.add(0, .95, 0), "Коллизия игрока (зелёная) · опора +", 0x94EBA4);
                if (bool(object(latest, "target"), "geometryTruncated"))
                    queueLabel(labels, selected.anchor.add(0, .65, 0), "Графика ограничена · физика объекта сохранена", 0xD5BA93);
                cross(matrices, lines, selected.anchor, .22, .3f, .92f, .45f);
                JsonObject target = object(latest, "target");
                if (target.has("yaw")) {
                    double angle = Math.toRadians(decimal(target, "yaw", 0));
                    Vec3d start = selected.anchor.add(0, .2, 0), end = start.add(-Math.sin(angle), 0, Math.cos(angle));
                    line(matrices, lines, start, end, .3f, .92f, .45f); arrow(matrices, lines, start, end, .3f, .92f, .45f);
                    queueLabel(labels, end.add(0, .4, 0), "Направление · Y " + string(target, "offset", "0"), 0x94EBA4);
                }
            }
        }
        if (visible(source, dimension, camera)) {
            outline(matrices, lines, source.selection, .66f, .4f, .95f);
            queueLabel(labels, source.anchor.add(0, 1.6, 0), "Источник [" + source.id + "]", 0xD7ACFF);
        }
        if (visible(hover, dimension, camera) && (selected == null || !same(hover, selected))) {
            outline(matrices, lines, hover.selection, .3f, .75f, 1f);
            queueLabel(labels, hover.anchor.add(0, 1, 0), "Прицел [" + hover.id + "]", 0xA3DDFF);
        }
        Visual origin = source == null ? selected : source;
        if (visible(origin, dimension, camera)) for (Connection connection : connections) {
            Visual destination = connection.target;
            if (!destination.dimension.equals(dimension)) continue;
            Vec3d a = origin.anchor.add(0, .45, 0), b = destination.anchor.add(0, .45, 0);
            Vec3d drawnEnd = b.subtract(a).lengthSquared() > VISIBLE_DISTANCE_SQUARED ? a.add(b.subtract(a).normalize().multiply(96)) : b;
            line(matrices, lines, a, drawnEnd, 1f, .46f, .23f);
            if (!connection.direction.equals("in")) arrow(matrices, lines, a, drawnEnd, 1f, .46f, .23f);
            if (connection.direction.equals("in") || connection.direction.equals("both")) arrow(matrices, lines, drawnEnd, a, 1f, .46f, .23f);
            if (visible(destination, dimension, camera)) {
                outline(matrices, lines, destination.selection, 1f, .46f, .23f);
                cross(matrices, lines, destination.anchor, .16, 1f, .46f, .23f);
                queueLabel(labels, destination.anchor.add(0, 1.3, 0), "Цель [" + destination.id + "] · " + connection.label
                        + (connection.loaded ? "" : " · последнее положение"), 0xFFBC99);
            }
        }
        if (source != null && hover != null && visible(source, dimension, camera) && visible(hover, dimension, camera) && !same(source, hover)
                && Set.of("LINK", "UNLINK", "LAMP_TARGET", "RULE_TARGET").contains(string(latest, "action", ""))) {
            line(matrices, lines, source.anchor.add(0, .65, 0), hover.anchor.add(0, .65, 0), .3f, .75f, 1f);
            arrow(matrices, lines, source.anchor.add(0, .65, 0), hover.anchor.add(0, .65, 0), .3f, .75f, 1f);
            queueLabel(labels, hover.anchor.add(0, 1.6, 0), "Предварительная цель · " + keyName(client.options.attackKey), 0xA3DDFF);
        }
        // Immediate consumers may share a fallback BufferBuilder. Text changes its vertex format,
        // so finish every outline/arrow before requesting any text layer; never reuse lines afterward.
        for (WorldLabel queued : labels) label(context, queued.point, queued.text, queued.color);
        matrices.pop();
    }

    private static boolean visible(Visual visual, String dimension, Vec3d camera) {
        return visual != null && visual.dimension.equals(dimension) && visual.anchor.squaredDistanceTo(camera) <= VISIBLE_DISTANCE_SQUARED;
    }
    private static boolean same(Visual a, Visual b) { return !a.instance.isBlank() && a.instance.equals(b.instance); }
    private static void outline(MatrixStack matrices, VertexConsumer lines, List<Box> boxes, float r, float g, float b) {
        for (Box box : boxes) WorldRenderer.drawBox(matrices, lines, box, r, g, b, 1);
    }
    private static void line(MatrixStack matrices, VertexConsumer lines, Vec3d a, Vec3d b, float r, float g, float blue) {
        Vec3d normal = b.subtract(a).normalize(); if (normal.lengthSquared() == 0) return;
        var entry = matrices.peek();
        lines.vertex(entry.getPositionMatrix(), (float) a.x, (float) a.y, (float) a.z).color(r, g, blue, 1).normal(entry.getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z).next();
        lines.vertex(entry.getPositionMatrix(), (float) b.x, (float) b.y, (float) b.z).color(r, g, blue, 1).normal(entry.getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z).next();
    }
    private static void arrow(MatrixStack matrices, VertexConsumer lines, Vec3d from, Vec3d to, float r, float g, float b) {
        Vec3d direction = to.subtract(from).normalize(); if (direction.lengthSquared() == 0) return;
        Vec3d tip = from.lerp(to, .72), back = tip.subtract(direction.multiply(.24));
        Vec3d side = direction.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(.12);
        if (side.lengthSquared() == 0) side = new Vec3d(.12, 0, 0);
        line(matrices, lines, tip, back.add(side), r, g, b); line(matrices, lines, tip, back.subtract(side), r, g, b);
    }
    private static void cross(MatrixStack matrices, VertexConsumer lines, Vec3d point, double size, float r, float g, float b) {
        line(matrices, lines, point.add(-size, 0, 0), point.add(size, 0, 0), r, g, b);
        line(matrices, lines, point.add(0, -size, 0), point.add(0, size, 0), r, g, b);
        line(matrices, lines, point.add(0, 0, -size), point.add(0, 0, size), r, g, b);
    }
    private static void queueLabel(List<WorldLabel> labels, Vec3d point, String text, int color) {
        if (labels.size() < MAX_CONNECTIONS + 8) labels.add(new WorldLabel(point, text, color));
    }
    private static void label(WorldRenderContext context, Vec3d point, String text, int color) {
        var matrices = context.matrixStack(); MinecraftClient client = MinecraftClient.getInstance();
        String trimmed = client.textRenderer.trimToWidth(text, 250);
        matrices.push(); matrices.translate(point.x, point.y, point.z); matrices.multiply(context.camera().getRotation()); matrices.scale(-.02f, -.02f, .02f);
        client.textRenderer.draw(Text.literal(trimmed), -client.textRenderer.getWidth(trimmed) / 2f, 0, color, false,
                matrices.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x80000000, 15728880);
        matrices.pop();
    }

    private static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || client.options.hudHidden || !BuildingTool.isHeld(client.player)) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        int panelWidth = Math.max(100, Math.min(500, width - 16)), left = (width - panelWidth) / 2, top = Math.max(8, height - 126);
        List<String> rows = new ArrayList<>();
        String attack = keyName(client.options.attackKey), use = keyName(client.options.useKey), pick = keyName(client.options.pickItemKey);
        rows.add(selected == null ? "90009 · Выбор: " + attack + " по объекту" : "Выбрано: " + selected.name + " [" + selected.id + "] · " + string(object(latest, "target"), "position", ""));
        String action = string(latest, "action", "SELECT"), label = string(latest, "actionLabel", "Выбор");
        rows.add("Операция: " + label + " · " + (action.equals("ROTATE") ? "угол " + string(object(latest, "target"), "rotationStep", "?") + "°" : "шаг Y " + stepLabel()));
        rows.add(attack + ": " + clickHint(action, false) + " · " + keyName(reverseModifier) + "+" + attack + ": " + clickHint(action, true));
        rows.add(keyName(modeModifier) + "+колесо: действие · " + keyName(stepModifier) + "+колесо: шаг · " + use + ": настройки");
        rows.add(keyName(modeModifier) + "+" + keyName(undoKey) + ": отмена · " + keyName(cancelKey) + ": снять выбор · " + pick + ": следующий");
        rows.add(source == null ? "Прицел: " + (hover == null ? "нет объекта" : hover.name + " [" + hover.id + "]")
                : "Источник: " + source.name + " [" + source.id + "] · целей " + connections.size() + " · прицел " + (hover == null ? "—" : "[" + hover.id + "]"));
        String status = System.nanoTime() < localMessageUntil ? localMessage : string(latest, "message", "");
        if (!BuildingTool.mainHeld(client.player)) status = "Основная рука нужна для правок. Уберите 90009 из обеих рук для разрушения.";
        else if (!pending.isEmpty()) status = System.nanoTime() - pending.values().iterator().next() > 5_000_000_000L ? "Ответ сервера задерживается; результат ещё не подтверждён." : "Ожидание подтверждения сервера…";
        else if (!conflictCache.isEmpty()) status += " · Клавиши совпадают: " + conflictCache.size() + " (Управление)";
        if (geometry && bool(object(latest, "target"), "geometryTruncated")) status += " · графика ограничена";
        rows.add(status);
        context.fill(left, top, left + panelWidth, top + rows.size() * 11 + 8, 0xC51A1717);
        for (int i = 0; i < rows.size(); i++) context.drawTextWithShadow(client.textRenderer, Text.literal(client.textRenderer.trimToWidth(rows.get(i), panelWidth - 12)),
                left + 6, top + 4 + i * 11, i == 0 ? 0xF0DAA9 : i == rows.size() - 1 ? 0xD5BA93 : 0xD8D4CD);
    }

    private static String clickHint(String action, boolean reverse) {
        if (selected == null && !Set.of("LINK", "UNLINK", "LAMP_SOURCE", "LAMP_TARGET", "RULE_SOURCE", "RULE_TARGET").contains(action)) return "выбрать";
        return switch (action) {
            case "ROTATE" -> reverse ? "назад" : "повернуть";
            case "UP" -> reverse ? "опустить" : "поднять";
            case "DOWN" -> reverse ? "поднять" : "опустить";
            case "MOUNT" -> reverse ? "предыдущий монтаж" : "следующий монтаж";
            case "LINK", "LAMP_TARGET", "RULE_TARGET" -> source == null ? "выбрать источник" : "добавить цель";
            case "UNLINK" -> source == null ? "выбрать источник" : "разорвать пару";
            case "PROFILE", "DOGS", "POSE" -> reverse ? "предыдущее состояние" : "сменить состояние";
            case "CONNECTIONS", "DIAGNOSTICS" -> "осмотреть";
            default -> "выбрать";
        };
    }
    private static String stepLabel() {
        double step = decimal(latest, "step", .125);
        if (step == .0625) return "1/16"; if (step == .125) return "1/8"; if (step == .25) return "1/4"; return "1";
    }

    private static List<Box> boxes(JsonArray values) {
        List<Box> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (result.size() >= MAX_BOXES) break;
            if (!value.isJsonArray() || value.getAsJsonArray().size() != 6) continue;
            JsonArray row = value.getAsJsonArray(); double[] coordinates = new double[6]; boolean valid = true;
            for (int i = 0; i < 6; i++) { coordinates[i] = row.get(i).getAsDouble(); valid &= Double.isFinite(coordinates[i]); }
            if (valid && coordinates[0] <= coordinates[3] && coordinates[1] <= coordinates[4] && coordinates[2] <= coordinates[5])
                result.add(new Box(coordinates[0], coordinates[1], coordinates[2], coordinates[3], coordinates[4], coordinates[5]));
        }
        return List.copyOf(result);
    }
    private static List<Box> limited(List<Box> boxes) { return boxes.size() <= MAX_BOXES ? boxes : List.copyOf(boxes.subList(0, MAX_BOXES)); }
    private static Vec3d point(JsonArray array) {
        if (array.size() != 3) return null;
        double x = array.get(0).getAsDouble(), y = array.get(1).getAsDouble(), z = array.get(2).getAsDouble();
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new Vec3d(x, y, z) : null;
    }
    private static Vec3d parsePosition(String value) {
        String[] coordinates = value.split(","); if (coordinates.length != 3) return null;
        try { return new Vec3d(Double.parseDouble(coordinates[0].trim()), Double.parseDouble(coordinates[1].trim()), Double.parseDouble(coordinates[2].trim())); }
        catch (NumberFormatException invalid) { return null; }
    }
    private static JsonObject object(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : new JsonObject(); }
    private static JsonArray array(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonArray() ? value.getAsJsonArray(key) : new JsonArray(); }
    private static String string(JsonObject value, String key, String fallback) { return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsString() : fallback; }
    private static long number(JsonObject value, String key, long fallback) { return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsLong() : fallback; }
    private static double decimal(JsonObject value, String key, double fallback) { return value.has(key) && value.get(key).isJsonPrimitive() ? value.get(key).getAsDouble() : fallback; }
    private static boolean bool(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonPrimitive() && value.get(key).getAsBoolean(); }
    private static void notice(String message) { localMessage = message; localMessageUntil = System.nanoTime() + 5_000_000_000L; }
    private static void error(String message, Throwable failure) { DwDiagnostics.error(null, "UNASSIGNED", "builder_client", null, "BUILDER_VIEW", message, failure); }
}
