package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.composite.overlaymixin.StaticLightingViewAccess;
import net.minecraft.client.render.model.BakedQuad;

/** Entry point for recorded composite meshes; also permits a QA-only reference bypass. */
public final class CompositeCornerLighting {
 private CompositeCornerLighting(){}
 public static boolean sample(CornerSurfaceLight sampler,BakedQuad quad,StaticLightingViewAccess view,int[] nativeLight){
  return sampler.sample(quad,view,nativeLight);
 }
}
