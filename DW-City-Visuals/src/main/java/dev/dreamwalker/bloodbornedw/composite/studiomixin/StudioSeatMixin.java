package dev.dreamwalker.bloodbornedw.composite.studiomixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dreamwalker.bloodbornedw.composite.BenchSeating;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.StudioFootprints;
import dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BenchSeating.class, remap = false)
public abstract class StudioSeatMixin {
    @ModifyReturnValue(method = "points", at = @At("RETURN"))
    private static List<BenchSeating.Point> dw$authoredSeats(List<BenchSeating.Point> original, BlockPos root,
            BlockState state, NbtCompound payload) {
        if (!StudioFootprints.edited(state)) return original;
        var geometry = StudioFootprints.dynamic(CompositeData.data(state, payload));
        if (geometry == null || geometry.outline() == null) return original;
        return geometry.seats().stream().map(seat -> new BenchSeating.Point(
                seat.contact().add(root.getX(), root.getY(), root.getZ()), seat.yaw())).toList();
    }

    @Inject(method = "valid", at = @At("HEAD"), cancellable = true)
    private static void dw$validEntitySeat(World world, BlockPos root, String identity,
            CallbackInfoReturnable<Boolean> result) {
        if (StudioRpGeometry.rpSeat(identity)) result.setReturnValue(world instanceof ServerWorld server
                && StudioRpGeometry.validSeat(server, root, identity));
    }
}
