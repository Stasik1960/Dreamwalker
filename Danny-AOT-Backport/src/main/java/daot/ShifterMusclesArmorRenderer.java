package daot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

@Environment(EnvType.CLIENT)
public class ShifterMusclesArmorRenderer extends GeoArmorRenderer<ShifterMusclesArmorItem> {
   public ShifterMusclesArmorRenderer() {
      super(new ShifterMusclesArmorModel());
   }

   protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
      this.setVisible(true);
   }
}
