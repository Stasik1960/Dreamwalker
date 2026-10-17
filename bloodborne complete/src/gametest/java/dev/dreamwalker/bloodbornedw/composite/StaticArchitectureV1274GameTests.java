package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.GsonBuilder;
import dev.dreamwalker.bloodbornedw.gametest.UtilityV127GameTests;
import dev.dreamwalker.bloodbornedw.runtime.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.test.*;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.RegionFile;

/** Finite architecture recipes: exact old footprints, native cell destruction,
 * public lighting lifecycle and actual post-write typed transaction rollback. */
public final class StaticArchitectureV1274GameTests implements FabricGameTest {
    private static final ChunkTicketType<ChunkPos> RELOAD_TICKET=ChunkTicketType.create("dw-static-saved-reload-proof",Comparator.comparingLong(ChunkPos::toLong),1200);
    private static final Map<ServerWorld,Map<Long,ColdReload>> RELOAD_PROBES=new WeakHashMap<>();
    private static boolean reloadEvents;
    private static void reloadEvents(){if(reloadEvents)return;reloadEvents=true;
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{var probe=RELOAD_PROBES.getOrDefault(world,Map.of()).get(chunk.getPos().toLong());if(probe!=null)probe.loaded(chunk);});
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{var probe=RELOAD_PROBES.getOrDefault(world,Map.of()).get(chunk.getPos().toLong());if(probe!=null)probe.unloads++;});
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_architecture_catalog_v1274",tickLimit=160)
    public void everyFinitePoseAndCellRecipeMatchesExistingExactPhysicsWithoutEntityOrUuid(TestContext t) {
        var poses=StaticArchitectureCatalog.poses();var recipes=StaticArchitectureCatalog.recipes();
        t.assertTrue(poses.size()>1&&recipes.size()>1,"The shared catalog covers a group, not one easy native cube");
        int multi=0,helper=0,overlap=0,boxes=0;
        for(var pose:poses){
            var fresh=CompositeRuntime.instance(BlockPos.ORIGIN,pose.state(),new UUID(7,11),pose.payload());
            t.assertTrue(fresh.cells().equals(pose.footprint()),"Pose footprint exactly matches the unchanged source runtime: "+pose.key());
            if(fresh.cells().size()>1)multi++;
        }
        t.assertTrue(multi>1,"Multiple real multi-cell source poses are represented");
        for(var recipe:recipes){
            var expectedCollision=new ArrayList<Box>();var expectedSelection=new ArrayList<Box>();int members=0;
            for(var fragment:recipe.fragments())if(fragment.member()){
                members++;var shape=fragment.pose().footprint().get(Cell.ORIGIN.subtract(fragment.origin()));
                t.assertTrue(shape!=null,"Every physical fragment refers to a real occupied old owner cell");
                expectedCollision.addAll(shape.collision());expectedSelection.addAll(fragment.pose().outline().getOrDefault(Cell.ORIGIN.subtract(fragment.origin()),List.of()));
                if(!fragment.origin().equals(Cell.ORIGIN))helper++;
            }
            if(members>1)overlap++;
            sameShape(t,ImportedPhysicalShapes.exact(expectedCollision),recipe.collisionShape(),"Exact union collision "+recipe.index());
            sameShape(t,ImportedPhysicalShapes.exact(expectedSelection),recipe.outlineShape(),"Exact union selection "+recipe.index());
            var state=StaticArchitectureCatalog.state(recipe.index());
            t.assertTrue(!state.hasBlockEntity()&&!(state.getBlock() instanceof BlockEntityProvider)&&state.getRenderType()==BlockRenderType.MODEL,"Native recipe has no entity/provider and uses ordinary chunk rendering");
            t.assertTrue(StaticArchitectureCatalog.recipe(state)==recipe,"Native palette resolves one immutable shared recipe");
            var encoded=NbtHelper.fromBlockState(state);var decoded=NbtHelper.toBlockState(net.minecraft.registry.Registries.BLOCK.getReadOnlyWrapper(),encoded);
            t.assertTrue(decoded==state&&!encoded.containsUuid("UUID")&&!encoded.contains("BlockEntityTag"),"Only finite native palette state is needed to save a recipe");
            var at=t.getAbsolutePos(new BlockPos(2,4,2));var elsewhere=at.add(11,3,9);
            t.assertTrue(recipe.collisionShape()==StaticArchitectureCatalog.recipe(decoded).collisionShape(),"Decoded palette resolves the same immutable potential shape data");
            boxes+=expectedCollision.size();
        }
        t.assertTrue(helper>0,"Catalog includes auxiliary collision cells");
        proof(t,"catalog",Map.of("status","PASS_ALL_FINITE_POSES_AND_EXACT_SHARED_CELL_SHAPES","poses",poses.size(),"recipes",recipes.size(),"multiCellPoses",multi,"helperFragments",helper,"overlapRecipes",overlap,"collisionBoxes",boxes));t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_architecture_break_v1274",tickLimit=180)
    public void survivalNativeBreakOfHelperRemovesItsWholeModelAndCollisionWithoutNeighborDamage(TestContext t) {
        var world=t.getWorld();var pose=StaticArchitectureCatalog.poses().stream().filter(p->p.footprint().size()>1&&p.footprint().keySet().stream().anyMatch(c->!c.equals(Cell.ORIGIN)&&!p.outline().getOrDefault(c,List.of()).isEmpty())).findFirst().orElseThrow();
        var root=t.getAbsolutePos(new BlockPos(80,30,80));var helperOffset=pose.footprint().keySet().stream().filter(c->!c.equals(Cell.ORIGIN)&&!pose.outline().getOrDefault(c,List.of()).isEmpty()).findFirst().orElseThrow();var pos=root.add(helperOffset.x(),helperOffset.y(),helperOffset.z());
        var fixture=new StaticFixture(t,List.of(new Placed(pose,root),new Placed(pose,root.east(32))));var player=UtilityV127GameTests.player(world);player.changeGameMode(GameMode.SURVIVAL);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));player.setPosition(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
        try {
            fixture.install();var neighbor=new TreeMap<BlockPos,BlockState>();for(var offset:pose.footprint().keySet()){var p=root.east(32).add(offset.x(),offset.y(),offset.z());neighbor.put(p,world.getBlockState(p));}
            // Aim through a real helper's local selection center, not a forged
            // root command. The mock player does not participate in physics ticks.
            var box=ImportedPhysicalShapes.exact(pose.outline().get(helperOffset)).getBoundingBoxes().get(0);var center=box.getCenter().add(Vec3d.of(pos));var eye=center.add(0,0,-2);player.setPosition(eye.x,eye.y-player.getStandingEyeHeight(),eye.z);player.setYaw(0);player.setPitch(0);
            boolean nativeReturn=player.interactionManager.tryBreakBlock(pos);CompositeRuntime.drain(world);
            for(var offset:pose.footprint().keySet()){var p=root.add(offset.x(),offset.y(),offset.z());t.assertTrue(world.getBlockEntity(p)==null&&world.getBlockState(p).getCollisionShape(world,p).isEmpty()&&world.getBlockState(p).getOutlineShape(world,p).isEmpty(),"Removed object's every root/helper cell has no invisible collision or selection");}
            for(var entry:neighbor.entrySet()){var p=entry.getKey();t.assertTrue(world.getBlockState(p)==entry.getValue()&&world.getBlockEntity(p)==null,"Independent neighboring object retains exact palette states");}
            t.assertTrue(CompositeLedger.get(world).at(CompositeData.cell(pos)).isEmpty(),"Static destruction needs no UUID ledger cleanup");
            proof(t,"break",Map.of("status","PASS_REAL_SURVIVAL_HELPER_BREAK_REMOVES_WHOLE_OBJECT_NO_GHOST_OR_NEIGHBOR_DAMAGE","pose",pose.key(),"removedCells",pose.footprint().size(),"neighborCells",neighbor.size(),"brokenBlockEntity",false,"interactionManagerReturn",nativeReturn));t.complete();
        } finally {fixture.close();player.discard();}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_architecture_overlap_v1274",tickLimit=200)
    public void removingOneSharedRecipeObjectPreservesExactCollisionOfTheOverlappingNeighbor(TestContext t){
        var shared=StaticArchitectureCatalog.recipes().stream().filter(r->r.fragments().stream().filter(StaticArchitectureCatalog.Fragment::member).map(StaticArchitectureCatalog.Fragment::origin).distinct().count()>1).findFirst().orElseThrow();
        var fragments=shared.fragments().stream().filter(StaticArchitectureCatalog.Fragment::member).toList();var a=fragments.get(0);var b=fragments.stream().filter(f->!f.origin().equals(a.origin())).findFirst().orElseThrow();var base=t.getAbsolutePos(new BlockPos(160,40,160));
        var one=new Placed(a.pose(),base.add(a.origin().x(),a.origin().y(),a.origin().z()));var two=new Placed(b.pose(),base.add(b.origin().x(),b.origin().y(),b.origin().z()));var fixture=new StaticFixture(t,List.of(one,two));
        try{fixture.install();var result=StaticArchitectureRuntime.remove(t.getWorld(),one.root(),one.pose());t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"Bounded normal static transaction removes one of two overlapping objects");CompositeRuntime.drain(t.getWorld());
            for(var p:fixture.states.keySet()){var offset=CompositeData.cell(p).subtract(CompositeData.cell(two.root()));var shape=two.pose().footprint().get(offset);var expected=shape==null?VoxelShapes.empty():ImportedPhysicalShapes.exact(shape.collision());var actual=t.getWorld().getBlockState(p).getCollisionShape(t.getWorld(),p);sameShape(t,expected,actual,"Only retained neighbor exact collision remains at "+p);t.assertTrue(t.getWorld().getBlockEntity(p)==null,"No helper BE is recreated during removal");}
            var retained=t.getWorld().getBlockState(two.root());t.assertTrue(retained.getBlock() instanceof StaticArchitectureBlock&&StaticArchitectureCatalog.recipe(retained).resident()>=0&&StaticArchitectureCatalog.recipe(retained).fragments().get(StaticArchitectureCatalog.recipe(retained).resident()).pose()==two.pose(),"Neighbor root retains its original complete visual pose");proof(t,"overlap",Map.of("status","PASS_OVERLAPPING_STATIC_OBJECT_REMOVAL_EXACT_NEIGHBOR_COLLISION","removedPose",one.pose().key(),"retainedPose",two.pose().key(),"cells",fixture.states.size()));t.complete();
        }finally{fixture.close();}}

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_architecture_fault_v1274",tickLimit=200)
    public void failureAfterTwoRealNativeReplacementsRestoresFullTypedRootHelperAndLedger(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(2,6,2));var helper=root.east();var rootCell=CompositeData.cell(root);var helperCell=CompositeData.cell(helper);
        var legacy=CompositeArchitecture.kindBlock("source_light_gray_concrete").getDefaultState();dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner owner=null;
        try {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.getChunk(root.add(x*16,0,z*16));
            var payload=ImportedTypeAliases.markPayload(null);payload.put("TypedTransactionProbe",typed());
            t.assertTrue(CompositeRuntime.place(world,root,legacy,UUID.randomUUID(),null,payload).outcome()==TransactionCore.Outcome.COMMITTED,"Existing multi-cell cornice installs before migration fault");
            var rootEntity=(CompositeBlockEntity)world.getBlockEntity(root);owner=rootEntity.resident();
            t.assertTrue(world.getBlockState(helper).isOf(CompositeArchitecture.CELL)&&world.getBlockEntity(helper) instanceof CompositeBlockEntity,"Fault target includes an actual auxiliary collision BlockEntity");
            var helperEntity=(CompositeBlockEntity)world.getBlockEntity(helper);helperEntity.set(null,helperEntity.contributions(),typed());
            var beforeStates=Map.of(rootCell,world.getBlockState(root),helperCell,world.getBlockState(helper));
            var beforeNbt=Map.of(rootCell,rootEntity.createNbt(),helperCell,helperEntity.createNbt());
            var beforeLedger=Map.of(rootCell,List.copyOf(CompositeLedger.get(world).at(rootCell)),helperCell,List.copyOf(CompositeLedger.get(world).at(helperCell)));
            class Fault extends StaticArchitectureWorldAccess {
                int captures,writes,restores;Set<Cell> keys=Set.of();
                Fault(){super(t.getWorld(),Set.of(rootCell,helperCell),true,2);}
                @Override public void prepareRollback(Map<Cell,CellSnapshot> expected){captures++;keys=Set.copyOf(expected.keySet());t.assertTrue(writes==0&&keys.equals(Set.of(rootCell,helperCell)),"Both actually changed cells are staged once before the first native write");super.prepareRollback(expected);}
                @Override public boolean writeSilently(Cell cell,CellSnapshot next){writes++;boolean applied=super.writeSilently(cell,next);t.assertTrue(world.getBlockEntity(CompositeData.pos(cell))==null,"Shared migration adapter injects the fault only after actual legacy BE removal");return applied;}
                @Override public boolean restoreSilently(Cell cell,CellSnapshot before){restores++;return super.restoreSilently(cell,before);}
            }
            var access=new Fault();var expected=new TreeMap<Cell,CellSnapshot>();var writes=new TreeMap<Cell,CellSnapshot>();int index=0;
            for(var cell:List.of(rootCell,helperCell)){expected.put(cell,access.read(cell));var state=StaticArchitectureCatalog.state(index++%StaticArchitectureCatalog.recipes().size());writes.put(cell,new CellSnapshot(CellSnapshot.Kind.FOREIGN,CompositeData.data(state,new NbtCompound()),null,List.of()));}
            var result=CompositeRuntime.execute(access,new TransactionCore.Preparation(new TransactionCore.Plan(access.dimensionKey(),expected,writes,Map.of()),null));
            t.assertTrue(result.outcome()==TransactionCore.Outcome.ROLLED_BACK&&access.captures==1&&access.writes==2&&access.restores==2,"Second actual native write fails and both cells roll back");
            for(var cell:expected.keySet()){var pos=CompositeData.pos(cell);t.assertTrue(world.getBlockState(pos)==beforeStates.get(cell)&&world.getBlockEntity(pos).createNbt().equals(beforeNbt.get(cell))&&CompositeLedger.get(world).at(cell).equals(beforeLedger.get(cell)),"Exact full typed NBT/state/UUID/ledger restored at "+cell);}
            proof(t,"fault",Map.of("status","PASS_TWO_REAL_NATIVE_WRITES_EXACT_TYPED_ROOT_HELPER_ROLLBACK","captures",access.captures,"actualWrites",access.writes,"restores",access.restores));t.complete();
        } finally {if(owner!=null)CompositeRuntime.remove(world,owner,null,false);CompositeRuntime.drain(world);}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_architecture_light_v1274",tickLimit=650)
    public void actualNativeSkyAndTorchRespondToStaticPartPlacementBreakAndRemoval(TestContext t){new LightProbe(t).start();}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="static_architecture_saved_reload_v1274",tickLimit=1400)
    public void savedStaticRootAndHelperActuallyUnloadAndReloadBeforeFullFutureWithoutDeadlock(TestContext t){new ColdReload(t).start();}
    /** A real saved static native palette re-enters CHUNK_LOAD before its FULL
     * future is published. Requests use native tickets; the test never joins
     * that future or clears chunk/lighting/ledger internals. */
    private static final class ColdReload {
        final TestContext t;final ServerWorld world;final ChunkPos chunk;final BlockPos root;final StaticArchitectureCatalog.Pose pose;final StaticFixture fixture;
        WorldChunk initial,reloaded;int loads,unloads,phase;long phaseStart;boolean held,closed,reloadBeforeFull,callbackExact;
        ColdReload(TestContext t){this.t=t;world=t.getWorld();var remote=new ChunkPos(t.getAbsolutePos(new BlockPos(48000,0,-48000)));chunk=remote;root=new BlockPos(remote.getStartX()+8,150,remote.getStartZ()+8);
            pose=StaticArchitectureCatalog.poses().stream().filter(p->p.footprint().size()>1&&p.footprint().keySet().stream().allMatch(c->Math.abs(c.x())<=6&&Math.abs(c.z())<=6)&&p.footprint().keySet().stream().anyMatch(c->!c.equals(Cell.ORIGIN))).findFirst().orElseThrow();fixture=new StaticFixture(t,List.of(new Placed(pose,root)));}
        void loaded(WorldChunk actual){loads++;if(phase==2){reloaded=actual;reloadBeforeFull=world.getChunkManager().getWorldChunk(chunk.x,chunk.z)==null;callbackExact=fixture.states.entrySet().stream().allMatch(e->actual.getBlockState(e.getKey())==e.getValue()&&actual.getBlockEntity(e.getKey())==null);}}
        void start(){try{reloadEvents();t.assertTrue(world.getChunkManager().getWorldChunk(chunk.x,chunk.z)==null,"Saved-static regression begins with an actually cold distant native chunk");RELOAD_PROBES.computeIfAbsent(world,k->new HashMap<>()).put(chunk.toLong(),this);
            fixture.install();initial=world.getChunkManager().getWorldChunk(chunk.x,chunk.z);t.assertTrue(initial!=null&&loads==1,"Fresh native load precedes installing the static root/helper fixture");
            t.assertTrue(fixture.states.keySet().stream().allMatch(p->new ChunkPos(p).equals(chunk)),"Root and genuine physical helper reside in the same saved native chunk");phaseStart=t.getTick();again();
        }catch(RuntimeException|Error failure){cleanup();throw failure;}}
        void step(){try{t.assertTrue(t.getTick()-phaseStart<390,"Saved static native chunk lifecycle phase completes: "+phase+", loads="+loads+", unloads="+unloads);
            var entities=((dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess)world).bloodborne_dw$profileEntityManager();
            if(phase==0){if(!entities.isLoaded(chunk.toLong())){again();return;}verify();world.save(null,true,false);
                proof(t,"saved-reload",Map.of("status","SAVED_AWAITING_REAL_NATIVE_UNLOAD","pose",pose.key(),"cells",fixture.states.size(),"chunk",List.of(chunk.x,chunk.z)));
                releaseForced();for(var cp:fixture.forced.keySet())world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,cp,0,cp);phase=1;phaseStart=t.getTick();again();return;}
            if(phase==1){if(unloads==0||world.getChunkManager().getWorldChunk(chunk.x,chunk.z)!=null||entities.isLoaded(chunk.toLong())){again();return;}
                t.assertTrue(unloads==1,"Exactly one actual CHUNK_UNLOAD callback was emitted before cold reopen: "+unloads);verifySavedPalette();
                proof(t,"saved-reload",Map.of("status","ACTUALLY_UNLOADED_SAVED_STATIC_PALETTE_BEFORE_NATIVE_TICKET_RELOAD","pose",pose.key(),"cells",fixture.states.size(),"nativeUnloads",unloads,"managerFullChunkPresent",false));
                phase=2;phaseStart=t.getTick();world.getChunkManager().addTicket(RELOAD_TICKET,chunk,0,chunk);held=true;again();return;}
            if(phase==2){var actual=world.getChunkManager().getWorldChunk(chunk.x,chunk.z);if(actual==null){again();return;}
                t.assertTrue(loads==2&&reloaded==actual&&initial!=actual,"A distinct native WorldChunk was really reconstructed from the saved region");t.assertTrue(reloadBeforeFull&&callbackExact,"Saved static root/helper callback completes before FULL publication with exact native palette and no BlockEntity");phase=3;phaseStart=t.getTick();again();return;}
            if(t.getTick()-phaseStart<30){again();return;}verify();var result=StaticArchitectureRuntime.remove(world,root,pose);t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"Reopened whole-object removal still commits through the ordinary exact transaction");
            for(var p:fixture.states.keySet())t.assertTrue(world.getBlockState(p).isAir()&&world.getBlockEntity(p)==null&&world.getBlockState(p).getCollisionShape(world,p).isEmpty(),"Saved/reopened static helper leaves no collision after bounded removal");world.save(null,true,false);
            proof(t,"saved-reload",Map.of("status","PASS_REAL_SAVED_STATIC_ROOT_HELPER_NATIVE_UNLOAD_AND_COLD_RELOAD_BEFORE_FULL","pose",pose.key(),"cells",fixture.states.size(),"nativeLoads",loads,"nativeUnloads",unloads,"distinctNativeChunkInstance",initial!=reloaded,"reloadCallbackBeforeFullPublication",reloadBeforeFull,"exactCallbackPaletteWithoutBlockEntity",callbackExact,"postReloadStableTicks",30,"manualInternalMapOrLightMutation",false));t.complete();cleanup();
        }catch(RuntimeException|Error failure){cleanup();throw failure;}}
        void verify(){for(var e:fixture.states.entrySet()){var p=e.getKey();t.assertTrue(world.getBlockState(p)==e.getValue()&&world.getBlockEntity(p)==null,"Saved native recipe is exact and has no service entity at "+p);sameShape(t,StaticArchitectureCatalog.recipe(e.getValue()).collisionShape(),world.getBlockState(p).getCollisionShape(world,p),"Saved native exact shared collision at "+p);t.assertTrue(CompositeLedger.get(world).at(CompositeData.cell(p)).isEmpty(),"Static saved/reopened cells create no UUID ledger records");}t.assertTrue(StaticArchitectureCatalog.matchesRoot(world.getBlockState(root),pose),"Original full visual pose survives saved native reopen");}
        void verifySavedPalette(){try{var directory=DimensionType.getSaveDirectory(world.getRegistryKey(),world.getServer().getSavePath(WorldSavePath.ROOT)).resolve("region");var file=directory.resolve("r."+chunk.getRegionX()+"."+chunk.getRegionZ()+".mca");t.assertTrue(Files.isRegularFile(file),"Native region exists after genuine save and unload");var saved=new HashSet<NbtCompound>();try(var region=new RegionFile(file,directory,false);var input=region.getChunkInputStream(chunk)){t.assertTrue(input!=null,"Native saved region index contains the actual static chunk");var data=NbtIo.read(input);for(var entry:data.getList("sections",NbtElement.COMPOUND_TYPE)){var section=(NbtCompound)entry;for(var value:section.getCompound("block_states").getList("palette",NbtElement.COMPOUND_TYPE))saved.add((NbtCompound)value);}}
            for(var state:new HashSet<>(fixture.states.values()))t.assertTrue(saved.contains(NbtHelper.fromBlockState(state)),"Stopped native saved palette really contains each expected static recipe");
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}}
        void releaseForced(){fixture.forced.forEach((cp,was)->{if(!was)world.setChunkForced(cp.x,cp.z,false);});}
        void again(){t.runAtTick(t.getTick()+1,this::step);}
        void cleanup(){if(closed)return;closed=true;releaseForced();if(held)world.getChunkManager().removeTicket(RELOAD_TICKET,chunk,0,chunk);held=false;world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,chunk,0,chunk);var probes=RELOAD_PROBES.get(world);if(probes!=null){probes.remove(chunk.toLong());if(probes.isEmpty())RELOAD_PROBES.remove(world);}}
    }
    private static final class LightProbe {
        final TestContext t;final net.minecraft.server.world.ServerWorld world;final BlockPos root,torch;final BlockState state;final StaticFixture fixture;final int torchDistance;
        final Map<ChunkPos,Boolean> forced=new LinkedHashMap<>();final Map<String,Object> samples=new LinkedHashMap<>();boolean closed;
        LightProbe(TestContext t){this.t=t;world=t.getWorld();root=t.getAbsolutePos(new BlockPos(30000,150,128));var recipe=StaticArchitectureCatalog.recipes().stream().filter(r->r.resident()>=0&&r.opacity()==15&&!r.collision().isEmpty()&&!r.fragments().get(r.resident()).pose().outline().getOrDefault(Cell.ORIGIN,List.of()).isEmpty()).findFirst().orElseThrow();var pose=recipe.fragments().get(recipe.resident()).pose();fixture=new StaticFixture(t,List.of(new Placed(pose,root)));torchDistance=Math.max(2,pose.footprint().keySet().stream().mapToInt(Cell::x).max().orElse(0)+2);torch=root.east(torchDistance);state=fixture.states.get(root);}
        void start(){try{
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var cp=new ChunkPos(root.add(x*16,0,z*16));forced.putIfAbsent(cp,world.getForcedChunks().contains(cp.toLong()));world.setChunkForced(cp.x,cp.z,true);world.getChunk(cp.x,cp.z);}
            await(()->sky(root)==15&&sky(root.down())==15,this::install,"Initial native direct sky must be15");
        }catch(RuntimeException|Error failure){cleanup();throw failure;}}
        int sky(BlockPos p){return world.getLightLevel(LightType.SKY,p);}int block(BlockPos p){return world.getLightLevel(LightType.BLOCK,p);}
        void install(){fixture.install();await(()->sky(root)==0&&sky(root.down())==14,this::illuminate,"Source-opacity15 static part casts native one-cell shadow");}
        void illuminate(){samples.put("placedSky",List.of(sky(root),sky(root.down())));world.setBlockState(torch.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);world.setBlockState(torch,Blocks.TORCH.getDefaultState(),Block.NOTIFY_ALL);await(()->block(torch)==14&&block(torch.west())==13&&block(root)==0&&block(root.west())==Math.max(0,11-torchDistance),this::removeTorch,"Actual torch propagates around the opaque static cell and cannot illuminate inside it");}
        void removeTorch(){samples.put("torchBlock",List.of(block(torch),block(torch.west()),block(root),block(root.west())));world.setBlockState(torch,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);await(()->block(torch)==0&&block(root.west())==0,this::destroy,"Ordinary torch removal clears native block light");}
        void destroy(){var player=UtilityV127GameTests.player(world);try{player.changeGameMode(GameMode.SURVIVAL);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,new ItemStack(Items.DIAMOND_PICKAXE));var center=world.getBlockState(root).getOutlineShape(world,root).getBoundingBoxes().get(0).getCenter().add(Vec3d.of(root));player.setPosition(center.x,center.y-player.getStandingEyeHeight(),center.z-2);player.setYaw(0);player.setPitch(0);player.interactionManager.tryBreakBlock(root);}finally{player.discard();}CompositeRuntime.drain(world);await(()->world.getBlockState(root).isAir()&&sky(root)==15&&sky(root.down())==15,this::finish,"Breaking the static part restores native sky without manually resetting light internals");}
        void finish(){try{samples.put("status","PASS_ACTUAL_NATIVE_SKY_SHADOW_TORCH_ADD_REMOVE_AND_SURVIVAL_BREAK");samples.put("afterBreakSky",List.of(sky(root),sky(root.down())));samples.put("directLightingProviderMutation",false);proof(t,"light",samples);t.complete();}finally{cleanup();}}
        void await(BooleanSupplier ready,Runnable next,String why){poll(ready,next,why,t.getTick()+120);}
        void poll(BooleanSupplier ready,Runnable next,String why,long deadline){t.runAtTick(t.getTick()+1,()->{try{if(ready.getAsBoolean()){next.run();return;}t.assertTrue(t.getTick()<deadline,why+"; sky="+sky(root)+"/"+sky(root.down())+", torch="+block(torch)+", behind="+block(root.west()));poll(ready,next,why,deadline);}catch(RuntimeException|Error failure){cleanup();throw failure;}});}
        void cleanup(){if(closed)return;closed=true;fixture.close();for(var p:List.of(torch,torch.down()))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);forced.forEach((cp,was)->{if(!was)world.setChunkForced(cp.x,cp.z,false);});}
    }
    private record Placed(StaticArchitectureCatalog.Pose pose,BlockPos root){}
    private static final class StaticFixture implements AutoCloseable {
        final TestContext t;final List<Placed> owners;final Map<BlockPos,BlockState> states=new TreeMap<>();final Map<ChunkPos,Boolean> forced=new LinkedHashMap<>();
        StaticFixture(TestContext t,List<Placed> owners){this.t=t;this.owners=owners;
            var cells=new TreeSet<BlockPos>();for(var o:owners)for(var cell:o.pose().footprint().keySet())cells.add(o.root().add(cell.x(),cell.y(),cell.z()));
            for(var cell:cells){var needed=owners.stream().filter(o->o.pose().footprint().containsKey(CompositeData.cell(cell).subtract(CompositeData.cell(o.root())))).toList();var resident=owners.stream().filter(o->o.root().equals(cell)).findFirst().orElse(null);
                var recipe=StaticArchitectureCatalog.recipes().stream().filter(r->r.fragments().size()==needed.size()&&(resident==null?r.resident()<0:r.resident()>=0&&r.fragments().get(r.resident()).pose()==resident.pose())&&needed.stream().allMatch(o->r.fragments().stream().anyMatch(f->f.member()&&f.pose()==o.pose()&&f.origin().equals(CompositeData.cell(o.root()).subtract(CompositeData.cell(cell)))))).findFirst().orElseThrow(()->new IllegalStateException("No finite exact subset recipe at "+cell));states.put(cell,StaticArchitectureCatalog.state(recipe.index()));
            }
        }
        void install(){var world=t.getWorld();for(var p:states.keySet())for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var cp=new ChunkPos(p.add(dx*16,0,dz*16));forced.putIfAbsent(cp,world.getForcedChunks().contains(cp.toLong()));world.setChunkForced(cp.x,cp.z,true);world.getChunk(cp.x,cp.z);}for(var row:states.entrySet())world.setBlockState(row.getKey(),row.getValue(),Block.NOTIFY_ALL);}
        @Override public void close(){var world=t.getWorld();for(var o:owners)if(world.getBlockState(o.root()).getBlock() instanceof StaticArchitectureBlock)StaticArchitectureRuntime.remove(world,o.root(),o.pose());for(var p:states.keySet())if(world.getBlockState(p).getBlock() instanceof StaticArchitectureBlock)world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);forced.forEach((cp,was)->{if(!was)world.setChunkForced(cp.x,cp.z,false);});}
    }
    private static void sameShape(TestContext t,VoxelShape expected,VoxelShape actual,String reason){t.assertTrue(!VoxelShapes.matchesAnywhere(expected,actual,BooleanBiFunction.NOT_SAME),reason);}
    private static NbtCompound typed(){var n=new NbtCompound();n.putByte("byte",(byte)-1);n.putShort("short",(short)300);n.putInt("int",123456);n.putLong("long",Long.MIN_VALUE+9);n.putFloat("float",1.25F);n.putDouble("double",-4.75);n.putByteArray("bytes",new byte[]{-1,0,1});n.putString("string","typed architecture rollback");var list=new NbtList();list.add(NbtInt.of(4));list.add(NbtInt.of(7));n.put("list",list);var child=new NbtCompound();child.putBoolean("child",true);n.put("compound",child);n.putIntArray("ints",new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE});n.putLongArray("longs",new long[]{Long.MIN_VALUE,0,Long.MAX_VALUE});return n;}
    private static void proof(TestContext t,String name,Map<String,?> value){try{Files.writeString(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT).resolve("v1274-static-architecture-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception failure){throw new IllegalStateException(failure);}}
}
