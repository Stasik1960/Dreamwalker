package dev.dreamwalker.bloodbornedw.editor.worldedit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge", remap = false)
abstract class WorldEditStateValueMixin {
    @WrapOperation(method = "with", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/class_2769;method_11900(Ljava/lang/String;)Ljava/util/Optional;"))
    private static Optional<?> dw$parseEnumValue(Property<?> property, String value, Operation<Optional<?>> original) {
        Optional<?> parsed = original.call(property, value);
        if (parsed.isPresent() || !(property instanceof EnumProperty<?>)) return parsed;
        // WorldEdit serializes some enum values as NORTH/LOWER, while Yarn
        // properties parse the names stored in Minecraft blockstates.
        String canonical = value.toLowerCase(Locale.ROOT);
        return canonical.equals(value) ? parsed : original.call(property, canonical);
    }
}
