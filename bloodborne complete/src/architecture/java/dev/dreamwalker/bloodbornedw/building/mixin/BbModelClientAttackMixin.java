package dev.dreamwalker.bloodbornedw.building.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.dreamwalker.bloodbornedw.building.BbModelClickScope;
import dev.dreamwalker.bloodbornedw.tool.BbToolService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientPlayerInteractionManager.class)
abstract class BbModelClientAttackMixin {
    @Shadow private void syncSelectedSlot() { throw new AssertionError(); }

    @WrapMethod(method = "attackBlock")
    private boolean dw$allowAuthorizedToolAttack(BlockPos pos, Direction side, Operation<Boolean> original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getMainHandStack().isOf(BbToolService.ITEM)) syncSelectedSlot();
        try (var ignored = BbModelClickScope.open(client.player, client.world, pos)) {
            return original.call(pos, side);
        }
    }
}
