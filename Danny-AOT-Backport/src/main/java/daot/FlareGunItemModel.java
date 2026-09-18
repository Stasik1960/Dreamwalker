package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class FlareGunItemModel extends GeoModel<FlareGunItem> {
   private static final Identifier MODEL = new Identifier("dannys-aot", "geo/flare_gun.geo.json");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/flare_gun.animation.json");
   private static final Identifier TEX_EMPTY = new Identifier("dannys-aot", "textures/item/flare_gun_empty.png");
   private static final Identifier[] TEX_COLORS = new Identifier[]{
      new Identifier("dannys-aot", "textures/item/flare_gun_red.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_black.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_purple.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_blue.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_green.png"),
      new Identifier("dannys-aot", "textures/item/flare_gun_yellow.png")
   };
   public static int currentColorOrdinal = -1;
   private static int overrideColorOrdinal = -2;
   private static long overrideExpireTick = 0L;

   public static void setTextureOverride(int colorOrdinal, int durationTicks) {
      overrideColorOrdinal = colorOrdinal;
      overrideExpireTick = System.currentTimeMillis() + durationTicks * 50L;
   }

   public static void clearOverride() {
      overrideColorOrdinal = -2;
   }

   public Identifier getModelResource(FlareGunItem animatable) {
      return MODEL;
   }

   public Identifier getTextureResource(FlareGunItem animatable) {
      int ord;
      if (overrideColorOrdinal != -2 && System.currentTimeMillis() < overrideExpireTick) {
         ord = overrideColorOrdinal;
      } else {
         if (overrideColorOrdinal != -2) {
            overrideColorOrdinal = -2;
         }

         ord = currentColorOrdinal;
      }

      return ord >= 0 && ord < TEX_COLORS.length ? TEX_COLORS[ord] : TEX_EMPTY;
   }

   public Identifier getAnimationResource(FlareGunItem animatable) {
      return ANIMATION;
   }
}
