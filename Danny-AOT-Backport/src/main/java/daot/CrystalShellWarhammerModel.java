package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class CrystalShellWarhammerModel extends GeoModel<CrystalShellWarhammerEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/crystal_shell_warhammer.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/crystal_shell_warhammer.png");

   public Identifier getModelResource(CrystalShellWarhammerEntity entity) {
      return MODEL;
   }

   public Identifier getTextureResource(CrystalShellWarhammerEntity entity) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(CrystalShellWarhammerEntity entity) {
      return null;
   }
}
