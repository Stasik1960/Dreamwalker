package daot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.block.BlockState;
import daot.compat.components.DataComponentTypes;
import daot.compat.components.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.util.GeckoLibUtil;

public class BladeItem extends SwordItem implements GeoItem {
   private final java.util.function.Supplier<Object> renderProvider = GeoItem.makeRenderer(this);
   @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public static Consumer<Consumer<Object>> clientRendererConsumer;
   public static Supplier<String> thunderSpearKeyNameSupplier = () -> "V";
   public static final int DAMAGE_PER_LEVEL = 10;
   public static final int MAX_DURABILITY = 40;
   private static final Map<UUID, Float> ogreBladeWearAccumulator = new HashMap<>();
   private static final String BLADE_DAMAGE_KEY = "BladeDamage";
   private static final String BLADE_STATE_KEY = "BladeState";
   private static final String EJECT_ANIMATION_KEY = "EjectAnimation";
   private static final String VISUAL_STATE_KEY = "VisualState";
   private static final String EJECT_START_TIME_KEY = "EjectStartTime";
   private static final int EJECT_ANIMATION_DURATION = 18;
   private static final String THUNDER_SPEAR_KEY = "ThunderSpear";

   private static int adjustWearForOgre(LivingEntity entity, int amount) {
      if (amount > 0 && entity instanceof PlayerEntity) {
         float divisor;
         if (entity.getCommandTags().contains("titan_bloodline")) {
            divisor = 12.0F;
         } else {
            if (!entity.getCommandTags().contains("ogre_shifter")) {
               return amount;
            }

            divisor = 6.0F;
         }

         UUID id = entity.getUuid();
         float acc = ogreBladeWearAccumulator.getOrDefault(id, 0.0F) + amount / divisor;
         int whole = (int)acc;
         ogreBladeWearAccumulator.put(id, acc - whole);
         return whole;
      } else {
         return amount;
      }
   }

   public BladeItem() {
      super(
         ToolMaterials.IRON,
         3, -2.4F, new Settings().maxDamage(1)
      );
      SingletonGeoAnimatable.registerSyncedAnimatable(this);
   }

   public static BladeItem.BladeState getBladeState(ItemStack stack) {
      if (stack.getItem() instanceof BladeItem) {
         int stateLevel = daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt("BladeState");
         return BladeItem.BladeState.fromLevel(stateLevel);
      } else {
         return BladeItem.BladeState.EMPTY;
      }
   }

   public static void updateEjectAnimationState(ItemStack stack, long currentGameTime) {
      if (stack.getItem() instanceof BladeItem) {
         NbtCompound tag = daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
         if (tag.getBoolean("EjectAnimation")) {
            long startTime = tag.getLong("EjectStartTime");
            if (currentGameTime - startTime >= 18L) {
               daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
                  NbtCompound updateTag = data.copyNbt();
                  updateTag.putBoolean("EjectAnimation", false);
                  return NbtComponent.of(updateTag);
               });
            }
         }
      }
   }

   public static BladeItem.BladeState getVisualBladeState(ItemStack stack) {
      if (stack.getItem() instanceof BladeItem) {
         NbtCompound tag = daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
         if (tag.getBoolean("EjectAnimation")) {
            int visualLevel = tag.getInt("VisualState");
            return BladeItem.BladeState.fromLevel(visualLevel);
         } else {
            int stateLevel = tag.getInt("BladeState");
            return BladeItem.BladeState.fromLevel(stateLevel);
         }
      } else {
         return BladeItem.BladeState.EMPTY;
      }
   }

   public static void setBladeState(ItemStack stack, BladeItem.BladeState state) {
      if (stack.getItem() instanceof BladeItem) {
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putInt("BladeState", state.getLevel());
            return NbtComponent.of(tag);
         });
      }
   }

   public static int getBladeDamage(ItemStack stack) {
      return stack.getItem() instanceof BladeItem
         ? daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt("BladeDamage")
         : 0;
   }

   public static void setBladeDamage(ItemStack stack, int damage) {
      if (stack.getItem() instanceof BladeItem) {
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putInt("BladeDamage", damage);
            return NbtComponent.of(tag);
         });
      }
   }

   public static boolean shouldEject(ItemStack stack) {
      return stack.getItem() instanceof BladeItem
         ? daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getBoolean("EjectAnimation")
         : false;
   }

   public static void setEjectAnimation(ItemStack stack, boolean eject) {
      if (stack.getItem() instanceof BladeItem) {
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putBoolean("EjectAnimation", eject);
            return NbtComponent.of(tag);
         });
      }
   }

   public static boolean hasThunderSpear(ItemStack stack) {
      return stack.getItem() instanceof BladeItem
         ? daot.compat.components.Components.getOrDefault(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getBoolean("ThunderSpear")
         : false;
   }

   public static void setThunderSpear(ItemStack stack, boolean loaded) {
      if (stack.getItem() instanceof BladeItem) {
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putBoolean("ThunderSpear", loaded);
            return NbtComponent.of(tag);
         });
      }
   }

   public static void damageBladeByAmount(ItemStack stack, int amount, LivingEntity entity) {
      if (stack.getItem() instanceof BladeItem bladeItem) {
         BladeItem.BladeState state = getBladeState(stack);
         if (state != BladeItem.BladeState.EMPTY) {
            amount = adjustWearForOgre(entity, amount);
            if (amount > 0) {
               int currentDamage = getBladeDamage(stack);
               int newDamage = Math.min(currentDamage + amount, 40);
               setBladeDamage(stack, newDamage);
               boolean stateChanged = bladeItem.updateBladeState(stack, newDamage, entity);
               if (stateChanged && entity instanceof PlayerEntity player) {
                  float pitch = 1.5F + player.getRandom().nextFloat() * 0.1F;
                  player.getWorld()
                     .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
               }

               if (entity instanceof PlayerEntity player) {
                  bladeItem.syncBladeDurability(player, stack);
               }
            }
         }
      }
   }

   public static void reloadBlade(ItemStack stack) {
      if (stack.getItem() instanceof BladeItem) {
         setBladeState(stack, BladeItem.BladeState.FRESH);
         setBladeDamage(stack, 0);
         setEjectAnimation(stack, false);
      }
   }

   public static void ejectBlade(ItemStack stack, PlayerEntity player, Hand hand) {
      if (stack.getItem() instanceof BladeItem bladeItem) {
         BladeItem.BladeState currentState = getBladeState(stack);
         long gameTime = player.getWorld().getTime();
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putInt("VisualState", currentState.getLevel());
            tag.putBoolean("EjectAnimation", true);
            tag.putLong("EjectStartTime", gameTime);
            tag.putInt("BladeState", BladeItem.BladeState.EMPTY.getLevel());
            tag.putInt("BladeDamage", 0);
            return NbtComponent.of(tag);
         });
         if (player.getWorld() instanceof ServerWorld serverLevel) {
            String animName = hand == Hand.MAIN_HAND ? "ejectright" : "ejectleft";
            bladeItem.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", animName);
         }
      }
   }

   public static void initializeEmpty(ItemStack stack) {
      if (stack.getItem() instanceof BladeItem && getBladeState(stack) == BladeItem.BladeState.EMPTY && getBladeDamage(stack) == 0) {
         setBladeState(stack, BladeItem.BladeState.EMPTY);
      }
   }

   @Override
   public Text getName(ItemStack stack) {
      BladeItem.BladeState state = getBladeState(stack);
      return state == BladeItem.BladeState.EMPTY ? Text.literal("ODM Grip") : Text.literal("Ultrahard Steel Blade");
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      BladeItem.BladeState state = getBladeState(stack);
      if (state != BladeItem.BladeState.EMPTY) {
         tooltip.add(Text.literal("Strong enough to pierce the flesh of a Titan").formatted(Formatting.DARK_GRAY));
         int damage = getBladeDamage(stack);
         int remaining = 40 - damage;
         tooltip.add(Text.literal("Durability: " + remaining + "/40").formatted(Formatting.GRAY));
         tooltip.add(Text.literal("Press R to eject blade").formatted(Formatting.YELLOW));
      } else {
         tooltip.add(Text.literal("Press R to reload blade").formatted(Formatting.YELLOW));
      }

      boolean hasSpear = hasThunderSpear(stack);
      String spearCount = hasSpear ? "1/1" : "0/1";
      tooltip.add(Text.literal("Thunder Spears: " + spearCount).formatted(Formatting.GRAY, Formatting.BOLD));
      String keyName = thunderSpearKeyNameSupplier.get();
      if (hasSpear) {
         tooltip.add(Text.literal("Press [" + keyName + "] to unload Thunder Spear").formatted(Formatting.DARK_GRAY));
      } else {
         tooltip.add(Text.literal("Press [" + keyName + "] to load Thunder Spear").formatted(Formatting.DARK_GRAY));
      }

      super.appendTooltip(stack, context, tooltip, type);
   }

   @Override
   public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
      BladeItem.BladeState state = getBladeState(stack);
      if (state == BladeItem.BladeState.EMPTY) {
         return false;
      } else {
         boolean trainingDummy = target instanceof TitanDummyEntity || target instanceof TitanDummyNapeEntity || target instanceof TitanDummyEyeEntity;
         int wear = trainingDummy ? 0 : adjustWearForOgre(attacker, 1);
         if (wear > 0) {
            int currentDamage = getBladeDamage(stack);
            currentDamage = Math.min(currentDamage + wear, 40);
            setBladeDamage(stack, currentDamage);
            boolean stateChanged = this.updateBladeState(stack, currentDamage, attacker);
            if (stateChanged && attacker instanceof PlayerEntity player) {
               float pitch = 1.5F + player.getRandom().nextFloat() * 0.1F;
               player.getWorld()
                  .playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
            }

            if (attacker instanceof PlayerEntity player) {
               this.syncBladeDurability(player, stack);
            }
         }

         return super.postHit(stack, target, attacker);
      }
   }


   @Override
   public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
      ItemStack stack = miner.getMainHandStack();
      return stack.getItem() == this && getBladeState(stack) == BladeItem.BladeState.EMPTY ? false : super.canMine(state, world, pos, miner);
   }

   private boolean updateBladeState(ItemStack stack, int damage, LivingEntity entity) {
      BladeItem.BladeState currentState = getBladeState(stack);
      BladeItem.BladeState newState = currentState;
      if (damage >= 40) {
         long gameTime = entity.getWorld().getTime();
         daot.compat.components.Components.apply(stack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
            NbtCompound tag = data.copyNbt();
            tag.putInt("VisualState", currentState.getLevel());
            tag.putBoolean("EjectAnimation", true);
            tag.putLong("EjectStartTime", gameTime);
            tag.putInt("BladeState", BladeItem.BladeState.EMPTY.getLevel());
            tag.putInt("BladeDamage", 0);
            return NbtComponent.of(tag);
         });
         if (entity instanceof PlayerEntity player && player.getWorld() instanceof ServerWorld serverLevel) {
            Hand hand = player.getStackInHand(Hand.MAIN_HAND) == stack ? Hand.MAIN_HAND : Hand.OFF_HAND;
            String animName = hand == Hand.MAIN_HAND ? "ejectright" : "ejectleft";
            this.triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", animName);
         }

         return true;
      } else {
         if (damage >= 30 && currentState != BladeItem.BladeState.CHIPPED_3) {
            newState = BladeItem.BladeState.CHIPPED_3;
         } else if (damage >= 20 && currentState != BladeItem.BladeState.CHIPPED_2 && currentState != BladeItem.BladeState.CHIPPED_3) {
            newState = BladeItem.BladeState.CHIPPED_2;
         } else if (damage >= 10 && currentState == BladeItem.BladeState.FRESH) {
            newState = BladeItem.BladeState.CHIPPED_1;
         }

         if (newState != currentState) {
            setBladeState(stack, newState);
            return true;
         } else {
            return false;
         }
      }
   }

   private void syncBladeDurability(PlayerEntity player, ItemStack attackingStack) {
      ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
      ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);
      ItemStack otherHand = null;
      if (mainHand == attackingStack && offHand.getItem() instanceof BladeItem) {
         otherHand = offHand;
      } else if (offHand == attackingStack && mainHand.getItem() instanceof BladeItem) {
         otherHand = mainHand;
      }

      if (otherHand != null) {
         int newDamage = getBladeDamage(attackingStack);
         BladeItem.BladeState newState = getBladeState(attackingStack);
         boolean newEject = shouldEject(attackingStack);
         int currentDamage = getBladeDamage(otherHand);
         BladeItem.BladeState currentState = getBladeState(otherHand);
         boolean currentEject = shouldEject(otherHand);
         if (newDamage != currentDamage) {
            setBladeDamage(otherHand, newDamage);
         }

         if (newState != currentState) {
            setBladeState(otherHand, newState);
         }

         if (newEject != currentEject) {
            if (newEject && !currentEject) {
               NbtCompound attackingTag = daot.compat.components.Components.getOrDefault(attackingStack, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
               int visualState = attackingTag.getInt("VisualState");
               long ejectStartTime = attackingTag.getLong("EjectStartTime");
               daot.compat.components.Components.apply(otherHand, DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT, data -> {
                  NbtCompound tag = data.copyNbt();
                  tag.putBoolean("EjectAnimation", true);
                  tag.putInt("VisualState", visualState);
                  tag.putLong("EjectStartTime", ejectStartTime);
                  return NbtComponent.of(tag);
               });
               if (player.getWorld() instanceof ServerWorld serverLevel) {
                  Hand otherHandType = otherHand == mainHand ? Hand.MAIN_HAND : Hand.OFF_HAND;
                  String animName = otherHandType == Hand.MAIN_HAND ? "ejectright" : "ejectleft";
                  this.triggerAnim(player, GeoItem.getOrAssignId(otherHand, serverLevel), "controller", animName);
               }
            } else {
               setEjectAnimation(otherHand, newEject);
            }
         }
      }
   }

   public void createRenderer(Consumer consumer) {
      if (clientRendererConsumer != null) {
         clientRendererConsumer.accept(consumer);
      }
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(
         new AnimationController(this, "controller", 0, state -> PlayState.STOP)
            .triggerableAnim("ejectright", RawAnimation.begin().thenPlay("animation.blade.ejectright"))
            .triggerableAnim("ejectleft", RawAnimation.begin().thenPlay("animation.blade.ejectleft"))
      );
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public static enum BladeState {
      EMPTY(0),
      FRESH(1),
      CHIPPED_1(2),
      CHIPPED_2(3),
      CHIPPED_3(4);

      private final int level;

      private BladeState(int level) {
         this.level = level;
      }

      public int getLevel() {
         return this.level;
      }

      public static BladeItem.BladeState fromLevel(int level) {
         for (BladeItem.BladeState state : values()) {
            if (state.level == level) {
               return state;
            }
         }

         return EMPTY;
      }
   }
}
