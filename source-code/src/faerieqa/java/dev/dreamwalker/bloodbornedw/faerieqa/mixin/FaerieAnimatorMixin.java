package dev.dreamwalker.bloodbornedw.faerieqa.mixin;
import dev.dreamwalker.bloodbornedw.faerieqa.FaerieQaClient;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** QA control only, excluded from the production JAR. Same scene/JVM, freeze
 * only the approved material's texture updates for alternating comparisons. */
@Mixin(targets="net.minecraft.client.texture.SpriteContents$AnimatorImpl")
public abstract class FaerieAnimatorMixin {
    @Shadow @Final private SpriteContents field_40543;
    @Shadow int frame;
    @Shadow int currentTime;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void dw$freeze(int x,int y,CallbackInfo ci){if(field_40543.getId().getPath().startsWith("block/faerie_veil/")&&FaerieQaClient.freeze)ci.cancel();}
    @Inject(method="tick",at=@At("RETURN"))
    private void dw$phase(int x,int y,CallbackInfo ci){if(field_40543.getId().getPath().startsWith("block/faerie_veil/"))FaerieQaClient.spriteTick(field_40543.getId().toString(),frame,currentTime);}
}
