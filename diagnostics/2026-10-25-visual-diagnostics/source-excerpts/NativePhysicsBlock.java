package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.*;
import net.minecraft.loot.context.*;
import net.minecraft.registry.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Ordinary chunk-paletted physics, shared by visual variants with exact equal
 * shapes, optics, fluid, behaviour and render layer. No block entities. */
public final class NativePhysicsBlock extends Block implements NativeCellBlock,NewStateStorage {
    public static final IntProperty PART=IntProperty.of("part",0,127);
    private static final List<NativePhysicsBlock> BANKS=new ArrayList<>();
    private static final ThreadLocal<Integer> CONSTRUCTING=new ThreadLocal<>();
    private final int bank;
    private static NativeGridCatalog.Cell at(int bank,BlockState state){return NativeGridCatalog.cell(NativeVisualLayout.representative(Math.min(bank*128+state.get(PART),NativeVisualLayout.count()-1)));}
    private NativePhysicsBlock(int bank){
        super(Settings.create().strength(.4F).dropsNothing()
            .solidBlock((s,w,p)->at(bank,s).optics().solidBlock()).suffocates((s,w,p)->at(bank,s).optics().suffocates())
            .blockVision((s,w,p)->at(bank,s).optics().blockVision()).luminance(s->at(bank,s).optics().luminance())
            .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));
        this.bank=bank;setDefaultState(getDefaultState().with(PART,0));
    }
    public static void initialize(){for(int bank=0;bank<(NativeVisualLayout.count()+127)/128;bank++){
        NativePhysicsBlock block;CONSTRUCTING.set(bank);try{block=new NativePhysicsBlock(bank);}finally{CONSTRUCTING.remove();}
        var id=new Identifier("bloodborne_dw","native_physics_"+bank);Registry.register(Registries.BLOCK,id,block);BANKS.add(block);Registry.register(Registries.ITEM,id,new VisualItem(block));
    }}
    public int physics(BlockState state){Integer constructing=CONSTRUCTING.get();return Math.min((constructing==null?bank:constructing)*128+state.get(PART),NativeVisualLayout.count()-1);}
    @Override public NativeGridCatalog.Cell cell(BlockState state){return NativeGridCatalog.cell(NativeVisualLayout.representative(physics(state)));}
    public static BlockState state(int physics){return BANKS.get(physics/128).getDefaultState().with(PART,physics%128);}
    public static ItemStack item(int visual){int physics=NativeVisualLayout.physics(visual);if(physics<0)throw new IllegalArgumentException("Whole item");var stack=new ItemStack(BANKS.get(physics/128));stack.getOrCreateNbt().putInt(NativeVisuals.ITEM_KEY,visual);stack.getOrCreateSubNbt("BlockStateTag").putString("part",Integer.toString(physics%128));return stack;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(PART);}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return cell(s).collision();}
    @Override public VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return cell(s).outline();}
    @Override public VoxelShape getCullingShape(BlockState s,BlockView w,BlockPos p){return cell(s).culling();}
    @Override public int getOpacity(BlockState s,BlockView w,BlockPos p){return cell(s).optics().opacity();}
    @Override public float getAmbientOcclusionLightLevel(BlockState s,BlockView w,BlockPos p){return cell(s).optics().ambientOcclusion();}
    @Override public boolean hasSidedTransparency(BlockState s){return cell(s).optics().sidedTransparency();}
    @Override public boolean isTransparent(BlockState s,BlockView w,BlockPos p){return cell(s).optics().transparent();}
    @Override public boolean isShapeFullCube(BlockState s,BlockView w,BlockPos p){return cell(s).optics().opaqueFullCube();}
    @Override public FluidState getFluidState(BlockState s){return cell(s).fluid();}
    @Override public BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos q){var f=cell(s).fluid();if(!f.isEmpty())w.scheduleFluidTick(p,f.getFluid(),f.getFluid().getTickRate(w));return s;}
    @Override public ItemStack getPickStack(BlockView w,BlockPos p,BlockState s){return item(NativeVisuals.required(w,p));}
    @Override public List<ItemStack> getDroppedStacks(BlockState s,LootContextParameterSet.Builder context){var p=BlockPos.ofFloored(context.get(LootContextParameters.ORIGIN));return List.of(item(NativeVisuals.dropIndex(context.getWorld(),p)));}
    @Override public ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){
        int alt=NativeGridCatalog.cell(NativeVisuals.required(w,p)).alt();if(alt<0||!player.isSneaking()||!player.getStackInHand(hand).isEmpty())return ActionResult.PASS;
        if(!w.isClient&&dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(w,player,p))NativeVisuals.write(w,p,alt,Block.NOTIFY_ALL);return ActionResult.success(w.isClient);
    }
    private static final class VisualItem extends BlockItem {
        VisualItem(NativePhysicsBlock block){super(block,new Item.Settings());}
        private int selected(ItemStack stack){if(!stack.hasNbt()||!stack.getNbt().contains(NativeVisuals.ITEM_KEY,3))return -1;int i=stack.getNbt().getInt(NativeVisuals.ITEM_KEY);if(i<0||i>=NativeGridCatalog.cells().size()||NativeVisualLayout.physics(i)<0)return -1;return NativeGridCatalog.state(i).getBlock()==getBlock()?i:-1;}
        @Override protected BlockState getPlacementState(ItemPlacementContext context){int index=selected(context.getStack());if(index<0)return null;var s=NativeGridCatalog.state(index);return canPlace(context,s)?s:null;}
        @Override public ActionResult place(ItemPlacementContext context){
            int index=selected(context.getStack());if(index<0)return ActionResult.FAIL;
            var world=context.getWorld();var pos=context.getBlockPos();var chunk=world.getChunk(pos);var before=NativeVisuals.data(chunk);NativeVisuals.data(chunk,before.with(pos,index));
            try{var result=super.place(context);if(!result.isAccepted())NativeVisuals.data(chunk,NativeVisuals.data(chunk).with(pos,before.at(pos)));else{chunk.setNeedsSaving(true);NativeVisuals.publish(world,pos);}return result;}
            catch(RuntimeException failure){NativeVisuals.data(chunk,NativeVisuals.data(chunk).with(pos,before.at(pos)));throw failure;}
        }
        @Override public Text getName(ItemStack stack){int index=selected(stack);return Text.literal(index<0?"Городской фрагмент":NativeGridCatalog.cell(index).displayName());}
    }
}
