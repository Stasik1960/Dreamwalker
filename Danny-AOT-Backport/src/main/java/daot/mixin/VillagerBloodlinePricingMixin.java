package daot.mixin;

import daot.BloodlineData;
import daot.BloodlineType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public class VillagerBloodlinePricingMixin {
   @Unique
   private static final RegistryKey<World> dannysaot$PARADIS_KEY = RegistryKey.of(RegistryKeys.WORLD, new Identifier("dannys-aot", "paradis"));

   @Inject(method = "prepareOffersFor", at = @At("TAIL"))
   private void dannysaot$applyBloodlinePricing(PlayerEntity player, CallbackInfo ci) {
      if (player instanceof ServerPlayerEntity serverPlayer) {
         VillagerEntity self = (VillagerEntity)(Object)this;
         ServerWorld level = (ServerWorld)self.getWorld();
         BloodlineData data = BloodlineData.get(level);
         BloodlineType bloodline = data.getBloodline(serverPlayer.getUuid());
         if (bloodline != null) {
            boolean inParadis = serverPlayer.getWorld().getRegistryKey().equals(dannysaot$PARADIS_KEY);

            int adjustmentType = switch (bloodline) {
               case ACKERMAN -> inParadis ? 1 : 0;
               case ELDIAN -> inParadis ? 0 : 1;
               case ROYAL -> inParadis ? -1 : 1;
               case MARLEYAN -> inParadis ? 1 : 0;
               default -> 0;
            };
            if (adjustmentType != 0) {
               TradeOfferList offers = self.getOffers();

               for (int i = 0; i < offers.size(); i++) {
                  TradeOffer offer = offers.get(i);
                  int baseCost = offer.getOriginalFirstBuyItem().getCount();
                  int adjustment;
                  if (adjustmentType > 0) {
                     adjustment = MathHelper.clamp((int)(baseCost * 0.3F), 3, 20);
                  } else {
                     adjustment = -MathHelper.clamp((int)(baseCost * 0.2F), 2, 12);
                  }

                  offer.increaseSpecialPrice(adjustment);
               }
            }
         }
      }
   }
}

