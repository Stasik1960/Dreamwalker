package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.item.NrpBreakScope;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Permit an authorised Adventure break while retaining each cell's world protection check. */
@Mixin(value = CompositeRuntime.class, remap = false)
abstract class CompositeNrpPermissionsMixin {
    @Redirect(method = "canModify", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/class_1656;field_7476:Z", remap = false))
    private static boolean dw$allowScopedAdventureBreak(PlayerAbilities abilities, ServerWorld world,
                                                        PlayerEntity player, BlockPos pos) {
        return abilities.allowModifyWorld || NrpBreakScope.permitsAdventure(world, player);
    }
}
