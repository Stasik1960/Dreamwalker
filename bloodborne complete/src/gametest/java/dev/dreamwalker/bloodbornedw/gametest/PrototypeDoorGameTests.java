package dev.dreamwalker.bloodbornedw.gametest;
import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Technical acceptance of the proposed door; authored opening and manual visuals remain pending. */
public final class PrototypeDoorGameTests implements FabricGameTest {
    private static final String TEMPLATE="bloodborne_dw:window_test", KEY="prototype_double_door";
    private static final BlockPos LOCAL=new BlockPos(8,4,8);

    @GameTest(templateName=TEMPLATE,tickLimit=240,batchId="prototype_door")
    public void ordinaryItemReachesEightYawsAndBothArtProfiles(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();Set<Integer> turns=new HashSet<>();
        try{
            for(int yaw=0;yaw<8;yaw++)for(String profile:List.of("base","alt")){
                clear(world,root);floor(world,root);outside(player,root,yaw*45);
                ItemStack stack=art(profile,2);NbtCompound original=stack.getNbt().copy();
                test.assertTrue(place(world,root,player,stack).isAccepted(),"ordinary door item accepts yaw"+yaw+"/"+profile);
                BlockState state=world.getBlockState(root);test.assertTrue(state.isOf(block()),"one door type is used for every rotation");
                turns.add(state.get(CompositeRootBlock.ROTATION));
                test.assertTrue(state.get(CompositeRootBlock.PROFILE).asString().equals(profile)&&stack.getCount()==1&&original.equals(stack.getNbt()),"one item consumed, art and remaining item NBT preserved");
                test.assertTrue(CompositeRuntime.remove(world,resident(world,root),player,false).outcome()==Outcome.COMMITTED,"whole-door cleanup succeeds");
            }
            test.assertTrue(turns.size()==8,"ordinary yaw reaches all eight orientations without commands");test.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=TEMPLATE,tickLimit=180,batchId="prototype_door")
    public void actualMovementAndOppositeSideClicksOpenARealPassage(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);floor(world,root);outside(player,root,0);test.assertTrue(place(world,root,player,art("base",1)).isAccepted(),"door ordinary placement succeeds");
            Owner owner=resident(world,root);test.assertTrue(travel(player,root)<1.5,"actual player movement is stopped by closed leaves");
            click(test,world,root,player,Direction.NORTH,new Vec3d(.5,.8,.125));
            test.assertTrue(world.getBlockState(root).get(CompositeRootBlock.OPEN),"north-side click opens one central leaf");
            double openedTravel=travel(player,root);
            test.assertTrue(Math.abs(openedTravel-3)<1e-6,"actual player movement crosses the open aperture; actual dz="+openedTravel+"; root="+world.getBlockState(root)+"; collision="+world.getBlockState(root).getCollisionShape(world,root).getBoundingBoxes());
            test.assertTrue(frameCollision(world,root),"fixed jamb remains solid when open");
            click(test,world,root,player,Direction.SOUTH,new Vec3d(-.9375,.8,.125));
            test.assertTrue(!world.getBlockState(root).get(CompositeRootBlock.OPEN)&&resident(world,root).equals(owner),"south-side frame click closes same instance");
            test.assertTrue(travel(player,root)<1.5,"closing restores only the intended aperture collision");test.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_door")
    public void nearFloorCenterAndOriginalSidePassageRemainOpen(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);floor(world,root);outside(player,root,0);
            test.assertTrue(place(world,root,player,art("base",1)).isAccepted(),"ordinary door placement succeeds");Owner owner=resident(world,root);
            test.assertTrue(sourceTravel(player,root,.5)<3.9,"closed central leaf blocks its opening");
            test.assertTrue(Math.abs(sourceTravel(player,root,-.6)-4)<1e-6,"static side decoration does not create a new obstacle in the measured source passage");
            test.assertTrue(CompositeRuntime.transition(world,owner,world.getBlockState(root).with(CompositeRootBlock.OPEN,true),player).outcome()==Outcome.COMMITTED,"open pose commits");
            for(double x:List.of(.5,-.6)){
                double actual=sourceTravel(player,root,x);
                test.assertTrue(Math.abs(actual-4)<1e-6,"open center/original side remains passable at actual source feet+.05; x="+x+" dz="+actual);
            }
            test.assertTrue(resident(world,root).equals(owner)&&frameCollision(world,root),"same instance and outer visible hard jamb retained");test.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_door")
    public void decorativeOverlapIsAllowedAndActualRootConflictIsAtomic(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL),next=root.east(3);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);floor(world,root);floor(world,next);outside(player,root,0);
            test.assertTrue(place(world,root,player,art("base",1)).isAccepted(),"first door placed");Owner first=resident(world,root);
            floor(world,root.east());test.assertTrue(!place(world,root.east(),player,art("alt",1)).isAccepted(),"actual positive volume frame overlap is rejected for a new object");
            test.assertTrue(place(world,next,player,art("alt",1)).isAccepted(),"touching solid headers and overlapping decorative edges do not reserve a full visual AABB");Owner second=resident(world,next);
            test.assertTrue(!first.equals(second),"adjacent doors have distinct instance UUIDs");
            BlockState saved=world.getBlockState(root);NbtCompound savedNbt=world.getBlockEntity(root).createNbt();NbtCompound ledger=CompositeLedger.get(world).writeNbt(new NbtCompound());
            test.assertTrue(CompositeRuntime.place(world,root,block().getDefaultState(),UUID.randomUUID(),player).outcome()==Outcome.REJECTED,"a second primary root in the same cell is a real conflict");
            test.assertTrue(saved.equals(world.getBlockState(root))&&savedNbt.equals(world.getBlockEntity(root).createNbt())&&ledger.equals(CompositeLedger.get(world).writeNbt(new NbtCompound())),"rejected root conflict makes no partial world or ledger writes");
            test.assertTrue(CompositeRuntime.remove(world,first,player,false).outcome()==Outcome.COMMITTED,"first overlapping owner removed");
            test.assertTrue(resident(world,next).equals(second)&&world.getBlockState(next).isOf(block()),"other root and overlap contribution survive removal");
            test.assertTrue(CompositeRuntime.remove(world,second,player,true).outcome()==Outcome.COMMITTED,"last object removed with a drop");
            List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(6),e->e.getStack().isOf(item()));
            test.assertTrue(drops.size()==1&&drops.get(0).getStack().getCount()==1,"one logical object drops one item");test.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_door")
    public void foreignChestAndTwoRealNeighborChangesPreserveExactData(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL),foreign=root.west(),neighbor=root.south();PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);floor(world,root);outside(player,root,0);world.setBlockState(foreign,Blocks.CHEST.getDefaultState(),Block.NOTIFY_ALL);
            ChestBlockEntity chest=(ChestBlockEntity)world.getBlockEntity(foreign);chest.setStack(0,new ItemStack(Items.EMERALD,11));chest.setCustomName(net.minecraft.text.Text.literal("Foreign source chest"));
            BlockState foreignState=world.getBlockState(foreign);NbtCompound data=chest.createNbt();
            test.assertTrue(place(world,root,player,art("alt",1)).isAccepted(),"soft overhang across native chest remains allowed");Owner owner=resident(world,root);BlockState saved=world.getBlockState(root);
            for(BlockState state:List.of(Blocks.ACACIA_STAIRS.getDefaultState(),Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState())){
                world.setBlockState(neighbor,state,Block.NOTIFY_ALL);test.assertTrue(world.getBlockState(root).equals(saved),"adding actual source-family neighbor does not turn door into stairs/wall");
                world.setBlockState(neighbor,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);test.assertTrue(world.getBlockState(root).equals(saved),"removing actual neighbor preserves independent door state");
            }
            test.assertTrue(CompositeRuntime.transition(world,owner,saved.with(CompositeRootBlock.OPEN,true).with(CompositeRootBlock.ROTATION,1),player).outcome()==Outcome.COMMITTED,"open plus global45 commits without decorative-overlap rejection");
            test.assertTrue(world.getBlockState(foreign).equals(foreignState)&&world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(data),"foreign native chest instance and all NBT survive opening and rotation");
            test.assertTrue(CompositeRuntime.remove(world,owner,player,false).outcome()==Outcome.COMMITTED,"whole removal commits");
            test.assertTrue(world.getBlockState(foreign).equals(foreignState)&&world.getBlockEntity(foreign)==chest&&chest.createNbt().equals(data),"whole removal leaves native foreign chest data intact");test.complete();
        }finally{clear(world,root);player.discard();}
    }

    @GameTest(templateName=TEMPLATE,tickLimit=220,batchId="prototype_door")
    public void sharedBuilderTurns45AndSuitableSlabSupportDoesNotRequireFullCube(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,net.minecraft.block.enums.SlabType.TOP),Block.NOTIFY_ALL);outside(player,root,0);
            test.assertTrue(place(world,root,player,art("base",1)).isAccepted(),"top half slab provides suitable contact without a full cube");Owner owner=resident(world,root);
            player.getAbilities().creativeMode=true;
            for(int turn=1;turn<=8;turn++){
                aimAtAnyRootSelection(world,root,player);ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);player.setStackInHand(Hand.MAIN_HAND,tool);
                BlockHitResult hit=new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,false);
                ActionResult used=BuildingTool.applyBlock(player,root,BuildingTool.Action.ROTATE);
                test.assertTrue(used.isAccepted(),"common tool rotates actual installed door45; turn="+turn+"; result="+used+"; target="+CompositeRuntime.target(world,root,player)+"; transaction="+CompositeRuntime.lastResult()+"; "+CompositeRuntime.debugTarget(world,root,player));
                test.assertTrue(world.getBlockState(root).get(CompositeRootBlock.ROTATION)==turn%8&&resident(world,root).equals(owner),"rotation keeps same registry and instance identity");
            }
            BlockState before=world.getBlockState(root);player.setSneaking(true);aimAtAnyRootSelection(world,root,player);ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);player.setStackInHand(Hand.MAIN_HAND,tool);
            test.assertTrue(BuildingTool.applyBlock(player,root,BuildingTool.Action.PROFILE).isAccepted(),"explicit profile mode in server left-click adapter changes decoration");
            test.assertTrue(world.getBlockState(root).get(CompositeRootBlock.PROFILE)!=before.get(CompositeRootBlock.PROFILE)&&world.getBlockState(root).get(CompositeRootBlock.OPEN).equals(before.get(CompositeRootBlock.OPEN)),"art-only switch does not open door");test.complete();
        }finally{player.setSneaking(false);clear(world,root);player.discard();}
    }

    private static CompositeRootBlock block(){return CompositeArchitecture.kindBlock(KEY);}
    @GameTest(templateName=TEMPLATE,tickLimit=100,batchId="prototype_door")
    public void onlyWholeCentralLeafMovesAroundItsBoundaryAndClosedArtworkStaysPartitioned(TestContext test){
        var closed=block().spec.pose(block().getDefaultState());var opened=block().spec.pose(block().getDefaultState().with(CompositeRootBlock.OPEN,true));
        test.assertTrue(closed.parts().size()==4&&opened.parts().size()==4,"two static side panels, one central leaf, one source header");int moving=0;
        for(int i=0;i<4;i++){if(!closed.parts().get(i).equals(opened.parts().get(i))){moving++;var part=opened.parts().get(i);test.assertTrue(part.model().getPath().endsWith("central_leaf")&&part.extraYaw()==90&&part.extraPivot().x()==0,"only unsplit central source rectangle swings at its side boundary");}}
        test.assertTrue(moving==1,"one moving leaf rather than two halves");test.complete();
    }
    private static Item item(){return CompositeArchitecture.kindItem(KEY);}
    @GameTest(templateName=TEMPLATE,tickLimit=140,batchId="prototype_door")
    public void survivalHeldToolBlocksDoorUntilItIsRemoved(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);floor(world,root);outside(player,root,0);
            test.assertTrue(place(world,root,player,art("base",1)).isAccepted(),"ordinary door placed");Owner owner=resident(world,root);
            for(Hand toolHand:Hand.values()){
                player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);
                player.setStackInHand(toolHand,new ItemStack(PrototypeArchitecture.BUILDER_TOOL));
                Vec3d fixedSide=Vec3d.of(root).add(-.9375,.8,.125);aim(player,fixedSide.add(0,0,-3),fixedSide);
                Vec3d eye=player.getEyePos(),end=eye.add(player.getRotationVec(1).multiply(6));
                BlockHitResult hit=world.raycast(new RaycastContext(eye,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));
                test.assertTrue(hit.getType()==HitResult.Type.BLOCK,"actual door selection with tool in "+toolHand);
                BlockState before=world.getBlockState(root),selected=world.getBlockState(hit.getBlockPos());
                test.assertTrue(selected.getBlock().onUse(selected,world,hit.getBlockPos(),player,Hand.MAIN_HAND,hit).isAccepted(),"held tool consumes ordinarydoorRMB");
                BlockState after=world.getBlockState(root);
                test.assertTrue(after.get(CompositeRootBlock.OPEN)==before.get(CompositeRootBlock.OPEN)&&after.get(CompositeRootBlock.ROTATION).equals(before.get(CompositeRootBlock.ROTATION))&&resident(world,root).equals(owner),"V10 held tool never opensdoor");
                test.assertTrue(BuildingTool.applyBlock(player,root,BuildingTool.Action.PROFILE)==ActionResult.FAIL&&world.getBlockState(root).equals(after),"same survival tool does not grant decorative editing rights");
            }
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY);aimAtAnyRootSelection(world,root,player);test.assertTrue(CompositeRuntime.use(world,root,player).isAccepted()&&world.getBlockState(root).get(CompositeRootBlock.OPEN),"removing tool restores ordinarydooropening");
            test.complete();
        }finally{clear(world,root);player.discard();}
    }
    private static Owner resident(ServerWorld world,BlockPos root){return ((CompositeBlockEntity)world.getBlockEntity(root)).resident();}
    private static ItemStack art(String profile,int count){ItemStack stack=new ItemStack(item(),count);NbtCompound tag=stack.getOrCreateSubNbt("BlockStateTag");tag.putString("variant","0");tag.putString("profile",profile);return stack;}
    private static void floor(ServerWorld world,BlockPos pos){world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
    private static void outside(PlayerEntity player,BlockPos root,float yaw){player.setPosition(root.getX()+.5,root.getY(),root.getZ()+6);player.setYaw(yaw);player.setPitch(0);}
    private static ActionResult place(ServerWorld world,BlockPos root,PlayerEntity player,ItemStack stack){player.setStackInHand(Hand.MAIN_HAND,stack);return item().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.of(root.down()).add(.5,1,.5),Direction.UP,root.down(),false)));}
    private static double travel(PlayerEntity player,BlockPos root){player.setPosition(root.getX()+.5,root.getY()+.25,root.getZ()-1);double before=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,3));return player.getZ()-before;}
    private static double sourceTravel(PlayerEntity player,BlockPos root,double x){player.setPosition(root.getX()+x,root.getY()+.05,root.getZ()-2.5);double before=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,4));return player.getZ()-before;}
    private static boolean frameCollision(ServerWorld world,BlockPos root){return !world.isSpaceEmpty(new Box(root.getX()-.99,root.getY()+.5,root.getZ()+.07,root.getX()-.88,root.getY()+1.5,root.getZ()+.18));}
    private static void aim(PlayerEntity player,Vec3d eye,Vec3d target){player.setPosition(eye.x,eye.y-player.getStandingEyeHeight(),eye.z);Vec3d d=target.subtract(eye);player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setHeadYaw(player.getYaw());player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    private static void click(TestContext test,ServerWorld world,BlockPos root,PlayerEntity player,Direction side,Vec3d local){Vec3d target=Vec3d.of(root).add(local),eye=target.add(0,0,side==Direction.NORTH?-3:3);aim(player,eye,target);player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);BlockHitResult hit=world.raycast(new RaycastContext(eye,target.add(0,0,side==Direction.NORTH?1:-1),RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player));test.assertTrue(hit.getType()==HitResult.Type.BLOCK,"actual selection ray from"+side);BlockState selected=world.getBlockState(hit.getBlockPos());test.assertTrue(selected.getBlock().onUse(selected,world,hit.getBlockPos(),player,Hand.MAIN_HAND,hit).isAccepted(),"actual clicked door part resolves its root from"+side);}
    private static void aimAtAnyRootSelection(ServerWorld world,BlockPos root,PlayerEntity player){var boxes=world.getBlockState(root).getOutlineShape(world,root).getBoundingBoxes();if(boxes.isEmpty())throw new AssertionError("Door root has no selection");Box b=boxes.get(0);Vec3d target=Vec3d.of(root).add((b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2);aim(player,target.add(0,0,-2),target);}
    private static void clear(ServerWorld world,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-5,-3,-5),root.add(5,5,5)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);for(ItemEntity item:world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(6),e->true))item.discard();}
}
