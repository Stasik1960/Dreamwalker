package dev.dreamwalker.bloodborneblocks.mixin;

import com.google.common.collect.ImmutableMap;
import dev.dreamwalker.bloodborneblocks.ArchitectureBlock;
import dev.dreamwalker.bloodborneblocks.compat.ArchitectureStateTransitions;
import net.minecraft.state.State;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// Applied after Lithium's priority-1000 RETURN injection. The early return is
// added afterward, so Lithium never expands a compact architecture state table.
@Mixin(value=State.class,priority=900)
abstract class CompactArchitectureStateMixin<O,S> {
 @Shadow @Final protected O owner;
 @Shadow @Final private ImmutableMap<Property<?>,Comparable<?>> entries;
 @Unique private Map<Map<Property<?>,Comparable<?>>,S> bloodborneBlocks$canonicalStates;

 @Inject(method="createWithTable",at=@At("HEAD"),cancellable=true)
 private void bloodborneBlocks$compactTable(Map<Map<Property<?>,Comparable<?>>,S> states,CallbackInfo callback) {
  if(!(owner instanceof ArchitectureBlock))return;
  boolean wide=false;
  for(Property<?> property:entries.keySet())if(property.getValues().size()>=64){wide=true;break;}
  if(!wide)return;
  if(bloodborneBlocks$canonicalStates!=null)throw new IllegalStateException();
  bloodborneBlocks$canonicalStates=states;
  callback.cancel();
 }

 @Inject(method="with",at=@At("HEAD"),cancellable=true)
 private <T extends Comparable<T>,V extends T> void bloodborneBlocks$with(Property<T> property,V value,CallbackInfoReturnable<S> callback) {
  bloodborneBlocks$lookup(property,value,false,callback);
 }

 @Inject(method="withIfExists",at=@At("HEAD"),cancellable=true)
 private <T extends Comparable<T>,V extends T> void bloodborneBlocks$withIfExists(Property<T> property,V value,CallbackInfoReturnable<S> callback) {
  bloodborneBlocks$lookup(property,value,true,callback);
 }

 @Unique @SuppressWarnings("unchecked") private void bloodborneBlocks$lookup(Property<?> property,Comparable<?> value,
   boolean allowMissing,CallbackInfoReturnable<S> callback) {
  if(bloodborneBlocks$canonicalStates!=null)callback.setReturnValue((S)ArchitectureStateTransitions.with(
   entries,bloodborneBlocks$canonicalStates,property,value,owner,this,allowMissing));
 }
}
