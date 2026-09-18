package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class BladeItemModel extends GeoModel<BladeItem> {
   private static final Identifier MODEL_LOADED = new Identifier("dannys-aot", "geo/blade.geo.json");
   private static final Identifier MODEL_EMPTY = new Identifier("dannys-aot", "geo/blade.geo.json");
   private static final Identifier MODEL_KIRITO = new Identifier("dannys-aot", "geo/kirito_sword.geo.json");
   private static final Identifier MODEL_NEEDLE = new Identifier("dannys-aot", "geo/needle_sword.geo.json");
   private static final Identifier MODEL_HYPER = new Identifier("dannys-aot", "geo/hyper_blade.geo.json");
   public static final Identifier TEXTURE_EMPTY = new Identifier("dannys-aot", "textures/item/emptyblade.png");
   public static final Identifier TEXTURE_FRESH = new Identifier("dannys-aot", "textures/item/blade.png");
   public static final Identifier TEXTURE_CHIPPED_1 = new Identifier("dannys-aot", "textures/item/bladechipped1.png");
   public static final Identifier TEXTURE_CHIPPED_2 = new Identifier("dannys-aot", "textures/item/bladechipped2.png");
   public static final Identifier TEXTURE_CHIPPED_3 = new Identifier("dannys-aot", "textures/item/bladechipped3.png");
   public static final Identifier TEXTURE_KIRITO_FRESH = new Identifier("dannys-aot", "textures/item/kirito_sword.png");
   public static final Identifier TEXTURE_KIRITO_CHIPPED_1 = new Identifier("dannys-aot", "textures/item/kiritochipped1.png");
   public static final Identifier TEXTURE_KIRITO_CHIPPED_2 = new Identifier("dannys-aot", "textures/item/kiritochipped2.png");
   public static final Identifier TEXTURE_KIRITO_CHIPPED_3 = new Identifier("dannys-aot", "textures/item/kiritochipped3.png");
   public static final Identifier TEXTURE_NEEDLE_FRESH = new Identifier("dannys-aot", "textures/item/needle_sword.png");
   public static final Identifier TEXTURE_NEEDLE_CHIPPED_1 = new Identifier("dannys-aot", "textures/item/needlechipped1.png");
   public static final Identifier TEXTURE_NEEDLE_CHIPPED_2 = new Identifier("dannys-aot", "textures/item/needlechipped2.png");
   public static final Identifier TEXTURE_NEEDLE_CHIPPED_3 = new Identifier("dannys-aot", "textures/item/needlechipped3.png");
   public static final Identifier TEXTURE_HYPER_EMPTY = new Identifier("dannys-aot", "textures/item/hyperblade_empty.png");
   public static final Identifier TEXTURE_HYPER_FRESH = new Identifier("dannys-aot", "textures/item/hyperblade.png");
   public static final Identifier TEXTURE_HYPER_CHIPPED_1 = new Identifier("dannys-aot", "textures/item/hyperbladedamaged1.png");
   public static final Identifier TEXTURE_HYPER_CHIPPED_2 = new Identifier("dannys-aot", "textures/item/hyperbladedamaged2.png");
   public static final Identifier TEXTURE_HYPER_CHIPPED_3 = new Identifier("dannys-aot", "textures/item/hyperbladedamaged3.png");
   private static final Identifier ANIMATION = new Identifier("dannys-aot", "animations/blade.animation.json");
   private BladeItem.BladeState currentState = BladeItem.BladeState.FRESH;
   boolean currentUseKirito = false;
   boolean currentUseNeedle = false;
   boolean currentUseHyper = false;

   public Identifier getModelResource(BladeItem animatable) {
      if (this.currentUseKirito) {
         return MODEL_KIRITO;
      } else if (this.currentUseNeedle) {
         return MODEL_NEEDLE;
      } else if (this.currentUseHyper) {
         return MODEL_HYPER;
      } else {
         return this.currentState == BladeItem.BladeState.EMPTY ? MODEL_EMPTY : MODEL_LOADED;
      }
   }

   public Identifier getTextureResource(BladeItem animatable) {
      return TEXTURE_FRESH;
   }

   public Identifier getAnimationResource(BladeItem animatable) {
      return ANIMATION;
   }

   public void setCurrentState(BladeItem.BladeState state) {
      this.currentState = state;
   }

   public static Identifier getTextureForState(BladeItem.BladeState state) {
      return switch (state) {
         case EMPTY -> TEXTURE_EMPTY;
         case FRESH -> TEXTURE_FRESH;
         case CHIPPED_1 -> TEXTURE_CHIPPED_1;
         case CHIPPED_2 -> TEXTURE_CHIPPED_2;
         case CHIPPED_3 -> TEXTURE_CHIPPED_3;
      };
   }

   public static Identifier getKiritoTextureForState(BladeItem.BladeState state) {
      return switch (state) {
         case EMPTY, CHIPPED_3 -> TEXTURE_KIRITO_CHIPPED_3;
         case FRESH -> TEXTURE_KIRITO_FRESH;
         case CHIPPED_1 -> TEXTURE_KIRITO_CHIPPED_1;
         case CHIPPED_2 -> TEXTURE_KIRITO_CHIPPED_2;
      };
   }

   public static Identifier getNeedleTextureForState(BladeItem.BladeState state) {
      return switch (state) {
         case EMPTY, CHIPPED_3 -> TEXTURE_NEEDLE_CHIPPED_3;
         case FRESH -> TEXTURE_NEEDLE_FRESH;
         case CHIPPED_1 -> TEXTURE_NEEDLE_CHIPPED_1;
         case CHIPPED_2 -> TEXTURE_NEEDLE_CHIPPED_2;
      };
   }

   public static Identifier getHyperTextureForState(BladeItem.BladeState state) {
      return switch (state) {
         case EMPTY -> TEXTURE_HYPER_EMPTY;
         case FRESH -> TEXTURE_HYPER_FRESH;
         case CHIPPED_1 -> TEXTURE_HYPER_CHIPPED_1;
         case CHIPPED_2 -> TEXTURE_HYPER_CHIPPED_2;
         case CHIPPED_3 -> TEXTURE_HYPER_CHIPPED_3;
      };
   }
}
