package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** Exact opaque unit cubes can be stored as native palettes in the full city.
 * Old/new hand-built owners retain compact=false and their existing UUID/NBT.
 * compact=true is an explicitly proven source-import state, never a whole-ID
 * replacement. No per-cell BE or global owner ledger is allocated in that mode. */
public class ImportedCompactBlock extends ImportedArtworkBlock {
    public static final BooleanProperty COMPACT=BooleanProperty.of("compact");
    private static final String OPTICAL_SOURCE_FAMILY="bloodborne_dw:source_snow_height12";
    private record Pose(boolean opaque,List<VoxelShape> collision,List<VoxelShape> aim,List<BlockState> sourceOptics) {}
    private static final Map<String,Map<Integer,Pose>> TYPES=load();
    private static VoxelShape shape(JsonArray boxes) {
        VoxelShape result=VoxelShapes.empty();
        for(var raw:boxes){var box=raw.getAsJsonObject();var a=box.getAsJsonArray("from");var b=box.getAsJsonArray("to");result=VoxelShapes.union(result,VoxelShapes.cuboid(a.get(0).getAsDouble()/16,a.get(1).getAsDouble()/16,a.get(2).getAsDouble()/16,b.get(0).getAsDouble()/16,b.get(1).getAsDouble()/16,b.get(2).getAsDouble()/16));}
        return result;
    }
    private static Map<String,Map<Integer,Pose>> load() {
        var input=ImportedCompactBlock.class.getResourceAsStream("/bloodborne_dw/imported-compact-v127.json");
        if(input==null)return Map.of();
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            Map<String,Map<Integer,Pose>> result=new LinkedHashMap<>();var doc=JsonParser.parseReader(reader).getAsJsonObject();
            if(doc.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Compact metadata schema");
            Map<String,VoxelShape> shared=new HashMap<>();
            for(var raw:doc.getAsJsonArray("types")) {
                var row=raw.getAsJsonObject();String id=row.get("registryId").getAsString();
                if(!id.startsWith("bloodborne_dw:source_"))throw new IllegalArgumentException("Original V12 cannot be compacted");
                Set<Integer> opaque=new HashSet<>();for(var n:row.getAsJsonArray("opaquePoses"))opaque.add(n.getAsInt());
                Map<Integer,Pose> poses=new LinkedHashMap<>();
                for(var n:row.getAsJsonArray("poses")) {
                    int pose=n.getAsInt();if(pose<0||pose>479)throw new IllegalArgumentException("Compact pose range");
                    var rotations=row.getAsJsonObject("shapes").getAsJsonArray(Integer.toString(pose));
                    if(rotations.size()!=8)throw new IllegalArgumentException("Compact pose requires eight reviewed physics orientations");
                    List<VoxelShape> collision=new ArrayList<>(),aim=new ArrayList<>();
                    for(int yaw=0;yaw<8;yaw++) {
                        var v=rotations.get(yaw).getAsJsonObject();JsonArray c=v.getAsJsonArray("collision"),a=v.getAsJsonArray("aim");
                        if(c==null||a==null)throw new IllegalArgumentException("Compact orientation lacks collision/aim arrays");
                        // compact(state) rejects odd yaw. Those states always
                        // use the unchanged owned renderer/physics superclass.
                        // Do not eagerly construct shapes that can never be used.
                        if((yaw&1)!=0){collision.add(VoxelShapes.empty());aim.add(VoxelShapes.empty());continue;}
                        var physical=shared.computeIfAbsent(c.toString(),ignored->shape(c));collision.add(physical);
                        aim.add(c.size()>0?physical:shared.computeIfAbsent(a.toString(),ignored->shape(a)));
                    }
                    List<BlockState> sourceOptics=List.of();
                    if(row.has("nativeSourceOptics")&&row.getAsJsonObject("nativeSourceOptics").has(Integer.toString(pose))){
                        if(!id.equals(OPTICAL_SOURCE_FAMILY)||pose<4)throw new IllegalArgumentException("Only appended source optical states may change compact lighting");
                        var optical=row.getAsJsonObject("nativeSourceOptics").getAsJsonObject(Integer.toString(pose));
                        BlockState nativeState=ImportedSourceLightingV127.source(optical.get("sourceState").getAsString());
                        int reference=optical.get("referenceRotation").getAsInt();if(reference<0||reference>7||reference%2!=0)throw new IllegalArgumentException("Native optical reference rotation");
                        var rotated=new ArrayList<BlockState>(8);
                        for(int yaw=0;yaw<8;yaw++)rotated.add(nativeState.rotate(switch(Math.floorMod(yaw-reference,8)/2){case 1->BlockRotation.CLOCKWISE_90;case 2->BlockRotation.CLOCKWISE_180;case 3->BlockRotation.COUNTERCLOCKWISE_90;default->BlockRotation.NONE;}));
                        sourceOptics=List.copyOf(rotated);
                    }
                    poses.put(pose,new Pose(opaque.contains(pose),List.copyOf(collision),List.copyOf(aim),sourceOptics));
                }
                result.put(id,Map.copyOf(poses));
            }
            return Map.copyOf(result);
        }catch(IOException failure){throw new IllegalStateException("Cannot read exact compact cube states",failure);}
    }
    public static boolean supports(String path){return TYPES.containsKey(path.contains(":")?path:"bloodborne_dw:"+path);}
    public static boolean compact(BlockState state){return state.getBlock() instanceof ImportedCompactBlock block&&state.get(COMPACT)&&state.get(ROTATION)%2==0&&TYPES.get(block.spec.id.toString()).containsKey(ImportedArtworkBlock.variant(state));}
    private static Pose pose(BlockState state){return TYPES.get(((ImportedCompactBlock)state.getBlock()).spec.id.toString()).get(ImportedArtworkBlock.variant(state));}
    public static boolean opaque(BlockState state){return compact(state)&&pose(state).opaque;}
    /** Materialize only the explicitly selected palette cube for height/tool
     * editing. The transaction keeps all unrelated guests and restores the
     * exact native palette on preflight/write failure. */
    public static dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner materialize(net.minecraft.server.world.ServerWorld world,BlockPos pos,PlayerEntity player) {
        BlockState before=world.getBlockState(pos);if(!compact(before)||!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(world,player,pos))return null;
        BlockState normal=before.with(COMPACT,false);NbtCompound payload=new NbtCompound();payload.putInt("ImportedTypeVersion",1);
        var object=CompositeRuntime.instance(world,pos,normal,UUID.randomUUID(),payload);
        var result=dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditObjectTransactions.replace(world,List.of(),List.of(object));
        return result.outcome()==dev.dreamwalker.bloodbornedw.runtime.TransactionCore.Outcome.COMMITTED?object.owner():null;
    }
    private static BlockState sourceOptics(BlockState state){
        // hasSidedTransparency is read while Block's StateManager is being built,
        // before CompositeRootBlock.spec/stateManager have been assigned. This
        // one appended family is identified by class and static properties.
        if(!(state.getBlock() instanceof SourceOptics)||!state.contains(COMPACT)||!state.get(COMPACT)||state.get(ROTATION)%2!=0)return null;
        int variant=state.get(VARIANT)+(state.contains(ART_BANK)?16*state.get(ART_BANK):0);
        Pose pose=TYPES.get(OPTICAL_SOURCE_FAMILY).get(variant);
        return pose==null||pose.sourceOptics.isEmpty()?null:pose.sourceOptics.get(state.get(ROTATION));
    }
    public ImportedCompactBlock(CompositeSpec spec){super(spec,Settings.create().strength(.4F).dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).solidBlock((state,view,pos)->{var source=sourceOptics(state);return source==null?opaque(state):source.isSolidBlock(view,pos);}).suffocates((state,view,pos)->{var source=sourceOptics(state);return source==null?opaque(state):source.shouldSuffocate(view,pos);}).blockVision((state,view,pos)->{var source=sourceOptics(state);return source==null?opaque(state):source.shouldBlockVision(view,pos);}));setDefaultState(getDefaultState().with(COMPACT,false));}
    public static ImportedCompactBlock create(CompositeSpec spec){if(spec.id.toString().equals(OPTICAL_SOURCE_FAMILY)&&spec.variants.size()>16)return new SourceOptics(spec);if(spec.variants.size()<=16)return new ImportedCompactBlock(spec);if(spec.id.getPath().equals("source_assembly_brick_wall"))return new Brick(spec);if(spec.id.getPath().equals("source_assembly_deepslate_brick_wall"))return new Deepslate(spec);return new Banked(spec);}
    private static class Banked extends ImportedCompactBlock {private Banked(CompositeSpec spec){super(spec);}}
    private static final class SourceOptics extends Banked {private SourceOptics(CompositeSpec spec){super(spec);}}
    private static final class Brick extends Banked {private static final net.minecraft.state.property.IntProperty BANK=net.minecraft.state.property.IntProperty.of("art_bank",0,29);private Brick(CompositeSpec spec){super(spec);}}
    private static final class Deepslate extends Banked {private static final net.minecraft.state.property.IntProperty BANK=net.minecraft.state.property.IntProperty.of("art_bank",0,9);private Deepslate(CompositeSpec spec){super(spec);}}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(ROTATION,VARIANT,OPEN,PROFILE,COMPACT);if(this instanceof Banked)builder.add(this instanceof Brick?Brick.BANK:this instanceof Deepslate?Deepslate.BANK:ART_BANK);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return compact(state)?null:super.createBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return compact(state)?BlockRenderType.MODEL:super.getRenderType(state);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView view,BlockPos pos,ShapeContext context){return compact(state)?pose(state).collision.get(state.get(ROTATION)):super.getCollisionShape(state,view,pos,context);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView view,BlockPos pos,ShapeContext context){return compact(state)?pose(state).aim.get(state.get(ROTATION)):super.getOutlineShape(state,view,pos,context);}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView view,BlockPos pos){var source=sourceOptics(state);return source!=null?source.getCullingShape(view,pos):opaque(state)?VoxelShapes.fullCube():super.getCullingShape(state,view,pos);}
    @Override public int getOpacity(BlockState state,BlockView view,BlockPos pos){var source=sourceOptics(state);return source!=null?source.getOpacity(view,pos):ImportedSourceLightingV127.opacity(spec,state,opaque(state)?15:0);}
    @Override public float getAmbientOcclusionLightLevel(BlockState state,BlockView view,BlockPos pos){var source=sourceOptics(state);return source!=null?source.getAmbientOcclusionLightLevel(view,pos):super.getAmbientOcclusionLightLevel(state,view,pos);}
    @Override public boolean isShapeFullCube(BlockState state,BlockView view,BlockPos pos){var source=sourceOptics(state);return source!=null?source.isFullCube(view,pos):super.isShapeFullCube(state,view,pos);}
    @Override public boolean hasSidedTransparency(BlockState state){var source=sourceOptics(state);return source!=null?source.hasSidedTransparency():super.hasSidedTransparency(state);}
    @Override public boolean isTransparent(BlockState state,BlockView view,BlockPos pos){var source=sourceOptics(state);return source!=null?source.isTransparent(view,pos):!opaque(state)&&super.isTransparent(state,view,pos);}
    private ItemStack compactArt(BlockState state){NbtCompound payload=new NbtCompound();payload.putInt("ImportedTypeVersion",1);return art(state,payload);}
    @Override public ItemStack getPickStack(BlockView view,BlockPos pos,BlockState state){return compact(state)?compactArt(state):super.getPickStack(view,pos,state);}
    @Override public java.util.List<ItemStack> getDroppedStacks(BlockState state,net.minecraft.loot.context.LootContextParameterSet.Builder builder){return compact(state)?List.of(compactArt(state)):super.getDroppedStacks(state,builder);}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit) {
        if(!compact(state))return super.onUse(state,world,pos,player,hand,hit);
        if(player.isSneaking()&&player.getStackInHand(hand).isEmpty()&&TYPES.get(spec.id.toString()).size()>1) {
            var allowed=new ArrayList<>(TYPES.get(spec.id.toString()).keySet());allowed.removeIf(p->ImportedSourcePartsV127.part(this,p));Collections.sort(allowed);if(allowed.isEmpty())return ActionResult.PASS;int next=allowed.get((allowed.indexOf(ImportedArtworkBlock.variant(state))+1)%allowed.size());BlockState changed=state.with(VARIANT,next%16);if(bankProperty(state)!=null)changed=changed.with(bankProperty(state),next/16);
            if(!world.isClient)world.setBlockState(pos,changed,Block.NOTIFY_ALL);return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    /** A compact cube's copied item uses ordinary native block placement. */
    public static final class CompactItem extends CompositeItem {
        public CompactItem(ImportedCompactBlock root){super(root);}
        @Override public ActionResult place(ItemPlacementContext context) {
            NbtCompound tag=context.getStack().getSubNbt("BlockStateTag");
            if(tag==null||!"true".equals(tag.getString("compact")))return super.place(context);
            ItemStack safe=context.getStack().copy();NbtCompound art=safe.getOrCreateSubNbt("BlockStateTag");art.remove("rotation");art.putString("open","false");safe.getOrCreateNbt().putInt("ImportedTypeVersion",1);
            int yaw=context.getPlayer()==null?0:Math.floorMod(Math.round(context.getPlayer().getYaw()/45),8);
            // Native palette rendering is proven only for cardinal orientations.
            // A 45 degree manual placement uses the normal owned renderer.
            if(yaw%2!=0)art.putString("compact","false");
            ItemPlacementContext redirected=new ItemPlacementContext(context){@Override public ItemStack getStack(){return safe;}};
            ActionResult result=yaw%2==0?placeNative(redirected):super.place(redirected);
            if(result.isAccepted())context.getStack().decrement(Math.max(0,context.getStack().getCount()-safe.getCount()));return result;
        }
        @Override protected BlockState getPlacementState(ItemPlacementContext context){
            var root=(ImportedCompactBlock)getBlock();BlockState state=root.getDefaultState();NbtCompound tag=context.getStack().getSubNbt("BlockStateTag");
            if(tag!=null)for(var property:root.getStateManager().getProperties())if(tag.contains(property.getName(),8))state=apply(state,property,tag.getString(property.getName()));
            state=state.with(ROTATION,context.getPlayer()==null?0:Math.floorMod(Math.round(context.getPlayer().getYaw()/45),8));
            return compact(state)&&canPlace(context,state)?state:null;
        }
        @Override protected boolean canPlace(ItemPlacementContext context,BlockState state){
            if(!super.canPlace(context,state))return false;
            var pos=context.getBlockPos();var boxes=state.getCollisionShape(context.getWorld(),pos,net.minecraft.block.ShapeContext.absent()).getBoundingBoxes().stream().map(b->b.offset(pos)).toList();
            return dev.dreamwalker.bloodbornedw.architecture.PlacementPhysics.conflict(context.getWorld(),boxes,null,context.getPlayer(),pos)==null;
        }
        private static <T extends Comparable<T>> BlockState apply(BlockState s,net.minecraft.state.property.Property<T> p,String value){return p.parse(value).map(v->s.with(p,v)).orElse(s);}
    }
}
