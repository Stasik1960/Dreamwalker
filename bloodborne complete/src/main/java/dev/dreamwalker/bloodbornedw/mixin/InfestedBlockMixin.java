package dev.dreamwalker.bloodbornedw.mixin;

import dev.dreamwalker.bloodbornedw.block.DwCarrierState;
import net.minecraft.block.InfestedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Map;

/** Registering architecture must not change vanilla silverfish conversions. */
@Mixin(InfestedBlock.class)
abstract class InfestedBlockMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object bloodborneDw$keepVanillaInfestedLookup(Map<Object, Object> map, Object key, Object value) {
        return DwCarrierState.isBuilding() ? null : map.put(key, value);
    }
}
