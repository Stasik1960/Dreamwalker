package dev.dreamwalker.bloodbornedw.review;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.GameRules;

/** Optional QA-only authoring. No QA entrypoint is present in the production JAR. */
public final class ReviewSceneBootstrap implements ModInitializer {
    private static final Set<String> KINDS=Set.of("prototype_double_door","prototype_wood_window","prototype_thin_window",
        "prototype_tree","prototype_roof","prototype_wall","prototype_ladder","prototype_glass_window_02",
        "prototype_wall_skin_0","prototype_wall_skin_1","prototype_wall_skin_2","prototype_wall_skin_3","prototype_wall_skin_4","prototype_wall_skin_5","prototype_wall_skin_7",
        "prototype_ladder_art_1","prototype_ladder_art_2");
    private record NativeSnapshot(BlockPos pos,BlockState state,BlockEntity instance,NbtCompound nbt) {}
    @Override public void onInitialize(){ServerLifecycleEvents.SERVER_STARTED.register(ReviewSceneBootstrap::author);ServerLifecycleEvents.SERVER_STARTED.register(SourceReviewBootstrap::author);}
    private static void author(MinecraftServer server){
        Path input=Path.of("review-scene-input.json"),output=Path.of("review-scene-output.json");
        if(!Files.isRegularFile(input))return;
        JsonObject report=new JsonObject();report.addProperty("schema","dreamwalker-isolated-review-scene-v1");
        report.addProperty("production_qa_included",false);report.addProperty("visual_acceptance","NOT_RUN");
        JsonArray placed=new JsonArray();report.add("objects",placed);ServerPlayerEntity player=null;
        try {
            JsonObject document=JsonParser.parseString(Files.readString(input)).getAsJsonObject();
            if(document.get("schemaVersion").getAsInt()!=1||!document.get("authoringGuard").getAsString().equals("FRESH_FLAT_ISOLATED_WORLD_ONLY"))throw new IllegalArgumentException("Explicit isolated fresh-flat authoring guard required");
            ServerWorld world=server.getOverworld();
            if(!(world.getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator))throw new IllegalArgumentException("Review scene authoring requires a fresh flat QA world");
            if(!server.getSaveProperties().getLevelName().equals(document.get("allowedLevelName").getAsString()))throw new IllegalArgumentException("Unexpected QA world name");
            if(Files.exists(output))throw new IllegalArgumentException("Scene output already exists; authoring cannot run twice");
            report.addProperty("sceneId",document.get("sceneId").getAsString());
            report.addProperty("levelName",server.getSaveProperties().getLevelName());
            // Pure memory observations in the actual loaded-mod runtime, before
            // fixture writes. Raw NBT key ordering is not an acceptance condition.
            report.add("nbtSerializationProbe",nbtSerializationProbe());
            List<NativeSnapshot> snapshots=new ArrayList<>();
            if(document.has("fills"))for(JsonElement raw:document.getAsJsonArray("fills")){
                JsonObject fill=raw.getAsJsonObject();BlockPos from=position(fill.getAsJsonArray("from")),to=position(fill.getAsJsonArray("to"));
                if(from.getY()!=63||to.getY()!=63)throw new IllegalArgumentException("Fresh QA foundation fills are confined toY63");
                long volume=(long)(to.getX()-from.getX()+1)*(to.getY()-from.getY()+1)*(to.getZ()-from.getZ()+1);
                if(volume<=0||volume>16384)throw new IllegalArgumentException("Bounded QA foundation volume exceeded");
                BlockState state=blockState(fill);
                for(BlockPos pos:BlockPos.iterate(from,to)){world.getChunk(pos);world.setBlockState(pos,state,Block.NOTIFY_ALL);}
            }
            if(document.has("nativeBlocks"))for(JsonElement raw:document.getAsJsonArray("nativeBlocks")){
                JsonObject value=raw.getAsJsonObject();BlockPos pos=position(value.getAsJsonArray("pos"));world.getChunk(pos);
                BlockState state=blockState(value);
                if(!world.getBlockState(pos).isAir())throw new IllegalArgumentException("Native fixture would overwrite existing block at"+pos);
                world.setBlockState(pos,state,Block.NOTIFY_ALL);
                if(value.has("diamondCount")){
                    if(!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest))throw new IllegalArgumentException("Diamond fixture needs a native chest");
                    chest.setStack(0,new ItemStack(Items.DIAMOND,value.get("diamondCount").getAsInt()));chest.markDirty();
                }
                if(value.has("preserve")&&value.get("preserve").getAsBoolean()){
                    BlockEntity be=world.getBlockEntity(pos);snapshots.add(new NativeSnapshot(pos,world.getBlockState(pos),be,be==null?null:be.createNbt()));
                }
            }
            GameProfile profile=new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:ReviewSceneQA".getBytes(StandardCharsets.UTF_8)),"ReviewSceneQA");
            JsonArray placementMessages=new JsonArray();report.add("ordinaryPlacementMessages",placementMessages);
            player=new ServerPlayerEntity(server,world,profile){
                @Override public void sendMessage(net.minecraft.text.Text message,boolean overlay){placementMessages.add(message.getString());}
            };player.getAbilities().allowModifyWorld=true;
            for(JsonElement raw:document.getAsJsonArray("objects")){
                JsonObject request=raw.getAsJsonObject();String path=request.get("kind").getAsString();
                if(!KINDS.contains(path))throw new IllegalArgumentException("Unknown review architecture kind"+path);
                BlockPos root=position(request.getAsJsonArray("root"));world.getChunk(root);
                report.addProperty("currentOrdinaryPlacement",path+" at "+root.toShortString());
                if(!world.getBlockState(root).isAir()&&!world.getBlockState(root).isOf(CompositeArchitecture.CELL))throw new IllegalArgumentException("Review root already occupied at"+root);
                int rotation=request.get("yaw").getAsInt();if(rotation<0||rotation>7)throw new IllegalArgumentException("Global yaw must be0..7");
                JsonObject row=request.deepCopy();row.addProperty("registryId","bloodborne_dw:"+path);
                boolean ladder=Registries.BLOCK.get(new Identifier("bloodborne_dw",path)) instanceof PrototypeLadderBlock;
                boolean wall=Registries.BLOCK.get(new Identifier("bloodborne_dw",path)) instanceof PrototypeWallBlock;
                if(!wall&&!ladder){
                    CompositeRootBlock block=CompositeArchitecture.kindBlock(path);BlockState state=block.getDefaultState()
                        .with(CompositeRootBlock.ROTATION,rotation).with(CompositeRootBlock.VARIANT,request.get("variant").getAsInt())
                        .with(CompositeRootBlock.PROFILE,"alt".equals(request.get("profile").getAsString())?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE)
                        .with(CompositeRootBlock.OPEN,request.has("open")&&request.get("open").getAsBoolean());
                    if(request.has("mount"))state=state.with(ThinWindowRootBlock.MOUNT,ThinWindowRootBlock.Mount.valueOf(request.get("mount").getAsString().toUpperCase(java.util.Locale.ROOT)));
                    // Preload the bounded footprint explicitly; placement itself remains the ordinary atomic runtime operation.
                    for(Cell cell:block.spec.footprint(state,0).keySet())world.getChunk(root.add(cell.x(),cell.y(),cell.z()));
                    ItemStack offered=new ItemStack(block.asItem());NbtCompound art=offered.getOrCreateSubNbt("BlockStateTag");
                    art.putString("variant",Integer.toString(state.get(CompositeRootBlock.VARIANT)));art.putString("profile",state.get(CompositeRootBlock.PROFILE).asString());art.putString("open",Boolean.toString(state.get(CompositeRootBlock.OPEN)));
                    Direction face=request.has("mountFace")?Direction.byName(request.get("mountFace").getAsString()):Direction.UP;
                    if(face==null)throw new IllegalArgumentException("Unknown ordinary placement face");
                    BlockPos clicked=root.offset(face.getOpposite());
                    player.setYaw(rotation*45);player.setHeadYaw(player.getYaw());player.setSneaking(request.has("placementSneaking")&&request.get("placementSneaking").getAsBoolean());
                    player.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-8);player.setStackInHand(Hand.MAIN_HAND,offered);
                    BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(clicked).add(Vec3d.of(face.getVector()).multiply(.5)),face,clicked,false);
                    ActionResult used=offered.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));player.setSneaking(false);
                    var result=CompositeRuntime.lastResult();
                    row.addProperty("placementPath","ordinary Item.useOnBlock");row.addProperty("itemAction",used.toString());row.addProperty("remainingItemCount",offered.getCount());
                    if(result!=null)row.addProperty("transaction",result.outcome().name());
                    if(!used.isAccepted()||!world.getBlockState(root).equals(state)||offered.getCount()!=0)throw new IllegalStateException("Ordinary scene composite rejected "+path+" at"+root+": "+(result==null?used:result.reason())+" actual="+world.getBlockState(root));
                    CompositeBlockEntity be=(CompositeBlockEntity)world.getBlockEntity(root);Owner owner=be.resident();
                    row.addProperty("ownerUuid",owner.instanceId().toString());row.addProperty("mountY",be.mountY());
                    long helpers=0,foreign=0,owned=0;
                    for(Cell cell:CompositeLedger.get(world).cells())if(CompositeLedger.get(world).at(cell).stream().anyMatch(contribution->contribution.owner().equals(owner))){
                        owned++;BlockState carrier=world.getBlockState(CompositeData.pos(cell));
                        if(carrier.isOf(CompositeArchitecture.CELL))helpers++;
                        else if(!(carrier.getBlock() instanceof CompositeRootBlock))foreign++;
                    }
                    row.addProperty("helperCount",helpers);row.addProperty("ownedCells",owned);row.addProperty("foreignOverlayCells",foreign);
                    row.addProperty("visualPartCount",block.spec.pose(state).parts().size());
                }else{
                    Item item=Registries.ITEM.get(new Identifier("bloodborne_dw",path));ItemStack stack=new ItemStack(item);
                    if(request.has("pickFrom")){BlockPos original=position(request.getAsJsonArray("pickFrom"));BlockState old=world.getBlockState(original);stack=old.getBlock().getPickStack(world,original,old);if(!stack.isOf(item))throw new IllegalArgumentException("Scene middle-pick returned wrong type");row.addProperty("itemOrigin","actual Block.getPickStack");}else row.addProperty("itemOrigin","creative main item with explicit art tag");
                    NbtCompound art=stack.getOrCreateSubNbt("BlockStateTag");
                    if(request.has("art"))for(var entry:request.getAsJsonObject("art").entrySet())art.putString(entry.getKey(),entry.getValue().getAsString());
                    art.putString("profile",request.get("profile").getAsString());
                    if(ladder)art.putString("variant",Integer.toString(request.get("variant").getAsInt()));
                    player.setYaw(request.has("yawOverride")?request.get("yawOverride").getAsFloat():ladder?rotation*45:rotation*45-180);player.setHeadYaw(player.getYaw());player.setPitch(0);player.setStackInHand(Hand.MAIN_HAND,stack);
                    BlockPos clicked;Direction face;
                    if(ladder&&!(request.has("placementFace")&&request.get("placementFace").getAsString().equals("up"))){
                        BlockState desired=PrototypeLadderBlock.withYaw(Registries.BLOCK.get(new Identifier("bloodborne_dw",path)).getDefaultState(),rotation);
                        Direction backing=PrototypeLadderBlock.backingDirections(desired).get(0);clicked=root.offset(backing);face=backing.getOpposite();
                    }else{clicked=root.down();face=Direction.UP;}
                    player.setPosition(root.getX()+.5,root.getY()+1,root.getZ()-3);
                    BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(clicked).add(Vec3d.of(face.getVector()).multiply(.5)),face,clicked,false);
                    ActionResult used=item.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit));
                    row.addProperty("placementPath","ordinary Item.useOnBlock");row.addProperty("itemAction",used.toString());row.addProperty("remainingItemCount",stack.getCount());
                    if(!used.isAccepted()||!world.getBlockState(root).isOf(Registries.BLOCK.get(new Identifier("bloodborne_dw",path))))throw new IllegalStateException("Normal item scene placement failed:"+path+" at"+root+" result="+used);
                    BlockState actual=world.getBlockState(root);
                    int yaw=ladder?PrototypeLadderBlock.yaw(actual):actual.get(PrototypeWallBlock.ROTATION);
                    if(yaw!=rotation||stack.getCount()!=0)throw new IllegalStateException("Normal scene item yaw/consumption mismatch:"+path);
                    row.addProperty("helperCount",0);
                }
                row.addProperty("actualState",world.getBlockState(root).toString());placed.add(row);
            }
            JsonArray preserved=new JsonArray();boolean intact=true;
            for(NativeSnapshot snapshot:snapshots){
                boolean stateEqual=world.getBlockState(snapshot.pos).equals(snapshot.state),sameBe=world.getBlockEntity(snapshot.pos)==snapshot.instance;
                boolean nbtEqual=snapshot.nbt==null?world.getBlockEntity(snapshot.pos)==null:snapshot.nbt.equals(world.getBlockEntity(snapshot.pos).createNbt());
                JsonObject row=new JsonObject();row.addProperty("pos",snapshot.pos.toShortString());row.addProperty("stateUnchanged",stateEqual);
                row.addProperty("sameNativeBeInstance",sameBe);row.addProperty("allNativeNbtEqual",nbtEqual);
                if(snapshot.nbt!=null)row.addProperty("beforeAndAfterNbt",snapshot.nbt.toString());preserved.add(row);intact&=stateEqual&&sameBe&&nbtEqual;
            }
            report.add("preservedNativeFixtures",preserved);if(!intact)throw new IllegalStateException("Native fixture changed during review placement");
            JsonArray commands=new JsonArray();
            if(document.has("commands"))for(JsonElement raw:document.getAsJsonArray("commands")){
                String command=raw.getAsString();if(!(command.startsWith("gamerule ")||command.startsWith("time set ")||command.equals("weather clear")||command.equals("defaultgamemode creative")||command.startsWith("setworldspawn ")||command.startsWith("summon bloodborne_rp:tree1 ")||command.startsWith("summon bloodborne_rp:hunterlamp ")))throw new IllegalArgumentException("Unexpected scene command");
                int result=server.getCommandManager().getDispatcher().execute(command,server.getCommandSource());
                JsonObject row=new JsonObject();row.addProperty("command",command);row.addProperty("result",result);commands.add(row);
                verifyGamerule(world,command);
            }
            report.add("commands",commands);report.addProperty("status","PASS_SCENE_AUTHORED_REQUIRES_PRODUCTION_REOPEN");
            report.addProperty("objectCount",placed.size());report.addProperty("reviewAcceptance","PENDING_USER_REVIEW");
        }catch(Throwable error){report.addProperty("status","FAIL_SCENE_AUTHORING");report.addProperty("error",error.toString());error.printStackTrace();}
        finally{
            if(player!=null)player.discard();
            try{Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
            catch(Exception error){throw new IllegalStateException("Cannot write isolated review scene report",error);}
            System.out.println("Dreamwalker isolated review scene: "+report.get("status"));
        }
    }
    private static JsonObject nbtSerializationProbe()throws Exception{
        JsonObject report=new JsonObject();report.addProperty("schema","dreamwalker-actual-mod-runtime-nbt-order-v1");
        var lithium=FabricLoader.getInstance().getModContainer("lithium");
        report.addProperty("lithiumLoaded",lithium.isPresent());
        report.addProperty("lithiumVersion",lithium.map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("NOT_LOADED"));
        report.addProperty("worldMutated",false);report.addProperty("rawEqualityRequired",false);
        JsonArray rows=new JsonArray();report.add("cases",rows);
        for(boolean removeShift:List.of(true,false)){
            NbtCompound payload=new NbtCompound();payload.putBoolean("GlazingMounted",true);
            payload.putString("MountFace",removeShift?"up":"north");payload.putDouble("MountY",removeShift?0:.8125);
            NbtList shift=new NbtList();shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(.5));
            payload.put("SourceShift",shift);if(removeShift)payload.remove("SourceShift");
            rows.add(nbtProbeCase(removeShift?"floor_removed_SourceShift":"vertical_four_keys",payload,removeShift));
        }
        // Cover nested compounds, typed scalar distinctions, list order and
        // primitive arrays separately from the small mount-payload observations.
        NbtCompound typed=new NbtCompound(),nested=new NbtCompound();nested.putByte("byteOne",(byte)1);
        nested.putInt("intOne",1);nested.putFloat("floatValue",.25f);nested.putDouble("doubleValue",.25);
        nested.putByteArray("byteArray",new byte[]{3,1,-7});nested.putIntArray("intArray",new int[]{3,1,-7});
        nested.putLongArray("longArray",new long[]{3,1,-7});typed.put("nested",nested);
        NbtList ordered=new NbtList();ordered.add(NbtString.of("second"));ordered.add(NbtString.of("first"));typed.put("orderedList",ordered);
        rows.add(nbtProbeCase("nested_typed_arrays_ordered_list",typed,false));
        report.addProperty("observationScope","Actual loaded runtime NbtCompound/NbtIo/copy +production CompositeData canonical bytes; no standalone simulation or world/block write.");
        return report;
    }
    private static JsonObject nbtProbeCase(String name,NbtCompound before,boolean removedShift)throws Exception{
        NbtCompound copy=before.copy();byte[] rawBefore=rawNbt(before),rawCopy=rawNbt(copy);
        byte[] canonicalBefore=CompositeData.bytes(before),canonicalCopy=CompositeData.bytes(copy);
        NbtCompound decoded=CompositeData.nbt(canonicalBefore);JsonObject row=new JsonObject();
        row.addProperty("name",name);row.addProperty("sourceShiftRemoved",removedShift);
        row.addProperty("compoundSemanticEqual",before.equals(copy));row.addProperty("rawBytesEqual",Arrays.equals(rawBefore,rawCopy));
        row.addProperty("canonicalBytesEqual",Arrays.equals(canonicalBefore,canonicalCopy));
        row.addProperty("canonicalRoundTripTypedEqual",before.equals(decoded));
        row.addProperty("beforeKeysClass",before.getKeys().getClass().getName());row.addProperty("copyKeysClass",copy.getKeys().getClass().getName());
        JsonArray beforeKeys=new JsonArray(),copyKeys=new JsonArray();for(String key:before.getKeys())beforeKeys.add(key);for(String key:copy.getKeys())copyKeys.add(key);
        row.add("beforeKeyIterationOrder",beforeKeys);row.add("copyKeyIterationOrder",copyKeys);
        row.addProperty("rawBeforeSha256",nbtHash(rawBefore));row.addProperty("rawCopySha256",nbtHash(rawCopy));
        row.addProperty("canonicalBeforeSha256",nbtHash(canonicalBefore));row.addProperty("canonicalCopySha256",nbtHash(canonicalCopy));
        row.addProperty("rawBeforeBytes",rawBefore.length);row.addProperty("rawCopyBytes",rawCopy.length);
        row.addProperty("canonicalBytes",canonicalBefore.length);row.addProperty("beforeTypedNbt",before.toString());
        row.addProperty("roundTripTypedNbt",decoded.toString());return row;
    }
    private static byte[] rawNbt(NbtCompound value)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();NbtIo.write(value,new DataOutputStream(output));return output.toByteArray();
    }
    private static String nbtHash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static BlockPos position(JsonArray value){
        if(value.size()!=3)throw new IllegalArgumentException("Scene position must have three integer coordinates");
        BlockPos pos=new BlockPos(value.get(0).getAsInt(),value.get(1).getAsInt(),value.get(2).getAsInt());
        if(Math.abs(pos.getX())>96||Math.abs(pos.getZ())>96||pos.getY()<63||pos.getY()>100)throw new IllegalArgumentException("Scene coordinate outside declared bounded QA volume");return pos;
    }
    private static BlockState blockState(JsonObject value){
        Identifier id=new Identifier(value.get("block").getAsString());
        if(!id.getNamespace().equals("minecraft")||!Registries.BLOCK.containsId(id))throw new IllegalArgumentException("Unknown native block:"+id);
        BlockState state=Registries.BLOCK.get(id).getDefaultState();
        if(value.has("properties"))for(var entry:value.getAsJsonObject("properties").entrySet()){
            Property<?> property=state.getBlock().getStateManager().getProperty(entry.getKey());if(property==null)throw new IllegalArgumentException("Unknown native property");
            state=with(state,property,entry.getValue().getAsString());
        }return state;
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,Property<T> property,String value){return state.with(property,property.parse(value).orElseThrow());}
    static void verifyGamerule(ServerWorld world,String command){
        if(!command.startsWith("gamerule "))return;String[] words=command.split(" ");if(words.length!=3)throw new IllegalArgumentException("Expected one gamerule name/value");
        String actual=switch(words[1]){
            case "doMobSpawning"->Boolean.toString(world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));
            case "doDaylightCycle"->Boolean.toString(world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE));
            case "doWeatherCycle"->Boolean.toString(world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE));
            case "doTileDrops"->Boolean.toString(world.getGameRules().getBoolean(GameRules.DO_TILE_DROPS));
            case "spawnRadius"->Integer.toString(world.getGameRules().getInt(GameRules.SPAWN_RADIUS));
            case "randomTickSpeed"->Integer.toString(world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED));
            default->throw new IllegalArgumentException("Unsupported QA gamerule verification: "+words[1]);
        };if(!actual.equals(words[2]))throw new IllegalStateException("QA gamerule value not applied: "+command+" actual="+actual);
    }
}
