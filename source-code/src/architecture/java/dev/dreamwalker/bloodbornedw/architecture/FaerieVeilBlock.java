package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import net.minecraft.block.*;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Opaque passable material. One state; no block entity, ticker or particles.
 * Existing composite ledger bindings remain independent and keep their physics. */
public final class FaerieVeilBlock extends Block {
    public static final Identifier ID=new Identifier("bloodborne_dw","dw_faerie_veil");
    public static FaerieVeilBlock BLOCK;public static Item ITEM;
    private FaerieVeilBlock(){super(Settings.create().dynamicBounds().strength(-1,3600000).dropsNothing().notSolid().replaceable()
        .solidBlock((s,w,p)->false).suffocates((s,w,p)->false).blockVision((s,w,p)->false).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));}
    public static void initialize(){if(BLOCK!=null)return;BLOCK=Registry.register(Registries.BLOCK,ID,new FaerieVeilBlock());ITEM=Registry.register(Registries.ITEM,ID,new VeilItem(BLOCK));}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return VoxelShapes.empty();}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return DwUtilityBlocks.creative(context)?VoxelShapes.fullCube():VoxelShapes.empty();}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.fullCube();}
    @Override public VoxelShape getSidesShape(BlockState state,BlockView world,BlockPos pos){return VoxelShapes.fullCube();}
    @Override public int getOpacity(BlockState state,BlockView world,BlockPos pos){return 15;}
    @Override public boolean isTransparent(BlockState state,BlockView world,BlockPos pos){return false;}
    @Override public boolean canPathfindThrough(BlockState state,BlockView world,BlockPos pos,NavigationType type){return true;}
    @Override public boolean canReplace(BlockState state,ItemPlacementContext context){return context.getStack().getItem() instanceof BlockItem item&&!(item instanceof DwUtilityBlocks.MarkerItem)&&item.getBlock()!=Blocks.BARRIER;}
    @Override public void onStateReplaced(BlockState state,World world,BlockPos pos,BlockState next,boolean moved){if(!state.isOf(next.getBlock())&&!CompositeRuntime.writing()&&world instanceof ServerWorld server)CompositeRuntime.scheduleCleanup(server,pos);super.onStateReplaced(state,world,pos,next,moved);}
    private static final class VeilItem extends DwUtilityBlocks.MarkerItem {
        VeilItem(Block block){super(block,DwUtilityBlocks.Kind.DARKNESS);}
        @Override public Text getName(ItemStack stack){return Text.literal("Завеса фей");}
        @Override public void appendTooltip(ItemStack stack,World world,java.util.List<Text> lines,net.minecraft.client.item.TooltipContext context){lines.add(Text.literal("Туман и светлячки · непрозрачная проходная завеса"));lines.add(Text.literal("Выделение и удаление — только в креативе"));}
    }
}
