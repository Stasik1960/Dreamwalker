package dev.dreamwalker.bloodborneblocks;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;

/** Selects an already-baked artistic state for item quads and GUI display. */
final class CityVariantItemModel extends ForwardingBakedModel {
 private final BakedModel fallback;
 private final ArchitectureBlock block;
 private final Map<String,BakedModel> variants;
 private final GuiItemBounds.Cache bounds;
 private final Map<BakedModel,BakedModel> guiVariants=new IdentityHashMap<>();
 private final ModelOverrideList overrides=new SelectingOverrides();
 CityVariantItemModel(BakedModel fallback,ArchitectureBlock block,Map<String,BakedModel> variants,GuiItemBounds.Cache bounds){
  this.fallback=fallback;this.block=block;this.variants=variants;this.bounds=bounds;wrapped=gui(fallback);
 }
 @Override public boolean isVanillaAdapter(){return false;}
 @Override public ModelOverrideList getOverrides(){return overrides;}
 @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){((FabricBakedModel)selectedRaw(stack)).emitItemQuads(stack,random,context);}
 private BakedModel selectedRaw(ItemStack stack){String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);return key==null?fallback:variants.getOrDefault(key,fallback);}
 private BakedModel gui(BakedModel selected){return guiVariants.computeIfAbsent(selected,key->new GuiItemModel(key,bounds.get(key),fallback.getTransformation()));}
 private final class SelectingOverrides extends ModelOverrideList {
  SelectingOverrides(){super((Baker)null,(JsonUnbakedModel)null,List.of());}
  @Override public BakedModel apply(BakedModel model,ItemStack stack,ClientWorld world,LivingEntity entity,int seed){return gui(selectedRaw(stack));}
 }
}
