package dev.dreamwalker.bloodbornedw.mixin;

import dev.dreamwalker.bloodbornedw.block.DwCarrierState;
import net.minecraft.block.FlowerPotBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/** A carrier pot must not replace vanilla's content-to-potted lookup entry. */
@Mixin(FlowerPotBlock.class)
abstract class FlowerPotBlockMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object bloodborneDw$keepVanillaPotLookup(Map<Object, Object> map, Object key, Object value) {
        return DwCarrierState.isBuilding() ? null : map.put(key, value);
    }
}
