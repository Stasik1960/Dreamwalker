package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class APGCartridgeItemRenderer extends GeoItemRenderer<APGCartridgeItem> {
   public APGCartridgeItemRenderer() {
      super(new APGCartridgeItemModel());
   }
}
