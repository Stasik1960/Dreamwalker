package dev.dreamwalker.selectorindex.mixin;
import dev.dreamwalker.selectorindex.IndexedMultipart;
import java.util.Collection;
import java.util.List;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_816;
import net.minecraft.class_819;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(targets = "net.minecraft.class_1088$class_4455", remap = false)
public abstract class ModelDefinitionIndexMixin {
    @Redirect(method = "method_21607", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_816;method_3519()Ljava/util/List;"), remap = false)
    private static List<class_819> dreamwalker$parts(class_816 multipart, class_2680 state, class_816 original, Collection<class_2769<?>> tint) {
        return ((IndexedMultipart)multipart).dreamwalker$select(state);
    }
}
