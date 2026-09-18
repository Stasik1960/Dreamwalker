package daot;

import java.util.List;
import java.util.function.Consumer;
import daot.compat.components.DataComponentTypes;
import daot.compat.components.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ArmorItem.Type;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ODMGearItem extends daot.compat.BackportArmorItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static final int MAX_GAS = 500;
   private static final String GAS_KEY = "Gas";
   public static Consumer<Consumer<Object>> clientRendererConsumer;

   public ODMGearItem(ArmorMaterial material, Type type, Settings properties) {
      super(material, type, properties);
   }

   public static int getGas(ItemStack stack) {
      NbtComponent customData = daot.compat.components.Components.get(stack, DataComponentTypes.CUSTOM_DATA);
      if (customData != null) {
         NbtCompound tag = customData.copyNbt();
         if (tag.contains("Gas")) {
            return tag.getInt("Gas");
         }
      }

      setGas(stack, 500);
      return 500;
   }

   public static void setGas(ItemStack stack, int amount) {
      int clampedAmount = Math.max(0, Math.min(500, amount));
      daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> data.apply(tag -> tag.putInt("Gas", clampedAmount)));
   }

   public static boolean consumeGas(ItemStack stack, int amount, PlayerEntity player) {
      if (player.isCreative()) {
         return true;
      } else {
         int current = getGas(stack);
         if (current <= 0) {
            return false;
         } else {
            setGas(stack, current - amount);
            return true;
         }
      }
   }

   public static boolean hasGas(ItemStack stack, PlayerEntity player) {
      return player.isCreative() ? true : getGas(stack) > 0;
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         try {
            clientRendererConsumer.accept(consumer);
         } catch (Exception var3) {
         }
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController(this, 20, state -> PlayState.CONTINUE));
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public boolean shouldPlayAnimsWhileGamePaused() {
      return false;
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      int gas = getGas(stack);
      Formatting gasColor = gas > 250 ? Formatting.AQUA : (gas > 100 ? Formatting.YELLOW : Formatting.RED);
      tooltip.add(Text.literal("Gas: " + gas + "/500").formatted(gasColor));
      tooltip.add(Text.literal("Equip blades in main & offhand to use").formatted(Formatting.RED));
      tooltip.add(Text.literal("Hold SPACE to boost").formatted(Formatting.WHITE));
      super.appendTooltip(stack, context, tooltip, type);
   }
}
