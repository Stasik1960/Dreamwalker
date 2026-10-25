package dev.dreamwalker.bloodbornedw.compat.customnpc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.dreamwalker.bloodbornerp.client.NpcMobVisualBridge;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use Custom NPC's existing arbitrary-renderer path for our GeckoLib proxies. */
@Pseudo
@Mixin(targets="noppes.npcs.client.renderer.RenderCustomNpc",remap=false)
public abstract class CustomNpcMobRendererMixin {
    @Shadow(remap=false) private LivingEntity entity;
    @Unique private boolean dreamwalker$scaled;

    @Inject(method="render",at=@At(value="FIELD",target="Lnoppes/npcs/ModelData;simpleRender:Z",opcode=180),remap=false,require=1)
    private void dreamwalker$bind(@Coerce Object npc,float yaw,float tick,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        if(entity instanceof RpMobEntity mob&&npc instanceof LivingEntity source){float scale=NpcMobVisualBridge.bind(mob,source);matrices.push();matrices.scale(scale,scale,scale);dreamwalker$scaled=true;}
    }
    @ModifyExpressionValue(method="render",at=@At(value="FIELD",target="Lnoppes/npcs/ModelData;simpleRender:Z",opcode=180),remap=false,require=1)
    private boolean dreamwalker$fullGeckoRenderer(boolean original){return original||entity instanceof RpMobEntity;}

    @Inject(method="render",at=@At("RETURN"),remap=false,require=1)
    private void dreamwalker$restore(@Coerce Object npc,float yaw,float tick,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        if(dreamwalker$scaled){matrices.pop();dreamwalker$scaled=false;}
    }
}
