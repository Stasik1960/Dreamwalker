package dev.dreamwalker.bloodbornedw.tool.mixin;

import dev.dreamwalker.bloodbornedw.composite.AutoStoneFence;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BbToolService.class, remap = false)
abstract class ImportedFenceWallModeMixin {
    @Inject(method = "special", at = @At("HEAD"), cancellable = true)
    private static void dw$toggleImportedFence(ServerPlayerEntity player, ToolTarget target, CallbackInfoReturnable<Boolean> result) {
        var world = player.getServerWorld();
        if (!BbToolService.canEdit(player) || target == null || target.world(player.getServer()) != world || !target.valid(player.getServer())) return;
        var pos = BlockPos.fromLong(target.root());
        if (!world.canPlayerModifyAt(player, pos)) return;
        var state = world.getBlockState(pos);
        Boolean changed = AutoStoneFence.toggleStraight(world, pos, state, player, player.getHorizontalFacing().rotateYClockwise().getAxis());
        if (changed == null) return;
        if (changed) {
            player.sendMessage(Text.literal(state.get(CompositeRootBlock.OPEN)
                    ? "Ограда: соединение с соседями" : "Ограда: сплошная секция"), true);
        }
        result.setReturnValue(changed);
    }
}
