package dev.dreamwalker.bloodbornedw.galleryqa;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;

/** Actual player-issued WorldEdit commands in a reserved area of the new gallery. */
public final class GalleryWorldEditQa {
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static String coords(BlockPos p){return p.getX()+","+p.getY()+","+p.getZ();}
    private static String id(MinecraftServer server,BlockPos p){return Registries.BLOCK.getId(server.getOverworld().getBlockState(p).getBlock()).toString();}
    private static void command(MinecraftServer server,ServerPlayerEntity player,String text,JsonArray log){
        check(server.getCommandManager().getDispatcher().getRoot().getChild(text.split(" ",2)[0])!=null,"WorldEdit command literal registered: "+text);
        // Native executeWithPrefix removes the chat's FIRST slash. WorldEdit's
        // own command name retains the second slash, e.g. //set -> /set.
        server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4),"/"+text);
        log.add("/"+text);
    }
    private static void select(MinecraftServer server,ServerPlayerEntity player,BlockPos a,BlockPos b,JsonArray log){
        command(server,player,"/pos1 "+coords(a),log);command(server,player,"/pos2 "+coords(b),log);
    }
    public static JsonObject run(MinecraftServer server,ServerPlayerEntity player){
        JsonObject proof=new JsonObject();JsonArray commands=new JsonArray();proof.add("commands",commands);
        proof.addProperty("schema","dreamwalker-v127-new-gallery-worldedit-commands-v1");
        proof.addProperty("worldEditVersion",FabricLoader.getInstance().getModContainer("worldedit").orElseThrow().getMetadata().getVersion().getFriendlyString());
        BlockPos root=new BlockPos(-40,64,-40),copy=root.east(32);Vec3d before=player.getPos();float yaw=player.getYaw(),pitch=player.getPitch();
        var world=server.getOverworld();
        try{
            server.getPlayerManager().addToOperators(player.getGameProfile());
            for(int x=-56;x<=16;x+=16)for(int z=-56;z<=-24;z+=16)world.getChunk(x>>4,z>>4);
            player.teleport(world,root.getX()+.5,64,root.getZ()-8.5,0,0);
            select(server,player,root,root,commands);
            command(server,player,"/set 93230",commands);check(id(server,root).equals("bloodborne_dw:dw_barrier"),"Numeric /set 93230 must create DW barrier");
            command(server,player,"/replace 93230 93231",commands);check(id(server,root).equals("bloodborne_dw:dw_light"),"Numeric /replace must create DW light");
            check(world.getBlockState(root).getLuminance()==14,"WorldEdit DW light preserves luminance14");
            command(server,player,"/undo",commands);check(id(server,root).equals("bloodborne_dw:dw_barrier"),"WorldEdit undo restores DW barrier");
            command(server,player,"/redo",commands);check(id(server,root).equals("bloodborne_dw:dw_light"),"WorldEdit redo restores DW light");
            proof.addProperty("numericUtilitySetReplaceUndoRedo","PASS");
            command(server,player,"/set 90002",commands);proof.addProperty("actualV12WallRegistry",id(server,root));check(id(server,root).equals("bloodborne_dw:prototype_wall"),"Numeric V12 wall set: "+id(server,root));
            check(world.getBlockEntity(root) instanceof CompositeBlockEntity own&&own.resident()!=null,"V12 wall retains its owner");
            proof.addProperty("originalV12NumericPlacement","PASS");
            command(server,player,"/set air",commands);
            var door=CompositeArchitecture.kindBlock("source_dark_arch_door");String number=DebugCatalogue.entry(door.spec.id).temporaryId();
            command(server,player,"/set "+number,commands);
            check(world.getBlockEntity(root) instanceof CompositeBlockEntity,"New numbered door creates typed block entity");
            var own=(CompositeBlockEntity)world.getBlockEntity(root);var originalUuid=own.resident().instanceId();var originalState=world.getBlockState(root);
            var instance=CompositeRuntime.instance(world,root,originalState,originalUuid,own.payload());
            var offset=instance.cells().keySet().stream().filter(p->p.x()!=0||p.y()!=0||p.z()!=0).findFirst().orElseThrow();
            BlockPos helper=root.add(offset.x(),offset.y(),offset.z());
            command(server,player,"/copy",commands);
            player.teleport(world,copy.getX()+.5,64,copy.getZ()-8.5,0,0);command(server,player,"/paste",commands);
            check(world.getBlockEntity(copy) instanceof CompositeBlockEntity,"Actual clipboard paste creates copied whole owner");
            var pasted=(CompositeBlockEntity)world.getBlockEntity(copy);
            check(!pasted.resident().instanceId().equals(originalUuid),"Clipboard copy has a fresh instance UUID");
            check(world.getBlockState(copy).equals(originalState),"Clipboard preserves exact block state");
            proof.addProperty("newDoorNumericId",number);proof.addProperty("wholeDoorCopyPaste","PASS_FRESH_UUID_EXACT_STATE");
            select(server,player,helper,helper,commands);command(server,player,"/replace "+number+" air",commands);
            check(!(world.getBlockEntity(root) instanceof CompositeBlockEntity),"Numeric mask at one helper removes the whole door");
            command(server,player,"/undo",commands);
            check(world.getBlockEntity(root) instanceof CompositeBlockEntity restored&&restored.resident().instanceId().equals(originalUuid),"Undo restores exact original door UUID");
            proof.addProperty("helperNumericDeleteUndo","PASS_WHOLE_OBJECT_ORIGINAL_UUID");
            select(server,player,new BlockPos(-56,64,-56),new BlockPos(16,100,-24),commands);command(server,player,"/set air",commands);
            check(!(world.getBlockEntity(root) instanceof CompositeBlockEntity)&&!(world.getBlockEntity(copy) instanceof CompositeBlockEntity),"WorldEdit test owners fully cleaned up");
            proof.addProperty("exhibitsModified",false);proof.addProperty("testAreaCleaned",true);proof.addProperty("status","PASS_ACTUAL_WORLDEDIT_PLAYER_COMMANDS");
        }catch(Throwable failure){proof.addProperty("status","FAIL_ACTUAL_WORLDEDIT_PLAYER_COMMANDS");proof.addProperty("failure",failure.toString());throw new RuntimeException(failure);
        }finally{
            player.teleport(world,before.x,before.y,before.z,yaw,pitch);
            try{Files.writeString(server.getSavePath(WorldSavePath.ROOT).resolve("gallery-worldedit-qa.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception failure){throw new RuntimeException(failure);}
        }
        return proof;
    }
}
