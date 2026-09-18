package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanBeardEyeRenderer extends EntityRenderer<TitanBeardEyeEntity> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/invisible.png");

   public TitanBeardEyeRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(TitanBeardEyeEntity entity) {
      return TEXTURE;
   }
}
