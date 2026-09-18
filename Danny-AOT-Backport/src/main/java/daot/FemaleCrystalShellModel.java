package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class FemaleCrystalShellModel extends GeoModel<FemaleCrystalShellEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/female_crystal.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/female_crystal.png");

   public Identifier getModelResource(FemaleCrystalShellEntity entity) {
      return MODEL;
   }

   public Identifier getTextureResource(FemaleCrystalShellEntity entity) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(FemaleCrystalShellEntity entity) {
      return null;
   }
}
