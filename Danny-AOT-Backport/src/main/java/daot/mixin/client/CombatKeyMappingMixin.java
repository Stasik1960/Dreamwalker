package daot.mixin.client;

import daot.DannysAotClient;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyBinding.class)
public class CombatKeyMappingMixin {
   @Shadow
   private static Map<Key, KeyBinding> KEY_TO_BINDINGS;
   @Shadow
   private static Map<String, KeyBinding> KEYS_BY_ID;

   @Inject(method = "updateKeysByCode", at = @At("TAIL"))
   private static void dannysaot$restoreVanillaKeysFromCombatShadow(CallbackInfo ci) {
      KeyBinding[] combatKeys = getCombatKeys();
      if (combatKeys != null) {
         try {
            Set<KeyBinding> combatKeySet = new HashSet<>();
            Set<Key> combatPhysicalKeys = new HashSet<>();

            for (KeyBinding ck : combatKeys) {
               if (ck != null) {
                  combatKeySet.add(ck);
                  Key k = ((KeyMappingKeyAccessor)ck).dannysaot$getBoundKey();
                  if (k != null && k.getCode() != InputUtil.UNKNOWN_KEY.getCode()) {
                     combatPhysicalKeys.add(k);
                  }
               }
            }

            if (combatPhysicalKeys.isEmpty()) {
               return;
            }

            for (Key physKey : new HashSet<>(combatPhysicalKeys)) {
               KeyBinding current = KEY_TO_BINDINGS.get(physKey);
               if (current != null && combatKeySet.contains(current)) {
                  KeyBinding replacement = null;

                  for (KeyBinding km : KEYS_BY_ID.values()) {
                     if (!combatKeySet.contains(km)) {
                        Key kmKey = ((KeyMappingKeyAccessor)km).dannysaot$getBoundKey();
                        if (physKey.equals(kmKey)) {
                           replacement = km;
                           break;
                        }
                     }
                  }

                  if (replacement != null) {
                     KEY_TO_BINDINGS.put(physKey, replacement);
                  } else {
                     KEY_TO_BINDINGS.remove(physKey);
                  }
               }
            }
         } catch (Throwable var11) {
         }
      }
   }

   private static KeyBinding[] getCombatKeys() {
      return DannysAotClient.COMBAT_MODE_TOGGLE_KEY == null
         ? null
         : new KeyBinding[]{
            DannysAotClient.COMBAT_MODE_TOGGLE_KEY,
            DannysAotClient.COMBAT_LEFT_HOOK_KEY,
            DannysAotClient.COMBAT_RIGHT_HOOK_KEY,
            DannysAotClient.COMBAT_BLOCK_KEY,
            DannysAotClient.COMBAT_SHIFT_LOCK_KEY,
            DannysAotClient.SHIFTER_DODGE_KEY,
            DannysAotClient.TITAN_SHIFT_KEY,
            DannysAotClient.TEASE_SHIFT_KEY,
            DannysAotClient.RELOAD_BLADE_KEY,
            DannysAotClient.APG_RELOAD_KEY,
            DannysAotClient.TITAN_ROAR_KEY,
            DannysAotClient.STEALTH_MODE_KEY,
            DannysAotClient.AWAKENED_POWER_KEY,
            DannysAotClient.HOOD_TOGGLE_KEY,
            DannysAotClient.HIDE_SHIFTER_UI_KEY,
            DannysAotClient.THUNDER_SPEAR_LOAD_KEY,
            DannysAotClient.FLARE_LOAD_KEY,
            DannysAotClient.FLARE_CYCLE_KEY,
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
   }
}
