package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory.Context;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

@Environment(EnvType.CLIENT)
public class ArmorPotionBlockRenderer extends GeoBlockRenderer<ArmorPotionBlockEntity> {
   public ArmorPotionBlockRenderer(Context context) {
      super(new ArmorPotionBlockModel());
   }
}
