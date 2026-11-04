package dev.dreamwalker.bloodbornedw.item.mixin;

import dev.dreamwalker.bloodbornedw.item.NrpBreakScope;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Block.class)
abstract class NrpLootMixin {
    @Redirect(method = "dropStack(Lnet/minecraft/world/World;Ljava/util/function/Supplier;Lnet/minecraft/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/GameRules;getBoolean(Lnet/minecraft/world/GameRules$Key;)Z"))
    private static boolean dw$dropMinedDreamWalkerLoot(GameRules rules,GameRules.Key<GameRules.BooleanRule> key,
                                                      World world,Supplier<ItemEntity> spawn,ItemStack stack) {
        return key == GameRules.DO_TILE_DROPS && NrpBreakScope.dropsIn(world) || rules.getBoolean(key);
    }
}
