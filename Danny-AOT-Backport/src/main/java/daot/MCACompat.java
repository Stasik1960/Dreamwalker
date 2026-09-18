package daot;

import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public class MCACompat {
   private static Boolean mcaLoaded = null;
   private static EntityType<?> mcaMaleVillager = null;
   private static EntityType<?> mcaFemaleVillager = null;
   private static boolean registryLookedUp = false;

   public static boolean isMCALoaded() {
      if (mcaLoaded == null) {
         mcaLoaded = FabricLoader.getInstance().isModLoaded("mca");
         if (mcaLoaded) {
            DannysAot.LOGGER.info("MCA detected - will spawn MCA villagers for compatibility");
         }
      }

      return mcaLoaded;
   }

   private static void lookupMCAEntityTypes() {
      if (!registryLookedUp) {
         registryLookedUp = true;

         try {
            Identifier maleId = new Identifier("mca:male_villager");
            Identifier femaleId = new Identifier("mca:female_villager");
            if (Registries.ENTITY_TYPE.containsId(maleId)) {
               mcaMaleVillager = Registries.ENTITY_TYPE.get(maleId);
            }

            if (Registries.ENTITY_TYPE.containsId(femaleId)) {
               mcaFemaleVillager = Registries.ENTITY_TYPE.get(femaleId);
            }

            if (mcaMaleVillager != null && mcaFemaleVillager != null) {
               DannysAot.LOGGER.info("MCA entity types found: male_villager and female_villager");
            } else {
               DannysAot.LOGGER.warn("MCA is loaded but entity types not found in registry - falling back to vanilla villagers");
            }
         } catch (Exception var2) {
            DannysAot.LOGGER.warn("Failed to look up MCA entity types: {}", var2.getMessage());
         }
      }
   }

   public static VillagerEntity createVillager(ServerWorld level) {
      if (isMCALoaded()) {
         lookupMCAEntityTypes();
         EntityType<?> chosenType = null;
         if (mcaMaleVillager != null && mcaFemaleVillager != null) {
            chosenType = ThreadLocalRandom.current().nextBoolean() ? mcaMaleVillager : mcaFemaleVillager;
         } else if (mcaMaleVillager != null) {
            chosenType = mcaMaleVillager;
         } else if (mcaFemaleVillager != null) {
            chosenType = mcaFemaleVillager;
         }

         if (chosenType != null) {
            Entity entity = chosenType.create(level);
            if (entity instanceof VillagerEntity villager) {
               return villager;
            }

            if (entity != null) {
               entity.discard();
               DannysAot.LOGGER.warn("MCA villager entity does not extend Villager class - falling back to vanilla");
            }
         }
      }

      return EntityType.VILLAGER.create(level);
   }
}
