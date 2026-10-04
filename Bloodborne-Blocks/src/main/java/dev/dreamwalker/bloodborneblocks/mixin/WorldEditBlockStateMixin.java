package dev.dreamwalker.bloodborneblocks.mixin;

import dev.dreamwalker.bloodborneblocks.compat.WorldEditStateCompatibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.Map;

/** Optional WorldEdit 7.2.x compatibility for wide Bloodborne block states. */
@Pseudo
@Mixin(targets="com.sk89q.worldedit.world.block.BlockState",remap=false)
abstract class WorldEditBlockStateMixin {
 @Shadow @Final private Map<Object,Object> values;

 @Unique private static final ThreadLocal<WorldEditStateCompatibility.PaletteDecision> bloodborneBlocks$paletteDecision=
  ThreadLocal.withInitial(WorldEditStateCompatibility.PaletteDecision::new);
 @Unique private static final ClassValue<Method> bloodborneBlocks$getBlockTypeMethod=new ClassValue<>() {
  @Override protected Method computeValue(Class<?> type) {
   try { return type.getMethod("getBlockType"); }
   catch(NoSuchMethodException exception) { throw new IllegalStateException("Unsupported WorldEdit BlockState API",exception); }
  }
 };
 @Unique private static final ClassValue<Method> bloodborneBlocks$getIdMethod=new ClassValue<>() {
  @Override protected Method computeValue(Class<?> type) {
   try { return type.getMethod("getId"); }
   catch(NoSuchMethodException exception) { throw new IllegalStateException("Unsupported WorldEdit BlockType API",exception); }
  }
 };
 @Unique private Map<? extends Map<?,?>,?> bloodborneBlocks$stateMap;

 @Inject(method="populate",at=@At("HEAD"),cancellable=true)
 private void bloodborneBlocks$keepWideStatesLazy(Map<? extends Map<?,?>,?> stateMap,CallbackInfo callback) {
  if(!bloodborneBlocks$isBloodborneBlock())return;
  boolean wide=bloodborneBlocks$paletteDecision.get().isWide(stateMap);
  if(wide) {
   bloodborneBlocks$stateMap=stateMap;
   callback.cancel();
  }
 }

 @Inject(method="with",at=@At("HEAD"),cancellable=true)
 private void bloodborneBlocks$lazyWith(@Coerce Object property,@Coerce Object value,CallbackInfoReturnable<Object> callback) {
  if(bloodborneBlocks$stateMap!=null)callback.setReturnValue(
   WorldEditStateCompatibility.with(values,bloodborneBlocks$stateMap,property,value,this));
 }

 @Unique private boolean bloodborneBlocks$isBloodborneBlock() {
  try {
   Object blockType=bloodborneBlocks$getBlockTypeMethod.get(getClass()).invoke(this);
   Object id=bloodborneBlocks$getIdMethod.get(blockType.getClass()).invoke(blockType);
   return id instanceof String identifier&&identifier.startsWith("bloodborne_blocks:");
  } catch(ReflectiveOperationException exception) {
   throw new IllegalStateException("Unable to inspect WorldEdit BlockState ID",exception);
  }
 }
}
