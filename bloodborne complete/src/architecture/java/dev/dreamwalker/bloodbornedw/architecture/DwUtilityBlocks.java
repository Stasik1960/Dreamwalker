package dev.dreamwalker.bloodbornedw.architecture;

import java.util.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Native invisible carriers. Their guests retain independent owner UUIDs and geometry. */
public final class DwUtilityBlocks {
    public static MarkerBlock BARRIER,LIGHT;
    public static MarkerItem BARRIER_ITEM,LIGHT_ITEM;
    private DwUtilityBlocks() {}
    public static void initialize(){
        if(BARRIER!=null)return;
        BARRIER=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("dw_barrier"),new MarkerBlock(false));
        LIGHT=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("dw_light"),new MarkerBlock(true));
        BARRIER_ITEM=Registry.register(Registries.ITEM,PrototypeArchitecture.id("dw_barrier"),new MarkerItem(BARRIER));
        LIGHT_ITEM=Registry.register(Registries.ITEM,PrototypeArchitecture.id("dw_light"),new MarkerItem(LIGHT));
        NoCollidePlacement.initialize();
    }
    public static boolean marker(BlockState state){return state.getBlock() instanceof MarkerBlock;}
    public static boolean barrier(BlockState state){return state.isOf(net.minecraft.block.Blocks.BARRIER)||BARRIER!=null&&state.isOf(BARRIER);}
    public static boolean light(BlockState state){return LIGHT!=null&&state.isOf(LIGHT);}
    /** A helper carrier may be swapped; an independent object's root cannot be overwritten. */
    public static boolean replaceCarrier(ServerWorld world,BlockPos pos,BlockState next,PlayerEntity player){
        if(!BuildPermissions.canEdit(world,player,pos)||!world.isChunkLoaded(pos))return false;
        if(world.getBlockEntity(pos) instanceof CompositeBlockEntity old&&old.resident()!=null)return false;
        var entries=CompositeRuntime.contributions(world,pos);
        CompositeShapeSnapshots.beginBatch(world);
        try{
            if(!world.setBlockState(pos,next,Block.FORCE_STATE|Block.NOTIFY_LISTENERS))return false;
            if(world.getBlockEntity(pos) instanceof CompositeBlockEntity cached)cached.set(null,entries,new NbtCompound());
            CompositeLedger.get(world).put(CompositeData.cell(pos),entries);
            CompositeNetworking.publish(world,List.of(CompositeData.cell(pos)));
            world.updateNeighbors(pos,next.getBlock());return true;
        }finally{CompositeShapeSnapshots.endBatch(world);}
    }
    public static boolean remove(ServerWorld world,BlockPos pos,PlayerEntity player){
        if(!marker(world.getBlockState(pos)))return false;
        return replaceCarrier(world,pos,CompositeRuntime.contributions(world,pos).isEmpty()?Blocks.AIR.getDefaultState():CompositeArchitecture.CELL.getDefaultState(),player);
    }
    public static final class MarkerBlock extends BlockWithEntity {
        public final boolean light;
        MarkerBlock(boolean light){
            super(settings(light));this.light=light;
            setDefaultState(getStateManager().getDefaultState().with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.BASE));
        }
        private static Settings settings(boolean light){
            Settings settings=Settings.create().nonOpaque().dynamicBounds().strength(-1,3600000).dropsNothing()
                .solidBlock((s,w,p)->false).suffocates((s,w,p)->false).blockVision((s,w,p)->false)
                .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).luminance(state->light?14:0);
            return light?settings.notSolid().replaceable():settings.solid();
        }
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(CompositeRootBlock.PROFILE);}
        @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CompositeBlockEntity(pos,state);}
        @Override public BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
        @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
            // Replacing a helper keeps its independently owned architecture.
            // Only the marker's own volume is player-only (or empty for light).
            var guests=CompositeRuntime.cellShape(world,pos,true,state,context);
            return !light&&context instanceof EntityShapeContext entity&&entity.getEntity() instanceof PlayerEntity?VoxelShapes.union(guests,VoxelShapes.fullCube()):guests;
        }
        @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return !light||context.isHolding(asItem())||dev.dreamwalker.bloodbornedw.tool.BbToolService.ITEM!=null&&context.isHolding(dev.dreamwalker.bloodbornedw.tool.BbToolService.ITEM)?VoxelShapes.fullCube():VoxelShapes.empty();}
        @Override public VoxelShape getSidesShape(BlockState state,BlockView world,BlockPos pos){return light?VoxelShapes.empty():VoxelShapes.fullCube();}
        @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.empty();}
        @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return 0;}
        @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return true;}
        @Override public boolean canReplace(BlockState state,ItemPlacementContext context){
            if(light)return super.canReplace(state,context);
            return context.getStack().getItem() instanceof BlockItem item
                &&!(item instanceof MarkerItem)&&!item.getBlock().equals(Blocks.BARRIER);
        }
        @Override public boolean canPathfindThrough(BlockState state,BlockView world,BlockPos pos,NavigationType type){return true;}
        @Override public ItemStack getPickStack(BlockView world,BlockPos pos,BlockState state){var stack=new ItemStack(asItem());stack.getOrCreateSubNbt("BlockStateTag").putString("profile",state.get(CompositeRootBlock.PROFILE).asString());return stack;}
        @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){
            if(!state.isOf(next.getBlock())&&!CompositeRuntime.writing()&&world instanceof ServerWorld server)CompositeRuntime.scheduleCleanup(server,pos);
            super.onStateReplaced(state,world,pos,next,moved);
        }
    }
    public static final class MarkerItem extends BlockItem {
        private final MarkerBlock block;
        MarkerItem(MarkerBlock block){super(block,new Item.Settings());this.block=block;}
        @Override public ActionResult place(ItemPlacementContext context){return placeMarker(context);}
        private ActionResult placeMarker(ItemPlacementContext context){
            var world=context.getWorld();var player=context.getPlayer();if(player==null)return ActionResult.FAIL;
            BlockPos pos=context.getBlockPos();
            var existing=world.getBlockState(pos);
            if(!(existing.isAir()||existing.isOf(CompositeArchitecture.CELL)||existing.canReplace(context)||light(existing)))return ActionResult.FAIL;
            if(barrier(existing))return ActionResult.FAIL;
            if(!block.light){
                // Barrier-to-barrier conflicts remain exact full cell checks even though mobs pass through.
                if(!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos))return ActionResult.FAIL;
                if(world.getBlockState(pos).isOf(Blocks.BARRIER)||world.getBlockState(pos).isOf(BARRIER))return ActionResult.FAIL;
            }
            var next=block.getDefaultState();var tag=context.getStack().getSubNbt("BlockStateTag");
            if(tag!=null&&"alt".equals(tag.getString("profile")))next=next.with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT);
            if(world.isClient)return ActionResult.SUCCESS;
            if(!replaceCarrier((ServerWorld)world,pos,next,player))return ActionResult.FAIL;
            if(!player.isCreative())context.getStack().decrement(1);return ActionResult.CONSUME;
        }
        @Override public Text getName(ItemStack stack){return DebugCatalogue.itemName(stack,Text.literal(block.light?"DW-свет":"DW-барьер"));}
        @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,net.minecraft.client.item.TooltipContext context){
            DebugCatalogue.itemTooltip(stack,lines);lines.add(Text.literal(block.light?"Невидимый свет · яркость 14":"Невидимая преграда только для игроков"));
        }
    }
}
