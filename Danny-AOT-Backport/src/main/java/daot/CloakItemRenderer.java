package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class CloakItemRenderer extends GeoItemRenderer<CloakItem> {
   private final CloakItemModel cloakModel = (CloakItemModel)this.model;

   public CloakItemRenderer() {
      super(new CloakItemModel());
   }

   public Identifier getTextureLocation(CloakItem animatable) {
      ItemStack stack = this.currentItemStack;
      if (stack != null) {
         this.cloakModel.isBlackCloak = stack.getItem() == DannysAot.BLACK_CLOAK;
      }

      return this.cloakModel.isBlackCloak ? CloakItemModel.TEXTURE_BLACK : CloakItemModel.TEXTURE_GREEN;
   }
}
