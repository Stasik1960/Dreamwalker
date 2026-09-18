package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class AbnormalTitanNapeRenderer extends EntityRenderer<AbnormalTitanNapeEntity> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/invisible.png");

   public AbnormalTitanNapeRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(AbnormalTitanNapeEntity entity) {
      return TEXTURE;
   }
}
