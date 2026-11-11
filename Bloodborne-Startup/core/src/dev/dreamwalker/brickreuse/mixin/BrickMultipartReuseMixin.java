package dev.dreamwalker.brickreuse.mixin;
import dev.dreamwalker.brickreuse.BrickReuseSnapshot;
import dev.dreamwalker.brickreuse.BrickReuseAccess;
import dev.dreamwalker.brickreuse.BrickReuseMetrics;
import dev.dreamwalker.brickreuse.CompactParentAccess;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.class_2960;
import net.minecraft.class_3665;
import net.minecraft.class_4730;
import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_7775;
import net.minecraft.class_816;
import net.minecraft.class_819;
import net.minecraft.class_813;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = class_816.class, remap = false)
public abstract class BrickMultipartReuseMixin {
    @Shadow @Final private net.minecraft.class_2689<net.minecraft.class_2248,net.minecraft.class_2680> field_4329;
    @Unique private volatile BrickReuseSnapshot dreamwalker$brickSnapshot;
    @Unique private boolean dreamwalker$brickAttempted;
    @Unique private java.lang.ref.WeakReference<Object> dreamwalker$parentLoader;
    @Unique private Class<?> dreamwalker$parentResolverClass;
    @Unique private static final BrickReuseAccess dreamwalker$access = BrickReuseAccess.create();

    @Inject(method = "method_4753", at = @At("HEAD"), cancellable = true, remap = false)
    private void dreamwalker$reuse(class_7775 baker, Function<class_4730,class_1058> sprites,
                                  class_3665 settings, class_2960 id, CallbackInfoReturnable<class_1087> callback) {
        BrickReuseSnapshot saved = dreamwalker$brickSnapshot;
        if (saved == null || !dreamwalker$compact(id)) return;
        try {
            Map<?,?> cache = dreamwalker$access.cache(baker);
            if (cache != saved.cache.get() || field_4329 != saved.stateFactory || settings.method_3509() != saved.rotation || settings.method_3512() != saved.uvLock) return;
            List<class_819> parts = ((class_816)(Object)this).method_3519();
            if (parts != saved.parts || parts.size() != saved.partCount) { dreamwalker$brickSnapshot = null; return; }
            for (int i = 0; i < saved.keys.length; i++) {
                if (cache.get(saved.keys[i]) != saved.children[i]) return;
            }
            BrickReuseMetrics.hit();
            callback.setReturnValue(saved.model);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            dreamwalker$brickSnapshot = null;
        }
    }

    @Inject(method = "method_4753", at = @At("RETURN"), remap = false)
    private void dreamwalker$capture(class_7775 baker, Function<class_4730,class_1058> sprites,
                                     class_3665 settings, class_2960 id, CallbackInfoReturnable<class_1087> callback) {
        if (dreamwalker$brickAttempted || !dreamwalker$compact(id)) return;
        dreamwalker$brickAttempted = true;
        if (callback.getReturnValue() == null) return;
        try {
            class_816 self = (class_816)(Object)this;
            List<class_819> parts = self.method_3519();
            Object model = callback.getReturnValue();
            var componentsMethod = model.getClass().getMethod("dreamwalker$components");
            Object graphObject = componentsMethod.invoke(model);
            if (!(graphObject instanceof List<?> graph) || graph.size() != parts.size()) return;
            Map<?,?> cache = dreamwalker$access.cache(baker);
            Map<Object,Object> childReferences = new LinkedHashMap<>();
            for (int i = 0; i < parts.size(); i++) {
                List<class_813> variants = parts.get(i).method_3529().method_3497();
                if (variants.size() != 1 || !(graph.get(i) instanceof Pair<?,?> pair)) return;
                class_813 variant = variants.get(0);
                Object key = dreamwalker$access.key.newInstance(variant.method_3510(), variant.method_3509(), variant.method_3512());
                Object child = cache.get(key);
                if (child == null || child != pair.getRight()) return;
                Object prior = childReferences.putIfAbsent(key, child);
                if (prior != null && prior != child) return;
                if (childReferences.size() > 128) return;
            }
            if (childReferences.isEmpty()) return;
            BrickReuseMetrics.proven(childReferences.size());
            dreamwalker$brickSnapshot = new BrickReuseSnapshot(cache, field_4329, parts, childReferences.keySet().toArray(), childReferences.values().toArray(),
                    settings.method_3509(), settings.method_3512(), callback.getReturnValue());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            dreamwalker$brickSnapshot = null;
        }
    }

    @Inject(method = "method_45785", at = @At("HEAD"), cancellable = true, remap = false)
    private void dreamwalker$skipRepeatedParents(Function<class_2960,net.minecraft.class_1100> resolver, CallbackInfo callback) {
        if (!dreamwalker$ownedCompact()) return;
        Object loader = CompactParentAccess.owner(resolver);
        if (loader != null && dreamwalker$parentLoader != null && dreamwalker$parentLoader.get() == loader && resolver.getClass() == dreamwalker$parentResolverClass) {
            BrickReuseMetrics.parentHit();
            callback.cancel();
        }
    }

    @Inject(method = "method_45785", at = @At("RETURN"), remap = false)
    private void dreamwalker$recordParents(Function<class_2960,net.minecraft.class_1100> resolver, CallbackInfo callback) {
        if (!dreamwalker$ownedCompact()) return;
        Object loader = CompactParentAccess.owner(resolver);
        if (loader != null && (dreamwalker$parentLoader == null || dreamwalker$parentLoader.get() != loader || dreamwalker$parentResolverClass != resolver.getClass()) && CompactParentAccess.calledFromModelLoader()) {
            dreamwalker$parentLoader = new java.lang.ref.WeakReference<>(loader);
            dreamwalker$parentResolverClass = resolver.getClass();
            BrickReuseMetrics.parentFirst();
        }
    }

    @Unique private boolean dreamwalker$ownedCompact() {
        return field_4329 != null && field_4329.method_11660() instanceof dev.dreamwalker.bloodbornedw.composite.ImportedCompactBlock;
    }

    @Unique private boolean dreamwalker$compact(class_2960 id) {
        return id instanceof net.minecraft.class_1091 && !((net.minecraft.class_1091)id).method_4740().equals("inventory")
                && id.method_12836().equals("bloodborne_dw") && id.method_12832().startsWith("source_") && dreamwalker$ownedCompact();
    }
}

