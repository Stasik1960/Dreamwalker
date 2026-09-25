package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.random.Random;
import java.util.Map;
import java.util.function.Supplier;

/** Selects a bounded city page's already-baked block-state mesh for its picked item stack. */
final class CityVariantItemModel extends ForwardingBakedModel {
 private final BloodborneBlocks.Definition definition;
 private final Map<String,BakedModel> variants;
 CityVariantItemModel(BakedModel fallback,BloodborneBlocks.Definition definition,Map<String,BakedModel> variants){wrapped=fallback;this.definition=definition;this.variants=variants;}
 @Override public boolean isVanillaAdapter(){return false;}
 @Override public void emitItemQuads(ItemStack stack,Supplier<Random> random,RenderContext context){
  String variant=null;var tag=stack.getSubNbt("BlockStateTag");
  if(tag!=null&&tag.contains("variant",NbtElement.STRING_TYPE))variant=tag.getString("variant");
  String key=BloodborneBlocks.cityVariantModelKey(definition,variant);
  BakedModel selected=key==null?wrapped:variants.getOrDefault(key,wrapped);
  ((FabricBakedModel)selected).emitItemQuads(stack,random,context);
 }
}
