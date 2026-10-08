package dev.dreamwalker.bloodbornerp.client.weapon;

import dev.dreamwalker.bloodbornerp.weapon.TrickWeaponItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import software.bernie.geckolib.renderer.GeoItemRenderer;

@Environment(EnvType.CLIENT)
public final class TrickWeaponRenderer extends GeoItemRenderer<TrickWeaponItem> {
  private final TrickWeaponModel weaponModel;
  public TrickWeaponRenderer() { this(new TrickWeaponModel()); }
  private TrickWeaponRenderer(TrickWeaponModel weaponModel) { super(weaponModel); this.weaponModel=weaponModel; }
  @Override public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
    weaponModel.setCurrentStack(stack);
    super.render(stack,mode,matrices,vertices,light,overlay);
  }
}
