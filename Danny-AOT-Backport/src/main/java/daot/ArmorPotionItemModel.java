package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class ArmorPotionItemModel extends GeoModel<ArmorPotionItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/armor_potion.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/armor_potion.png");

   public Identifier getModelResource(ArmorPotionItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(ArmorPotionItem animatable) {
      return TEXTURE;
   }

   public Identifier getAnimationResource(ArmorPotionItem animatable) {
      return null;
   }
}
