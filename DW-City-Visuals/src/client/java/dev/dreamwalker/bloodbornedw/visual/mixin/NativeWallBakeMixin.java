package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.NativeWallVisuals;
import java.util.function.Function;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.NativeGridClient$Session",remap=false)
abstract class NativeWallBakeMixin {
    @Unique private boolean dw$alternate;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void dw$profile(CallbackInfo ci) { dw$alternate=NativeWallVisuals.loadingProfileIsAlt(); }
    @Redirect(method="bake",at=@At(value="NEW",target="net/minecraft/class_777",remap=false),remap=false)
    private BakedQuad dw$wall(int[] data,int tint,Direction face,Sprite sprite,boolean shade,int mesh,Function<SpriteIdentifier,Sprite> textures) {
        return NativeWallVisuals.bake(data,tint,face,sprite,shade,textures,dw$alternate);
    }
}
