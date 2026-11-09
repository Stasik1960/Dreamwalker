package dev.dreamwalker.bloodbornedw.tool.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dreamwalker.bloodbornedw.architecture.RpCollisionShapes;
import dev.dreamwalker.bloodbornedw.composite.StudioRpGeometry;
import dev.dreamwalker.bloodbornedw.tool.DebugAdjustments;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.EntityView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RpCollisionShapes.class, remap = false)
abstract class RpPlayerDebugCollisionMixin {
    @WrapOperation(method = "append", at = @At(value = "INVOKE",
            target = "Ldev/dreamwalker/bloodbornerp/object/RpObjectEntity;activePhysicalBoxes()Ljava/util/List;"))
    private static List<Box> dw$playerParts(RpObjectEntity object, Operation<List<Box>> original,
            EntityView view, Entity mover) {
        if (!(mover instanceof PlayerEntity)) return original.call(object);
        if (DebugAdjustments.playersDisabled(object)) return List.of();
        var authored = StudioRpGeometry.layout(object);
        return authored == null ? original.call(object) : authored.players();
    }
}
