package dev.dreamwalker.bloodbornedw.composite.mixin;
import dev.dreamwalker.bloodbornedw.composite.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockDustParticle.class)
public abstract class NativeVisualParticleMixin extends SpriteBillboardParticle {
    protected NativeVisualParticleMixin(ClientWorld world,double x,double y,double z){super(world,x,y,z);}
    @Inject(method="<init>(Lnet/minecraft/client/world/ClientWorld;DDDDDDLnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)V",at=@At("RETURN"))
    private void dw$visualParticle(ClientWorld world,double x,double y,double z,double vx,double vy,double vz,BlockState state,BlockPos pos,CallbackInfo ci){if(state.getBlock() instanceof NativePhysicsBlock){int visual=NativeVisuals.index(world,pos);if(visual>=0)setSprite(NativeGridClient.particle(visual));}}
}
