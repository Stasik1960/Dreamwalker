package daot;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.world.World;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class EmptySyringeItem extends Item {
   private static final Map<UUID, UUID> TARGET_TITANS = new ConcurrentHashMap<>();
   private static final Map<UUID, UUID> TARGET_POWERED_VILLAGERS = new ConcurrentHashMap<>();
   private static final Map<UUID, Boolean> SELF_EXTRACTING = new ConcurrentHashMap<>();

   public EmptySyringeItem(Settings properties) {
      super(properties);
   }

   public static void setTargetTitan(PlayerEntity player, Entity titan) {
      TARGET_TITANS.put(player.getUuid(), titan.getUuid());
   }

   public static boolean hasTargetTitan(PlayerEntity player) {
      return TARGET_TITANS.containsKey(player.getUuid());
   }

   public static UUID getAndRemoveTargetTitan(PlayerEntity player) {
      return TARGET_TITANS.remove(player.getUuid());
   }

   public static void removeTargetTitan(PlayerEntity player) {
      TARGET_TITANS.remove(player.getUuid());
   }

   public static void setTargetPoweredVillager(PlayerEntity player, VillagerEntity villager) {
      TARGET_POWERED_VILLAGERS.put(player.getUuid(), villager.getUuid());
   }

   public static boolean hasTargetPoweredVillager(PlayerEntity player) {
      return TARGET_POWERED_VILLAGERS.containsKey(player.getUuid());
   }

   public static UUID getAndRemoveTargetPoweredVillager(PlayerEntity player) {
      return TARGET_POWERED_VILLAGERS.remove(player.getUuid());
   }

   public static void removeTargetPoweredVillager(PlayerEntity player) {
      TARGET_POWERED_VILLAGERS.remove(player.getUuid());
   }

   public static void setSelfExtracting(PlayerEntity player, boolean value) {
      if (value) {
         SELF_EXTRACTING.put(player.getUuid(), true);
      } else {
         SELF_EXTRACTING.remove(player.getUuid());
      }
   }

   public static boolean isSelfExtracting(PlayerEntity player) {
      return SELF_EXTRACTING.getOrDefault(player.getUuid(), false);
   }

   @Override
   public int getMaxUseTime(ItemStack stack) {
      return 20;
   }

   @Override
   public UseAction getUseAction(ItemStack stack) {
      return UseAction.BOW;
   }

   @Override
   public Text getName(ItemStack stack) {
      return Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.WHITE).formatted(Formatting.ITALIC);
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      super.appendTooltip(stack, context, tooltip, type);
      tooltip.add(Text.literal("Filled with:").formatted(Formatting.GRAY));
      tooltip.add(Text.literal("EMPTY").formatted(Formatting.DARK_RED));
      tooltip.add(Text.literal("Sneak + right click to extract").formatted(Formatting.DARK_GRAY));
      tooltip.add(Text.literal("Right click on a Pure Titan to extract").formatted(Formatting.DARK_GRAY));
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      if (!hasTargetTitan(user) && !hasTargetPoweredVillager(user)) {
         if (user.isUsingItem() && user.getActiveItem().getItem() == this) {
            return TypedActionResult.pass(stack);
         } else if (!user.isSneaking()) {
            return TypedActionResult.pass(stack);
         } else {
            setSelfExtracting(user, true);
            if (!world.isClient()) {
               boolean hasColossal = user.getCommandTags().contains("colossal");
               boolean hasAttack = user.getCommandTags().contains("attack");
               boolean hasArmored = user.getCommandTags().contains("armored");
               boolean hasBeast = user.getCommandTags().contains("beast");
               boolean hasFemale = user.getCommandTags().contains("female");
               boolean hasWarhammer = user.getCommandTags().contains("warhammer");
               boolean isRoyal = false;
               if (user instanceof ServerPlayerEntity sp) {
                  isRoyal = BloodlineData.get(sp.getServerWorld()).getBloodline(sp.getUuid()) == BloodlineType.ROYAL;
               }

               if (!hasColossal && !hasAttack && !hasArmored && !hasBeast && !hasFemale && !hasWarhammer && !isRoyal) {
                  setSelfExtracting(user, false);
                  if (user instanceof ServerPlayerEntity serverPlayer) {
                     serverPlayer.sendMessage(Text.literal("Nothing to extract"), true);
                  }

                  return TypedActionResult.fail(stack);
               }
            }

            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
         }
      } else {
         setSelfExtracting(user, false);
         user.setCurrentHand(hand);
         return TypedActionResult.consume(stack);
      }
   }

   @Override
   public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
      if (user instanceof PlayerEntity player) {
         setSelfExtracting(player, false);
         if (world.isClient()) {
            return stack;
         } else {
            ServerWorld serverLevel = (ServerWorld)world;
            UUID titanUUID = getAndRemoveTargetTitan(player);
            if (titanUUID != null) {
               return this.handleTitanExtraction(stack, serverLevel, player, titanUUID);
            } else {
               UUID villagerUUID = getAndRemoveTargetPoweredVillager(player);
               return villagerUUID != null
                  ? this.handlePoweredVillagerExtraction(stack, serverLevel, player, villagerUUID)
                  : this.handleSelfExtraction(stack, serverLevel, player);
            }
         }
      } else {
         return stack;
      }
   }

   private ItemStack handleTitanExtraction(ItemStack stack, ServerWorld level, PlayerEntity player, UUID titanUUID) {
      Entity titanEntity = level.getEntity(titanUUID);
      if (titanEntity == null) {
         return stack;
      } else {
         boolean isPureTitan = titanEntity instanceof SmallTitanEntity
            || titanEntity instanceof SmallTitan2Entity
            || titanEntity instanceof FritzTitanEntity
            || titanEntity instanceof TitanEntity;
         if (!isPureTitan) {
            return stack;
         } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
            ItemStack filledSyringe = new ItemStack(DannysAot.SYRINGE);
            SpinalFluidData fluidData = new SpinalFluidData("Pure Titan");
            daot.compat.components.Components.set(filledSyringe, DannysAot.SPINAL_FLUID_DATA, fluidData);
            DannysAot.LOGGER.info("Player {} extracted spinal fluid from a pure titan", player.getName().getString());
            return this.replaceWithFilledSyringe(stack, player, filledSyringe);
         }
      }
   }

   private ItemStack handlePoweredVillagerExtraction(ItemStack stack, ServerWorld level, PlayerEntity player, UUID villagerUUID) {
      if (level.getEntity(villagerUUID) instanceof VillagerEntity villager) {
         TitanPowerData data = TitanPowerData.get(level);
         TitanPowerType power = data.getPower(villager.getUuid());
         if (power == null) {
            return stack;
         } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
            ItemStack filledSyringe = new ItemStack(DannysAot.SYRINGE);
            String sourceName = power.getDisplayName() + " Titan Shifter";
            SpinalFluidData fluidData = new SpinalFluidData(sourceName);
            daot.compat.components.Components.set(filledSyringe, DannysAot.SPINAL_FLUID_DATA, fluidData);
            DannysAot.LOGGER.info("Player {} extracted {} power from villager", player.getName().getString(), power.getDisplayName());
            return this.replaceWithFilledSyringe(stack, player, filledSyringe);
         }
      } else {
         return stack;
      }
   }

   private ItemStack handleSelfExtraction(ItemStack stack, ServerWorld level, PlayerEntity player) {
      player.damage(level.getDamageSources().generic(), 1.0F);
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
      ItemStack filledSyringe = new ItemStack(DannysAot.SYRINGE);
      boolean isRoyal = false;
      if (player instanceof ServerPlayerEntity sp) {
         BloodlineData bloodlineData = BloodlineData.get(sp.getServerWorld());
         isRoyal = bloodlineData.getBloodline(sp.getUuid()) == BloodlineType.ROYAL || sp.getCommandTags().contains("titan_bloodline");
      }

      SpinalFluidData fluidData = new SpinalFluidData(player.getName().getString(), isRoyal);
      daot.compat.components.Components.set(filledSyringe, DannysAot.SPINAL_FLUID_DATA, fluidData);
      DannysAot.LOGGER.info("Player {} extracted their own spinal fluid", player.getName().getString());
      return this.replaceWithFilledSyringe(stack, player, filledSyringe);
   }

   private ItemStack replaceWithFilledSyringe(ItemStack stack, PlayerEntity player, ItemStack filledSyringe) {
      if (!player.getAbilities().creativeMode) {
         stack.decrement(1);
         if (!player.getInventory().insertStack(filledSyringe)) {
            player.dropItem(filledSyringe, false);
         }

         return stack;
      } else {
         if (!player.getInventory().insertStack(filledSyringe)) {
            player.dropItem(filledSyringe, false);
         }

         return stack;
      }
   }

   @Override
   public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
      if (user instanceof PlayerEntity player) {
         removeTargetTitan(player);
         removeTargetPoweredVillager(player);
         setSelfExtracting(player, false);
      }
   }
}
