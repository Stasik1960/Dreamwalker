package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallArchitecture;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallItem;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public final class AlternateSelectiveGameTests implements FabricGameTest {
    private static final net.minecraft.server.world.ChunkTicketType<ChunkPos> COLD_PROOF_TICKET=net.minecraft.server.world.ChunkTicketType.create("dw_selective_cold_proof",Comparator.comparingLong(ChunkPos::toLong),1200);
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_contract")
    public void everyPlaceableBlockSupportsAltAndKeepsEveryOtherStateProperty(TestContext t){
        int blocks=0;
        for(var id:Registries.BLOCK.getIds()){
            var block=Registries.BLOCK.get(id);if(!ToolTarget.modRegistry(id)||!(block.asItem() instanceof BlockItem))continue;blocks++;
            var base=block.getDefaultState();for(var property:base.getProperties())if(!property.getName().equals("profile"))base=lastValue(base,property);
            t.assertTrue(BbAlternateService.supports(base),"placeable mod block has both profiles: "+id);
            var alt=BbAlternateService.profile(base,"alt");t.assertTrue("alt".equals(BbAlternateService.profile(alt)),"ALT is an actual native block state: "+id);
            for(var property:base.getProperties())if(!property.getName().equals("profile"))t.assertTrue(base.getEntries().get(property).equals(alt.getEntries().get(property)),"ALT retains art/pose/mount/rotation: "+id+"/"+property.getName());
            t.assertTrue(BbAlternateService.profile(alt,"base").equals(base),"BASE restores every property exactly: "+id);
        }
        t.assertTrue(blocks>100,"real catalogue includes native and imported player-placeable blocks");
        t.assertTrue(!BbAlternateService.supports(Blocks.STONE.getDefaultState()),"vanilla blocks never enter an ALT job");t.complete();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static BlockState lastValue(BlockState state,Property property){Comparable last=null;for(Object value:property.getValues())last=(Comparable)value;return state.with(property,last);}

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_contract")
    public void nativeLadderAndWallAltResourcesResolveToTheExactBaseModelTree(TestContext t){
        int bindings=0;Map<String,JsonObject> models=new HashMap<>();
        for(var ladder:PrototypeArchitecture.LADDERS){var id=Registries.BLOCK.getId(ladder);var variants=resource("/assets/"+id.getNamespace()+"/blockstates/"+id.getPath()+".json").getAsJsonObject("variants");
            for(var entry:variants.entrySet())if(entry.getKey().contains("profile=alt")){var base=variants.get(entry.getKey().replace("profile=alt","profile=base"));t.assertTrue(base!=null&&bindingSignature(base,models).equals(bindingSignature(entry.getValue(),models)),"native ladder BASE/ALT has identical art, UV and transforms: "+id+"/"+entry.getKey());bindings++;}
            for(int variant=0;variant<=2;variant++){
                var baseItem=ladder.asItem().getDefaultStack();baseItem.getOrCreateSubNbt("BlockStateTag").putString("variant",Integer.toString(variant));baseItem.getOrCreateSubNbt("BlockStateTag").putString("profile","base");var altItem=baseItem.copy();altItem.getOrCreateSubNbt("BlockStateTag").putString("profile","alt");
                t.assertTrue(ladderItemSignature(baseItem,models).equals(ladderItemSignature(altItem,models)),"inventory/pick predicate selects identical BASE/ALT art and UV: "+id+"/"+variant);bindings++;
            }
        }
        for(String name:List.of("post","low","tall_0","tall_1","tall_2","tall_3","tall_4","tall_5","tall_6","tall_7"))for(String diagonal:List.of("","_diagonal")){
            var base=modelSignature("bloodborne_dw:block/wall/base/"+name+diagonal,models);var alt=modelSignature("bloodborne_dw:block/wall/alt/"+name+diagonal,models);t.assertTrue(base.equals(alt),"native wall BASE/ALT primitive has identical art and UV: "+name+diagonal);bindings++;
        }
        for(String course:List.of("low","tall"))for(int material=0;material<8;material++){var base=modelSignature("bloodborne_dw:item/wall/base/"+course+"_"+material,models);var alt=modelSignature("bloodborne_dw:item/wall/alt/"+course+"_"+material,models);t.assertTrue(base.equals(alt),"wall item BASE/ALT aliases have identical art and UV: "+course+"/"+material);bindings++;}
        for(var wall:PrototypeWallArchitecture.blocks())for(String course:List.of("low","tall"))for(int material=0;material<8;material++){
            var base=wall.asItem().getDefaultStack();var tag=base.getOrCreateSubNbt("BlockStateTag");tag.putString("material",Integer.toString(material));tag.putString("course",course);tag.putString("profile","base");var alt=base.copy();alt.getOrCreateSubNbt("BlockStateTag").putString("profile","alt");t.assertTrue(wallItemSignature(base,models).equals(wallItemSignature(alt,models)),"native wall inventory/pick predicate resolves identical BASE/ALT art: "+Registries.ITEM.getId(base.getItem())+"/"+course+"/"+material);bindings++;
        }
        t.assertTrue(bindings>100,"all original native ladder bindings and wall primitive families were verified");t.complete();
    }
    private static JsonObject resource(String path){try(var input=AlternateSelectiveGameTests.class.getResourceAsStream(path)){if(input==null)throw new IllegalStateException("Missing native model resource "+path);return JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
    private static JsonElement bindingSignature(JsonElement value,Map<String,JsonObject> cache){if(value.isJsonArray()){var out=new JsonArray();for(var child:value.getAsJsonArray())out.add(bindingSignature(child,cache));return out;}var out=value.deepCopy().getAsJsonObject();out.add("model",modelSignature(out.get("model").getAsString(),cache));return out;}
    private static JsonObject ladderItemSignature(net.minecraft.item.ItemStack stack,Map<String,JsonObject> models){
        var id=Registries.ITEM.getId(stack.getItem());String model=id.getNamespace()+":item/"+id.getPath();var document=resource("/assets/"+id.getNamespace()+"/models/item/"+id.getPath()+".json");float variant=PrototypeLadderItem.variant(stack)/2f,profile=PrototypeLadderItem.profile(stack)==PrototypeLadderBlock.Profile.ALT?1:0;
        // Follow the ordered vanilla predicate list using the actual native
        // item selectors. Once a model is selected, its override table is
        // routing metadata rather than rendered vertices/UV/colour.
        if(document.has("overrides"))for(var raw:document.getAsJsonArray("overrides")){var override=raw.getAsJsonObject();boolean matches=true;for(var entry:override.getAsJsonObject("predicate").entrySet()){float value=entry.getKey().equals("bloodborne_dw:variant")?variant:entry.getKey().equals("bloodborne_dw:profile")?profile:Float.NEGATIVE_INFINITY;if(value<entry.getValue().getAsFloat()){matches=false;break;}}if(matches)model=override.get("model").getAsString();}
        var result=modelSignature(model,models).deepCopy();result.remove("overrides");return result;
    }
    private static JsonObject wallItemSignature(net.minecraft.item.ItemStack stack,Map<String,JsonObject> models){
        var id=Registries.ITEM.getId(stack.getItem());String model=id.getNamespace()+":item/"+id.getPath();var document=resource("/assets/"+id.getNamespace()+"/models/item/"+id.getPath()+".json");var values=Map.of("bloodborne_dw:wall_material",PrototypeWallItem.material(stack)/7f,"bloodborne_dw:wall_course",PrototypeWallItem.course(stack)==PrototypeWallBlock.Course.TALL?1f:0f,"bloodborne_dw:wall_profile",PrototypeWallItem.profile(stack)==PrototypeWallBlock.Profile.ALT?1f:0f);
        if(document.has("overrides"))for(var raw:document.getAsJsonArray("overrides")){var override=raw.getAsJsonObject();boolean matches=true;for(var entry:override.getAsJsonObject("predicate").entrySet())if(values.getOrDefault(entry.getKey(),Float.NEGATIVE_INFINITY)<entry.getValue().getAsFloat()){matches=false;break;}if(matches)model=override.get("model").getAsString();}
        var result=modelSignature(model,models).deepCopy();result.remove("overrides");return result;
    }
    private static JsonObject modelSignature(String id,Map<String,JsonObject> cache){
        if(cache.containsKey(id))return cache.get(id);var name=new net.minecraft.util.Identifier(id);String path="/assets/"+name.getNamespace()+"/models/"+name.getPath()+".json";JsonObject own;
        try(var input=AlternateSelectiveGameTests.class.getResourceAsStream(path)){if(input==null){if(name.getNamespace().equals("bloodborne_dw"))throw new IllegalStateException("Missing actual mod model resource "+path);var external=new JsonObject();external.addProperty("external",id);cache.put(id,external);return external;}own=JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        var result=own.has("parent")?modelSignature(own.get("parent").getAsString(),cache).deepCopy():new JsonObject();for(var entry:own.entrySet())if(!entry.getKey().equals("parent"))result.add(entry.getKey(),entry.getValue().deepCopy());cache.put(id,result);return result;
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="selective_alt_contract")
    public void everyRpObjectPersistsAltWithoutChangingPoseDataOrCollider(TestContext t){
        var p=BbToolGameTests.player(t);var root=t.getAbsolutePos(new BlockPos(3,3,3));p.setPosition(Vec3d.ofCenter(root));int objects=0;
        try{for(var entry:ObjectRegistry.TYPES.entrySet()){
            var rp=entry.getValue().create(t.getWorld());rp.refreshPositionAndAngles(root.getX()+.5,150,root.getZ()+.5,45,17);rp.setObjectScale(.5f);rp.setLocked(true);t.getWorld().spawnEntity(rp);var target=ToolTarget.entity(rp);
            var pos=rp.getPos();var geometry=rp.activePhysicalBoxes();var before=new NbtCompound();rp.writeNbt(before);
            t.assertTrue(BbAlternateService.change(p,target,"alt")&&rp.alternateVisual(),"older RP object accepts tool ALT: "+entry.getKey());
            t.assertTrue(rp.getPos().equals(pos)&&rp.activePhysicalBoxes().equals(geometry)&&rp.isLocked()&&rp.objectScale()==.5f,"ALT never changes RP physical/object data: "+entry.getKey());
            var saved=new NbtCompound();rp.writeNbt(saved);var same=saved.copy();same.remove("BbAltVisual");t.assertTrue(same.equals(before),"only the alternate boolean is added to persisted RP data: "+entry.getKey());
            var clone=entry.getValue().create(t.getWorld());clone.readNbt(saved);t.assertTrue(clone.alternateVisual()&&clone.getPos().equals(pos),"native RP NBT reload retains ALT and exact Pos: "+entry.getKey());
            t.assertTrue(rp.getPickBlockStack().getSubNbt("bloodborne_rp_object").getBoolean("BbAltVisual"),"picked RP item retains ALT: "+entry.getKey());
            t.assertTrue(BbAlternateService.change(p,target,"base")&&!rp.alternateVisual(),"RP BASE reverses the same target: "+entry.getKey());rp.discard();clone.discard();objects++;
        }}finally{p.discard();}
        t.assertTrue(objects>20,"all registered RP object types were exercised");t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_contract")
    public void revertingLegacyRpToBaseCannotResurrectStaleAltFromProvenance(TestContext t){
        var type=ObjectRegistry.TYPES.get("curtainsmall");var original=type.create(t.getWorld());var incoming=new NbtCompound();original.writeNbt(incoming);incoming.putInt("EntityState",0);incoming.putBoolean("BbAltVisual",true);incoming.putLong("OpaqueSourceLong",Long.MIN_VALUE+17);
        var restored=type.create(t.getWorld());restored.readNbt(incoming);t.assertTrue(restored.alternateVisual(),"native incoming ALT is honoured");restored.setAlternateVisual(false);var outgoing=new NbtCompound();restored.writeNbt(outgoing);
        t.assertTrue(!outgoing.contains("BbAltVisual"),"BASE does not reintroduce stale ALT from source passthrough");t.assertTrue(outgoing.getCompound("SourceLegacyPayload").getCompound("Original").equals(incoming),"exact typed source provenance remains immutable");
        var twice=type.create(t.getWorld());twice.readNbt(outgoing);t.assertTrue(!twice.alternateVisual(),"native reload stays BASE after reverting legacy ALT");original.discard();restored.discard();twice.discard();t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=200,batchId="selective_alt_contract")
    public void commandsAndServiceEnforceThreeHundredAndRejectThreeHundredOne(TestContext t){
        var p=BbToolGameTests.player(t);var source=p.getCommandSource().withLevel(2);var commands=t.getWorld().getServer().getCommandManager();
        try{
            t.assertTrue(commands.executeWithPrefix(source,"bb select 300")==1,"command accepts radius 300");
            for(String value:List.of("bb select 301","bb alt 301","bb base 301","bb select 0","bb alt -1"))t.assertTrue(commands.executeWithPrefix(source,value)==0,"command rejects invalid radius: "+value);
            t.assertTrue(!BbAlternateService.start(p,"alt",301)&&!BbAlternateService.start(p,"alt",-1)&&!BbAlternateService.start(p,"base",0),"direct service cannot bypass command limits");
            t.assertTrue(commands.executeWithPrefix(source,"bb alt 300")==1,"real area ALT command accepts 300");t.assertTrue(BbAlternateService.status(p.getServer()).running(),"real command schedules bounded job");BbAlternateService.cancel(p.getServer());
            t.assertTrue(commands.executeWithPrefix(source,"bb base 300")==1,"real area BASE command accepts 300");BbAlternateService.cancel(p.getServer());
        }finally{BbAlternateService.cancel(p.getServer());p.discard();}t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_contract")
    public void actualSavedProtoChunkIsExcludedWhileFullChunkIsReadWithoutWrites(TestContext t)throws Exception{
        var directory=java.nio.file.Files.createTempDirectory(t.getWorld().getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT),"selective-full-region-");var file=directory.resolve("r.-2.3.mca");var full=new ChunkPos(-64,96);var proto=new ChunkPos(-63,96);
        try{
            try(var writer=new net.minecraft.world.storage.RegionFile(file,directory,false)){
                var data=new NbtCompound();data.putString("Status","minecraft:full");data.putInt("xPos",full.x);data.putInt("zPos",full.z);try(var output=writer.getChunkOutputStream(full)){NbtIo.write(data,output);}
                data.putString("Status","minecraft:noise");data.putInt("xPos",proto.x);data.putInt("zPos",proto.z);try(var output=writer.getChunkOutputStream(proto)){NbtIo.write(data,output);}
            }
            byte[] before=java.nio.file.Files.readAllBytes(file);
            t.assertTrue(BbAlternateService.savedChunks(file).equals(Set.of(full.toLong(),proto.toLong())),"native saved header really contains both FULL and proto records");
            t.assertTrue(BbAlternateService.savedFullChunks(file).equals(Set.of(full.toLong())),"only actual saved FULL terrain enters asynchronous edit requests");
            t.assertTrue(Arrays.equals(before,java.nio.file.Files.readAllBytes(file)),"FULL/proto discovery performs no region writes");
        }finally{java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory);}t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=300,batchId="selective_alt_area")
    public void areaJobChangesNativeImportedRpAndUtilitiesAndPreservesOwnerPayload(TestContext t){
        var w=t.getWorld();var p=BbToolGameTests.player(t);var root=t.getAbsolutePos(new BlockPos(3,3,3));p.setPosition(root.getX()+.5,160,root.getZ()+.5);root=new BlockPos(root.getX(),160,root.getZ());
        var ladder=root;var wall=root.east(4);var utility=root.south(4);var imported=root.east(8);var vanilla=root.south(8);var outside=root.east(34);
        var ladderState=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true).with(PrototypeLadderBlock.DIAGONAL,true);w.setBlockState(ladder,ladderState);VerticalMount.placed(w,ladder,ladderState);
        var wallState=PrototypeWallArchitecture.WALL.getDefaultState();w.setBlockState(wall,wallState);VerticalMount.placed(w,wall,wallState);
        t.assertTrue(DwUtilityBlocks.replaceCarrier(w,utility,DwUtilityBlocks.LIGHT.getDefaultState(),p),"utility fixture installed");w.setBlockState(vanilla,Blocks.STONE.getDefaultState());w.getChunk(outside);w.setBlockState(outside,ladderState);VerticalMount.placed(w,outside,ladderState);
        var block=CompositeArchitecture.blocks().stream().filter(b->b.spec.simplePhysicalCollision&&!b.spec.requiredSupport&&!(b instanceof ThinWindowRootBlock)&&b.spec.footprint(b.getDefaultState(),0).size()<=4).findFirst().orElseThrow();
        var payload=new NbtCompound();payload.putString("OpaqueAltProbe","сохранить");payload.putLong("OpaqueLong",Long.MIN_VALUE+41);payload.putDouble("VerticalOffset",.375);payload.putIntArray("OpaqueArray",new int[]{-7,0,19});
        var initial=CompositeRuntime.instance(w,imported,block.getDefaultState(),UUID.randomUUID(),payload);for(var cell:initial.cells().keySet())w.getChunk(CompositeData.pos(initial.owner().root().add(cell)));
        t.assertTrue(CompositeRuntime.place(w,imported,block.getDefaultState(),initial.owner().instanceId(),p,payload).outcome()==TransactionCore.Outcome.COMMITTED,"imported fixture installed with exact typed payload");
        var own=(CompositeBlockEntity)w.getBlockEntity(imported);var uuid=own.resident().instanceId();var saved=own.payload().copy();var before=w.getBlockState(imported);
        var rp=ObjectRegistry.TYPES.get("cage_obj_1").create(w);rp.setPosition(root.getX()+12.5,160,root.getZ()+.5);w.spawnEntity(rp);var rpPos=rp.getPos();rp.setLocked(true);rp.setObjectScale(.5f);
        Set<ChunkPos> chunks=new LinkedHashSet<>();for(var pos:List.of(ladder,wall,utility,imported,outside,rp.getBlockPos()))chunks.add(new ChunkPos(pos));
        t.assertTrue(BbAlternateService.startVisits(p,"alt",20,chunks.stream().map(c->new BbAlternateService.Visit(w,c)).toList()),"actual bounded area processor started");
        final BlockPos keptLadder=ladder,keptWall=wall,keptUtility=utility,keptImported=imported;
        t.runAtTick(100,()->{
            t.assertTrue(!BbAlternateService.status(p.getServer()).running(),"area processor completed within finite tick budget");
            for(var pos:List.of(keptLadder,keptWall,keptUtility,keptImported))t.assertTrue("alt".equals(BbAlternateService.profile(w.getBlockState(pos))),"all block families changed in area: "+pos);
            t.assertTrue(rp.alternateVisual()&&rp.getPos().equals(rpPos)&&rp.isLocked()&&rp.objectScale()==.5f,"RP area edit preserves physical and object data");
            t.assertTrue(w.getBlockState(vanilla).isOf(Blocks.STONE)&&"base".equals(BbAlternateService.profile(w.getBlockState(outside))),"vanilla and out-of-sphere blocks stay unchanged");
            var after=(CompositeBlockEntity)w.getBlockEntity(keptImported);t.assertTrue(after.resident().instanceId().equals(uuid)&&after.payload().equals(saved)&&BbAlternateService.profile(w.getBlockState(keptImported),"base").equals(before),"ALT retains UUID, exact typed payload and every state except profile");
            t.assertTrue(BbAlternateService.startVisits(p,"base",20,chunks.stream().map(c->new BbAlternateService.Visit(w,c)).toList()),"actual BASE reverse job started");
        });
        t.runAtTick(200,()->{try{for(var pos:List.of(keptLadder,keptWall,keptUtility,keptImported))t.assertTrue("base".equals(BbAlternateService.profile(w.getBlockState(pos))),"BASE restores each block family: "+pos);t.assertTrue(!rp.alternateVisual(),"BASE restores RP");t.complete();}finally{BbAlternateService.cancel(p.getServer());rp.discard();p.discard();}});
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=1200,batchId="selective_alt_cold")
    public void realSavedColdChunkAndRpLoadReceiveAltAndReleaseTheirTickets(TestContext t){new ColdProof(t).prepare();}
    private static final class ColdProof {
        final TestContext t;final net.minecraft.server.world.ServerWorld world;final net.minecraft.server.network.ServerPlayerEntity player;final ChunkPos chunk;final BlockPos pos;
        final UUID rpUuid=UUID.randomUUID();UUID ownerUuid;NbtCompound payload,rpData;int phase;long phaseStart;boolean held;
        ColdProof(TestContext t){this.t=t;world=t.getWorld();player=BbToolGameTests.player(t);var remote=t.getAbsolutePos(new BlockPos(8192,0,-8192));chunk=new ChunkPos(remote);pos=new BlockPos(chunk.getStartX()+4,160,chunk.getStartZ()+4);player.setPosition(Vec3d.ofBottomCenter(pos));}
        void prepare(){
            t.assertTrue(world.getChunkManager().getWorldChunk(chunk.x,chunk.z)==null,"remote fixture was not loaded before native preparation");
            world.getChunk(pos);hold();var state=PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.FREESTANDING,true).with(PrototypeLadderBlock.DIAGONAL,true);world.setBlockState(pos,state);VerticalMount.placed(world,pos,state);
            var own=(CompositeBlockEntity)world.getBlockEntity(pos);ownerUuid=own.resident().instanceId();payload=new NbtCompound();payload.putLong("ColdTypedLong",Long.MIN_VALUE+77);payload.putIntArray("ColdTypedArray",new int[]{-3,0,51});payload.putString("ColdTypedLabel","холодный объект");payload=VerticalMount.nativePayload(world,pos,state,payload);own.set(own.resident(),List.of(),payload);
            var rp=ObjectRegistry.TYPES.get("curtainsmall").create(world);rp.setUuid(rpUuid);rp.setPosition(Vec3d.ofBottomCenter(pos.east(4)));rp.setYaw(45);rp.setObjectScale(1.125f);rp.setCustomName(net.minecraft.text.Text.literal("cold selective ALT"));world.spawnEntity(rp);rpData=stable(rp);phaseStart=t.getTick();again();
        }
        void step(){try{
            var entities=((dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess)world).bloodborne_dw$profileEntityManager();
            t.assertTrue(t.getTick()-phaseStart<400,"cold native save/load/unload phase completes: "+phase);
            if(phase==0){if(!entities.isLoaded(chunk.toLong())){again();return;}world.save(null,true,false);release();world.getChunkManager().removeTicket(net.minecraft.server.world.ChunkTicketType.UNKNOWN,chunk,0,chunk);phase=1;phaseStart=t.getTick();again();return;}
            if(phase==1){if(world.getChunkManager().getWorldChunk(chunk.x,chunk.z)!=null||entities.isLoaded(chunk.toLong())||world.getEntity(rpUuid)!=null){again();return;}
                var directory=net.minecraft.world.dimension.DimensionType.getSaveDirectory(world.getRegistryKey(),world.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT));var file=directory.resolve("region").resolve("r."+chunk.getRegionX()+"."+chunk.getRegionZ()+".mca");
                try{t.assertTrue(BbAlternateService.savedChunks(file).contains(chunk.toLong()),"actual saved block region header retains cold native fixture");}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
                t.assertTrue(BbAlternateService.start(player,"alt",20),"production radius discovery starts from genuinely unloaded saved chunk and RP");phase=2;phaseStart=t.getTick();again();return;}
            if(phase==2){if(!held&&world.getEntity(rpUuid) instanceof RpObjectEntity&&entities.isLoaded(chunk.toLong()))hold();var status=BbAlternateService.status(world.getServer());if(status.running()){again();return;}
                t.assertTrue(held&&status.changed()==2&&status.failed()==0,"native cold loader waited for both block and RP storage and edited each once: "+status);
                var own=(CompositeBlockEntity)world.getBlockEntity(pos);t.assertTrue("alt".equals(BbAlternateService.profile(world.getBlockState(pos)))&&own.resident().instanceId().equals(ownerUuid)&&own.payload().equals(payload),"cold ALT retains native owner UUID and exact typed payload");
                var restored=(RpObjectEntity)world.getEntity(rpUuid);t.assertTrue(restored.alternateVisual()&&rpData.equals(stable(restored)),"cold RP ALT retains UUID, Pos, Rotation, Scale, name and typed object state");
                release();phase=3;phaseStart=t.getTick();again();return;}
            if(world.getChunkManager().getWorldChunk(chunk.x,chunk.z)!=null||entities.isLoaded(chunk.toLong())||world.getEntity(rpUuid)!=null){again();return;}
            t.assertTrue(!BbAlternateService.status(world.getServer()).running(),"finished job leaves no active queue");player.discard();t.complete();
        }catch(RuntimeException|Error failure){BbAlternateService.cancel(world.getServer());release();player.discard();throw failure;}}
        void again(){t.runAtTick(t.getTick()+1,this::step);}
        void hold(){world.getChunkManager().addTicket(COLD_PROOF_TICKET,chunk,0,chunk);held=true;}
        void release(){if(held)world.getChunkManager().removeTicket(COLD_PROOF_TICKET,chunk,0,chunk);held=false;}
        static NbtCompound stable(RpObjectEntity rp){var n=new NbtCompound();rp.writeNbt(n);for(String key:List.of("BbAltVisual","Motion","FallDistance","Fire","Air","OnGround","PortalCooldown"))n.remove(key);return n;}
    }
}
