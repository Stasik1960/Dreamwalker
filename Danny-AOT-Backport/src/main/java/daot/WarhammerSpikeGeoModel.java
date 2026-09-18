package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class WarhammerSpikeGeoModel extends GeoModel<WarhammerSpikeEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/spike.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/spike_model.png");
   private static final Identifier BIG_MODEL = new Identifier("dannys-aot", "geo/bigspike.geo.json");
   private static final Identifier BIG_TEXTURE = new Identifier("dannys-aot", "textures/entity/bigspike.png");
   private static final Identifier SMALL_MODEL = new Identifier("dannys-aot", "geo/smallspike.geo.json");
   private static final Identifier SMALL_TEXTURE = new Identifier("dannys-aot", "textures/entity/smallspike.png");

   public Identifier getModelResource(WarhammerSpikeEntity entity) {
      if (entity != null && entity.isImpaleSpike()) {
         return BIG_MODEL;
      } else {
         return entity != null && entity.isSpikeField() ? SMALL_MODEL : MODEL;
      }
   }

   public Identifier getTextureResource(WarhammerSpikeEntity entity) {
      if (entity != null && entity.isImpaleSpike()) {
         return BIG_TEXTURE;
      } else {
         return entity != null && entity.isSpikeField() ? SMALL_TEXTURE : TEXTURE;
      }
   }

   public Identifier getAnimationResource(WarhammerSpikeEntity entity) {
      return null;
   }
}
