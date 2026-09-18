package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ArmorPotionBlockModel extends GeoModel<ArmorPotionBlockEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/armor_potion_block.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/armor_potion.png");

   public Identifier getModelResource(ArmorPotionBlockEntity blockEntity) {
      return MODEL;
   }

   public Identifier getTextureResource(ArmorPotionBlockEntity blockEntity) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ArmorPotionBlockEntity blockEntity) {
      return null;
   }
}
