package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
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

/** The backing and root of a ladder keep their normal loaded, height and protection checks. */
@Mixin(value = SourceLadderRuntime.class, remap = false)
abstract class SourceLadderNrpPermissionsMixin {
    @Redirect(method = "accessible", remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/class_1656;field_7476:Z", remap = false))
    private static boolean dw$allowScopedAdventureBreak(PlayerAbilities abilities, World world,
                                                        BlockPos pos, PlayerEntity player) {
        return abilities.allowModifyWorld
                || world instanceof ServerWorld serverWorld && NrpBreakScope.permitsAdventure(serverWorld, player);
    }
}
