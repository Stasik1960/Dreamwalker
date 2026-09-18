package daot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

@Environment(EnvType.CLIENT)
public class CameraShakeHandler {
   private static float shakeIntensity = 0.0F;
   private static int shakeDuration = 0;
   private static float currentShakeX = 0.0F;
   private static float currentShakeY = 0.0F;
   private static float prevShakeX = 0.0F;
   private static float prevShakeY = 0.0F;
   private static float targetShakeX = 0.0F;
   private static float targetShakeY = 0.0F;
   private static final int SHAKE_DURATION_TICKS = 14;
   private static final float MAX_SHAKE_ANGLE = 4.5F;
   private static final float SHAKE_SMOOTHING = 0.2F;
   private static int shakeTickCounter = 0;
   private static float strwsFireValueX = 0.0F;
   private static float strwsFireValueY = 0.0F;
   private static float prevStrwsFireValueX = 0.0F;
   private static float prevStrwsFireValueY = 0.0F;
   private static int strwsFireTicks = 0;
   private static float strwsFireIntensity = 0.0F;
   private static final int STRWS_FIRE_DURATION = 6;
   private static final float STRWS_FIRE_ANGLE = 4.0F;
   private static final float STRWS_FIRE_FREQ = 0.9F;
   private static float rubberbandX = 0.0F;
   private static float rubberbandY = 0.0F;
   private static float prevRubberbandX = 0.0F;
   private static float prevRubberbandY = 0.0F;
   private static float rubberbandTargetX = 0.0F;
   private static float rubberbandTargetY = 0.0F;
   private static int rubberbandTicks = 0;
   private static final int RUBBERBAND_DURATION = 18;
   private static final float RUBBERBAND_MAX_ANGLE = 8.0F;
   private static final float RUBBERBAND_PUSH_SPEED = 0.6F;
   private static final float RUBBERBAND_RETURN_SPEED = 0.12F;
   private static float swingDipValue = 0.0F;
   private static float prevSwingDipValue = 0.0F;
   private static int swingDipTicks = 0;
   private static final int SWING_DIP_DURATION = 8;
   private static final float SWING_DIP_ANGLE = 5.0F;
   private static int thunderDipDirection = 0;
   private static float thunderDipValue = 0.0F;
   private static float prevThunderDipValue = 0.0F;
   private static int thunderDipTicks = 0;
   private static final int THUNDER_DIP_DURATION = 10;
   private static final float THUNDER_DIP_ANGLE = 4.0F;
   private static float gunRecoilValue = 0.0F;
   private static float prevGunRecoilValue = 0.0F;
   private static int gunRecoilTicks = 0;
   private static float gunRecoilAngle = 0.0F;
   private static final int GUN_RECOIL_DURATION = 14;
   private static float landingDipValue = 0.0F;
   private static float prevLandingDipValue = 0.0F;
   private static int landingDipTicks = 0;
   private static float landingDipIntensity = 0.0F;
   private static final int LANDING_DIP_DURATION = 14;
   private static final float LANDING_DIP_MAX_ANGLE = 12.0F;
   private static float wireYankValue = 0.0F;
   private static float prevWireYankValue = 0.0F;
   private static int wireYankTicks = 0;
   private static final int WIRE_YANK_DURATION = 16;
   private static final float WIRE_YANK_ANGLE = 15.0F;
   private static int royalShoutShakeTicks = 0;
   private static final int ROYAL_SHOUT_SHAKE_DURATION = 120;
   private static final float ROYAL_SHOUT_SHAKE_INTENSITY = 1.4F;
   private static final float ROYAL_SHOUT_MAX_ANGLE = 5.0F;
   private static int laserShakeTicks = 0;
   private static final int LASER_SHAKE_KEEPALIVE = 4;
   private static final float LASER_SHAKE_MAX_ANGLE = 0.75F;
   private static final float LASER_SHAKE_PHASE_RATE = 0.12F;
   private static int femaleTitanRoarShakeTicks = 0;
   private static final int FEMALE_TITAN_ROAR_SHAKE_KEEPALIVE = 4;
   private static final float FEMALE_TITAN_ROAR_SHAKE_INTENSITY = 1.4F;
   private static final float FEMALE_TITAN_ROAR_MAX_ANGLE = 5.0F;
   private static final double FEMALE_TITAN_ROAR_SHAKE_RADIUS = 200.0;
   private static int armorPotionShakeTicks = 0;
   private static final int ARMOR_POTION_SHAKE_DURATION = 40;
   private static final float ARMOR_POTION_BOB_ANGLE = 1.5F;
   private static int beastRoarShakeTicks = 0;
   private static final int BEAST_ROAR_SHAKE_KEEPALIVE = 4;
   private static final float BEAST_ROAR_JITTER_AMP = 3.5F;
   private static float beastRoarJitterX = 0.0F;
   private static float beastRoarJitterY = 0.0F;
   private static float prevBeastRoarJitterX = 0.0F;
   private static float prevBeastRoarJitterY = 0.0F;
   private static final Random beastRoarRandom = new Random();
   private static float dodgeLungeX = 0.0F;
   private static float dodgeLungeY = 0.0F;
   private static float prevDodgeLungeX = 0.0F;
   private static float prevDodgeLungeY = 0.0F;
   private static float dodgeLungeAmpX = 0.0F;
   private static float dodgeLungeAmpY = 0.0F;
   private static int dodgeLungeTick = -1;
   private static final int DODGE_LUNGE_DURATION = 14;
   private static final float DODGE_LUNGE_AMP = 4.6F;
   private static int earthquakeShakeTicks = 0;
   private static int earthquakeShakeDuration = 0;
   private static float earthquakeShakeIntensity = 0.0F;
   private static float earthquakeJitterX = 0.0F;
   private static float earthquakeJitterY = 0.0F;
   private static float prevEarthquakeJitterX = 0.0F;
   private static float prevEarthquakeJitterY = 0.0F;
   private static int earthquakeRollEveryTicks = 1;
   private static float earthquakeDecayPow = 1.0F;
   private static final float EARTHQUAKE_JITTER_AMP = 4.5F;
   private static final Random earthquakeRandom = new Random();
   private static int stompImpulseTicks = 0;
   private static float stompImpulseIntensity = 0.0F;
   private static float stompImpulseValueX = 0.0F;
   private static float stompImpulseValueY = 0.0F;
   private static float prevStompImpulseValueX = 0.0F;
   private static float prevStompImpulseValueY = 0.0F;
   private static final int STOMP_IMPULSE_DURATION = 12;
   private static final float STOMP_IMPULSE_DIP = 0.92F;
   private static final float STOMP_IMPULSE_JITTER = 0.23F;
   private static final Random stompImpulseRandom = new Random();
   private static int colossalBurstRemaining = 0;
   private static int colossalBurstNextFireIn = 0;
   private static float colossalBurstIntensity = 0.0F;
   private static final int COLOSSAL_BURST_SPACING_TICKS = 3;
   private static int armoredBurstRemaining = 0;
   private static int armoredBurstNextFireIn = 0;
   private static float armoredBurstIntensity = 0.0F;
   private static final int ARMORED_BURST_SPACING_TICKS = 3;
   private static final double STOMP_THRESHOLD = 0.08;
   private static final double[] TITAN_STOMP_KEYFRAMES = new double[]{0.5, 1.5};
   private static final double CHASE_ANIM_SPEED = 3.0;
   private static final double SHAKE_RADIUS = 40.0;
   private static final double COLOSSAL_SHAKE_RADIUS = 80.0;
   private static final double ATTACK_TITAN_SHAKE_RADIUS = 45.0;
   private static final double ATTACK_IMPACT_SHAKE_RADIUS = 50.0;
   private static final double SMALL_TITAN_SHAKE_RADIUS = 20.0;
   private static final double SMALL_TITAN_CHASE_ANIM_SPEED = 3.0;
   private static final double SMALL_TITAN_2_SHAKE_RADIUS = 30.0;
   private static final double SMALL_TITAN_2_CHASE_ANIM_SPEED = 3.0;
   private static final double FRITZ_TITAN_SHAKE_RADIUS = 40.0;
   private static final double FRITZ_TITAN_CHASE_ANIM_SPEED = 2.0;
   private static final double[] FRITZ_WALK_STOMP_KEYFRAMES = new double[]{0.75, 2.25};
   private static final double[] FRITZ_RUN_STOMP_KEYFRAMES = new double[]{0.375, 1.125};
   private static final Map<Integer, Double> titanLastAnimTime = new HashMap<>();
   private static final Map<Integer, Double> smallTitanLastAnimTime = new HashMap<>();
   private static final Map<Integer, Double> smallTitan2LastAnimTime = new HashMap<>();
   private static final Map<Integer, Double> fritzTitanLastAnimTime = new HashMap<>();
   private static final Map<Integer, Integer> sadTitanLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> colossalLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> colossalLastSeenImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> attackTitanLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> attackTitanLastSeenImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> armoredTitanLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> armoredTitanLastSeenImpactTick = new HashMap<>();
   private static final Map<Integer, Boolean> armoredTitanLastSeenRecovery = new HashMap<>();
   private static final Map<Integer, Integer> attackTitanLastSeenHitTick = new HashMap<>();
   private static final Map<Integer, Integer> armoredTitanLastSeenHitTick = new HashMap<>();
   private static final Map<Integer, Integer> colossalTitanLastSeenHitTick = new HashMap<>();
   private static final Map<Integer, Integer> femaleTitanLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> femaleTitanLastSeenImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> femaleTitanLastSeenHitTick = new HashMap<>();
   private static final Map<Integer, Integer> beastTitanLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> beastTitanLastSeenImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> beastTitanLastSeenGrabImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> beastTitanLastSeenThrowImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> beastTitanLastSeenDismountTick = new HashMap<>();
   private static final Map<Integer, Integer> warhammerTitanLastSeenStompTick = new HashMap<>();
   private static final Map<Integer, Integer> warhammerTitanLastSeenImpactTick = new HashMap<>();
   private static final Map<Integer, Integer> warhammerTitanLastSeenHitTick = new HashMap<>();
   private static final Map<Integer, Integer> warhammerTitanLastSeenLandingTick = new HashMap<>();
   private static final double OGRE_SHAKE_RADIUS = 80.0;

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            tick();
            checkNearbyTitans(client.player);
            checkNearbyColossalTitans(client.player);
            checkNearbyAttackTitans(client.player);
            checkNearbyArmoredTitans(client.player);
            checkNearbyFemaleTitans(client.player);
            checkNearbySmallTitans(client.player);
            checkNearbySmallTitan2s(client.player);
            checkNearbyFritzTitans(client.player);
            checkNearbyBeastTitans(client.player);
            checkNearbyWarhammerTitans(client.player);
            checkNearbyOgreTitans(client.player);
            checkNearbySadTitans(client.player);
         }
      });
   }

   public static void triggerShake(float intensity) {
      shakeIntensity = Math.max(shakeIntensity, intensity);
      shakeDuration = 14;
   }

   public static void triggerFastShake(float intensity) {
      shakeIntensity = Math.max(shakeIntensity, intensity);
      shakeDuration = Math.max(shakeDuration, 7);
   }

   public static void triggerStrwsFireShake(float intensity) {
      strwsFireIntensity = Math.max(strwsFireIntensity, intensity);
      strwsFireTicks = 6;
   }

   public static void triggerEarthquakeShake(int durationTicks, float intensity) {
      triggerEarthquakeShake(durationTicks, intensity, 1);
   }

   public static void triggerEarthquakeShake(int durationTicks, float intensity, int rollEveryTicks) {
      triggerEarthquakeShake(durationTicks, intensity, rollEveryTicks, 1.0F);
   }

   public static void triggerEarthquakeShake(int durationTicks, float intensity, int rollEveryTicks, float decayPow) {
      earthquakeShakeIntensity = Math.max(earthquakeShakeIntensity, intensity);
      earthquakeShakeTicks = Math.max(earthquakeShakeTicks, durationTicks);
      earthquakeShakeDuration = Math.max(earthquakeShakeDuration, durationTicks);
      earthquakeRollEveryTicks = Math.max(1, rollEveryTicks);
      earthquakeDecayPow = decayPow;
   }

   public static void triggerStompImpulse(float intensity) {
      stompImpulseIntensity = Math.max(stompImpulseIntensity, intensity);
      stompImpulseTicks = 12;
   }

   public static void triggerColossalStompBurst(float intensity) {
      colossalBurstRemaining = 3;
      colossalBurstNextFireIn = 0;
      colossalBurstIntensity = Math.max(colossalBurstIntensity, intensity);
   }

   public static void triggerArmoredStompBurst(float intensity) {
      armoredBurstRemaining = 2;
      armoredBurstNextFireIn = 0;
      armoredBurstIntensity = Math.max(armoredBurstIntensity, intensity);
   }

   public static void triggerRoyalShoutShake() {
      royalShoutShakeTicks = 120;
   }

   public static void pingLaserShake() {
      laserShakeTicks = 4;
   }

   public static void triggerArmorPotionShake() {
      armorPotionShakeTicks = 40;
   }

   public static void triggerDodgeLunge(float dodgeDirYaw) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         float playerYaw = mc.player.getYaw();
         float relativeRad = (float)Math.toRadians(dodgeDirYaw - playerYaw);
         dodgeLungeAmpX = (float)Math.sin(relativeRad) * 4.6F;
         dodgeLungeAmpY = (float)(-Math.cos(relativeRad)) * 4.6F * 0.35F;
         dodgeLungeTick = 0;
      }
   }

   public static void pingFemaleTitanRoarShake() {
      femaleTitanRoarShakeTicks = 4;
   }

   public static void pingBeastRoarShake() {
      beastRoarShakeTicks = 4;
   }

   public static void triggerCommandShake() {
      royalShoutShakeTicks = 10;
   }

   public static void triggerSpearDodgeShake() {
      triggerFastShake(0.3F);
   }

   public static void triggerSpearLungeShake() {
      triggerFastShake(0.5F);
   }

   public static void triggerSpearImpactShake() {
      triggerShake(0.8F);
   }

   public static void triggerSpearComboHitShake() {
      triggerFastShake(0.2F);
   }

   public static void triggerSpearComboEndShake() {
      triggerShake(0.6F);
   }

   public static void triggerSpearShockwaveShake() {
      triggerShake(1.0F);
      royalShoutShakeTicks = 30;
   }

   public static void triggerWireYank() {
      wireYankTicks = 16;
      wireYankValue = 0.0F;
      prevWireYankValue = 0.0F;
   }

   public static void triggerSwingDip() {
      swingDipTicks = 8;
   }

   public static void triggerThunderDip(int direction) {
      thunderDipDirection = direction;
      thunderDipTicks = 10;
      thunderDipValue = 0.0F;
      prevThunderDipValue = 0.0F;
   }

   public static void triggerGunRecoilDip(float intensity) {
      gunRecoilAngle = Math.max(gunRecoilAngle, intensity);
      gunRecoilTicks = 14;
   }

   public static void triggerLandingDip(float intensity) {
      landingDipIntensity = Math.min(intensity, 5.0F);
      landingDipTicks = 14;
      landingDipValue = 0.0F;
      prevLandingDipValue = 0.0F;
   }

   public static void triggerRubberbandShake(float hitDirYaw, float intensity) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         float playerYaw = mc.player.getYaw();
         float relativeAngle = hitDirYaw - playerYaw;
         float relativeRad = (float)Math.toRadians(relativeAngle);
         rubberbandTargetX = (float)Math.sin(relativeRad) * 8.0F * intensity;
         rubberbandTargetY = (float)(-Math.cos(relativeRad)) * 8.0F * intensity * 0.4F;
         rubberbandTicks = 18;
      }
   }

   public static void tick() {
      shakeTickCounter++;
      if (colossalBurstRemaining > 0) {
         if (colossalBurstNextFireIn <= 0) {
            triggerStompImpulse(colossalBurstIntensity);
            colossalBurstRemaining--;
            colossalBurstNextFireIn = 3;
            if (colossalBurstRemaining == 0) {
               colossalBurstIntensity = 0.0F;
            }
         } else {
            colossalBurstNextFireIn--;
         }
      }

      if (armoredBurstRemaining > 0) {
         if (armoredBurstNextFireIn <= 0) {
            triggerStompImpulse(armoredBurstIntensity);
            armoredBurstRemaining--;
            armoredBurstNextFireIn = 3;
            if (armoredBurstRemaining == 0) {
               armoredBurstIntensity = 0.0F;
            }
         } else {
            armoredBurstNextFireIn--;
         }
      }

      prevShakeX = currentShakeX;
      prevShakeY = currentShakeY;
      prevRubberbandX = rubberbandX;
      prevRubberbandY = rubberbandY;
      prevSwingDipValue = swingDipValue;
      prevThunderDipValue = thunderDipValue;
      prevWireYankValue = wireYankValue;
      prevGunRecoilValue = gunRecoilValue;
      if (shakeDuration > 0) {
         shakeDuration--;
         float decayFactor = shakeDuration / 14.0F;
         float currentIntensity = shakeIntensity * decayFactor;
         float phase = shakeTickCounter * 0.3F;
         targetShakeX = (float)Math.sin(phase * 2.1) * 4.5F * currentIntensity;
         targetShakeY = (float)Math.cos(phase * 1.4) * 4.5F * currentIntensity * 0.5F;
      } else {
         shakeIntensity = 0.0F;
         targetShakeX = 0.0F;
         targetShakeY = 0.0F;
      }

      if (royalShoutShakeTicks > 0) {
         royalShoutShakeTicks--;
         float phase = shakeTickCounter * 0.25F;
         float rsIntensity = 1.4F;
         if (royalShoutShakeTicks < 20) {
            rsIntensity *= royalShoutShakeTicks / 20.0F;
         }

         targetShakeX = targetShakeX + (float)Math.sin(phase * 1.7) * 5.0F * rsIntensity;
         targetShakeY = targetShakeY + (float)Math.cos(phase * 1.3) * 5.0F * rsIntensity * 0.5F;
      }

      if (laserShakeTicks > 0) {
         laserShakeTicks--;
         float phase = shakeTickCounter * 0.12F;
         targetShakeX = targetShakeX + (float)Math.sin(phase * 1.7) * 0.75F;
         targetShakeY = targetShakeY + (float)Math.cos(phase * 1.3) * 0.75F * 0.5F;
      }

      if (femaleTitanRoarShakeTicks > 0) {
         femaleTitanRoarShakeTicks--;
         float phase = shakeTickCounter * 0.25F;
         float ftIntensity = 1.4F;
         targetShakeX = targetShakeX + (float)Math.sin(phase * 1.7) * 5.0F * ftIntensity;
         targetShakeY = targetShakeY + (float)Math.cos(phase * 1.3) * 5.0F * ftIntensity * 0.5F;
      }

      if (armorPotionShakeTicks > 0) {
         armorPotionShakeTicks--;
         float bobPhase = shakeTickCounter * 0.24F;
         float envelope = 1.0F;
         int total = 40;
         int elapsed = total - armorPotionShakeTicks;
         if (elapsed < 4) {
            envelope = elapsed / 4.0F;
         } else if (armorPotionShakeTicks < 8) {
            envelope = armorPotionShakeTicks / 8.0F;
         }

         targetShakeY = targetShakeY + (float)Math.sin(bobPhase) * 1.5F * envelope;
         targetShakeX = targetShakeX + (float)Math.sin(bobPhase * 0.5) * 1.5F * 0.3F * envelope;
      }

      prevEarthquakeJitterX = earthquakeJitterX;
      prevEarthquakeJitterY = earthquakeJitterY;
      if (earthquakeShakeTicks > 0) {
         float linearEnvelope = earthquakeShakeDuration > 0 ? (float)earthquakeShakeTicks / earthquakeShakeDuration : 0.0F;
         float envelope = earthquakeDecayPow == 1.0F ? linearEnvelope : (float)Math.pow(linearEnvelope, earthquakeDecayPow);
         if (shakeTickCounter % earthquakeRollEveryTicks == 0) {
            float amp = 4.5F * earthquakeShakeIntensity * envelope;
            earthquakeJitterX = (earthquakeRandom.nextFloat() * 2.0F - 1.0F) * amp;
            earthquakeJitterY = (earthquakeRandom.nextFloat() * 2.0F - 1.0F) * amp;
         }

         earthquakeShakeTicks--;
      } else {
         earthquakeJitterX = 0.0F;
         earthquakeJitterY = 0.0F;
         earthquakeShakeIntensity = 0.0F;
         earthquakeShakeDuration = 0;
         earthquakeRollEveryTicks = 1;
         earthquakeDecayPow = 1.0F;
      }

      prevBeastRoarJitterX = beastRoarJitterX;
      prevBeastRoarJitterY = beastRoarJitterY;
      if (beastRoarShakeTicks > 0) {
         beastRoarShakeTicks--;
         beastRoarJitterX = (beastRoarRandom.nextFloat() * 2.0F - 1.0F) * 3.5F;
         beastRoarJitterY = (beastRoarRandom.nextFloat() * 2.0F - 1.0F) * 3.5F;
      } else {
         beastRoarJitterX *= 0.6F;
         beastRoarJitterY *= 0.6F;
         if (Math.abs(beastRoarJitterX) < 0.05F) {
            beastRoarJitterX = 0.0F;
         }

         if (Math.abs(beastRoarJitterY) < 0.05F) {
            beastRoarJitterY = 0.0F;
         }
      }

      prevStompImpulseValueX = stompImpulseValueX;
      prevStompImpulseValueY = stompImpulseValueY;
      if (stompImpulseTicks > 0) {
         int elapsed = 12 - stompImpulseTicks;
         float t = elapsed / 12.0F;
         float envelope = (float)(Math.exp(-3.0 * t) * Math.cos(t * Math.PI * 1.5));
         float baseDip = -0.92F * envelope * stompImpulseIntensity;
         float jitterScale = (1.0F - t) * stompImpulseIntensity;
         float jitterPitch = (stompImpulseRandom.nextFloat() * 2.0F - 1.0F) * 0.23F * jitterScale;
         float jitterYaw = (stompImpulseRandom.nextFloat() * 2.0F - 1.0F) * 0.23F * 0.5F * jitterScale;
         stompImpulseValueX = baseDip + jitterPitch;
         stompImpulseValueY = jitterYaw;
         stompImpulseTicks--;
      } else {
         stompImpulseIntensity = 0.0F;
         stompImpulseValueX = 0.0F;
         stompImpulseValueY = 0.0F;
      }

      prevStrwsFireValueX = strwsFireValueX;
      prevStrwsFireValueY = strwsFireValueY;
      if (strwsFireTicks > 0) {
         strwsFireTicks--;
         float decay = strwsFireTicks / 6.0F;
         float amp = 4.0F * strwsFireIntensity * decay;
         float phase = shakeTickCounter * 0.9F;
         strwsFireValueX = (float)Math.sin(phase * 2.1) * amp;
         strwsFireValueY = (float)Math.cos(phase * 1.4) * amp * 0.6F;
      } else {
         strwsFireValueX *= 0.5F;
         strwsFireValueY *= 0.5F;
         strwsFireIntensity = 0.0F;
         if (Math.abs(strwsFireValueX) < 0.01F) {
            strwsFireValueX = 0.0F;
         }

         if (Math.abs(strwsFireValueY) < 0.01F) {
            strwsFireValueY = 0.0F;
         }
      }

      currentShakeX = currentShakeX + (targetShakeX - currentShakeX) * 0.2F;
      currentShakeY = currentShakeY + (targetShakeY - currentShakeY) * 0.2F;
      prevDodgeLungeX = dodgeLungeX;
      prevDodgeLungeY = dodgeLungeY;
      if (dodgeLungeTick >= 0 && dodgeLungeTick < 14) {
         float t = dodgeLungeTick;
         float omega = (float) (Math.PI / 3);
         float decay = 0.18F;
         float envelope = (float)Math.exp(-decay * t);
         float wave = (float)Math.sin(omega * t);
         dodgeLungeX = dodgeLungeAmpX * envelope * wave;
         dodgeLungeY = dodgeLungeAmpY * envelope * wave;
         dodgeLungeTick++;
      } else if (dodgeLungeTick >= 0) {
         dodgeLungeX *= 0.75F;
         dodgeLungeY *= 0.75F;
         if (Math.abs(dodgeLungeX) < 0.01F && Math.abs(dodgeLungeY) < 0.01F) {
            dodgeLungeX = 0.0F;
            dodgeLungeY = 0.0F;
            dodgeLungeTick = -1;
         }
      }

      if (swingDipTicks > 0) {
         swingDipTicks--;
         float progress = 1.0F - swingDipTicks / 8.0F;
         swingDipValue = (float)(-Math.sin(progress * Math.PI)) * 5.0F;
      } else {
         swingDipValue *= 0.8F;
         if (Math.abs(swingDipValue) < 0.01F) {
            swingDipValue = 0.0F;
         }
      }

      if (thunderDipTicks > 0) {
         thunderDipTicks--;
         float progress = 1.0F - thunderDipTicks / 10.0F;
         thunderDipValue = (float)(-Math.sin(progress * Math.PI)) * 4.0F;
      } else {
         thunderDipValue *= 0.8F;
         if (Math.abs(thunderDipValue) < 0.01F) {
            thunderDipValue = 0.0F;
            thunderDipDirection = 0;
         }
      }

      if (gunRecoilTicks > 0) {
         gunRecoilTicks--;
         float progress = 1.0F - gunRecoilTicks / 14.0F;
         if (progress < 0.22F) {
            float phase = progress / 0.22F;
            gunRecoilValue = gunRecoilAngle * phase;
         } else {
            float phase = (progress - 0.22F) / 0.78F;
            gunRecoilValue = gunRecoilAngle * (1.0F - phase * phase);
         }
      } else {
         gunRecoilValue *= 0.8F;
         if (Math.abs(gunRecoilValue) < 0.01F) {
            gunRecoilValue = 0.0F;
            gunRecoilAngle = 0.0F;
         }
      }

      prevLandingDipValue = landingDipValue;
      if (landingDipTicks > 0) {
         landingDipTicks--;
         float progress = 1.0F - landingDipTicks / 14.0F;
         float dipAngle = 12.0F * Math.min(landingDipIntensity, 3.0F) / 3.0F;
         if (progress < 0.2F) {
            float dipProgress = progress / 0.2F;
            landingDipValue = -dipAngle * dipProgress;
         } else {
            float returnProgress = (progress - 0.2F) / 0.8F;
            landingDipValue = -dipAngle * (1.0F - returnProgress * returnProgress);
         }
      } else {
         landingDipValue *= 0.85F;
         if (Math.abs(landingDipValue) < 0.01F) {
            landingDipValue = 0.0F;
         }
      }

      if (wireYankTicks > 0) {
         wireYankTicks--;
         float progress = 1.0F - wireYankTicks / 16.0F;
         wireYankValue = (float)(Math.sin(progress * Math.PI * 3.0) * 15.0 * (1.0 - progress));
      } else {
         wireYankValue *= 0.8F;
         if (Math.abs(wireYankValue) < 0.01F) {
            wireYankValue = 0.0F;
         }
      }

      if (rubberbandTicks > 0) {
         rubberbandTicks--;
         float progress = 1.0F - rubberbandTicks / 18.0F;
         if (progress < 0.25F) {
            rubberbandX = rubberbandX + (rubberbandTargetX - rubberbandX) * 0.6F;
            rubberbandY = rubberbandY + (rubberbandTargetY - rubberbandY) * 0.6F;
         } else {
            rubberbandX = rubberbandX + (0.0F - rubberbandX) * 0.12F;
            rubberbandY = rubberbandY + (0.0F - rubberbandY) * 0.12F;
         }
      } else {
         rubberbandX *= 0.85F;
         rubberbandY *= 0.85F;
         if (Math.abs(rubberbandX) < 0.01F) {
            rubberbandX = 0.0F;
         }

         if (Math.abs(rubberbandY) < 0.01F) {
            rubberbandY = 0.0F;
         }
      }
   }

   public static float getShakeX() {
      float thunderX = thunderDipDirection == 2 ? thunderDipValue : 0.0F;
      return currentShakeX
         + rubberbandX
         + swingDipValue
         + thunderX
         + landingDipValue
         + wireYankValue
         + gunRecoilValue
         + dodgeLungeY
         + beastRoarJitterX
         + stompImpulseValueX
         + earthquakeJitterX
         + strwsFireValueX;
   }

   public static float getShakeY() {
      float thunderY = thunderDipDirection != 1 && thunderDipDirection != -1 ? 0.0F : thunderDipValue * thunderDipDirection;
      return currentShakeY + rubberbandY + thunderY + dodgeLungeX + beastRoarJitterY + stompImpulseValueY + earthquakeJitterY + strwsFireValueY;
   }

   public static float getShakeX(float partialTick) {
      float shake = prevShakeX + (currentShakeX - prevShakeX) * partialTick;
      float rubber = prevRubberbandX + (rubberbandX - prevRubberbandX) * partialTick;
      float dip = prevSwingDipValue + (swingDipValue - prevSwingDipValue) * partialTick;
      float thunderDip = prevThunderDipValue + (thunderDipValue - prevThunderDipValue) * partialTick;
      float thunderX = thunderDipDirection == 2 ? thunderDip : 0.0F;
      float landDip = prevLandingDipValue + (landingDipValue - prevLandingDipValue) * partialTick;
      float wireYank = prevWireYankValue + (wireYankValue - prevWireYankValue) * partialTick;
      float gunRecoil = prevGunRecoilValue + (gunRecoilValue - prevGunRecoilValue) * partialTick;
      float dodgePitch = prevDodgeLungeY + (dodgeLungeY - prevDodgeLungeY) * partialTick;
      float beastRoar = prevBeastRoarJitterX + (beastRoarJitterX - prevBeastRoarJitterX) * partialTick;
      float stomp = prevStompImpulseValueX + (stompImpulseValueX - prevStompImpulseValueX) * partialTick;
      float quake = prevEarthquakeJitterX + (earthquakeJitterX - prevEarthquakeJitterX) * partialTick;
      float strwsFire = prevStrwsFireValueX + (strwsFireValueX - prevStrwsFireValueX) * partialTick;
      return shake + rubber + dip + thunderX + landDip + wireYank + gunRecoil + dodgePitch + beastRoar + stomp + quake + strwsFire;
   }

   public static float getShakeY(float partialTick) {
      float shake = prevShakeY + (currentShakeY - prevShakeY) * partialTick;
      float rubber = prevRubberbandY + (rubberbandY - prevRubberbandY) * partialTick;
      float thunderDip = prevThunderDipValue + (thunderDipValue - prevThunderDipValue) * partialTick;
      float thunderY = thunderDipDirection != 1 && thunderDipDirection != -1 ? 0.0F : thunderDip * thunderDipDirection;
      float dodgeYaw = prevDodgeLungeX + (dodgeLungeX - prevDodgeLungeX) * partialTick;
      float beastRoar = prevBeastRoarJitterY + (beastRoarJitterY - prevBeastRoarJitterY) * partialTick;
      float stomp = prevStompImpulseValueY + (stompImpulseValueY - prevStompImpulseValueY) * partialTick;
      float quake = prevEarthquakeJitterY + (earthquakeJitterY - prevEarthquakeJitterY) * partialTick;
      float strwsFire = prevStrwsFireValueY + (strwsFireValueY - prevStrwsFireValueY) * partialTick;
      return shake + rubber + thunderY + dodgeYaw + beastRoar + stomp + quake + strwsFire;
   }

   private static boolean isAtTitanStompKeyframe(double animTime) {
      for (double keyframe : TITAN_STOMP_KEYFRAMES) {
         double diff = Math.abs(animTime % 2.0 - keyframe);
         if (diff < 0.08) {
            return true;
         }
      }

      return false;
   }

   private static double getTitanAnimationTime(TitanEntity titan) {
      double animSpeed = titan.isAttacking() ? 3.0 : 1.0;
      double secondsPerCycle = 2.0 / animSpeed;
      double ticksPerCycle = secondsPerCycle * 20.0;
      long animTick = titan.age;
      return animTick % ticksPerCycle / 20.0 * animSpeed;
   }

   public static void checkNearbyTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         Set<Integer> currentTitans = new HashSet<>();
         TitanEntity closestTitan = null;
         double closestDistance = 40.0;
         boolean closestTitanShouldStomp = false;

         for (TitanEntity titan : player.getWorld().getNonSpectatingEntities(TitanEntity.class, player.getBoundingBox().expand(40.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            if (distance < 40.0) {
               boolean isMoving = titan.getVelocity().horizontalLengthSquared() > 0.001;
               if (isMoving) {
                  double currentAnimTime = getTitanAnimationTime(titan);
                  Double lastAnimTime = titanLastAnimTime.get(titanId);
                  boolean shouldStomp = false;
                  if (lastAnimTime != null && isAtTitanStompKeyframe(currentAnimTime) && !isAtTitanStompKeyframe(lastAnimTime)) {
                     shouldStomp = true;
                  }

                  titanLastAnimTime.put(titanId, currentAnimTime);
                  if (shouldStomp && distance < closestDistance) {
                     closestTitan = titan;
                     closestDistance = distance;
                     closestTitanShouldStomp = true;
                  }
               }
            }
         }

         if (closestTitanShouldStomp && closestTitan != null) {
            float normalizedDistance = (float)(closestDistance / 40.0);
            float intensity = (1.0F - normalizedDistance * normalizedDistance) * 0.6F;
            triggerStompImpulse(intensity);
         }

         titanLastAnimTime.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyColossalTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         boolean isRidingColossal = player.getVehicle() instanceof ColossalTitanEntity;
         ColossalTitanEntity riddenTitan = isRidingColossal ? (ColossalTitanEntity)player.getVehicle() : null;
         Set<Integer> currentTitans = new HashSet<>();
         ColossalTitanEntity closestStompingTitan = null;
         double closestDistance = 80.0;
         ColossalTitanEntity closestHitTitan = null;
         double closestHitDistance = 80.0;
         ColossalTitanEntity closestImpactTitan = null;
         double closestImpactDistance = 80.0;

         for (ColossalTitanEntity titan : player.getWorld().getNonSpectatingEntities(ColossalTitanEntity.class, player.getBoundingBox().expand(80.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = colossalLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            colossalLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped) {
               if (titan == riddenTitan) {
                  closestStompingTitan = titan;
                  closestDistance = 0.0;
               } else if (distance < closestDistance) {
                  closestStompingTitan = titan;
                  closestDistance = distance;
               }
            }

            int currentHitTick = titan.getLastHitTick();
            Integer lastSeenHitTick = colossalTitanLastSeenHitTick.get(titanId);
            boolean justHit = lastSeenHitTick != null && currentHitTick != lastSeenHitTick && currentHitTick > 0;
            if (lastSeenHitTick == null && currentHitTick > 0) {
               justHit = titan.age - currentHitTick < 5;
            }

            colossalTitanLastSeenHitTick.put(titanId, currentHitTick);
            if (justHit) {
               if (titan == riddenTitan) {
                  closestHitTitan = titan;
                  closestHitDistance = 0.0;
               } else if (distance < closestHitDistance) {
                  closestHitTitan = titan;
                  closestHitDistance = distance;
               }
            }

            int currentImpactTick = titan.getLastAttackImpactTick();
            Integer lastSeenImpactTick = colossalLastSeenImpactTick.get(titanId);
            boolean justImpacted = lastSeenImpactTick != null && currentImpactTick != lastSeenImpactTick && currentImpactTick > 0;
            if (lastSeenImpactTick == null && currentImpactTick > 0) {
               justImpacted = titan.age - currentImpactTick < 5;
            }

            colossalLastSeenImpactTick.put(titanId, currentImpactTick);
            if (justImpacted) {
               if (titan == riddenTitan) {
                  closestImpactTitan = titan;
                  closestImpactDistance = 0.0;
               } else if (distance < closestImpactDistance) {
                  closestImpactTitan = titan;
                  closestImpactDistance = distance;
               }
            }
         }

         if (closestStompingTitan != null) {
            float intensity;
            if (closestStompingTitan == riddenTitan) {
               intensity = 1.0F;
            } else {
               float normalizedDistance = (float)(closestDistance / 80.0);
               intensity = Math.max(0.0F, 1.8F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerColossalStompBurst(intensity);
         }

         if (closestImpactTitan != null) {
            float intensity;
            if (closestImpactTitan == riddenTitan) {
               intensity = 1.0F;
            } else {
               float normalizedDistance = (float)(closestImpactDistance / 80.0);
               intensity = Math.max(0.0F, 2.4F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.5F);
            }

            triggerColossalStompBurst(intensity);
         }

         if (closestHitTitan != null) {
            float intensity;
            if (closestHitTitan == riddenTitan) {
               intensity = 0.9F;
            } else {
               float normalizedDistance = (float)(closestHitDistance / 80.0);
               intensity = Math.max(0.0F, 0.8F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 0.6F);
            }

            triggerRubberbandShake(closestHitTitan.getHitDirYaw(), intensity);
         }

         for (ColossalTitanEntity titan : player.getWorld().getNonSpectatingEntities(ColossalTitanEntity.class, player.getBoundingBox().expand(80.0))) {
            if (titan.isSteaming()) {
               double distancex = player.distanceTo(titan);
               float intensity;
               if (titan == riddenTitan) {
                  intensity = 0.5F;
               } else {
                  if (!(distancex < 80.0)) {
                     continue;
                  }

                  float normalizedDistance = (float)(distancex / 80.0);
                  intensity = Math.max(0.0F, 1.5F - normalizedDistance * normalizedDistance);
                  intensity = Math.min(intensity, 0.8F);
               }

               triggerStompImpulse(intensity);
               break;
            }
         }

         for (ColossalTitanEntity titanx : player.getWorld().getNonSpectatingEntities(ColossalTitanEntity.class, player.getBoundingBox().expand(80.0))) {
            int shakeLevel = titanx.getArmDragShake();
            if (shakeLevel > 0) {
               double distancex = player.distanceTo(titanx);
               float baseIntensity = shakeLevel >= 2 ? 1.0F : 0.4F;
               float intensity;
               if (titanx == riddenTitan) {
                  intensity = baseIntensity;
               } else {
                  if (!(distancex < 80.0)) {
                     continue;
                  }

                  float normalizedDistance = (float)(distancex / 80.0);
                  intensity = baseIntensity * Math.max(0.0F, 1.0F - normalizedDistance * normalizedDistance);
               }

               if (!(intensity <= 0.01F)) {
                  triggerEarthquakeShake(6, intensity);
                  break;
               }
            }
         }

         colossalLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
         colossalTitanLastSeenHitTick.keySet().removeIf(id -> !currentTitans.contains(id));
         colossalLastSeenImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyAttackTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         boolean isRidingAttack = player.getVehicle() instanceof AttackTitanEntity;
         AttackTitanEntity riddenTitan = isRidingAttack ? (AttackTitanEntity)player.getVehicle() : null;
         Set<Integer> currentTitans = new HashSet<>();
         AttackTitanEntity closestStompingTitan = null;
         double closestStompDistance = 45.0;
         boolean stompWasRunning = false;
         AttackTitanEntity closestImpactTitan = null;
         double closestImpactDistance = 50.0;
         int impactAttackNumber = 0;
         AttackTitanEntity closestHitTitan = null;
         double closestHitDistance = 50.0;

         for (AttackTitanEntity titan : player.getWorld()
            .getNonSpectatingEntities(AttackTitanEntity.class, player.getBoundingBox().expand(Math.max(45.0, 50.0)))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = attackTitanLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            attackTitanLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped && distance < 45.0) {
               if (titan == riddenTitan) {
                  closestStompingTitan = titan;
                  closestStompDistance = 0.0;
                  stompWasRunning = titan.isSprinting();
               } else if (distance < closestStompDistance) {
                  closestStompingTitan = titan;
                  closestStompDistance = distance;
                  stompWasRunning = titan.isSprinting();
               }
            }

            int currentImpactTick = titan.getLastAttackImpactTick();
            Integer lastSeenImpactTick = attackTitanLastSeenImpactTick.get(titanId);
            boolean justImpacted = lastSeenImpactTick != null && currentImpactTick != lastSeenImpactTick && currentImpactTick > 0;
            if (lastSeenImpactTick == null && currentImpactTick > 0) {
               justImpacted = titan.age - currentImpactTick < 5;
            }

            attackTitanLastSeenImpactTick.put(titanId, currentImpactTick);
            if (justImpacted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestImpactTitan = titan;
                  closestImpactDistance = 0.0;
                  impactAttackNumber = titan.getAttackNumber();
               } else if (distance < closestImpactDistance) {
                  closestImpactTitan = titan;
                  closestImpactDistance = distance;
                  impactAttackNumber = titan.getAttackNumber();
               }
            }

            int currentHitTick = titan.getLastHitTick();
            Integer lastSeenHitTick = attackTitanLastSeenHitTick.get(titanId);
            boolean justHit = lastSeenHitTick != null && currentHitTick != lastSeenHitTick && currentHitTick > 0;
            if (lastSeenHitTick == null && currentHitTick > 0) {
               justHit = titan.age - currentHitTick < 5;
            }

            attackTitanLastSeenHitTick.put(titanId, currentHitTick);
            if (justHit && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestHitTitan = titan;
                  closestHitDistance = 0.0;
               } else if (distance < closestHitDistance) {
                  closestHitTitan = titan;
                  closestHitDistance = distance;
               }
            }
         }

         if (closestStompingTitan != null) {
            boolean riderHandledByKeyframes = closestStompingTitan == riddenTitan
               && closestStompingTitan instanceof AttackTitanEntity
               && closestStompingTitan.usesKeyframeFootsteps()
               && !stompWasRunning;
            if (!riderHandledByKeyframes) {
               float intensity;
               if (closestStompingTitan == riddenTitan) {
                  intensity = stompWasRunning ? 0.9F : 0.5F;
               } else {
                  float normalizedDistance = (float)(closestStompDistance / 45.0);
                  float baseIntensity = stompWasRunning ? 1.5F : 1.0F;
                  intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
                  intensity = Math.min(intensity, 1.0F);
               }

               triggerStompImpulse(intensity);
            }
         }

         if (closestImpactTitan != null) {
            float baseIntensity = impactAttackNumber == 3 ? 1.5F : 1.2F;
            float intensity;
            if (closestImpactTitan == riddenTitan) {
               intensity = impactAttackNumber == 3 ? 1.0F : 0.8F;
            } else {
               float normalizedDistance = (float)(closestImpactDistance / 50.0);
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestHitTitan != null) {
            float intensity;
            if (closestHitTitan == riddenTitan) {
               intensity = 0.9F;
            } else {
               float normalizedDistance = (float)(closestHitDistance / 50.0);
               intensity = Math.max(0.0F, 0.8F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 0.6F);
            }

            triggerRubberbandShake(closestHitTitan.getHitDirYaw(), intensity);
         }

         currentTitans.forEach(id -> {});
         attackTitanLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
         attackTitanLastSeenImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         attackTitanLastSeenHitTick.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyArmoredTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         boolean isRidingArmored = player.getVehicle() instanceof ArmoredTitanEntity;
         ArmoredTitanEntity riddenTitan = isRidingArmored ? (ArmoredTitanEntity)player.getVehicle() : null;
         Set<Integer> currentTitans = new HashSet<>();
         ArmoredTitanEntity closestStompingTitan = null;
         double closestStompDistance = 45.0;
         boolean stompWasRunning = false;
         ArmoredTitanEntity closestImpactTitan = null;
         double closestImpactDistance = 50.0;
         int impactAttackNumber = 0;
         ArmoredTitanEntity closestHitTitan = null;
         double closestHitDistance = 50.0;

         for (ArmoredTitanEntity titan : player.getWorld()
            .getNonSpectatingEntities(ArmoredTitanEntity.class, player.getBoundingBox().expand(Math.max(45.0, 50.0)))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = armoredTitanLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            armoredTitanLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped && distance < 45.0) {
               if (titan == riddenTitan) {
                  closestStompingTitan = titan;
                  closestStompDistance = 0.0;
                  stompWasRunning = titan.isSprinting();
               } else if (distance < closestStompDistance) {
                  closestStompingTitan = titan;
                  closestStompDistance = distance;
                  stompWasRunning = titan.isSprinting();
               }
            }

            int currentImpactTick = titan.getLastAttackImpactTick();
            Integer lastSeenImpactTick = armoredTitanLastSeenImpactTick.get(titanId);
            boolean justImpacted = lastSeenImpactTick != null && currentImpactTick != lastSeenImpactTick && currentImpactTick > 0;
            if (lastSeenImpactTick == null && currentImpactTick > 0) {
               justImpacted = titan.age - currentImpactTick < 5;
            }

            armoredTitanLastSeenImpactTick.put(titanId, currentImpactTick);
            if (justImpacted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestImpactTitan = titan;
                  closestImpactDistance = 0.0;
                  impactAttackNumber = titan.getAttackNumber();
               } else if (distance < closestImpactDistance) {
                  closestImpactTitan = titan;
                  closestImpactDistance = distance;
                  impactAttackNumber = titan.getAttackNumber();
               }
            }

            int currentHitTick = titan.getLastHitTick();
            Integer lastSeenHitTick = armoredTitanLastSeenHitTick.get(titanId);
            boolean justHit = lastSeenHitTick != null && currentHitTick != lastSeenHitTick && currentHitTick > 0;
            if (lastSeenHitTick == null && currentHitTick > 0) {
               justHit = titan.age - currentHitTick < 5;
            }

            armoredTitanLastSeenHitTick.put(titanId, currentHitTick);
            if (justHit && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestHitTitan = titan;
                  closestHitDistance = 0.0;
               } else if (distance < closestHitDistance) {
                  closestHitTitan = titan;
                  closestHitDistance = distance;
               }
            }

            boolean recovering = titan.isInChargeRecovery();
            Boolean lastSeenRecovery = armoredTitanLastSeenRecovery.put(titanId, recovering);
            if (recovering && (lastSeenRecovery == null || !lastSeenRecovery)) {
               double quakeRadius = 100.0;
               if (distance < quakeRadius) {
                  float falloff = 1.0F - (float)(distance / quakeRadius);
                  triggerEarthquakeShake(18, 0.5F * falloff, 1, 1.5F);
               }
            }

            int smashTick = titan.getLastChargeSmashTick();
            boolean smashingNow = smashTick > 0 && titan.age - smashTick <= 2;
            if (smashingNow) {
               double smashRadius = 60.0;
               if (titan == riddenTitan) {
                  triggerEarthquakeShake(6, 0.6F, 1, 1.0F);
               } else if (distance < smashRadius) {
                  float falloff = 1.0F - (float)(distance / smashRadius);
                  triggerEarthquakeShake(6, 0.6F * falloff * falloff, 1, 1.0F);
               }
            }
         }

         if (closestStompingTitan != null) {
            float intensity;
            if (closestStompingTitan == riddenTitan) {
               intensity = stompWasRunning ? 0.9F : 0.5F;
            } else {
               float normalizedDistance = (float)(closestStompDistance / 45.0);
               float baseIntensity = stompWasRunning ? 1.5F : 1.0F;
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerArmoredStompBurst(intensity * 2.1F);
            if (closestStompingTitan.isConsciousnessTransferActive()) {
               float quake = closestStompingTitan == riddenTitan ? 1.8F : Math.min(1.8F, intensity * 1.8F);
               triggerEarthquakeShake(20, quake, 1, 1.5F);
            }
         }

         if (closestImpactTitan != null) {
            float baseIntensity = impactAttackNumber == 3 ? 1.5F : 1.2F;
            float intensityx;
            if (closestImpactTitan == riddenTitan) {
               intensityx = impactAttackNumber == 3 ? 1.0F : 0.8F;
            } else {
               float normalizedDistance = (float)(closestImpactDistance / 50.0);
               intensityx = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensityx = Math.min(intensityx, 1.0F);
            }

            triggerStompImpulse(intensityx);
            if (impactAttackNumber == 5) {
               float quake = closestImpactTitan == riddenTitan ? 1.8F : Math.min(1.8F, intensityx * 1.8F);
               triggerEarthquakeShake(24, quake, 1, 1.5F);
            }
         }

         if (closestHitTitan != null) {
            float intensityxx;
            if (closestHitTitan == riddenTitan) {
               intensityxx = 0.9F;
            } else {
               float normalizedDistance = (float)(closestHitDistance / 50.0);
               intensityxx = Math.max(0.0F, 0.8F - normalizedDistance * normalizedDistance);
               intensityxx = Math.min(intensityxx, 0.6F);
            }

            triggerRubberbandShake(closestHitTitan.getHitDirYaw(), intensityxx);
         }

         armoredTitanLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
         armoredTitanLastSeenImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         armoredTitanLastSeenHitTick.keySet().removeIf(id -> !currentTitans.contains(id));
         armoredTitanLastSeenRecovery.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyFemaleTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         boolean isRidingFemale = player.getVehicle() instanceof FemaleTitanEntity;
         FemaleTitanEntity riddenTitan = isRidingFemale ? (FemaleTitanEntity)player.getVehicle() : null;
         Set<Integer> currentTitans = new HashSet<>();
         FemaleTitanEntity closestStompingTitan = null;
         double closestStompDistance = 45.0;
         boolean stompWasRunning = false;
         FemaleTitanEntity closestImpactTitan = null;
         double closestImpactDistance = 50.0;
         int impactAttackNumber = 0;
         FemaleTitanEntity closestHitTitan = null;
         double closestHitDistance = 50.0;

         for (FemaleTitanEntity titan : player.getWorld()
            .getNonSpectatingEntities(FemaleTitanEntity.class, player.getBoundingBox().expand(Math.max(200.0, Math.max(45.0, 50.0))))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            if (titan.isRoaring() && distance < 200.0) {
               int elapsed = titan.age - titan.getRoarStartTick();
               if (elapsed >= FemaleTitanEntity.getRoarSoundDelayTicks() && elapsed < FemaleTitanEntity.getRoarDurationTicks()) {
                  pingFemaleTitanRoarShake();
               }
            }

            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = femaleTitanLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            femaleTitanLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped && distance < 45.0) {
               if (titan == riddenTitan) {
                  closestStompingTitan = titan;
                  closestStompDistance = 0.0;
                  stompWasRunning = titan.isSprinting();
               } else if (distance < closestStompDistance) {
                  closestStompingTitan = titan;
                  closestStompDistance = distance;
                  stompWasRunning = titan.isSprinting();
               }
            }

            int currentImpactTick = titan.getLastAttackImpactTick();
            Integer lastSeenImpactTick = femaleTitanLastSeenImpactTick.get(titanId);
            boolean justImpacted = lastSeenImpactTick != null && currentImpactTick != lastSeenImpactTick && currentImpactTick > 0;
            if (lastSeenImpactTick == null && currentImpactTick > 0) {
               justImpacted = titan.age - currentImpactTick < 5;
            }

            femaleTitanLastSeenImpactTick.put(titanId, currentImpactTick);
            if (justImpacted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestImpactTitan = titan;
                  closestImpactDistance = 0.0;
                  impactAttackNumber = titan.getAttackNumber();
               } else if (distance < closestImpactDistance) {
                  closestImpactTitan = titan;
                  closestImpactDistance = distance;
                  impactAttackNumber = titan.getAttackNumber();
               }
            }

            int currentHitTick = titan.getLastHitTick();
            Integer lastSeenHitTick = femaleTitanLastSeenHitTick.get(titanId);
            boolean justHit = lastSeenHitTick != null && currentHitTick != lastSeenHitTick && currentHitTick > 0;
            if (lastSeenHitTick == null && currentHitTick > 0) {
               justHit = titan.age - currentHitTick < 5;
            }

            femaleTitanLastSeenHitTick.put(titanId, currentHitTick);
            if (justHit && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestHitTitan = titan;
                  closestHitDistance = 0.0;
               } else if (distance < closestHitDistance) {
                  closestHitTitan = titan;
                  closestHitDistance = distance;
               }
            }
         }

         if (closestStompingTitan != null) {
            float intensity;
            if (closestStompingTitan == riddenTitan) {
               intensity = stompWasRunning ? 0.9F : 0.5F;
            } else {
               float normalizedDistance = (float)(closestStompDistance / 45.0);
               float baseIntensity = stompWasRunning ? 1.5F : 1.0F;
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestImpactTitan != null) {
            float baseIntensity = impactAttackNumber == 3 ? 1.5F : 1.2F;
            float intensity;
            if (closestImpactTitan == riddenTitan) {
               intensity = impactAttackNumber == 3 ? 1.0F : 0.8F;
            } else {
               float normalizedDistance = (float)(closestImpactDistance / 50.0);
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestHitTitan != null) {
            float intensity;
            if (closestHitTitan == riddenTitan) {
               intensity = 0.9F;
            } else {
               float normalizedDistance = (float)(closestHitDistance / 50.0);
               intensity = Math.max(0.0F, 0.8F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 0.6F);
            }

            triggerRubberbandShake(closestHitTitan.getHitDirYaw(), intensity);
         }

         femaleTitanLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
         femaleTitanLastSeenImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         femaleTitanLastSeenHitTick.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   private static double getSmallTitanAnimationTime(SmallTitanEntity titan) {
      double animSpeed = titan.isAttacking() ? 3.0 : 1.0;
      double secondsPerCycle = 2.0 / animSpeed;
      double ticksPerCycle = secondsPerCycle * 20.0;
      long animTick = titan.age;
      return animTick % ticksPerCycle / 20.0 * animSpeed;
   }

   public static void checkNearbySmallTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         Set<Integer> currentTitans = new HashSet<>();
         SmallTitanEntity closestTitan = null;
         double closestDistance = 20.0;
         boolean closestTitanShouldStomp = false;

         for (SmallTitanEntity titan : player.getWorld().getNonSpectatingEntities(SmallTitanEntity.class, player.getBoundingBox().expand(20.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            if (distance < 20.0) {
               boolean isMoving = titan.getVelocity().horizontalLengthSquared() > 0.001;
               if (isMoving) {
                  double currentAnimTime = getSmallTitanAnimationTime(titan);
                  Double lastAnimTime = smallTitanLastAnimTime.get(titanId);
                  boolean shouldStomp = false;
                  if (lastAnimTime != null && isAtTitanStompKeyframe(currentAnimTime) && !isAtTitanStompKeyframe(lastAnimTime)) {
                     shouldStomp = true;
                  }

                  smallTitanLastAnimTime.put(titanId, currentAnimTime);
                  if (shouldStomp && distance < closestDistance) {
                     closestTitan = titan;
                     closestDistance = distance;
                     closestTitanShouldStomp = true;
                  }
               }
            }
         }

         if (closestTitanShouldStomp && closestTitan != null) {
            float normalizedDistance = (float)(closestDistance / 20.0);
            float intensity = (1.0F - normalizedDistance * normalizedDistance) * 0.5F;
            triggerStompImpulse(intensity);
         }

         smallTitanLastAnimTime.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbySadTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         Set<Integer> currentTitans = new HashSet<>();
         SadTitanEntity closestStomping = null;
         double closestDistance = 20.0;

         for (SadTitanEntity titan : player.getWorld().getNonSpectatingEntities(SadTitanEntity.class, player.getBoundingBox().expand(20.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = sadTitanLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            sadTitanLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped && distance < closestDistance) {
               closestStomping = titan;
               closestDistance = distance;
            }
         }

         if (closestStomping != null) {
            float normalizedDistance = (float)(closestDistance / 20.0);
            float intensity = (1.0F - normalizedDistance * normalizedDistance) * 0.5F;
            triggerStompImpulse(intensity);
         }

         sadTitanLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   private static double getSmallTitan2AnimationTime(SmallTitan2Entity titan) {
      double animSpeed = titan.isAttacking() ? 3.0 : 1.0;
      double secondsPerCycle = 2.0 / animSpeed;
      double ticksPerCycle = secondsPerCycle * 20.0;
      long animTick = titan.age;
      return animTick % ticksPerCycle / 20.0 * animSpeed;
   }

   public static void checkNearbySmallTitan2s(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         Set<Integer> currentTitans = new HashSet<>();
         SmallTitan2Entity closestTitan = null;
         double closestDistance = 30.0;
         boolean closestTitanShouldStomp = false;

         for (SmallTitan2Entity titan : player.getWorld().getNonSpectatingEntities(SmallTitan2Entity.class, player.getBoundingBox().expand(30.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            if (distance < 30.0) {
               boolean isMoving = titan.getVelocity().horizontalLengthSquared() > 0.001;
               if (isMoving) {
                  double currentAnimTime = getSmallTitan2AnimationTime(titan);
                  Double lastAnimTime = smallTitan2LastAnimTime.get(titanId);
                  boolean shouldStomp = false;
                  if (lastAnimTime != null && isAtTitanStompKeyframe(currentAnimTime) && !isAtTitanStompKeyframe(lastAnimTime)) {
                     shouldStomp = true;
                  }

                  smallTitan2LastAnimTime.put(titanId, currentAnimTime);
                  if (shouldStomp && distance < closestDistance) {
                     closestTitan = titan;
                     closestDistance = distance;
                     closestTitanShouldStomp = true;
                  }
               }
            }
         }

         if (closestTitanShouldStomp && closestTitan != null) {
            float normalizedDistance = (float)(closestDistance / 30.0);
            float intensity = (1.0F - normalizedDistance * normalizedDistance) * 0.7F;
            triggerStompImpulse(intensity);
         }

         smallTitan2LastAnimTime.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   private static double getFritzTitanAnimationTime(FritzTitanEntity titan) {
      boolean aggressive = titan.isAttacking();
      double baseCycleLength = aggressive ? 1.5 : 3.0;
      double animSpeed = aggressive ? 2.0 : 1.0;
      double secondsPerCycle = baseCycleLength / animSpeed;
      double ticksPerCycle = secondsPerCycle * 20.0;
      long animTick = titan.age;
      return animTick % ticksPerCycle / 20.0 * animSpeed;
   }

   private static boolean isAtFritzStompKeyframe(double animTime, boolean aggressive) {
      double[] keyframes = aggressive ? FRITZ_RUN_STOMP_KEYFRAMES : FRITZ_WALK_STOMP_KEYFRAMES;
      double cycleMod = aggressive ? 1.5 : 3.0;

      for (double keyframe : keyframes) {
         double diff = Math.abs(animTime % cycleMod - keyframe);
         if (diff < 0.08) {
            return true;
         }
      }

      return false;
   }

   public static void checkNearbyFritzTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         Set<Integer> currentTitans = new HashSet<>();
         FritzTitanEntity closestTitan = null;
         double closestDistance = 40.0;
         boolean closestTitanShouldStomp = false;

         for (FritzTitanEntity titan : player.getWorld().getNonSpectatingEntities(FritzTitanEntity.class, player.getBoundingBox().expand(40.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            if (distance < 40.0) {
               boolean isMoving = titan.getVelocity().horizontalLengthSquared() > 0.001;
               if (isMoving) {
                  double currentAnimTime = getFritzTitanAnimationTime(titan);
                  Double lastAnimTime = fritzTitanLastAnimTime.get(titanId);
                  boolean aggressive = titan.isAttacking();
                  boolean shouldStomp = false;
                  if (lastAnimTime != null && isAtFritzStompKeyframe(currentAnimTime, aggressive) && !isAtFritzStompKeyframe(lastAnimTime, aggressive)) {
                     shouldStomp = true;
                  }

                  fritzTitanLastAnimTime.put(titanId, currentAnimTime);
                  if (shouldStomp && distance < closestDistance) {
                     closestTitan = titan;
                     closestDistance = distance;
                     closestTitanShouldStomp = true;
                  }
               }
            }
         }

         if (closestTitanShouldStomp && closestTitan != null) {
            float normalizedDistance = (float)(closestDistance / 40.0);
            float intensity = (1.0F - normalizedDistance * normalizedDistance) * 0.6F;
            triggerStompImpulse(intensity);
         }

         fritzTitanLastAnimTime.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyBeastTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         boolean isRidingBeast = player.getVehicle() instanceof BeastTitanEntity;
         BeastTitanEntity riddenTitan = isRidingBeast ? (BeastTitanEntity)player.getVehicle() : null;
         Set<Integer> currentTitans = new HashSet<>();
         BeastTitanEntity closestStompingTitan = null;
         double closestStompDistance = 45.0;
         boolean stompWasRunning = false;
         BeastTitanEntity closestImpactTitan = null;
         double closestImpactDistance = 50.0;
         BeastTitanEntity closestGrabImpactTitan = null;
         double closestGrabImpactDistance = 50.0;
         BeastTitanEntity closestThrowImpactTitan = null;
         double closestThrowImpactDistance = 50.0;
         BeastTitanEntity closestDismountTitan = null;
         double closestDismountDistance = 50.0;

         for (BeastTitanEntity titan : player.getWorld().getNonSpectatingEntities(BeastTitanEntity.class, player.getBoundingBox().expand(45.0))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            if (titan.isBeastRoarAbilityActive() && (titan == riddenTitan || distance < 45.0)) {
               pingBeastRoarShake();
               if (titan == riddenTitan) {
                  BoostFovHandler.pingBeastRoarFov();
               }
            }

            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = beastTitanLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            beastTitanLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped && distance < 45.0) {
               if (titan == riddenTitan) {
                  closestStompingTitan = titan;
                  closestStompDistance = 0.0;
                  stompWasRunning = titan.isSprinting();
               } else if (distance < closestStompDistance) {
                  closestStompingTitan = titan;
                  closestStompDistance = distance;
                  stompWasRunning = titan.isSprinting();
               }
            }

            int currentImpactTick = titan.getLastAttackImpactTick();
            Integer lastSeenImpactTick = beastTitanLastSeenImpactTick.get(titanId);
            boolean justImpacted = lastSeenImpactTick != null && currentImpactTick != lastSeenImpactTick && currentImpactTick > 0;
            if (lastSeenImpactTick == null && currentImpactTick > 0) {
               justImpacted = titan.age - currentImpactTick < 5;
            }

            beastTitanLastSeenImpactTick.put(titanId, currentImpactTick);
            if (justImpacted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestImpactTitan = titan;
                  closestImpactDistance = 0.0;
               } else if (distance < closestImpactDistance) {
                  closestImpactTitan = titan;
                  closestImpactDistance = distance;
               }
            }

            int currentGrabImpactTick = titan.getLastGrabImpactTick();
            Integer lastSeenGrabImpactTick = beastTitanLastSeenGrabImpactTick.get(titanId);
            boolean justGrabImpacted = lastSeenGrabImpactTick != null && currentGrabImpactTick != lastSeenGrabImpactTick && currentGrabImpactTick > 0;
            if (lastSeenGrabImpactTick == null && currentGrabImpactTick > 0) {
               justGrabImpacted = titan.age - currentGrabImpactTick < 5;
            }

            beastTitanLastSeenGrabImpactTick.put(titanId, currentGrabImpactTick);
            if (justGrabImpacted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestGrabImpactTitan = titan;
                  closestGrabImpactDistance = 0.0;
               } else if (distance < closestGrabImpactDistance) {
                  closestGrabImpactTitan = titan;
                  closestGrabImpactDistance = distance;
               }
            }

            if (titan.isGrabRumbling() && titan == riddenTitan) {
               triggerFastShake(0.4F);
            } else if (titan.isGrabRumbling() && distance < 50.0) {
               float normalizedDist = (float)(distance / 50.0);
               float rumbleIntensity = Math.max(0.0F, 0.3F - normalizedDist * 0.3F);
               if (rumbleIntensity > 0.0F) {
                  triggerFastShake(rumbleIntensity);
               }
            }

            int currentThrowImpactTick = titan.getLastThrowImpactTick();
            Integer lastSeenThrowImpactTick = beastTitanLastSeenThrowImpactTick.get(titanId);
            boolean justThrowImpacted = lastSeenThrowImpactTick != null && currentThrowImpactTick != lastSeenThrowImpactTick && currentThrowImpactTick > 0;
            if (lastSeenThrowImpactTick == null && currentThrowImpactTick > 0) {
               justThrowImpacted = titan.age - currentThrowImpactTick < 5;
            }

            beastTitanLastSeenThrowImpactTick.put(titanId, currentThrowImpactTick);
            if (justThrowImpacted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestThrowImpactTitan = titan;
                  closestThrowImpactDistance = 0.0;
               } else if (distance < closestThrowImpactDistance) {
                  closestThrowImpactTitan = titan;
                  closestThrowImpactDistance = distance;
               }
            }

            int currentDismountTick = titan.getLastDismountTick();
            Integer lastSeenDismountTick = beastTitanLastSeenDismountTick.get(titanId);
            boolean justDismounted = lastSeenDismountTick != null && currentDismountTick != lastSeenDismountTick && currentDismountTick > 0;
            if (lastSeenDismountTick == null && currentDismountTick > 0) {
               justDismounted = titan.age - currentDismountTick < 5;
            }

            beastTitanLastSeenDismountTick.put(titanId, currentDismountTick);
            if (justDismounted && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestDismountTitan = titan;
                  closestDismountDistance = 0.0;
               } else if (distance < closestDismountDistance) {
                  closestDismountTitan = titan;
                  closestDismountDistance = distance;
               }
            }
         }

         if (closestStompingTitan != null) {
            float intensity;
            if (closestStompingTitan == riddenTitan) {
               intensity = stompWasRunning ? 0.9F : 0.5F;
            } else {
               float normalizedDistance = (float)(closestStompDistance / 45.0);
               float baseIntensity = stompWasRunning ? 1.5F : 1.0F;
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestImpactTitan != null) {
            float intensity;
            if (closestImpactTitan == riddenTitan) {
               intensity = 0.8F;
            } else {
               float normalizedDistance = (float)(closestImpactDistance / 50.0);
               intensity = Math.max(0.0F, 1.2F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestGrabImpactTitan != null) {
            float intensity;
            if (closestGrabImpactTitan == riddenTitan) {
               intensity = 0.8F;
            } else {
               float normalizedDistance = (float)(closestGrabImpactDistance / 50.0);
               intensity = Math.max(0.0F, 1.0F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 0.8F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestThrowImpactTitan != null) {
            float intensity;
            if (closestThrowImpactTitan == riddenTitan) {
               intensity = 1.6F;
            } else {
               float normalizedDistance = (float)(closestThrowImpactDistance / 50.0);
               intensity = Math.max(0.0F, 2.4F - normalizedDistance * normalizedDistance * 2.4F);
               intensity = Math.min(intensity, 2.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestDismountTitan != null) {
            if (closestDismountTitan == riddenTitan) {
               triggerSwingDip();
               triggerFastShake(0.8F);
            } else {
               float normalizedDistance = (float)(closestDismountDistance / 50.0);
               float intensity = Math.max(0.0F, 0.6F - normalizedDistance * normalizedDistance * 0.6F);
               if (intensity > 0.0F) {
                  triggerFastShake(intensity);
               }
            }
         }

         beastTitanLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
         beastTitanLastSeenImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         beastTitanLastSeenGrabImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         beastTitanLastSeenThrowImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         beastTitanLastSeenDismountTick.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyWarhammerTitans(PlayerEntity player) {
      if (player != null && player.getWorld() != null) {
         boolean isRidingWarhammer = player.getVehicle() instanceof WarhammerTitanEntity;
         WarhammerTitanEntity riddenTitan = isRidingWarhammer ? (WarhammerTitanEntity)player.getVehicle() : null;
         Set<Integer> currentTitans = new HashSet<>();
         WarhammerTitanEntity closestStompingTitan = null;
         double closestStompDistance = 45.0;
         boolean stompWasRunning = false;
         WarhammerTitanEntity closestImpactTitan = null;
         double closestImpactDistance = 100.0;
         int impactAttackNumber = 0;
         WarhammerTitanEntity closestHitTitan = null;
         double closestHitDistance = 50.0;

         for (WarhammerTitanEntity titan : player.getWorld()
            .getNonSpectatingEntities(WarhammerTitanEntity.class, player.getBoundingBox().expand(Math.max(45.0, 100.0)))) {
            int titanId = titan.getId();
            currentTitans.add(titanId);
            double distance = player.distanceTo(titan);
            int currentStompTick = titan.getLastStompTick();
            Integer lastSeenStompTick = warhammerTitanLastSeenStompTick.get(titanId);
            boolean justStomped = lastSeenStompTick != null && currentStompTick != lastSeenStompTick && currentStompTick > 0;
            if (lastSeenStompTick == null && currentStompTick > 0) {
               justStomped = titan.age - currentStompTick < 5;
            }

            warhammerTitanLastSeenStompTick.put(titanId, currentStompTick);
            if (justStomped && distance < 45.0) {
               if (titan == riddenTitan) {
                  closestStompingTitan = titan;
                  closestStompDistance = 0.0;
                  stompWasRunning = titan.isSprinting();
               } else if (distance < closestStompDistance) {
                  closestStompingTitan = titan;
                  closestStompDistance = distance;
                  stompWasRunning = titan.isSprinting();
               }
            }

            int currentImpactTick = titan.getLastAttackImpactTick();
            Integer lastSeenImpactTick = warhammerTitanLastSeenImpactTick.get(titanId);
            boolean justImpacted = lastSeenImpactTick != null && currentImpactTick != lastSeenImpactTick && currentImpactTick > 0;
            if (lastSeenImpactTick == null && currentImpactTick > 0) {
               justImpacted = titan.age - currentImpactTick < 5;
            }

            warhammerTitanLastSeenImpactTick.put(titanId, currentImpactTick);
            if (justImpacted && distance < 100.0) {
               if (titan == riddenTitan) {
                  closestImpactTitan = titan;
                  closestImpactDistance = 0.0;
                  impactAttackNumber = titan.getAttackNumber();
               } else if (distance < closestImpactDistance) {
                  closestImpactTitan = titan;
                  closestImpactDistance = distance;
                  impactAttackNumber = titan.getAttackNumber();
               }
            }

            int currentHitTick = titan.getLastHitTick();
            Integer lastSeenHitTick = warhammerTitanLastSeenHitTick.get(titanId);
            boolean justHit = lastSeenHitTick != null && currentHitTick != lastSeenHitTick && currentHitTick > 0;
            if (lastSeenHitTick == null && currentHitTick > 0) {
               justHit = titan.age - currentHitTick < 5;
            }

            warhammerTitanLastSeenHitTick.put(titanId, currentHitTick);
            if (justHit && distance < 50.0) {
               if (titan == riddenTitan) {
                  closestHitTitan = titan;
                  closestHitDistance = 0.0;
               } else if (distance < closestHitDistance) {
                  closestHitTitan = titan;
                  closestHitDistance = distance;
               }
            }
         }

         if (closestStompingTitan != null) {
            float intensity;
            if (closestStompingTitan == riddenTitan) {
               intensity = stompWasRunning ? 0.9F : 0.5F;
            } else {
               float normalizedDistance = (float)(closestStompDistance / 45.0);
               float baseIntensity = stompWasRunning ? 1.5F : 1.0F;
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestImpactTitan != null) {
            boolean isStompOrPunch = impactAttackNumber == 6
               || impactAttackNumber == 7
               || impactAttackNumber == 8
               || impactAttackNumber == 9
               || impactAttackNumber == 10
               || impactAttackNumber == 12;
            float baseIntensity;
            if (isStompOrPunch) {
               baseIntensity = 2.4F;
            } else if (impactAttackNumber != 3 && impactAttackNumber != 5) {
               baseIntensity = 1.2F;
            } else {
               baseIntensity = 1.5F;
            }

            float intensity;
            if (closestImpactTitan != riddenTitan) {
               double shakeRadius = isStompOrPunch ? 100.0 : 50.0;
               float normalizedDistance = (float)(closestImpactDistance / shakeRadius);
               intensity = Math.max(0.0F, baseIntensity - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, isStompOrPunch ? 2.0F : 1.0F);
            } else {
               intensity = isStompOrPunch ? 1.6F : (impactAttackNumber != 3 && impactAttackNumber != 5 ? 0.8F : 1.0F);
            }

            triggerStompImpulse(intensity);
         }

         if (closestHitTitan != null) {
            float intensity;
            if (closestHitTitan == riddenTitan) {
               intensity = 0.9F;
            } else {
               float normalizedDistance = (float)(closestHitDistance / 50.0);
               intensity = Math.max(0.0F, 0.8F - normalizedDistance * normalizedDistance);
               intensity = Math.min(intensity, 0.6F);
            }

            triggerRubberbandShake(closestHitTitan.getHitDirYaw(), intensity);
         }

         for (WarhammerTitanEntity titan : player.getWorld().getNonSpectatingEntities(WarhammerTitanEntity.class, player.getBoundingBox().expand(100.0))) {
            int titanIdx = titan.getId();
            int currentLandingTick = titan.getLastLandingTick();
            Integer lastSeenLandingTick = warhammerTitanLastSeenLandingTick.get(titanIdx);
            boolean justLanded = lastSeenLandingTick != null && currentLandingTick != lastSeenLandingTick && currentLandingTick > 0;
            if (lastSeenLandingTick == null && currentLandingTick > 0) {
               justLanded = titan.age - currentLandingTick < 5;
            }

            warhammerTitanLastSeenLandingTick.put(titanIdx, currentLandingTick);
            if (justLanded) {
               float landIntensity = titan.getLandingIntensity();
               double distancex = player.distanceTo(titan);
               float shakeIntensityVal;
               if (titan == riddenTitan) {
                  shakeIntensityVal = 0.5F + landIntensity * 0.3F;
               } else {
                  double shakeRadius = 50.0 + landIntensity * 15.0;
                  if (distancex < shakeRadius) {
                     float normalizedDistance = (float)(distancex / shakeRadius);
                     float baseShake = 0.8F + landIntensity * 0.4F;
                     shakeIntensityVal = Math.max(0.0F, baseShake - normalizedDistance * normalizedDistance);
                  } else {
                     shakeIntensityVal = 0.0F;
                  }
               }

               if (shakeIntensityVal > 0.0F) {
                  triggerShake(shakeIntensityVal);
               }

               if (titan == riddenTitan || distancex < 30.0) {
                  triggerLandingDip(landIntensity);
               }
            }
         }

         warhammerTitanLastSeenStompTick.keySet().removeIf(id -> !currentTitans.contains(id));
         warhammerTitanLastSeenImpactTick.keySet().removeIf(id -> !currentTitans.contains(id));
         warhammerTitanLastSeenHitTick.keySet().removeIf(id -> !currentTitans.contains(id));
         warhammerTitanLastSeenLandingTick.keySet().removeIf(id -> !currentTitans.contains(id));
      }
   }

   public static void checkNearbyOgreTitans(PlayerEntity player) {
      for (OgreTitanEntity ogre : player.getWorld().getNonSpectatingEntities(OgreTitanEntity.class, player.getBoundingBox().expand(80.0))) {
         if (!ogre.isDead()) {
            boolean isMoving = ogre.getVelocity().horizontalLengthSquared() > 0.005;
            if (isMoving) {
               int stompInterval = ogre.isAttacking() ? 8 : 14;
               if (ogre.age % stompInterval == 0) {
                  double distance = player.distanceTo(ogre);
                  if (distance < 80.0) {
                     float normalizedDistance = (float)(distance / 80.0);
                     float intensity = (1.0F - normalizedDistance * normalizedDistance) * 1.0F;
                     triggerStompImpulse(intensity);
                  }
               }
            }
         }
      }
   }
}
