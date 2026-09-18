package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanTropicalEyeRenderer extends EntityRenderer<TitanTropicalEyeEntity> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/invisible.png");

   public TitanTropicalEyeRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(TitanTropicalEyeEntity entity) {
      return TEXTURE;
   }
}
