package dev.dreamwalker.editorqa.mixin;
import dev.dreamwalker.editorqa.EditorModelProbe;
import net.minecraft.client.render.model.BakedQuad;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="dev.dreamwalker.bloodbornedw.visual.FacadeMaterials",remap=false)
public abstract class EditorMaterialsProbeMixin {
 @Inject(method="quad(Lnet/minecraft/class_777;IZ)Lnet/minecraft/class_777;",at=@At("HEAD"),remap=false)
 private static void qHead(BakedQuad quad,int role,boolean manual,CallbackInfoReturnable<BakedQuad> ci){EditorModelProbe.materialBefore(quad,role,manual);}
 @Inject(method="quad(Lnet/minecraft/class_777;IZ)Lnet/minecraft/class_777;",at=@At("RETURN"),remap=false)
 private static void qTail(BakedQuad quad,int role,boolean manual,CallbackInfoReturnable<BakedQuad> ci){EditorModelProbe.materialAfter("material_quad",ci.getReturnValue());}
 @Inject(method="transform(Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;IZ)Z",at=@At("HEAD"),remap=false)
 private static void tHead(MutableQuadView quad,int role,boolean manual,CallbackInfoReturnable<Boolean> ci){EditorModelProbe.materialBefore(quad,role,manual);}
 @Inject(method="transform(Lnet/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView;IZ)Z",at=@At("RETURN"),remap=false)
 private static void tTail(MutableQuadView quad,int role,boolean manual,CallbackInfoReturnable<Boolean> ci){EditorModelProbe.materialAfter("material_transform",quad);}
}
