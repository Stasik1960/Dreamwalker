package daot.jei;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
public record HardenedFurnaceRecipe(ItemStack primaryInput, int primaryCount, ItemStack secondaryInput, int secondaryCount, ItemStack output) {
   public List<ItemStack> getInputs() {
      ItemStack primary = this.primaryInput.copy();
      primary.setCount(this.primaryCount);
      ItemStack secondary = this.secondaryInput.copy();
      secondary.setCount(this.secondaryCount);
      return List.of(primary, secondary);
   }
}
