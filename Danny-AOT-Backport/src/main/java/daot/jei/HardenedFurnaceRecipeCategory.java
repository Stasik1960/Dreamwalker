package daot.jei;

import daot.DannysAot;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.drawable.IDrawableAnimated.StartDirection;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class HardenedFurnaceRecipeCategory implements IRecipeCategory<HardenedFurnaceRecipe> {
   public static final Identifier UID = new Identifier("dannys-aot", "hardened_furnace");
   public static final RecipeType<HardenedFurnaceRecipe> RECIPE_TYPE = RecipeType.create("dannys-aot", "hardened_furnace", HardenedFurnaceRecipe.class);
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/gui/iceburst_furnace.png");
   private final IDrawable background;
   private final IDrawable icon;
   private final IDrawableAnimated arrow;
   private final IDrawableAnimated flame;

   public HardenedFurnaceRecipeCategory(IGuiHelper guiHelper) {
      this.background = guiHelper.createDrawable(TEXTURE, 0, 0, 176, 85);
      this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(DannysAot.ICEBURST_FURNACE));
      IDrawableStatic arrowStatic = guiHelper.createDrawable(TEXTURE, 176, 14, 24, 17);
      this.arrow = guiHelper.createAnimatedDrawable(arrowStatic, 200, StartDirection.LEFT, false);
      IDrawableStatic flameStatic = guiHelper.createDrawable(TEXTURE, 176, 0, 14, 14);
      this.flame = guiHelper.createAnimatedDrawable(flameStatic, 300, StartDirection.TOP, true);
   }

   public RecipeType<HardenedFurnaceRecipe> getRecipeType() {
      return RECIPE_TYPE;
   }

   public Text getTitle() {
      return Text.translatable("block.dannys-aot.iceburst_furnace");
   }

   public IDrawable getBackground() {
      return this.background;
   }

   public IDrawable getIcon() {
      return this.icon;
   }

   public void setRecipe(IRecipeLayoutBuilder builder, HardenedFurnaceRecipe recipe, IFocusGroup focuses) {
      List<ItemStack> inputs = recipe.getInputs();
      builder.addSlot(RecipeIngredientRole.INPUT, 44, 17).addItemStack(inputs.get(0));
      builder.addSlot(RecipeIngredientRole.INPUT, 68, 17).addItemStack(inputs.get(1));
      builder.addSlot(RecipeIngredientRole.OUTPUT, 116, 35).addItemStack(recipe.output());
   }

   public void draw(HardenedFurnaceRecipe recipe, IRecipeSlotsView recipeSlotsView, DrawContext guiGraphics, double mouseX, double mouseY) {
      this.arrow.draw(guiGraphics, 89, 18);
      this.flame.draw(guiGraphics, 57, 47);
   }
}
