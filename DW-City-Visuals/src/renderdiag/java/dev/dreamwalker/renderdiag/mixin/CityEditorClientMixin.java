package dev.dreamwalker.renderdiag.mixin;
import dev.dreamwalker.renderdiag.RenderDiag;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="dev.dreamwalker.bloodbornedw.editor.CityEditorClient",remap=false)
abstract class CityEditorClientMixin {
    @Inject(method="lambda$onInitializeClient$2",at=@At("HEAD"),remap=false) private static void update(MinecraftClient client,ClientPlayNetworkHandler handler,Identifier dimension,int x,int z,NbtCompound packet,CallbackInfo ci) { if(RenderDiag.ENABLED) (packet.getCompound("Editor").getLongArray("Positions").length==0?RenderDiag.updateEmpty:RenderDiag.updateFull).increment(); }
}
