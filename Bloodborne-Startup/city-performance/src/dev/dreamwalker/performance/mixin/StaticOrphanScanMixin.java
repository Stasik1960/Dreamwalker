package dev.dreamwalker.performance.mixin;

import dev.dreamwalker.bloodbornedw.composite.StaticArchitectureCatalog;
import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Cell;
import java.util.function.Predicate;
import net.minecraft.class_2680;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "dev.dreamwalker.bloodbornedw.composite.StaticArchitectureRuntime", remap = false)
public abstract class StaticOrphanScanMixin {
    @ModifyArg(method = "loadedChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/class_2826;method_19523(Ljava/util/function/Predicate;)Z"), remap = false)
    private static Predicate<class_2680> dw$findPotentialOrphans(Predicate<class_2680> isStatic) {
        return state -> isStatic.test(state) && dw$needsRootLookup(state);
    }

    @Unique
    private static boolean dw$needsRootLookup(class_2680 state) {
        var recipe = StaticArchitectureCatalog.recipe(state);
        var fragments = recipe.fragments();
        // A singleton rooted at this cell validates against the state we just
        // read. Only other recipes can enqueue work in the original scan.
        return fragments.size() != 1 || !fragments.get(0).origin().equals(Cell.ORIGIN)
                || !StaticArchitectureCatalog.matchesRoot(state, fragments.get(0).pose());
    }
}
