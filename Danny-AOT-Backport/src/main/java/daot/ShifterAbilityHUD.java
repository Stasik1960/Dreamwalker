package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ShifterAbilityHUD {
   private static final Identifier SLOT_UNSELECTED = new Identifier("dannys-aot", "textures/gui/abilities_slot_unselected.png");
   private static final Identifier SLOT_SELECTED = new Identifier("dannys-aot", "textures/gui/abilities_slot_selected.png");
   private static final Identifier ICON_GROUND_STOMP = new Identifier("dannys-aot", "textures/gui/ability_titan_ground_stomp.png");
   private static final Identifier ICON_BERSERK = new Identifier("dannys-aot", "textures/gui/ability_titan_berserk.png");
   private static final Identifier ICON_POUNCE = new Identifier("dannys-aot", "textures/gui/ability_titan_pounce.png");
   private static final Identifier ICON_KICKBACK = new Identifier("dannys-aot", "textures/gui/ability_titan_kickback.png");
   private static final Identifier ICON_JAW_NAPE_HARDENING = new Identifier("dannys-aot", "textures/gui/ability_titan_jaw_nape_hardening.png");
   private static final Identifier ICON_SLASH_BARRAGE = new Identifier("dannys-aot", "textures/gui/ability_titan_slash_barrage.png");
   private static final Identifier ICON_CHOMP = new Identifier("dannys-aot", "textures/gui/ability_titan_chomp.png");
   private static final Identifier ICON_HEAVY_PUNCH = new Identifier("dannys-aot", "textures/gui/ability_titan_heavy_punch.png");
   private static final Identifier ICON_SCATTER_SHOT = new Identifier("dannys-aot", "textures/gui/ability_titan_scatter_shot.png");
   private static final Identifier ICON_SCATTER_SHOT_FIRE = new Identifier("dannys-aot", "textures/gui/ability_titan_scatter_shot_fire.png");
   private static final Identifier ICON_THROW_BIG = new Identifier("dannys-aot", "textures/gui/ability_titan_throw_big.png");
   private static final Identifier ICON_BEAST_ROAR = new Identifier("dannys-aot", "textures/gui/ability_titan_beast_roar.png");
   private static final Identifier ICON_AWAKEN = new Identifier("dannys-aot", "textures/gui/ability_titan_awaken.png");
   private static final Identifier ICON_STOP = new Identifier("dannys-aot", "textures/gui/ability_titan_stop.png");
   private static final Identifier ICON_CONTINUE = new Identifier("dannys-aot", "textures/gui/ability_titan_continue.png");
   private static final Identifier ICON_REGROUP = new Identifier("dannys-aot", "textures/gui/ability_titan_regroup.png");
   private static final Identifier ICON_TARGET = new Identifier("dannys-aot", "textures/gui/ability_titan_target.png");
   private static final Identifier ICON_FEMALE_GROUND_STOMP = new Identifier("dannys-aot", "textures/gui/female_ability_titan_ground_stomp.png");
   private static final Identifier ICON_FEMALE_HEAD_KICK = new Identifier("dannys-aot", "textures/gui/female_ability_titan_head_kick.png");
   private static final Identifier ICON_FEMALE_SUMMONING_ROAR = new Identifier("dannys-aot", "textures/gui/female_ability_titan_summoning_roar.png");
   private static final Identifier ICON_FEMALE_HARDEN = new Identifier("dannys-aot", "textures/gui/female_ability_titan_harden.png");
   private static final Identifier ICON_FEMALE_WIRE_DETACH = new Identifier("dannys-aot", "textures/gui/female_ability_titan_wire_detach.png");
   private static final Identifier ICON_FEMALE_WIRE_SWING = new Identifier("dannys-aot", "textures/gui/female_ability_titan_wire_swing.png");
   private static final Identifier ICON_FEMALE_NAPE_HARDENING = new Identifier("dannys-aot", "textures/gui/female_abilitiy_titan_nape_hardening.png");
   private static final Identifier ICON_FEMALE_CRYSTAL_COCOON = new Identifier("dannys-aot", "textures/gui/ability_titan_crystal_cocoon.png");
   private static final Identifier ICON_FEMALE_NAPE_COVERING = new Identifier("dannys-aot", "textures/gui/female_abilitiy_titan_nape_covering.png");
   private static final Identifier ICON_FEMALE_GRAB = new Identifier("dannys-aot", "textures/gui/ability_titan_grab_female.png");
   private static final Identifier ICON_ARMORED_HARDENED_STOMP = new Identifier("dannys-aot", "textures/gui/ability_titan_hardened_stomp.png");
   private static final Identifier ICON_ARMORED_BREACH = new Identifier("dannys-aot", "textures/gui/ability_titan_breach.png");
   private static final Identifier ICON_ARMORED_CONSCIOUSNESS_TRANSFER = new Identifier("dannys-aot", "textures/gui/ability_titan_consciousness_transfer.png");
   private static final Identifier ICON_ARMORED_CLIMB = new Identifier("dannys-aot", "textures/gui/ability_titan_hardened_fists_tier_1.png");
   private static final Identifier ICON_ARMORED_TOSS = new Identifier("dannys-aot", "textures/gui/ability_titan_toss.png");
   private static final Identifier ICON_ARMORED_SHATTER = new Identifier("dannys-aot", "textures/gui/ability_titan_armor_shatter.png");
   private static final Identifier ICON_ARMORED_HEAVY = new Identifier("dannys-aot", "textures/gui/ability_titan_armored_heavy.png");
   private static final Identifier ICON_ARMORED_KICK = new Identifier("dannys-aot", "textures/gui/ability_armored_kick.png");
   private static final Identifier ICON_ARMORED_GRAB = new Identifier("dannys-aot", "textures/gui/ability_titan_titan_hardened_grab.png");
   private static final Identifier ICON_COLOSSAL_STEAM = new Identifier("dannys-aot", "textures/gui/ability_titan_colossal_steam.png");
   private static final Identifier ICON_COLOSSAL_KICK = new Identifier("dannys-aot", "textures/gui/ability_titan_colossal_kick.png");
   private static final Identifier ICON_GROUND_TEAR = new Identifier("dannys-aot", "textures/gui/ability_titan_ground_tear.png");
   private static final Identifier ICON_INFERNAL_HEAT = new Identifier("dannys-aot", "textures/gui/ability_titan_infernal_heat.png");
   private static final Identifier ICON_GROUND_SMASH_WARHAMMER = new Identifier("dannys-aot", "textures/gui/ability_titan_ground_smash_warhammer.png");
   private static final Identifier ICON_GROUND_STOMP_WARHAMMER = new Identifier("dannys-aot", "textures/gui/ability_titan_ground_stomp_warhammer.png");
   private static final Identifier ICON_HAMMER_ATTACK = new Identifier("dannys-aot", "textures/gui/ability_titan_hammer_attack.png");
   private static final Identifier ICON_PIERCING_THORNS = new Identifier("dannys-aot", "textures/gui/ability_titan_piercing_thorns.png");
   private static final Identifier ICON_SPIKE_FIELD = new Identifier("dannys-aot", "textures/gui/ability_titan_spike_field.png");
   private static final Identifier ICON_HARDENED_BLOCK = new Identifier("dannys-aot", "textures/gui/ability_titan_hardened_block.png");
   private static final Identifier ICON_CONTROL_CENTER = new Identifier("dannys-aot", "textures/gui/ability_titan_control_center.png");
   private static final Identifier ICON_GIANT_SPIKE = new Identifier("dannys-aot", "textures/gui/abilities_titan_giant_spike.png");
   private static final Identifier ICON_SUMMON_HAMMER = new Identifier("dannys-aot", "textures/gui/abilities_titan_summon_hammer.png");
   private static final int SLOT_TEX_W = 48;
   private static final int SLOT_TEX_H = 46;
   private static final int ICON_TEX = 32;
   private static final int SLOT_SIZE = 22;
   private static final int ICON_SIZE = 16;
   private static final int SLOT_GAP = 2;
   private static final int SLOT_COUNT = 9;
   private static final int BOTTOM_OFFSET = 4;
   private static final int KEYBIND_LABEL_GAP = 2;
   private static final int KEYBIND_LABEL_COLOR = 13421772;
   private static final float KEYBIND_LABEL_SCALE = 0.6666667F;
   private static final KeyBinding[] ABILITY_KEYS = new KeyBinding[]{
      DannysAotClient.SHIFTER_ABILITY_1_KEY,
      DannysAotClient.SHIFTER_ABILITY_2_KEY,
      DannysAotClient.SHIFTER_ABILITY_3_KEY,
      DannysAotClient.SHIFTER_ABILITY_4_KEY,
      DannysAotClient.SHIFTER_ABILITY_5_KEY,
      DannysAotClient.SHIFTER_ABILITY_6_KEY,
      DannysAotClient.SHIFTER_ABILITY_7_KEY,
      DannysAotClient.SHIFTER_ABILITY_8_KEY,
      DannysAotClient.SHIFTER_ABILITY_9_KEY
   };

   public static boolean isAbilityBarVisible() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && !mc.options.hudHidden) {
         Entity v = mc.player.getVehicle();
         return !(v instanceof WarhammerTitanEntity wh && !wh.isDismounting())
               && !(v instanceof CartShifterTitanEntity cart && !cart.isDismounting())
               && !(v instanceof TestShifterTitanEntity jaw && !jaw.isDismounting())
               && !(v instanceof AttackTitanEntity at && !(v instanceof TestShifterTitanEntity) && !at.isDismounting())
               && !(v instanceof ArmoredTitanEntity ar && !ar.isDismounting())
               && !(v instanceof ColossalTitanEntity ct && !ct.isDismounting())
               && !(v instanceof FemaleTitanEntity ft && !ft.isDismounting())
               && !(v instanceof BeastTitanEntity bt && !bt.isDismounting())
            ? FounderAbilityBarState.isActive()
            : true;
      } else {
         return false;
      }
   }

   public static void register() {
      HudRenderCallback.EVENT
         .register(
            (HudRenderCallback)(guiGraphics, tickCounter) -> {
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc.player != null && !mc.options.hudHidden) {
                  Entity vehicle = mc.player.getVehicle();
                  int activeAbilitySlot = -1;
                  if (vehicle instanceof FoundingTitanEntity fts && !fts.isDismounting() && FounderAbilityBarState.isActive()) {
                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.FOUNDING);
                  } else if (vehicle instanceof WarhammerTitanEntity wh && !wh.isDismounting()) {
                     if (wh.isTitanAttacking()) {
                        activeAbilitySlot = getWarhammerActiveSlot(wh.getAttackNumber());
                     }

                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.WARHAMMER);
                  } else if (vehicle instanceof CartShifterTitanEntity cart && !cart.isDismounting()) {
                     if (cart.isChomping()) {
                        activeAbilitySlot = 0;
                     } else if (cart.isCargo()) {
                        activeAbilitySlot = 1;
                     }

                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.CART);
                  } else if (vehicle instanceof TestShifterTitanEntity jaw && !jaw.isDismounting()) {
                     if (jaw.isTitanAttacking()) {
                        if (TestShifterTitanEntity.isPounceAttack(jaw.getAttackNumber())) {
                           activeAbilitySlot = 0;
                        } else if (TestShifterTitanEntity.isKickbackAttack(jaw.getAttackNumber())) {
                           activeAbilitySlot = 1;
                        } else if (TestShifterTitanEntity.isBarrageAttack(jaw.getAttackNumber())) {
                           activeAbilitySlot = 2;
                        }
                     } else if (jaw.isJawChomping() || jaw.isGrabbing()) {
                        activeAbilitySlot = 4;
                     } else if (jaw.isNapeHardened()) {
                        activeAbilitySlot = 3;
                     }

                     int[] jawCdRem = new int[9];
                     int[] jawCdTot = new int[9];
                     jawCdRem[0] = jaw.getPounceCooldownTicks();
                     jawCdTot[0] = TestShifterTitanEntity.pounceCooldownTotal();
                     jawCdRem[1] = jaw.getKickbackCooldownTicks();
                     jawCdTot[1] = TestShifterTitanEntity.kickbackCooldownTotal();
                     jawCdRem[2] = jaw.getBarrageCooldownTicks();
                     jawCdTot[2] = TestShifterTitanEntity.barrageCooldownTotal();
                     jawCdRem[4] = jaw.getChompCooldownTicks();
                     jawCdTot[4] = TestShifterTitanEntity.chompCooldownTotal();
                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.JAW, jawCdRem, jawCdTot);
                  } else if (vehicle instanceof OgreShifterTitanEntity ogre && !ogre.isDismounting()) {
                     if (ogre.isOgreProtecting()) {
                        activeAbilitySlot = 2;
                     } else if (ogre.isOgreWireYanking()) {
                        activeAbilitySlot = 3;
                     } else if (ogre.isTitanAttacking()) {
                        activeAbilitySlot = switch (ogre.getAttackNumber()) {
                           case 3, 7 -> 1;
                           case 4 -> 0;
                           default -> -1;
                        };
                     }

                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.OGRE);
                  } else if (vehicle instanceof AttackTitanEntity at && !(vehicle instanceof TestShifterTitanEntity) && !at.isDismounting()) {
                     if (at.isTitanAttacking()) {
                        activeAbilitySlot = getAttackTitanActiveSlot(at.getAttackNumber());
                     } else if (at.isGrabbing() || at.isThrowing() || at.isEating()) {
                        activeAbilitySlot = 6;
                     }

                     renderAbilityBar(
                        guiGraphics,
                        activeAbilitySlot,
                        vehicle instanceof FoundingTitanEntity ? ShifterAbilityHUD.TitanType.FOUNDING_SHIFTED : ShifterAbilityHUD.TitanType.ATTACK
                     );
                  } else if (vehicle instanceof ArmoredTitanEntity ar && !ar.isDismounting()) {
                     if (ar.isGrabbing() || ar.isThrowing() || ar.isEating()) {
                        activeAbilitySlot = 6;
                     } else if (ar.getTossPhase() != 0) {
                        activeAbilitySlot = 5;
                     } else if (ar.isConsciousnessTransferActive()) {
                        activeAbilitySlot = 8;
                     } else if (ar.isClimbEnabled()) {
                        activeAbilitySlot = 7;
                     } else if (ar.isBreaching()) {
                        activeAbilitySlot = 3;
                     } else if (ar.isTitanAttacking() && ar.getAttackNumber() == 3) {
                        activeAbilitySlot = 0;
                     } else if (ar.isTitanAttacking() && ar.getAttackNumber() == 4) {
                        activeAbilitySlot = 1;
                     } else if (ar.isTitanAttacking() && ar.getAttackNumber() == 5) {
                        activeAbilitySlot = 2;
                     }

                     int[] armoredCdRem = new int[9];
                     int[] armoredCdTot = new int[9];
                     armoredCdRem[0] = ar.getStompCooldownTicks();
                     armoredCdTot[0] = 60;
                     armoredCdRem[1] = ar.getKickCooldownTicks();
                     armoredCdTot[1] = 60;
                     armoredCdRem[2] = ar.getHeavyCooldownTicks();
                     armoredCdTot[2] = 220;
                     int[] redTintSlots = new int[]{
                        ar.isLegsShattered() ? 4 : -1, ar.isConsciousnessTransferUsed() && !ar.isConsciousnessTransferActive() ? 8 : -1
                     };
                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.ARMORED, armoredCdRem, armoredCdTot, redTintSlots);
                  } else if (vehicle instanceof ColossalTitanEntity ct && !ct.isDismounting()) {
                     if (ct.isSteaming()) {
                        activeAbilitySlot = 0;
                     } else if (ct.isKicking()) {
                        activeAbilitySlot = 1;
                     } else if (ct.isInfernalHeating()) {
                        activeAbilitySlot = 3;
                     }

                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.COLOSSAL);
                  } else if (vehicle instanceof FemaleTitanEntity ft && !ft.isDismounting()) {
                     if (ft.isTitanAttacking()) {
                        activeAbilitySlot = getFemaleTitanActiveSlot(ft.getAttackNumber());
                     } else if (ft.isWireYanking()) {
                        activeAbilitySlot = 3;
                     }

                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.FEMALE);
                  } else if (vehicle instanceof BeastTitanEntity bt && !bt.isDismounting()) {
                     if (bt.isBeastRoarAbilityActive()) {
                        activeAbilitySlot = 3;
                     }

                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.BEAST);
                  } else if (FounderAbilityBarState.isActive()) {
                     renderAbilityBar(guiGraphics, activeAbilitySlot, ShifterAbilityHUD.TitanType.FOUNDING);
                  }
               }
            }
         );
   }

   private static int getWarhammerActiveSlot(int attackNumber) {
      return switch (attackNumber) {
         case 3 -> 0;
         case 4 -> 2;
         default -> -1;
         case 8 -> 3;
         case 9 -> 4;
         case 10 -> 7;
      };
   }

   private static int getFemaleTitanActiveSlot(int attackNumber) {
      return switch (attackNumber) {
         case 3 -> 0;
         case 4 -> 1;
         case 5 -> 4;
         default -> -1;
         case 9 -> 8;
      };
   }

   private static int getAttackTitanActiveSlot(int attackNumber) {
      return -1;
   }

   private static void renderAbilityBar(DrawContext guiGraphics, int activeSlot, ShifterAbilityHUD.TitanType titanType) {
      renderAbilityBar(guiGraphics, activeSlot, titanType, null, null);
   }

   private static void renderAbilityBar(DrawContext guiGraphics, int activeSlot, ShifterAbilityHUD.TitanType titanType, int[] cdRemaining, int[] cdTotal) {
      renderAbilityBar(guiGraphics, activeSlot, titanType, cdRemaining, cdTotal, null);
   }

   private static void renderAbilityBar(
      DrawContext guiGraphics, int activeSlot, ShifterAbilityHUD.TitanType titanType, int[] cdRemaining, int[] cdTotal, int[] redTintSlots
   ) {
      MinecraftClient mc = MinecraftClient.getInstance();
      TextRenderer font = mc.textRenderer;
      int totalWidth = 214;
      int startX = (guiGraphics.getScaledWindowWidth() - totalWidth) / 2;
      int y = guiGraphics.getScaledWindowHeight() - 22 - 4;
      boolean hasHardening = HardeningClientState.hasHardening();
      boolean isRoyalLocal = BloodlineClientData.isRoyal();

      for (int i = 0; i < 9; i++) {
         int slotX = startX + i * 24;
         boolean lockedHardening = !hasHardening
            && (titanType == ShifterAbilityHUD.TitanType.FEMALE && (i == 5 || i == 8) || titanType == ShifterAbilityHUD.TitanType.ATTACK && i == 5);
         boolean isBeastRoyalSlot = titanType == ShifterAbilityHUD.TitanType.BEAST && i >= 4 && i <= 8;
         boolean lockedRoyal = isBeastRoyalSlot && !isRoyalLocal;
         boolean locked = lockedHardening || lockedRoyal;
         int cdRem = cdRemaining != null && i < cdRemaining.length ? cdRemaining[i] : 0;
         int cdTot = cdTotal != null && i < cdTotal.length ? cdTotal[i] : 0;
         boolean cooling = cdRem > 0 && cdTot > 0;
         String keyName = ABILITY_KEYS[i].getBoundKeyLocalizedText().getString().toUpperCase();
         int fullLabelWidth = font.getWidth(keyName);
         float scaledLabelWidth = fullLabelWidth * 0.6666667F;
         float scaledLabelHeight = 9.0F * 0.6666667F;
         float labelX = slotX + (22.0F - scaledLabelWidth) / 2.0F;
         float labelY = y - scaledLabelHeight - 2.0F;
         int labelColor = locked ? 6710886 : 13421772;
         guiGraphics.getMatrices().push();
         guiGraphics.getMatrices().translate(labelX, labelY, 0.0F);
         guiGraphics.getMatrices().scale(0.6666667F, 0.6666667F, 1.0F);
         guiGraphics.drawText(font, keyName, 0, 0, labelColor, !locked);
         guiGraphics.getMatrices().pop();
         boolean dim = locked || cooling;
         float a = dim ? 0.4F : 1.0F;
         float c = dim ? 0.5F : 1.0F;
         boolean redTint = false;
         if (redTintSlots != null) {
            for (int rs : redTintSlots) {
               if (rs == i) {
                  redTint = true;
                  break;
               }
            }
         }

         if (redTint) {
            guiGraphics.setShaderColor(1.0F, 0.3F, 0.3F, a);
         } else {
            guiGraphics.setShaderColor(c, c, c, a);
         }

         Identifier slotTex = i == activeSlot ? SLOT_SELECTED : SLOT_UNSELECTED;
         guiGraphics.drawTexture(slotTex, slotX, y, 22, 22, 0.0F, 0.0F, 48, 46, 48, 46);
         Identifier icon = getIconForSlot(titanType, i);
         if (icon != null) {
            int iconOffset = 3;
            guiGraphics.drawTexture(icon, slotX + iconOffset, y + iconOffset, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
         }

         guiGraphics.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         if (cooling) {
            int secs = (cdRem + 19) / 20;
            String secText = secs + "s";
            int tw = font.getWidth(secText);
            guiGraphics.getMatrices().push();
            guiGraphics.getMatrices().translate(slotX + 11.0F, y + 11.0F, 200.0F);
            guiGraphics.getMatrices().scale(1.2F, 1.2F, 1.0F);
            guiGraphics.drawText(font, secText, -tw / 2, -9 / 2, -1, true);
            guiGraphics.getMatrices().pop();
         }

         String slotLabel = getTextLabelForSlot(titanType, i);
         if (slotLabel != null && icon == null) {
            int textColor = locked ? 5592405 : 16777215;
            drawCenteredScaledText(guiGraphics, font, slotLabel, slotX, y, textColor, !locked);
         }
      }
   }

   private static void drawCenteredScaledText(DrawContext guiGraphics, TextRenderer font, String text, int slotX, int slotY, int color, boolean shadow) {
      int rawWidth = font.getWidth(text);
      float maxInner = 18.0F;
      float scale = Math.min(1.0F, maxInner / rawWidth);
      scale = Math.min(scale, 0.65F);
      float scaledW = rawWidth * scale;
      float scaledH = 9.0F * scale;
      float tx = slotX + (22.0F - scaledW) / 2.0F;
      float ty = slotY + (22.0F - scaledH) / 2.0F;
      guiGraphics.getMatrices().push();
      guiGraphics.getMatrices().translate(tx, ty, 0.0F);
      guiGraphics.getMatrices().scale(scale, scale, 1.0F);
      guiGraphics.drawText(font, text, 0, 0, color, shadow);
      guiGraphics.getMatrices().pop();
   }

   private static String getTextLabelForSlot(ShifterAbilityHUD.TitanType titanType, int slotIndex) {
      if (titanType == ShifterAbilityHUD.TitanType.BEAST) {
         return switch (slotIndex) {
            case 4 -> "Awaken";
            case 5 -> "Stop";
            case 6 -> "Continue";
            case 7 -> "Regroup";
            case 8 -> "Target";
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.ATTACK) {
         return switch (slotIndex) {
            case 5 -> "Harden";
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.ARMORED) {
         return switch (slotIndex) {
            case 0 -> "Smash";
            case 1 -> "Kick";
            case 2 -> "Heavy";
            case 3 -> "Breach";
            case 4 -> "Shatter";
            default -> null;
            case 7 -> "Climb";
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.FOUNDING) {
         return switch (slotIndex) {
            case 0 -> "Target";
            case 1 -> "Regroup";
            case 2 -> "Stop";
            case 3 -> FounderAbilityBarState.isLocalMode() ? "Local" : "Global";
            case 4 -> "Summon";
            case 5 -> "Guards";
            case 6 -> "Mount";
            default -> null;
            case 8 -> "Despawn";
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.CART) {
         return switch (slotIndex) {
            case 0 -> "Chomp";
            case 1 -> "Cargo";
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.OGRE) {
         return switch (slotIndex) {
            case 0 -> "Kick";
            case 1 -> "Smash";
            case 2 -> "Protect";
            case 3 -> "Yank";
            case 4 -> "Heal";
            default -> null;
         };
      } else {
         return null;
      }
   }

   private static Identifier getIconForSlot(ShifterAbilityHUD.TitanType titanType, int slotIndex) {
      if (titanType == ShifterAbilityHUD.TitanType.WARHAMMER) {
         return switch (slotIndex) {
            case 0 -> ICON_HAMMER_ATTACK;
            case 1 -> ICON_GROUND_STOMP_WARHAMMER;
            case 2 -> ICON_GROUND_SMASH_WARHAMMER;
            case 3 -> ICON_PIERCING_THORNS;
            case 4 -> ICON_SPIKE_FIELD;
            case 5 -> ICON_HARDENED_BLOCK;
            case 6 -> ICON_CONTROL_CENTER;
            case 7 -> ICON_GIANT_SPIKE;
            case 8 -> ICON_SUMMON_HAMMER;
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.FEMALE) {
         return switch (slotIndex) {
            case 0 -> ICON_FEMALE_GROUND_STOMP;
            case 1 -> ICON_FEMALE_HEAD_KICK;
            case 2 -> ICON_FEMALE_SUMMONING_ROAR;
            case 3 -> ICON_FEMALE_WIRE_SWING;
            case 4 -> ICON_FEMALE_NAPE_COVERING;
            case 5 -> ICON_FEMALE_NAPE_HARDENING;
            case 6 -> ICON_FEMALE_GRAB;
            case 7 -> ICON_FEMALE_WIRE_DETACH;
            case 8 -> ICON_FEMALE_CRYSTAL_COCOON;
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.ATTACK || titanType == ShifterAbilityHUD.TitanType.FOUNDING_SHIFTED) {
         return switch (slotIndex) {
            case 0 -> ICON_GROUND_STOMP;
            default -> null;
            case 3 -> ICON_HEAVY_PUNCH;
            case 6 -> ICON_FEMALE_GRAB;
            case 8 -> ICON_BERSERK;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.BEAST) {
         return switch (slotIndex) {
            case 0 -> ICON_SCATTER_SHOT;
            case 1 -> ICON_SCATTER_SHOT_FIRE;
            case 2 -> ICON_THROW_BIG;
            case 3 -> ICON_BEAST_ROAR;
            case 4 -> ICON_AWAKEN;
            case 5 -> ICON_STOP;
            case 6 -> ICON_CONTINUE;
            case 7 -> ICON_REGROUP;
            case 8 -> ICON_TARGET;
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.ARMORED) {
         return switch (slotIndex) {
            case 0 -> ICON_ARMORED_HARDENED_STOMP;
            case 1 -> ICON_ARMORED_KICK;
            case 2 -> ICON_ARMORED_HEAVY;
            case 3 -> ICON_ARMORED_BREACH;
            case 4 -> ICON_ARMORED_SHATTER;
            case 5 -> ICON_ARMORED_TOSS;
            case 6 -> ICON_ARMORED_GRAB;
            case 7 -> ICON_ARMORED_CLIMB;
            case 8 -> ICON_ARMORED_CONSCIOUSNESS_TRANSFER;
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.JAW) {
         return switch (slotIndex) {
            case 0 -> ICON_POUNCE;
            case 1 -> ICON_KICKBACK;
            case 2 -> ICON_SLASH_BARRAGE;
            case 3 -> ICON_JAW_NAPE_HARDENING;
            case 4 -> ICON_CHOMP;
            default -> null;
         };
      } else if (titanType == ShifterAbilityHUD.TitanType.COLOSSAL) {
         return switch (slotIndex) {
            case 0 -> ICON_COLOSSAL_STEAM;
            case 1 -> ICON_COLOSSAL_KICK;
            case 2 -> ICON_GROUND_TEAR;
            case 3 -> ICON_INFERNAL_HEAT;
            default -> null;
         };
      } else {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   private static enum TitanType {
      WARHAMMER,
      FEMALE,
      ATTACK,
      ARMORED,
      BEAST,
      COLOSSAL,
      FOUNDING,
      FOUNDING_SHIFTED,
      CART,
      JAW,
      OGRE,
      OTHER;
   }
}
