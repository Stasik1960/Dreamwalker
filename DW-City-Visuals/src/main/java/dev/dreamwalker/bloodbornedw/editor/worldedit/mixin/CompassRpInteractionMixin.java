package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.fabric.FabricAdapter;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RP targets normally consume the entity click before Fabric's right-click-in-air event. */
@Mixin(value = RpObjectEntity.class, remap = false)
public abstract class CompassRpInteractionMixin {
    @Inject(method = "method_5688", at = @At("HEAD"), cancellable = true)
    private void dw$forwardCompassClick(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity serverPlayer
                && player.getMainHandStack().isOf(Items.COMPASS) && dw$worldEditClick(serverPlayer)) {
            cir.setReturnValue(ActionResult.CONSUME);
        }
    }

    @Unique
    private static boolean dw$worldEditClick(ServerPlayerEntity player) {
        try {
            // The optional WorldEdit compile JAR uses intermediary names for Minecraft types.
            var adaptPlayer = FabricAdapter.class.getMethod("adaptPlayer", ServerPlayerEntity.class);
            return WorldEdit.getInstance().handleRightClick((Player)adaptPlayer.invoke(null, player));
        } catch (ReflectiveOperationException unsupportedWorldEdit) {
            return false;
        }
    }
}
