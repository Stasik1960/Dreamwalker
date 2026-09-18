package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class CloakModel extends GeoModel<CloakItem> {
   private static final Identifier MODEL_HOOD_UP = new Identifier("dannys-aot", "geo/cloak_hood.geo.json");
   private static final Identifier MODEL_HOOD_DOWN = new Identifier("dannys-aot", "geo/cloak_hood_down.geo.json");
   private static final Identifier MODEL_TDXM_UP = new Identifier("dannys-aot", "geo/tdxm_cloak_hood.geo.json");
   private static final Identifier MODEL_TDXM_DOWN = new Identifier("dannys-aot", "geo/tdxm_cloak_hood_down.geo.json");
   private static final Identifier MODEL_BERSERK_UP = new Identifier("dannys-aot", "geo/berserk_cloak_hood.geo.json");
   private static final Identifier MODEL_BERSERK_DOWN = new Identifier("dannys-aot", "geo/berserk_cloak_hood_down.geo.json");
   private static final Identifier MODEL_ROYALTY_UP = new Identifier("dannys-aot", "geo/royal_cloak_hood.geo.json");
   private static final Identifier MODEL_ROYALTY_DOWN = new Identifier("dannys-aot", "geo/royal_cloak_hood_down.geo.json");
   private static final Identifier MODEL_TITAN_UP = new Identifier("dannys-aot", "geo/aot_hood_titan.geo.json");
   private static final Identifier MODEL_TITAN_DOWN = new Identifier("dannys-aot", "geo/aot_hood_titan_down.geo.json");
   private static final Identifier MODEL_HYPER_UP = new Identifier("dannys-aot", "geo/hyper_cloak_hood.geo.json");
   private static final Identifier MODEL_HYPER_DOWN = new Identifier("dannys-aot", "geo/hyper_cloak_hood_down.geo.json");
   private static final Identifier ANIMATION_TDXM = new Identifier("dannys-aot", "animations/tdxm_cloak.animation.json");
   private static final Identifier ANIMATION_BERSERK = new Identifier("dannys-aot", "animations/berserk_hood.animation.json");
   static final Identifier TEXTURE_BLACK = new Identifier("dannys-aot", "textures/armor/aot_hood_black.png");
   static final Identifier TEXTURE_BLACK_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_black_down.png");
   static final Identifier TEXTURE_GREEN = new Identifier("dannys-aot", "textures/armor/aot_hood_green.png");
   static final Identifier TEXTURE_GREEN_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_green_down.png");
   static final Identifier TEXTURE_GREEN_SCOUT = new Identifier("dannys-aot", "textures/armor/aot_hood_green_scout.png");
   static final Identifier TEXTURE_GREEN_SCOUT_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_green_down_scout.png");
   static final Identifier TEXTURE_GREEN_GARRISON = new Identifier("dannys-aot", "textures/armor/aot_hood_green_garrison.png");
   static final Identifier TEXTURE_GREEN_GARRISON_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_green_down_garrison.png");
   static final Identifier TEXTURE_GREEN_MILITARY_POLICE = new Identifier("dannys-aot", "textures/armor/aot_hood_green_military_police.png");
   static final Identifier TEXTURE_GREEN_MILITARY_POLICE_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_green_down_military_police.png");
   static final Identifier TEXTURE_GOLD = new Identifier("dannys-aot", "textures/armor/gold_aot_hood_green.png");
   static final Identifier TEXTURE_GOLD_DOWN = new Identifier("dannys-aot", "textures/armor/gold_aot_hood_green_down.png");
   static final Identifier TEXTURE_ROYAL = new Identifier("dannys-aot", "textures/armor/aot_hood_royal.png");
   static final Identifier TEXTURE_ROYAL_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_royal_down.png");
   static final Identifier TEXTURE_TDXM = new Identifier("dannys-aot", "textures/armor/aot_hood_tdxm.png");
   static final Identifier TEXTURE_TDXM_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_tdxm_down.png");
   static final Identifier TEXTURE_BERSERK = new Identifier("dannys-aot", "textures/armor/aot_hood_berserk.png");
   static final Identifier TEXTURE_BERSERK_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_berserk_down.png");
   static final Identifier TEXTURE_AARON = new Identifier("dannys-aot", "textures/armor/aaroncloak.png");
   static final Identifier TEXTURE_AARON_DOWN = new Identifier("dannys-aot", "textures/armor/aaroncloak_down.png");
   static final Identifier TEXTURE_ROYALTY = new Identifier("dannys-aot", "textures/armor/royalhood.png");
   static final Identifier TEXTURE_ROYALTY_DOWN = new Identifier("dannys-aot", "textures/armor/royalhooddown.png");
   static final Identifier TEXTURE_TITAN = new Identifier("dannys-aot", "textures/armor/aot_hood_titan.png");
   static final Identifier TEXTURE_TITAN_DOWN = new Identifier("dannys-aot", "textures/armor/aot_hood_titan_down.png");
   static final Identifier TEXTURE_HYPER = new Identifier("dannys-aot", "textures/armor/hyper_cloak_hood.png");
   static final Identifier TEXTURE_HYPER_DOWN = new Identifier("dannys-aot", "textures/armor/hyper_cloak_hood_down.png");
   boolean currentHoodUp = false;
   boolean currentUseTdxm = false;
   boolean currentUseBerserk = false;
   boolean currentUseRoyalty = false;
   boolean currentUseTitan = false;
   boolean currentUseHyper = false;
   Identifier currentTexture = TEXTURE_BLACK;

   public Identifier getModelResource(CloakItem animatable) {
      if (this.currentUseTdxm) {
         return this.currentHoodUp ? MODEL_TDXM_UP : MODEL_TDXM_DOWN;
      } else if (this.currentUseBerserk) {
         return this.currentHoodUp ? MODEL_BERSERK_UP : MODEL_BERSERK_DOWN;
      } else if (this.currentUseRoyalty) {
         return this.currentHoodUp ? MODEL_ROYALTY_UP : MODEL_ROYALTY_DOWN;
      } else if (this.currentUseTitan) {
         return this.currentHoodUp ? MODEL_TITAN_UP : MODEL_TITAN_DOWN;
      } else if (this.currentUseHyper) {
         return this.currentHoodUp ? MODEL_HYPER_UP : MODEL_HYPER_DOWN;
      } else {
         return this.currentHoodUp ? MODEL_HOOD_UP : MODEL_HOOD_DOWN;
      }
   }

   public Identifier getTextureResource(CloakItem animatable) {
      return this.currentTexture;
   }

   public Identifier getAnimationResource(CloakItem animatable) {
      if (this.currentUseTdxm) {
         return ANIMATION_TDXM;
      } else if (this.currentUseBerserk) {
         return ANIMATION_BERSERK;
      } else {
         return this.currentUseTitan ? ANIMATION_BERSERK : null;
      }
   }
}
