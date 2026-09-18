package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class TitanTropicalNapeRenderer extends EntityRenderer<TitanTropicalNapeEntity> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/invisible.png");

   public TitanTropicalNapeRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(TitanTropicalNapeEntity entity) {
      return TEXTURE;
   }
}
