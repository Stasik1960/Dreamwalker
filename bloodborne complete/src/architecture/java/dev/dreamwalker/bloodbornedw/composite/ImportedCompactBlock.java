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
public final class ImportedCompactBlock extends CompositeRootBlock {
    public static final BooleanProperty COMPACT=BooleanProperty.of("compact");
    private record Pose(boolean opaque,List<VoxelShape> collision,List<VoxelShape> aim) {}
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
            for(var raw:doc.getAsJsonArray("types")) {var row=raw.getAsJsonObject();String id=row.get("registryId").getAsString();if(!id.startsWith("bloodborne_dw:source_"))throw new IllegalArgumentException("Original V12 cannot be compacted");Set<Integer> opaque=new HashSet<>();for(var n:row.getAsJsonArray("opaquePoses"))opaque.add(n.getAsInt());Map<Integer,Pose> poses=new LinkedHashMap<>();for(var n:row.getAsJsonArray("poses")){int pose=n.getAsInt();if(pose<0||pose>15)throw new IllegalArgumentException("Compact pose range");List<VoxelShape> collision=new ArrayList<>(),aim=new ArrayList<>();for(var r:row.getAsJsonObject("shapes").getAsJsonArray(Integer.toString(pose))){var v=r.getAsJsonObject();JsonArray c=v.getAsJsonArray("collision"),a=v.getAsJsonArray("aim");collision.add(shared.computeIfAbsent(c.toString(),ignored->shape(c)));aim.add(c.size()>0?collision.get(collision.size()-1):shared.computeIfAbsent(a.toString(),ignored->shape(a)));}if(collision.size()!=8)throw new IllegalArgumentException("Compact pose requires eight reviewed physics orientations");poses.put(pose,new Pose(opaque.contains(pose),List.copyOf(collision),List.copyOf(aim)));}result.put(id,Map.copyOf(poses));}
            return Map.copyOf(result);
        }catch(IOException failure){throw new IllegalStateException("Cannot read exact compact cube states",failure);}
    }
    public static boolean supports(String path){return TYPES.containsKey(path.contains(":")?path:"bloodborne_dw:"+path);}
    public static boolean compact(BlockState state){return state.getBlock() instanceof ImportedCompactBlock block&&state.get(COMPACT)&&state.get(ROTATION)%2==0&&TYPES.get(block.spec.id.toString()).containsKey(state.get(VARIANT));}
    private static Pose pose(BlockState state){return TYPES.get(((ImportedCompactBlock)state.getBlock()).spec.id.toString()).get(state.get(VARIANT));}
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
    public ImportedCompactBlock(CompositeSpec spec){super(spec,Settings.create().strength(.4F).dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK).solidBlock((state,view,pos)->opaque(state)).suffocates((state,view,pos)->opaque(state)).blockVision((state,view,pos)->opaque(state)));setDefaultState(getDefaultState().with(COMPACT,false));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){super.appendProperties(builder);builder.add(COMPACT);}
    @Override public BlockEntity createBlockEntity(BlockPos pos,BlockState state){return compact(state)?null:super.createBlockEntity(pos,state);}
    @Override public BlockRenderType getRenderType(BlockState state){return compact(state)?BlockRenderType.MODEL:super.getRenderType(state);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockView view,BlockPos pos,ShapeContext context){return compact(state)?pose(state).collision.get(state.get(ROTATION)):super.getCollisionShape(state,view,pos,context);}
    @Override public VoxelShape getOutlineShape(BlockState state,BlockView view,BlockPos pos,ShapeContext context){return compact(state)?pose(state).aim.get(state.get(ROTATION)):super.getOutlineShape(state,view,pos,context);}
    @Override public VoxelShape getCullingShape(BlockState state,BlockView view,BlockPos pos){return opaque(state)?VoxelShapes.fullCube():super.getCullingShape(state,view,pos);}
    @Override public int getOpacity(BlockState state,BlockView view,BlockPos pos){return opaque(state)?15:super.getOpacity(state,view,pos);}
    @Override public boolean isTransparent(BlockState state,BlockView view,BlockPos pos){return !opaque(state)&&super.isTransparent(state,view,pos);}
    @Override public ItemStack getPickStack(BlockView view,BlockPos pos,BlockState state){return compact(state)?art(state,new NbtCompound()):super.getPickStack(view,pos,state);}
    @Override public ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,Hand hand,BlockHitResult hit) {
        if(!compact(state))return super.onUse(state,world,pos,player,hand,hit);
        if(player.isSneaking()&&player.getStackInHand(hand).isEmpty()&&TYPES.get(spec.id.toString()).size()>1) {
            var allowed=new ArrayList<>(TYPES.get(spec.id.toString()).keySet());Collections.sort(allowed);int next=allowed.get((allowed.indexOf(state.get(VARIANT))+1)%allowed.size());
            if(!world.isClient)world.setBlockState(pos,state.with(VARIANT,next),Block.NOTIFY_ALL);return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    /** A compact cube's copied item uses ordinary native block placement. */
    public static final class CompactItem extends CompositeItem {
        private final BlockItem nativeItem;
        public CompactItem(ImportedCompactBlock root){super(root);nativeItem=new BlockItem(root,new Item.Settings());}
        @Override public ActionResult place(ItemPlacementContext context) {
            NbtCompound tag=context.getStack().getSubNbt("BlockStateTag");
            return tag!=null&&"true".equals(tag.getString("compact"))?nativeItem.place(context):super.place(context);
        }
    }
}
