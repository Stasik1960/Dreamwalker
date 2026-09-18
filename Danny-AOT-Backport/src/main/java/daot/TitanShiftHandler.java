package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;

@Environment(EnvType.CLIENT)
public class TitanShiftHandler {
   private static boolean wasRidingTitan = false;
   private static boolean wasRidingPureTitan = false;
   private static boolean wasBeingEaten = false;
   private static boolean eatingVictimPendingDismount = false;
   private static Perspective previousCameraType = Perspective.FIRST_PERSON;
   private static final float PRESHIFT_ZOOM_MULTIPLIER = 2.0F;
   private static final float SHIFT_ZOOM_MULTIPLIER = 15.0F;
   private static final float MOUNT_ZOOM_MULTIPLIER = 17.5F;
   private static final float TARGET_ZOOM_MULTIPLIER = 7.0F;
   private static final float ATTACK_TITAN_ZOOM_MULTIPLIER = 3.5F;
   private static final float PURE_TITAN_ZOOM_MULTIPLIER = 3.0F;
   private static final float BEING_EATEN_ZOOM_MULTIPLIER = 2.5F;
   private static final float CRAWLER_ZOOM_MULTIPLIER = 5.25F;
   private static final float ZOOM_OUT_SMOOTHING = 0.08F;
   private static final float ZOOM_IN_SMOOTHING = 0.16F;
   private static final int POST_TRANSFORM_DELAY_TICKS = 0;
   private static int postTransformDelayTicks = 0;
   private static boolean waitingForPostTransformDelay = false;
   private static float ridingZoomTarget = 7.0F;
   private static boolean sawTransforming = false;
   private static int transformWaitTicks = 0;
   private static final int TRANSFORM_DETECT_GRACE_TICKS = 3;
   private static float lastTickZoom = 1.0F;
   private static float currentTickZoom = 1.0F;
   private static float targetZoom = 1.0F;
   private static boolean zoomOutComplete = false;
   private static boolean isDismountZoom = false;
   private static boolean isShiftingPending = false;
   private static int shiftingPendingTicks = 0;
   private static final int SHIFTING_TIMEOUT_TICKS = 100;
   private static float lastTickCrouchOffset = 0.0F;
   private static float currentTickCrouchOffset = 0.0F;
   private static final float CROUCH_CAMERA_TARGET = -5.0F;
   private static final float CROUCH_CAMERA_LERP = 0.12F;
   private static int rockGrabCameraDelayTicks = 0;
   private static final int ROCK_GRAB_CAMERA_DELAY = 10;
   private static boolean wasPlayerDead = false;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (client.player == null) {
                  resetAll();
               } else {
                  boolean isPlayerDead = client.player.isDead();
                  if (isPlayerDead && !wasPlayerDead) {
                     client.options.setPerspective(previousCameraType);
                     resetAll();
                     wasPlayerDead = true;
                  } else if (wasPlayerDead && !isPlayerDead) {
                     client.options.setPerspective(Perspective.FIRST_PERSON);
                     resetAll();
                     wasPlayerDead = false;
                  } else {
                     wasPlayerDead = isPlayerDead;
                     if (!isPlayerDead) {
                        Entity vehicle = client.player.getVehicle();
                        boolean isRidingColossalTitan = vehicle instanceof ColossalTitanEntity;
                        boolean isRidingAttackTitan = vehicle instanceof AttackTitanEntity;
                        boolean isRidingArmoredTitan = vehicle instanceof ArmoredTitanEntity;
                        boolean isRidingFemaleTitan = vehicle instanceof FemaleTitanEntity;
                        boolean isRidingBeastTitan = vehicle instanceof BeastTitanEntity;
                        boolean isRidingWarhammerTitan = vehicle instanceof WarhammerTitanEntity;
                        boolean isRidingCrawler = vehicle instanceof CrawlerTitanEntity crawler && crawler.isRideable;
                        boolean isRidingPureTitan = vehicle instanceof SmallTitanEntity
                           || vehicle instanceof SmallTitan2Entity
                           || vehicle instanceof FritzTitanEntity
                           || vehicle instanceof TitanEntity
                           || vehicle instanceof ConnieFatherEntity;
                        boolean isRidingTitan = isRidingColossalTitan
                           || isRidingAttackTitan
                           || isRidingArmoredTitan
                           || isRidingFemaleTitan
                           || isRidingBeastTitan
                           || isRidingWarhammerTitan
                           || isRidingCrawler;
                        boolean isRidingAnyTitan = isRidingTitan || isRidingPureTitan;
                        ColossalTitanEntity colossalTitan = isRidingColossalTitan ? (ColossalTitanEntity)vehicle : null;
                        AttackTitanEntity attackTitan = isRidingAttackTitan ? (AttackTitanEntity)vehicle : null;
                        ArmoredTitanEntity armoredTitan = isRidingArmoredTitan ? (ArmoredTitanEntity)vehicle : null;
                        FemaleTitanEntity femaleTitan = isRidingFemaleTitan ? (FemaleTitanEntity)vehicle : null;
                        BeastTitanEntity beastTitan = isRidingBeastTitan ? (BeastTitanEntity)vehicle : null;
                        WarhammerTitanEntity warhammerTitan = isRidingWarhammerTitan ? (WarhammerTitanEntity)vehicle : null;
                        int playerId = client.player.getId();
                        boolean isBeingEaten = false;

                        for (SmallTitanEntity titan : client.world
                           .getNonSpectatingEntities(SmallTitanEntity.class, client.player.getBoundingBox().expand(50.0))) {
                           if (titan.getEatingTargetId() == playerId) {
                              isBeingEaten = true;
                              break;
                           }
                        }

                        if (!isBeingEaten) {
                           for (SmallTitan2Entity titanx : client.world
                              .getNonSpectatingEntities(SmallTitan2Entity.class, client.player.getBoundingBox().expand(50.0))) {
                              if (titanx.getEatingTargetId() == playerId) {
                                 isBeingEaten = true;
                                 break;
                              }
                           }
                        }

                        if (!isBeingEaten) {
                           for (FritzTitanEntity titanxx : client.world
                              .getNonSpectatingEntities(FritzTitanEntity.class, client.player.getBoundingBox().expand(50.0))) {
                              if (titanxx.getEatingTargetId() == playerId) {
                                 isBeingEaten = true;
                                 break;
                              }
                           }
                        }

                        if (!isBeingEaten) {
                           for (ConnieFatherEntity titanxxx : client.world
                              .getNonSpectatingEntities(ConnieFatherEntity.class, client.player.getBoundingBox().expand(50.0))) {
                              if (titanxxx.getEatingTargetId() == playerId) {
                                 isBeingEaten = true;
                                 break;
                              }
                           }
                        }

                        lastTickZoom = currentTickZoom;
                        lastTickCrouchOffset = currentTickCrouchOffset;
                        boolean isCrouchingNow = false;
                        if (attackTitan != null) {
                           isCrouchingNow = attackTitan.isInSneakingPose();
                        } else if (armoredTitan != null) {
                           isCrouchingNow = armoredTitan.isInSneakingPose() || armoredTitan.isInChargeRecovery();
                        } else if (femaleTitan != null) {
                           isCrouchingNow = femaleTitan.isInSneakingPose();
                        } else if (beastTitan != null) {
                           if (beastTitan.getRockThrowPhase() == 1) {
                              rockGrabCameraDelayTicks++;
                              isCrouchingNow = beastTitan.isInSneakingPose() || rockGrabCameraDelayTicks > 10;
                           } else {
                              rockGrabCameraDelayTicks = 0;
                              isCrouchingNow = beastTitan.isInSneakingPose();
                           }
                        } else if (warhammerTitan != null) {
                           isCrouchingNow = warhammerTitan.isInSneakingPose();
                        }

                        float crouchTarget = isCrouchingNow ? -5.0F : 0.0F;
                        currentTickCrouchOffset = currentTickCrouchOffset + (crouchTarget - currentTickCrouchOffset) * 0.12F;
                        if (Math.abs(currentTickCrouchOffset - crouchTarget) < 0.05F) {
                           currentTickCrouchOffset = crouchTarget;
                        }

                        if (isRidingTitan && !wasRidingTitan) {
                           if (!isShiftingPending) {
                              previousCameraType = client.options.getPerspective();
                           }

                           if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
                              client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                           }

                           ridingZoomTarget = isRidingCrawler
                              ? 5.25F
                              : (
                                 !isRidingAttackTitan && !isRidingArmoredTitan && !isRidingFemaleTitan && !isRidingBeastTitan && !isRidingWarhammerTitan
                                    ? 7.0F
                                    : 3.5F
                              );
                           if (isRidingCrawler) {
                              targetZoom = 5.25F;
                              waitingForPostTransformDelay = false;
                           } else {
                              targetZoom = 17.5F;
                           }

                           waitingForPostTransformDelay = true;
                           postTransformDelayTicks = 0;
                           sawTransforming = false;
                           transformWaitTicks = 0;
                           isDismountZoom = false;
                           isShiftingPending = false;
                           shiftingPendingTicks = 0;
                        }

                        if (!isRidingTitan && wasRidingTitan) {
                           client.options.setPerspective(previousCameraType);
                           targetZoom = 1.0F;
                           zoomOutComplete = false;
                           isDismountZoom = false;
                           waitingForPostTransformDelay = false;
                           postTransformDelayTicks = 0;
                        }

                        if (isRidingPureTitan && !wasRidingPureTitan && !isBeingEaten) {
                           if (!isShiftingPending) {
                              previousCameraType = client.options.getPerspective();
                           }

                           if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
                              client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                           }

                           targetZoom = 3.0F;
                           isDismountZoom = false;
                           isShiftingPending = false;
                           shiftingPendingTicks = 0;
                        }

                        if (!isRidingPureTitan && wasRidingPureTitan) {
                           if (eatingVictimPendingDismount) {
                              eatingVictimPendingDismount = false;
                           } else {
                              client.options.setPerspective(previousCameraType);
                              targetZoom = 1.0F;
                              zoomOutComplete = false;
                           }
                        }

                        if (isBeingEaten && !wasBeingEaten) {
                           if (!isShiftingPending && !isRidingTitan) {
                              previousCameraType = client.options.getPerspective();
                           }

                           if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
                              client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                           }

                           targetZoom = 2.5F;
                           zoomOutComplete = false;
                        }

                        if (!isBeingEaten && wasBeingEaten) {
                           client.options.setPerspective(previousCameraType);
                           targetZoom = 1.0F;
                           zoomOutComplete = false;
                           if (isRidingPureTitan) {
                              eatingVictimPendingDismount = true;
                           }
                        }

                        if (isShiftingPending && !isRidingAnyTitan) {
                           shiftingPendingTicks++;
                           if (shiftingPendingTicks > 100) {
                              isShiftingPending = false;
                              shiftingPendingTicks = 0;
                              targetZoom = 1.0F;
                              client.options.setPerspective(previousCameraType);
                           }
                        }

                        if (isRidingTitan && waitingForPostTransformDelay) {
                           boolean titanTransforming = false;
                           if (colossalTitan != null) {
                              titanTransforming = colossalTitan.isTransforming();
                           } else if (attackTitan != null) {
                              titanTransforming = attackTitan.isTransforming();
                           } else if (armoredTitan != null) {
                              titanTransforming = armoredTitan.isTransforming();
                           } else if (femaleTitan != null) {
                              titanTransforming = femaleTitan.isTransforming();
                           } else if (beastTitan != null) {
                              titanTransforming = beastTitan.isTransforming();
                           } else if (warhammerTitan != null) {
                              titanTransforming = warhammerTitan.isTransforming();
                           }

                           if (titanTransforming) {
                              sawTransforming = true;
                              postTransformDelayTicks = 0;
                           } else if (sawTransforming) {
                              postTransformDelayTicks++;
                              if (postTransformDelayTicks >= 0) {
                                 targetZoom = ridingZoomTarget;
                                 waitingForPostTransformDelay = false;
                                 postTransformDelayTicks = 0;
                              }
                           } else {
                              transformWaitTicks++;
                              if (transformWaitTicks > 3) {
                                 targetZoom = ridingZoomTarget;
                                 waitingForPostTransformDelay = false;
                                 postTransformDelayTicks = 0;
                              }
                           }
                        }

                        if (isRidingTitan) {
                           boolean titanDismounting = false;
                           if (colossalTitan != null) {
                              titanDismounting = colossalTitan.isDismounting();
                           } else if (attackTitan != null) {
                              titanDismounting = attackTitan.isDismounting();
                           } else if (armoredTitan != null) {
                              titanDismounting = armoredTitan.isDismounting();
                           } else if (femaleTitan != null) {
                              titanDismounting = femaleTitan.isDismounting();
                           } else if (beastTitan != null) {
                              titanDismounting = beastTitan.isDismounting();
                           } else if (warhammerTitan != null) {
                              titanDismounting = warhammerTitan.isDismounting();
                           }

                           if (titanDismounting && !isDismountZoom) {
                              isDismountZoom = true;
                              targetZoom = 1.0F;
                              if (warhammerTitan != null && warhammerTitan.isCrystalPeeking()) {
                                 client.options.setPerspective(Perspective.FIRST_PERSON);
                              }
                           } else if (!titanDismounting && isDismountZoom) {
                              isDismountZoom = false;
                              targetZoom = isRidingCrawler
                                 ? 5.25F
                                 : (
                                    !isRidingAttackTitan && !isRidingArmoredTitan && !isRidingFemaleTitan && !isRidingBeastTitan && !isRidingWarhammerTitan
                                       ? 7.0F
                                       : 3.5F
                                 );
                              if (isRidingWarhammerTitan) {
                                 client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                              }
                           }
                        }

                        if (Math.abs(currentTickZoom - targetZoom) > 0.001F) {
                           float smoothing = targetZoom < currentTickZoom ? 0.16F : 0.08F;
                           currentTickZoom = currentTickZoom + (targetZoom - currentTickZoom) * smoothing;
                           if (Math.abs(currentTickZoom - targetZoom) < 0.01F) {
                              currentTickZoom = targetZoom;
                           }

                           if (currentTickZoom == targetZoom && isRidingTitan && targetZoom == 7.0F && !isDismountZoom) {
                              zoomOutComplete = true;
                           }
                        }

                        wasRidingTitan = isRidingTitan;
                        wasRidingPureTitan = isRidingPureTitan;
                        wasBeingEaten = isBeingEaten;
                     }
                  }
               }
            }
         );
   }

   private static void resetAll() {
      wasRidingTitan = false;
      wasRidingPureTitan = false;
      wasBeingEaten = false;
      eatingVictimPendingDismount = false;
      lastTickZoom = 1.0F;
      currentTickZoom = 1.0F;
      targetZoom = 1.0F;
      zoomOutComplete = false;
      isDismountZoom = false;
      isShiftingPending = false;
      shiftingPendingTicks = 0;
      lastTickCrouchOffset = 0.0F;
      currentTickCrouchOffset = 0.0F;
      waitingForPostTransformDelay = false;
      postTransformDelayTicks = 0;
      sawTransforming = false;
      transformWaitTicks = 0;
      ridingZoomTarget = 7.0F;
   }

   @Deprecated
   public static void onTitanShiftKeyPressed() {
      onPreshiftStart();
   }

   public static void onPreshiftStart() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         if (!(client.player.getVehicle() instanceof ColossalTitanEntity)
            && !(client.player.getVehicle() instanceof AttackTitanEntity)
            && !(client.player.getVehicle() instanceof ArmoredTitanEntity)
            && !(client.player.getVehicle() instanceof FemaleTitanEntity)
            && !(client.player.getVehicle() instanceof BeastTitanEntity)
            && !(client.player.getVehicle() instanceof WarhammerTitanEntity)) {
            previousCameraType = client.options.getPerspective();
            if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
               client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }

            targetZoom = 2.0F;
            zoomOutComplete = false;
            isShiftingPending = true;
            shiftingPendingTicks = 0;
         }
      }
   }

   public static void onTitanSpawned() {
      targetZoom = 17.5F;
   }

   public static void onTitanMounted() {
      targetZoom = 17.5F;
      isShiftingPending = false;
      shiftingPendingTicks = 0;
   }

   public static boolean isShiftZoomActive() {
      return waitingForPostTransformDelay;
   }

   public static float getInterpolatedZoom(float partialTick) {
      return lastTickZoom + (currentTickZoom - lastTickZoom) * partialTick;
   }

   public static float getInterpolatedCrouchOffset(float partialTick) {
      return lastTickCrouchOffset + (currentTickCrouchOffset - lastTickCrouchOffset) * partialTick;
   }

   public static float getCurrentZoomMultiplier() {
      return currentTickZoom;
   }

   public static boolean isZoomOutComplete() {
      return zoomOutComplete;
   }
}
