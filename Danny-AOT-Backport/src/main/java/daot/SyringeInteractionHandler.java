package daot;

import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

public class SyringeInteractionHandler {
   public static void register() {
      UseEntityCallback.EVENT
         .register(
            (UseEntityCallback)(player, world, hand, entity, hitResult) -> {
               ItemStack stack = player.getStackInHand(hand);
               if (stack.getItem() instanceof SyringeItem) {
                  if (entity instanceof PlayerEntity targetPlayer && entity != player) {
                     if (world.isClient()) {
                        return ActionResult.SUCCESS;
                     } else if (!DannysAot.canInjectOtherPlayers(world)) {
                        if (player instanceof ServerPlayerEntity serverPlayer) {
                           serverPlayer.sendMessage(Text.literal("Player injection is disabled"), true);
                        }

                        return ActionResult.FAIL;
                     } else {
                        boolean hasShifterPowers = targetPlayer.getCommandTags().contains("attack")
                           || targetPlayer.getCommandTags().contains("colossal")
                           || targetPlayer.getCommandTags().contains("armored")
                           || targetPlayer.getCommandTags().contains("beast")
                           || targetPlayer.getCommandTags().contains("female")
                           || targetPlayer.getCommandTags().contains("warhammer");
                        if (hasShifterPowers) {
                           if (player instanceof ServerPlayerEntity serverPlayer) {
                              serverPlayer.sendMessage(Text.literal("Already has the power of the titans"), true);
                           }

                           return ActionResult.FAIL;
                        } else if (VillagerTransformTracker.isPlayerInjected(targetPlayer)) {
                           if (player instanceof ServerPlayerEntity serverPlayer) {
                              serverPlayer.sendMessage(Text.literal("Already injected"), true);
                           }

                           return ActionResult.FAIL;
                        } else if (targetPlayer.getCommandTags().contains("titan_bloodline")) {
                           return ActionResult.FAIL;
                        } else {
                           if (targetPlayer instanceof ServerPlayerEntity targetServerPlayer) {
                              BloodlineData bloodlineData = BloodlineData.get(targetServerPlayer.getServerWorld());
                              BloodlineType targetBloodline = bloodlineData.getBloodline(targetServerPlayer.getUuid());
                              if (targetBloodline == BloodlineType.ACKERMAN || targetBloodline == BloodlineType.MARLEYAN) {
                                 if (player instanceof ServerPlayerEntity serverPlayer) {
                                    serverPlayer.sendMessage(Text.literal("Player is not a Subject of Ymir"), true);
                                 }

                                 return ActionResult.FAIL;
                              }
                           }

                           SyringeItem.setTargetPlayer(player, targetPlayer);
                           player.setCurrentHand(hand);
                           return ActionResult.SUCCESS;
                        }
                     }
                  } else if (entity instanceof VillagerEntity villager) {
                     if (world.isClient()) {
                        return ActionResult.SUCCESS;
                     } else {
                        ServerWorld serverLevel = (ServerWorld)world;
                        if (!VillagerTransformTracker.isInjected(villager) && !VillagerTransformTracker.isTransforming(villager)) {
                           TitanPowerData data = TitanPowerData.get(serverLevel);
                           UUID villagerUUID = villager.getUuid();
                           boolean hasPower = data.hasPower(villagerUUID);
                           TitanPowerType power = data.getPower(villagerUUID);
                           DannysAot.LOGGER
                              .info(
                                 "Injection check - Villager UUID: {}, hasPower: {}, power: {}, totalPowered: {}",
                                 new Object[]{villagerUUID, hasPower, power, data.getAllPoweredVillagers().size()}
                              );
                           if (hasPower) {
                              if (!EmptySyringeItem.hasTargetPoweredVillager(player) && player instanceof ServerPlayerEntity serverPlayer) {
                                 serverPlayer.sendMessage(Text.literal("Already has the power of the titans"), true);
                              }

                              return ActionResult.FAIL;
                           } else {
                              SyringeItem.setTargetVillager(player, villager);
                              player.setCurrentHand(hand);
                              return ActionResult.SUCCESS;
                           }
                        } else {
                           return ActionResult.FAIL;
                        }
                     }
                  } else {
                     return ActionResult.PASS;
                  }
               } else if (stack.getItem() instanceof EmptySyringeItem) {
                  boolean isPureTitan = entity instanceof SmallTitanEntity
                     || entity instanceof SmallTitan2Entity
                     || entity instanceof TitanEntity
                     || entity instanceof FritzTitanEntity;
                  if (isPureTitan) {
                     if (world.isClient()) {
                        return ActionResult.SUCCESS;
                     } else {
                        EmptySyringeItem.setTargetTitan(player, entity);
                        player.setCurrentHand(hand);
                        return ActionResult.SUCCESS;
                     }
                  } else {
                     if (entity instanceof VillagerEntity villagerx) {
                        if (world.isClient()) {
                           return ActionResult.PASS;
                        }

                        ServerWorld serverLevel = (ServerWorld)world;
                        TitanPowerData data = TitanPowerData.get(serverLevel);
                        if (data.hasPower(villagerx.getUuid())) {
                           EmptySyringeItem.setTargetPoweredVillager(player, villagerx);
                           player.setCurrentHand(hand);
                           return ActionResult.SUCCESS;
                        }
                     }

                     return ActionResult.PASS;
                  }
               } else if (!(stack.getItem() instanceof HandcuffsItem)
                  || (!(entity instanceof PlayerEntity) || entity == player) && !(entity instanceof VillagerEntity)) {
                  ActionResult carryResult = handleCarriedPrisoner(player, world, hand);
                  if (carryResult != ActionResult.PASS) {
                     return carryResult;
                  } else if (stack.getItem() instanceof HandcuffsKeyItem
                     && (entity instanceof PlayerEntity || entity instanceof VillagerEntity)
                     && HandcuffsTracker.isCuffed(entity)) {
                     if (world.isClient()) {
                        return ActionResult.SUCCESS;
                     } else {
                        if (entity.hasVehicle()) {
                           Entity vehicle = entity.getVehicle();
                           if (entity instanceof ServerPlayerEntity prisoner) {
                              prisoner.addCommandTag("dannysaot_allow_dismount");
                           }

                           entity.stopRiding();
                           HandcuffsTracker.broadcastPassengerSync(vehicle);
                        }

                        HandcuffsTracker.uncuffEntity(entity, player);
                        if (player instanceof ServerPlayerEntity sp) {
                           sp.sendMessage(Text.literal("Released the cuffs!").formatted(Formatting.GREEN), true);
                        }

                        return ActionResult.SUCCESS;
                     }
                  } else if (player.isSneaking()
                     && (entity instanceof PlayerEntity || entity instanceof VillagerEntity)
                     && (HandcuffsTracker.isCuffed(entity) || DefeatedCarryTracker.isDefeated(entity.getUuid()))) {
                     if (world.isClient()) {
                        return ActionResult.SUCCESS;
                     } else if (HandcuffsTracker.isEscortCooldownActive(player.getUuid())) {
                        return ActionResult.SUCCESS;
                     } else {
                        HandcuffsTracker.setEscortCooldown(player.getUuid());
                        if (entity.getVehicle() instanceof PlayerEntity) {
                           if (player instanceof ServerPlayerEntity sp) {
                              sp.sendMessage(Text.literal("Prisoner is already being carried").formatted(Formatting.RED), true);
                           }
                        } else {
                           if (entity.hasVehicle()) {
                              entity.stopRiding();
                           }

                           float yawRad = (float)Math.toRadians(player.getYaw());
                           double frontX = player.getX() - Math.sin(yawRad);
                           double frontZ = player.getZ() + Math.cos(yawRad);
                           entity.requestTeleport(frontX, player.getY(), frontZ);
                           if (entity instanceof ServerPlayerEntity passPlayer) {
                              passPlayer.networkHandler.requestTeleport(frontX, player.getY(), frontZ, passPlayer.getYaw(), passPlayer.getPitch());
                           }

                           boolean success = entity.startRiding(player, true);
                           if (success) {
                              HandcuffsTracker.broadcastPassengerSync(player);
                              if (player instanceof ServerPlayerEntity sp) {
                                 sp.sendMessage(Text.literal("Carrying prisoner").formatted(Formatting.GRAY), true);
                              }
                           } else if (player instanceof ServerPlayerEntity sp) {
                              sp.sendMessage(Text.literal("Could not pick up prisoner").formatted(Formatting.RED), true);
                           }
                        }

                        return ActionResult.SUCCESS;
                     }
                  } else {
                     return ActionResult.PASS;
                  }
               } else if (world.isClient()) {
                  return ActionResult.SUCCESS;
               } else if (HandcuffsTracker.isCuffed(entity)) {
                  if (player instanceof ServerPlayerEntity sp) {
                     sp.sendMessage(Text.literal("Already cuffed"), true);
                  }

                  return ActionResult.SUCCESS;
               } else if (entity instanceof PlayerEntity targetP && targetP.isCreative()) {
                  if (player instanceof ServerPlayerEntity sp) {
                     sp.sendMessage(Text.literal("Cannot cuff a creative player"), true);
                  }

                  return ActionResult.SUCCESS;
               } else if (!(
                  entity instanceof PlayerEntity targetP
                     && targetP.getVehicle() != null
                     && (
                        targetP.getVehicle() instanceof AttackTitanEntity
                           || targetP.getVehicle() instanceof ArmoredTitanEntity
                           || targetP.getVehicle() instanceof ColossalTitanEntity
                           || targetP.getVehicle() instanceof FemaleTitanEntity
                           || targetP.getVehicle() instanceof BeastTitanEntity
                           || targetP.getVehicle() instanceof WarhammerTitanEntity
                     )
               )) {
                  if (!DannysAot.isCuffingAllowed(world) && !player.hasPermissionLevel(2)) {
                     if (player instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(Text.literal("Cuffing is disabled"), true);
                     }

                     return ActionResult.SUCCESS;
                  } else {
                     HandcuffsTracker.onInteractEntity((ServerPlayerEntity)player, entity);
                     player.setCurrentHand(hand);
                     return ActionResult.SUCCESS;
                  }
               } else {
                  if (player instanceof ServerPlayerEntity sp) {
                     sp.sendMessage(Text.literal("Cannot cuff a titan shifter"), true);
                  }

                  return ActionResult.SUCCESS;
               }
            }
         );
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hitResult) -> handleCarriedPrisoner(player, world, hand));
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
         ActionResult result = handleCarriedPrisoner(player, world, hand);
         if (result != ActionResult.PASS) {
            return TypedActionResult.success(player.getStackInHand(hand));
         } else {
            TypedActionResult<ItemStack> laceResult = tryLaceFoodWithSyringe(player, world, hand);
            return laceResult != null ? laceResult : TypedActionResult.pass(player.getStackInHand(hand));
         }
      });
      DannysAot.LOGGER.info("Registered syringe interaction handler");
   }

   private static TypedActionResult<ItemStack> tryLaceFoodWithSyringe(PlayerEntity player, World world, Hand hand) {
      if (hand != Hand.MAIN_HAND) {
         return null;
      } else {
         ItemStack mainStack = player.getStackInHand(Hand.MAIN_HAND);
         ItemStack offStack = player.getStackInHand(Hand.OFF_HAND);
         if (mainStack.isEmpty() || offStack.isEmpty()) {
            return null;
         } else if (!(offStack.getItem() instanceof SyringeItem)) {
            return null;
         } else {
            SpinalFluidData fluid = daot.compat.components.Components.getOrDefault(offStack, DannysAot.SPINAL_FLUID_DATA, SpinalFluidData.DEFAULT);
            if (fluid == null) {
               return null;
            } else {
               UseAction anim = mainStack.getUseAction();
               if (anim != UseAction.EAT && anim != UseAction.DRINK) {
                  return null;
               } else if (world.isClient()) {
                  return TypedActionResult.success(mainStack);
               } else {
                  daot.compat.components.Components.set(mainStack, DannysAot.LACED_FOOD_DATA, new LacedFoodData(fluid.sourceName(), fluid.isRoyal()));
                  if (!player.getAbilities().creativeMode) {
                     player.setStackInHand(Hand.OFF_HAND, new ItemStack(DannysAot.EMPTY_SYRINGE));
                  }

                  world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.8F, 1.3F);
                  if (player instanceof ServerPlayerEntity sp) {
                     Text itemName = mainStack.getName();
                     sp.sendMessage(Text.empty().append(itemName).append(Text.literal(" laced with Spinal Fluid")), true);
                  }

                  return TypedActionResult.success(mainStack);
               }
            }
         }
      }
   }

   private static ActionResult handleCarriedPrisoner(PlayerEntity player, World world, Hand hand) {
      ItemStack stack = player.getStackInHand(hand);
      Entity carriedPrisoner = null;

      for (Entity passenger : player.getPassengerList()) {
         if (HandcuffsTracker.isCuffed(passenger) || DefeatedCarryTracker.isDefeated(passenger.getUuid())) {
            carriedPrisoner = passenger;
            break;
         }
      }

      if (carriedPrisoner == null) {
         return ActionResult.PASS;
      } else if (stack.getItem() instanceof HandcuffsKeyItem) {
         if (world.isClient()) {
            return ActionResult.SUCCESS;
         } else {
            if (carriedPrisoner instanceof ServerPlayerEntity prisoner) {
               prisoner.addCommandTag("dannysaot_allow_dismount");
            }

            carriedPrisoner.stopRiding();
            HandcuffsTracker.broadcastPassengerSync(player);
            HandcuffsTracker.uncuffEntity(carriedPrisoner, player);
            if (player instanceof ServerPlayerEntity sp) {
               sp.sendMessage(Text.literal("Released the cuffs!").formatted(Formatting.GREEN), true);
            }

            return ActionResult.SUCCESS;
         }
      } else if (player.isSneaking()) {
         if (world.isClient()) {
            return ActionResult.SUCCESS;
         } else if (HandcuffsTracker.isEscortCooldownActive(player.getUuid())) {
            return ActionResult.SUCCESS;
         } else {
            HandcuffsTracker.setEscortCooldown(player.getUuid());
            if (carriedPrisoner instanceof ServerPlayerEntity prisoner) {
               prisoner.addCommandTag("dannysaot_allow_dismount");
            }

            carriedPrisoner.stopRiding();
            HandcuffsTracker.broadcastPassengerSync(player);
            if (carriedPrisoner instanceof ServerPlayerEntity prisoner) {
               prisoner.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), prisoner.getYaw(), prisoner.getPitch());
            }

            if (player instanceof ServerPlayerEntity sp) {
               sp.sendMessage(Text.literal("Dropped prisoner").formatted(Formatting.GRAY), true);
            }

            return ActionResult.SUCCESS;
         }
      } else {
         return ActionResult.PASS;
      }
   }
}
