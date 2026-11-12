package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.RemainingArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeItem;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeSpec;
import dev.dreamwalker.bloodbornedw.composite.DistinctForms;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CompositeItem.class)
abstract class DistinctFormItemMixin {
    @Shadow @Final private CompositeRootBlock root;

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$useFormName(ItemStack stack, CallbackInfoReturnable<Text> cir) {
        if (!DistinctForms.fixed(root)) return;
        int pose = CompositeSpec.poseVariant(root.getDefaultState());
        String name = RemainingArchitecture.variantName(root.spec.id.getPath(), pose);
        if (name == null) name = root.spec.displayName;
        cir.setReturnValue(Text.literal(name + " · форма " + pose));
    }

    @Inject(method = "appendTooltip", at = @At("HEAD"), cancellable = true)
    private void dreamwalker$showFormId(ItemStack stack, World world, List<Text> tooltip,
            TooltipContext context, CallbackInfo ci) {
        if (!DistinctForms.fixed(root)) return;
        tooltip.add(Text.literal(Registries.ITEM.getId(stack.getItem()).toString()));
        ci.cancel();
    }
}
