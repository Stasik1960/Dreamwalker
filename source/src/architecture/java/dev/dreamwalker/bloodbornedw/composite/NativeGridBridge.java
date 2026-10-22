package dev.dreamwalker.bloodbornedw.composite;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/** One plain shared background state in an already required functional BE.
 * This field never describes an owner, instance geometry or another object. */
public final class NativeGridBridge {
    public static final String KEY = "NativeGridBase";
    /** Immutable chunk-build input; only actual bridge BEs allocate this record. */
    public record RenderData(Object legacy, int base) {}
    private static final class Carriers {
        static final Set<String> IDS = load();
        private static Set<String> load() {
            Set<String> ids = new HashSet<>(); ids.add("bloodborne_dw:composite_cell");
            try (InputStream input = NativeGridBridge.class.getResourceAsStream(NativeGridCatalog.DIRECTORY + "bridge_carriers.json")) {
                if (input != null) try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
                    if (data.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("Unsupported native bridge carrier schema");
                    for (JsonElement id : data.getAsJsonArray("registryIds")) ids.add(id.getAsString());
                }
            } catch (IOException failure) { throw new IllegalStateException("Cannot read native bridge carriers", failure); }
            return Set.copyOf(ids);
        }
    }
    private NativeGridBridge() {}
    public static boolean carrier(BlockState state) {
        return !NativeGridCatalog.cells().isEmpty()
                && (state.getBlock() instanceof CompositeRootBlock || state.isOf(CompositeArchitecture.CELL))
                && Carriers.IDS.contains(Registries.BLOCK.getId(state.getBlock()).toString());
    }
    public static int read(NbtCompound nbt, BlockState carrier) {
        if (!nbt.contains(KEY)) return -1;
        if (!nbt.contains(KEY, 3)) throw new IllegalArgumentException("NativeGridBase must be an integer");
        int index = nbt.getInt(KEY); validate(index, carrier); return index;
    }
    public static void validate(int index, BlockState carrier) {
        if (index < 0 || index >= NativeGridCatalog.cells().size() || !carrier(carrier))
            throw new IllegalArgumentException("Unknown or unsupported native bridge background/carrier");
        if(ImportedCompactBlock.compact(carrier))throw new IllegalArgumentException("A compact no-BE carrier cannot hold NativeGridBase");
        var cell = NativeGridCatalog.cell(index);
        if (NativeGridCatalog.membership(index) != null) throw new IllegalArgumentException("Whole-template roles cannot be bridge backgrounds");
        if (!cell.fluid().isEmpty() || cell.optics().luminance() != 0 || !carrier.getFluidState().isEmpty() || carrier.getLuminance() != 0)
            throw new IllegalArgumentException("Native bridge requires a dry nonemitting base and carrier");
        // These predicates have no world/position argument. A BE cannot safely
        // override them, so accept only an exact match to its physical state.
        if (cell.optics().opaque() != carrier.isOpaque() || cell.optics().sidedTransparency() != carrier.hasSidedTransparency())
            throw new IllegalArgumentException("Native bridge worldless optical predicates differ");
    }
    public static int base(BlockView view, BlockPos pos) {
        if(view==null||pos==null)return -1;
        if(view instanceof ProjectedBlockView projected)return base(projected.backingWorld(),projected.worldPosition(pos));
        int published=CompositeShapeSnapshots.nativeGridBase(view,pos);
        if(published>=0||view instanceof World||view instanceof WorldChunk)return published;
        // Client section regions expose the same immutable BE attachment.
        // Never use this fallback on a server World/light worker.
        return view instanceof BlockRenderView rendered?renderBase(rendered.getBlockEntityRenderData(pos)):-1;
    }
    public static int renderBase(Object data) { return data instanceof RenderData bridge ? bridge.base() : -1; }
    public static VoxelShape union(BlockView view, BlockPos pos, boolean collision, VoxelShape retained) {
        int index = base(view, pos);
        if (index < 0) return retained;
        var cell = NativeGridCatalog.cell(index);
        return VoxelShapes.union(retained, collision ? cell.collision() : cell.outline());
    }
    public static int opacity(int index) { return index < 0 ? 0 : NativeGridCatalog.cell(index).optics().opacity(); }
    public static boolean backgroundHit(BlockView view,BlockPos pos,PlayerEntity player){
        int index=base(view,pos);if(index<0||player==null)return false;
        var eye=player.getEyePos();var end=eye.add(player.getRotationVec(1).multiply(player.isCreative()?5:4.5));
        return NativeGridCatalog.cell(index).outline().raycast(eye,end,pos)!=null;
    }
    /** The old owner picker already excludes an owner hidden by this base. */
    public static boolean breakBackground(ServerWorld world,BlockPos pos,PlayerEntity player){
        int index=base(world,pos);if(index<0)return false;
        if(!backgroundHit(world,pos,player))return false;
        if(!player.getAbilities().allowModifyWorld||!world.canPlayerModifyAt(player,pos))return true;
        if(!(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity)||entity.nativeGridBase()!=index)return true;
        entity.nativeGridBase(-1);entity.markDirty();entity.publishShapeSnapshot();
        world.updateListeners(pos,world.getBlockState(pos),world.getBlockState(pos),Block.NOTIFY_LISTENERS);
        world.getChunkManager().markForUpdate(pos);world.getChunkManager().getLightingProvider().checkBlock(pos);
        world.updateNeighbors(pos,world.getBlockState(pos).getBlock());
        if(!player.getAbilities().creativeMode)Block.dropStack(world,pos,NativeGridRuntime.item(index));
        return true;
    }
}
