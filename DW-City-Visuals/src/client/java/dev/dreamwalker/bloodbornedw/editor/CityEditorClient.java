package dev.dreamwalker.bloodbornedw.editor;

import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrames;
import dev.dreamwalker.bloodbornedw.composite.NativeVisuals;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.world.chunk.WorldChunk;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.EditorMeshUpdates;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

/** Client controls and chunk snapshot updates for the city facade editor. */
public final class CityEditorClient implements ClientModInitializer {
    private static KeyBinding open;
    private static volatile int material = 1, radius = 0, undo, redo;
    private static String displayMaterial = "Розовый";
    private static volatile int secondary = 0, accent = 0, primaryWeight = 85, secondaryWeight = 15;
    private static volatile String mode = "single", message = "";
    private static volatile boolean selected, hasFirst, hasSecond;
    private static volatile long first, second;
    private static volatile String selectionDimension = "";
    private static boolean shiftAttackHandled;
    private static String cornerAction = "", cornerDimension = "";
    private static long cornerPosition, cornerRequestId = -1L;
    private static int cornerAfterTick, cornerDeadline, cornerAttempts;
    private static volatile boolean serverBusy;
    private static String strokeId="";
    private static long requestSequence, pendingRequestId=-1L, pendingPosition=Long.MIN_VALUE, retryPosition=Long.MIN_VALUE;
    private static long clickGeneration, pendingGeneration=-1L, retryGeneration=-1L, strokePressGeneration=Long.MIN_VALUE;
    private static boolean pendingOneShot, waitingForServerIdle, retryOneShot;
    private static int retryAfterTick;
    private static boolean attackClickQueued, releaseAfterQueued;
    private static int strokeTick;
    private static int lastStrokeTick = Integer.MIN_VALUE / 2;
    private static long lastStrokePos = Long.MIN_VALUE;
    private static boolean pressActive, resetBrush;
    private static final int MAX_PENDING_PATCHES = 512, MAX_PENDING_CELLS = 65_536, MAX_PATCH_CELLS = 512;
    private static final AtomicInteger INCOMING = new AtomicInteger();
    private static final Map<WorldChunk,Map<Long,Integer>> PATCHES = new LinkedHashMap<>();
    private static final Set<RebuildSection> DIRTY_SECTIONS = new LinkedHashSet<>();
    private static final Map<RebuildSection,Long> RECENT_REBUILDS = new LinkedHashMap<>();
    private static final long STROKE_COALESCE_NANOS = 100_000_000L;
    private static final Set<CompositeBlockEntity> DIRTY_OWNERS = new LinkedHashSet<>();
    private static ClientWorld updateWorld;
    private static long lastRevision, latestConfigRequest;
    private static int pendingCells;


    @Override public void onInitializeClient() {
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bloodborne_dw.city_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, "category.bloodborne_dw"));
        ClientPlayNetworking.registerGlobalReceiver(CityEditorServer.STATE, (client, handler, buf, sender) -> {
            if (buf.readableBytes() > 2048) return;
            NbtCompound state;
            try {
                state = buf.readNbt(new NbtTagSizeTracker(8192));
                if (state == null || buf.isReadable()) return;
            } catch (RuntimeException invalid) { return; }
            client.execute(() -> {
                if (client.getNetworkHandler() != handler) return;
                String serverMode = state.getString("mode");
                if (!EditorProtocol.MODES.contains(serverMode)) return;
                long ackId = state.getLong("requestId");
                if (ackId == 0 || ackId >= latestConfigRequest) {
                    int incomingMaterial = clampMaterial(state.getInt("material"));
                    if (material != incomingMaterial) {
                        material = incomingMaterial;
                        displayMaterial = materialName(material);
                    }
                    mode = serverMode;
                    radius = Math.max(0,Math.min(4,state.getInt("radius")));
                    secondary = clampMaterial(state.getInt("secondary"));
                    accent = clampMaterial(state.getInt("accent"));
                    primaryWeight = clampWeight(state.getInt("primaryWeight"));
                    secondaryWeight = Math.min(100-primaryWeight,clampWeight(state.getInt("secondaryWeight")));
                }
                hasFirst = state.getBoolean("hasFirst"); hasSecond = state.getBoolean("hasSecond");
                first = state.getLong("first"); second = state.getLong("second"); selectionDimension = state.getString("selectionDimension");
                message = state.getString("message");
                if (message.startsWith("Цвет скопирован: #") && EditorCells.isRgb(material)) {
                    CityEditorScreen.remember(material & 0xffffff);
                    resetBrush = false;
                }
                undo = state.getInt("undo"); redo = state.getInt("redo"); selected = state.getBoolean("selected");
                serverBusy = state.getBoolean("busy");
                if (cornerRequestId != -1L && ackId == cornerRequestId) {
                    cornerRequestId = -1L;
                    if (state.getBoolean("retry") && cornerAttempts < 8) cornerAfterTick = strokeTick + 2;
                    else cornerAction = "";
                }
                if(!acknowledgePaint(ackId,state.getBoolean("accepted"),state.getBoolean("retry"),serverBusy)
                        && waitingForServerIdle && !serverBusy) {
                    waitingForServerIdle=false; retryAfterTick=strokeTick+2;
                }
                if (client.currentScreen instanceof CityEditorScreen screen) screen.refresh();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CityEditorServer.UPDATE, (client, handler, buf, sender) -> {
            if (buf.readableBytes() > 2048) return;
            try {
                EditorDelta.Decoded delta = EditorDelta.read(buf);
                Identifier dimension = delta.dimension();
                ChunkPos chunkPos = delta.chunk();
                long revision = delta.revision();
                long[] positions = delta.positions();
                int[] values = delta.values();
                if (INCOMING.incrementAndGet() > MAX_PENDING_PATCHES) {
                    INCOMING.decrementAndGet();
                    handler.getConnection().disconnect(Text.literal("Очередь цветов переполнена. Подключитесь повторно для синхронизации."));
                    return;
                }
                client.execute(() -> {
                    try {
                        if (client.getNetworkHandler() != handler || client.world == null
                                || !client.world.getRegistryKey().getValue().equals(dimension)) return;
                        trackWorld(client.world);
                        if (revision <= lastRevision) return;
                        var chunk = client.world.getChunkManager().getChunk(chunkPos.x,chunkPos.z,ChunkStatus.FULL,false);
                        if (chunk == null) return;
                        for (long pos : positions) {
                            int y = BlockPos.unpackLongY(pos);
                            if (y < chunk.getBottomY() || y >= chunk.getTopY()) return;
                        }
                        lastRevision = revision;
                        var patch = PATCHES.computeIfAbsent(chunk,ignored -> new LinkedHashMap<>());
                        for (int i = 0; i < positions.length; i++) {
                            if (patch.put(positions[i],values[i]) == null) pendingCells++;
                        }
                        if (pendingCells > MAX_PENDING_CELLS || PATCHES.size() > MAX_PENDING_PATCHES) {
                            handler.getConnection().disconnect(Text.literal("Очередь цветов переполнена. Подключитесь повторно для синхронизации."));
                        }
                    } finally { INCOMING.decrementAndGet(); }
                });
            } catch (RuntimeException invalid) { /* Ignore malformed visual metadata. */ }
        });
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk) -> discardPatch(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk) -> {
            discardPatch(chunk);
            DIRTY_OWNERS.removeAll(chunk.getBlockEntities().values());
            DIRTY_SECTIONS.removeIf(section -> section.x == chunk.getPos().x && section.z == chunk.getPos().z);
            RECENT_REBUILDS.keySet().removeIf(section -> section.x == chunk.getPos().x && section.z == chunk.getPos().z);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> {
            trackWorld(null);
            resetBrush = false;
            serverBusy = false;
            latestConfigRequest = 0;
            selected = hasFirst = hasSecond = false;
            undo = redo = 0;
            message = "";
            clearCorner();
            resetStroke();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            strokeTick++;
            applyPending(client);
            while (open.wasPressed()) if (client.player != null && heldBrush(client)) {
                config(); client.setScreen(new CityEditorScreen());
            }
            if (client.player == null || client.currentScreen != null || !heldBrush(client)) { clearCorner(); resetStroke(); return; }
            boolean attackHeld=client.options.attackKey.isPressed();
            if (!attackHeld) shiftAttackHandled=false;
            if (!cornerAction.isEmpty()) { processCorner(client); return; }
            if (client.player.isSneaking() || shiftAttackHandled) { stopForCorner(); return; }
            if (!attackHeld && !attackClickQueued) {
                pressActive=false;
                if (pendingRequestId != -1L) releaseAfterQueued=true;
                else if (retryPosition != Long.MIN_VALUE) { releaseAfterQueued=true; attackClickQueued=true; }
                else resetStroke();
                return;
            }
            if (!attackHeld) { releaseAfterQueued=true; pressActive=false; }
            if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;
            boolean oneShot = mode.equals("area") || mode.equals("fill") || mode.equals("noise");
            if(!oneShot&&strokeId.isEmpty())strokeId=java.util.UUID.randomUUID().toString();
            long pos=hit.getBlockPos().asLong();
            boolean retryRequest=retryPosition!=Long.MIN_VALUE;
            long requestGeneration=retryRequest?retryGeneration:clickGeneration;
            boolean requestOneShot=retryRequest?retryOneShot:oneShot;
            long requestPosition=retryRequest?retryPosition:pos;
            if (requestOneShot && strokePressGeneration==requestGeneration) {
                attackClickQueued=false;
                if(!attackHeld&&pendingRequestId==-1L)resetStroke();
                return;
            }
            if (pendingRequestId != -1L || waitingForServerIdle || strokeTick - lastStrokeTick < 2 || strokeTick < retryAfterTick) return;
            if (!requestOneShot && requestPosition == lastStrokePos) {
                attackClickQueued=false;
                if(!attackHeld)resetStroke();
                return;
            }
            if (retryRequest && requestPosition != pos) {
                retryPosition = Long.MIN_VALUE;
                retryGeneration = -1L;
                waitingForServerIdle = false;
                if (!attackHeld) resetStroke();
                return;
            }
            if (sendTrackedPaint(requestPosition,requestOneShot,requestGeneration)) {
                if(!retryRequest||requestGeneration==clickGeneration)attackClickQueued=false;
                if(retryRequest){retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;}
                lastStrokeTick = strokeTick;
            }
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (world.isClient && client.currentScreen == null && heldBrush(client)) {
                if (player.isSneaking()) {
                    if (!shiftAttackHandled) { queueCorner("first", client, pos.asLong()); shiftAttackHandled=true; }
                    return net.minecraft.util.ActionResult.FAIL;
                }
                if (!cornerAction.isEmpty() || shiftAttackHandled) return net.minecraft.util.ActionResult.FAIL;
                if(!pressActive||releaseAfterQueued){clickGeneration++;pressActive=true;releaseAfterQueued=false;}
                attackClickQueued=true;return net.minecraft.util.ActionResult.FAIL;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (world.isClient && client.currentScreen == null && heldBrush(client)) { if (player.isSneaking()) queueCorner("second", client, hit.getBlockPos().asLong()); else if (cornerAction.isEmpty()) request("sample"); return net.minecraft.util.ActionResult.FAIL; }
            return net.minecraft.util.ActionResult.PASS;
        });
        HudRenderCallback.EVENT.register((draw, tick) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && heldBrush(client)) {
                String status = (resetBrush ? "Исходный цвет" : displayMaterial) + " · " + modeName(mode) + (mode.equals("plane") ? " · " + (radius*2+1) + "×" + (radius*2+1) : "") + (message.isEmpty() ? "" : " · " + message);
                draw.drawCenteredTextWithShadow(client.textRenderer, Text.literal(status), draw.getScaledWindowWidth()/2, draw.getScaledWindowHeight()-62, 0xFFE8D9B0);
            }
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen != null || !heldBrush(client) || !(client.crosshairTarget instanceof BlockHitResult hit)) return;
            float color = selected ? 0.45F : 0.95F;
            var camera=context.camera().getPos();
            WorldRenderer.drawBox(context.matrixStack(), context.consumers().getBuffer(RenderLayer.getLines()),
                new Box(hit.getBlockPos()).expand(0.002).offset(-camera.x,-camera.y,-camera.z), color, 0.88F, 0.32F, 0.92F);
            if (hasFirst && client.world != null && client.world.getRegistryKey().getValue().toString().equals(selectionDimension)) {
                var a=net.minecraft.util.math.BlockPos.fromLong(first); var b=hasSecond ? net.minecraft.util.math.BlockPos.fromLong(second) : a;
                WorldRenderer.drawBox(context.matrixStack(), context.consumers().getBuffer(RenderLayer.getLines()),
                    new Box(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,Math.max(a.getY(),b.getY())+1,Math.max(a.getZ(),b.getZ())+1).offset(-camera.x,-camera.y,-camera.z), 0.35F,0.8F,1F,0.9F);
            }
        });
    }
    static int material() { return material; }
    static int secondary() { return secondary; }
    static int accent() { return accent; }
    static int primaryWeight() { return primaryWeight; }
    static int secondaryWeight() { return secondaryWeight; }
    static int radius() { return radius; }
    static String mode() { return mode; }
    static int undoCount() { return undo; }
    static int redoCount() { return redo; }
    static boolean selected() { return selected; }
    static boolean busy() { return serverBusy; }
    static String selectionText() {
        var world=MinecraftClient.getInstance().world;
        if(hasFirst && world!=null && !world.getRegistryKey().getValue().toString().equals(selectionDimension))return "Выделение: в другом измерении";
        return hasSecond ? "Выделение: две точки" : hasFirst ? "Выделение: первая точка" : "Выделение: нет точек";
    }
    static String message() { return message; }
    static String materialName(int value) { if (EditorCells.isRgb(value)) return String.format("#%06X",value & 0xffffff); return switch (value) { case 0 -> "Базовый"; case 1 -> "Розовый"; case 2 -> "Шалфей"; case 3 -> "Песочный"; case 4 -> "Красный"; default -> "?"; }; }
    static String modeName(String value) { return switch(value) { case "single" -> "одиночный"; case "area" -> "область"; case "plane" -> "плоскость"; case "fill" -> "заливка"; case "noise" -> "шум"; default -> value; }; }
    static boolean heldBrush(MinecraftClient client) { return client.player != null && client.player.getMainHandStack().isOf(CityEditorServer.BRUSH); }
    private static long nextRequestId() { long value=++requestSequence; return value==0L?++requestSequence:value; }
    private static boolean sendTrackedPaint(long pos, boolean oneShot,long generation) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||!heldBrush(client)||!ClientPlayNetworking.canSend(CityEditorServer.REQUEST)||pendingRequestId!=-1L)return false;
        long id=nextRequestId(); NbtCompound packet=new NbtCompound(); packet.putString("action",resetBrush ? "reset" : "paint");packet.putLong("requestId",id);
        packet.putLong("expectedTarget",pos);
        if(!strokeId.isEmpty())packet.putString("stroke",strokeId);
        var buf=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();buf.writeNbt(packet);ClientPlayNetworking.send(CityEditorServer.REQUEST,buf);
        pendingRequestId=id;pendingPosition=pos;pendingOneShot=oneShot;pendingGeneration=generation;return true;
    }
    static void request(String action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (!heldBrush(client)&&!action.equals("endStroke")) || !ClientPlayNetworking.canSend(CityEditorServer.REQUEST)) return;
        NbtCompound request = new NbtCompound(); request.putString("action", action);
        request.putLong("requestId",nextRequestId());
        if(action.equals("paint")&&!strokeId.isEmpty())request.putString("stroke",strokeId);
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); buf.writeNbt(request); ClientPlayNetworking.send(CityEditorServer.REQUEST, buf);
    }
    private static void config() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||!heldBrush(client)||!ClientPlayNetworking.canSend(CityEditorServer.REQUEST))return;
        NbtCompound request=new NbtCompound();request.putString("action","config");latestConfigRequest=nextRequestId();request.putLong("requestId",latestConfigRequest);request.putInt("material",material);request.putInt("radius",radius);request.putString("mode",mode);
        request.putInt("secondary",secondary);request.putInt("accent",accent);request.putInt("primaryWeight",primaryWeight);request.putInt("secondaryWeight",secondaryWeight);
        var buf=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();buf.writeNbt(request);ClientPlayNetworking.send(CityEditorServer.REQUEST,buf);
    }
    static boolean resetBrush() { return resetBrush; }
    static void toggleReset() { resetBrush = !resetBrush; }
    static void setMaterial(int value) { resetBrush = false; value=clampMaterial(value); if (material!=value) { material=value; displayMaterial=materialName(value); config(); } }
    static void setMode(String value) { if (!mode.equals(value)) { mode=value; config(); } }
    static void setRadius(int value) { value=Math.max(0,Math.min(4,value)); if (radius!=value) { radius=value; config(); } }
    static void setSecondary(int value) { secondary=clampMaterial(value); config(); }
    static void setAccent(int value) { accent=clampMaterial(value); config(); }
    static void setWeights(int primary, int secondary) { primaryWeight=primary; secondaryWeight=secondary; config(); }
    private static int clampMaterial(int value) { return EditorCells.isStored(value) ? value : 0; }
    private static int clampWeight(int value) { return Math.max(0,Math.min(100,value)); }
    static boolean acknowledgePaint(long id,boolean accepted,boolean retry,boolean busy){
        if(pendingRequestId==-1L||id!=pendingRequestId)return false;
        long ackPos=pendingPosition,ackGeneration=pendingGeneration;boolean ackOneShot=pendingOneShot;
        pendingRequestId=-1L;pendingPosition=Long.MIN_VALUE;pendingOneShot=false;pendingGeneration=-1L;
        if (!cornerAction.isEmpty() || (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isSneaking())) {
            resetStroke(); cornerAfterTick=strokeTick+2; return true;
        }
        if(retry){retryPosition=ackPos;retryOneShot=ackOneShot;retryGeneration=ackGeneration;waitingForServerIdle=busy;retryAfterTick=strokeTick+2;if(releaseAfterQueued)attackClickQueued=true;return true;}
        lastStrokePos=ackPos;if(ackOneShot)strokePressGeneration=ackGeneration;
        retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;
        if(clickGeneration>ackGeneration&&attackClickQueued)finishAcknowledgedStrokePreservingQueuedPress();
        else if(releaseAfterQueued)resetStroke();
        return true;
    }
    private static void clearCorner() { cornerAction=""; cornerRequestId=-1L; cornerAttempts=0; }
    private static void stopForCorner() {
        attackClickQueued=false; retryPosition=Long.MIN_VALUE; retryOneShot=false; retryGeneration=-1L;
        waitingForServerIdle=false; pressActive=false;
        if (pendingRequestId == -1L) { if (!strokeId.isEmpty()) resetStroke(); }
        else releaseAfterQueued=true;
    }
    private static void queueCorner(String action, MinecraftClient client, long pos) {
        if (!cornerAction.isEmpty() || client.world == null) return;
        cornerAction=action; cornerPosition=pos; cornerDimension=client.world.getRegistryKey().getValue().toString();
        cornerRequestId=-1L; cornerAttempts=0; cornerAfterTick=strokeTick+2; cornerDeadline=strokeTick+100;
        stopForCorner();
    }
    private static void processCorner(MinecraftClient client) {
        stopForCorner();
        if (strokeTick > cornerDeadline || client.world == null || !client.world.getRegistryKey().getValue().toString().equals(cornerDimension)
                || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getBlockPos().asLong()!=cornerPosition) {
            clearCorner(); message="Выделение отменено: цель изменилась или сервер не ответил"; return;
        }
        if (pendingRequestId!=-1L || cornerRequestId!=-1L || serverBusy || strokeTick<cornerAfterTick) return;
        if (!ClientPlayNetworking.canSend(CityEditorServer.REQUEST)) { clearCorner(); return; }
        long id=nextRequestId(); NbtCompound packet=new NbtCompound();
        packet.putString("action",cornerAction); packet.putLong("requestId",id); packet.putLong("expectedTarget",cornerPosition);
        var buf=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); buf.writeNbt(packet);
        cornerRequestId=id; cornerAttempts++; ClientPlayNetworking.send(CityEditorServer.REQUEST,buf);
    }
    private static void finishAcknowledgedStrokePreservingQueuedPress(){
        if(!strokeId.isEmpty()){request("endStroke");strokeId="";}
        lastStrokePos=Long.MIN_VALUE;strokePressGeneration=Long.MIN_VALUE;
        retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;retryAfterTick=0;
    }
    private static void resetStroke() { if(!strokeId.isEmpty()){request("endStroke");strokeId="";}lastStrokePos=Long.MIN_VALUE; strokePressGeneration=Long.MIN_VALUE; lastStrokeTick=Integer.MIN_VALUE/2; pendingRequestId=-1L;pendingPosition=Long.MIN_VALUE;pendingOneShot=false;pendingGeneration=-1L;retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;retryAfterTick=0;attackClickQueued=false;releaseAfterQueued=false;pressActive=false; }

    private record RebuildSection(int x,int y,int z) {}

    private static void trackWorld(ClientWorld world) {
        if (updateWorld == world) return;
        updateWorld = world;
        PATCHES.clear();
        DIRTY_SECTIONS.clear();
        RECENT_REBUILDS.clear();
        DIRTY_OWNERS.clear();
        pendingCells = 0;
        lastRevision = 0;
    }

    private static void discardPatch(WorldChunk chunk) {
        var patch = PATCHES.remove(chunk);
        if (patch != null) pendingCells -= patch.size();
    }

    private static void applyPending(MinecraftClient client) {
        ClientWorld world = client.world;
        trackWorld(world);
        if (world == null) return;
        boolean coalesce = serverBusy || !strokeId.isEmpty();
        if (!coalesce && !RECENT_REBUILDS.isEmpty()) RECENT_REBUILDS.clear();
        if (PATCHES.isEmpty() && DIRTY_OWNERS.isEmpty() && DIRTY_SECTIONS.isEmpty()
                && RECENT_REBUILDS.isEmpty()) return;
        long start = System.nanoTime();
        long deadline = start + 1_000_000;
        if (!RECENT_REBUILDS.isEmpty())
            RECENT_REBUILDS.values().removeIf(last -> start - last >= STROKE_COALESCE_NANOS);
        // Drain invalidations at the same rate as patches so a scattered edit cannot grow the queue indefinitely.
        var patches = PATCHES.entrySet().iterator();
        while (DIRTY_SECTIONS.size() < 4096 && DIRTY_OWNERS.size() < 4096
                && patches.hasNext() && System.nanoTime() < start + 500_000) {
            var entry = patches.next();
            WorldChunk chunk = entry.getKey();
            Map<Long,Integer> patch = entry.getValue();
            if (world.getChunkManager().getChunk(chunk.getPos().x,chunk.getPos().z,ChunkStatus.FULL,false) != chunk) {
                pendingCells -= patch.size();
                patches.remove();
                continue;
            }
            if (patch.size() <= MAX_PATCH_CELLS) patches.remove();
            else {
                Map<Long,Integer> batch = new LinkedHashMap<>();
                var cells = patch.entrySet().iterator();
                while (batch.size() < MAX_PATCH_CELLS) {
                    var change = cells.next();
                    batch.put(change.getKey(),change.getValue());
                    cells.remove();
                }
                patch = batch;
            }
            pendingCells -= patch.size();
            var data = (RetainedFrameData)(Object)RetainedFrames.data(chunk);
            EditorCells before = data.dw$editor();
            EditorCells after = before.withAll(patch);
            if (before == after) continue;
            NativeVisuals.data(chunk,data.dw$withEditor(after));
            for (var change : patch.entrySet()) {
                if (before.style(change.getKey()) == change.getValue()) continue;
                BlockPos pos = BlockPos.fromLong(change.getKey());
                if (chunk.getBlockEntities().get(pos) instanceof CompositeBlockEntity owner) DIRTY_OWNERS.add(owner);
                dirty(pos);
            }
        }
        var owners = DIRTY_OWNERS.iterator();
        while (owners.hasNext() && System.nanoTime() < deadline) {
            EditorMeshUpdates.changed(owners.next());
            owners.remove();
        }
        var sections = DIRTY_SECTIONS.iterator();
        int scheduled = 0;
        while (scheduled < 2 && sections.hasNext() && System.nanoTime() < deadline) {
            RebuildSection section = sections.next();
            if (coalesce && RECENT_REBUILDS.containsKey(section)) continue;
            sections.remove();
            if (section.y >= world.getBottomSectionCoord() && section.y < world.getTopSectionCoord()
                    && world.getChunkManager().getChunk(section.x,section.z,ChunkStatus.FULL,false) != null) {
                client.worldRenderer.scheduleBlockRender(section.x,section.y,section.z);
                if (coalesce) RECENT_REBUILDS.put(section,start);
                scheduled++;
            }
        }
    }

    private static void dirty(BlockPos pos) {
        for (int x = (pos.getX()-1) >> 4; x <= (pos.getX()+1) >> 4; x++)
            for (int y = (pos.getY()-1) >> 4; y <= (pos.getY()+1) >> 4; y++)
                for (int z = (pos.getZ()-1) >> 4; z <= (pos.getZ()+1) >> 4; z++)
                    DIRTY_SECTIONS.add(new RebuildSection(x,y,z));
    }
}
