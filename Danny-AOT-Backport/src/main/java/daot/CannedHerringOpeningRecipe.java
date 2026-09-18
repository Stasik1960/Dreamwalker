package daot;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public class CannedHerringOpeningRecipe extends SpecialCraftingRecipe {
   public CannedHerringOpeningRecipe(net.minecraft.util.Identifier id, CraftingRecipeCategory category) {
      super(id, category);
   }

   public boolean matches(RecipeInputInventory input, World level) {
      int herring = 0;
      int shears = 0;

      for (int i = 0; i < input.size(); i++) {
         ItemStack s = input.getStack(i);
         if (!s.isEmpty()) {
            if (s.isOf(DannysAot.CANNED_HERRING)) {
               herring++;
            } else {
               if (!s.isOf(Items.SHEARS)) {
                  return false;
               }

               shears++;
            }
         }
      }

      return herring == 1 && shears == 1;
   }

   public ItemStack craft(RecipeInputInventory input, DynamicRegistryManager registries) {
      return new ItemStack(DannysAot.CANNED_HERRING_OPEN);
   }

   public DefaultedList<ItemStack> getRemainder(RecipeInputInventory input) {
      DefaultedList<ItemStack> remaining = DefaultedList.ofSize(input.size(), ItemStack.EMPTY);

      for (int i = 0; i < input.size(); i++) {
         ItemStack s = input.getStack(i);
         if (s.isOf(Items.SHEARS)) {
            remaining.set(i, s.copy());
         }
      }

      return remaining;
   }

   @Override
   public boolean fits(int width, int height) {
      return width * height >= 2;
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return DannysAot.CANNED_HERRING_OPENING_SERIALIZER;
   }
}
