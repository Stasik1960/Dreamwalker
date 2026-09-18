package daot;

import java.util.List;
import java.util.function.Consumer;
import daot.compat.components.DataComponentTypes;
import daot.compat.components.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class GasCanisterItem extends Item implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   public static final int MAX_GAS = 500;
   private static final String GAS_KEY = "CanisterGas";
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;

   public GasCanisterItem(Settings properties) {
      super(properties);
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   public static int getGas(ItemStack stack) {
      NbtComponent customData = daot.compat.components.Components.get(stack, DataComponentTypes.CUSTOM_DATA);
      if (customData != null) {
         NbtCompound tag = customData.copyNbt();
         if (tag.contains("CanisterGas")) {
            return tag.getInt("CanisterGas");
         }
      }

      return 0;
   }

   public static void setGas(ItemStack stack, int amount) {
      int clamped = Math.max(0, Math.min(500, amount));
      daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> data.apply(tag -> tag.putInt("CanisterGas", clamped)));
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      if (user.isSneaking()) {
         ItemStack leggings = user.getEquippedStack(EquipmentSlot.LEGS);
         if (DannysAot.isODMGear(leggings.getItem())) {
            int canisterGas = getGas(stack);
            if (canisterGas <= 0) {
               return TypedActionResult.pass(stack);
            }

            int odmGas = DannysAot.getGasFromGear(leggings);
            int missing = DannysAot.getMaxGasForGear(leggings) - odmGas;
            if (missing <= 0) {
               return TypedActionResult.pass(stack);
            }

            int transfer = Math.min(canisterGas, missing);
            DannysAot.setGasOnGear(leggings, odmGas + transfer);
            setGas(stack, canisterGas - transfer);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 1.0F, 2.0F);
            return TypedActionResult.consume(stack);
         }
      } else {
         if (RealisticResourceUseTracker.isEnabled(world)) {
            if (!world.isClient()) {
               user.sendMessage(Text.literal("Refuel canisters in the hardened furnace.").formatted(Formatting.RED), true);
            }

            return TypedActionResult.fail(stack);
         }

         int currentGas = getGas(stack);
         if (currentGas >= 500) {
            if (!world.isClient()) {
               user.sendMessage(Text.literal("Gas Canister is full!").formatted(Formatting.RED), true);
            }

            return TypedActionResult.fail(stack);
         }

         for (int i = 0; i < user.getInventory().size(); i++) {
            ItemStack invStack = user.getInventory().getStack(i);
            if (invStack.getItem() == DannysAot.ICE_BURST_CLUSTER) {
               if (!user.isCreative()) {
                  invStack.decrement(1);
               }

               setGas(stack, Math.min(currentGas + 25, 500));
               world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_BUCKET_FILL_LAVA, SoundCategory.PLAYERS, 1.0F, 2.0F);
               return TypedActionResult.consume(stack);
            }
         }
      }

      return TypedActionResult.pass(stack);
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   public boolean isItemBarVisible(ItemStack stack) {
      return getGas(stack) > 0;
   }

   @Override
   public int getItemBarStep(ItemStack stack) {
      return Math.round(getGas(stack) * 13.0F / 500.0F);
   }

   @Override
   public int getItemBarColor(ItemStack stack) {
      float ratio = getGas(stack) / 500.0F;
      int r = (int)(255.0F * (1.0F - ratio));
      int g = (int)(255.0F * ratio);
      return r << 16 | g << 8;
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      int gas = getGas(stack);
      Formatting gasColor = gas > 250 ? Formatting.AQUA : (gas > 100 ? Formatting.YELLOW : Formatting.RED);
      tooltip.add(Text.literal("Gas: " + gas + "/500").formatted(gasColor));
      tooltip.add(Text.literal("Right click to fill from Ice Burst Clusters").formatted(Formatting.DARK_GRAY));
      tooltip.add(Text.literal("Sneak + Right click to fill ODM Gear").formatted(Formatting.DARK_GRAY));
      super.appendTooltip(stack, context, tooltip, type);
   }
}
