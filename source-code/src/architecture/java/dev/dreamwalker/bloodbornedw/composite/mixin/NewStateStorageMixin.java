package dev.dreamwalker.bloodbornedw.composite.mixin;

import com.google.common.collect.Table;
import dev.dreamwalker.bloodbornedw.composite.CompactStateTransitions;
import java.util.Map;
import net.minecraft.state.State;
import net.minecraft.state.property.Property;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(State.class)
public abstract class NewStateStorageMixin<O,S> {
    @Shadow @Final protected O owner;
    @Shadow private Table<Property<?>,Comparable<?>,S> withTable;
    @Inject(method="createWithTable",at=@At("HEAD"),cancellable=true)
    private void bloodborne_dw$createOwnedTransitions(Map<Map<Property<?>,Comparable<?>>,S> states,CallbackInfo ci){
        if(!CompactStateTransitions.ownedClass(owner))return;
        if(withTable!=null)throw new IllegalStateException();
        var state=(State<?,?>)(Object)this;
        var direct=CompactStateTransitions.createOwned(owner,state.getEntries(),states,this);
        if(direct==null)return;
        withTable=direct;
        CompactStateTransitions.verifyOwnedMethods(owner,state,states);
        ci.cancel();
    }
    @Inject(method="createWithTable",at=@At("RETURN"))
    private void bloodborne_dw$compactNewTransitions(Map<Map<Property<?>,Comparable<?>>,S> states,CallbackInfo ci){
        if(CompactStateTransitions.ownedClass(owner))withTable=CompactStateTransitions.compact(withTable);
        else CompactStateTransitions.ineligible();
    }
}
