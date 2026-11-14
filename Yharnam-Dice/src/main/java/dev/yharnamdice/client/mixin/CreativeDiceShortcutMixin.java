package dev.yharnamdice.client.mixin;

import dev.yharnamdice.DiceMod;
import dev.yharnamdice.client.CreativeDiceShortcut;
import net.fabricmc.fabric.impl.client.itemgroup.CreativeGuiExtensions;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeInventoryScreen.class)
abstract class CreativeDiceShortcutMixin<T extends ScreenHandler> extends AbstractInventoryScreen<T> {
    CreativeDiceShortcutMixin(T handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Shadow protected abstract void setSelectedTab(ItemGroup group);

    @Inject(method = "init", at = @At("TAIL"))
    private void yharnamDice$addTabShortcut(CallbackInfo ci) {
        ItemGroup group = Registries.ITEM_GROUP.get(DiceMod.id("dice"));
        if (group == null || group.getDisplayStacks().isEmpty()) return;
        int left = Math.min(x + backgroundWidth + 4, width - 25);
        var button = new CreativeDiceShortcut(left, y + 4,
                group.getDisplayName(), ignored -> yharnamDice$selectGroup(group));
        addDrawableChild(button);
    }

    @Unique
    private void yharnamDice$selectGroup(ItemGroup group) {
        if (!((Object)this instanceof CreativeGuiExtensions pages)
                || !(group instanceof net.fabricmc.fabric.impl.itemgroup.FabricItemGroup paged)) {
            setSelectedTab(group);
            return;
        }
        // Go through Fabric's paging callbacks so its vanilla and mod tabs retain their layout.
        int target = paged.getPage();
        while (pages.fabric_currentPage() != target) {
            int previous = pages.fabric_currentPage();
            if (previous < target) pages.fabric_nextPage();
            else pages.fabric_previousPage();
            if (pages.fabric_currentPage() == previous) return;
        }
        setSelectedTab(group);
    }

}
