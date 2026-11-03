package dev.dreamwalker.bloodbornedw.editor;

import dev.dreamwalker.bloodbornedw.composite.RetainedFrameData;
import dev.dreamwalker.bloodbornedw.composite.RetainedFrames;
import dev.dreamwalker.bloodbornedw.composite.NativeVisualData;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

/** Client controls and chunk snapshot updates for the city facade editor. */
public final class CityEditorClient implements ClientModInitializer {
    private static KeyBinding open;
    private static volatile int material = 1, radius = 0, undo, redo;
    private static volatile int secondary = 2, accent = 3, primaryWeight = 80, secondaryWeight = 15;
    private static volatile String mode = "single", message = "";
    private static volatile boolean selected;
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
    private static boolean pressActive;

    @Override public void onInitializeClient() {
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.bloodborne_dw.city_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, "category.bloodborne_dw"));
        ClientPlayNetworking.registerGlobalReceiver(CityEditorServer.STATE, (client, handler, buf, sender) -> {
            NbtCompound state = buf.readNbt();
            if (state == null || buf.isReadable()) return;
            client.execute(() -> {
                if (client.getNetworkHandler() != handler) return;
                material = state.getInt("material"); mode = state.getString("mode"); radius = state.getInt("radius");
                secondary = clampMaterial(state.getInt("secondary")); accent = clampMaterial(state.getInt("accent"));
                primaryWeight = clampWeight(state.getInt("primaryWeight")); secondaryWeight = clampWeight(state.getInt("secondaryWeight"));
                if (primaryWeight + secondaryWeight > 100) secondaryWeight = 100 - primaryWeight;
                message = state.getString("message"); undo = state.getInt("undo"); redo = state.getInt("redo"); selected = state.getBoolean("selected");
                serverBusy = state.getBoolean("busy");
                long ackId = state.getLong("requestId");
                if(!acknowledgePaint(ackId,state.getBoolean("accepted"),state.getBoolean("retry"),serverBusy)
                        && waitingForServerIdle && !serverBusy) {
                    waitingForServerIdle=false; retryAfterTick=strokeTick+2;
                }
                if (client.currentScreen instanceof CityEditorScreen screen) screen.refresh();
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CityEditorServer.UPDATE, (client, handler, buf, sender) -> {
            Identifier dimension = buf.readIdentifier(); int chunkX = buf.readInt(), chunkZ = buf.readInt(); NbtCompound packet = buf.readNbt();
            if (packet == null || buf.isReadable() || !packet.contains("Editor", 10)) return;
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null || !client.world.getRegistryKey().getValue().equals(dimension)) return;
                var chunk = client.world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) return;
                try {
                    EditorCells cells = EditorCells.readPacked(packet.getCompound("Editor"), new ChunkPos(chunkX, chunkZ), client.world.getBottomY(), client.world.getTopY());
                    NativeVisualData data = RetainedFrames.data(chunk);
                    EditorCells oldCells = ((RetainedFrameData)(Object)data).dw$editor();
                    java.util.Set<RebuildSection> changed = changedSections(oldCells, cells);
                    if (changed.isEmpty()) return;
                    NativeVisualData replacement = ((RetainedFrameData)(Object)data).dw$withEditor(cells);
                    if (replacement != data) {
                        dev.dreamwalker.bloodbornedw.composite.NativeVisuals.data(chunk, replacement);
                        for (var entity : chunk.getBlockEntities().values()) {
                            if (entity instanceof CompositeBlockEntity owner
                                    && oldCells.style(owner.getPos()) != cells.style(owner.getPos())) {
                                EditorMeshUpdates.changed(owner);
                            }
                        }
                        for (RebuildSection section : changed) {
                            int minX=section.x<<4, minY=section.y<<4, minZ=section.z<<4;
                            client.worldRenderer.scheduleBlockRenders(minX,minY,minZ,minX+15,minY+15,minZ+15);
                        }
                    }
                } catch (RuntimeException invalid) { /* Ignore malformed visual metadata. */ }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            strokeTick++;
            while (open.wasPressed()) if (client.player != null && heldBrush(client)) client.setScreen(new CityEditorScreen());
            if (client.player == null || client.currentScreen != null || !heldBrush(client)) { resetStroke(); return; }
            boolean attackHeld=client.options.attackKey.isPressed();
            if (!attackHeld && !attackClickQueued) {
                pressActive=false;
                if (pendingRequestId != -1L) releaseAfterQueued=true;
                else if (retryPosition != Long.MIN_VALUE) { releaseAfterQueued=true; attackClickQueued=true; }
                else resetStroke();
                return;
            }
            if (!attackHeld) { releaseAfterQueued=true; pressActive=false; }
            if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;
            boolean oneShot = mode.equals("area") || mode.equals("fill") || mode.equals("noise") || mode.equals("stamp");
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
            if (sendTrackedPaint(requestPosition,requestOneShot,requestGeneration)) {
                if(!retryRequest||requestGeneration==clickGeneration)attackClickQueued=false;
                if(retryRequest){retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;}
                lastStrokeTick = strokeTick;
            }
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (world.isClient && client.currentScreen == null && heldBrush(client)) {
                if(!pressActive||releaseAfterQueued){clickGeneration++;pressActive=true;releaseAfterQueued=false;}
                attackClickQueued=true;return net.minecraft.util.ActionResult.FAIL;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (world.isClient && client.currentScreen == null && heldBrush(client)) { request("sample"); return net.minecraft.util.ActionResult.FAIL; }
            return net.minecraft.util.ActionResult.PASS;
        });
        HudRenderCallback.EVENT.register((draw, tick) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && heldBrush(client)) {
                String status = "Режим: " + modeName(mode) + " · Радиус: " + radius + " · " + materialName(material) + (message.isEmpty() ? "" : " · " + message);
                draw.drawText(client.textRenderer, Text.literal(status), 8, 8, 0xFFE8D9B0, true);
            }
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen != null || !heldBrush(client) || !(client.crosshairTarget instanceof BlockHitResult hit)) return;
            float color = selected ? 0.45F : 0.95F;
            var camera=context.camera().getPos();
            WorldRenderer.drawBox(context.matrixStack(), context.consumers().getBuffer(RenderLayer.getLines()),
                new Box(hit.getBlockPos()).expand(0.002).offset(-camera.x,-camera.y,-camera.z), color, 0.88F, 0.32F, 0.92F);
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
    static String message() { return message; }
    static String materialName(int value) { return switch (value) { case 0 -> "Базовый"; case 1 -> "Розовый"; case 2 -> "Шалфей"; case 3 -> "Песочный"; case 4 -> "Красный"; default -> "?"; }; }
    static String modeName(String value) { return switch(value) { case "single" -> "одиночный"; case "area" -> "область"; case "plane" -> "плоскость"; case "fill" -> "заливка"; case "noise" -> "шум"; case "stamp" -> "штамп"; default -> value; }; }
    static boolean heldBrush(MinecraftClient client) { return client.player != null && client.player.getMainHandStack().isOf(CityEditorServer.BRUSH); }
    private static long nextRequestId() { long value=++requestSequence; return value==0L?++requestSequence:value; }
    private static boolean sendTrackedPaint(long pos, boolean oneShot,long generation) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||!heldBrush(client)||!ClientPlayNetworking.canSend(CityEditorServer.REQUEST)||pendingRequestId!=-1L)return false;
        long id=nextRequestId(); NbtCompound packet=new NbtCompound(); packet.putString("action","paint");packet.putLong("requestId",id);
        if(!strokeId.isEmpty())packet.putString("stroke",strokeId);
        var buf=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();buf.writeNbt(packet);ClientPlayNetworking.send(CityEditorServer.REQUEST,buf);
        pendingRequestId=id;pendingPosition=pos;pendingOneShot=oneShot;pendingGeneration=generation;return true;
    }
    static void request(String action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (!heldBrush(client)&&!action.equals("endStroke")) || !ClientPlayNetworking.canSend(CityEditorServer.REQUEST)) return;
        NbtCompound request = new NbtCompound(); request.putString("action", action);
        if(action.equals("paint"))request.putLong("requestId",nextRequestId());
        if(action.equals("paint")&&!strokeId.isEmpty())request.putString("stroke",strokeId);
        var buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create(); buf.writeNbt(request); ClientPlayNetworking.send(CityEditorServer.REQUEST, buf);
    }
    private static void config() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||!heldBrush(client)||!ClientPlayNetworking.canSend(CityEditorServer.REQUEST))return;
        NbtCompound request=new NbtCompound();request.putString("action","config");request.putInt("material",material);request.putInt("radius",radius);request.putString("mode",mode);
        request.putInt("secondary",secondary);request.putInt("accent",accent);request.putInt("primaryWeight",primaryWeight);request.putInt("secondaryWeight",secondaryWeight);
        var buf=net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();buf.writeNbt(request);ClientPlayNetworking.send(CityEditorServer.REQUEST,buf);
    }
    static void setMaterial(int value) { material = Math.max(0,Math.min(4,value)); config(); }
    static void setMode(String value) { mode = value; config(); }
    static void setRadius(int value) { radius = Math.max(0, Math.min(4, value)); config(); }
    static void setSecondary(int value) { secondary=clampMaterial(value); config(); }
    static void setAccent(int value) { accent=clampMaterial(value); config(); }
    static void setPrimaryWeight(int value) { primaryWeight=clampWeight(value); if(primaryWeight+secondaryWeight>100)secondaryWeight=100-primaryWeight; config(); }
    static void setSecondaryWeight(int value) { secondaryWeight=clampWeight(value); if(primaryWeight+secondaryWeight>100)primaryWeight=100-secondaryWeight; config(); }
    private static int clampMaterial(int value) { return Math.max(0,Math.min(4,value)); }
    private static int clampWeight(int value) { return Math.max(0,Math.min(100,value)); }
    static boolean acknowledgePaint(long id,boolean accepted,boolean retry,boolean busy){
        if(pendingRequestId==-1L||id!=pendingRequestId)return false;
        long ackPos=pendingPosition,ackGeneration=pendingGeneration;boolean ackOneShot=pendingOneShot;
        pendingRequestId=-1L;pendingPosition=Long.MIN_VALUE;pendingOneShot=false;pendingGeneration=-1L;
        if(retry){retryPosition=ackPos;retryOneShot=ackOneShot;retryGeneration=ackGeneration;waitingForServerIdle=busy;retryAfterTick=strokeTick+2;if(releaseAfterQueued)attackClickQueued=true;return true;}
        lastStrokePos=ackPos;if(ackOneShot)strokePressGeneration=ackGeneration;
        retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;
        if(clickGeneration>ackGeneration&&attackClickQueued)finishAcknowledgedStrokePreservingQueuedPress();
        else if(releaseAfterQueued)resetStroke();
        return true;
    }
    private static void finishAcknowledgedStrokePreservingQueuedPress(){
        if(!strokeId.isEmpty()){request("endStroke");strokeId="";}
        lastStrokePos=Long.MIN_VALUE;strokePressGeneration=Long.MIN_VALUE;
        retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;retryAfterTick=0;
    }
    private static void resetStroke() { if(!strokeId.isEmpty()){request("endStroke");strokeId="";}lastStrokePos=Long.MIN_VALUE; strokePressGeneration=Long.MIN_VALUE; lastStrokeTick=Integer.MIN_VALUE/2; pendingRequestId=-1L;pendingPosition=Long.MIN_VALUE;pendingOneShot=false;pendingGeneration=-1L;retryPosition=Long.MIN_VALUE;retryOneShot=false;retryGeneration=-1L;waitingForServerIdle=false;retryAfterTick=0;attackClickQueued=false;releaseAfterQueued=false;pressActive=false; }

    private record RebuildSection(int x,int y,int z) {}
    private static java.util.Set<RebuildSection> changedSections(EditorCells before,EditorCells after) {
        var changed=new java.util.HashSet<RebuildSection>();int i=0,j=0;
        while(i<before.size()||j<after.size()) {
            long oldPos=i<before.size()?before.positionAt(i):Long.MAX_VALUE;
            long newPos=j<after.size()?after.positionAt(j):Long.MAX_VALUE;
            long pos; boolean differs;
            if(oldPos==newPos){pos=oldPos;differs=before.styleAt(i)!=after.styleAt(j);i++;j++;}
            else if(oldPos<newPos){pos=oldPos;differs=true;i++;}
            else {pos=newPos;differs=true;j++;}
            if(!differs)continue;
            var p=net.minecraft.util.math.BlockPos.fromLong(pos);int sx=p.getX()>>4,sy=p.getY()>>4,sz=p.getZ()>>4;
            changed.add(new RebuildSection(sx,sy,sz));
            if((p.getY()&15)==0)changed.add(new RebuildSection(sx,sy-1,sz));
            if((p.getY()&15)==15)changed.add(new RebuildSection(sx,sy+1,sz));
            if((p.getX()&15)==0)changed.add(new RebuildSection(sx-1,sy,sz));
            if((p.getX()&15)==15)changed.add(new RebuildSection(sx+1,sy,sz));
            if((p.getZ()&15)==0)changed.add(new RebuildSection(sx,sy,sz-1));
            if((p.getZ()&15)==15)changed.add(new RebuildSection(sx,sy,sz+1));
        }
        return changed;
    }
}
