package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import java.util.Map;
import java.util.function.Supplier;

/** Selects an already-baked artistic state, retaining the inventory display transform. */
final class CityVariantItemModel extends ForwardingBakedModel {
 private final ArchitectureBlock block;
 private final Map<String,BakedModel> variants;
 CityVariantItemModel(BakedModel fallback,ArchitectureBlock block,Map<String,BakedModel> variants){wrapped=fallback;this.block=block;this.variants=variants;}
 @Override public boolean isVanillaAdapter(){return false;}
 @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){
  String key=ArchitectureCreativeCatalog.itemModelKey(block,stack);
  BakedModel selected=key==null?wrapped:variants.getOrDefault(key,wrapped);
  ((FabricBakedModel)selected).emitItemQuads(stack,random,context);
 }
}
