package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.composite.NativeGridRuntime;
import dev.dreamwalker.bloodbornedw.item.NrpBreakScope;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Native-grid removal still verifies protection for every occupied cell. */
@Mixin(value = NativeGridRuntime.class, remap = false)
abstract class NativeGridNrpPermissionsMixin {
    @Redirect(method = "mayModify", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/class_1656;field_7476:Z", remap = false))
    private static boolean dw$allowScopedAdventureBreak(PlayerAbilities abilities, World world,
                                                        PlayerEntity player, BlockPos pos) {
        return abilities.allowModifyWorld
                || world instanceof ServerWorld serverWorld && NrpBreakScope.permitsAdventure(serverWorld, player);
    }
}
