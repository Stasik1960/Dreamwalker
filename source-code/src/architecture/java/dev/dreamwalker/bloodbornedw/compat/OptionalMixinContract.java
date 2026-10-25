package dev.dreamwalker.bloodbornedw.compat;

import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Fail before optional internal mixins apply to an untested binary API.
 * An absent optional backend is skipped. No Sodium-without-Indium policy. */
public final class OptionalMixinContract implements IMixinConfigPlugin {
    public static boolean checked(String id,String supported){
        var mod=FabricLoader.getInstance().getModContainer(id);if(mod.isEmpty())return false;
        String actual=mod.get().getMetadata().getVersion().getFriendlyString();
        if(!actual.split("\\+",2)[0].equals(supported))throw new IllegalStateException("Dreamwalker visual-physics.2: unsupported internal API for "+id+" "+actual+"; tested adapter requires "+supported+". Keep the existing modpack versions or update the Dreamwalker adapter before opening a world.");
        return true;
    }
    @Override public void onLoad(String mixinPackage){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        if(target.startsWith("me.jellysquid.mods.sodium."))return checked("sodium","0.5.13");
        if(target.startsWith("link.infra.indium."))return checked("indium","1.0.36");
        if(mixin.endsWith("LithiumPaletteAllocationMixin"))return checked("lithium","0.11.4");
        if(target.startsWith("com.jamieswhiteshirt.reachentityattributes."))return checked("reach-entity-attributes","2.4.0");
        return true;
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
