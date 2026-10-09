package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.BuilderToolMigration;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public abstract class BuilderToolMigrationMixin {
    @ModifyVariable(method="fromNbt",at=@At("HEAD"),argsOnly=true)
    private static NbtCompound dwMigrateSavedBuilder(NbtCompound serialized) {
        return BuilderToolMigration.migrateNbt(serialized);
    }
    @ModifyVariable(method="<init>(Lnet/minecraft/item/ItemConvertible;I)V",at=@At("HEAD"),argsOnly=true)
    private static ItemConvertible dwCanonicalBuilderItem(ItemConvertible item) {
        return BuilderToolMigration.canonicalItem(item);
    }
}
