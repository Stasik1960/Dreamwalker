package dev.dreamwalker.bloodbornedw.regressionqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornedw.debug.UnifiedCreativeCatalogue;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.enums.WallShape;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

/** Diagnostic only; identical helper runs against both immutable production JARs. */
public final class WallRegressionAudit implements ModInitializer {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private int ticks; private boolean done;
    @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(done||++ticks<30)return;done=true;JsonObject report=new JsonObject();JsonArray cases=new JsonArray();
        try{
            ServerWorld world=server.getOverworld();
            ServerPlayerEntity actor=new ServerPlayerEntity(server,world,new GameProfile(UUID.nameUUIDFromBytes("wall-regression-actor".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"WallAudit"));
            actor.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> p){} @Override public void send(Packet<?> p,PacketCallbacks cb){}},actor);
            actor.changeGameMode(GameMode.CREATIVE);actor.getAbilities().allowModifyWorld=true;
            actor.setPosition(0,140,-15);actor.setYaw(0);
            for(String id:List.of("prototype_wall","prototype_wall_skin_0")){
                var item=Registries.ITEM.get(new Identifier("bloodborne_dw",id));
                ItemStack creative=UnifiedCreativeCatalogue.canonicalItems().stream().filter(s->s.isOf(item)).findFirst().orElseThrow();
                for(int yaw:new int[]{0,90,180,270}){
                    BlockPos p=new BlockPos(2+cases.size()*6,129,2);world.getChunk(p);world.getChunk(p.east());
                    for(int x=-1;x<=2;x++)for(int z=-1;z<=2;z++)world.setBlockState(p.add(x,-1,z),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                    actor.setYaw(yaw);JsonObject row=new JsonObject();row.addProperty("registryId","bloodborne_dw:"+id);row.addProperty("playerYaw",yaw);
                    row.addProperty("freshCreativeNbt",creative.getNbt()==null?null:creative.getNbt().toString());
                    place(world,actor,p,creative.copy());BlockState initial=world.getBlockState(p);require(initial.get(PrototypeWallBlock.CONNECTIONS)==PrototypeWallBlock.Connections.AUTO,"fresh AUTO");row.add("fresh",state(initial));
                    world.setBlockState(p.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);BlockState connected=world.getBlockState(p);
                    require(((PrototypeWallBlock)connected.getBlock()).nativeState(connected).get(WallBlock.EAST_SHAPE)!=WallShape.NONE,"AUTO connects to solid east");row.add("solidEast",state(connected));
                    world.setBlockState(p.east(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);BlockState disconnected=world.getBlockState(p);
                    require(((PrototypeWallBlock)disconnected.getBlock()).nativeState(disconnected).get(WallBlock.EAST_SHAPE)==WallShape.NONE,"AUTO disconnects");row.add("removedEast",state(disconnected));
                    place(world,actor,p.north(),creative.copy());BlockState ownConnected=world.getBlockState(p);
                    require(((PrototypeWallBlock)ownConnected.getBlock()).nativeState(ownConnected).get(WallBlock.NORTH_SHAPE)!=WallShape.NONE,"AUTO connects to own neighbor");row.add("ownNorth",state(ownConnected));
                    ItemStack autoPick=ownConnected.getBlock().getPickStack(world,p,ownConnected);row.addProperty("autoPickNbt",autoPick.getNbt().toString());
                    BlockPos copy=p.south();place(world,actor,copy,autoPick);require(world.getBlockState(copy).get(PrototypeWallBlock.CONNECTIONS)==PrototypeWallBlock.Connections.AUTO,"AUTO pick keeps AUTO");row.add("autoPickPlaced",state(world.getBlockState(copy)));
                    BlockState manual=initial.with(PrototypeWallBlock.CONNECTIONS,PrototypeWallBlock.Connections.MANUAL).with(PrototypeWallBlock.NORTH,WallShape.TALL).with(PrototypeWallBlock.EAST,WallShape.NONE).with(PrototypeWallBlock.SOUTH,WallShape.NONE).with(PrototypeWallBlock.WEST,WallShape.NONE).with(PrototypeWallBlock.POST,true).with(PrototypeWallBlock.ROTATION,0);
                    world.setBlockState(p,manual,Block.NOTIFY_ALL);world.setBlockState(p.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
                    require(world.getBlockState(p).get(PrototypeWallBlock.EAST)==WallShape.NONE,"MANUAL ignores east neighbor");row.add("manualEast",state(world.getBlockState(p)));
                    ItemStack manualPick=manual.getBlock().getPickStack(world,p,manual);row.addProperty("manualPickNbt",manualPick.getNbt().toString());
                    world.setBlockState(copy,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);place(world,actor,copy,manualPick);
                    world.setBlockState(copy.east(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);BlockState copied=world.getBlockState(copy);
                    require(copied.get(PrototypeWallBlock.CONNECTIONS)==PrototypeWallBlock.Connections.MANUAL&&copied.get(PrototypeWallBlock.EAST)==WallShape.NONE,"picked gallery remains MANUAL");row.add("manualPickPlaced",state(copied));
                    row.addProperty("collisionBoxes",copied.getCollisionShape(world,copy).getBoundingBoxes().toString());row.addProperty("status","PASS");cases.add(row);
                }
            }
            JsonArray nativeDefaults=new JsonArray();for(ItemStack stack:UnifiedCreativeCatalogue.canonicalItems())if(stack.getItem() instanceof BlockItem b&&Registries.ITEM.getId(stack.getItem()).getNamespace().equals("bloodborne_dw")&&!Registries.ITEM.getId(stack.getItem()).getPath().startsWith("source_")){
                JsonObject row=new JsonObject();row.addProperty("item",Registries.ITEM.getId(stack.getItem()).toString());row.addProperty("itemNbt",stack.getNbt()==null?null:stack.getNbt().toString());row.add("defaultState",state(b.getBlock().getDefaultState()));nativeDefaults.add(row);
            }
            report.add("oldCreativeBlockDefaults",nativeDefaults);report.addProperty("status","PASS");
        }catch(Throwable failure){report.addProperty("status","FAIL");report.addProperty("failure",failure.toString());}
        report.add("cases",cases);report.addProperty("method","Actual server BlockItem placement, neighbor updates and pick NBT; fresh independent test world; no production/source gallery writes.");
        try{Files.writeString(Path.of("wall-regression-output.json"),JSON.toJson(report)+"\n");Files.writeString(Path.of("qa-normal-stop.request"),"diagnostic completed\n");}catch(Exception failure){throw new RuntimeException(failure);}
    });}
    private static void place(ServerWorld w,ServerPlayerEntity actor,BlockPos p,ItemStack stack){
        actor.setStackInHand(Hand.MAIN_HAND,stack);ActionResult result=stack.getItem().useOnBlock(new ItemUsageContext(actor,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(p.getX()+.5,p.getY(),p.getZ()+.5),Direction.UP,p.down(),false)));
        require(result.isAccepted()&&w.getBlockState(p).getBlock() instanceof PrototypeWallBlock,"ordinary item placement "+p+" "+result);
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static JsonObject state(BlockState state){JsonObject row=new JsonObject();row.addProperty("id",Registries.BLOCK.getId(state.getBlock()).toString());for(var e:state.getEntries().entrySet())row.addProperty(e.getKey().getName(),name(e.getKey(),e.getValue()));return row;}
    @SuppressWarnings({"unchecked","rawtypes"})private static String name(Property p,Comparable v){return p.name(v);}
}
