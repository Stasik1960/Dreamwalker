package dev.dreamwalker.bloodbornedw.v124qa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import dev.dreamwalker.bloodbornedw.tool.*;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.text.Text;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Authors genuine V12.3 saved data for every old exact type/variant mapping.
 * Uses only APIs present in V12.3; no new production helper is referenced.
 */
public final class LegacyTypeSaveAuthor implements ModInitializer {
    private final JsonObject result=new JsonObject();private final JsonArray fixtures=new JsonArray();
    private JsonArray mappings;private int ticks,index,cursorX=32,cursorZ=32,rowDepth,commands,links,bounds;private boolean done;
    @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(!server.isDedicated()||done||++ticks<30)return;
        try{
            ServerWorld world=server.getOverworld();
            if(mappings==null){
                V124QaEvidence.require(V124QaEvidence.mode("legacy-author"),"Legacy author mode required");
                V124QaEvidence.require(V124QaEvidence.productionSha().equals(V124QaEvidence.baselineSha()),"Legacy fixtures must be authored by the original V12.3 production JAR");
                V124QaEvidence.require(server.getSaveProperties().getLevelName().equals("isolated-smoke-world"),"Fresh isolated test world required");
                V124QaEvidence.require(!Files.exists(server.getSavePath(WorldSavePath.ROOT).resolve("v124-fixture.json")),"Legacy fixture already exists; no overwrite");
                mappings=V124QaEvidence.mapping().getAsJsonArray("mappings");
                result.addProperty("schema","dreamwalker-v124-saved-type-fixtures-v1");result.addProperty("beforeProductionJarSha256",V124QaEvidence.productionSha());
                result.addProperty("artExpectationVersion",1);
                result.add("fixtures",fixtures);world.setTimeOfDay(6000);
                V124QaEvidence.require(server.getCommandManager().executeWithPrefix(server.getCommandSource(),"scoreboard objectives add v124_events dummy")>0,"Create independent migration event counter");
                world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0,server);
            }
            for(int n=0;n<4&&index<mappings.size();n++)fixture(world,mappings.get(index).getAsJsonObject(),index++);
            if(index<mappings.size()){if(index%32==0)write("RUNNING");return;}
            result.addProperty("oldExactStateFixtures",fixtures.size());result.addProperty("savedCommandBindings",commands);result.addProperty("savedMechanismLinks",links);result.addProperty("sourceJsonRenderBounds",bounds);
            result.addProperty("authorCollisionExport","NOT_SUPPLIED_NOT_APPLIED");result.addProperty("sourceWorldChanged",false);
            world.getServer().save(false,true,true);result.addProperty("status","PASS_LEGACY_SAVE");result.addProperty("completedFixtures",index);result.addProperty("ticks",ticks);
            V124QaEvidence.write(server.getSavePath(WorldSavePath.ROOT).resolve("v124-fixture.json"),result);done=true;write("PASS_LEGACY_SAVE");Files.writeString(Path.of("qa-normal-stop.request"),"V12.3 legacy save authored\n");
        }catch(Throwable failure){done=true;result.addProperty("failure",failure.toString());java.io.StringWriter trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));result.addProperty("stackTrace",trace.toString());write("FAIL");try{Files.writeString(Path.of("qa-normal-stop.request"),"V12.3 legacy author failed\n");}catch(Exception ignored){}}
    });}
    private void fixture(ServerWorld world,JsonObject mapping,int number){
        String oldRegistry=mapping.get("oldRegistryId").getAsString();V124QaEvidence.require(oldRegistry.startsWith("bloodborne_dw:source_")&&Registries.BLOCK.containsId(new Identifier(oldRegistry)),"Every old mapping registry must exist in V12.3: "+oldRegistry);
        CompositeRootBlock block=(CompositeRootBlock)Registries.BLOCK.get(new Identifier(oldRegistry));int variant=mapping.get("oldVariant").getAsInt();
        V124QaEvidence.require(variant<block.spec.variants.size(),"Old mapping variant must exist in V12.3: "+oldRegistry+"/"+variant);
        BlockState state=block.getDefaultState().with(CompositeRootBlock.VARIANT,variant).with(CompositeRootBlock.ROTATION,number&7).with(CompositeRootBlock.PROFILE,(number&1)==0?CompositeRootBlock.Profile.BASE:CompositeRootBlock.Profile.ALT).with(CompositeRootBlock.OPEN,block.spec.openable&&(number&2)!=0);
        NbtCompound payload=new NbtCompound();payload.putDouble(VerticalMount.KEY,number%3==0?.375:0);payload.putBoolean("MountEdited",number%3==0);
        NbtList shift=new NbtList();shift.add(NbtDouble.of(number%4==0?.25:0));shift.add(NbtDouble.of(number%4==0?.125:0));shift.add(NbtDouble.of(number%4==0?-.125:0));payload.put("SourceShift",shift);
        payload.putString("CustomEvent","retained_"+number);payload.putIntArray("CustomNumbers",new int[]{number,-number,17});payload.putUuid("LinkedInstance",UUID.nameUUIDFromBytes(("linked_"+number).getBytes(StandardCharsets.UTF_8)));
        var footprint=CompositeSourceShift.footprint(block.spec,state,payload);int minX=footprint.keySet().stream().mapToInt(Cell::x).min().orElse(0),maxX=footprint.keySet().stream().mapToInt(Cell::x).max().orElse(0),minZ=footprint.keySet().stream().mapToInt(Cell::z).min().orElse(0),maxZ=footprint.keySet().stream().mapToInt(Cell::z).max().orElse(0);
        int width=Math.max(8,maxX-minX+1),depth=Math.max(8,maxZ-minZ+1);if(cursorX+width>1024){cursorX=32;cursorZ+=rowDepth+8;rowDepth=0;}
        BlockPos root=new BlockPos(cursorX-minX,128,cursorZ-minZ);cursorX+=width+8;rowDepth=Math.max(rowDepth,depth);
        for(Cell cell:footprint.keySet())for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){BlockPos p=root.add(cell.x()+dx,cell.y(),cell.z()+dz);world.setChunkForced(p.getX()>>4,p.getZ()>>4,true);world.getChunk(p);}
        BlockPos support=root.add(block.spec.supportOffset.x(),block.spec.supportOffset.y(),block.spec.supportOffset.z());world.getChunk(support);
        if(block.spec.requiredSupport)world.setBlockState(support,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
        UUID uuid=UUID.nameUUIDFromBytes(("v124_legacy_"+oldRegistry+"_"+variant).getBytes(StandardCharsets.UTF_8));
        var placed=CompositeRuntime.place(world,root,state,uuid,null,payload);V124QaEvidence.require(placed.outcome()==TransactionCore.Outcome.COMMITTED,"Legacy ordinary server placement failed "+oldRegistry+"/"+variant+": "+placed.reason());
        CompositeBlockEntity own=(CompositeBlockEntity)world.getBlockEntity(root);payload=own.payload();
        JsonObject fixture=new JsonObject();fixture.addProperty("index",number);fixture.addProperty("oldId",mapping.get("oldId").getAsString());fixture.addProperty("newId",mapping.get("newId").getAsString());fixture.addProperty("oldRegistry",oldRegistry);fixture.addProperty("newRegistry",mapping.get("newRegistryId").getAsString());fixture.addProperty("uuid",uuid.toString());
        fixture.add("root",V124QaEvidence.array(root.getX(),root.getY(),root.getZ()));fixture.add("oldState",V124QaEvidence.state(state));fixture.add("expectedState",V124QaEvidence.expectedState(state,mapping));fixture.add("mapping",mapping.deepCopy());
        fixture.addProperty("oldPayloadNbt",payload.toString());fixture.addProperty("expectedPayloadNbt",V124QaEvidence.expectedPayload(payload,mapping).toString());fixture.addProperty("legacyCellCount",CompositeRuntime.instance(world,root,state,uuid,payload).cells().size());
        var visual=V124QaEvidence.renderBounds(block,state,payload);double[] boundsForCamera;
        if(visual.isPresent()){boundsForCamera=visual.get();fixture.add("oldBounds",V124QaEvidence.array(boundsForCamera));fixture.addProperty("oldRenderBoundsStatus","PASS_SOURCE_JSON_VERTICES");bounds++;}
        else{boundsForCamera=new double[]{0,0,0,1,1,1};fixture.addProperty("oldRenderBoundsStatus","NOT_RUN_GENERATED_OR_BUILTIN_MESH");}
        double cx=(boundsForCamera[0]+boundsForCamera[3])/2,cy=(boundsForCamera[1]+boundsForCamera[4])/2,cz=(boundsForCamera[2]+boundsForCamera[5])/2,span=Math.max(boundsForCamera[3]-boundsForCamera[0],Math.max(boundsForCamera[4]-boundsForCamera[1],boundsForCamera[5]-boundsForCamera[2]));
        double distance=Math.max(5,span*1.8);fixture.add("camera",V124QaEvidence.array(root.getX()+cx,root.getY()+cy+.25-1.62,root.getZ()+cz-distance,0,Math.toDegrees(Math.atan2(.25,distance))));
        if(commands<8){ServerPlayerEntity operator=actor(world);world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(operator.getGameProfile(),4,false));try{
                ToolTarget target=new ToolTarget(ToolTarget.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),uuid,root.asLong(),oldRegistry);
                V124QaEvidence.require(ToolCommands.save(operator,target,"time query daytime",2,3),"Save original UUID command binding");
                V124QaEvidence.require(ToolCommands.fire(operator,target),"Spend original command use before save");
                fixture.addProperty("commandOwnerUuid",operator.getUuid().toString());fixture.addProperty("commandGlobalUsed",1);fixture.addProperty("commandPersonalLimit",2);fixture.addProperty("commandGlobalLimit",3);commands++;
            }finally{world.getServer().getPlayerManager().removeFromOperators(operator.getGameProfile());ToolCommands.forgetPlayer(world.getServer(),operator.getUuid());operator.discard();}}
        if(block.spec.openable&&links<4){var lever=ObjectRegistry.TYPES.get("lever_1").create(world);lever.setPosition(root.getX()+5.5,root.getY(),root.getZ()+5.5);world.spawnEntity(lever);var target=new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),uuid,root.asLong(),oldRegistry);V124QaEvidence.require(MechanismLinks.link(lever,target),"Save actual RP lever architecture binding");fixture.addProperty("leverUuid",lever.getUuid().toString());
            ServerPlayerEntity operator=actor(world);world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(operator.getGameProfile(),4,false));try{
                String probe="v124_probe_"+number,command="scoreboard players add "+probe+" v124_events 1";
                V124QaEvidence.require(ObjectPolicies.setManual(operator,target,true),"Save legacy levers-only policy");
                V124QaEvidence.require(ObjectPolicies.setCommands(operator,target,true,List.of(command))&&ObjectPolicies.setCommands(operator,target,false,List.of(command)),"Save legacy open/close event commands");
                fixture.addProperty("eventProbe",probe);fixture.addProperty("eventCommand",command);fixture.addProperty("eventCountBefore",0);
            }finally{world.getServer().getPlayerManager().removeFromOperators(operator.getGameProfile());operator.discard();}links++;}
        fixture.addProperty("status","PASS_LEGACY_INSTALLED");fixtures.add(fixture);
    }
    private void write(String status){result.addProperty("status",status);result.addProperty("completedFixtures",index);result.addProperty("ticks",ticks);V124QaEvidence.write(Path.of("v124-native-output.json"),result);}
    private static ServerPlayerEntity actor(ServerWorld world){var p=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"V124Legacy"));p.networkHandler=new ServerPlayNetworkHandler(world.getServer(),new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> p){}@Override public void send(Packet<?> p,PacketCallbacks c){}},p);p.changeGameMode(GameMode.CREATIVE);p.getAbilities().allowModifyWorld=true;return p;}
}
