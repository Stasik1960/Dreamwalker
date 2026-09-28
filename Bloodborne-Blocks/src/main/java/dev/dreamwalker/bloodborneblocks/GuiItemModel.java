package dev.dreamwalker.bloodborneblocks;

import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.ModelTransformationMode;

/** Replaces only GUI transformation; all item-hand and world modes delegate unchanged. */
final class GuiItemModel extends ForwardingBakedModel {
 private final ModelTransformation transformations;
 GuiItemModel(BakedModel model,GuiItemBounds bounds){this(model,bounds,model.getTransformation());}
 GuiItemModel(BakedModel model,GuiItemBounds bounds,ModelTransformation fallback){
  wrapped=model;ModelTransformation original=model.getTransformation();transformations=new ModelTransformation(
   fallback.getTransformation(ModelTransformationMode.THIRD_PERSON_LEFT_HAND),fallback.getTransformation(ModelTransformationMode.THIRD_PERSON_RIGHT_HAND),
   fallback.getTransformation(ModelTransformationMode.FIRST_PERSON_LEFT_HAND),fallback.getTransformation(ModelTransformationMode.FIRST_PERSON_RIGHT_HAND),
   fallback.getTransformation(ModelTransformationMode.HEAD),bounds.gui(original.getTransformation(ModelTransformationMode.GUI)),
   fallback.getTransformation(ModelTransformationMode.GROUND),fallback.getTransformation(ModelTransformationMode.FIXED));
 }
 @Override public ModelTransformation getTransformation(){return transformations;}
}
