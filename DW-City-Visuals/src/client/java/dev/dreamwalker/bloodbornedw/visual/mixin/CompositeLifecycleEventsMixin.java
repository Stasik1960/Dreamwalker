package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.CompositeLifecycleDispatch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets="dev.dreamwalker.bloodbornedw.composite.CompositeClient",remap=false)
abstract class CompositeLifecycleEventsMixin {
    @Redirect(method="lambda$initialize$0",at=@At(value="INVOKE",target="Ldev/dreamwalker/bloodbornedw/composite/CompositeWorldMesh;disconnect()V"),remap=false)
    private static void dw$disconnect(ClientPlayNetworkHandler handler,MinecraftClient client) { CompositeLifecycleDispatch.disconnect(handler,client); }
    @Redirect(method="lambda$initialize$2",at=@At(value="INVOKE",target="Ldev/dreamwalker/bloodbornedw/composite/CompositeWorldMesh;forgetChunk(Lnet/minecraft/class_1923;)V"),remap=false)
    private static void dw$forget(ChunkPos pos,ClientWorld world,WorldChunk chunk) { CompositeLifecycleDispatch.forget(world,chunk,pos); }
}
