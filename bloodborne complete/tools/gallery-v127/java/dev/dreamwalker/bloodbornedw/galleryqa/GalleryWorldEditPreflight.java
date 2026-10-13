package dev.dreamwalker.bloodbornedw.galleryqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.*;
import net.minecraft.world.GameMode;

/** Cheap opt-in command preflight; final acceptance still needs the real client. */
public final class GalleryWorldEditPreflight implements ModInitializer {
    private int ticks;private boolean done;
    @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(done||!Files.isRegularFile(Path.of("gallery-worldedit-preflight.request"))||++ticks<40)return;
        done=true;JsonObject value=new JsonObject();ServerPlayerEntity actor=null;
        try{
            actor=new ServerPlayerEntity(server,server.getOverworld(),new GameProfile(UUID.nameUUIDFromBytes("GalleryWeQA".getBytes()),"GalleryWeQA")){
                @Override public boolean hasPermissionLevel(int level){return level<=4;}};
            actor.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND){
                @Override public void send(Packet<?> packet){}
                @Override public void send(Packet<?> packet,PacketCallbacks callbacks){}
            },actor);
            actor.changeGameMode(GameMode.CREATIVE);
            value=GalleryWorldEditQa.run(server,actor);value.addProperty("actorScope","SIMULATED_NATIVE_SERVER_PLAYER_PREFLIGHT_ONLY");
        }catch(Throwable failure){value.addProperty("status","FAIL_PREFLIGHT");value.addProperty("failure",failure.toString());failure.printStackTrace();
        }finally{
            if(actor!=null)actor.discard();
            try{Files.writeString(Path.of("gallery-worldedit-preflight.json"),new GsonBuilder().setPrettyPrinting().create().toJson(value));Files.writeString(Path.of("qa-normal-stop.request"),"preflight complete\n");}catch(Exception failure){throw new RuntimeException(failure);}
        }
    });}
}
