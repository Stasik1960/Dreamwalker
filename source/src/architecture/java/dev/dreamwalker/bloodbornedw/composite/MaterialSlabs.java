package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Direction;

/** Real vanilla slab storage for the restored pavement materials. Geometry,
 * placement, combining slabs, bucket handling and fluid ticks use SlabBlock. */
public final class MaterialSlabs {
    public static final DirectionProperty FACING=net.minecraft.state.property.Properties.HORIZONTAL_FACING;
    private static final Map<String,BlockItem> PRIMARY=new HashMap<>();
    private static final List<Slab> BLOCKS=new ArrayList<>();
    private static final Set<Slab> DECORATED=new LinkedHashSet<>();
    private static final Map<String,Slab> BY_ID=new HashMap<>(),OVERLAYS=new HashMap<>();
    private MaterialSlabs(){}
    public static void initialize(){
        if(!BLOCKS.isEmpty())return;
        try(var input=MaterialSlabs.class.getResourceAsStream("/bloodborne_dw/restored-material-slabs.json")){
            if(input==null)throw new IllegalStateException("Missing restored slabs");
            var doc=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            for(var raw:doc.getAsJsonArray("materials")){
                var row=raw.getAsJsonObject();Identifier id=new Identifier(row.get("id").getAsString());
                int recipe=row.has("overlayRecipe")?row.get("overlayRecipe").getAsInt():-1;
                Slab base=row.has("base")?Objects.requireNonNull(BY_ID.get(row.get("base").getAsString())):null;
                Slab slab=Registry.register(Registries.BLOCK,id,new Slab(recipe,base));BLOCKS.add(slab);BY_ID.put(id.toString(),slab);
                if(row.has("decorated")&&row.get("decorated").getAsBoolean()||base!=null&&DECORATED.contains(base))DECORATED.add(slab);
                if(base!=null)OVERLAYS.put(row.get("base").getAsString()+"/"+recipe,slab);
                String name=row.get("name").getAsString();
                BlockItem item=Registry.register(Registries.ITEM,id,new SlabItem(slab,new Item.Settings()){
                    @Override public Text getName(ItemStack stack){return Text.literal(name);}
                });
                if(row.get("primary").getAsBoolean())PRIMARY.put(row.get("source").getAsString()+"/"+row.get("variant").getAsInt(),item);
            }
        }catch(Exception failure){throw new IllegalStateException("Cannot load restored slabs",failure);}
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(!hasOverlay(state))return true;
            if(!world.isClient){
                BlockState remaining=StaticArchitectureCatalog.state(recipeIndex(state)).with(StaticArchitectureBlock.WATERLOGGED,state.get(SlabBlock.WATERLOGGED));
                world.setBlockState(pos,remaining,Block.NOTIFY_ALL);
                if(!player.getAbilities().creativeMode)Block.dropStack(world,pos,new ItemStack(((Slab)state.getBlock()).base.asItem(),state.get(SlabBlock.TYPE)==net.minecraft.block.enums.SlabType.DOUBLE?2:1));
                world.syncWorldEvent(2001,pos,Block.getRawIdFromState(state));
            }
            return false;
        });
    }
    public static List<Slab> blocks(){return List.copyOf(BLOCKS);}
    public static Set<Slab> decoratedBlocks(){return Collections.unmodifiableSet(DECORATED);}
    public static boolean hasOverlay(BlockState state){return state.getBlock() instanceof Slab slab&&slab.overlayRecipe>=0;}
    public static int recipeIndex(BlockState state){return ((Slab)state.getBlock()).overlayRecipe;}
    public static BlockState withRecipe(BlockState state,int recipe){
        Slab old=(Slab)state.getBlock();Slab next=recipe<0?old.base:OVERLAYS.get(Registries.BLOCK.getId(old.base)+"/"+recipe);
        if(next==null)throw new IllegalArgumentException("Missing finite slab/helper closure "+recipe);
        return next.getDefaultState().with(SlabBlock.TYPE,state.get(SlabBlock.TYPE)).with(SlabBlock.WATERLOGGED,state.get(SlabBlock.WATERLOGGED)).with(FACING,state.get(FACING));
    }
    public static boolean supports(CompositeSpec spec,BlockState state){return PRIMARY.containsKey(spec.id+"/"+CompositeSpec.poseVariant(state));}
    public static BlockItem replacement(CompositeRootBlock root,ItemStack stack){
        var tag=stack.getSubNbt("BlockStateTag");int variant=0;
        if(tag!=null)try{variant=Integer.parseInt(tag.getString("variant"));if(tag.contains("art_bank"))variant+=16*Integer.parseInt(tag.getString("art_bank"));}catch(NumberFormatException ignored){}
        return PRIMARY.get(root.spec.id+"/"+variant);
    }
    public static final class Slab extends SlabBlock implements NewStateStorage {
        private final int overlayRecipe;
        private final Slab base;
        private Slab(int recipe,Slab base){super(Settings.copy(Blocks.STONE_SLAB).strength(.8F).dynamicBounds());this.overlayRecipe=recipe;this.base=base==null?this:base;setDefaultState(getDefaultState().with(FACING,Direction.NORTH));}
        @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(FACING);}
        @Override public BlockState getPlacementState(ItemPlacementContext context){BlockState existing=context.getWorld().getBlockState(context.getBlockPos());BlockState state=super.getPlacementState(context);return state==null?null:existing.isOf(this)?state.with(FACING,existing.get(FACING)):state.with(FACING,context.getHorizontalPlayerFacing().getOpposite());}
        @Override public BlockState rotate(BlockState state,BlockRotation rotation){return state.with(FACING,rotation.rotate(state.get(FACING)));}
        @Override public BlockState mirror(BlockState state,BlockMirror mirror){return state.rotate(mirror.getRotation(state.get(FACING)));}
        @Override public boolean canReplace(BlockState state,ItemPlacementContext context){return overlayRecipe>=0&&context.getStack().isOf(base.asItem())?base.canReplace(state,context):super.canReplace(state,context);}
        @Override public net.minecraft.util.shape.VoxelShape getCollisionShape(BlockState state,net.minecraft.world.BlockView view,net.minecraft.util.math.BlockPos pos,ShapeContext context){var shape=super.getCollisionShape(state,view,pos,context);return overlayRecipe<0?shape:net.minecraft.util.shape.VoxelShapes.union(shape,StaticArchitectureCatalog.recipes().get(overlayRecipe).collisionShape());}
        @Override public net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state,net.minecraft.world.BlockView view,net.minecraft.util.math.BlockPos pos,ShapeContext context){var shape=super.getOutlineShape(state,view,pos,context);return overlayRecipe<0?shape:net.minecraft.util.shape.VoxelShapes.union(shape,StaticArchitectureCatalog.recipes().get(overlayRecipe).outlineShape());}
        @Override public ItemStack getPickStack(net.minecraft.world.BlockView view,net.minecraft.util.math.BlockPos pos,BlockState state){return new ItemStack(base.asItem());}
        @Override public java.util.List<ItemStack> getDroppedStacks(BlockState state,net.minecraft.loot.context.LootContextParameterSet.Builder builder){return List.of(new ItemStack(base.asItem(),state.get(TYPE)==net.minecraft.block.enums.SlabType.DOUBLE?2:1));}
        @Override public void onStateReplaced(BlockState state,net.minecraft.world.World world,net.minecraft.util.math.BlockPos pos,BlockState next,boolean moved){
            if(overlayRecipe>=0&&!state.isOf(next.getBlock())&&world instanceof net.minecraft.server.world.ServerWorld server&&(!StaticArchitectureCatalog.contains(next)||StaticArchitectureCatalog.recipe(next).index()!=overlayRecipe))StaticArchitectureRuntime.replaced(server,pos,state);
            super.onStateReplaced(state,world,pos,next,moved);
        }
    }
    private static class SlabItem extends BlockItem {
        private final Slab slab;
        SlabItem(Slab slab,Item.Settings settings){super(slab,settings);this.slab=slab;}
        @Override protected BlockState getPlacementState(ItemPlacementContext context){
            var existing=context.getWorld().getBlockState(context.getBlockPos());
            if(existing.getBlock() instanceof Slab other&&other.overlayRecipe>=0&&other.base==slab.base&&existing.get(SlabBlock.TYPE)!=net.minecraft.block.enums.SlabType.DOUBLE){var next=existing.with(SlabBlock.TYPE,net.minecraft.block.enums.SlabType.DOUBLE).with(SlabBlock.WATERLOGGED,false);return canPlace(context,next)?next:null;}
            return super.getPlacementState(context);
        }
    }
}
