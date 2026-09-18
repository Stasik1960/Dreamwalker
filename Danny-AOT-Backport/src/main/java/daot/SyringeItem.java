package daot;

import daot.network.ModNetworking;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class SyringeItem extends Item {
   private static final Map<UUID, UUID> TARGET_VILLAGERS = new ConcurrentHashMap<>();
   private static final Map<UUID, UUID> TARGET_PLAYERS = new ConcurrentHashMap<>();
   private static final Map<UUID, Boolean> SELF_INJECTING = new ConcurrentHashMap<>();

   public SyringeItem(Settings properties) {
      super(properties);
   }

   @Override
   public int getMaxUseTime(ItemStack stack) {
      return 20;
   }

   @Override
   public Text getName(ItemStack stack) {
      return Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.DARK_PURPLE).formatted(Formatting.ITALIC);
   }

   @Override
   public UseAction getUseAction(ItemStack stack) {
      return UseAction.BOW;
   }

   @Override
   public void appendTooltip(ItemStack stack, World context, List<Text> tooltip, TooltipContext type) {
      super.appendTooltip(stack, context, tooltip, type);
      SpinalFluidData fluidData = daot.compat.components.Components.getOrDefault(stack, DannysAot.SPINAL_FLUID_DATA, SpinalFluidData.DEFAULT);
      tooltip.add(Text.literal("Filled with:").formatted(Formatting.GRAY));
      if (fluidData.isRoyal()) {
         tooltip.add(Text.literal(fluidData.getDisplayName()).formatted(Formatting.GOLD).formatted(Formatting.BOLD));
      } else {
         tooltip.add(Text.literal(fluidData.getDisplayName()).formatted(Formatting.DARK_RED));
      }
   }

   @Override
   public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
      ItemStack stack = user.getStackInHand(hand);
      if (hasTargetVillager(user)) {
         setSelfInjecting(user, false);
         user.setCurrentHand(hand);
         return TypedActionResult.consume(stack);
      } else if (hasTargetPlayer(user)) {
         setSelfInjecting(user, false);
         user.setCurrentHand(hand);
         return TypedActionResult.consume(stack);
      } else if (!user.isSneaking()) {
         return TypedActionResult.pass(stack);
      } else {
         setSelfInjecting(user, true);
         if (!world.isClient()) {
            if (!DannysAot.isSelfInjectAllowed(world)) {
               setSelfInjecting(user, false);
               if (user instanceof ServerPlayerEntity serverPlayer) {
                  serverPlayer.sendMessage(Text.literal("Self-Injection is Disabled").formatted(Formatting.RED), true);
               }

               return TypedActionResult.fail(stack);
            }

            Identifier pathsDim = new Identifier("dannys-aot", "paths");
            if (user.getWorld().getRegistryKey().getValue().equals(pathsDim)) {
               setSelfInjecting(user, false);
               if (user instanceof ServerPlayerEntity serverPlayer) {
                  serverPlayer.sendMessage(Text.literal("Cannot use syringes in the Paths"), true);
               }

               return TypedActionResult.fail(stack);
            }

            boolean hasColossal = user.getCommandTags().contains("colossal");
            boolean hasAttack = user.getCommandTags().contains("attack");
            boolean hasArmored = user.getCommandTags().contains("armored");
            boolean hasBeast = user.getCommandTags().contains("beast");
            boolean hasFemale = user.getCommandTags().contains("female");
            boolean hasWarhammer = user.getCommandTags().contains("warhammer");
            if (hasColossal || hasAttack || hasArmored || hasBeast || hasFemale || hasWarhammer) {
               setSelfInjecting(user, false);
               if (user instanceof ServerPlayerEntity serverPlayer) {
                  serverPlayer.sendMessage(Text.literal("Already has the power of the titans"), true);
               }

               return TypedActionResult.fail(stack);
            }

            if (user.getCommandTags().contains("titan_bloodline")) {
               setSelfInjecting(user, false);
               return TypedActionResult.fail(stack);
            }

            if (user instanceof ServerPlayerEntity serverPlayer) {
               BloodlineData bloodlineData = BloodlineData.get(serverPlayer.getServerWorld());
               BloodlineType bloodline = bloodlineData.getBloodline(serverPlayer.getUuid());
               if (bloodline == BloodlineType.ACKERMAN || bloodline == BloodlineType.MARLEYAN) {
                  setSelfInjecting(user, false);
                  serverPlayer.sendMessage(Text.literal("Only Subjects of Ymir can use this").formatted(Formatting.RED), true);
                  return TypedActionResult.fail(stack);
               }
            }
         }

         user.setCurrentHand(hand);
         return TypedActionResult.consume(stack);
      }
   }

   @Override
   public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
      if (user instanceof PlayerEntity player) {
         boolean wasSelfInjecting = isSelfInjecting(player);
         setSelfInjecting(player, false);
         if (world.isClient()) {
            return stack;
         } else {
            ServerWorld serverLevel = (ServerWorld)world;
            if (wasSelfInjecting) {
               return this.handleSelfInjection(stack, serverLevel, player);
            } else {
               UUID targetPlayerUUID = getAndRemoveTargetPlayer(player);
               if (targetPlayerUUID == null) {
                  UUID villagerUUID = getAndRemoveTargetVillager(player);
                  if (villagerUUID == null) {
                     return stack;
                  } else {
                     VillagerEntity villager = (VillagerEntity)serverLevel.getEntity(villagerUUID);
                     if (villager == null || !villager.isAlive()) {
                        return stack;
                     } else if (!VillagerTransformTracker.isInjected(villager) && !VillagerTransformTracker.isTransforming(villager)) {
                        TitanPowerData data = TitanPowerData.get(serverLevel);
                        if (data.hasPower(villager.getUuid())) {
                           return stack;
                        } else {
                           world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.PLAYERS, 1.0F, 1.0F);
                           SpinalFluidData fluidData = daot.compat.components.Components.getOrDefault(stack, DannysAot.SPINAL_FLUID_DATA, SpinalFluidData.DEFAULT);
                           VillagerTransformTracker.injectVillager(villager, fluidData.sourceName(), fluidData.isRoyal());
                           DannysAot.LOGGER
                              .info(
                                 "Player {} injected villager at ({}, {}, {})",
                                 new Object[]{player.getName().getString(), villager.getX(), villager.getY(), villager.getZ()}
                              );
                           return !player.getAbilities().creativeMode ? new ItemStack(DannysAot.EMPTY_SYRINGE) : stack;
                        }
                     } else {
                        return stack;
                     }
                  }
               } else {
                  if (serverLevel.getEntity(targetPlayerUUID) instanceof ServerPlayerEntity targetPlayer && targetPlayer.isAlive()) {
                     boolean hasShifterPowers = targetPlayer.getCommandTags().contains("attack")
                        || targetPlayer.getCommandTags().contains("colossal")
                        || targetPlayer.getCommandTags().contains("armored")
                        || targetPlayer.getCommandTags().contains("beast")
                        || targetPlayer.getCommandTags().contains("female")
                        || targetPlayer.getCommandTags().contains("warhammer");
                     BloodlineData bloodlineData = BloodlineData.get(serverLevel);
                     BloodlineType targetBloodline = bloodlineData.getBloodline(targetPlayer.getUuid());
                     boolean isImmune = targetBloodline == BloodlineType.ACKERMAN
                        || targetBloodline == BloodlineType.MARLEYAN
                        || targetPlayer.getCommandTags().contains("titan_bloodline");
                     if (!hasShifterPowers && !isImmune && !VillagerTransformTracker.isPlayerInjected(targetPlayer)) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.PLAYERS, 1.0F, 1.0F);
                        SpinalFluidData fluidData = daot.compat.components.Components.getOrDefault(stack, DannysAot.SPINAL_FLUID_DATA, SpinalFluidData.DEFAULT);
                        VillagerTransformTracker.injectPlayer(targetPlayer, fluidData.sourceName(), fluidData.isRoyal());
                        targetPlayer.sendMessage(Text.literal("You have been injected with spinal fluid!").formatted(Formatting.DARK_RED), false);
                        DannysAot.LOGGER
                           .info("Player {} injected player {} with spinal fluid", player.getName().getString(), targetPlayer.getName().getString());
                        if (!player.getAbilities().creativeMode) {
                           return new ItemStack(DannysAot.EMPTY_SYRINGE);
                        }
                     }
                  }

                  return stack;
               }
            }
         }
      } else {
         return stack;
      }
   }

   private ItemStack handleSelfInjection(ItemStack stack, ServerWorld level, PlayerEntity player) {
      boolean hasColossal = player.getCommandTags().contains("colossal");
      boolean hasAttack = player.getCommandTags().contains("attack");
      boolean hasArmored = player.getCommandTags().contains("armored");
      boolean hasBeast = player.getCommandTags().contains("beast");
      boolean hasFemale = player.getCommandTags().contains("female");
      boolean hasWarhammer = player.getCommandTags().contains("warhammer");
      if (!hasColossal && !hasAttack && !hasArmored && !hasBeast && !hasFemale && !hasWarhammer) {
         if (player.getCommandTags().contains("titan_bloodline")) {
            return stack;
         } else {
            if (player instanceof ServerPlayerEntity serverPlayer) {
               BloodlineData bloodlineData = BloodlineData.get(level);
               BloodlineType bloodline = bloodlineData.getBloodline(serverPlayer.getUuid());
               if (bloodline == BloodlineType.ACKERMAN || bloodline == BloodlineType.MARLEYAN) {
                  serverPlayer.sendMessage(Text.literal("Only Subjects of Ymir can use this").formatted(Formatting.RED), true);
                  return stack;
               }
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.PLAYERS, 1.0F, 1.0F);
            if (player instanceof ServerPlayerEntity serverPlayerx) {
               ModNetworking.initiatePureTitanShift(serverPlayerx, level);
            }

            return !player.getAbilities().creativeMode ? new ItemStack(DannysAot.EMPTY_SYRINGE) : stack;
         }
      } else {
         if (player instanceof ServerPlayerEntity serverPlayerx) {
            serverPlayerx.sendMessage(Text.literal("Already has the power of the titans"), true);
         }

         return stack;
      }
   }

   @Override
   public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
      if (user instanceof PlayerEntity player) {
         removeTargetVillager(player);
         removeTargetPlayer(player);
         setSelfInjecting(player, false);
      }
   }

   public static void setTargetVillager(PlayerEntity player, VillagerEntity villager) {
      TARGET_VILLAGERS.put(player.getUuid(), villager.getUuid());
   }

   public static boolean hasTargetVillager(PlayerEntity player) {
      return TARGET_VILLAGERS.containsKey(player.getUuid());
   }

   public static UUID getAndRemoveTargetVillager(PlayerEntity player) {
      return TARGET_VILLAGERS.remove(player.getUuid());
   }

   public static void removeTargetVillager(PlayerEntity player) {
      TARGET_VILLAGERS.remove(player.getUuid());
   }

   public static void setTargetPlayer(PlayerEntity injector, PlayerEntity target) {
      TARGET_PLAYERS.put(injector.getUuid(), target.getUuid());
   }

   public static boolean hasTargetPlayer(PlayerEntity player) {
      return TARGET_PLAYERS.containsKey(player.getUuid());
   }

   public static UUID getAndRemoveTargetPlayer(PlayerEntity player) {
      return TARGET_PLAYERS.remove(player.getUuid());
   }

   public static void removeTargetPlayer(PlayerEntity player) {
      TARGET_PLAYERS.remove(player.getUuid());
   }

   public static void setSelfInjecting(PlayerEntity player, boolean value) {
      if (value) {
         SELF_INJECTING.put(player.getUuid(), true);
      } else {
         SELF_INJECTING.remove(player.getUuid());
      }
   }

   public static boolean isSelfInjecting(PlayerEntity player) {
      return SELF_INJECTING.getOrDefault(player.getUuid(), false);
   }
}
