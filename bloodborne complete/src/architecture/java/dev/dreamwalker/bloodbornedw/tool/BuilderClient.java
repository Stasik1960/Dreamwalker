package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import java.util.UUID;

public final class BuilderClient {
    private static boolean initialized,pressed;
    private static JsonObject latest=new JsonObject();private static long highlightUntil;
    private BuilderClient(){}
    public static void initialize(){if(initialized)return;initialized=true;
        ClientPlayNetworking.registerGlobalReceiver(BuilderServer.VIEW,(client,handler,buf,sender)->{int bytes=buf.readableBytes();String raw;try{raw=buf.readString(BuilderServer.MAX_VIEW);}catch(RuntimeException malformed){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED","builder_client",null,"BUILDER_VIEW","Malformed bounded menu packet",malformed);return;}dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.network("client","receive",BuilderServer.VIEW.toString(),bytes);client.execute(()->{try{latest=JsonParser.parseString(raw).getAsJsonObject();highlightUntil=System.nanoTime()+5_000_000_000L;if(client.currentScreen instanceof BuilderScreen screen)screen.accept(latest);else if(latest.get("openMenu").getAsBoolean())client.setScreen(new BuilderScreen(latest));}catch(RuntimeException invalid){dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.error(null,"UNASSIGNED","builder_client",null,"BUILDER_VIEW","Malformed server menu view",invalid);}});});
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(!client.options.attackKey.isPressed())pressed=false;if(client.player==null)latest=new JsonObject();});
        WorldRenderEvents.AFTER_ENTITIES.register(context->{MinecraftClient mc=MinecraftClient.getInstance();if(mc.world==null||mc.player==null||!BuildingTool.isHeld(mc.player)||System.nanoTime()>highlightUntil||!latest.has("target")||context.consumers()==null)return;
            JsonObject target=latest.getAsJsonObject("target");UUID uuid;try{uuid=UUID.fromString(target.get("instance").getAsString());}catch(RuntimeException invalid){return;}var matrices=context.matrixStack();Vec3d camera=context.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);var lines=context.consumers().getBuffer(RenderLayer.getLines());
            if(target.get("kind").getAsString().equals("RP-объект")){for(var entity:mc.world.getEntities())if(entity instanceof RpObjectEntity rp&&rp.getUuid().equals(uuid))for(Box box:rp.selectionBoxes())WorldRenderer.drawBox(matrices,lines,box,.9f,.65f,.12f,1);}
            else for(var cell:CompositeLedger.get(mc.world).cells())for(var c:CompositeLedger.get(mc.world).at(cell))if(c.owner().instanceId().equals(uuid))for(var box:CompositeRuntime.shape(c.shape().selection()).getBoundingBoxes())WorldRenderer.drawBox(matrices,lines,box.offset(CompositeData.pos(cell)),.9f,.65f,.12f,1);
            matrices.pop();});
    }
    public static void send(JsonObject request){if(!ClientPlayNetworking.canSend(BuilderServer.REQUEST))return;String json=request.toString();if(json.length()>BuilderServer.MAX_REQUEST)return;var buf=PacketByteBufs.create();buf.writeString(json,BuilderServer.MAX_REQUEST);dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics.network("client","send",BuilderServer.REQUEST.toString(),buf.readableBytes());ClientPlayNetworking.send(BuilderServer.REQUEST,buf);}
    public static JsonObject request(String op){JsonObject q=new JsonObject();q.addProperty("op",op);return q;}
    /** The attack latch ends only after key release; holding never repeats an edit. */
    public static boolean attack(){MinecraftClient client=MinecraftClient.getInstance();if(!BuildingTool.isHeld(client.player))return false;if(!pressed){pressed=true;send(request("click"));}return true;}
    public static boolean use(){MinecraftClient client=MinecraftClient.getInstance();if(!BuildingTool.mainHeld(client.player))return false;send(request("open"));return true;}
}
