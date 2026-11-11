package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.fabric.FabricWorldEdit;
import dev.dreamwalker.bloodbornedw.editor.worldedit.CompassPassage;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Runs inside WorldEdit's right-click handler, after its permissions and distance check. */
@Mixin(targets = "com.sk89q.worldedit.command.tool.NavigationWand", remap = false)
public abstract class CompassNavigationMixin {
    @WrapOperation(method = "actPrimary", at = @At(value = "INVOKE",
        target = "Lcom/sk89q/worldedit/entity/Player;passThroughForwardWall(I)Z"))
    private boolean dw$throughDreamwalker(Player player, int maxDistance, Operation<Boolean> original) {
        var server = FabricWorldEdit.LIFECYCLED_SERVER.valueOrThrow();
        var nativePlayer = server.getPlayerManager().getPlayer(player.getUniqueId());
        if (nativePlayer != null && nativePlayer.getMainHandStack().isOf(Items.COMPASS)) {
            // Consume the duplicate without WorldEdit reporting a failed traversal.
            if (!dev.dreamwalker.bloodbornedw.tool.ClickRateLimit.accept(nativePlayer, 2)) return true;
            var outcome = CompassPassage.teleport(nativePlayer, maxDistance);
            if (outcome != CompassPassage.Outcome.VANILLA) return outcome == CompassPassage.Outcome.TELEPORTED;
        }
        return original.call(player, maxDistance);
    }
}
