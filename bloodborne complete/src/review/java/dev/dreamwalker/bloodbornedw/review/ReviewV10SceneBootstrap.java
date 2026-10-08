package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornerp.object.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.*;
import net.minecraft.server.network.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;

/** Fresh-world fixture authoring, separate from actual GUI/packet acceptance. Never installed in production. */
public final class ReviewV10SceneBootstrap implements ModInitializer {
    private static final BlockPos REMOTE=new BlockPos(800,64,64);
    private static final ChunkTicketType<BlockPos> AUTHOR_TICKET=ChunkTicketType.create("dw_v10_fixture_author",Comparator.comparingLong(BlockPos::asLong),200);
    private static final Map<MinecraftServer,Integer> PENDING=new WeakHashMap<>();
    private static final Map<MinecraftServer,RemoteAwait> REMOTE_PENDING=new WeakHashMap<>();
    private record PlacementAttempt(String kind,String id,BlockPos root,Box lookup,Set<UUID> before,JsonObject request,JsonObject row){}
    private static final class RemoteAwait {
        final JsonObject report;final JsonArray fixtures,objects,samples;final ServerPlayerEntity player;final PlacementAttempt attempt;int ticks;
        RemoteAwait(JsonObject report,JsonArray fixtures,JsonArray objects,JsonArray samples,ServerPlayerEntity player,PlacementAttempt attempt){this.report=report;this.fixtures=fixtures;this.objects=objects;this.samples=samples;this.player=player;this.attempt=attempt;}
    }
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{Path path=Path.of("review-v10-scene-input.json");if(!Files.isRegularFile(path))return;var world=server.getOverworld();boolean safe=false;try{var m=JsonParser.parseString(Files.readString(path)).getAsJsonObject();safe="FRESH_FLAT_ISOLATED_WORLD_ONLY".equals(m.get("authoringGuard").getAsString())&&"V10".equals(m.get("revision").getAsString())&&server.getSaveProperties().getLevelName().equals(m.get("allowedLevelName").getAsString())&&world.getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator&&!Files.exists(Path.of("review-v10-scene-output.json"));}catch(Exception ignored){}if(safe){world.getChunkManager().addTicket(AUTHOR_TICKET,new ChunkPos(REMOTE),2,REMOTE);world.getChunk(REMOTE);}PENDING.put(server,0);});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(REMOTE_PENDING.containsKey(server)){pollRemote(server);return;}Integer age=PENDING.get(server);if(age==null)return;if(age<5){PENDING.put(server,age+1);return;}PENDING.remove(server);try{author(server);}finally{if(!REMOTE_PENDING.containsKey(server))removeSetupTicket(server);}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{PENDING.remove(server);RemoteAwait waiting=REMOTE_PENDING.remove(server);if(waiting!=null){waiting.report.addProperty("status","FAIL_V10_SCENE_AUTHORING");waiting.report.addProperty("failure","Server stopped before remote post-spawn tracking was verified");waiting.player.discard();writeReport(waiting.report);}});
    }
    private static void author(MinecraftServer server){
        Path input=Path.of("review-v10-scene-input.json"),output=Path.of("review-v10-scene-output.json");if(!Files.isRegularFile(input))return;
        JsonObject report=new JsonObject();JsonArray fixtures=new JsonArray(),objects=new JsonArray(),samples=new JsonArray();report.addProperty("schema","dw-review-v10-scene-output-v1");report.addProperty("status","RUNNING");report.addProperty("revision","V10");report.addProperty("manualVisualAcceptance","NOT_RUN");report.add("fixtures",fixtures);report.add("objects",objects);report.add("architectureSamples",samples);ServerPlayerEntity player=null;boolean deferred=false;
        try{
            JsonObject marker=JsonParser.parseString(Files.readString(input)).getAsJsonObject();ServerWorld world=server.getOverworld();
            require(marker.get("authoringGuard").getAsString().equals("FRESH_FLAT_ISOLATED_WORLD_ONLY"),"Fresh isolated guard required");require(marker.get("revision").getAsString().equals("V10"),"Explicit V10 marker required");
            require(server.getSaveProperties().getLevelName().equals(marker.get("allowedLevelName").getAsString())&&world.getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator,"Wrong world name or non-flat fixture");require(!Files.exists(output),"Authoring output already exists");
            report.addProperty("markerSha256",ReviewV10ProofSupport.sha(Files.readAllBytes(input)));ReviewV10ProofSupport.artifact(marker,report);report.addProperty("sceneId",marker.get("sceneId").getAsString());report.addProperty("levelName",server.getSaveProperties().getLevelName());
            report.addProperty("authorDeferredServerTicks",5);report.addProperty("remoteSetupTicketScope","single exact chunk(50,4), radius2 only during marker-author setup; removed in callback finally, never forced/persisted and not a LampService travel ticket");
            require(!marker.has("baseScene"),"V10 scene is a fresh bounded stand; legacy scene inheritance is forbidden");
            // Only explicit fresh flat Y63 grass/dirt/AIR with no BE may become the fixture foundation.
            for(int x=-96;x<=96;x++)for(int z=-24;z<=96;z++){BlockPos pos=new BlockPos(x,63,z);world.getChunk(pos);require(freshFoundation(world,pos),"Foundation would overwrite non-fixture state");world.setBlockState(pos,Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_ALL);}
            for(int x=-66;x<=-62;x++)for(int z=62;z<=66;z++){BlockPos pos=new BlockPos(x,99,z);world.getChunk(pos);require(world.getBlockState(pos).isAir(),"Temporary test pad native cell already occupied");world.setBlockState(pos,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
            player=new ServerPlayerEntity(server,world,new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:ReviewV10Author".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"ReviewV10Author")){@Override public boolean hasPermissionLevel(int level){return level<=4;}};
            player.networkHandler=new ServerPlayNetworkHandler(server,new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}},player);player.changeGameMode(GameMode.CREATIVE);player.getAbilities().allowModifyWorld=true;
            if(marker.has("fixtures")){for(JsonElement value:marker.getAsJsonArray("fixtures"))place(world,player,value.getAsJsonObject(),fixtures,objects);}
            else{
                for(Object[] entry:new Object[][]{{"lamp_A","hunterlamp",60},{"lamp_B","hunterlamp",72},{"lamp_C","hunterlamp",84},{"lever_1","lever_1",-32},{"lever_2","lever_2",-24},{"lever_3","lever_1",-16},{"rp_door","door_1",8},{"wood_gate","wood_gate",24}})place(world,player,request((String)entry[0],"RP",(String)entry[1],(int)entry[2],64,-16),fixtures,objects);
                place(world,player,request("architecture_door","ARCHITECTURE","prototype_double_door",-8,64,-16),fixtures,objects);
                place(world,player,request("accepted_rp_tree","RP","tree1",-76,64,32),fixtures,objects);
            }
            place(world,player,request("rp_ladder","RP","ladder",40,64,16),fixtures,objects);
            place(world,player,request("rp_stairs","RP","stairs",64,64,16),fixtures,objects);
            place(world,player,request("diag_rp_door","RP","door_2",8,64,16),fixtures,objects);
            String[] paths={"prototype_double_door","prototype_wall","prototype_roof","prototype_thin_window","prototype_tree","prototype_ladder","prototype_wood_window","prototype_glass_window_02","prototype_wall_skin_0","prototype_wall_skin_1","prototype_wall_skin_2","prototype_wall_skin_3","prototype_wall_skin_4","prototype_wall_skin_5","prototype_wall_skin_7","prototype_ladder_art_1","prototype_ladder_art_2","prototype_glass_window_03"};
            String[] types={"90001","90002","90003","90004","90005","90006","90007","90010","90011","90012","90013","90014","90015","90016","90017","90018","90019","90020"};
            for(int i=0;i<paths.length;i++){
                BlockPos root=new BlockPos(-60+(i%6)*24,64,40+(i/6)*20);JsonObject requested=request("sample_"+types[i],"ARCHITECTURE",paths[i],root.getX(),root.getY(),root.getZ());requested.addProperty("sneaking",paths[i].contains("window"));place(world,player,requested,fixtures,objects);
                double amount=Set.of("90002","90003","90006","90020").contains(types[i])?2:(i%2==0?.125:-.125);player.setPosition(root.getX()+.5,64,root.getZ()-3);
                UUID owner=VerticalMount.owner(world,root).instanceId();require(VerticalMount.setOffset(world,root,amount,player),"Author-only sample height setup refused: "+paths[i]);require(VerticalMount.owner(world,root).instanceId().equals(owner),"Sample height setup replaced UUID");
                JsonObject sample=new JsonObject();sample.add("root",position(root));sample.addProperty("expectedType",types[i]);sample.addProperty("expectedOffset",amount);sample.addProperty("registry","bloodborne_dw:"+paths[i]);sample.addProperty("uuid",owner.toString());sample.addProperty("setupPath","marker-only ordinary server item placement then VerticalMount.setOffset; NOT a GUI height proof");samples.add(sample);
            }
            authorSourceShowcase(world,player,report);
            authorItemChests(world,report,paths);
            server.getCommandManager().getDispatcher().execute("scoreboard objectives add dw_v10_gui dummy",server.getCommandSource());server.getCommandManager().getDispatcher().execute("scoreboard players set event_counter dw_v10_gui 0",server.getCommandSource());
            server.getCommandManager().getDispatcher().execute("gamerule spawnRadius 0",server.getCommandSource());server.getCommandManager().getDispatcher().execute("setworldspawn 0 64 -24",server.getCommandSource());
            report.addProperty("freshNativeTestPad",true);report.add("rpTestPad",position(new BlockPos(-64,100,64)));report.addProperty("actualGuiAndClientPackets","NOT_RUN_AUTHORING_ONLY");
            // New remote spawns enter the loaded entity lookup on a later world tick. Keep the
            // exact temporary setup ticket until that real callback, never synthesize identity.
            for(int x=798;x<=802;x++)for(int z=62;z<=66;z++){BlockPos pos=new BlockPos(x,63,z);world.getChunk(pos);require(freshFoundation(world,pos),"Remote destination floor occupied");world.setBlockState(pos,Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_ALL);}
            PlacementAttempt remote=beginPlacement(world,player,request("lamp_D","RP","hunterlamp",800,64,64));
            REMOTE_PENDING.put(server,new RemoteAwait(report,fixtures,objects,samples,player,remote));deferred=true;
        }catch(Throwable failure){report.addProperty("status","FAIL_V10_SCENE_AUTHORING");report.addProperty("failureClass",failure.getClass().getName());report.addProperty("failure",String.valueOf(failure.getMessage()));}
        finally{if(!deferred){if(player!=null)player.discard();writeReport(report);}}
    }
    private static void place(ServerWorld world,ServerPlayerEntity player,JsonObject request,JsonArray fixtures,JsonArray objects){
        finishPlacement(world,beginPlacement(world,player,request),fixtures,objects);
    }
    private static PlacementAttempt beginPlacement(ServerWorld world,ServerPlayerEntity player,JsonObject request){
        String kind=request.get("kind").getAsString(),id=request.get("registry").getAsString();BlockPos root=readPosition(request.getAsJsonArray("root"));world.getChunk(root);
        Box lookup=new Box(root.getX()-64,world.getBottomY(),root.getZ()-64,root.getX()+65,world.getTopY(),root.getZ()+65);Set<UUID> before=new HashSet<>();for(RpObjectEntity entity:world.getEntitiesByClass(RpObjectEntity.class,lookup,e->true))before.add(entity.getUuid());Item item=Registries.ITEM.get(new Identifier(kind.equals("RP")?"bloodborne_rp":"bloodborne_dw",id+(kind.equals("RP")?"_placer":"")));require(item!=Items.AIR,"Unknown fixture item "+id);ItemStack stack=item.getDefaultStack();
        player.setPosition(root.getX()+.5,root.getY(),root.getZ()-5);player.setYaw(kind.equals("RP")?270:0);player.setPitch(0);player.setSneaking(request.has("sneaking")&&request.get("sneaking").getAsBoolean());player.setStackInHand(Hand.MAIN_HAND,stack);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
        ActionResult used=item.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5),Direction.UP,root.down(),false)));require(used.isAccepted(),"Ordinary fixture item refused "+id+" at "+root);
        JsonObject row=request.deepCopy();row.addProperty("placementPath","ordinary Item.useOnBlock in marker-guarded server authoring (not clientGUIproof)");row.addProperty("itemAction",used.toString());
        return new PlacementAttempt(kind,id,root,lookup,before,request,row);
    }
    private static void finishPlacement(ServerWorld world,PlacementAttempt attempt,JsonArray fixtures,JsonArray objects){
        String kind=attempt.kind(),id=attempt.id();BlockPos root=attempt.root();Box lookup=attempt.lookup();Set<UUID> before=attempt.before();JsonObject request=attempt.request(),row=attempt.row();
        if(kind.equals("RP")){
            List<RpObjectEntity> added=new ArrayList<>();int allAdded=0;for(RpObjectEntity rp:world.getEntitiesByClass(RpObjectEntity.class,lookup,e->true))if(!before.contains(rp.getUuid())){allAdded++;Vec3d origin=expectedRpOrigin(id,root,rp);if(rp.assetId().equals(ObjectRegistry.canonicalId(id))&&rp.getPos().squaredDistanceTo(origin)<1e-12)added.add(rp);}int tickableAdded=0;for(Entity e:world.iterateEntities())if(e instanceof RpObjectEntity&&!before.contains(e.getUuid()))tickableAdded++;require(added.size()==1,"Expected one exact fixture entity asset="+id+" exactTypePoseCount="+added.size()+" spatialNewCount="+allAdded+" tickableCount="+tickableAdded+" index="+RpObjectIndex.metrics(world));RpObjectEntity object=added.get(0);row.addProperty("lookupPath","bounded loaded spatial getEntitiesByClass; newly observed UUID + exact canonical asset + actual authored placement origin");row.addProperty("newSpatialCandidates",allAdded);row.addProperty("tickableAddedCount",tickableAdded);var entry=DebugCatalogue.entry(Registries.ENTITY_TYPE.getId(object.getType()));object.setCustomName(Text.literal(request.get("key").getAsString()+" "+entry.name()+" ["+entry.temporaryId()+"]"));object.setCustomNameVisible(true);row.addProperty("typeId",entry.temporaryId());row.addProperty("uuid",object.getUuidAsString());row.addProperty("entityRegistryId",Registries.ENTITY_TYPE.getId(object.getType()).toString());row.addProperty("yaw",object.getYaw());row.addProperty("verticalOffset",object.verticalOffset());row.add("actualPos",vector(object.getPos()));
        }else{
            require(world.getBlockEntity(root) instanceof CompositeBlockEntity,"Composite author did not save root BE");CompositeBlockEntity be=(CompositeBlockEntity)world.getBlockEntity(root);require(be.resident()!=null,"Fixture owner absent");row.addProperty("typeId",DebugCatalogue.entry(world.getBlockState(root)).temporaryId());row.addProperty("uuid",be.resident().instanceId().toString());row.addProperty("registryId",Registries.BLOCK.getId(world.getBlockState(root).getBlock()).toString());row.addProperty("state",world.getBlockState(root).toString());objects.add(row.deepCopy());
        }label(world,root,row.get("typeId").getAsString(),request.get("key").getAsString(),id);fixtures.add(row);
    }
    private static void pollRemote(MinecraftServer server){
        RemoteAwait waiting=REMOTE_PENDING.get(server);if(waiting==null)return;waiting.ticks++;
        if(waiting.ticks<2)return;ServerWorld world=server.getOverworld();PlacementAttempt attempt=waiting.attempt;
        List<RpObjectEntity> exact=world.getEntitiesByClass(RpObjectEntity.class,attempt.lookup(),rp->!attempt.before().contains(rp.getUuid())&&rp.assetId().equals(ObjectRegistry.canonicalId(attempt.id()))&&rp.getPos().squaredDistanceTo(expectedRpOrigin(attempt.id(),attempt.root(),rp))<1e-12);
        if(exact.isEmpty()&&waiting.ticks<100)return;
        try{
            finishPlacement(world,attempt,waiting.fixtures,waiting.objects);
            JsonObject distant=waiting.fixtures.get(waiting.fixtures.size()-1).getAsJsonObject();RpObjectEntity remote=exact.get(0);
            require(world.getEntity(remote.getUuid())==remote,"Remote exact loaded UUID lookup disagrees with actual spatial entity");
            Vec3d target=RpObjectSelection.nearestPoint(remote,waiting.player.getEyePos());Vec3d delta=target.subtract(waiting.player.getEyePos());waiting.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));waiting.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            require(dev.dreamwalker.bloodbornerp.lamp.LampService.register(waiting.player,"QA D remote"),"Explicit remote node registration setup failed");
            distant.addProperty("registrationScope","author-only explicit admin registration after actual post-spawn loaded tracking; link/travel must be independently proven through actual GUI");distant.addProperty("postSpawnLoadedCaptureTicks",waiting.ticks);
            waiting.report.addProperty("remotePostSpawnWaitTicks",waiting.ticks);waiting.report.addProperty("remoteLoadedUuidCaptureVerified",true);waiting.report.addProperty("fixtureCount",waiting.fixtures.size());waiting.report.addProperty("architectureSampleCount",waiting.samples.size());waiting.report.addProperty("status","PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN");
        }catch(Throwable failure){waiting.report.addProperty("status","FAIL_V10_SCENE_AUTHORING");waiting.report.addProperty("failureClass",failure.getClass().getName());waiting.report.addProperty("failure",String.valueOf(failure.getMessage()));waiting.report.addProperty("remotePostSpawnWaitTicks",waiting.ticks);}
        finally{REMOTE_PENDING.remove(server);waiting.player.discard();removeSetupTicket(server);waiting.report.addProperty("remoteSetupTicketRemoved",true);writeReport(waiting.report);}
    }
    private static void removeSetupTicket(MinecraftServer server){server.getOverworld().getChunkManager().removeTicket(AUTHOR_TICKET,new ChunkPos(REMOTE),2,REMOTE);}
    private static void writeReport(JsonObject report){try{ReviewV10ProofSupport.write(Path.of("review-v10-scene-output.json"),report);}catch(Exception failure){throw new RuntimeException(failure);}}
    private static void authorSourceShowcase(ServerWorld world,ServerPlayerEntity player,JsonObject report){
        BlockPos visual=new BlockPos(-84,64,60),physical=visual.south();require(world.getBlockState(visual).isAir()&&world.getBlockState(physical).isAir(),"Source template cells must be fresh AIR");
        var bee=Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.FACING,Direction.NORTH).with(BeehiveBlock.HONEY_LEVEL,1);var ladder=Blocks.LADDER.getDefaultState().with(LadderBlock.FACING,Direction.SOUTH);world.setBlockState(visual,bee,Block.NOTIFY_ALL);world.setBlockState(physical,ladder,Block.NOTIFY_ALL);
        var expected=world.getBlockEntity(visual).createNbtWithId();var result=SourceLadderRuntime.install(world,new SourceLadderRuntime.Installation(visual,physical,bee,ladder,expected,null,0,PrototypeLadderBlock.Profile.BASE,null),null);require(result.committed(),"Recognized two-cell source-template install refused: "+result.reason());
        JsonObject row=new JsonObject();row.add("root",position(physical));row.add("backing",position(visual));row.addProperty("uuid",result.owner().toString());row.addProperty("sourceClone",true);row.addProperty("variant",0);row.addProperty("scope","new-world authored recognized beehive honey1 + opposite vanilla ladder template; not an original-world migration or source-coordinate proof");report.add("sourceLadderShowcase",row);label(world,physical,"90006","SOURCE TEMPLATE","prototype_ladder");
    }
    private static void authorItemChests(ServerWorld world,JsonObject report,String[] architecture){
        List<Identifier> items=new ArrayList<>();for(String id:ObjectRegistry.TYPES.keySet())if(ObjectRegistry.isCanonicalPlacementItem(id))items.add(new Identifier("bloodborne_rp",id+"_placer"));for(String id:architecture)items.add(new Identifier("bloodborne_dw",id));items.add(new Identifier("bloodborne_dw","composite_builder"));items.add(new Identifier("bloodborne_dw","builder_tool"));
        JsonArray chests=new JsonArray();for(int offset=0,n=0;offset<items.size();offset+=27,n++){
            BlockPos pos=new BlockPos(-88+n*4,64,-4);require(world.getBlockState(pos).isAir(),"Item chest position occupied");world.setBlockState(pos,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);ChestBlockEntity chest=(ChestBlockEntity)world.getBlockEntity(pos);chest.setCustomName(Text.literal("V10 catalogue "+(n+1)+" RP910xx / ARCH900xx"));JsonObject row=new JsonObject();row.add("root",position(pos));row.addProperty("name","V10 ordinary catalogue "+(n+1));JsonArray entries=new JsonArray();for(int slot=0;slot<27&&offset+slot<items.size();slot++){Identifier id=items.get(offset+slot);Item item=Registries.ITEM.get(id);require(item!=Items.AIR,"Catalogue chest item missing: "+id);chest.setStack(slot,item.getDefaultStack());entries.add(id.toString());}chest.markDirty();row.add("items",entries);chests.add(row);label(world,pos,"900xx/910xx","CATALOGUE "+(n+1),"ordinary items");
        }report.add("signedItemChests",chests);
    }
    private static JsonObject request(String key,String kind,String registry,int x,int y,int z){JsonObject row=new JsonObject();row.addProperty("key",key);row.addProperty("kind",kind);row.addProperty("registry",registry);row.add("root",position(new BlockPos(x,y,z)));return row;}
    private static Vec3d expectedRpOrigin(String id,BlockPos root,RpObjectEntity entity){if(!RpObjectGeometry.custom(ObjectRegistry.canonicalId(id)))return new Vec3d(root.getX(),ObjectRegistry.placementY(id,root.getY()),root.getZ());Vec3d anchor=RpObjectGeometry.placementAnchor(ObjectRegistry.canonicalId(id),Direction.UP,entity.isOpen()).multiply(entity.asset().scale()*entity.objectScale());return new Vec3d(root.getX()+.5,root.getY(),root.getZ()+.5).subtract(RpObjectGeometry.rotate(anchor,ObjectRegistry.placementYaw(270)));}
    private static BlockPos readPosition(JsonArray value){require(value.size()==3,"3D fixture coordinates required");BlockPos pos=new BlockPos(value.get(0).getAsInt(),value.get(1).getAsInt(),value.get(2).getAsInt());boolean remote=pos.equals(new BlockPos(800,64,64));require(remote||Math.abs(pos.getX())<=96&&Math.abs(pos.getZ())<=96&&pos.getY()>=64&&pos.getY()<=100,"Fixture outside bounded QA stand or exact authorized remote destination");return pos;}
    private static JsonArray position(BlockPos pos){JsonArray value=new JsonArray();value.add(pos.getX());value.add(pos.getY());value.add(pos.getZ());return value;}
    private static JsonArray vector(Vec3d pos){JsonArray value=new JsonArray();value.add(pos.x);value.add(pos.y);value.add(pos.z);return value;}
    private static boolean freshFoundation(ServerWorld world,BlockPos pos){var state=world.getBlockState(pos);return pos.getY()==63&&world.getBlockEntity(pos)==null&&(state.isAir()||state.isOf(Blocks.SMOOTH_STONE)||state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.DIRT));}
    private static void label(ServerWorld world,BlockPos root,String type,String key,String registry){BlockPos pos=new BlockPos(root.getX(),64,root.getZ()-4);world.getChunk(pos);require(world.getBlockState(pos).isAir()&&freshFoundation(world,pos.down()),"Label would overwrite non-fixture state");world.setBlockState(pos.down(),Blocks.SMOOTH_STONE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(pos,Blocks.OAK_SIGN.getDefaultState(),Block.NOTIFY_ALL);SignBlockEntity sign=(SignBlockEntity)world.getBlockEntity(pos);SignText text=new SignText().withMessage(0,Text.literal("TEMP ["+type+"]")).withMessage(1,Text.literal(key)).withMessage(2,Text.literal(registry)).withMessage(3,Text.literal(root.getX()+" "+root.getY()+" "+root.getZ()));sign.setText(text,true);sign.setText(text,false);sign.markDirty();world.updateListeners(pos,world.getBlockState(pos),world.getBlockState(pos),Block.NOTIFY_ALL);}
    private static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
}
