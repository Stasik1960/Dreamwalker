package daot.jei;

import daot.DannysAot;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
@JeiPlugin
public class DannysAotJeiPlugin implements IModPlugin {
   private static final Identifier PLUGIN_UID = new Identifier("dannys-aot", "jei_plugin");

   public Identifier getPluginUid() {
      return PLUGIN_UID;
   }

   public void registerCategories(IRecipeCategoryRegistration registration) {
      registration.addRecipeCategories(new IRecipeCategory[]{new HardenedFurnaceRecipeCategory(registration.getJeiHelpers().getGuiHelper())});
   }

   public void registerRecipes(IRecipeRegistration registration) {
      List<HardenedFurnaceRecipe> recipes = new ArrayList<>();
      recipes.add(
         new HardenedFurnaceRecipe(
            new ItemStack(DannysAot.IRON_BAMBOO_ITEM), 2, new ItemStack(Items.RAW_IRON), 2, new ItemStack(DannysAot.HARDENED_IRON_BAMBOO)
         )
      );
      recipes.add(
         new HardenedFurnaceRecipe(
            new ItemStack(DannysAot.HARDENED_IRON_BAMBOO), 1, new ItemStack(Items.IRON_INGOT), 1, new ItemStack(DannysAot.ULTRAHARD_STEEL_INGOT)
         )
      );
      registration.addRecipes(HardenedFurnaceRecipeCategory.RECIPE_TYPE, recipes);
   }

   public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
      registration.addRecipeCatalyst(new ItemStack(DannysAot.ICEBURST_FURNACE), new RecipeType[]{HardenedFurnaceRecipeCategory.RECIPE_TYPE});
   }
}
