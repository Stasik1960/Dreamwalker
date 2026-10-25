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
    public enum Kind {BARRIER,LIGHT,DARKNESS,WATER}
    public static MarkerBlock BARRIER,LIGHT,DARKNESS,WATER;
    public static MarkerItem BARRIER_ITEM,LIGHT_ITEM,DARKNESS_ITEM,WATER_ITEM;
    public static FauxWaterFluid WATER_FLUID;
    private DwUtilityBlocks() {}
    public static void initialize(){
        if(BARRIER!=null)return;
        WATER_FLUID=Registry.register(Registries.FLUID,PrototypeArchitecture.id("dw_fake_water"),new FauxWaterFluid());
        BARRIER=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("dw_barrier"),new MarkerBlock(Kind.BARRIER));
        LIGHT=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("dw_light"),new MarkerBlock(Kind.LIGHT));
        DARKNESS=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("dw_darkness"),new MarkerBlock(Kind.DARKNESS));
        WATER=Registry.register(Registries.BLOCK,PrototypeArchitecture.id("dw_fake_water"),new WaterMarkerBlock());
        BARRIER_ITEM=Registry.register(Registries.ITEM,PrototypeArchitecture.id("dw_barrier"),new MarkerItem(BARRIER));
        LIGHT_ITEM=Registry.register(Registries.ITEM,PrototypeArchitecture.id("dw_light"),new MarkerItem(LIGHT));
        DARKNESS_ITEM=Registry.register(Registries.ITEM,PrototypeArchitecture.id("dw_darkness"),new MarkerItem(DARKNESS));
        WATER_ITEM=Registry.register(Registries.ITEM,PrototypeArchitecture.id("dw_fake_water"),new MarkerItem(WATER));
        FaerieVeilBlock.initialize();
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->!marker(state)||player.isCreative());
        NoCollidePlacement.initialize();
    }
    public static boolean marker(BlockState state){return state.getBlock() instanceof MarkerBlock||state.getBlock() instanceof FaerieVeilBlock;}
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
        if(player==null||!player.isCreative()||!marker(world.getBlockState(pos)))return false;
        return replaceCarrier(world,pos,CompositeRuntime.contributions(world,pos).isEmpty()?Blocks.AIR.getDefaultState():CompositeArchitecture.CELL.getDefaultState(),player);
    }
    public static class MarkerBlock extends BlockWithEntity {
        public final boolean light;public final Kind kind;
        MarkerBlock(Kind kind){
            super(settings(kind));this.kind=kind;this.light=kind==Kind.LIGHT;
            setDefaultState(getStateManager().getDefaultState().with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.BASE));
        }
        private static Settings settings(Kind kind){
            Settings settings=Settings.create().dynamicBounds().strength(-1,3600000).dropsNothing()
                .solidBlock((s,w,p)->false).suffocates((s,w,p)->false).blockVision((s,w,p)->false)
                .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).luminance(state->kind==Kind.LIGHT?14:0);
            if(kind!=Kind.DARKNESS)settings.nonOpaque();
            return kind==Kind.BARRIER?settings.solid():settings.notSolid().replaceable();
        }
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(CompositeRootBlock.PROFILE);}
        @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new CompositeBlockEntity(pos,state);}
        @Override public BlockRenderType getRenderType(BlockState state){return kind==Kind.DARKNESS?BlockRenderType.MODEL:BlockRenderType.INVISIBLE;}
        @Override public net.minecraft.fluid.FluidState getFluidState(BlockState state){return kind==Kind.WATER?net.minecraft.fluid.Fluids.WATER.getDefaultState():super.getFluidState(state);}
        @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
            // Replacing a helper keeps its independently owned architecture.
            // Only the marker's own volume is player-only (or empty for light).
            var guests=CompositeRuntime.cellShape(world,pos,true,state,context);
            return kind==Kind.BARRIER&&context instanceof EntityShapeContext entity&&entity.getEntity() instanceof PlayerEntity?VoxelShapes.union(guests,VoxelShapes.fullCube()):guests;
        }
        @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return creative(context)?VoxelShapes.fullCube():VoxelShapes.empty();}
        @Override public VoxelShape getSidesShape(BlockState state,BlockView world,BlockPos pos){return kind==Kind.DARKNESS||kind==Kind.BARRIER?VoxelShapes.fullCube():VoxelShapes.empty();}
        @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return kind==Kind.DARKNESS?VoxelShapes.fullCube():VoxelShapes.empty();}
        @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return kind==Kind.DARKNESS?15:kind==Kind.WATER?1:0;}
        @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return kind!=Kind.DARKNESS;}
        @Override public boolean canReplace(BlockState state,ItemPlacementContext context){
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
    public static class MarkerItem extends BlockItem {
        private final Block block;private final Kind kind;
        MarkerItem(MarkerBlock block){this(block,block.kind);}
        protected MarkerItem(Block block,Kind kind){super(block,new Item.Settings());this.block=block;this.kind=kind;}
        @Override public ActionResult place(ItemPlacementContext context){return placeMarker(context);}
        private ActionResult placeMarker(ItemPlacementContext context){
            var world=context.getWorld();var player=context.getPlayer();if(player==null)return ActionResult.FAIL;
            if(kind==Kind.WATER){
                if(!player.isCreative())return ActionResult.FAIL;
                BlockPos clicked=context.canReplaceExisting()?context.getBlockPos():context.getBlockPos().offset(context.getSide().getOpposite());
                var host=world.getBlockState(clicked);
                if(host.getBlock() instanceof Waterloggable&&host.contains(net.minecraft.state.property.Properties.WATERLOGGED)&&!host.get(net.minecraft.state.property.Properties.WATERLOGGED)){
                    if(world.isClient)return ActionResult.SUCCESS;
                    return FauxWaterlogging.fill((ServerWorld)world,clicked,player)?ActionResult.CONSUME:ActionResult.FAIL;
                }
            }
            // The helper may be replaceable but its carrier is not the aimed surface.
            // Use the actual ray boundary; marker palettes remain native whole cells.
            BlockPos pos=NoCollidePlacement.surfaceAnchor(context);
            var existing=world.getBlockState(pos);
            if(!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos))return ActionResult.FAIL;
            if(!(existing.isAir()||existing.isOf(CompositeArchitecture.CELL)||existing.canReplace(context)||light(existing)))return ActionResult.FAIL;
            if(barrier(existing))return ActionResult.FAIL;
            if(kind==Kind.BARRIER){
                // Barrier-to-barrier conflicts remain exact full cell checks even though mobs pass through.
                if(!world.isChunkLoaded(pos)||!world.isInBuildLimit(pos)||!world.getWorldBorder().contains(pos))return ActionResult.FAIL;
                if(world.getBlockState(pos).isOf(Blocks.BARRIER)||world.getBlockState(pos).isOf(BARRIER))return ActionResult.FAIL;
            }
            var next=block.getDefaultState();var tag=context.getStack().getSubNbt("BlockStateTag");
            if(next.contains(CompositeRootBlock.PROFILE)&&tag!=null&&"alt".equals(tag.getString("profile")))next=next.with(CompositeRootBlock.PROFILE,CompositeRootBlock.Profile.ALT);
            if(world.isClient)return ActionResult.SUCCESS;
            if(!replaceCarrier((ServerWorld)world,pos,next,player))return ActionResult.FAIL;
            if(kind==Kind.WATER)world.playSound(null,pos,net.minecraft.sound.SoundEvents.ITEM_BUCKET_EMPTY,net.minecraft.sound.SoundCategory.BLOCKS,1,1);
            if(!player.isCreative())context.getStack().decrement(1);return ActionResult.CONSUME;
        }
        @Override public Text getName(ItemStack stack){return DebugCatalogue.itemName(stack,Text.literal(switch(kind){case LIGHT->"DW-свет";case BARRIER->"DW-барьер";case DARKNESS->"Тьма";case WATER->"Фальшивая вода";}));}
        @Override public void appendTooltip(ItemStack stack,World world,List<Text> lines,net.minecraft.client.item.TooltipContext context){
            DebugCatalogue.itemTooltip(stack,lines);lines.add(Text.literal(switch(kind){case LIGHT->"Невидимый свет · яркость 14";case BARRIER->"Невидимая преграда только для игроков";case DARKNESS->"Абсолютно чёрный блок без собственной коллизии";case WATER->"Вода без течения, плавания и замедления";}));lines.add(Text.literal("Выделение и удаление — только в креативе"));
        }
    }
    private static final class WaterMarkerBlock extends MarkerBlock {
        WaterMarkerBlock(){super(Kind.WATER);}
        @Override public net.minecraft.fluid.FluidState getFluidState(BlockState state){return net.minecraft.fluid.Fluids.WATER.getDefaultState();}
    }
    public static boolean creative(ShapeContext context){return context instanceof EntityShapeContext entity&&entity.getEntity() instanceof PlayerEntity player&&player.isCreative();}
}
