package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.composite.NativePlayerBreak;
import dev.dreamwalker.bloodbornedw.item.NrpBreakScope;
import java.util.Collection;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NativePlayerBreak.class, remap = false)
abstract class NativeNrpPermissionsMixin {
    @Redirect(method = "allow", remap = false, at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
            target = "Lnet/minecraft/class_1656;field_7476:Z", remap = false))
    private static boolean dw$checkAdventureTool(PlayerAbilities abilities,ServerWorld world,
                                                 PlayerEntity player,Collection<BlockPos> cells) {
        return abilities.allowModifyWorld || NrpBreakScope.permitsAdventure(world,player);
    }
}
