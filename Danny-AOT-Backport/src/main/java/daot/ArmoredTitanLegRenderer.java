package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ArmoredTitanLegRenderer extends EntityRenderer<ArmoredTitanLegEntity> {
   public ArmoredTitanLegRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(ArmoredTitanLegEntity entity) {
      return new Identifier("textures/misc/white.png");
   }
}
