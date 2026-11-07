package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectGeometry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Adjust newly placed RP objects before their surface anchor and collision are computed. */
@Mixin(targets = "dev.dreamwalker.bloodbornerp.object.PlacementObjectItem", remap = false)
public abstract class RpPlacementRotationMixin {
    @ModifyArg(method = "useOriginal", index = 4, at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornerp/object/ObjectRegistry;placeOnSurface(Lnet/minecraft/class_1657;Ljava/lang/String;Lnet/minecraft/class_243;Lnet/minecraft/class_2350;FLnet/minecraft/class_1799;)Z"))
    private float dw$surfaceYaw(PlayerEntity player, String id, Vec3d pos, Direction face, float degrees, ItemStack stack) {
        return dw$fitRpModel(id, degrees);
    }

    @ModifyArg(method = "useOriginal", index = 3, at = @At(value = "INVOKE",
        target = "Ldev/dreamwalker/bloodbornerp/object/ObjectRegistry;place(Lnet/minecraft/class_1657;Ljava/lang/String;Lnet/minecraft/class_2338;FLnet/minecraft/class_1799;)Z"))
    private float dw$groundYaw(PlayerEntity player, String id, BlockPos pos, float degrees, ItemStack stack) {
        return dw$fitRpModel(id, degrees);
    }

    @Unique
    private static float dw$fitRpModel(String id, float degrees) {
        String canonical = ObjectRegistry.canonicalId(id);
        var bounds = RpObjectGeometry.localVisualBounds(canonical);
        // RP's GeckoLib geometry already includes one extra quarter turn.
        return canonical.equals("stairs") || bounds.getXLength() > bounds.getZLength() + 1e-5 ? degrees + 90 : degrees;
    }
}
