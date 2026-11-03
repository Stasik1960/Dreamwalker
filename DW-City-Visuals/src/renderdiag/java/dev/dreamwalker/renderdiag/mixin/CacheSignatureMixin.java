package dev.dreamwalker.renderdiag.mixin;
import dev.dreamwalker.bloodbornedw.composite.RenderDiagBridge;
import dev.dreamwalker.renderdiag.RenderDiag;
import dev.dreamwalker.renderdiag.RootSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh$CacheSignature",remap=false)
abstract class CacheSignatureMixin implements RootSignature {
    @Shadow @Final private long[] sections;
    @Unique private long dwdiag$root;
    @Unique private boolean dwdiag$hasRoot;
    public void dwdiag$setRoot(long section) { dwdiag$root=section; dwdiag$hasRoot=true; }
    @Inject(method="current",at=@At("RETURN"),remap=false)
    private void current(CallbackInfoReturnable<Boolean> ci) {
        if(!RenderDiag.ENABLED) return;
        RenderDiag.signatureTotal.increment();
        if(dwdiag$hasRoot) RenderDiag.rootKnown.increment();
        if(ci.getReturnValueZ()) return;
        RenderDiag.signatureInvalid.increment(); boolean root=false, other=false, dep=false;
        for(int i=0;i+1<sections.length;i+=2) {
            boolean isRoot=dwdiag$hasRoot&&sections[i]==dwdiag$root;
            if(isRoot) dep=true;
            if(RenderDiagBridge.stamp(sections[i])!=sections[i+1]) { if(isRoot) root=true; else if(dwdiag$hasRoot) other=true; }
        }
        if(dep) RenderDiag.rootDependency.increment();
        if(root) RenderDiag.invalidRoot.increment(); if(other) RenderDiag.invalidOther.increment(); if(!root&&!other) RenderDiag.invalidUnknown.increment();
    }
}
