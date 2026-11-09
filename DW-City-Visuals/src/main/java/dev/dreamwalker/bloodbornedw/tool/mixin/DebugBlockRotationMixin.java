package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.tool.BbToolRotations;
import dev.dreamwalker.bloodbornedw.tool.DebugBlockRotation;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BbToolRotations.class, remap = false)
abstract class DebugBlockRotationMixin {
    @Redirect(method = "rotate", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/composite/GlazingTypes;step90(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2680;"))
    private static BlockState dw$glazing45(BlockState state) {
        return state.with(CompositeRootBlock.ROTATION,
                (state.get(CompositeRootBlock.ROTATION) + DebugBlockRotation.direction()) & 7);
    }

    @WrapOperation(method = "rotate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2680;method_11657(Lnet/minecraft/class_2769;Ljava/lang/Comparable;)Ljava/lang/Object;"),
            require = 3)
    private static Object dw$compositeDirection(BlockState state, Property<?> property, Comparable<?> value,
            Operation<Object> original) {
        if (property == CompositeRootBlock.ROTATION && DebugBlockRotation.direction() < 0) {
            value = (state.get(CompositeRootBlock.ROTATION) - 1) & 7;
        }
        return original.call(state, property, value);
    }

    @WrapOperation(method = "rotate", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/architecture/wall/PrototypeWallBlock;rotate45(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2680;"))
    private static BlockState dw$wallDirection(PrototypeWallBlock wall, BlockState state, Operation<BlockState> original) {
        BlockState next = original.call(wall, state);
        if (DebugBlockRotation.direction() > 0) return next;
        int reversed = (2 * state.get(PrototypeWallBlock.ROTATION) - next.get(PrototypeWallBlock.ROTATION)) & 7;
        return PrototypeWallBlock.canonicalForm(next.with(PrototypeWallBlock.ROTATION, reversed));
    }

    @WrapOperation(method = "rotate", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornedw/architecture/PrototypeLadderBlock;step45(Lnet/minecraft/class_2680;)Lnet/minecraft/class_2680;"))
    private static BlockState dw$ladderDirection(PrototypeLadderBlock ladder, BlockState state,
            Operation<BlockState> original) {
        return DebugBlockRotation.direction() > 0 ? original.call(ladder, state)
                : PrototypeLadderBlock.withYaw(state, PrototypeLadderBlock.yaw(state) - 1);
    }

    @WrapOperation(method = "rotate", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;rotateObject(Lnet/minecraft/class_1657;F)Z"))
    private static boolean dw$objectDirection(RpObjectEntity object, PlayerEntity player, float yaw,
            Operation<Boolean> original) {
        return original.call(object, player, DebugBlockRotation.direction() > 0 ? yaw : yaw - 90.0F);
    }

    @Redirect(method = "rotate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2680;method_26186(Lnet/minecraft/class_2470;)Lnet/minecraft/class_2680;"))
    private static BlockState dw$nativeDirection(BlockState state, BlockRotation fallback) {
        int direction = DebugBlockRotation.direction();
        if (state.contains(Properties.ROTATION)) {
            return state.with(Properties.ROTATION, (state.get(Properties.ROTATION) + 2 * direction) & 15);
        }
        return state.rotate(direction > 0 ? fallback : BlockRotation.COUNTERCLOCKWISE_90);
    }
}
