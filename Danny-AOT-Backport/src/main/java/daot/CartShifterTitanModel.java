package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class CartShifterTitanModel extends GeoModel<CartShifterTitanEntity> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/cart.geo.json");
   private static final Identifier CARGO_MODEL = new Identifier("dannys-aot", "geo/cart_cargo.geo.json");
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/cart.png");
   private static final Identifier CARGO_TEXTURE = new Identifier("dannys-aot", "textures/entity/cart_cargo.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/cart.animation.json");

   public Identifier getModelResource(CartShifterTitanEntity animatable) {
      return animatable.isCargo() ? CARGO_MODEL : MODEL;
   }

   public Identifier getTextureResource(CartShifterTitanEntity animatable) {
      return animatable.isCargo() ? CARGO_TEXTURE : TEXTURE;
   }

   public Identifier getAnimationResource(CartShifterTitanEntity animatable) {
      return ANIMATION;
   }
}
