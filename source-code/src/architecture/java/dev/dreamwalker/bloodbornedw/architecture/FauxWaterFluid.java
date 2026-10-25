package dev.dreamwalker.bloodbornedw.architecture;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.*;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Deliberately absent from WATER/LAVA tags: no swimming, drag, buoyancy or fog.
 * Actual block states retain vanilla water for the ordinary terrain renderer. */
public final class FauxWaterFluid extends Fluid {
    @Override public Item getBucketItem(){return Items.AIR;}
    @Override protected boolean canBeReplacedWith(FluidState state,BlockView world,BlockPos pos,Fluid other,Direction direction){return false;}
    @Override protected Vec3d getVelocity(BlockView world,BlockPos pos,FluidState state){return Vec3d.ZERO;}
    @Override public int getTickRate(WorldView world){return 0;}
    @Override protected float getBlastResistance(){return 3600000;}
    @Override public float getHeight(FluidState state,BlockView world,BlockPos pos){return world.getFluidState(pos.up()).isOf(this)?1:getHeight(state);}
    @Override public float getHeight(FluidState state){return 8f/9f;}
    @Override protected BlockState toBlockState(FluidState state){return DwUtilityBlocks.WATER.getDefaultState();}
    @Override public boolean isStill(FluidState state){return true;}
    @Override public int getLevel(FluidState state){return 8;}
    @Override public VoxelShape getShape(FluidState state,BlockView world,BlockPos pos){return VoxelShapes.cuboid(0,0,0,1,getHeight(state,world,pos),1);}
}
