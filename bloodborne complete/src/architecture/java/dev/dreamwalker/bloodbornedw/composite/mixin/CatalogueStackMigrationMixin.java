package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.CatalogueMigration;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class CatalogueStackMigrationMixin {
    @Inject(method="fromNbt",at=@At("RETURN"),cancellable=true)
    private static void canonicalRead(NbtCompound tag,CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(CatalogueMigration.canonicalStack(cir.getReturnValue()));
    }
    @Inject(method="inventoryTick",at=@At("HEAD"))
    private void canonicalInventory(World world,Entity entity,int slot,boolean selected,CallbackInfo ci) {
        if(!world.isClient&&entity instanceof PlayerEntity player) {
            ItemStack old=(ItemStack)(Object)this,next=CatalogueMigration.canonicalStack(old);
            if(next!=old&&player.getInventory().getStack(slot)==old) {player.getInventory().setStack(slot,next);player.getInventory().markDirty();}
        }
    }
}
