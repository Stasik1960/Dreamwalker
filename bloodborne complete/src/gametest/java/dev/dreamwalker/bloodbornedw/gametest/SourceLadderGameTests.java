package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;

/** Source pair installation and lifecycle. Client vertex equivalence is independently audited offline. */
public final class SourceLadderGameTests implements FabricGameTest {
    private static final BlockPos LOCAL=new BlockPos(3,3,3);
    private static final ChunkTicketType<ChunkPos> RELOAD_LEASE=ChunkTicketType.create("dreamwalker_source_ladder_reload_test",Comparator.comparingLong(ChunkPos::toLong));
    private static SourceLadderRuntime.Installation original(ServerWorld world,BlockPos visual,Direction sourceFacing,int variant,PrototypeLadderBlock.Profile profile){
        BlockPos physical=visual.offset(sourceFacing.getOpposite());BlockState art=Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.FACING,sourceFacing).with(BeehiveBlock.HONEY_LEVEL,1);
        world.setBlockState(visual,art,Block.NOTIFY_ALL);BlockState climbing=Blocks.LADDER.getDefaultState().with(LadderBlock.FACING,sourceFacing.getOpposite());world.setBlockState(physical,climbing,Block.NOTIFY_ALL);
        BlockEntity bee=world.getBlockEntity(visual);NbtCompound typed=bee.createNbtWithId();NbtCompound opaque=new NbtCompound();opaque.putString("id","minecraft:bee");opaque.putLong("long",9007199254740993L);opaque.putByteArray("bytes",new byte[]{-1,0,127});opaque.putIntArray("ints",new int[]{-17,3});opaque.putLongArray("longs",new long[]{Long.MIN_VALUE,Long.MAX_VALUE});NbtList list=new NbtList();NbtCompound entry=new NbtCompound();entry.putString("opaque","original source payload");list.add(entry);opaque.put("list",list);NbtCompound beeEntry=new NbtCompound();beeEntry.put("EntityData",opaque);beeEntry.putInt("TicksInHive",7);beeEntry.putInt("MinOccupationTicks",2400);NbtList bees=new NbtList();bees.add(beeEntry);typed.put("Bees",bees);bee.readNbt(typed);bee.markDirty();
        return new SourceLadderRuntime.Installation(visual,physical,art,climbing,bee.createNbtWithId(),null,variant,profile,UUID.randomUUID());
    }
    private static PlayerEntity player(TestContext test){PlayerEntity player=test.createMockSurvivalPlayer();BlockPos p=test.getAbsolutePos(new BlockPos(0,6,0));player.refreshPositionAndAngles(p.getX(),p.getY(),p.getZ(),0,0);return player;}
    private static void clear(ServerWorld world,BlockPos center){for(int x=-2;x<=2;x++)for(int y=-1;y<=1;y++)for(int z=-2;z<=2;z++)world.setBlockState(center.add(x,y,z),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);for(ItemEntity item:world.getEntitiesByClass(ItemEntity.class,new Box(center).expand(5),e->true))item.discard();}
    private static void art(TestContext test,ItemStack item,int variant,PrototypeLadderBlock.Profile profile){test.assertTrue(item.isOf(PrototypeArchitecture.ladderItem(0))&&item.getCount()==1&&PrototypeLadderItem.variant(item)==0&&PrototypeLadderItem.profile(item)==profile,"one canonical item with its 90006 retains declared profile after alias migration");test.assertTrue(item.getSubNbt("BlockEntityTag")==null&&item.getSubNbt("BlockStateTag").getKeys().equals(Set.of("variant","profile")),"pick/drop strips installation owner, facing, source role and opaque source metadata");}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="source_ladder")
    public void exactNorthAndWestPairsPreserveTypedMetadataAndMeasuredCollision(TestContext test){
        ServerWorld world=test.getWorld();BlockPos visual=test.getAbsolutePos(LOCAL);PlayerEntity player=player(test);
        try{for(Direction facing:List.of(Direction.NORTH,Direction.WEST))for(int variant=0;variant<3;variant++)for(PrototypeLadderBlock.Profile profile:PrototypeLadderBlock.Profile.values()){
            clear(world,visual);var request=original(world,visual,facing,variant,profile);var oldPhysical=request.originalPhysical().getCollisionShape(world,request.physicalCell());var oldVisual=request.originalVisual().getCollisionShape(world,visual);
            var result=SourceLadderRuntime.install(world,request,player);test.assertTrue(result.committed(),"recognized exact pair installs: "+result.reason());BlockState state=world.getBlockState(request.physicalCell());
            test.assertTrue(state.isOf(PrototypeArchitecture.LADDER)&&state.get(PrototypeLadderBlock.SOURCE_CLONE)&&state.get(PrototypeLadderBlock.FACING)==facing.getOpposite(),"one same ladder ID uses original physical facing");
            test.assertTrue(!VoxelShapes.matchesAnywhere(oldPhysical,state.getCollisionShape(world,request.physicalCell()),BooleanBiFunction.NOT_SAME)&&!VoxelShapes.matchesAnywhere(oldVisual,world.getBlockState(visual).getCollisionShape(world,visual),BooleanBiFunction.NOT_SAME),"initial physics matches measured thin3/16 ladder and full source beehive cube exactly");
            SourceLadderBlockEntity root=(SourceLadderBlockEntity)world.getBlockEntity(request.physicalCell()),backing=(SourceLadderBlockEntity)world.getBlockEntity(visual);
            test.assertTrue(root.owner().equals(request.owner())&&root.owner().equals(backing.owner())&&root.provenance().getCompound("VisualBlockEntity").equals(request.visualBlockEntity()),"root and fixed backing preserve UUID and all opaque typed original NBT");
            SourceLadderBlockEntity restored=new SourceLadderBlockEntity(request.physicalCell(),state);restored.readNbt(root.createNbtWithId());test.assertTrue(restored.owner().equals(root.owner())&&restored.provenance().equals(root.provenance())&&restored.backing().equals(visual),"static BE save/load retains complete identity and provenance without a ticker");
            art(test,SourceLadderRuntime.BACKING.getPickStack(world,visual,world.getBlockState(visual)),variant,profile);
            player.refreshPositionAndAngles(request.physicalCell().getX()+.5,request.physicalCell().getY(),request.physicalCell().getZ()+.5,0,0);test.assertTrue(player.isClimbing(),"the original climbing cell stays climbable");BlockPos outside=test.getAbsolutePos(new BlockPos(0,6,0));player.refreshPositionAndAngles(outside.getX(),outside.getY(),outside.getZ(),0,0);
            ItemStack middlePicked=PrototypeArchitecture.LADDER.getPickStack(world,request.physicalCell(),state);art(test,middlePicked,variant,profile);
            test.assertTrue(SourceLadderRuntime.remove(world,request.physicalCell(),player,false),"remove original owner before placing its middle-picked normal item");
            BlockPos ordinary=visual.up();world.setBlockState(ordinary.south(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);player.setYaw(-135);player.setStackInHand(Hand.MAIN_HAND,middlePicked);
            var normalResult=middlePicked.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(ordinary.south()),Direction.NORTH,ordinary.south(),false)));
            BlockState normal=world.getBlockState(ordinary);test.assertTrue(normalResult.isAccepted()&&normal.isOf(PrototypeArchitecture.ladderBlock(0))&&!normal.get(PrototypeLadderBlock.SOURCE_CLONE)&&!normal.get(PrototypeLadderBlock.DIAGONAL)&&normal.get(PrototypeLadderBlock.FACING)==Direction.NORTH&&normal.get(PrototypeLadderBlock.VARIANT)==0&&normal.get(PrototypeLadderBlock.PROFILE)==profile&&ordinaryOwner(world,ordinary),"middle pick from actual source installation places ordinary ladder at a flat wall with art alone; result="+normalResult+", state="+normal);
            test.assertTrue(world.getBlockState(request.physicalCell()).isAir()&&world.getBlockState(visual).isAir()&&world.getBlockState(ordinary.south()).isOf(Blocks.STONE),"ordinary re-placement creates no legacy backing/helper and leaves the real foreign wall intact");
        }
        clear(world,visual);var request=original(world,visual,Direction.NORTH,2,PrototypeLadderBlock.Profile.ALT);test.assertTrue(SourceLadderRuntime.install(world,request,player).committed(),"install before technical demotion");BlockPos root=request.physicalCell();world.setBlockState(root.south(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);BlockState ordinary=PrototypeLadderBlock.withYaw(world.getBlockState(root),0).with(PrototypeLadderBlock.SOURCE_CLONE,false);world.setBlockState(root,ordinary,Block.NOTIFY_LISTENERS);test.assertTrue(world.getBlockState(root).equals(ordinary)&&ordinaryOwner(world,root)&&world.getBlockState(visual).isAir(),"same-ID source technical demotion removes old BE and backing while preserving newly supported ordinary state");test.assertTrue(world.getBlockState(root.south()).isOf(Blocks.STONE),"technical demotion preserves actual foreign support");List<ItemEntity> demotionDrops=world.getEntitiesByClass(ItemEntity.class,new Box(visual).expand(5),e->true);test.assertTrue(demotionDrops.size()==1,"technical demotion retires old installed owner with one item");art(test,demotionDrops.get(0).getStack(),2,PrototypeLadderBlock.Profile.ALT);
        test.complete();}finally{clear(world,visual);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="source_ladder")
    public void RemovingEitherMemberYieldsOneNormalItemAndNeverConsumesHoneyZeroCaps(TestContext test){
        ServerWorld world=test.getWorld();BlockPos visual=test.getAbsolutePos(LOCAL);PlayerEntity player=player(test);
        try{for(boolean helper:List.of(false,true))for(boolean directReplacement:List.of(false,true)){
            clear(world,visual);var request=original(world,visual,Direction.NORTH,2,PrototypeLadderBlock.Profile.ALT);BlockPos cap=visual.up();BlockState capState=Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.HONEY_LEVEL,0);world.setBlockState(cap,capState,Block.NOTIFY_ALL);NbtCompound capNbt=world.getBlockEntity(cap).createNbtWithId();test.assertTrue(SourceLadderRuntime.install(world,request,player).committed(),"install before lifecycle removal");
            BlockPos target=helper?visual:request.physicalCell();if(directReplacement)world.setBlockState(target,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);else world.breakBlock(target,true,player);
            test.assertTrue(world.getBlockState(helper?request.physicalCell():visual).isAir(),"breaking/replacing either role removes its verified same-owner counterpart");test.assertTrue(!directReplacement||world.getBlockState(target).isOf(Blocks.GOLD_BLOCK),"replacement block survives whole-owner cleanup");
            List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(visual).expand(5),e->true);test.assertTrue(drops.size()==1,"exactly one item for either member and either removal path, got "+drops.size());art(test,drops.get(0).getStack(),2,PrototypeLadderBlock.Profile.ALT);
            test.assertTrue(world.getBlockState(cap).equals(capState)&&world.getBlockEntity(cap).createNbtWithId().equals(capNbt),"honey0 cap state and typed NBT are never claimed or consumed");
        }test.complete();}finally{clear(world,visual);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="source_ladder")
    public void SourceCloneBuilderKeepsFixedBackingAndRequiresRealAdditionalFaces(TestContext test){
        ServerWorld world=test.getWorld();BlockPos visual=test.getAbsolutePos(LOCAL);PlayerEntity player=player(test);
        player.getAbilities().creativeMode=true;
        try{clear(world,visual);var request=original(world,visual,Direction.NORTH,1,PrototypeLadderBlock.Profile.BASE);test.assertTrue(SourceLadderRuntime.install(world,request,player).committed(),"install");BlockPos root=request.physicalCell();BlockState before=world.getBlockState(root),next=PrototypeArchitecture.LADDER.step45(before);NbtCompound saved=world.getBlockEntity(visual).createNbtWithId();
            test.assertTrue(!SourceLadderRuntime.edit(world,root,before,next,player)&&world.getBlockState(root).equals(before)&&world.getBlockEntity(visual).createNbtWithId().equals(saved),"unsupported45 turn leaves root, fixed helper and all metadata unchanged");
            for(Direction d:Direction.Type.HORIZONTAL)if(!root.offset(d).equals(visual))world.setBlockState(root.offset(d),Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
            ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);player.setStackInHand(Hand.MAIN_HAND,tool);
            for(int i=0;i<8;i++){BlockState current=world.getBlockState(root);var result=dev.dreamwalker.bloodbornedw.architecture.BuildingTool.applyBlock(player,visual,dev.dreamwalker.bloodbornedw.architecture.BuildingTool.Action.ROTATE);test.assertTrue(result.isAccepted()&&world.getBlockState(root).equals(PrototypeArchitecture.LADDER.step45(current)),"server LKM action adapter resolves helper to source owner and rotates45 with actual backing");test.assertTrue(world.getBlockEntity(visual).createNbtWithId().equals(saved),"legacy backing stays fixed and UUID/provenance unchanged");}
            test.assertTrue(world.getBlockState(root).equals(before),"eight supported turns restore same source art, ID and root state");for(Direction d:Direction.Type.HORIZONTAL)if(!root.offset(d).equals(visual))test.assertTrue(world.getBlockState(root.offset(d)).isOf(Blocks.DIAMOND_BLOCK),"no new hidden support replaces actual foreign wall");test.complete();
        }finally{clear(world,visual);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="source_ladder")
    public void ChangedOrDeniedSourcePairAndHoneyZeroRefuseBeforeAnyWrite(TestContext test){
        ServerWorld world=test.getWorld();BlockPos visual=test.getAbsolutePos(LOCAL);PlayerEntity player=player(test);
        try{clear(world,visual);var request=original(world,visual,Direction.NORTH,0,PrototypeLadderBlock.Profile.BASE);player.getAbilities().allowModifyWorld=false;test.assertTrue(!SourceLadderRuntime.install(world,request,player).committed()&&world.getBlockState(visual).equals(request.originalVisual())&&world.getBlockState(request.physicalCell()).equals(request.originalPhysical()),"denied installation preserves both actual source states");player.getAbilities().allowModifyWorld=true;
            NbtCompound changed=world.getBlockEntity(visual).createNbtWithId();changed.put("FlowerPos",NbtHelper.fromBlockPos(visual.add(7,1,2)));world.getBlockEntity(visual).readNbt(changed);changed=world.getBlockEntity(visual).createNbtWithId();test.assertTrue(!SourceLadderRuntime.install(world,request,player).committed()&&world.getBlockEntity(visual).createNbtWithId().equals(changed),"changed typed source metadata rejects stale plan atomically");
            clear(world,visual);request=original(world,visual,Direction.NORTH,0,PrototypeLadderBlock.Profile.BASE);BlockState cap=request.originalVisual().with(BeehiveBlock.HONEY_LEVEL,0);world.setBlockState(visual,cap,Block.NOTIFY_ALL);var forbidden=new SourceLadderRuntime.Installation(visual,request.physicalCell(),cap,request.originalPhysical(),world.getBlockEntity(visual).createNbtWithId(),null,0,PrototypeLadderBlock.Profile.BASE,null);test.assertTrue(!SourceLadderRuntime.install(world,forbidden,player).committed()&&world.getBlockState(visual).equals(cap),"honey0 cap cannot become a source backing installation");test.complete();
        }finally{clear(world,visual);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="source_ladder")
    public void StaleBackingCannotDeleteReplacementOwnerAndPickReplacesAsOrdinary(TestContext test){
        ServerWorld world=test.getWorld();BlockPos visual=test.getAbsolutePos(LOCAL);PlayerEntity player=player(test);
        try{clear(world,visual);var request=original(world,visual,Direction.NORTH,2,PrototypeLadderBlock.Profile.ALT);test.assertTrue(SourceLadderRuntime.install(world,request,player).committed(),"install");BlockPos root=request.physicalCell();SourceLadderBlockEntity stale=(SourceLadderBlockEntity)world.getBlockEntity(visual);SourceLadderBlockEntity replacement=(SourceLadderBlockEntity)world.getBlockEntity(root);replacement.set(UUID.randomUUID(),root,visual,replacement.provenance());NbtCompound replacementNbt=replacement.createNbtWithId();BlockState replacementState=world.getBlockState(root);
            test.assertTrue(SourceLadderRuntime.resolveRoot(world,visual)==null,"UUID mismatch cannot resolve an old helper to a replacement owner");world.setBlockState(visual,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);test.assertTrue(world.getBlockState(root).equals(replacementState)&&world.getBlockEntity(root).createNbtWithId().equals(replacementNbt),"stale backing replacement cannot delete a new UUID root");
            clear(world,visual);request=original(world,visual,Direction.NORTH,2,PrototypeLadderBlock.Profile.ALT);test.assertTrue(SourceLadderRuntime.install(world,request,player).committed(),"install before misattached metadata check");root=request.physicalCell();SourceLadderBlockEntity valid=(SourceLadderBlockEntity)world.getBlockEntity(root);BlockPos third=visual.up();world.setBlockState(third,SourceLadderRuntime.BACKING.getDefaultState(),Block.NOTIFY_LISTENERS);SourceLadderBlockEntity misattached=(SourceLadderBlockEntity)world.getBlockEntity(third);misattached.set(valid.owner(),root,visual,valid.provenance());test.assertTrue(SourceLadderRuntime.resolveRoot(world,third)==null&&!SourceLadderRuntime.remove(world,third,player,true),"matching metadata at a third cell cannot grant object ownership");world.setBlockState(third,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);test.assertTrue(SourceLadderRuntime.resolveRoot(world,visual).equals(root)&&world.getBlockState(third).isOf(Blocks.GOLD_BLOCK),"replacing misattached helper keeps the actual two-cell pair and foreign replacement intact");
            // A normal item cannot import a clone flag or source block entity, even when hand-edited.
            clear(world,visual);BlockPos ordinary=visual;world.setBlockState(ordinary.south(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);ItemStack stack=PrototypeArchitecture.LADDER.artisticStack(PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.VARIANT,2).with(PrototypeLadderBlock.PROFILE,PrototypeLadderBlock.Profile.ALT));stack.getOrCreateSubNbt("BlockStateTag").putString("source_clone","true");stack.getOrCreateSubNbt("BlockEntityTag").putUuid("Owner",UUID.randomUUID());NbtCompound original=stack.getNbt().copy();player.setYaw(180);player.setStackInHand(Hand.MAIN_HAND,stack);var result=stack.useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(ordinary.south()),Direction.NORTH,ordinary.south(),false)));test.assertTrue(result.isAccepted()&&!world.getBlockState(ordinary).get(PrototypeLadderBlock.SOURCE_CLONE)&&ordinaryOwner(world,ordinary),"ordinary placement cannot create source technical roles from item NBT");test.assertTrue(original.equals(stack.getNbt()),"placement sanitizes a copy and preserves original held stack NBT");test.complete();
        }finally{clear(world,visual);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="source_ladder")
    public void NativeSurvivalBreakPermissionGuardChecksWholePairAndRejectsAmbiguousOwner(TestContext test){
        ServerWorld world=test.getWorld();BlockPos visual=test.getAbsolutePos(LOCAL);PlayerEntity player=player(test);
        try{clear(world,visual);var request=original(world,visual,Direction.NORTH,1,PrototypeLadderBlock.Profile.BASE);test.assertTrue(SourceLadderRuntime.install(world,request,player).committed(),"install guarded source pair");BlockPos root=request.physicalCell();NbtCompound originalRoot=world.getBlockEntity(root).createNbtWithId(),originalBacking=world.getBlockEntity(visual).createNbtWithId();
            try{SourceLadderRuntime.runInternalWrite(()->{test.assertTrue(SourceLadderRuntime.writing(),"bounded journal scope suppresses source callbacks");SourceLadderRuntime.runInternalWrite(()->test.assertTrue(SourceLadderRuntime.writing(),"nested scope preserves inherited write guard"));test.assertTrue(SourceLadderRuntime.writing(),"inner scope restores outer guard");throw new IllegalStateException("injected rollback exception");});}catch(IllegalStateException expected){test.assertTrue(expected.getMessage().equals("injected rollback exception"),"only injected journal exception propagates");}test.assertTrue(!SourceLadderRuntime.writing(),"exception restores original write guard before subsequent native permission checks");
            player.getAbilities().allowModifyWorld=false;
            for(BlockPos target:List.of(root,visual))test.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,target,world.getBlockState(target),world.getBlockEntity(target)),"native break preflight refuses player without permission at either targeted role");
            test.assertTrue(world.getBlockEntity(root).createNbtWithId().equals(originalRoot)&&world.getBlockEntity(visual).createNbtWithId().equals(originalBacking),"denied native break leaves complete original owner state unchanged");player.getAbilities().allowModifyWorld=true;
            for(BlockPos target:List.of(root,visual))test.assertTrue(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,target,world.getBlockState(target),world.getBlockEntity(target)),"valid permitted whole pair passes native survival break preflight");
            SourceLadderBlockEntity backing=(SourceLadderBlockEntity)world.getBlockEntity(visual);backing.set(UUID.randomUUID(),root,visual,backing.provenance());
            for(BlockPos target:List.of(root,visual))test.assertTrue(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,target,world.getBlockState(target),world.getBlockEntity(target)),"ambiguous UUID ownership cancels native survival break instead of granting cleanup authority");
            BlockPos foreign=visual.up();world.setBlockState(foreign,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);test.assertTrue(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world,player,foreign,world.getBlockState(foreign),null),"ordinary foreign block remains outside source owner permissions/cleanup");test.complete();
        }finally{clear(world,visual);player.discard();}
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=400,batchId="source_ladder_reload")
    public void ActualChunkSaveUnloadReloadDefersAcrossBoundaryWithoutForceLoadingOrDeadlock(TestContext test){
        ServerWorld world=test.getWorld();BlockPos origin=test.getAbsolutePos(LOCAL);int cx=(origin.getX()>>4)+512,cz=(origin.getZ()>>4)+512;BlockPos visual=new BlockPos(cx*16+15,80,cz*16+8);BlockPos physical=visual.east();ChunkPos artChunk=new ChunkPos(visual),rootChunk=new ChunkPos(physical);
        // Native getChunk deliberately exercises actual fresh-load callbacks before installation.
        world.getChunk(artChunk.x,artChunk.z);world.getChunk(rootChunk.x,rootChunk.z);var request=original(world,visual,Direction.WEST,2,PrototypeLadderBlock.Profile.ALT);test.assertTrue(SourceLadderRuntime.install(world,request,null).committed(),"trusted source port across actual chunk boundary");
        NbtCompound rootNbt=world.getBlockEntity(physical).createNbtWithId(),artNbt=world.getBlockEntity(visual).createNbtWithId();world.getChunkManager().save(true);
        for(int x=artChunk.x-1;x<=rootChunk.x+1;x++)for(int z=artChunk.z-1;z<=artChunk.z+1;z++){ChunkPos p=new ChunkPos(x,z);world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,p,0,p);}
        int[] phase={0};long[] since={0};test.runAtEveryTick(()->{
            if(phase[0]==0){if(world.getChunkManager().getWorldChunk(artChunk.x,artChunk.z)!=null||world.getChunkManager().getWorldChunk(rootChunk.x,rootChunk.z)!=null)return;world.getChunk(rootChunk.x,rootChunk.z);world.getChunkManager().addTicket(RELOAD_LEASE,rootChunk,0,rootChunk);phase[0]=1;since[0]=test.getTick();return;}
            if(phase[0]==1&&test.getTick()-since[0]>=2){test.assertTrue(world.getChunkManager().getWorldChunk(artChunk.x,artChunk.z)==null,"checking reloaded root never force-loads its missing backing chunk");test.assertTrue(world.getBlockEntity(physical).createNbtWithId().equals(rootNbt),"orphan validation defers while backing chunk is absent and retains typed root");world.getChunk(artChunk.x,artChunk.z);world.getChunkManager().addTicket(RELOAD_LEASE,artChunk,0,artChunk);phase[0]=2;since[0]=test.getTick();return;}
            if(phase[0]==2&&test.getTick()-since[0]>=2){
                BlockEntity actualRoot=world.getBlockEntity(physical),actualBacking=world.getBlockEntity(visual);BlockPos resolved=SourceLadderRuntime.resolveRoot(world,visual);
                String state="physical="+physical+" state="+world.getBlockState(physical)+"; backing="+visual+" state="+world.getBlockState(visual)+"; resolved="+resolved;
                test.assertTrue(physical.equals(resolved),"actual disk chunk reload restores paired owner: "+state+"; rootNBT="+(actualRoot==null?"null":actualRoot.createNbtWithId())+"; backingNBT="+(actualBacking==null?"null":actualBacking.createNbtWithId()));
                test.assertTrue(actualRoot!=null&&actualRoot.createNbtWithId().equals(rootNbt),"actual disk reload preserves complete typed root provenance: "+state+"; expected="+rootNbt+"; actual="+(actualRoot==null?"null":actualRoot.createNbtWithId()));
                test.assertTrue(actualBacking!=null&&actualBacking.createNbtWithId().equals(artNbt),"actual disk reload preserves complete typed backing provenance: "+state+"; expected="+artNbt+"; actual="+(actualBacking==null?"null":actualBacking.createNbtWithId()));
                SourceLadderRuntime.remove(world,physical,null,false);world.getChunkManager().removeTicket(RELOAD_LEASE,rootChunk,0,rootChunk);world.getChunkManager().removeTicket(RELOAD_LEASE,artChunk,0,artChunk);phase[0]=3;test.complete();
            }
        });
    }
    private static boolean ordinaryOwner(ServerWorld world,BlockPos pos){return world.getBlockEntity(pos) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity own&&!(own instanceof SourceLadderBlockEntity)&&own.resident()!=null&&own.payload().isEmpty();}

}
