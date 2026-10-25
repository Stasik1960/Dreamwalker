package dev.dreamwalker.bloodbornedw.gallery;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;

/** Opt-in continuous movement between existing capture frames. Movement is
 * sent by ordinary ClientPlayerEntity ticks; the server is only read for ACK. */
final class GallerySpectatorTravel {
    private static final double MAX_DISTANCE=2048,MAX_STEP=1;
    private static final int MAX_TICKS=4096;
    private final Vec3d start,target;private final float yaw,pitch;
    private final long startedWorldTick;private final int initialLoadedChunks;
    private Vec3d lastObserved,end;private long endedWorldTick;
    private int ticks,movementTicks,waitingForChunkTicks;
    private double distance,observedDistance,maxStep,maxObservedTick;
    private boolean complete,startChunkLoadedAtEnd,targetChunkLoadedAtEnd;
    private volatile boolean serverQueryPending,serverAcknowledged;
    private volatile Vec3d serverPosition;private volatile String serverFailure;
    private volatile int serverLoadedChunksAtAcknowledgement=-1;

    GallerySpectatorTravel(MinecraftClient client,JsonArray camera){
        require(client.isInSingleplayer()&&client.getServer()!=null,"Travel capture requires the working-copy integrated server");
        require(client.player!=null&&client.world!=null&&client.player.isSpectator(),"Travel capture requires an existing spectator player");
        start=client.player.getPos();lastObserved=start;target=new Vec3d(camera.get(0).getAsDouble(),camera.get(1).getAsDouble(),camera.get(2).getAsDouble());yaw=camera.get(3).getAsFloat();pitch=camera.get(4).getAsFloat();
        require(start.distanceTo(target)<=MAX_DISTANCE,"Travel leg exceeds 2048 blocks");
        startedWorldTick=client.world.getTime();initialLoadedChunks=client.world.getChunkManager().getLoadedChunkCount();
    }
    boolean complete(){return complete;}
    boolean tick(MinecraftClient client){
        if(complete)return true;require(++ticks<=MAX_TICKS,"Native spectator travel exceeded 4096 client ticks");
        require(client.isInSingleplayer()&&client.player!=null&&client.world!=null&&client.player.isSpectator(),"Spectator travel context changed");
        require(serverFailure==null,serverFailure);
        Vec3d current=client.player.getPos();double observed=current.distanceTo(lastObserved);observedDistance+=observed;maxObservedTick=Math.max(maxObservedTick,observed);lastObserved=current;
        client.player.setVelocity(Vec3d.ZERO);client.player.setYaw(yaw);client.player.setPitch(pitch);
        Vec3d remaining=target.subtract(current);double left=remaining.length();
        if(left>0.00001){
            Vec3d step=remaining.multiply(Math.min(MAX_STEP,left)/left);
            if(!chunkPresent(client,BlockPos.ofFloored(current.add(step)))){waitingForChunkTicks++;return false;}
            require(step.length()<=MAX_STEP+0.000001,"Travel requested an excessive native movement step");
            require(distance+step.length()<=MAX_DISTANCE+0.000001,"Cumulative spectator travel exceeded 2048 blocks");
            client.player.move(MovementType.SELF,step);double moved=client.player.getPos().distanceTo(current);
            require(moved<=MAX_STEP+0.000001,"Native travel movement exceeded one block");
            distance+=moved;maxStep=Math.max(maxStep,moved);movementTicks++;return false;
        }
        if(serverAcknowledged){
            complete=true;end=current;endedWorldTick=client.world.getTime();
            startChunkLoadedAtEnd=chunkPresent(client,BlockPos.ofFloored(start));targetChunkLoadedAtEnd=chunkPresent(client,BlockPos.ofFloored(target));return true;
        }
        if(!serverQueryPending){
            serverQueryPending=true;var server=client.getServer();var uuid=client.player.getUuid();var dimension=client.world.getRegistryKey();
            server.execute(()->{try{var player=server.getPlayerManager().getPlayer(uuid);require(player!=null&&player.isSpectator()&&player.getWorld().getRegistryKey().equals(dimension),"Server spectator travel context changed");serverPosition=player.getPos();boolean acknowledged=serverPosition.squaredDistanceTo(target)<0.0025;if(acknowledged)serverLoadedChunksAtAcknowledgement=player.getServerWorld().getChunkManager().getLoadedChunkCount();serverAcknowledged=acknowledged;}catch(Throwable failure){serverFailure=failure.toString();}finally{serverQueryPending=false;}});
        }
        return false;
    }
    JsonObject report(){
        var out=new JsonObject();out.addProperty("method","ClientPlayerEntity.move(SELF); ordinary native movement packets; read-only integrated-server ACK");
        out.addProperty("navigationCommandSent",false);out.addProperty("singleplayerSpectatorVerified",true);
        out.addProperty("chunkDeliveryCheck","ClientChunkManager.getChunk(FULL,false) != null; no EmptyChunk fallback");
        out.addProperty("status",complete?"COMPLETE_SERVER_ACKNOWLEDGED":"MOVING");out.add("start",point(start));out.add("target",point(target));if(end!=null)out.add("end",point(end));if(serverPosition!=null)out.add("serverPosition",point(serverPosition));
        out.addProperty("travelTicks",ticks);out.addProperty("movementTicks",movementTicks);out.addProperty("waitingForLoadedChunkTicks",waitingForChunkTicks);out.addProperty("distance",distance);out.addProperty("straightLineDistance",start.distanceTo(target));out.addProperty("observedDistance",observedDistance);out.addProperty("maxMovementStep",maxStep);out.addProperty("maxObservedTickDistance",maxObservedTick);
        out.addProperty("maxAllowedMovementStep",MAX_STEP);out.addProperty("maxAllowedDistance",MAX_DISTANCE);out.addProperty("maxAllowedTicks",MAX_TICKS);out.addProperty("startedWorldTick",startedWorldTick);out.addProperty("initialLoadedChunks",initialLoadedChunks);out.addProperty("serverAcknowledged",serverAcknowledged);
        if(serverAcknowledged)out.addProperty("serverLoadedChunksAtAcknowledgement",serverLoadedChunksAtAcknowledgement);
        if(complete){out.addProperty("endedWorldTick",endedWorldTick);out.addProperty("startChunkLoadedAtEnd",startChunkLoadedAtEnd);out.addProperty("targetChunkLoadedAtEnd",targetChunkLoadedAtEnd);}return out;
    }
    private static boolean chunkPresent(MinecraftClient client,BlockPos pos){return client.world.getChunkManager().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false)!=null;}
    private static JsonArray point(Vec3d point){var array=new JsonArray();array.add(point.x);array.add(point.y);array.add(point.z);return array;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
