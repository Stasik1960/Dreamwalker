package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornedw.architecture.compat.SourceTechnicalLight;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Technical source light is persistent terrain, although its physical shapes are empty. */
public final class SourceTechnicalLightGameTests implements FabricGameTest {
    /** Create constructs AllShapes from native states with null world/position. */
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="composite_compatibility")
    public void nativeWorldlessQueriesPreserveOriginalShapesWithoutAnOwner(TestContext test){
        for(BlockState state:new BlockState[]{Blocks.STONE.getDefaultState(),Blocks.STONE_SLAB.getDefaultState(),SourceTechnicalLight.SOURCE_LIGHT.getDefaultState()}){
            var outline=state.getOutlineShape(null,null);var collision=state.getCollisionShape(null,null);
            test.assertTrue(!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(outline,collision,net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"native worldless outline/collision stay equal for "+state);
            test.assertTrue(!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(collision,state.getCollisionShape(null,null,ShapeContext.absent()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"both native collision overloads accept worldless shape construction for "+state);
            test.assertTrue(!net.minecraft.util.shape.VoxelShapes.matchesAnywhere(outline,state.getOutlineShape(null,null,ShapeContext.absent()),net.minecraft.util.function.BooleanBiFunction.NOT_SAME),"both native outline overloads preserve worldless geometry for "+state);
            double expected=state.isOf(SourceTechnicalLight.SOURCE_LIGHT)?0:state.isOf(Blocks.STONE_SLAB)?.5:1;
            test.assertTrue(expected==0?collision.isEmpty():!collision.isEmpty()&&collision.getMax(net.minecraft.util.math.Direction.Axis.Y)==expected,"native physical height retained for "+state);
        }
        test.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=80,batchId="source_technical_light")
    public void originalAirPhysicsLightNineAndNativePalettePersistWithoutAnItem(TestContext test){
        ServerWorld world=test.getWorld();BlockPos pos=test.getAbsolutePos(new BlockPos(3,3,3));
        PlayerEntity player=test.createMockSurvivalPlayer();
        try{
            for(BlockPos p:BlockPos.iterate(pos.add(-1,-1,-2),pos.add(1,2,2)))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);
            for(BlockPos p:BlockPos.iterate(pos.add(-1,-1,-2),pos.add(1,-1,2)))world.setBlockState(p,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS);
            BlockState state=SourceTechnicalLight.SOURCE_LIGHT.getDefaultState();world.setBlockState(pos,state,Block.NOTIFY_ALL);
            test.assertTrue(Registries.BLOCK.getId(state.getBlock()).equals(SourceTechnicalLight.ID)&&!state.isAir(),"original supplier omits air(): technical identity is retained rather than classified as disposable vanilla air");
            test.assertTrue(state.getLuminance()==9&&state.isReplaceable()&&state.getFluidState().isEmpty(),"source luminous9/replaceable/empty-fluid semantics");
            test.assertTrue(state.getOutlineShape(world,pos).isEmpty()&&state.getCollisionShape(world,pos).isEmpty()&&state.getCullingShape(world,pos).isEmpty(),"original AirBlock outline, collision and occlusion are empty");
            test.assertTrue(state.getOpacity(world,pos)==0&&state.isTransparent(world,pos)&&!state.isSolidBlock(world,pos)&&!state.shouldSuffocate(world,pos)&&!state.shouldBlockVision(world,pos),"original Material.AIR has no opaque, solid, suffocation or vision surface");
            test.assertTrue(state.getRenderType()==BlockRenderType.INVISIBLE&&state.getPistonBehavior()==PistonBehavior.NORMAL&&!state.hasBlockEntity()&&world.getBlockEntity(pos)==null&&!state.hasRandomTicks(),"source invisible static block has NORMAL piston behavior and no block entity/ticking contract");
            test.assertTrue(state.getBlock().asItem()==Items.AIR&&state.getBlock().getPickStack(world,pos,state).isEmpty()&&state.getBlock().getStateManager().getStates().size()==1,"internal compatibility ID has one state and no collectible item");
            NbtCompound palette=NbtHelper.fromBlockState(state);test.assertTrue(palette.getString("Name").equals(SourceTechnicalLight.ID.toString())&&NbtHelper.toBlockState(world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),palette.copy()).equals(state),"Minecraft save palette retains exact registered luminous identity");
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.LIGHT));
            test.assertTrue(state.getOutlineShape(world,pos,ShapeContext.of(player)).isEmpty(),"holding vanilla LIGHT does not reveal a selectable cube unlike vanilla LightBlock");
            Vec3d start=Vec3d.ofCenter(pos).add(0,0,-1.5),end=start.add(0,0,3);
            test.assertTrue(world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,player)).getType()==HitResult.Type.MISS,"actual selection ray passes through the original empty technical cell");
            player.refreshPositionAndAngles(pos.getX()+.5,pos.getY(),pos.getZ()-1,0,0);double z=player.getZ();player.move(MovementType.SELF,new Vec3d(0,0,2));
            test.assertTrue(Math.abs(player.getZ()-z-2)<1e-7&&world.getBlockState(pos).equals(state),"actual player traverses two meters while technical light remains present");
            test.complete();
        }finally{for(BlockPos p:BlockPos.iterate(pos.add(-1,-1,-2),pos.add(1,2,2)))world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS);player.discard();}
    }
}
