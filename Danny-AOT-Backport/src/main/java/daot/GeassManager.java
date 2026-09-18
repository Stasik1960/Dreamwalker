package daot;

import daot.network.GeassControlPayload;
import daot.network.MindControlInputPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import daot.compat.network.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class GeassManager {
   private static UUID controllerUUID = null;
   private static UUID selectedTargetUUID = null;
   private static boolean targetIsPlayer = true;
   private static GeassManager.CommandType activeCommand = GeassManager.CommandType.NONE;
   private static UUID killVictimUUID = null;
   private static int airDrainCounter = 0;
   private static double freezeX;
   private static double freezeY;
   private static double freezeZ;
   private static float freezeYaw;
   private static float freezePitch;
   private static int mobAttackCooldown = 0;
   private static int sprintJumpTimer = 0;
   private static boolean stealthMode = false;
   private static boolean wasCrouching = false;
   private static int lastCrouchTick = -100;
   private static int crouchCount = 0;
   private static double savedCtrlX;
   private static double savedCtrlY;
   private static double savedCtrlZ;
   private static float savedCtrlYaw;
   private static float savedCtrlPitch;
   private static boolean wasCtrlInvulnerable = false;
   private static boolean wasCtrlSwinging = false;
   private static NbtList savedControllerInventory = null;
   private static NbtList savedTargetInventory = null;
   static float ctrlInputForward = 0.0F;
   static float ctrlInputStrafe = 0.0F;
   static boolean ctrlInputJumping = false;
   static boolean ctrlInputSprinting = false;
   static boolean ctrlInputShifting = false;
   static boolean pendingTargetSwing = false;
   static BlockPos mcBreakingPos = null;
   static Direction mcBreakingDir = null;
   static boolean mcBreakingActive = false;
   static int mcBreakingStartTick = 0;
   private static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(1.0F, 0.0F, 0.0F), 0.7F);
   static boolean ctrlInputInventoryOpen = false;
   private static boolean allowGeassDrop = false;

   public static boolean isGeassActive() {
      return controllerUUID != null;
   }

   public static boolean isBeingControlled(UUID uuid) {
      return selectedTargetUUID != null && selectedTargetUUID.equals(uuid) && activeCommand != GeassManager.CommandType.NONE;
   }

   public static boolean isFrozen(UUID uuid) {
      return selectedTargetUUID != null && selectedTargetUUID.equals(uuid) && activeCommand == GeassManager.CommandType.FREEZE;
   }

   public static boolean isMindController(UUID uuid) {
      return controllerUUID != null && controllerUUID.equals(uuid) && activeCommand == GeassManager.CommandType.MIND_CONTROL;
   }

   public static void handleControllerAction(ServerPlayerEntity controller, int action, int entityId, long blockPosLong, int direction, int hand) {
      if (isMindController(controller.getUuid())) {
         MinecraftServer server = controller.server;
         ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
         if (target != null) {
            Hand interactionHand = hand == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            switch (action) {
               case 0:
                  target.swingHand(Hand.MAIN_HAND);
                  pendingTargetSwing = true;
                  break;
               case 1:
                  Entity victim = target.getServerWorld().getEntityById(entityId);
                  if (victim == null) {
                     for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                        if (p.getId() == entityId) {
                           victim = p;
                           break;
                        }
                     }
                  }

                  target.swingHand(Hand.MAIN_HAND);
                  pendingTargetSwing = true;
                  if (victim != null && victim != controller) {
                     target.attack(victim);
                  }
                  break;
               case 2:
                  mcBreakingPos = BlockPos.fromLong(blockPosLong);
                  mcBreakingDir = Direction.values()[Math.min(direction, 5)];
                  mcBreakingActive = true;
                  mcBreakingStartTick = server.getTicks();
                  target.swingHand(Hand.MAIN_HAND);
                  pendingTargetSwing = true;
                  break;
               case 3:
                  if (mcBreakingActive && mcBreakingPos != null) {
                     target.getWorld().setBlockBreakingInfo(target.getId(), mcBreakingPos, -1);
                  }

                  mcBreakingActive = false;
                  break;
               case 4:
                  Entity useTarget = target.getServerWorld().getEntityById(entityId);
                  if (useTarget != null && useTarget != controller) {
                     ActionResult entityResult = target.interact(useTarget, interactionHand);
                     if (entityResult.isAccepted()) {
                        target.swingHand(Hand.MAIN_HAND);
                        pendingTargetSwing = true;
                     }
                  }
                  break;
               case 5:
                  BlockPos usePos = BlockPos.fromLong(blockPosLong);
                  Direction useDir = Direction.values()[Math.min(direction, 5)];
                  BlockState blockState = target.getWorld().getBlockState(usePos);
                  BlockHitResult blockHit = new BlockHitResult(Vec3d.ofCenter(usePos).add(0.0, -0.5 + useDir.getOffsetY() * 0.5, 0.0), useDir, usePos, false);
                  ActionResult blockResult = blockState.onUse(target.getWorld(), target, Hand.MAIN_HAND, blockHit);
                  if (blockResult.isAccepted()) {
                     target.swingHand(Hand.MAIN_HAND);
                     pendingTargetSwing = true;
                  } else {
                     ItemStack heldItem = target.getStackInHand(interactionHand);
                     ActionResult itemResult = target.interactionManager.interactBlock(target, target.getWorld(), heldItem, interactionHand, blockHit);
                     if (itemResult.isAccepted()) {
                        target.swingHand(Hand.MAIN_HAND);
                        pendingTargetSwing = true;
                     }
                  }
                  break;
               case 6:
                  ItemStack airItem = target.getStackInHand(interactionHand);
                  ActionResult airResult = target.interactionManager.interactItem(target, target.getWorld(), airItem, interactionHand);
                  if (airResult.isAccepted()) {
                     target.swingHand(Hand.MAIN_HAND);
                     pendingTargetSwing = true;
                  }
            }

            syncInventoryFromTarget(controller, target);
            controller.playerScreenHandler.sendContentUpdates();
         }
      }
   }

   public static void setControllerExtraInput(
      ServerPlayerEntity player, float forward, float strafe, boolean jumping, boolean sprinting, boolean shifting, boolean inventoryOpen
   ) {
      if (controllerUUID != null && controllerUUID.equals(player.getUuid())) {
         ctrlInputForward = forward;
         ctrlInputStrafe = strafe;
         ctrlInputJumping = jumping;
         ctrlInputSprinting = sprinting;
         ctrlInputShifting = shifting;
         ctrlInputInventoryOpen = inventoryOpen;
      }
   }

   public static void syncAfterAction(ServerPlayerEntity controller, ServerPlayerEntity target) {
      syncInventoryFromTarget(controller, target);
      controller.playerScreenHandler.sendContentUpdates();
      target.playerScreenHandler.sendContentUpdates();
   }

   public static void onPlayerJoin(ServerPlayerEntity player) {
      sendControlPacket(player, 0, -1);
   }

   public static UUID getControllerUUID() {
      return controllerUUID;
   }

   public static UUID getMindControlTargetUUID() {
      return activeCommand == GeassManager.CommandType.MIND_CONTROL ? selectedTargetUUID : null;
   }

   public static boolean hasSelectedTarget(UUID senderUUID) {
      return controllerUUID != null && controllerUUID.equals(senderUUID) && selectedTargetUUID != null;
   }

   public static void toggle(ServerPlayerEntity player) {
      if (controllerUUID != null && controllerUUID.equals(player.getUuid())) {
         deactivate(player);
      } else if (controllerUUID == null) {
         activate(player);
      }
   }

   private static void activate(ServerPlayerEntity controller) {
      LivingEntity target = findEyeContactTarget(controller);
      if (target == null) {
         controller.sendMessage(Text.literal("No eye contact.").styled(s -> s.withColor(16729156)));
      } else {
         controllerUUID = controller.getUuid();
         selectedTargetUUID = target.getUuid();
         targetIsPlayer = target instanceof ServerPlayerEntity;
         killVictimUUID = null;
         airDrainCounter = 0;
         if (controller.isSneaking() && target instanceof ServerPlayerEntity targetPlayer) {
            activeCommand = GeassManager.CommandType.MIND_CONTROL;
            savedCtrlX = controller.getX();
            savedCtrlY = controller.getY();
            savedCtrlZ = controller.getZ();
            savedCtrlYaw = controller.getYaw();
            savedCtrlPitch = controller.getPitch();
            wasCtrlInvulnerable = controller.isInvulnerable();
            wasCtrlSwinging = false;
            controller.teleport(
               (ServerWorld)targetPlayer.getWorld(),
               targetPlayer.getX(),
               targetPlayer.getY(),
               targetPlayer.getZ(),
               targetPlayer.getYaw(),
               targetPlayer.getPitch()
            );
            controller.setInvisible(true);
            controller.setInvulnerable(true);

            for (ServerPlayerEntity other : controller.server.getPlayerManager().getPlayerList()) {
               if (other != controller) {
                  other.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(controller.getUuid())));
                  other.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(controller.getId()));
               }
            }

            sendControlPacket(targetPlayer, 5, -1);
            sendControlPacket(controller, 7, targetPlayer.getId());
            savedControllerInventory = controller.getInventory().writeNbt(new NbtList());
            savedTargetInventory = targetPlayer.getInventory().writeNbt(new NbtList());
            syncInventoryFromTarget(controller, targetPlayer);
            controller.sendMessage(Text.literal("Mind control: " + target.getName().getString()).styled(s -> s.withColor(16711680).withBold(true)));
         } else {
            activeCommand = GeassManager.CommandType.NONE;
            controller.sendMessage(Text.literal(target.getName().getString() + " selected.").styled(s -> s.withColor(16711680).withBold(true)));
         }
      }
   }

   private static void releaseMindControl(ServerPlayerEntity controller) {
      MinecraftServer srv = controller.server;
      ServerPlayerEntity target = srv.getPlayerManager().getPlayer(selectedTargetUUID);
      if (target != null) {
         syncInventoryToTarget(controller, target);
         sendControlPacket(target, 0, -1);
      }

      if (savedControllerInventory != null) {
         controller.getInventory().readNbt(savedControllerInventory);
         savedControllerInventory = null;
      }

      savedTargetInventory = null;
      activeCommand = GeassManager.CommandType.NONE;
      sendControlPacket(controller, 0, -1);
      controller.setInvisible(false);
      controller.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20, 0, false, false));
      controller.setInvulnerable(wasCtrlInvulnerable);
      controller.teleport((ServerWorld)controller.getWorld(), savedCtrlX, savedCtrlY, savedCtrlZ, savedCtrlYaw, savedCtrlPitch);
      controller.sidewaysSpeed = 0.0F;
      controller.forwardSpeed = 0.0F;
      controller.setVelocity(0.0, 0.0, 0.0);
      controller.playerScreenHandler.sendContentUpdates();
      mcBreakingActive = false;

      for (ServerPlayerEntity other : controller.server.getPlayerManager().getPlayerList()) {
         if (other != controller) {
            other.networkHandler.sendPacket(PlayerListS2CPacket.entryFromPlayer(List.of(controller)));
         }
      }

      ServerChunkManager chunkSource = controller.getServerWorld().getChunkManager();
      chunkSource.unloadEntity(controller);
      chunkSource.loadEntity(controller);
   }

   private static void deactivate(ServerPlayerEntity controller) {
      if (activeCommand == GeassManager.CommandType.MIND_CONTROL && controller != null) {
         releaseMindControl(controller);
      }

      if (selectedTargetUUID != null) {
         if (targetIsPlayer) {
            MinecraftServer server = controller != null ? controller.server : null;
            if (server != null) {
               ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
               if (target != null) {
                  sendControlPacket(target, 0, -1);
               }
            }
         } else if (controller != null && controller.getServerWorld().getEntity(selectedTargetUUID) instanceof MobEntity mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
         }
      }

      activeCommand = GeassManager.CommandType.NONE;
      killVictimUUID = null;
      controllerUUID = null;
      selectedTargetUUID = null;
      airDrainCounter = 0;
      wasCtrlSwinging = false;
      if (controller != null) {
         controller.sendMessage(Text.literal("Released.").styled(s -> s.withColor(8947848)));
      }
   }

   public static boolean tryParseCommand(ServerPlayerEntity sender, String rawText, MinecraftServer server) {
      if (!hasSelectedTarget(sender.getUuid())) {
         return false;
      } else if (activeCommand == GeassManager.CommandType.MIND_CONTROL) {
         return false;
      } else {
         String text = rawText.toLowerCase().trim();
         boolean isCommand = false;
         if (text.equals("give me that")) {
            if (targetIsPlayer) {
               executeGiveMe(sender, server);
            }

            isCommand = true;
         } else if (text.equals("give me your odm")) {
            if (targetIsPlayer) {
               executeGiveMeODM(sender, server);
            }

            isCommand = true;
         } else if (text.equals("follow me")) {
            activeCommand = GeassManager.CommandType.FOLLOW;
            isCommand = true;
         } else if (text.startsWith("kill ")) {
            String victimName = rawText.trim().substring(5).trim();
            LivingEntity victim = findKillTarget(server, sender, victimName);
            if (victim != null && !victim.getUuid().equals(selectedTargetUUID)) {
               activeCommand = GeassManager.CommandType.KILL;
               killVictimUUID = victim.getUuid();
               isCommand = true;
            }
         } else if (text.equals("stop breathing")) {
            activeCommand = GeassManager.CommandType.STOP_BREATHING;
            airDrainCounter = 0;
            if (targetIsPlayer) {
               ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
               if (target != null) {
                  sendControlPacket(target, 3, -1);
               }
            }

            isCommand = true;
         } else if (text.equals("freeze")) {
            LivingEntity target = getTargetEntity(server, sender);
            if (target != null) {
               freezeX = target.getX();
               freezeY = target.getY();
               freezeZ = target.getZ();
               freezeYaw = target.getYaw();
               freezePitch = target.getPitch();
               if (targetIsPlayer && target instanceof ServerPlayerEntity sp) {
                  sendControlPacket(sp, 4, -1);
               }
            }

            activeCommand = GeassManager.CommandType.FREEZE;
            isCommand = true;
         } else if (text.equals("stop")) {
            if (targetIsPlayer) {
               ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
               if (target != null) {
                  sendControlPacket(target, 0, -1);
               }
            } else if (getTargetEntity(server, sender) instanceof MobEntity mob) {
               mob.setTarget(null);
               mob.getNavigation().stop();
            }

            activeCommand = GeassManager.CommandType.NONE;
            killVictimUUID = null;
            airDrainCounter = 0;
            isCommand = true;
         }

         if (isCommand && !stealthMode) {
            broadcastLocalChat(sender, rawText, server, 50.0);
         }

         return isCommand;
      }
   }

   public static void tick(MinecraftServer server) {
      if (controllerUUID != null) {
         ServerPlayerEntity controller = server.getPlayerManager().getPlayer(controllerUUID);
         if (controller == null) {
            clearAll();
         } else {
            LivingEntity target = getTargetEntity(server, controller);
            if (target == null && selectedTargetUUID != null) {
               deactivate(controller);
            } else {
               if (target != null && target.isDead() && activeCommand != GeassManager.CommandType.NONE) {
                  if (activeCommand == GeassManager.CommandType.MIND_CONTROL) {
                     releaseMindControl(controller);
                  }

                  if (targetIsPlayer && target instanceof ServerPlayerEntity sp) {
                     sendControlPacket(sp, 0, -1);
                  } else if (target instanceof MobEntity mob) {
                     mob.setTarget(null);
                  }

                  activeCommand = GeassManager.CommandType.NONE;
                  killVictimUUID = null;
                  controller.sendMessage(Text.literal("Target died.").styled(s -> s.withColor(8947848)));
               }

               if (activeCommand != GeassManager.CommandType.MIND_CONTROL) {
                  boolean crouching = controller.isSneaking();
                  if (crouching && !wasCrouching) {
                     int currentTick = controller.age;
                     if (currentTick - lastCrouchTick < 10) {
                        stealthMode = !stealthMode;
                        controller.sendMessage(
                           Text.literal(stealthMode ? "Stealth: ON" : "Stealth: OFF").styled(s -> s.withColor(stealthMode ? 16711680 : 8947848))
                        );
                        lastCrouchTick = -100;
                     } else {
                        lastCrouchTick = currentTick;
                     }
                  }

                  wasCrouching = crouching;
               }

               spawnGeassParticles(controller);
               if (target != null && activeCommand != GeassManager.CommandType.NONE) {
                  if (targetIsPlayer && target instanceof ServerPlayerEntity sp) {
                     switch (activeCommand) {
                        case FOLLOW:
                           tickFollow(controller, sp);
                           break;
                        case KILL:
                           tickKill(controller, sp, server);
                           break;
                        case STOP_BREATHING:
                           tickStopBreathing(target);
                           break;
                        case FREEZE:
                           tickFreeze(sp);
                           break;
                        case MIND_CONTROL:
                           tickMindControl(controller, sp, server);
                     }
                  } else if (!targetIsPlayer) {
                     switch (activeCommand) {
                        case FOLLOW:
                           tickMobFollow(controller, target);
                           break;
                        case KILL:
                           tickMobKill(controller, target, server);
                           break;
                        case STOP_BREATHING:
                           tickStopBreathing(target);
                           break;
                        case FREEZE:
                           tickMobFreeze(target);
                     }
                  }
               }
            }
         }
      }
   }

   public static void onPlayerTick(ServerPlayerEntity player, boolean jumping) {
      if (controllerUUID == null || !controllerUUID.equals(player.getUuid()) || activeCommand != GeassManager.CommandType.MIND_CONTROL) {
         if (selectedTargetUUID == null || !selectedTargetUUID.equals(player.getUuid()) || activeCommand != GeassManager.CommandType.MIND_CONTROL) {
            if (selectedTargetUUID != null && selectedTargetUUID.equals(player.getUuid())) {
               if (activeCommand != GeassManager.CommandType.NONE) {
                  player.forwardSpeed = 0.0F;
                  player.sidewaysSpeed = 0.0F;
                  Vec3d vel = player.getVelocity();
                  player.setVelocity(0.0, vel.y, 0.0);
               }
            }
         }
      }
   }

   public static boolean isDropAllowed() {
      return allowGeassDrop;
   }

   private static void executeGiveMe(ServerPlayerEntity controller, MinecraftServer server) {
      ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
      if (target != null) {
         ItemStack held = target.getMainHandStack();
         if (!held.isEmpty()) {
            allowGeassDrop = true;
            target.dropItem(held.copy(), true);
            allowGeassDrop = false;
            target.getInventory().setStack(target.getInventory().selectedSlot, ItemStack.EMPTY);
         }
      }
   }

   private static void executeGiveMeODM(ServerPlayerEntity controller, MinecraftServer server) {
      ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
      if (target != null) {
         allowGeassDrop = true;
         ItemStack leggings = target.getInventory().armor.get(1);
         if (!leggings.isEmpty() && DannysAot.isODMGear(leggings.getItem())) {
            target.dropItem(leggings.copy(), true);
            target.getInventory().armor.set(1, ItemStack.EMPTY);
         }

         for (int i = 0; i < target.getInventory().size(); i++) {
            ItemStack stack = target.getInventory().getStack(i);
            if (!stack.isEmpty() && DannysAot.isODMGear(stack.getItem())) {
               target.dropItem(stack.copy(), true);
               target.getInventory().setStack(i, ItemStack.EMPTY);
            }
         }

         allowGeassDrop = false;
      }
   }

   private static void tickFollow(ServerPlayerEntity controller, ServerPlayerEntity target) {
      sendControlPacket(target, 1, controller.getId());
   }

   private static void tickKill(ServerPlayerEntity controller, ServerPlayerEntity target, MinecraftServer server) {
      if (killVictimUUID == null) {
         activeCommand = GeassManager.CommandType.NONE;
      } else {
         Entity victimEntity = ((ServerWorld)target.getWorld()).getEntity(killVictimUUID);
         if (victimEntity == null) {
            victimEntity = server.getPlayerManager().getPlayer(killVictimUUID);
         }

         if (victimEntity instanceof LivingEntity victim && !victim.isDead()) {
            sendControlPacket(target, 2, victim.getId());
         } else {
            activeCommand = GeassManager.CommandType.NONE;
            killVictimUUID = null;
            sendControlPacket(target, 0, -1);
            controller.sendMessage(Text.literal("Target eliminated.").styled(s -> s.withColor(16711680)));
         }
      }
   }

   private static void tickMindControl(ServerPlayerEntity controller, ServerPlayerEntity target, MinecraftServer server) {
      sendControlPacket(target, 5, -1);
      target.setYaw(controller.getYaw());
      target.setPitch(controller.getPitch());
      target.headYaw = controller.getYaw();
      int selectedSlot = controller.getInventory().selectedSlot;
      target.getInventory().selectedSlot = selectedSlot;
      controller.setHealth(target.getHealth());
      controller.getHungerManager().setFoodLevel(target.getHungerManager().getFoodLevel());
      controller.getHungerManager().setSaturationLevel(target.getHungerManager().getSaturationLevel());
      float forward = ctrlInputForward;
      float strafe = ctrlInputStrafe;
      boolean sprinting = ctrlInputSprinting;
      boolean shifting = ctrlInputShifting;
      if (controller.handSwinging && controller.handSwingTicks == 0) {
         pendingTargetSwing = true;
      }

      if (controller.currentScreenHandler == controller.playerScreenHandler && target.currentScreenHandler != target.playerScreenHandler) {
         target.closeHandledScreen();
      }

      boolean swing = pendingTargetSwing;
      pendingTargetSwing = false;
      MindControlInputPayload inputPayload = new MindControlInputPayload(
         controller.getYaw(),
         controller.getPitch(),
         forward,
         strafe,
         ctrlInputJumping,
         sprinting,
         shifting,
         selectedSlot,
         swing,
         controller.getX(),
         controller.getY(),
         controller.getZ(),
         ctrlInputInventoryOpen
      );

      try {
         ServerPlayNetworking.send(target, inputPayload);
      } catch (Exception var11) {
      }

      syncInventoryBidirectional(controller, target);
   }

   private static void syncInventoryFromTarget(ServerPlayerEntity controller, ServerPlayerEntity target) {
      PlayerInventory ctrlInv = controller.getInventory();
      PlayerInventory targetInv = target.getInventory();

      for (int i = 0; i < targetInv.size(); i++) {
         ctrlInv.setStack(i, targetInv.getStack(i).copy());
      }

      for (int i = 0; i < 4; i++) {
         ctrlInv.armor.set(i, targetInv.armor.get(i).copy());
      }

      ctrlInv.offHand.set(0, targetInv.offHand.get(0).copy());
      ctrlInv.selectedSlot = targetInv.selectedSlot;
      controller.playerScreenHandler.sendContentUpdates();
   }

   private static void syncInventoryToTarget(ServerPlayerEntity controller, ServerPlayerEntity target) {
      PlayerInventory ctrlInv = controller.getInventory();
      PlayerInventory targetInv = target.getInventory();

      for (int i = 0; i < 9; i++) {
         targetInv.setStack(i, ctrlInv.getStack(i).copy());
      }

      for (int i = 9; i < 36; i++) {
         ItemStack ctrlStack = ctrlInv.getStack(i);
         ItemStack targetStack = targetInv.getStack(i);
         if (!ItemStack.areItemsEqual(ctrlStack, targetStack) || ctrlStack.getCount() != targetStack.getCount()) {
            targetInv.setStack(i, ctrlStack.copy());
         }
      }

      for (int ix = 0; ix < 4; ix++) {
         targetInv.armor.set(ix, ctrlInv.armor.get(ix).copy());
      }

      targetInv.offHand.set(0, ctrlInv.offHand.get(0).copy());
      target.playerScreenHandler.sendContentUpdates();
   }

   private static void syncInventoryBidirectional(ServerPlayerEntity controller, ServerPlayerEntity target) {
      PlayerInventory ctrlInv = controller.getInventory();
      PlayerInventory targetInv = target.getInventory();
      boolean targetChanged = false;
      boolean ctrlChanged = false;
      int size = Math.min(ctrlInv.size(), targetInv.size());

      for (int i = 0; i < Math.min(size, 36); i++) {
         ItemStack ctrlStack = ctrlInv.getStack(i);
         ItemStack targetStack = targetInv.getStack(i);
         if (!ItemStack.areEqual(ctrlStack, targetStack)) {
            boolean itemOrCountChanged = !ItemStack.areItemsEqual(ctrlStack, targetStack) || ctrlStack.getCount() != targetStack.getCount();
            if (itemOrCountChanged) {
               targetInv.setStack(i, ctrlStack.copy());
               targetChanged = true;
            } else {
               ctrlInv.setStack(i, targetStack.copy());
               ctrlChanged = true;
            }
         }
      }

      for (int ix = 0; ix < 4; ix++) {
         ItemStack ctrlArmor = ctrlInv.armor.get(ix);
         ItemStack targetArmor = targetInv.armor.get(ix);
         if (!ItemStack.areEqual(ctrlArmor, targetArmor)) {
            targetInv.armor.set(ix, ctrlArmor.copy());
            targetChanged = true;
         }
      }

      if (!ItemStack.areEqual(ctrlInv.offHand.get(0), targetInv.offHand.get(0))) {
         targetInv.offHand.set(0, ctrlInv.offHand.get(0).copy());
         targetChanged = true;
      }

      if (targetChanged) {
         target.playerScreenHandler.sendContentUpdates();
      }

      if (ctrlChanged) {
         controller.playerScreenHandler.sendContentUpdates();
      }
   }

   private static void tickMobFollow(ServerPlayerEntity controller, LivingEntity target) {
      if (target instanceof MobEntity mob) {
         double dist = mob.distanceTo(controller);
         double speed = dist > 10.0 ? 1.5 : 1.0;
         mob.getNavigation().startMovingTo(controller, speed);
      }
   }

   private static void tickMobKill(ServerPlayerEntity controller, LivingEntity target, MinecraftServer server) {
      if (killVictimUUID == null) {
         activeCommand = GeassManager.CommandType.NONE;
      } else if (((ServerWorld)target.getWorld()).getEntity(killVictimUUID) instanceof LivingEntity victim && !victim.isDead()) {
         if (target instanceof MobEntity mob) {
            mob.setTarget(victim);
            mob.getNavigation().startMovingTo(victim, 1.2);
            mob.getLookControl().lookAt(victim, 30.0F, 30.0F);
            double dist = mob.distanceTo(victim);
            if (dist <= 2.5 && mobAttackCooldown <= 0) {
               if (mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE) != null) {
                  mob.tryAttack(victim);
               } else {
                  victim.damage(mob.getDamageSources().mobAttack(mob), 2.0F);
               }

               mob.swingHand(Hand.MAIN_HAND);
               mobAttackCooldown = 20;
            }

            if (mobAttackCooldown > 0) {
               mobAttackCooldown--;
            }
         }
      } else {
         activeCommand = GeassManager.CommandType.NONE;
         killVictimUUID = null;
         if (target instanceof MobEntity mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
         }

         mobAttackCooldown = 0;
         controller.sendMessage(Text.literal("Target eliminated.").styled(s -> s.withColor(16711680)));
      }
   }

   private static void tickMobFreeze(LivingEntity target) {
      target.requestTeleport(freezeX, freezeY, freezeZ);
      target.setYaw(freezeYaw);
      target.setPitch(freezePitch);
      target.setVelocity(0.0, 0.0, 0.0);
      target.velocityModified = true;
      if (target instanceof MobEntity mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }
   }

   private static void tickStopBreathing(LivingEntity target) {
      int air = target.getAir();
      int newAir = air - 5;
      if (newAir < 0) {
         newAir = 0;
      }

      target.setAir(newAir);
      if (newAir <= 0) {
         airDrainCounter++;
         if (airDrainCounter % 20 == 0) {
            target.damage(target.getDamageSources().drown(), 2.0F);
         }
      }
   }

   private static void tickFreeze(ServerPlayerEntity target) {
      target.teleport((ServerWorld)target.getWorld(), freezeX, freezeY, freezeZ, freezeYaw, freezePitch);
      target.setVelocity(0.0, 0.0, 0.0);
      target.velocityModified = true;
   }

   private static LivingEntity getTargetEntity(MinecraftServer server, ServerPlayerEntity controller) {
      if (selectedTargetUUID == null) {
         return null;
      } else if (targetIsPlayer) {
         return server.getPlayerManager().getPlayer(selectedTargetUUID);
      } else {
         return controller.getServerWorld().getEntity(selectedTargetUUID) instanceof LivingEntity le ? le : null;
      }
   }

   private static Entity findAttackTarget(ServerPlayerEntity target) {
      Vec3d eye = target.getEyePos();
      Vec3d look = target.getRotationVector();
      double reach = 4.5;
      Vec3d end = eye.add(look.multiply(reach));
      Box searchBox = target.getBoundingBox().stretch(look.multiply(reach)).expand(1.0);
      double closestDist = Double.MAX_VALUE;
      Entity closest = null;

      for (Entity entity : target.getServerWorld().getOtherEntities(target, searchBox, e -> e instanceof LivingEntity && !e.isSpectator() && e.canHit())) {
         Box box = entity.getBoundingBox().expand(0.3);
         Optional<Vec3d> hit = box.raycast(eye, end);
         if (hit.isPresent()) {
            double dist = eye.squaredDistanceTo(hit.get());
            if (dist < closestDist && dist < reach * reach) {
               closestDist = dist;
               closest = entity;
            }
         }
      }

      return closest;
   }

   private static void sendControlPacket(ServerPlayerEntity target, int commandType, int targetEntityId) {
      try {
         ServerPlayNetworking.send(target, new GeassControlPayload(commandType, targetEntityId));
      } catch (Exception var4) {
      }
   }

   private static void broadcastLocalChat(ServerPlayerEntity sender, String rawText, MinecraftServer server, double range) {
      MutableText msg = Text.literal("<" + sender.getName().getString() + "> " + rawText);

      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         if (p.distanceTo(sender) <= range || p == sender) {
            p.sendMessage(msg);
         }
      }
   }

   private static void clearAll() {
      controllerUUID = null;
      selectedTargetUUID = null;
      targetIsPlayer = true;
      activeCommand = GeassManager.CommandType.NONE;
      killVictimUUID = null;
      airDrainCounter = 0;
      mobAttackCooldown = 0;
      stealthMode = false;
      sprintJumpTimer = 0;
      wasCtrlSwinging = false;
      ctrlInputForward = 0.0F;
      ctrlInputStrafe = 0.0F;
      ctrlInputJumping = false;
      ctrlInputSprinting = false;
      savedTargetInventory = null;
   }

   private static LivingEntity findEyeContactTarget(ServerPlayerEntity controller) {
      Vec3d eye = controller.getEyePos();
      Vec3d look = controller.getRotationVector();
      Vec3d end = eye.add(look.multiply(64.0));
      Box searchBox = controller.getBoundingBox().stretch(look.multiply(64.0)).expand(1.0);
      double closestDist = Double.MAX_VALUE;
      LivingEntity closest = null;

      for (Entity entity : controller.getServerWorld().getOtherEntities(controller, searchBox, e -> e instanceof LivingEntity && !e.isSpectator())) {
         LivingEntity candidate = (LivingEntity)entity;
         Box box = candidate.getBoundingBox().expand(0.3);
         Optional<Vec3d> hit = box.raycast(eye, end);
         if (hit.isPresent()) {
            double dist = eye.squaredDistanceTo(hit.get());
            if (dist < closestDist) {
               if (candidate instanceof ServerPlayerEntity) {
                  Vec3d targetToController = controller.getPos().subtract(candidate.getPos()).normalize();
                  double dot = candidate.getRotationVector().dotProduct(targetToController);
                  if (dot > 0.3) {
                     closestDist = dist;
                     closest = candidate;
                  }
               } else {
                  closestDist = dist;
                  closest = candidate;
               }
            }
         }
      }

      return closest;
   }

   private static LivingEntity findKillTarget(MinecraftServer server, ServerPlayerEntity controller, String name) {
      ServerPlayerEntity player = findPlayerByName(server, name);
      if (player != null) {
         return player;
      } else {
         Box searchBox = controller.getBoundingBox().expand(64.0);

         for (Entity e : controller.getWorld()
            .getOtherEntities(controller, searchBox, ent -> ent instanceof LivingEntity && !(ent instanceof ServerPlayerEntity))) {
            LivingEntity le = (LivingEntity)e;
            if (le.getName().getString().equalsIgnoreCase(name)) {
               return le;
            }
         }

         return null;
      }
   }

   private static void spawnGeassParticles(ServerPlayerEntity controller) {
      if (!stealthMode) {
         if (activeCommand != GeassManager.CommandType.MIND_CONTROL) {
            if (controller.age % 2 == 0) {
               ServerWorld level = controller.getServerWorld();
               float yawRad = (float)Math.toRadians(controller.getYaw());
               double leftX = Math.cos(yawRad);
               double leftZ = Math.sin(yawRad);
               double fwdX = -Math.sin(yawRad);
               double fwdZ = Math.cos(yawRad);
               double x = controller.getX() + leftX * 0.22 + fwdX * 0.3;
               double y = controller.getEyeY() - 0.05;
               double z = controller.getZ() + leftZ * 0.22 + fwdZ * 0.3;
               level.spawnParticles(RED_DUST, x, y, z, 3, 0.04, 0.04, 0.04, 0.0);
            }
         }
      }
   }

   private static ServerPlayerEntity findPlayerByName(MinecraftServer server, String name) {
      for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
         if (p.getName().getString().equalsIgnoreCase(name)) {
            return p;
         }
      }

      return null;
   }

   public static void handleDisconnect(UUID playerUUID, MinecraftServer server) {
      if (controllerUUID != null && controllerUUID.equals(playerUUID)) {
         ServerPlayerEntity controller = server.getPlayerManager().getPlayer(playerUUID);
         if (controller != null && activeCommand == GeassManager.CommandType.MIND_CONTROL) {
            controller.stopRiding();
            controller.networkHandler.sendPacket(new SetCameraEntityS2CPacket(controller));
            controller.setInvisible(false);
            controller.setInvulnerable(wasCtrlInvulnerable);
         }

         if (selectedTargetUUID != null) {
            if (targetIsPlayer) {
               ServerPlayerEntity target = server.getPlayerManager().getPlayer(selectedTargetUUID);
               if (target != null) {
                  sendControlPacket(target, 0, -1);
               }
            } else {
               for (ServerWorld level : server.getWorlds()) {
                  if (level.getEntity(selectedTargetUUID) instanceof MobEntity mob) {
                     mob.setTarget(null);
                     mob.getNavigation().stop();
                     break;
                  }
               }
            }
         }

         clearAll();
      }

      if (selectedTargetUUID != null && selectedTargetUUID.equals(playerUUID)) {
         ServerPlayerEntity controllerx = controllerUUID != null ? server.getPlayerManager().getPlayer(controllerUUID) : null;
         deactivate(controllerx);
      }
   }

   static enum CommandType {
      NONE,
      FOLLOW,
      KILL,
      STOP_BREATHING,
      FREEZE,
      MIND_CONTROL;
   }
}
