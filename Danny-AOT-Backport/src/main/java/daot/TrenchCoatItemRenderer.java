package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public class TrenchCoatItemRenderer extends GeoItemRenderer<TrenchCoatItem> {
   public TrenchCoatItemRenderer() {
      super(new TrenchCoatItemModel());
   }

   public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
      if (stack.getItem() instanceof TrenchCoatItem item) {
         TrenchCoatItemModel model = (TrenchCoatItemModel)this.getGeoModel();
         model.currentGeo = new Identifier("dannys-aot", item.getItemGeoPath());
         model.currentTexture = new Identifier("dannys-aot", item.getTexturePath());
      }

      super.render(stack, mode, matrices, vertexConsumers, light, overlay);
   }
}
