package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornedw.composite.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NativeVisualData.class, remap = false)
public abstract class RetainedFrameDataMixin implements RetainedFrameData {
    @Shadow @Final private Map<Integer, int[]> sections;
    @Shadow @Final private Set<Long> forcedWater;
    @Unique private Map<Long, Integer> dw$frames = Map.of();

    @Invoker("<init>")
    private static NativeVisualData dw$new(Map<Integer, int[]> sections, Set<Long> water) { throw new AssertionError(); }

    @Override public Map<Long, Integer> dw$frames() { return dw$frames; }
    @Override public NativeVisualData dw$withFrames(Map<Long, Integer> frames) {
        if (frames.isEmpty()) return sections.isEmpty() && forcedWater.isEmpty() ? NativeVisualData.EMPTY : dw$new(sections, forcedWater);
        NativeVisualData copy = dw$new(sections, forcedWater);
        ((RetainedFrameData)(Object)copy).dw$installFrames(frames);
        return copy;
    }
    @Override public void dw$installFrames(Map<Long, Integer> frames) {
        if ((Object)this == NativeVisualData.EMPTY || !dw$frames.isEmpty() || frames.isEmpty()) throw new IllegalStateException("Retained frame snapshot already published");
        dw$frames = Map.copyOf(frames);
    }

    @Inject(method = "empty", at = @At("RETURN"), cancellable = true)
    private void dw$notEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (!dw$frames.isEmpty()) cir.setReturnValue(false);
    }
    @Inject(method = "with", at = @At("RETURN"), cancellable = true)
    private void dw$carryFrames(BlockPos pos, int index, CallbackInfoReturnable<NativeVisualData> cir) {
        if (!dw$frames.isEmpty() && cir.getReturnValue() != (Object)this)
            cir.setReturnValue(((RetainedFrameData)(Object)cir.getReturnValue()).dw$withFrames(dw$frames));
    }
    @Inject(method = "nbt", at = @At("RETURN"))
    private void dw$writeFrames(CallbackInfoReturnable<NbtCompound> cir) {
        if (dw$frames.isEmpty()) return;
        NbtList list = new NbtList();
        dw$frames.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            NbtCompound row = new NbtCompound(); row.putLong("Pos", entry.getKey()); row.putByte("Profile", entry.getValue().byteValue()); list.add(row);
        });
        cir.getReturnValue().put("Source63Frames", list);
    }
    @Inject(method = "read", at = @At("RETURN"), cancellable = true)
    private static void dw$readFrames(NbtCompound nbt, Chunk chunk, CallbackInfoReturnable<NativeVisualData> cir) {
        if (!nbt.contains("Source63Frames")) return;
        if (!nbt.contains("Source63Frames", NbtElement.LIST_TYPE)) throw new IllegalArgumentException("Invalid retained frame list");
        NbtList list = (NbtList)nbt.get("Source63Frames");
        if ((!list.isEmpty() && list.getHeldType() != NbtElement.COMPOUND_TYPE) || list.size() > chunk.countVerticalSections() * 4096) throw new IllegalArgumentException("Retained frame budget");
        Map<Long, Integer> frames = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound row = list.getCompound(i);
            if (!row.contains("Pos", NbtElement.LONG_TYPE) || !row.contains("Profile", NbtElement.BYTE_TYPE)) throw new IllegalArgumentException("Invalid retained frame entry");
            long encoded = row.getLong("Pos"); BlockPos pos = BlockPos.fromLong(encoded); int profile = row.getByte("Profile");
            if (!new ChunkPos(pos).equals(chunk.getPos()) || pos.getY() < chunk.getBottomY() || pos.getY() >= chunk.getTopY() || profile < 0 || profile > 1 || frames.put(encoded, profile) != null)
                throw new IllegalArgumentException("Retained frame position/profile");
        }
        if (!frames.isEmpty()) cir.setReturnValue(((RetainedFrameData)(Object)cir.getReturnValue()).dw$withFrames(frames));
    }
}




