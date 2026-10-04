package dev.dreamwalker.bloodbornerp.client.weapon;

import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import dev.dreamwalker.bloodbornerp.weapon.TrickWeaponItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** Resolves the catalog entry for the stack currently being rendered, with public fallbacks. */
@Environment(EnvType.CLIENT)
public final class TrickWeaponModel extends GeoModel<TrickWeaponItem> {
  private static final Identifier FALLBACK_MODEL=new Identifier("bloodborne_rp","geo/fallback_weapon.geo.json");
  private static final Identifier FALLBACK_ANIMATION=new Identifier("bloodborne_rp","animations/fallback.animation.json");
  private static final Identifier FALLBACK_TEXTURE=new Identifier("minecraft","textures/block/iron_block.png");
  private ItemStack currentStack=ItemStack.EMPTY;

  void setCurrentStack(ItemStack stack) { currentStack=stack; }

  @Override public Identifier getModelResource(TrickWeaponItem weapon) { return resource(weapon,AssetPart.MODEL); }
  @Override public Identifier getTextureResource(TrickWeaponItem weapon) { return resource(weapon,AssetPart.TEXTURE); }
  @Override public Identifier getAnimationResource(TrickWeaponItem weapon) { return resource(weapon,AssetPart.ANIMATION); }

  private Identifier resource(TrickWeaponItem weapon, AssetPart part) {
    ItemStack stack=currentStack;
    String assetId=stack.getItem()==weapon ? weapon.formId(stack) : weapon.assetId();
    AssetSpec spec=AssetCatalog.get(assetId);
    String path=part.value(spec);
    Identifier requested=path.indexOf(':')>=0 ? new Identifier(path) : new Identifier("bloodborne_rp",path);
    return MinecraftClient.getInstance().getResourceManager().getResource(requested).isPresent() ? requested : part.fallback;
  }

  private enum AssetPart {
    MODEL(FALLBACK_MODEL) { @Override String value(AssetSpec spec) { return spec.model(); } },
    TEXTURE(FALLBACK_TEXTURE) { @Override String value(AssetSpec spec) { return spec.texture(); } },
    ANIMATION(FALLBACK_ANIMATION) { @Override String value(AssetSpec spec) { return spec.animation(); } };
    final Identifier fallback;
    AssetPart(Identifier fallback) { this.fallback=fallback; }
    abstract String value(AssetSpec spec);
  }
}
