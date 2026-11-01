package dev.dreamwalker.bloodbornedw.composite;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

/** Independent sparse metadata; the physical block and its block entity remain unchanged. */
public final class RetainedFrames {
    public static final Identifier UPDATE = new Identifier("bloodborne_dw", "retained_frame_update");
    private RetainedFrames() {}
    public static NativeVisualData data(Chunk chunk) { return NativeVisuals.data(chunk); }
    public static boolean hasSection(NativeVisualData data, int sectionY) {
        for (long encoded : ((RetainedFrameData)(Object)data).dw$frames().keySet()) if ((BlockPos.fromLong(encoded).getY() >> 4) == sectionY) return true;
        return false;
    }
    public static Map<Long, Integer> all(NativeVisualData data) { return ((RetainedFrameData)(Object)data).dw$frames(); }
    public static int profile(NativeVisualData data, BlockPos pos) { return ((RetainedFrameData)(Object)data).dw$frames().getOrDefault(pos.asLong(), -1); }
    public static BlockState get(Chunk chunk, BlockPos pos) { int profile = profile(NativeVisuals.data(chunk), pos); return profile < 0 ? null : state(profile); }
    public static BlockState state(int profile) {
        if (profile < 0 || profile > 1) throw new IllegalArgumentException("Invalid retained frame profile");
        return Registries.BLOCK.get(SourceFallbackCarrierV1271.TRANSLUCENT_ID).getDefaultState()
            .with(SourceFallbackCarrierV1271.SOURCE, 63).with(SourceFallbackCarrierV1271.SOURCE_BANK, 0)
            .with(CompositeRootBlock.PROFILE, profile == 0 ? CompositeRootBlock.Profile.BASE : CompositeRootBlock.Profile.ALT);
    }
    /** Local index = y * 256 + z * 16 + x. */
    public static Map<Integer, Integer> section(NativeVisualData data, int sectionY) {
        if (!hasSection(data, sectionY)) return Map.of();
        Map<Integer, Integer> result = new HashMap<>();
        ((RetainedFrameData)(Object)data).dw$frames().forEach((encoded, profile) -> {
            BlockPos pos = BlockPos.fromLong(encoded); if ((pos.getY() >> 4) == sectionY)
                result.put(((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15), profile);
        });
        return Map.copyOf(result);
    }
    public static void put(WorldChunk chunk, BlockPos pos, BlockState frame) {
        if (!RetainedFrameState.matches(frame)) throw new IllegalArgumentException("Not the source 63 frame");
        int profile = frame.get(CompositeRootBlock.PROFILE) == CompositeRootBlock.Profile.ALT ? 1 : 0;
        change(chunk, pos, profile, true);
    }
    public static void remove(WorldChunk chunk, BlockPos pos) { change(chunk, pos, -1, true); }
    public static void applyRemote(WorldChunk chunk, BlockPos pos, int profile) { change(chunk, pos, profile, false); }
    private static void change(WorldChunk chunk, BlockPos pos, int profile, boolean publish) {
        if (!new ChunkPos(pos).equals(chunk.getPos()) || pos.getY() < chunk.getBottomY() || pos.getY() >= chunk.getTopY() || profile < -1 || profile > 1)
            throw new IllegalArgumentException("Retained frame outside chunk or invalid profile");
        NativeVisualData old = NativeVisuals.data(chunk); RetainedFrameData attachment = (RetainedFrameData)(Object)old;
        if (attachment.dw$frames().getOrDefault(pos.asLong(), -1) == profile) return;
        Map<Long, Integer> frames = new HashMap<>(attachment.dw$frames());
        if (profile < 0) frames.remove(pos.asLong()); else frames.put(pos.asLong(), profile);
        NativeVisuals.data(chunk, attachment.dw$withFrames(frames));
        if (publish) {
            chunk.setNeedsSaving(true);
            if (chunk.getWorld() instanceof ServerWorld world) for (var player : PlayerLookup.tracking(world, pos)) {
                var buf = PacketByteBufs.create(); buf.writeIdentifier(world.getRegistryKey().getValue()); buf.writeBlockPos(pos); buf.writeByte(profile + 1);
                ServerPlayNetworking.send(player, UPDATE, buf);
            }
        }
    }
}


