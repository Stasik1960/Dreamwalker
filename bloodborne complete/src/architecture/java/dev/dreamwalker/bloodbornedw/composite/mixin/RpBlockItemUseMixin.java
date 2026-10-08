package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.BuildingTool;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Construction item-use temporarily targets the native block behind RP. Ordinary attack still selects RP. */
@Mixin(MinecraftClient.class)
public abstract class RpBlockItemUseMixin {
    @Unique private HitResult dreamwalker$rpUseTarget;
    @Inject(method="doItemUse",at=@At("HEAD"))
    private void dreamwalker$blockBehindRp(CallbackInfo ci){
        MinecraftClient client=(MinecraftClient)(Object)this;
        if(client.player==null||client.interactionManager==null||BuildingTool.isHeld(client.player)||!(client.crosshairTarget instanceof EntityHitResult entityHit)||!(entityHit.getEntity() instanceof RpObjectEntity))return;
        if(!(client.player.getMainHandStack().getItem() instanceof BlockItem)&&!(client.player.getOffHandStack().getItem() instanceof BlockItem)
                &&!ObjectRegistry.isPlacementItem(client.player.getMainHandStack())&&!ObjectRegistry.isPlacementItem(client.player.getOffHandStack()))return;
        HitResult nativeHit=client.player.raycast(client.interactionManager.getReachDistance(),1,false);
        if(nativeHit instanceof BlockHitResult&&nativeHit.getType()==HitResult.Type.BLOCK){dreamwalker$rpUseTarget=client.crosshairTarget;client.crosshairTarget=nativeHit;}
    }
    @Inject(method="doItemUse",at=@At("RETURN"))
    private void dreamwalker$restoreRpAttackTarget(CallbackInfo ci){
        if(dreamwalker$rpUseTarget!=null){((MinecraftClient)(Object)this).crosshairTarget=dreamwalker$rpUseTarget;dreamwalker$rpUseTarget=null;}
    }
}
