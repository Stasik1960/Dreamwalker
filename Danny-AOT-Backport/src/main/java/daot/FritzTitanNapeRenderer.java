package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class FritzTitanNapeRenderer extends EntityRenderer<FritzTitanNapeEntity> {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/entity/invisible.png");

   public FritzTitanNapeRenderer(Context context) {
      super(context);
   }

   public Identifier getTexture(FritzTitanNapeEntity entity) {
      return TEXTURE;
   }
}
