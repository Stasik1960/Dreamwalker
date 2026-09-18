package daot;

import daot.network.ModNetworking;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import daot.compat.components.DataComponentTypes;
import daot.compat.components.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.Animation.LoopType;
import software.bernie.geckolib.util.GeckoLibUtil;

public class FlareGunItem extends Item implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private static final String COLOR_KEY = "FlareColor";
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<RenderProvider>> clientRendererConsumer;
   public static Supplier<String> loadKeyNameSupplier = () -> "X";
   public static Supplier<String> cycleKeyNameSupplier = () -> "C";

   public FlareGunItem(Settings properties) {
      super(properties);
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   public static int getLoadedColorOrdinal(ItemStack stack) {
      NbtComponent customData = daot.compat.components.Components.get(stack, DataComponentTypes.CUSTOM_DATA);
      if (customData != null) {
         NbtCompound tag = customData.copyNbt();
         if (tag.contains("FlareColor")) {
            return tag.getInt("FlareColor");
         }
      }

      return -1;
   }

   public static void setLoadedColorOrdinal(ItemStack stack, int colorOrdinal) {
      daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> data.apply(tag -> tag.putInt("FlareColor", colorOrdinal)));
   }

   public static boolean isLoaded(ItemStack stack) {
      return getLoadedColorOrdinal(stack) >= 0;
   }

   public static FlareCartridgeItem.FlareColor getLoadedColor(ItemStack stack) {
      int ord = getLoadedColorOrdinal(stack);
      return ord >= 0 && ord < FlareCartridgeItem.FlareColor.values().length ? FlareCartridgeItem.FlareColor.values()[ord] : null;
   }

   public static void clearLoaded(ItemStack stack) {
      daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> data.apply(tag -> tag.putInt("FlareColor", -1)));
   }

   @Override
   public UseAction getUseAction(ItemStack stack) {
      return UseAction.NONE;
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      if (!isLoaded(stack)) {
         return TypedActionResult.pass(stack);
      } else if (world.isClient()) {
         return TypedActionResult.consume(stack);
      } else if (user instanceof ServerPlayerEntity sp && ModNetworking.isFlareGunOnCooldown(sp)) {
         return TypedActionResult.pass(stack);
      } else {
         int colorOrd = getLoadedColorOrdinal(stack);
         FlareProjectileEntity flare = new FlareProjectileEntity(world, user, colorOrd);
         Vec3d lookDir = user.getRotationVector();
         flare.setVelocity(lookDir.x, lookDir.y, lookDir.z, 2.0F, 1.0F);
         world.spawnEntity(flare);
         world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.FLARE_SHOOT, SoundCategory.PLAYERS, 0.8F, 1.0F);
         clearLoaded(stack);
         if (user instanceof ServerPlayerEntity sp) {
            ModNetworking.setFlareGunCooldown(sp, 15);
         }

         user.getItemCooldownManager().set(this, 15);
         return TypedActionResult.consume(stack);
      }
   }

   public static int findNearestCartridgeSlot(PlayerEntity player, int gunSlot) {
      if (gunSlot == 40) {
         for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() instanceof FlareCartridgeItem) {
               return i;
            }
         }
      } else {
         for (int offset = 1; offset <= 8; offset++) {
            int rightSlot = gunSlot + offset;
            int leftSlot = gunSlot - offset;
            if (rightSlot >= 0 && rightSlot < 9) {
               ItemStack s = player.getInventory().getStack(rightSlot);
               if (s.getItem() instanceof FlareCartridgeItem) {
                  return rightSlot;
               }
            }

            if (leftSlot >= 0 && leftSlot < 9) {
               ItemStack s = player.getInventory().getStack(leftSlot);
               if (s.getItem() instanceof FlareCartridgeItem) {
                  return leftSlot;
               }
            }
         }
      }

      if (gunSlot != 40 && player.getOffHandStack().getItem() instanceof FlareCartridgeItem) {
         return 40;
      } else {
         for (int ix = 9; ix < 36; ix++) {
            ItemStack s = player.getInventory().getStack(ix);
            if (s.getItem() instanceof FlareCartridgeItem) {
               return ix;
            }
         }

         return -1;
      }
   }

   public static int findCartridgeSlotOfColor(PlayerEntity player, int gunSlot, FlareCartridgeItem.FlareColor color) {
      if (gunSlot == 40) {
         for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() instanceof FlareCartridgeItem fc && fc.getColor() == color) {
               return i;
            }
         }
      } else {
         for (int offset = 0; offset <= 8; offset++) {
            int rightSlot = gunSlot + offset;
            int leftSlot = gunSlot - offset;
            if (rightSlot >= 0 && rightSlot < 9) {
               ItemStack s = player.getInventory().getStack(rightSlot);
               if (s.getItem() instanceof FlareCartridgeItem fc && fc.getColor() == color) {
                  return rightSlot;
               }
            }

            if (offset > 0 && leftSlot >= 0 && leftSlot < 9) {
               ItemStack s = player.getInventory().getStack(leftSlot);
               if (s.getItem() instanceof FlareCartridgeItem fc && fc.getColor() == color) {
                  return leftSlot;
               }
            }
         }
      }

      if (player.getOffHandStack().getItem() instanceof FlareCartridgeItem fc && fc.getColor() == color) {
         return 40;
      } else {
         for (int ix = 9; ix < 36; ix++) {
            ItemStack s = player.getInventory().getStack(ix);
            if (s.getItem() instanceof FlareCartridgeItem fc && fc.getColor() == color) {
               return ix;
            }
         }

         return -1;
      }
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      FlareCartridgeItem.FlareColor loaded = getLoadedColor(stack);
      if (loaded != null) {
         Formatting colorFmt = getColorFormatting(loaded);
         String name = loaded.name().charAt(0) + loaded.name().substring(1).toLowerCase();
         tooltip.add(Text.literal("Loaded: ").formatted(Formatting.GRAY).append(Text.literal(name).formatted(colorFmt, Formatting.BOLD)));
      } else {
         tooltip.add(Text.literal("Empty").formatted(Formatting.DARK_GRAY));
      }

      tooltip.add(Text.literal("Press [" + loadKeyNameSupplier.get() + "] to load Cartridge").formatted(Formatting.DARK_GRAY));
      tooltip.add(Text.literal("Press [" + cycleKeyNameSupplier.get() + "] to swap Cartridge").formatted(Formatting.DARK_GRAY));
      super.appendTooltip(stack, context, tooltip, type);
   }

   public static Formatting getColorFormatting(FlareCartridgeItem.FlareColor color) {
      return switch (color) {
         case RED -> Formatting.RED;
         case BLACK -> Formatting.DARK_GRAY;
         case PURPLE -> Formatting.LIGHT_PURPLE;
         case BLUE -> Formatting.BLUE;
         case GREEN -> Formatting.GREEN;
         case YELLOW -> Formatting.YELLOW;
      };
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(
         new AnimationController(this, "flare_gun_controller", 5, state -> PlayState.STOP)
            .triggerableAnim("load_cartridge", RawAnimation.begin().then("load_cartridge", LoopType.PLAY_ONCE))
            .triggerableAnim("discard_cartridge", RawAnimation.begin().then("discard_cartridge", LoopType.PLAY_ONCE))
            .triggerableAnim("swap_cartridge", RawAnimation.begin().then("swap_cartridge", LoopType.PLAY_ONCE))
      );
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }
}
