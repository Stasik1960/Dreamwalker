package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanDummyEyeRenderer extends EntityRenderer<TitanDummyEyeEntity> {
   public TitanDummyEyeRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(TitanDummyEyeEntity entity) {
      return new Identifier("textures/misc/white.png");
   }
}
