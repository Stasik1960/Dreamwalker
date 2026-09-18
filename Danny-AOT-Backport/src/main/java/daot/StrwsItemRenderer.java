package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class StrwsItemRenderer extends GeoItemRenderer<StrwsItem> {
   public StrwsItemRenderer() {
      super(new StrwsItemModel());
   }
}
