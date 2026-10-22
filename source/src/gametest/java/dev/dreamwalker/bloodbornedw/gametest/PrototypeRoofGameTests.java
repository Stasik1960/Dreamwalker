package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;

/** Source art remains sloped; physical geometry is the accepted axis-aligned base cube. */
public final class PrototypeRoofGameTests implements FabricGameTest {
    private static final String TEMPLATE="bloodborne_dw:window_test",KEY="prototype_roof";
    private static final BlockPos LOCAL=new BlockPos(8,4,8);
    private static CompositeRootBlock block(){return CompositeArchitecture.kindBlock(KEY);}
    private static Item item(){return CompositeArchitecture.kindItem(KEY);}
    private static Owner owner(ServerWorld world,BlockPos pos){return ((CompositeBlockEntity)world.getBlockEntity(pos)).resident();}
    @GameTest(templateName=TEMPLATE,tickLimit=240,batchId="prototype_roof")
    public void ordinaryItemsReachEightYawsAndBothProfilesOnBothActualSlabHeights(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();Set<Integer> poses=new HashSet<>();
        try{
            for(int yaw=0;yaw<8;yaw++)for(String profile:List.of("base","alt"))for(SlabType slab:List.of(SlabType.TOP,SlabType.BOTTOM)){
                clear(world,root);BlockState support=Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,slab);world.setBlockState(root.down(),support,Block.NOTIFY_ALL);outside(player,root,yaw*45);
                ItemStack stack=art(profile,2);NbtCompound nbt=stack.getNbt().copy();test.assertTrue(place(player,stack,root).isAccepted(),"roof item supports yaw/profile on real "+slab+" slab");
                BlockState state=world.getBlockState(root);poses.add(state.get(CompositeRootBlock.ROTATION));
                test.assertTrue(state.isOf(block())&&state.get(CompositeRootBlock.ROTATION)==yaw&&state.get(CompositeRootBlock.PROFILE).asString().equals(profile)&&!state.get(CompositeRootBlock.OPEN),"same independent fixed roof identity/art is placed at the requested yaw");
                CompositeBlockEntity entity=(CompositeBlockEntity)world.getBlockEntity(root);test.assertTrue(Math.abs(entity.mountY()-(slab==SlabType.BOTTOM?-.5:0))<1e-8,"render and sparse physical shape seat on actual support height");
                test.assertTrue(stack.getCount()==1&&stack.getNbt().equals(nbt)&&world.getBlockState(root.down()).equals(support),"one item consumed; remaining art NBT and foreign slab preserved");
                test.assertTrue(CompositeRuntime.remove(world,owner(world,root),player,false).outcome()==Outcome.COMMITTED,"whole normalized roof removes atomically");
            }test.assertTrue(poses.size()==8,"all eight ordinary item poses are reached");test.complete();
        }finally{clear(world,root);player.discard();}
    }
    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_roof")
    public void sameJungleStairsCarrierFixturesCannotSwitchIndependentRoofAndWindow(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL),window=root.east(4);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);for(BlockPos pos:List.of(root,window))world.setBlockState(pos.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);outside(player,root,0);
            test.assertTrue(place(player,art("alt",1),root).isAccepted(),"independent source roof placed");Owner roof=owner(world,root);BlockState roofState=world.getBlockState(root);
            ItemStack windowStack=new ItemStack(CompositeArchitecture.kindItem("prototype_wood_window"));windowStack.getOrCreateSubNbt("BlockStateTag").putString("profile","base");
            test.assertTrue(place(player,windowStack,window).isAccepted(),"unrelated source wood window from same carrier placed independently");Owner windowOwner=owner(world,window);BlockState windowState=world.getBlockState(window);
            test.assertTrue(!roof.registryId().equals(windowOwner.registryId())&&!(world.getBlockState(root).getBlock() instanceof net.minecraft.block.StairsBlock),"two original jungle-stairs meanings have independent registered identities");
            for(BlockHalf half:List.of(BlockHalf.TOP,BlockHalf.BOTTOM))for(int cycle=0;cycle<2;cycle++){
                BlockPos sourceFixture=root.north();BlockState carrier=Blocks.JUNGLE_STAIRS.getDefaultState().with(StairsBlock.FACING,Direction.NORTH).with(StairsBlock.HALF,half).with(StairsBlock.SHAPE,StairShape.STRAIGHT);
                world.setBlockState(sourceFixture,carrier,Block.NOTIFY_ALL);CompositeRuntime.drain(world);
                test.assertTrue(world.getBlockState(root).equals(roofState)&&world.getBlockState(window).equals(windowState),"source half="+half+" neighbor cannot switch either independent object into another carrier meaning");
                world.setBlockState(sourceFixture,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);
                test.assertTrue(world.getBlockState(root).equals(roofState)&&world.getBlockState(window).equals(windowState),"removing source carrier preserves both frozen independent states");
            }
            CompositeRuntime.remove(world,roof,player,false);test.assertTrue(owner(world,window).equals(windowOwner)&&world.getBlockState(window).equals(windowState),"roof removal leaves unrelated window identity intact");test.complete();
        }finally{clear(world,root);player.discard();}
    }
    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_roof")
    public void baseCubeRemainsAxisAlignedAtAllVisualYawsAndFinsRemainSelectionOnly(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);outside(player,root,0);test.assertTrue(place(player,art("base",1),root).isAccepted(),"source-normalized roof placed");
            test.assertTrue(!world.isSpaceEmpty(new Box(root.getX()+.03,root.getY()+.05,root.getZ()+.60,root.getX()+.15,root.getY()+.15,root.getZ()+.72)),"base cube has real player collision throughout its unit volume");
            test.assertTrue(world.isSpaceEmpty(new Box(root.getX()+.03,root.getY()+1.05,root.getZ()+.60,root.getX()+.15,root.getY()+1.15,root.getZ()+.72)),"sloped visual volume above the base cube remains physically empty");
            player.setStackInHand(Hand.MAIN_HAND,CompositeArchitecture.BUILDER.getDefaultStack());var footprint=block().spec.footprint(world.getBlockState(root),0);boolean finGap=false;
            outer:for(var entry:footprint.entrySet())for(ObjectGeometry.Box selected:entry.getValue().selection()){
                Vec3d middle=new Vec3d((selected.minX()+selected.maxX())/2,(selected.minY()+selected.maxY())/2,(selected.minZ()+selected.maxZ())/2);
                Box probe=new Box(middle.x-.001,middle.y-.001,middle.z-.001,middle.x+.001,middle.y+.001,middle.z+.001);
                VoxelShape solid=CompositeRuntime.shape(entry.getValue().collision());if(VoxelShapes.matchesAnywhere(solid,VoxelShapes.cuboid(probe),net.minecraft.util.function.BooleanBiFunction.AND))continue;
                BlockPos cell=root.add(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());VoxelShape outline=world.getBlockState(cell).getOutlineShape(world,cell,ShapeContext.of(player));
                Vec3d start=Vec3d.of(cell).add(middle).add(-.2,0,0),end=start.add(.4,0,0);
                if(outline.raycast(start,end,cell)!=null){test.assertTrue(world.isSpaceEmpty(probe.offset(cell)),"source fin target is selection only and has no hard collision");finGap=true;break outer;}
            }
            player.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);test.assertTrue(finGap,"at least one authored two-sided fin is targetable by90009 independently from hard collision");
            for(int yaw=1;yaw<8;yaw++){
                BlockState next=world.getBlockState(root).with(CompositeRootBlock.ROTATION,yaw);test.assertTrue(CompositeRuntime.transition(world,owner(world,root),next,player).outcome()==Outcome.COMMITTED,"sloped footprint turns as one assembly at yaw "+yaw);
                var parts=block().spec.footprint(next,0);double volume=0;Box enclosing=null;
                for(var entry:parts.entrySet())for(var box:CompositeRuntime.shape(entry.getValue().collision()).getBoundingBoxes()){
                    volume+=(box.maxX-box.minX)*(box.maxY-box.minY)*(box.maxZ-box.minZ);Box absolute=box.offset(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());enclosing=enclosing==null?absolute:enclosing.union(absolute);
                }
                test.assertTrue(Math.abs(volume-1)<1e-8&&enclosing.equals(new Box(0,0,0,1,1,1)),"visual yaw retains the exact one-cube physical base; yaw="+yaw+" volume="+volume+" bounds="+enclosing);
            }test.complete();
        }finally{clear(world,root);player.discard();}
    }
    @GameTest(templateName=TEMPLATE,tickLimit=200,batchId="prototype_roof")
    public void commonBuilderKeepsIdentityAndPickDropArtAtEveryYaw(TestContext test){
        ServerWorld world=test.getWorld();BlockPos root=test.getAbsolutePos(LOCAL);PlayerEntity player=test.createMockSurvivalPlayer();player.getAbilities().creativeMode=true;
        try{
            clear(world,root);world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);outside(player,root,0);test.assertTrue(place(player,art("alt",1),root).isAccepted(),"roof placed for builder");Owner resident=owner(world,root);ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);tool.getOrCreateNbt().putString("marker","preserved");NbtCompound old=tool.getNbt().copy();
            for(int turn=1;turn<=8;turn++){
                aimAtRoot(world,root,player);player.setStackInHand(Hand.MAIN_HAND,tool);
                test.assertTrue(dev.dreamwalker.bloodbornedw.architecture.BuildingTool.applyBlock(player,root,dev.dreamwalker.bloodbornedw.architecture.BuildingTool.Action.ROTATE).isAccepted(),"common builder turns whole roof45");
                BlockState state=world.getBlockState(root);test.assertTrue(state.isOf(block())&&state.get(CompositeRootBlock.ROTATION)==turn%8&&state.get(CompositeRootBlock.PROFILE)==CompositeRootBlock.Profile.ALT&&resident.equals(owner(world,root)),"same root UUID/type/art preserved through every yaw");
                ItemStack pick=CompositeRuntime.pick(world,resident);test.assertTrue(pick.isOf(item())&&pick.getSubNbt("BlockStateTag").getString("profile").equals("alt")&&!pick.getSubNbt("BlockStateTag").contains("rotation"),"whole pick keeps art while destination recomputes yaw");
                var decoded=NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),NbtHelper.fromBlockState(state));test.assertTrue(decoded.equals(state),"palette NBT preserves exact independent state without numeric catalog ID");
            }
            test.assertTrue(tool.getCount()==1&&tool.getNbt().equals(old),"builder keeps original item NBT/count");
            test.assertTrue(CompositeRuntime.remove(world,resident,player,true).outcome()==Outcome.COMMITTED,"whole roof breaks as one object");List<ItemEntity> drops=world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(5),e->e.getStack().isOf(item()));
            test.assertTrue(drops.size()==1&&drops.get(0).getStack().getCount()==1&&drops.get(0).getStack().getSubNbt("BlockStateTag").getString("profile").equals("alt"),"whole removal drops exactly one art-preserving roof");test.complete();
        }finally{clear(world,root);player.discard();}
    }
    private static ItemStack art(String profile,int count){ItemStack result=new ItemStack(item(),count);result.getOrCreateSubNbt("BlockStateTag").putString("variant","0");result.getOrCreateSubNbt("BlockStateTag").putString("profile",profile);return result;}
    private static ActionResult place(PlayerEntity player,ItemStack stack,BlockPos root){player.setStackInHand(Hand.MAIN_HAND,stack);return stack.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.of(root.down()).add(.5,1,.5),Direction.UP,root.down(),false)));}
    private static void outside(PlayerEntity player,BlockPos root,float yaw){player.setPosition(root.getX()+.5,root.getY(),root.getZ()+6);player.setYaw(yaw);player.setPitch(0);}
    private static void aimAtRoot(ServerWorld world,BlockPos root,PlayerEntity player){Box box=world.getBlockState(root).getOutlineShape(world,root).getBoundingBoxes().get(0);Vec3d target=Vec3d.of(root).add((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2),eye=target.add(0,0,-2);player.setPosition(eye.x,eye.y-player.getStandingEyeHeight(),eye.z);Vec3d d=target.subtract(eye);player.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));player.setPitch((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));}
    private static void clear(ServerWorld world,BlockPos root){for(BlockPos pos:BlockPos.iterate(root.add(-5,-3,-5),root.add(6,5,5)))world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);CompositeRuntime.drain(world);for(ItemEntity entity:world.getEntitiesByClass(ItemEntity.class,new Box(root).expand(6),e->true))entity.discard();}
}
