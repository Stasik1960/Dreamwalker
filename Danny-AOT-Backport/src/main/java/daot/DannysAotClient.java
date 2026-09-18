package daot;

import daot.mixin.client.ParticleAccessor;
import daot.mixin.client.ParticleEngineAccessor;
import daot.network.APGAimSyncPayload;
import daot.network.APGFireShakePayload;
import daot.network.APGReloadPayload;
import daot.network.AllowODMPayload;
import daot.network.AllowShiftingPayload;
import daot.network.AllowThunderSpearsPayload;
import daot.network.AnimBroadcastPayload;
import daot.network.ArmorPotionDrinkPayload;
import daot.network.AtrainSpeedStatePayload;
import daot.network.AwakenToggleSyncPayload;
import daot.network.AwakenedPowerPayload;
import daot.network.AwakenedPowerSyncPayload;
import daot.network.BladeAnimSyncPayload;
import daot.network.BladeBlockBrokenPayload;
import daot.network.BladeReloadPayload;
import daot.network.BloodlineSyncPayload;
import daot.network.BloodmoonMusicPayload;
import daot.network.BloodmoonSyncPayload;
import daot.network.ButcherGrabStatePayload;
import daot.network.ButcherTentacleBroadcastPayload;
import daot.network.ChargedODMAttacksPayload;
import daot.network.ConfigSyncPayload;
import daot.network.EffectPayload;
import daot.network.FlareGunCyclePayload;
import daot.network.FlareGunLoadPayload;
import daot.network.FogSyncPayload;
import daot.network.FounderAnimPayload;
import daot.network.FounderHumanAbilityPayload;
import daot.network.GeassControlPayload;
import daot.network.GeassTogglePayload;
import daot.network.GrassODMPayload;
import daot.network.HandcuffsSyncPayload;
import daot.network.HomelanderAttackBroadcastPayload;
import daot.network.HomelanderBloodSyncPayload;
import daot.network.HomelanderFlightInputBroadcastPayload;
import daot.network.HomelanderFlyStatePayload;
import daot.network.HomelanderGrabIntentBroadcastPayload;
import daot.network.HomelanderGrabRotationSyncPayload;
import daot.network.HomelanderGrabSyncPayload;
import daot.network.HomelanderLaserActivePayload;
import daot.network.HomelanderLaserDirectionBroadcastPayload;
import daot.network.HoodSyncPayload;
import daot.network.HoodTogglePayload;
import daot.network.JawPounceTargetPayload;
import daot.network.KineticOdmDamagePayload;
import daot.network.MindControlInputPayload;
import daot.network.MindControlRotationSyncPayload;
import daot.network.ODMHookSyncPayload;
import daot.network.ODMJamPayload;
import daot.network.PoweredVillagerSyncPayload;
import daot.network.PushAuraStatePayload;
import daot.network.PushAuraTogglePayload;
import daot.network.RealisticResourceUsePayload;
import daot.network.RepelModeTogglePayload;
import daot.network.ShiftLightningPayload;
import daot.network.ShiftShakePayload;
import daot.network.ShifterDodgeEffectPayload;
import daot.network.ShifterDodgePayload;
import daot.network.ShifterMarkSyncPayload;
import daot.network.SoldierboyBlastShakePayload;
import daot.network.SoldierboyChargeStatePayload;
import daot.network.SoldierboyLaserActivePayload;
import daot.network.SoldierboyNeckGrabStatePayload;
import daot.network.StaminaSyncPayload;
import daot.network.StealthTogglePayload;
import daot.network.StrwsAimSyncPayload;
import daot.network.StrwsControlPayload;
import daot.network.StrwsFireShakePayload;
import daot.network.TargetGlowPayload;
import daot.network.TargetModeSyncPayload;
import daot.network.TeaseShiftPayload;
import daot.network.ThunderSpearLoadPayload;
import daot.network.TitanAbilityPayload;
import daot.network.TitanArmPayload;
import daot.network.TitanAttackPayload;
import daot.network.TitanBloodlineSyncPayload;
import daot.network.TitanDashAnimPayload;
import daot.network.TitanDashSyncPayload;
import daot.network.TitanImpactShakePayload;
import daot.network.TitanJumpPayload;
import daot.network.TitanRoarPayload;
import daot.network.TitanShiftPayload;
import daot.network.TitanSprintPayload;
import daot.network.WeatherAffectsODMPayload;
import daot.network.ZekesGlowPayload;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.Unload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import daot.compat.network.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.item.ItemColorProvider;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.client.RenderProvider;

@Environment(EnvType.CLIENT)
public class DannysAotClient implements ClientModInitializer {
   public static KeyBinding LEFT_HOOK_KEY;
   public static KeyBinding RIGHT_HOOK_KEY;
   public static KeyBinding TITAN_SHIFT_KEY;
   public static KeyBinding TEASE_SHIFT_KEY;
   public static KeyBinding RELOAD_BLADE_KEY;
   public static KeyBinding TITAN_ROAR_KEY;
   public static KeyBinding SHIFTER_ABILITY_1_KEY;
   public static KeyBinding SHIFTER_ABILITY_2_KEY;
   public static KeyBinding SHIFTER_ABILITY_3_KEY;
   public static KeyBinding SHIFTER_ABILITY_4_KEY;
   public static KeyBinding SHIFTER_ABILITY_5_KEY;
   public static KeyBinding SHIFTER_ABILITY_6_KEY;
   public static KeyBinding SHIFTER_ABILITY_7_KEY;
   public static KeyBinding SHIFTER_ABILITY_8_KEY;
   public static KeyBinding SHIFTER_ABILITY_9_KEY;
   public static KeyBinding STEALTH_MODE_KEY;
   public static KeyBinding AWAKENED_POWER_KEY;
   public static KeyBinding HOOD_TOGGLE_KEY;
   public static KeyBinding HIDE_SHIFTER_UI_KEY;
   public static KeyBinding THUNDER_SPEAR_LOAD_KEY;
   public static KeyBinding FLARE_LOAD_KEY;
   public static KeyBinding FLARE_CYCLE_KEY;
   public static KeyBinding APG_RELOAD_KEY;
   public static KeyBinding SHIFTER_DODGE_KEY;
   public static KeyBinding PUSH_AURA_KEY;
   public static KeyBinding COMBAT_MODE_TOGGLE_KEY;
   public static KeyBinding COMBAT_LEFT_HOOK_KEY;
   public static KeyBinding COMBAT_RIGHT_HOOK_KEY;
   public static KeyBinding COMBAT_BLOCK_KEY;
   public static KeyBinding COMBAT_SHIFT_LOCK_KEY;
   private static boolean wasFlareLoadKeyDown = false;
   private static boolean wasFlareCycleKeyDown = false;
   private static boolean wasAttackKeyDown = false;
   private static boolean lastSprintState = false;
   private static boolean lastArmState = false;
   private static boolean wasJumpKeyDown = false;
   private static final RegistryKey<World> FOG_PARADIS = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));
   private static long lastBladeReloadTime = 0L;
   private static final long BLADE_RELOAD_COOLDOWN = 10L;
   private static boolean wasReloadKeyDown = false;
   private static boolean wasApgReloadKeyDown = false;
   private static long lastApgReloadTime = 0L;
   private static boolean wasUseKeyDownForSpear = false;
   private static boolean wasAttackKeyDownForSpear = false;
   private static boolean wasThunderSpearKeyDown = false;
   private static boolean wasTitanShiftKeyDown = false;
   private static boolean wasTeaseShiftKeyDown = false;
   private static boolean wasStealthModeKeyDown = false;
   private static boolean wasHoodToggleKeyDown = false;
   private static boolean wasHideShifterUIKeyDown = false;
   private static boolean wasFounderAbilityBarKeyDown = false;
   private static boolean wasAwakenedPowerKeyDown = false;
   private static boolean wasTitanRoarKeyDown = false;
   private static boolean wasGeassKeyDown = false;
   private static final boolean[] wasAbilityKeyDown = new boolean[9];
   private static boolean wasShifterDodgeKeyDown = false;
   private static boolean wasCombatModeToggleKeyDown = false;
   private static boolean wasCombatShiftLockKeyDown = false;
   public static boolean shiftingAllowedByGamerule = true;
   public static boolean thunderSpearsAllowedByGamerule = true;

   private static boolean modifyDisconnectButton(Element listener) {
      if (listener instanceof ButtonWidget button) {
         String text = button.getMessage().getString().toLowerCase();
         if (text.contains("disconnect") || text.contains("save and quit") || text.contains("quit")) {
            button.setMessage(
               Text.literal("").append(button.getMessage()).append(Text.literal(" (WILL LOSE SHIFTER)").formatted(Formatting.RED, Formatting.BOLD))
            );
            return true;
         }
      }

      return false;
   }

   static boolean isRawKeyDown(KeyBinding key) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.getWindow() == null) {
         return false;
      } else if (mc.currentScreen != null) {
         return false;
      } else {
         Key boundKey = KeyBindingHelper.getBoundKeyOf(key);
         int keyCode = boundKey.getCode();
         return keyCode == InputUtil.UNKNOWN_KEY.getCode() ? false : InputUtil.isKeyPressed(mc.getWindow().getHandle(), keyCode);
      }
   }

   private static KeyBinding[] getAbilityKeys() {
      return new KeyBinding[]{
         SHIFTER_ABILITY_1_KEY,
         SHIFTER_ABILITY_2_KEY,
         SHIFTER_ABILITY_3_KEY,
         SHIFTER_ABILITY_4_KEY,
         SHIFTER_ABILITY_5_KEY,
         SHIFTER_ABILITY_6_KEY,
         SHIFTER_ABILITY_7_KEY,
         SHIFTER_ABILITY_8_KEY,
         SHIFTER_ABILITY_9_KEY
      };
   }

   public void onInitializeClient() {
      daot.compat.BackportParticles.initialize();
      AttackTitanEntity.MOVEMENT_KEYFRAME_INSTALLER = AttackTitanFootstepHandler::install;
      ColossalTitanEntity.MOVEMENT_KEYFRAME_INSTALLER = ColossalTitanFootstepHandler::install;
      DannysAot.canReadCannedHerring = () -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         return mc.player == null ? true : ShifterStaminaHUD.isShifter() || BloodlineClientData.isMarleyan(mc.player.getUuid());
      };
      ItemTooltipCallback.EVENT.register((ItemTooltipCallback)(stack, tooltipContext, lines) -> {
         if ((daot.compat.components.Components.get(stack, DannysAot.LACED_FOOD_DATA) != null)) {
            ClientPlayerEntity localPlayer = MinecraftClient.getInstance().player;
            if (localPlayer != null) {
               ItemStack head = localPlayer.getEquippedStack(EquipmentSlot.HEAD);
               if (head.getItem() instanceof ZekesGlassesItem) {
                  lines.add(Text.literal("(LACED)").formatted(Formatting.RED, Formatting.BOLD));
               }
            }
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(
         ConfigSyncPayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> ODMConfig.applySyncedConfig(
                  payload.dualHookEaseTime(),
                  payload.gasTickInterval(),
                  payload.gasConsumptionNormal(),
                  payload.gasConsumptionBoost(),
                  payload.basePullSpeed(),
                  payload.dualHookPullMultiplier(),
                  payload.dualHookBoostPullMultiplier(),
                  payload.orbitPullMultiplier(),
                  payload.baseOrbitSpeed(),
                  payload.dualHookOrbitMultiplier(),
                  payload.upwardLift(),
                  payload.boostPullMultiplier(),
                  payload.boostOrbitMultiplier(),
                  payload.boostRampRate(),
                  payload.boostDecayRate(),
                  payload.maxHookDistance(),
                  payload.momentumPreserveTime(),
                  payload.flightSoundVelocityThreshold()
               )
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         AllowODMPayload.TYPE, (payload, context) -> context.client().execute(() -> ODMTickHandler.setODMAllowedByGamerule(payload.allowed()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         GrassODMPayload.TYPE, (payload, context) -> context.client().execute(() -> ODMTickHandler.setGrassODMAllowed(payload.allowed()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ChargedODMAttacksPayload.TYPE,
         (payload, context) -> context.client().execute(() -> BladeAnimationHandler.setChargedODMAttacksEnabled(payload.enabled()))
      );
      ClientPlayNetworking.registerGlobalReceiver(PushAuraStatePayload.TYPE, (payload, context) -> context.client().execute(() -> {
         MinecraftClient mc = context.client();
         if (payload.active()) {
            int previousId = PushAuraClientData.pushAuraEntityId;
            PushAuraClientData.pushAuraEntityId = payload.entityId();
            PushAuraClientData.clientAttractMode = payload.attract();
            if (previousId != payload.entityId()) {
               PushAuraClientData.roarEffectVisible = false;
               PushAuraClientData.roarTickCounter = 0;
               if (PushAuraClientData.auraLoopSound != null) {
                  PushAuraClientData.auraLoopSound.fadeOut();
                  PushAuraClientData.auraLoopSound = null;
               }
            }
         } else if (PushAuraClientData.pushAuraEntityId == payload.entityId()) {
            PushAuraClientData.clear();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         WeatherAffectsODMPayload.TYPE, (payload, context) -> context.client().execute(() -> ODMTickHandler.setWeatherAffectsOdm(payload.enabled()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         KineticOdmDamagePayload.TYPE, (payload, context) -> context.client().execute(() -> ODMTickHandler.setKineticOdmDamage(payload.enabled()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         RealisticResourceUsePayload.TYPE,
         (payload, context) -> context.client().execute(() -> RealisticResourceUseTracker.setClientEnabled(payload.enabled()))
      );
      ClientPlayNetworking.registerGlobalReceiver(ODMJamPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         ClientPlayerEntity player = context.client().player;
         if (player != null) {
            if (ODMTickHandler.isJamImmune(player.getUuid())) {
               return;
            }

            ODMTickHandler.jamOdmForDuration(player.getUuid(), payload.durationMs());
            if (payload.releaseHooks()) {
               HookPoint left = ODMTickHandler.getLeftHook();
               HookPoint right = ODMTickHandler.getRightHook();
               if (left != null && left.active) {
                  left.release();
                  BladeAnimationHandler.release(Hand.OFF_HAND);
               }

               if (right != null && right.active) {
                  right.release();
                  BladeAnimationHandler.release(Hand.MAIN_HAND);
               }

               CameraShakeHandler.triggerWireYank();
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         AllowShiftingPayload.TYPE, (payload, context) -> context.client().execute(() -> shiftingAllowedByGamerule = payload.allowed())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         APGFireShakePayload.TYPE, (payload, context) -> context.client().execute(() -> CameraShakeHandler.triggerFastShake(payload.intensity()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         StrwsFireShakePayload.TYPE, (payload, context) -> context.client().execute(() -> CameraShakeHandler.triggerStrwsFireShake(payload.intensity()))
      );
      ClientPlayNetworking.registerGlobalReceiver(ArmorPotionDrinkPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         HardeningClientState.set(payload.hasHardening());
         if (payload.justDrank()) {
            CameraShakeHandler.triggerArmorPotionShake();
            BoostFovHandler.triggerArmorPotionFov();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(StrwsControlPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (payload.active()) {
            StrwsAimClientState.start(payload.controller());
         } else {
            StrwsAimClientState.stop();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(StrwsAimSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         MinecraftClient mc = context.client();
         if (mc.world != null) {
            if (mc.world.getBlockEntity(payload.controller()) instanceof StrwsBlockEntity be) {
               be.setAim(payload.yaw(), payload.pitch());
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(ShifterDodgeEffectPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         MinecraftClient mc = context.client();
         if (mc.world != null) {
            Entity titan = mc.world.getEntityById(payload.titanEntityId());
            if (titan != null) {
               Box bb = titan.getBoundingBox();
               double cx = (bb.minX + bb.maxX) * 0.5;
               double cy = (bb.minY + bb.maxY) * 0.5;
               double cz = (bb.minZ + bb.maxZ) * 0.5;
               mc.world.playSound(cx, cy, cz, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 3.0F, 0.8F, false);
               Random rng = new Random();
               int count = 80;

               for (int i = 0; i < count; i++) {
                  double px = bb.minX + rng.nextDouble() * (bb.maxX - bb.minX);
                  double py = bb.minY + rng.nextDouble() * (bb.maxY - bb.minY);
                  double pz = bb.minZ + rng.nextDouble() * (bb.maxZ - bb.minZ);
                  mc.world.addParticle(daot.compat.BackportEffects.DUST_PLUME, px, py, pz, 0.0, 0.0, 0.0);
               }

               if (mc.player != null && titan.hasPassenger(mc.player)) {
                  float dirX = payload.dirX();
                  float dirZ = payload.dirZ();
                  float dodgeYaw = (float)Math.toDegrees(Math.atan2(-dirX, dirZ));
                  CameraShakeHandler.triggerDodgeLunge(dodgeYaw);
               }
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         AllowThunderSpearsPayload.TYPE, (payload, context) -> context.client().execute(() -> thunderSpearsAllowedByGamerule = payload.allowed())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         BladeBlockBrokenPayload.TYPE, (payload, context) -> context.client().execute(() -> BladeAnimationHandler.onBlockBroken())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         BladeAnimSyncPayload.TYPE,
         (payload, context) -> context.client().execute(() -> ODMAnimationHandler.applyRemoteAction(payload.entityId(), payload.action()))
      );
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.SMALL_ICE_BURST_SHARD, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.MEDIUM_ICE_BURST_SHARD, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.LARGE_ICE_BURST_SHARD, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.ICE_BURST_SHARD_CLUSTER, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.IRON_BAMBOO, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.IRON_BAMBOO_SAPLING, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.BAMBOO_LEAVES, RenderLayer.getCutout());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.HARDENED_BLOCK, RenderLayer.getTranslucent());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.HARDENED_SLAB, RenderLayer.getTranslucent());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.HARDENED_STAIRS, RenderLayer.getTranslucent());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.ULTRA_HARDENED_BLOCK, RenderLayer.getTranslucent());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.ULTRA_HARDENED_SLAB, RenderLayer.getTranslucent());
      BlockRenderLayerMap.INSTANCE.putBlock(DannysAot.ULTRA_HARDENED_STAIRS, RenderLayer.getTranslucent());
      HandledScreens.register(DannysAot.ICEBURST_FURNACE_MENU, IceburstFurnaceScreen::new);
      HandledScreens.register(DannysAot.STRWS_MENU, StrwsScreen::new);
      ODMGearItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private ODMGearRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new ODMGearRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      UniformItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private UniformRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new UniformRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      GarrisonUniformItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private GarrisonUniformRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new GarrisonUniformRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      ScoutUniformItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private ScoutUniformRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new ScoutUniformRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      MilitaryPoliceUniformItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private MilitaryPoliceUniformRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new MilitaryPoliceUniformRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      CultistRobeItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private CultistRobeRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new CultistRobeRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.armorRenderer;
            }
         }
      );
      RedScarfItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private RedScarfRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new RedScarfRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.armorRenderer;
            }
         }
      );
      CloakItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private CloakRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new CloakRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      TrenchCoatItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private TrenchCoatRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new TrenchCoatRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      MarleyArmbandItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private MarleyArmbandRenderer armorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.armorRenderer == null) {
                  this.armorRenderer = new MarleyArmbandRenderer();
               }

               this.armorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.armorRenderer.updateModelState(livingEntity, itemStack);
               return this.armorRenderer;
            }
         }
      );
      BladeItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private BladeItemRenderer bladeRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.bladeRenderer == null) {
               this.bladeRenderer = new BladeItemRenderer();
            }

            return this.bladeRenderer;
         }
      });
      BladeComponentItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private BladeComponentItemRenderer bladeComponentRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.bladeComponentRenderer == null) {
               this.bladeComponentRenderer = new BladeComponentItemRenderer();
            }

            return this.bladeComponentRenderer;
         }
      });
      APGGunItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private APGGunItemRenderer apgGunRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.apgGunRenderer == null) {
               this.apgGunRenderer = new APGGunItemRenderer();
            }

            return this.apgGunRenderer;
         }
      });
      APGCartridgeItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private APGCartridgeItemRenderer apgCartridgeRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.apgCartridgeRenderer == null) {
               this.apgCartridgeRenderer = new APGCartridgeItemRenderer();
            }

            return this.apgCartridgeRenderer;
         }
      });
      GasCanisterItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private GasCanisterItemRenderer gasCanisterRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.gasCanisterRenderer == null) {
               this.gasCanisterRenderer = new GasCanisterItemRenderer();
            }

            return this.gasCanisterRenderer;
         }
      });
      ArmorPotionItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private ArmorPotionItemRenderer armorPotionRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.armorPotionRenderer == null) {
               this.armorPotionRenderer = new ArmorPotionItemRenderer();
            }

            return this.armorPotionRenderer;
         }
      });
      StrwsItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private StrwsItemRenderer strwsRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.strwsRenderer == null) {
               this.strwsRenderer = new StrwsItemRenderer();
            }

            return this.strwsRenderer;
         }
      });
      ThunderSpearItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private ThunderSpearItemRenderer thunderSpearRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.thunderSpearRenderer == null) {
               this.thunderSpearRenderer = new ThunderSpearItemRenderer();
            }

            return this.thunderSpearRenderer;
         }
      });
      FlareGunItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private FlareGunItemRenderer flareGunRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.flareGunRenderer == null) {
               this.flareGunRenderer = new FlareGunItemRenderer();
            }

            return this.flareGunRenderer;
         }
      });
      ZekesGlassesItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private ZekesGlassesItemRenderer zekesGlassesItemRenderer;
            private ZekesGlassesArmorRenderer zekesGlassesArmorRenderer;

            public BuiltinModelItemRenderer getGeoItemRenderer() {
               if (this.zekesGlassesItemRenderer == null) {
                  this.zekesGlassesItemRenderer = new ZekesGlassesItemRenderer();
               }

               return this.zekesGlassesItemRenderer;
            }

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.zekesGlassesArmorRenderer == null) {
                  this.zekesGlassesArmorRenderer = new ZekesGlassesArmorRenderer();
               }

               this.zekesGlassesArmorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.zekesGlassesArmorRenderer;
            }
         }
      );
      ODMAPGItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private ODMAPGRenderer odmApgRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.odmApgRenderer == null) {
                  this.odmApgRenderer = new ODMAPGRenderer();
               }

               this.odmApgRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.odmApgRenderer;
            }
         }
      );
      APGSuitItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private APGSuitRenderer apgSuitRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.apgSuitRenderer == null) {
                  this.apgSuitRenderer = new APGSuitRenderer();
               }

               this.apgSuitRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.apgSuitRenderer;
            }
         }
      );
      BasementKeyItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private KeyNecklaceArmorRenderer keyNecklaceArmorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.keyNecklaceArmorRenderer == null) {
                  this.keyNecklaceArmorRenderer = new KeyNecklaceArmorRenderer();
               }

               this.keyNecklaceArmorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.keyNecklaceArmorRenderer;
            }
         }
      );
      KennyHatItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private KennyHatArmorRenderer kennyHatArmorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.kennyHatArmorRenderer == null) {
                  this.kennyHatArmorRenderer = new KennyHatArmorRenderer();
               }

               this.kennyHatArmorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.kennyHatArmorRenderer;
            }
         }
      );
      MarleyUniformItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private MarleyUniformRenderer marleyUniformRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.marleyUniformRenderer == null) {
                  this.marleyUniformRenderer = new MarleyUniformRenderer();
               }

               this.marleyUniformRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.marleyUniformRenderer;
            }
         }
      );
      MarleyEldianUniformItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private MarleyEldianUniformRenderer marleyEldianUniformRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.marleyEldianUniformRenderer == null) {
                  this.marleyEldianUniformRenderer = new MarleyEldianUniformRenderer();
               }

               this.marleyEldianUniformRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               return this.marleyEldianUniformRenderer;
            }
         }
      );
      ODMBootsItem.clientRendererConsumer = consumer -> consumer.accept(
         new RenderProvider() {
            private ODMBootsArmorRenderer odmBootsArmorRenderer;

            public <T extends LivingEntity> BipedEntityModel<?> getGeoArmorRenderer(
               T livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, BipedEntityModel<T> original
            ) {
               if (this.odmBootsArmorRenderer == null) {
                  this.odmBootsArmorRenderer = new ODMBootsArmorRenderer();
               }

               this.odmBootsArmorRenderer.prepForRender(livingEntity, itemStack, equipmentSlot, original);
               this.odmBootsArmorRenderer.updateModelState(livingEntity, itemStack);
               return this.odmBootsArmorRenderer;
            }
         }
      );
      RegimentBannerItem.clientRendererConsumer = consumer -> consumer.accept(new RenderProvider() {
         private RegimentBannerItemRenderer bannerRenderer;

         public BuiltinModelItemRenderer getGeoItemRenderer() {
            if (this.bannerRenderer == null) {
               this.bannerRenderer = new RegimentBannerItemRenderer();
            }

            return this.bannerRenderer;
         }
      });
      LEFT_HOOK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.left_hook", 71, "category.dannys-aot.main"));
      RIGHT_HOOK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.right_hook", 72, "category.dannys-aot.main"));
      TITAN_SHIFT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.titan_shift", 66, "category.dannys-aot.main"));
      TEASE_SHIFT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.tease_shift", 75, "category.dannys-aot.main"));
      RELOAD_BLADE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.reload_blade", 82, "category.dannys-aot.main"));
      TITAN_ROAR_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.titan_roar", 90, "category.dannys-aot.main"));
      SHIFTER_ABILITY_1_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_1", 49, "category.dannys-aot.main"));
      SHIFTER_ABILITY_2_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_2", 50, "category.dannys-aot.main"));
      SHIFTER_ABILITY_3_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_3", 51, "category.dannys-aot.main"));
      SHIFTER_ABILITY_4_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_4", 52, "category.dannys-aot.main"));
      SHIFTER_ABILITY_5_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_5", 53, "category.dannys-aot.main"));
      SHIFTER_ABILITY_6_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_6", 54, "category.dannys-aot.main"));
      SHIFTER_ABILITY_7_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_7", 55, "category.dannys-aot.main"));
      SHIFTER_ABILITY_8_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_8", 56, "category.dannys-aot.main"));
      SHIFTER_ABILITY_9_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_ability_9", 57, "category.dannys-aot.main"));
      STEALTH_MODE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.stealth_mode", 78, "category.dannys-aot.main"));
      AWAKENED_POWER_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.awakened_power", 89, "category.dannys-aot.main"));
      HOOD_TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.hood_toggle", 72, "category.dannys-aot.main"));
      HIDE_SHIFTER_UI_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.hide_shifter_ui", 91, "category.dannys-aot.main"));
      THUNDER_SPEAR_LOAD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.thunder_spear_load", 86, "category.dannys-aot.main"));
      FLARE_LOAD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.flare_load", 88, "category.dannys-aot.main"));
      FLARE_CYCLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.flare_cycle", 67, "category.dannys-aot.main"));
      APG_RELOAD_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.apg_reload", 82, "category.dannys-aot.main"));
      SHIFTER_DODGE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.shifter_dodge", 342, "category.dannys-aot.main"));
      String localName = MinecraftClient.getInstance().getSession().getUsername();
      if ("Speakor".equals(localName) || "Inrupt".equals(localName)) {
         PUSH_AURA_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.dannys-aot.push_aura", 59, "category.dannys-aot.main"));
      }

      COMBAT_MODE_TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.dannys-aot.combat_mode_toggle", InputUtil.UNKNOWN_KEY.getCode(), "category.dannys-aot.combat_mode")
      );
      COMBAT_LEFT_HOOK_KEY = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.dannys-aot.combat_left_hook", InputUtil.UNKNOWN_KEY.getCode(), "category.dannys-aot.combat_mode")
      );
      COMBAT_RIGHT_HOOK_KEY = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.dannys-aot.combat_right_hook", InputUtil.UNKNOWN_KEY.getCode(), "category.dannys-aot.combat_mode")
      );
      COMBAT_BLOCK_KEY = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.dannys-aot.combat_block", InputUtil.UNKNOWN_KEY.getCode(), "category.dannys-aot.combat_mode")
      );
      COMBAT_SHIFT_LOCK_KEY = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.dannys-aot.combat_shift_lock", InputUtil.UNKNOWN_KEY.getCode(), "category.dannys-aot.combat_mode")
      );
      CloakItem.hoodKeyNameSupplier = () -> HOOD_TOGGLE_KEY.getBoundKeyLocalizedText().getString();
      FlareGunItem.loadKeyNameSupplier = () -> FLARE_LOAD_KEY.getBoundKeyLocalizedText().getString();
      FlareGunItem.cycleKeyNameSupplier = () -> FLARE_CYCLE_KEY.getBoundKeyLocalizedText().getString();
      BladeItem.thunderSpearKeyNameSupplier = () -> THUNDER_SPEAR_LOAD_KEY.getBoundKeyLocalizedText().getString();
      ThunderSpearItem.loadKeyNameSupplier = () -> THUNDER_SPEAR_LOAD_KEY.getBoundKeyLocalizedText().getString();
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)client -> {
         if (client.player != null) {
            Entity vehicle = client.player.getVehicle();
            boolean ridingTitanNotInEject = false;
            if (vehicle instanceof AttackTitanEntity titan) {
               ridingTitanNotInEject = !titan.isDismounting();
            } else if (vehicle instanceof ArmoredTitanEntity titan) {
               ridingTitanNotInEject = !titan.isDismounting();
            } else if (vehicle instanceof FemaleTitanEntity titan) {
               ridingTitanNotInEject = !titan.isDismounting();
            } else if (vehicle instanceof ColossalTitanEntity titan) {
               ridingTitanNotInEject = !titan.isDismounting();
            } else if (vehicle instanceof BeastTitanEntity titan) {
               ridingTitanNotInEject = !titan.isDismounting();
            } else if (vehicle instanceof WarhammerTitanEntity titan) {
               ridingTitanNotInEject = !titan.isDismounting();
            }

            if (ridingTitanNotInEject) {
               while (client.options.inventoryKey.wasPressed()) {
               }

               if (client.currentScreen instanceof InventoryScreen || client.currentScreen instanceof CreativeInventoryScreen) {
                  client.setScreen(null);
               }
            }

            if (CombatModeState.isEnabled()) {
               int leftCode = KeyBindingHelper.getBoundKeyOf(COMBAT_LEFT_HOOK_KEY).getCode();
               int rightCode = KeyBindingHelper.getBoundKeyOf(COMBAT_RIGHT_HOOK_KEY).getCode();
               int blockCode = KeyBindingHelper.getBoundKeyOf(COMBAT_BLOCK_KEY).getCode();
               int unknownCode = InputUtil.UNKNOWN_KEY.getCode();

               for (KeyBinding key : client.options.allKeys) {
                  if (key != COMBAT_LEFT_HOOK_KEY && key != COMBAT_RIGHT_HOOK_KEY && key != COMBAT_BLOCK_KEY && key != COMBAT_SHIFT_LOCK_KEY) {
                     int kc = KeyBindingHelper.getBoundKeyOf(key).getCode();
                     if (kc != unknownCode && (kc == leftCode || kc == rightCode || kc == blockCode)) {
                        while (key.wasPressed()) {
                        }
                     }
                  }
               }

               while (client.options.useKey.wasPressed()) {
               }

               while (client.options.togglePerspectiveKey.wasPressed()) {
               }
            }
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            boolean isCuffed = HandcuffsTracker.isClientCuffed(client.player.getUuid()) || client.player.getCommandTags().contains("handcuffed");
            if (isCuffed && (client.currentScreen instanceof InventoryScreen || client.currentScreen instanceof CreativeInventoryScreen)) {
               client.setScreen(null);
            }
         }
      });
      ScreenEvents.AFTER_INIT
         .register(
            (AfterInit)(client, screen, scaledWidth, scaledHeight) -> {
               if (screen instanceof GameMenuScreen) {
                  if (client.player != null) {
                     boolean isCuffedShifter = HandcuffsTracker.isClientCuffedShifter(client.player.getUuid());
                     boolean isKnockedOut = client.player.hasStatusEffect(StatusEffects.DARKNESS);
                     if (isCuffedShifter || isKnockedOut) {
                        String warningText = isCuffedShifter ? " (WILL LOSE SHIFTER)" : " (WILL DIE ON REJOIN)";

                        for (ClickableWidget widget : Screens.getButtons(screen)) {
                           if (widget instanceof ButtonWidget button) {
                              String text = button.getMessage().getString().toLowerCase();
                              if (text.contains("disconnect") || text.contains("save and quit") || text.contains("quit")) {
                                 button.setMessage(
                                    Text.literal("").append(button.getMessage()).append(Text.literal(warningText).formatted(Formatting.RED, Formatting.BOLD))
                                 );
                                 break;
                              }
                           }
                        }
                     }
                  }
               }
            }
         );
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               boolean reloadDown = isRawKeyDown(RELOAD_BLADE_KEY);
               if (reloadDown && !wasReloadKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  long currentTime = client.player.getWorld().getTime();
                  if (currentTime - lastBladeReloadTime >= 10L) {
                     boolean hasBladeInMainHand = client.player.getMainHandStack().getItem() instanceof BladeItem;
                     boolean hasBladeInOffHand = client.player.getOffHandStack().getItem() instanceof BladeItem;
                     if (hasBladeInMainHand || hasBladeInOffHand) {
                        ClientPlayNetworking.send(new BladeReloadPayload());
                        lastBladeReloadTime = currentTime;
                        ODMAnimationHandler.triggerReload();
                        ClientPlayNetworking.send(new AnimBroadcastPayload(10));
                     }
                  }
               }

               wasReloadKeyDown = reloadDown;
               boolean apgReloadDown = isRawKeyDown(APG_RELOAD_KEY);
               if (apgReloadDown && !wasApgReloadKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  long currentTime = client.player.getWorld().getTime();
                  if (currentTime - lastApgReloadTime >= 10L) {
                     boolean hasApgInMain = client.player.getMainHandStack().getItem() == DannysAot.APG_GUN;
                     boolean hasApgInOff = client.player.getOffHandStack().getItem() == DannysAot.APG_GUN;
                     if (hasApgInMain || hasApgInOff) {
                        ClientPlayNetworking.send(new APGReloadPayload());
                        lastApgReloadTime = currentTime;
                     }
                  }
               }

               wasApgReloadKeyDown = apgReloadDown;
               boolean titanShiftDown = isRawKeyDown(TITAN_SHIFT_KEY);
               if (titanShiftDown && !wasTitanShiftKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  if (!shiftingAllowedByGamerule && !client.player.isCreative() && !client.player.hasPermissionLevel(2)) {
                     client.player.sendMessage(Text.literal("Shifting is Disabled").styled(style -> style.withColor(Formatting.RED)), true);
                  } else {
                     ClientPlayNetworking.send(new TitanShiftPayload());
                  }
               }

               wasTitanShiftKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(TEASE_SHIFT_KEY);
               if (titanShiftDown && !wasTeaseShiftKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  if (!shiftingAllowedByGamerule && !client.player.isCreative() && !client.player.hasPermissionLevel(2)) {
                     client.player.sendMessage(Text.literal("Shifting is Disabled").styled(style -> style.withColor(Formatting.RED)), true);
                  } else {
                     ClientPlayNetworking.send(new TeaseShiftPayload());
                  }
               }

               wasTeaseShiftKeyDown = titanShiftDown;
               MinecraftClient mc = MinecraftClient.getInstance();
               boolean geassDown = mc.currentScreen == null && mc.getWindow() != null && InputUtil.isKeyPressed(mc.getWindow().getHandle(), 96);
               if (geassDown && !wasGeassKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  String pName = client.player.getGameProfile().getName();
                  if ("Speakor".equals(pName) || "Inrupt".equals(pName)) {
                     ClientPlayNetworking.send(new GeassTogglePayload());
                  }
               }

               wasGeassKeyDown = geassDown;
               titanShiftDown = isRawKeyDown(STEALTH_MODE_KEY);
               if (titanShiftDown && !wasStealthModeKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  ClientPlayNetworking.send(new StealthTogglePayload());
               }

               wasStealthModeKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(HOOD_TOGGLE_KEY);
               if (titanShiftDown && !wasHoodToggleKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  ClientPlayNetworking.send(new HoodTogglePayload());
               }

               wasHoodToggleKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(HIDE_SHIFTER_UI_KEY);
               if (titanShiftDown && !wasHideShifterUIKeyDown) {
                  ShifterStaminaHUD.toggleHidden();
               }

               wasHideShifterUIKeyDown = titanShiftDown;
               titanShiftDown = client.getWindow() != null && client.currentScreen == null && InputUtil.isKeyPressed(client.getWindow().getHandle(), 71);
               if (titanShiftDown && !wasFounderAbilityBarKeyDown && ShifterStaminaHUD.isFounding()) {
                  FounderAbilityBarState.toggle();
               }

               wasFounderAbilityBarKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(COMBAT_MODE_TOGGLE_KEY);
               if (titanShiftDown && !wasCombatModeToggleKeyDown) {
                  CombatModeState.toggle();
               }

               wasCombatModeToggleKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(COMBAT_SHIFT_LOCK_KEY);
               if (titanShiftDown && !wasCombatShiftLockKeyDown) {
                  CombatModeState.toggleShiftLock();
               }

               wasCombatShiftLockKeyDown = titanShiftDown;
               if (CombatModeState.isEnabled()
                  && client.player != null
                  && (
                     client.player.isDead()
                        || client.player.getVehicle() != null
                        || !DannysAot.isODMGear(client.player.getEquippedStack(EquipmentSlot.LEGS).getItem())
                  )) {
                  CombatModeState.disable();
               }

               if (CombatModeState.isEnabled()) {
                  CombatModeState.tickZoom();
               }

               FreezeVignetteClientData.tick();
               titanShiftDown = isRawKeyDown(THUNDER_SPEAR_LOAD_KEY);
               if (titanShiftDown && !wasThunderSpearKeyDown && client.getNetworkHandler() != null && client.player != null && client.currentScreen == null) {
                  geassDown = client.player.getMainHandStack().getItem() instanceof BladeItem;
                  boolean hasBladeInOffHand = client.player.getOffHandStack().getItem() instanceof BladeItem;
                  if (geassDown || hasBladeInOffHand) {
                     ClientPlayNetworking.send(new ThunderSpearLoadPayload());
                  }
               }

               wasThunderSpearKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(FLARE_LOAD_KEY);
               if (titanShiftDown && !wasFlareLoadKeyDown && client.getNetworkHandler() != null && client.player != null && client.currentScreen == null) {
                  ItemStack flareStack = null;
                  if (client.player.getMainHandStack().getItem() instanceof FlareGunItem) {
                     flareStack = client.player.getMainHandStack();
                  } else if (client.player.getOffHandStack().getItem() instanceof FlareGunItem) {
                     flareStack = client.player.getOffHandStack();
                  }

                  if (flareStack != null) {
                     int currentOrd = FlareGunItem.getLoadedColorOrdinal(flareStack);
                     if (currentOrd >= 0) {
                        FlareGunItemModel.setTextureOverride(currentOrd, 23);
                     } else {
                        FlareGunItemModel.setTextureOverride(-1, 5);
                     }

                     ClientPlayNetworking.send(new FlareGunLoadPayload());
                  }
               }

               wasFlareLoadKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(FLARE_CYCLE_KEY);
               if (titanShiftDown && !wasFlareCycleKeyDown && client.getNetworkHandler() != null && client.player != null && client.currentScreen == null) {
                  ItemStack cycleFlareStack = null;
                  if (client.player.getMainHandStack().getItem() instanceof FlareGunItem) {
                     cycleFlareStack = client.player.getMainHandStack();
                  } else if (client.player.getOffHandStack().getItem() instanceof FlareGunItem) {
                     cycleFlareStack = client.player.getOffHandStack();
                  }

                  if (cycleFlareStack != null && FlareGunItem.isLoaded(cycleFlareStack)) {
                     int swapOrd = FlareGunItem.getLoadedColorOrdinal(cycleFlareStack);
                     FlareGunItemModel.setTextureOverride(swapOrd, 5);
                     ClientPlayNetworking.send(new FlareGunCyclePayload());
                  }
               }

               wasFlareCycleKeyDown = titanShiftDown;
               TitanDashClientData.clientTick();
               titanShiftDown = isRawKeyDown(AWAKENED_POWER_KEY);
               if (titanShiftDown && !wasAwakenedPowerKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  ClientPlayNetworking.send(new AwakenedPowerPayload());
               }

               wasAwakenedPowerKeyDown = titanShiftDown;
               titanShiftDown = isRawKeyDown(TITAN_ROAR_KEY);
               if (titanShiftDown && !wasTitanRoarKeyDown && client.getNetworkHandler() != null && client.player != null) {
                  ClientPlayNetworking.send(new TitanRoarPayload());
                  if (ShifterStaminaHUD.isBeastControlsVisible()) {
                     ShifterStaminaHUD.highlightAbility(0);
                  }
               }

               wasTitanRoarKeyDown = titanShiftDown;
               boolean[] abilityEdge = new boolean[9];
               KeyBinding[] abilityKeys = getAbilityKeys();

               for (int i = 0; i < 9; i++) {
                  boolean down = isRawKeyDown(abilityKeys[i]);
                  abilityEdge[i] = down && !wasAbilityKeyDown[i];
                  wasAbilityKeyDown[i] = down;
               }

               if (client.player != null && client.getNetworkHandler() != null && FounderAbilityBarState.isActive()) {
                  for (int i = 0; i < 9; i++) {
                     if (abilityEdge[i]) {
                        ClientPlayNetworking.send(new FounderHumanAbilityPayload(i + 1));
                        if (i == 3) {
                           FounderAbilityBarState.toggleScope();
                        }

                        if (i == 0 || i == 1 || i == 2 || i == 4) {
                           FounderPlayerAnimationHandler.triggerAirPunch();
                        }
                     }
                  }
               }

               if (client.player != null
                  && (
                     client.player.getVehicle() instanceof AttackTitanEntity
                        || client.player.getVehicle() instanceof ArmoredTitanEntity
                        || client.player.getVehicle() instanceof FemaleTitanEntity
                        || client.player.getVehicle() instanceof BeastTitanEntity
                        || client.player.getVehicle() instanceof WarhammerTitanEntity
                  )) {
                  geassDown = client.options.attackKey.isPressed();
                  if (geassDown && !wasAttackKeyDown && client.getNetworkHandler() != null) {
                     ClientPlayNetworking.send(new TitanAttackPayload());
                  }

                  wasAttackKeyDown = geassDown;
                  boolean isSprintKeyDown = client.options.sprintKey.isPressed();
                  boolean hasMovementInput = client.player.input.movementForward != 0.0F || client.player.input.movementSideways != 0.0F;
                  boolean wantsSprint = isSprintKeyDown && hasMovementInput;
                  if (wantsSprint != lastSprintState) {
                     if (client.getNetworkHandler() != null) {
                        ClientPlayNetworking.send(new TitanSprintPayload(wantsSprint));
                     }

                     lastSprintState = wantsSprint;
                  }

                  boolean isArmKeyDown = client.options.useKey.isPressed();
                  if (isArmKeyDown != lastArmState) {
                     if (client.getNetworkHandler() != null) {
                        ClientPlayNetworking.send(new TitanArmPayload(isArmKeyDown));
                     }

                     lastArmState = isArmKeyDown;
                  }

                  boolean isJumpKeyDown = client.options.jumpKey.isPressed();
                  if (isJumpKeyDown && !wasJumpKeyDown) {
                     if (client.getNetworkHandler() != null) {
                        ClientPlayNetworking.send(new TitanJumpPayload());
                     }

                     if (client.player.getVehicle() instanceof WarhammerTitanEntity whTitan) {
                        whTitan.triggerJump(client.player);
                     } else if (client.player.getVehicle() instanceof ArmoredTitanEntity armoredTitan) {
                        armoredTitan.triggerJump(client.player);
                     } else if (client.player.getVehicle() instanceof AttackTitanEntity attackTitan) {
                        attackTitan.triggerJump(client.player);
                     } else if (client.player.getVehicle() instanceof FemaleTitanEntity femaleTitan) {
                        femaleTitan.triggerJump(client.player);
                     } else if (client.player.getVehicle() instanceof BeastTitanEntity beastTitan) {
                        beastTitan.triggerJump(client.player);
                     }
                  }

                  wasJumpKeyDown = isJumpKeyDown;
                  if (client.getNetworkHandler() != null && !FounderAbilityBarState.isActive()) {
                     for (int ix = 0; ix < 9; ix++) {
                        if (abilityEdge[ix]) {
                           if (ix == 0
                              && JawLockOnTracker.hasTarget()
                              && client.player.getVehicle() instanceof TestShifterTitanEntity
                              && !(client.player.getVehicle() instanceof CartShifterTitanEntity)) {
                              ClientPlayNetworking.send(new JawPounceTargetPayload(JawLockOnTracker.getTargetId()));
                           }

                           ClientPlayNetworking.send(new TitanAbilityPayload(ix + 1));
                           if (client.player.getVehicle() instanceof BeastTitanEntity) {
                              ShifterStaminaHUD.highlightAbility(ix + 1);
                           }

                           if (ix == 0 && client.player.getVehicle() instanceof TestShifterTitanEntity jaw && !(jaw instanceof CartShifterTitanEntity)) {
                              Vec3d aim = JawLockOnTracker.aimDirection(jaw);
                              jaw.startPounceLeapClient(aim.x, aim.z, JawLockOnTracker.getTargetId());
                           }
                        }
                     }
                  }

                  isJumpKeyDown = isRawKeyDown(SHIFTER_DODGE_KEY);
                  if (isJumpKeyDown && !wasShifterDodgeKeyDown && client.getNetworkHandler() != null) {
                     float fwd = client.player.input.movementForward;
                     float strafe = client.player.input.movementSideways;
                     boolean hasInput = Math.abs(fwd) > 0.01F || Math.abs(strafe) > 0.01F;
                     boolean crouching = client.player.isSneaking();
                     if (hasInput && !crouching) {
                        int dir;
                        if (Math.abs(fwd) >= Math.abs(strafe)) {
                           dir = fwd > 0.0F ? 1 : 0;
                        } else {
                           dir = strafe > 0.0F ? 2 : 3;
                        }

                        ClientPlayNetworking.send(new ShifterDodgePayload(dir));
                     }
                  }

                  wasShifterDodgeKeyDown = isJumpKeyDown;
               } else if (client.player != null && client.player.getVehicle() instanceof ColossalTitanEntity) {
                  geassDown = client.options.attackKey.isPressed();
                  if (geassDown && !wasAttackKeyDown && client.getNetworkHandler() != null) {
                     ClientPlayNetworking.send(new TitanAttackPayload());
                  }

                  wasAttackKeyDown = geassDown;
                  if (client.getNetworkHandler() != null) {
                     for (int ixx = 0; ixx < 9; ixx++) {
                        if (abilityEdge[ixx]) {
                           ClientPlayNetworking.send(new TitanAbilityPayload(ixx + 1));
                        }
                     }
                  }
               } else {
                  wasAttackKeyDown = false;
                  lastSprintState = false;
                  lastArmState = false;
                  if (ShifterStaminaHUD.isBeastControlsVisible() && client.getNetworkHandler() != null) {
                     for (int ixxx = 0; ixxx < 9; ixxx++) {
                        if (abilityEdge[ixxx]) {
                           ClientPlayNetworking.send(new TitanAbilityPayload(ixxx + 1));
                           ShifterStaminaHUD.highlightAbility(ixxx + 1);
                        }
                     }
                  }

                  if (client.getNetworkHandler() != null && client.player != null && client.player.isSneaking()) {
                     geassDown = client.options.attackKey.isPressed();
                     boolean useKeyDown = client.options.useKey.isPressed();
                     if (!thunderSpearsAllowedByGamerule) {
                        if (geassDown && !wasAttackKeyDownForSpear || useKeyDown && !wasUseKeyDownForSpear) {
                           client.player.sendMessage(Text.literal("Thunder Spears have been disabled").styled(style -> style.withColor(Formatting.RED)), true);
                        }

                        wasAttackKeyDownForSpear = geassDown;
                        wasUseKeyDownForSpear = useKeyDown;
                     } else {
                        boolean mainIsRight = client.player.getMainArm() == Arm.RIGHT;
                        Hand leftHand = mainIsRight ? Hand.OFF_HAND : Hand.MAIN_HAND;
                        Hand rightHand = mainIsRight ? Hand.MAIN_HAND : Hand.OFF_HAND;
                        boolean firedAny = false;
                        boolean firedLeft = false;
                        boolean firedRight = false;
                        if (geassDown && !wasAttackKeyDownForSpear) {
                           ItemStack leftStack = client.player.getStackInHand(leftHand);
                           if (leftStack.getItem() instanceof BladeItem && BladeItem.hasThunderSpear(leftStack)) {
                              ThunderSpearAttachmentTracker.instantFire(leftHand);
                              firedLeft = true;
                              firedAny = true;
                           }
                        }

                        if (useKeyDown && !wasUseKeyDownForSpear) {
                           ItemStack rightStack = client.player.getStackInHand(rightHand);
                           if (rightStack.getItem() instanceof BladeItem && BladeItem.hasThunderSpear(rightStack)) {
                              ThunderSpearAttachmentTracker.instantFire(rightHand);
                              firedRight = true;
                              firedAny = true;
                           }
                        }

                        if (firedLeft && firedRight) {
                           ODMAnimationHandler.triggerDualThunderSpear();
                           ClientPlayNetworking.send(new AnimBroadcastPayload(9));
                        } else if (firedLeft) {
                           ODMAnimationHandler.triggerThunderSpear(true);
                           ClientPlayNetworking.send(new AnimBroadcastPayload(8));
                        } else if (firedRight) {
                           ODMAnimationHandler.triggerThunderSpear(false);
                           ClientPlayNetworking.send(new AnimBroadcastPayload(7));
                        }

                        if (firedAny) {
                           client.player.playSound(ModSounds.TRIGGER, 1.0F, 1.5F);
                           if (firedLeft && firedRight) {
                              CameraShakeHandler.triggerThunderDip(2);
                           } else if (firedLeft) {
                              CameraShakeHandler.triggerThunderDip(1);
                           } else {
                              CameraShakeHandler.triggerThunderDip(-1);
                           }

                           BoostFovHandler.triggerThunderSpearFireFov();
                        }

                        wasAttackKeyDownForSpear = geassDown;
                        wasUseKeyDownForSpear = useKeyDown;
                     }
                  } else if (client.player != null) {
                     wasAttackKeyDownForSpear = client.options.attackKey.isPressed();
                     wasUseKeyDownForSpear = client.options.useKey.isPressed();
                  }
               }
            }
         );
      ODMTickHandler.register();
      HomelanderFlightHandler.register();
      AtrainSpeedHandler.register();
      TranslucentInputHandler.register();
      ButcherTentacleInputHandler.register();
      ButcherGrabInputHandler.register();
      ButcherTentacleRenderer.register();
      AtrainSpeedAfterImageHandler.register();
      AtrainAirMomentumHandler.register();
      SonicBoomHandler.register();
      HomelanderLaserInputHandler.register();
      HomelanderLaserShakeHandler.register();
      HomelanderXrayHandler.register();
      SoldierboyInputHandler.register();
      SoldierboyChargeParticleHandler.register();
      SoldierboyNeckGrabInputHandler.register();
      SoldierboyNeckGrabClientHandler.register();
      HomelanderAttackInputHandler.register();
      HomelanderGrabClientHandler.register();
      HomelanderGrabbedCameraTickHandler.register();
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, world, hand, entity, hitResult) -> {
         if (!world.isClient()) {
            return ActionResult.PASS;
         } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player != mc.player) {
               return ActionResult.PASS;
            } else if (BloodlineClientData.get(player.getUuid()) != BloodlineType.HOMELANDER) {
               return ActionResult.PASS;
            } else {
               return !HomelanderFlightHandler.isFlying(player.getUuid()) ? ActionResult.PASS : ActionResult.FAIL;
            }
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> ODMSoundManager.tickRemoteSounds());
      ClientPlayNetworking.registerGlobalReceiver(
         ODMHookSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> RemoteHookTracker.updateFromPayload(payload))
      );
      ClientPlayNetworking.registerGlobalReceiver(BloodmoonSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         BloodmoonClientState.setActive(payload.active());
         BloodmoonMusicHandler.setActive(payload.active());
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         FogSyncPayload.TYPE,
         (payload, context) -> context.client().execute(() -> FogClientState.set(payload.fogActive(), payload.breachedWalls(), payload.breachedDistricts()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         TitanBloodlineSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> TitanBloodlineClientData.setActive(payload.active()))
      );
      ClientPlayNetworking.registerGlobalReceiver(TitanDashSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         ClientPlayerEntity dasher = context.client().player;
         if (payload.phase() == 1 && !TitanDashClientData.isCharging()) {
            TitanDashPlayerAnimationHandler.triggerDash();
            if (dasher != null) {
               TitanSurgeEffectTracker.play(dasher);
            }
         }

         if (payload.phase() == 0 && dasher != null) {
            TitanSurgeEffectTracker.stop(dasher.getId());
         }

         TitanDashClientData.update(payload.charge(), payload.maxCharge(), payload.phase(), payload.dirX(), payload.dirY(), payload.dirZ());
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         BloodmoonMusicPayload.TYPE, (payload, context) -> context.client().execute(() -> BloodmoonMusicHandler.setTrack(payload.trackIndex()))
      );
      BloodmoonMusicHandler.register();
      ClientPlayNetworking.registerGlobalReceiver(
         APGAimSyncPayload.TYPE,
         (payload, context) -> context.client().execute(() -> RemoteAPGAimTracker.updateState(payload.playerId(), payload.mainAiming(), payload.offAiming()))
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> RemoteAPGAimTracker.tick());
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               boolean suppress = FogClientState.isActive()
                  && client.world != null
                  && client.player != null
                  && client.world.getRegistryKey() == FOG_PARADIS
                  && FogClientState.isSpawnZone(client.player.getBlockX(), client.player.getBlockZ());
               DistantHorizonsFogCompat.setSuppressed(suppress);
            }
         );
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         RemoteHookTracker.clear();
         RemoteAPGAimTracker.clear();
         HomelanderLaserClientHandler.clearAll();
         SoldierboyClientHandler.clearAll();
         SoldierboyLaserSoundManager.clearAll();
         SoldierboyNeckGrabClientHandler.clearAll();
         SoldierboyPlayerAnimationHandler.clearAll();
         TitanBloodlineClientData.setActive(false);
         TitanDashClientData.reset();
         TitanSurgeEffectTracker.reset();
         FogClientState.set(false, new int[0], new int[0]);
         DistantHorizonsFogCompat.restore();
      });
      ClientEntityEvents.ENTITY_UNLOAD.register((Unload)(entity, world) -> {
         if (entity instanceof PlayerEntity) {
            RemoteHookTracker.removePlayer(entity.getId());
            RemoteAPGAimTracker.removePlayer(entity.getId());
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(
         PoweredVillagerSyncPayload.TYPE, (payload, context) -> PoweredVillagerParticleHandler.handleSync(payload, context)
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderFlyStatePayload.TYPE,
         (payload, context) -> context.client()
            .execute(() -> HomelanderFlightHandler.onRemoteFlyState(payload.playerUuid(), payload.flying(), payload.initialTakeoff()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         AtrainSpeedStatePayload.TYPE, (payload, context) -> context.client().execute(() -> AtrainSpeedClientState.set(payload.playerUuid(), payload.active()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderLaserActivePayload.TYPE,
         (payload, context) -> context.client().execute(() -> HomelanderLaserClientHandler.setActive(payload.playerUuid(), payload.active()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ButcherTentacleBroadcastPayload.TYPE,
         (payload, context) -> context.client()
            .execute(() -> ButcherTentacleClientHandler.start(payload.firerId(), payload.startTick(), payload.dirX(), payload.dirY(), payload.dirZ()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ButcherGrabStatePayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> ButcherGrabClientState.update(payload.firerId(), payload.active(), payload.dirX(), payload.dirY(), payload.dirZ(), payload.distance())
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderLaserDirectionBroadcastPayload.TYPE,
         (payload, context) -> context.client()
            .execute(() -> HomelanderLaserClientHandler.setDirection(payload.firerId(), new Vec3d(payload.dirX(), payload.dirY(), payload.dirZ())))
      );
      ClientPlayNetworking.registerGlobalReceiver(SoldierboyChargeStatePayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (context.client().world != null) {
            SoldierboyClientHandler.setRemoteCharging(payload.playerUuid(), payload.ability(), payload.charging(), context.client().world.getTime());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         SoldierboyLaserActivePayload.TYPE,
         (payload, context) -> context.client().execute(() -> SoldierboyClientHandler.setLaserFiring(payload.playerUuid(), payload.active()))
      );
      ClientPlayNetworking.registerGlobalReceiver(SoldierboyNeckGrabStatePayload.TYPE, (payload, context) -> context.client().execute(() -> {
         SoldierboyPlayerAnimationHandler.setNeckGrabActive(payload.grabberUuid(), payload.active());
         SoldierboyNeckGrabClientHandler.setActive(payload.grabberUuid(), payload.victimEntityId(), payload.active());
         if (context.client().player != null && payload.grabberUuid().equals(context.client().player.getUuid())) {
            SoldierboyNeckGrabInputHandler.setLocalGrabActive(payload.active());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(SoldierboyBlastShakePayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (context.client().player != null) {
            double dx = payload.x() - context.client().player.getX();
            double dy = payload.y() - context.client().player.getY();
            double dz = payload.z() - context.client().player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = 150.0;
            if (!(distSq > maxDist * maxDist)) {
               float falloff = 1.0F - (float)(Math.sqrt(distSq) / maxDist);
               CameraShakeHandler.triggerEarthquakeShake(20, 2.0F * falloff);
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(ShiftShakePayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (context.client().player != null) {
            double dx = payload.x() - context.client().player.getX();
            double dy = payload.y() - context.client().player.getY();
            double dz = payload.z() - context.client().player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = 100.0;
            if (!(distSq > maxDist * maxDist)) {
               float falloff = 1.0F - (float)(Math.sqrt(distSq) / maxDist);
               CameraShakeHandler.triggerEarthquakeShake(70, 1.0F * falloff, 1, 1.5F);
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(TitanImpactShakePayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (context.client().player != null) {
            double dx = payload.x() - context.client().player.getX();
            double dy = payload.y() - context.client().player.getY();
            double dz = payload.z() - context.client().player.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            double maxDist = 60.0;
            if (!(distSq > maxDist * maxDist)) {
               float falloff = 1.0F - (float)(Math.sqrt(distSq) / maxDist);
               CameraShakeHandler.triggerEarthquakeShake(25, payload.intensity() * falloff, 1, 1.5F);
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderGrabSyncPayload.TYPE,
         (payload, context) -> context.client().execute(() -> HomelanderGrabClientHandler.onSync(payload.grabberUuid(), payload.victimEntityId()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderFlightInputBroadcastPayload.TYPE,
         (payload, context) -> context.client().execute(() -> HomelanderPlayerAnimationHandler.onRemoteInputBits(payload.playerUuid(), payload.bits()))
      );
      ClientPlayNetworking.registerGlobalReceiver(HomelanderAttackBroadcastPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (context.client().world != null) {
            if (context.client().world.getPlayerByUuid(payload.attackerUuid()) instanceof AbstractClientPlayerEntity acp) {
               HomelanderPlayerAnimationHandler.triggerAttack(acp, payload.attackType(), 5);
            }
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderGrabIntentBroadcastPayload.TYPE,
         (payload, context) -> context.client().execute(() -> HomelanderPlayerAnimationHandler.setRemoteGrabIntent(payload.playerUuid(), payload.wantsGrab()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderGrabRotationSyncPayload.TYPE, (payload, context) -> HomelanderGrabClientHandler.onGrabberRotationSync(payload.yaw(), payload.pitch())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         EffectPayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> {
                  if (context.client().world != null) {
                     if ("blood".equals(payload.effectType())) {
                        ShiftParticleHelper.spawnBloodParticle(context.client().world, payload.x(), payload.y(), payload.z(), payload.scale());
                     } else if ("roar".equals(payload.effectType())) {
                        ShiftParticleHelper.spawnRoarParticle(context.client().world, payload.x(), payload.y(), payload.z(), payload.scale());
                     } else if ("royal_shout".equals(payload.effectType())) {
                        CameraShakeHandler.triggerRoyalShoutShake();
                        BoostFovHandler.triggerRoyalShoutFov();
                     } else if ("command_stop".equals(payload.effectType())) {
                        CameraShakeHandler.triggerCommandShake();
                        BoostFovHandler.triggerCommandFov();
                     } else if ("command_continue".equals(payload.effectType())) {
                        BoostFovHandler.triggerCommandFov();
                     } else if ("thunder_spear_lodge".equals(payload.effectType())) {
                        int lodgeEntityId = (int)payload.scale();
                        FlyingThunderSpearTracker.onServerLodge(payload.x(), payload.y(), payload.z(), lodgeEntityId);
                        ThunderSpearAttachmentTracker.endOldestHoldover();
                     } else if ("thunder_spear_explode".equals(payload.effectType())) {
                        FlyingThunderSpearTracker.onServerExplosion(payload.x(), payload.y(), payload.z());
                        ClientPlayerEntity lp = context.client().player;
                        if (lp != null) {
                           double dist = lp.getPos().distanceTo(new Vec3d(payload.x(), payload.y(), payload.z()));
                           if (dist < 100.0) {
                              float normalizedDist = (float)(dist / 100.0);
                              float intensity = (1.0F - normalizedDist * normalizedDist) * 0.6F;
                              if (intensity > 0.01F) {
                                 CameraShakeHandler.triggerShake(intensity);
                              }
                           }
                        }

                        if (context.client().world != null) {
                           double ex = payload.x();
                           double ey = payload.y();
                           double ez = payload.z();
                           Random rng = new Random();

                           for (int i = 0; i < 24; i++) {
                              double theta = rng.nextDouble() * Math.PI * 2.0;
                              double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);
                              double speed = 0.3 + rng.nextDouble() * 0.5;
                              double vx = Math.sin(phi) * Math.cos(theta) * speed;
                              double vy = Math.sin(phi) * Math.sin(theta) * speed * 0.6 + 0.15;
                              double vz = Math.cos(phi) * speed;
                              context.client()
                                 .world
                                 .addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, ex + vx * 0.3, ey + vy * 0.3, ez + vz * 0.3, vx * 0.4, vy * 0.4, vz * 0.4);
                           }

                           for (int i = 0; i < 16; i++) {
                              double ox = (rng.nextDouble() - 0.5) * 1.5;
                              double oy = (rng.nextDouble() - 0.5) * 1.5;
                              double oz = (rng.nextDouble() - 0.5) * 1.5;
                              context.client().world.addParticle(ParticleTypes.FLAME, ex + ox, ey + oy, ez + oz, ox * 0.15, oy * 0.15 + 0.05, oz * 0.15);
                           }

                           for (int i = 0; i < 12; i++) {
                              double theta = rng.nextDouble() * Math.PI * 2.0;
                              double phi = Math.acos(2.0 * rng.nextDouble() - 1.0);
                              double speed = 0.2 + rng.nextDouble() * 0.6;
                              double vx = Math.sin(phi) * Math.cos(theta) * speed;
                              double vy = Math.sin(phi) * Math.sin(theta) * speed + 0.3;
                              double vz = Math.cos(phi) * speed;
                              context.client().world.addParticle(ParticleTypes.LAVA, ex + vx * 0.2, ey + vy * 0.2, ez + vz * 0.2, vx, vy, vz);
                           }

                           for (int i = 0; i < 8; i++) {
                              double ox = (rng.nextDouble() - 0.5) * 0.8;
                              double oy = rng.nextDouble() * 0.5;
                              double oz = (rng.nextDouble() - 0.5) * 0.8;
                              context.client().world.addParticle(ParticleTypes.LARGE_SMOKE, ex + ox, ey + oy, ez + oz, ox * 0.05, 0.1 + oy * 0.1, oz * 0.05);
                           }
                        }
                     } else if ("blade_swing".equals(payload.effectType())) {
                        ClientPlayerEntity player = context.client().player;
                        if (player != null) {
                           ItemStack mainHand = player.getMainHandStack();
                           ItemStack offHand = player.getOffHandStack();
                           boolean anyBlade = false;
                           if (mainHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(mainHand) != BladeItem.BladeState.EMPTY) {
                              BladeAnimationHandler.triggerSwing(Hand.MAIN_HAND);
                              anyBlade = true;
                           }

                           if (offHand.getItem() == DannysAot.BLADE && BladeItem.getBladeState(offHand) != BladeItem.BladeState.EMPTY) {
                              BladeAnimationHandler.triggerSwing(Hand.OFF_HAND);
                              anyBlade = true;
                           }

                           if (anyBlade) {
                              float swingPitch = 0.65F + player.getRandom().nextFloat() * 0.05F;
                              context.client()
                                 .getSoundManager()
                                 .play(
                                    new EntityTrackingSoundInstance(
                                       ModSounds.BLADE_SWING, SoundCategory.PLAYERS, 0.1F, swingPitch, player, context.client().world.random.nextLong()
                                    )
                                 );
                              CameraShakeHandler.triggerSwingDip();
                              if (!ODMAnimationHandler.isLocalPlayerAttacking()) {
                                 ODMAnimationHandler.triggerAttack();
                              }
                           }
                        }
                     } else if ("teleport".equals(payload.effectType())) {
                        int entityId = (int)payload.scale();
                        if (context.client().world != null) {
                           Entity entity = context.client().world.getEntityById(entityId);
                           if (entity != null) {
                              ShiftParticleHelper.spawnTeleportEffect(context.client().world, entity);
                           }
                        }
                     }
                  }
               }
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(StaminaSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         if (payload.stamina() < 0.0F) {
            ShifterStaminaHUD.clearStamina();
         } else {
            ShifterStaminaHUD.updateStamina(payload.stamina(), payload.maxStamina(), payload.hasBeast(), payload.hasFounding());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         FounderAnimPayload.TYPE, (payload, context) -> context.client().execute(() -> FounderPlayerAnimationHandler.playRemote(payload.entityId()))
      );
      ClientPlayNetworking.registerGlobalReceiver(TitanDashAnimPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         TitanDashPlayerAnimationHandler.playRemote(payload.entityId());
         TitanSurgeEffectTracker.playRemote(payload.entityId());
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         ZekesGlowPayload.TYPE, (payload, context) -> context.client().execute(() -> ZekesGlassesGlowTracker.updateGlowingEntities(payload.entityIds()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         TargetGlowPayload.TYPE, (payload, context) -> context.client().execute(() -> TargetGlowTracker.update(payload.entityIds()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         TargetModeSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> ShifterStaminaHUD.setTargetMode(payload.mode()))
      );
      ClientPlayNetworking.registerGlobalReceiver(AwakenToggleSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         AwakenedPowerClientData.setActiveFor(payload.playerUuid(), payload.active());
         if (context.client().player != null && payload.playerUuid().equals(context.client().player.getUuid())) {
            ShifterStaminaHUD.setAwakenToggleActive(payload.active());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(BloodlineSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         UUID targetUuid = payload.playerUuid();
         BloodlineType type = BloodlineType.fromOrdinal(payload.bloodlineOrdinal());
         if (type != null) {
            BloodlineClientData.set(targetUuid, type);
         } else {
            BloodlineClientData.clear(targetUuid);
         }

         ODMAnimationHandler.clearState(targetUuid);
         if (context.client().player != null && targetUuid.equals(context.client().player.getUuid()) && type != BloodlineType.ACKERMAN) {
            AwakenedPowerClientData.clear();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(AwakenedPowerSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> {
         AwakenedPowerClientData.update(payload.charge(), payload.maxCharge(), payload.active());
         if (context.client().player != null) {
            AwakenedPowerClientData.setActiveFor(context.client().player.getUuid(), payload.active());
            AwakenedPowerTracker.setClientActivePlayer(payload.active() ? context.client().player.getUuid() : null);
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         HoodSyncPayload.TYPE, (payload, context) -> context.client().execute(() -> HoodTracker.setClientHoodState(payload.playerUuid(), payload.hoodUp()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         ShifterMarkSyncPayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> ShifterMarkTracker.setClientState(
                  payload.playerUuid(), payload.markType(), payload.dismountedAtGameTime(), payload.fadeStartedAtGameTime()
               )
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HomelanderBloodSyncPayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> HomelanderBloodTracker.setClientState(
                  payload.playerUuid(), payload.stage(), payload.startedAtGameTime(), payload.fadeStartedAtGameTime(), payload.fadeDurationTicks()
               )
            )
      );
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         ShifterMarkTracker.clearClientStates();
         HomelanderBloodTracker.clearClientStates();
      });
      ClientPlayNetworking.registerGlobalReceiver(
         GeassControlPayload.TYPE,
         (payload, context) -> context.client().execute(() -> GeassClientState.update(payload.commandType(), payload.targetEntityId()))
      );
      ClientPlayNetworking.registerGlobalReceiver(MindControlRotationSyncPayload.TYPE, (payload, context) -> {
         if (GeassClientState.isMindControlTarget) {
            GeassClientState.prevMcInputYaw = GeassClientState.mcInputYaw;
            GeassClientState.prevMcInputPitch = GeassClientState.mcInputPitch;
            GeassClientState.mcInputYaw = payload.yaw();
            GeassClientState.mcInputPitch = payload.pitch();
            GeassClientState.mcInputPayloadTimeNano = System.nanoTime();
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(
         MindControlInputPayload.TYPE,
         (payload, context) -> context.client()
            .execute(
               () -> {
                  MinecraftClient mc = MinecraftClient.getInstance();
                  if (mc.player != null) {
                     if (GeassClientState.isMindControlTarget || mc.cameraEntity == null || mc.cameraEntity == mc.player) {
                        GeassClientState.applyMindControlInput(
                           payload.yaw(),
                           payload.pitch(),
                           payload.forward(),
                           payload.strafe(),
                           payload.jumping(),
                           payload.sprinting(),
                           payload.shifting(),
                           payload.selectedSlot(),
                           payload.posX(),
                           payload.posY(),
                           payload.posZ()
                        );
                        if (payload.swing()) {
                           GeassClientState.mcInputSwing = true;
                        }

                        GeassClientState.mcInputInventoryOpen = payload.inventoryOpen();
                     }
                  }
               }
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         HandcuffsSyncPayload.TYPE,
         (payload, context) -> context.client()
            .execute(() -> HandcuffsTracker.setClientCuffedState(payload.playerUuid(), payload.cuffed(), payload.hasShifter()))
      );
      TitanBoneDebugger.register();
      HookLineRenderer.register();
      HomelanderLaserRenderer.register();
      SoldierboyLaserRenderer.register();
      SoldierboyLaserSoundManager.register();
      SoldierboyShakeHandler.register();
      CrystalCableRenderer.register();
      CameraTiltHandler.register();
      BoostFovHandler.register();
      CameraShakeHandler.register();
      BladeAnimationHandler.register();
      APGInputHandler.register();
      ODMAnimationHandler.register();
      ShifterPlayerAnimationHandler.register();
      HomelanderPlayerAnimationHandler.register();
      SoldierboyPlayerAnimationHandler.register();
      HomelanderGrabbedAnimationHandler.register();
      FounderPlayerAnimationHandler.register();
      TitanDashPlayerAnimationHandler.register();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> ThunderSpearAttachmentTracker.tick());
      BiteFirstPersonRenderer.register();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null) {
            if (client.player.getVehicle() instanceof TestShifterTitanEntity testTitan) {
               if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
                  testTitan.snapYawToRider(client.player.getYaw());
               }
            }
         }
      });
      FlyingThunderSpearTracker.register();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> FlyingThunderSpearTracker.tick());
      BladeEjectAnimationHandler.register();
      ODMGasHUD.register();
      CombatModeHUD.register();
      SadTitanClientHelper.register();
      FreezeVignetteHUD.register();
      ShifterStaminaHUD.register();
      ShifterHealthHUD.register();
      ShifterAbilityHUD.register();
      AwakenedPowerHUD.register();
      TitanShiftHandler.register();
      PreshiftEffectTracker.register();
      TitanSurgeEffectTracker.register();
      JawLockOnTracker.register();
      TitanShiftSoundHandler.register();
      ClientPlayNetworking.registerGlobalReceiver(ShiftLightningPayload.TYPE, (payload, context) -> ShiftLightningClientTracker.mark(payload.entityId()));
      PathsAmbientParticleHandler.register();
      VillagerTransformEffectHandler.register();
      ColossalSteamHandler.register();
      ClientTickEvents.END_CLIENT_TICK.register(PoweredVillagerParticleHandler::tick);
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> ShifterStaminaHUD.tick());
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (PushAuraClientData.pushAuraEntityId != -1 && client.world != null) {
                  Entity holder = client.world.getEntityById(PushAuraClientData.pushAuraEntityId);
                  if (holder == null) {
                     PushAuraClientData.clear();
                  } else {
                     boolean isLocalFirstPerson = holder == client.player && client.options.getPerspective().isFirstPerson();
                     boolean shouldBeVisible = !isLocalFirstPerson;
                     if (shouldBeVisible && !PushAuraClientData.roarEffectVisible) {
                        ShiftParticleHelper.spawnDannyModeRoarBoundToEntity(client.world, holder);
                        PushAuraClientData.roarEffectVisible = true;
                        PushAuraClientData.roarTickCounter = 0;
                     } else if (!shouldBeVisible && PushAuraClientData.roarEffectVisible) {
                        ShiftParticleHelper.stopDannyModeRoarEffect();
                        PushAuraClientData.roarEffectVisible = false;
                     }

                     if (PushAuraClientData.roarEffectVisible) {
                        PushAuraClientData.roarTickCounter++;
                        if (PushAuraClientData.roarTickCounter >= 60) {
                           PushAuraClientData.roarTickCounter = 0;
                           ShiftParticleHelper.stopDannyModeRoarEffect();
                           ShiftParticleHelper.spawnDannyModeRoarBoundToEntity(client.world, holder);
                        }
                     }

                     if (PushAuraClientData.auraLoopSound == null) {
                        PushAuraClientData.auraLoopSound = new FadingLoopSound(
                           SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0F, 0.5F, holder, 0.05F, 0.1F, 40.0
                        );
                        client.getSoundManager().play(PushAuraClientData.auraLoopSound);
                     }
                  }
               }
            }
         );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.player != null && client.getNetworkHandler() != null) {
            if (PUSH_AURA_KEY != null) {
               while (PUSH_AURA_KEY.wasPressed()) {
                  ClientPlayNetworking.send(new PushAuraTogglePayload());
               }
            }

            int auraId = PushAuraClientData.pushAuraEntityId;
            if (auraId != -1) {
               Entity auraEntity = client.world == null ? null : client.world.getEntityById(auraId);
               if (auraEntity != null) {
                  if (auraId == client.player.getId()) {
                     boolean shift = client.options.sneakKey.isPressed();
                     if (shift && !PushAuraClientData.lastShiftState) {
                        long now = client.player.age;
                        if (now - PushAuraClientData.lastShiftPressTick <= 10L) {
                           ClientPlayNetworking.send(new RepelModeTogglePayload());
                           PushAuraClientData.lastShiftPressTick = -100L;
                        } else {
                           PushAuraClientData.lastShiftPressTick = now;
                        }
                     }

                     PushAuraClientData.lastShiftState = shift;
                  }

                  boolean crouching = auraEntity.isSneaking();
                  boolean attract = PushAuraClientData.clientAttractMode;
                  double radius = 7.5;
                  double coreRadius = 1.5;
                  double fullFreezeRadius = 1.25;
                  if (auraEntity != client.player) {
                     ClientPlayerEntity me = client.player;
                     double dx = me.getX() - auraEntity.getX();
                     double dy = me.getY() - auraEntity.getY();
                     double dz = me.getZ() - auraEntity.getZ();
                     double rawDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                     double distance = Math.max(rawDist, 0.5);
                     if (distance <= radius) {
                        if (crouching) {
                           double freezeFactor = distance <= fullFreezeRadius ? 0.0 : (distance - fullFreezeRadius) / (radius - fullFreezeRadius);
                           double newX = me.prevX + (me.getX() - me.prevX) * freezeFactor;
                           double newY = me.isOnGround() ? me.getY() : me.prevY + (me.getY() - me.prevY) * freezeFactor;
                           double newZ = me.prevZ + (me.getZ() - me.prevZ) * freezeFactor;
                           me.setPosition(newX, newY, newZ);
                           Vec3d vel = me.getVelocity();
                           double fvy = vel.y < 0.0 ? vel.y * freezeFactor : vel.y;
                           me.setVelocity(vel.x * freezeFactor, fvy, vel.z * freezeFactor);
                        } else if (FreezeVignetteClientData.isFrozen() && distance <= 2.5) {
                           double hDist = Math.sqrt(dx * dx + dz * dz);
                           double hDirX = hDist > 0.1 ? dx / hDist : 0.0;
                           double hDirZ = hDist > 0.1 ? dz / hDist : 0.0;
                           double targetX = auraEntity.getX() + hDirX * coreRadius;
                           double targetY = auraEntity.getY() + 1.0;
                           double targetZ = auraEntity.getZ() + hDirZ * coreRadius;
                           double toTX = targetX - me.getX();
                           double toTY = targetY - me.getY();
                           double toTZ = targetZ - me.getZ();
                           double distToT = Math.sqrt(toTX * toTX + toTY * toTY + toTZ * toTZ);
                           if (distToT < 0.5) {
                              me.setPosition(targetX, targetY, targetZ);
                              me.setVelocity(Vec3d.ZERO);
                           } else {
                              double speed = Math.min(distToT * 0.3, 1.5);
                              me.setVelocity(toTX / distToT * speed, toTY / distToT * speed, toTZ / distToT * speed);
                           }
                        } else {
                           double t = 1.0 - distance / radius;
                           double dirX = dx / distance;
                           double dirY = Math.max(0.02, dy / distance);
                           double dirZ = dz / distance;
                           Vec3d vel = me.getVelocity();
                           me.setVelocity(vel.x + dirX * t, vel.y + dirY * t * 0.3, vel.z + dirZ * t);
                        }
                     }
                  }

                  double sourceX = auraEntity.getX();
                  double sourceY = auraEntity.getY() + auraEntity.getHeight() * 0.5;
                  double sourceZ = auraEntity.getZ();
                  double radiusSq = radius * radius;
                  ParticleEngineAccessor engineAccessor = (ParticleEngineAccessor)client.particleManager;

                  for (Queue<Particle> queue : engineAccessor.getParticles().values()) {
                     for (Particle particle : queue) {
                        ParticleAccessor pa = (ParticleAccessor)particle;
                        double dx = pa.getX() - sourceX;
                        double dy = pa.getY() - sourceY;
                        double dz = pa.getZ() - sourceZ;
                        double distSq = dx * dx + dy * dy + dz * dz;
                        if (!(distSq > radiusSq) && !(distSq < 0.01)) {
                           double dist = Math.sqrt(distSq);
                           if (crouching) {
                              double hardFreeze = 1.25;
                              double freezeFactor = dist <= hardFreeze ? 0.0 : (dist - hardFreeze) / (radius - hardFreeze);
                              if (dist <= hardFreeze) {
                                 pa.setX(pa.getXo());
                                 pa.setY(pa.getYo());
                                 pa.setZ(pa.getZo());
                                 pa.setXd(0.0);
                                 pa.setYd(0.04 * pa.getGravity());
                                 pa.setZd(0.0);
                              } else {
                                 pa.setXd(pa.getXd() * freezeFactor);
                                 pa.setZd(pa.getZd() * freezeFactor);
                                 float gravity = pa.getGravity();
                                 if (freezeFactor < 0.05) {
                                    pa.setYd(0.04 * gravity * (1.0 - freezeFactor));
                                 } else {
                                    pa.setYd(pa.getYd() * freezeFactor);
                                 }
                              }
                           } else if (attract) {
                              double hDist = Math.sqrt(dx * dx + dz * dz);
                              if (hDist > coreRadius) {
                                 double speed = Math.min((hDist - coreRadius) * 0.25, 0.5);
                                 double hDirX = dx / hDist;
                                 double hDirZ = dz / hDist;
                                 pa.setXd(-hDirX * speed);
                                 pa.setYd(pa.getYd() * 0.5);
                                 pa.setZd(-hDirZ * speed);
                              } else {
                                 double vertSpeed = 0.3;
                                 pa.setYd(dy > 0.0 ? vertSpeed : -vertSpeed);
                                 pa.setXd(pa.getXd() * 0.1);
                                 pa.setZd(pa.getZd() * 0.1);
                              }
                           } else {
                              double t = 1.0 - dist / radius;
                              double dirX = dx / dist;
                              double dirY = dy / dist;
                              double dirZ = dz / dist;
                              pa.setXd(pa.getXd() + dirX * t);
                              pa.setYd(pa.getYd() + dirY * t);
                              pa.setZd(pa.getZd() + dirZ * t);
                           }
                        }
                     }
                  }
               }
            }
         }
      });
      EntityRendererRegistry.register(DannysAot.TITAN_DUMMY, TitanDummyRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_DUMMY_NAPE, TitanDummyNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_DUMMY_EYE, TitanDummyEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN, TitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.COLOSSAL_TITAN, ColossalTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.ATTACK_TITAN, AttackTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.TRIPLE_T_TITAN, TripleTTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.OGRE_SHIFTER_TITAN, OgreShifterTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.TEST_SHIFTER_TITAN, TestShifterTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.CART_SHIFTER_TITAN, CartShifterTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.SMALL_TITAN, SmallTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.SMALL_TITAN_NAPE, SmallTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.SMALL_TITAN_EYE, SmallTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRAWLER_TITAN, CrawlerTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRAWLER_TITAN_NAPE, CrawlerTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRAWLER_TITAN_EYE, CrawlerTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.SMALL_TITAN_2, SmallTitan2Renderer::new);
      EntityRendererRegistry.register(DannysAot.SMALL_TITAN_2_NAPE, SmallTitan2NapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.SMALL_TITAN_2_EYE, SmallTitan2EyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.YELLOW_TITAN, YellowTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.YELLOW_TITAN_NAPE, YellowTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.YELLOW_TITAN_EYE, YellowTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.FRITZ_TITAN, FritzTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.BEAST_TITAN, BeastTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.BEAST_TITAN_NAPE, BeastTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.BEAST_TITAN_EYE, BeastTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.BEAST_TITAN_GRAB, BeastTitanGrabRenderer::new);
      EntityRendererRegistry.register(DannysAot.FEMALE_TITAN_GRAB, FemaleTitanGrabRenderer::new);
      EntityRendererRegistry.register(DannysAot.ATTACK_TITAN_GRAB, AttackTitanGrabRenderer::new);
      EntityRendererRegistry.register(DannysAot.FRITZ_TITAN_NAPE, FritzTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.FRITZ_TITAN_EYE, FritzTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_BEARD, TitanBeardRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_BEARD_NAPE, TitanBeardNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_BEARD_EYE, TitanBeardEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_TROPICAL, TitanTropicalRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_TROPICAL_NAPE, TitanTropicalNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_TROPICAL_EYE, TitanTropicalEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.SAD_TITAN, SadTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.SAD_TITAN_NAPE, SadTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.SAD_TITAN_EYE, SadTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.ABNORMAL_TITAN, AbnormalTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.ABNORMAL_TITAN_NAPE, AbnormalTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRAWLING_ABNORMAL_TITAN, CrawlingAbnormalTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRAWLING_ABNORMAL_TITAN_NAPE, CrawlingAbnormalTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.CONNIE_FATHER, ConnieFatherRenderer::new);
      EntityRendererRegistry.register(DannysAot.CONNIE_FATHER_NAPE, ConnieFatherNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.CONNIE_FATHER_EYE, ConnieFatherEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.OGRE_TITAN, OgreTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.OGRE_TITAN_NAPE, OgreTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.FOUNDING_TITAN, FoundingTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_NAPE, TitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.TITAN_EYE, TitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.ATTACK_TITAN_NAPE, AttackTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.ATTACK_TITAN_EYE, AttackTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.FEMALE_TITAN, FemaleTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.FEMALE_TITAN_NAPE, FemaleTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.FEMALE_TITAN_EYE, FemaleTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.WARHAMMER_TITAN, WarhammerTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.WARHAMMER_TITAN_NAPE, WarhammerTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.WARHAMMER_TITAN_EYE, WarhammerTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.ARMORED_TITAN, ArmoredTitanRenderer::new);
      EntityRendererRegistry.register(DannysAot.ARMORED_TITAN_GRAB, ArmoredTitanGrabRenderer::new);
      EntityRendererRegistry.register(DannysAot.ARMORED_TITAN_NAPE, ArmoredTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.ARMORED_TITAN_LEG, ArmoredTitanLegRenderer::new);
      EntityRendererRegistry.register(DannysAot.ARMORED_TITAN_EYE, ArmoredTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.COLOSSAL_TITAN_NAPE, ColossalTitanNapeRenderer::new);
      EntityRendererRegistry.register(DannysAot.COLOSSAL_TITAN_EYE, ColossalTitanEyeRenderer::new);
      EntityRendererRegistry.register(DannysAot.COLOSSAL_TITAN_HAND, ColossalTitanHandRenderer::new);
      EntityRendererRegistry.register(DannysAot.THUNDER_SPEAR_ENTITY, ThunderSpearEntityRenderer::new);
      EntityRendererRegistry.register(DannysAot.ROCK_PROJECTILE, RockProjectileRenderer::new);
      EntityRendererRegistry.register(DannysAot.WARHAMMER_SPIKE, WarhammerSpikeRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRYSTAL_SHELL_WARHAMMER, CrystalShellWarhammerRenderer::new);
      EntityRendererRegistry.register(DannysAot.CRYSTAL_SHELL_FEMALE, FemaleCrystalShellRenderer::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.FLARE_GREEN_PARTICLE, FlareParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.FLARE_RED_PARTICLE, FlareParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.FLARE_BLACK_PARTICLE, FlareParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.FLARE_PURPLE_PARTICLE, FlareParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.FLARE_BLUE_PARTICLE, FlareParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.FLARE_YELLOW_PARTICLE, FlareParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.NAPE_STEAM_PARTICLE, NapeSteamParticle.Provider::new);
      ParticleFactoryRegistry.getInstance().register(DannysAot.SHIFTER_TRAIL_PARTICLE, sprites -> new NapeSteamParticle.Provider(sprites, 1.0F, 0.5F));
      ParticleFactoryRegistry.getInstance().register(DannysAot.PLAYER_DISMOUNT_PARTICLE, sprites -> new NapeSteamParticle.Provider(sprites, 0.5F, 0.5F));
      ParticleFactoryRegistry.getInstance()
         .register(DannysAot.NUKE_SMOKE_PARTICLE, sprites -> new NukeCloudParticle.Provider(sprites, NukeCloudParticle.Mode.SMOKE));
      ParticleFactoryRegistry.getInstance()
         .register(DannysAot.NUKE_FIRE_PARTICLE, sprites -> new NukeCloudParticle.Provider(sprites, NukeCloudParticle.Mode.FIRE));
      ParticleFactoryRegistry.getInstance()
         .register(DannysAot.NUKE_DUST_PARTICLE, sprites -> new NukeCloudParticle.Provider(sprites, NukeCloudParticle.Mode.DUST));
      ParticleFactoryRegistry.getInstance()
         .register(DannysAot.COLOSSAL_STEAM_PARTICLE, sprites -> new NukeCloudParticle.Provider(sprites, NukeCloudParticle.Mode.STEAM));
      ParticleFactoryRegistry.getInstance().register(DannysAot.COLOSSAL_NAPE_STEAM_PARTICLE, sprites -> new NapeSteamParticle.Provider(sprites, 3.0F, 1.0F));
      ParticleFactoryRegistry.getInstance().register(DannysAot.COLOSSAL_NAPE_TRAIL_PARTICLE, sprites -> new NapeSteamParticle.Provider(sprites, 3.0F, 0.5F));
      ParticleFactoryRegistry.getInstance().register(DannysAot.COLOSSAL_BODY_STEAM_PARTICLE, ColossalBodySteamParticle.Provider::new);
      EntityRendererRegistry.register(
         DannysAot.FLARE_PROJECTILE,
         ctx -> new EntityRenderer<FlareProjectileEntity>(ctx) {
            public Identifier getTexture(FlareProjectileEntity entity) {
               return new Identifier("dannys-aot", "textures/item/flare_gun_empty.png");
            }

            public void render(
               FlareProjectileEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
            ) {
            }
         }
      );
      EntityRendererRegistry.register(DannysAot.GIB, GibRenderer::new);
      EntityRendererRegistry.register(
         DannysAot.APG_PROJECTILE,
         ctx -> new EntityRenderer<APGProjectileEntity>(ctx) {
            public Identifier getTexture(APGProjectileEntity entity) {
               return new Identifier("dannys-aot", "textures/item/apg_cartridge.png");
            }

            public void render(
               APGProjectileEntity entity, float entityYaw, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight
            ) {
            }
         }
      );
      LivingEntityFeatureRendererRegistrationCallback.EVENT
         .register((LivingEntityFeatureRendererRegistrationCallback)(entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer playerRenderer) {
               registrationHelper.register(new ThunderSpearArmFeature(playerRenderer));
               registrationHelper.register(new ShifterMarkLayer(playerRenderer));
               registrationHelper.register(new ShifterMusclesLayer(playerRenderer));
               registrationHelper.register(new HomelanderBloodLayer(playerRenderer));
            }
         });
      BlockEntityRendererFactories.register(DannysAot.PARADIS_PORTAL_BLOCK_ENTITY, ParadisPortalRenderer::new);
      BlockEntityRendererFactories.register(DannysAot.REGIMENT_BANNER_BLOCK_ENTITY, RegimentBannerRenderer::new);
      BlockEntityRendererFactories.register(DannysAot.ARMOR_POTION_BLOCK_ENTITY, ArmorPotionBlockRenderer::new);
      BlockEntityRendererFactories.register(DannysAot.STRWS_BLOCK_ENTITY, StrwsBlockRenderer::new);
      ModelPredicateProviderRegistry.register(DannysAot.EMPTY_SYRINGE, new Identifier("pulling"), (stack, level, entity, seed) -> {
         if (entity == null) {
            return 0.0F;
         } else if (!entity.isUsingItem()) {
            return 0.0F;
         } else if (entity.getActiveItem() != stack) {
            return 0.0F;
         } else {
            return entity.isSneaking() ? 1.0F : 0.5F;
         }
      });
      ModelPredicateProviderRegistry.register(DannysAot.SYRINGE, new Identifier("pulling"), (stack, level, entity, seed) -> {
         if (entity == null) {
            return 0.0F;
         } else if (!entity.isUsingItem()) {
            return 0.0F;
         } else if (entity.getActiveItem() != stack) {
            return 0.0F;
         } else {
            return entity.isSneaking() ? 1.0F : 0.5F;
         }
      });
      ColorProviderRegistry.ITEM.register((ItemColorProvider)(stack, tintIndex) -> -1, new ItemConvertible[]{DannysAot.PURE_TITAN_SPAWN_EGG});
   }
}
