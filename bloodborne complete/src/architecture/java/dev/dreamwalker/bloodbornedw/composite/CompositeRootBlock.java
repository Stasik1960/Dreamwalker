package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public class CompositeRootBlock extends BlockWithEntity {
    public static final IntProperty ROTATION=IntProperty.of("rotation",0,7),VARIANT=IntProperty.of("variant",0,15);
    public static final BooleanProperty OPEN=BooleanProperty.of("open");
    public static final EnumProperty<Profile> PROFILE=EnumProperty.of("profile",Profile.class);
    public enum Profile implements StringIdentifiable{BASE("base"),ALT("alt");private final String name;Profile(String name){this.name=name;}public String asString(){return name;}}
    public final CompositeSpec spec;
    public CompositeRootBlock(CompositeSpec spec){super(Settings.create().strength(.4F).nonOpaque().dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));this.spec=spec;setDefaultState(getDefaultState().with(ROTATION,0).with(VARIANT,0).with(OPEN,false).with(PROFILE,Profile.BASE));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(ROTATION,VARIANT,OPEN,PROFILE);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CompositeBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return CompositeRuntime.cellShape(world,pos,true,state,context);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return CompositeRuntime.cellShape(world,pos,false,state,context);}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return 0;}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return true;}
    @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){return CompositeRuntime.defaultPick(world,pos,state);}
    @Override public List<ItemStack> getDroppedStacks(BlockState state,net.minecraft.loot.context.LootContextParameterSet.Builder builder){var entity=builder.getOptional(net.minecraft.loot.context.LootContextParameters.BLOCK_ENTITY);return List.of(art(state,entity instanceof CompositeBlockEntity own?own.payload():new NbtCompound()));}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit){return CompositeRuntime.use(world,pos,player);}
    @Override public BlockState rotate(BlockState state,BlockRotation rotation){return state.with(ROTATION,Math.floorMod(state.get(ROTATION)+switch(rotation){case CLOCKWISE_90->2;case CLOCKWISE_180->4;case COUNTERCLOCKWISE_90->6;default->0;},8));}
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){if(!state.isOf(next.getBlock())&&!CompositeRuntime.writing()&&world instanceof ServerWorld server)CompositeRuntime.scheduleCleanup(server,pos);super.onStateReplaced(state,world,pos,next,moved);}
    @Override public BlockState getStateForNeighborUpdate(BlockState state,net.minecraft.util.math.Direction side,BlockState neighbor,WorldAccess world,BlockPos pos,BlockPos neighborPos){if(world instanceof ServerWorld server&&(spec.requiredSupport||this instanceof ThinWindowRootBlock))CompositeRuntime.scheduleSupport(server,pos);return state;}
    public ItemStack art(BlockState state,NbtCompound payload){ItemStack stack=new ItemStack(asItem());NbtCompound tag=stack.getOrCreateSubNbt("BlockStateTag");tag.putString("variant",Integer.toString(state.get(VARIANT)));tag.putString("profile",state.get(PROFILE).asString());tag.putString("open",Boolean.toString(state.get(OPEN)));return stack;}
}
