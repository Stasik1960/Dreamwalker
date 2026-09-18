package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

@Environment(EnvType.CLIENT)
public class APGSuitRenderer extends GeoArmorRenderer<APGSuitItem> {
   public APGSuitRenderer() {
      super(new APGSuitModel());
   }

   protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
      this.setVisible(true);
   }
}
