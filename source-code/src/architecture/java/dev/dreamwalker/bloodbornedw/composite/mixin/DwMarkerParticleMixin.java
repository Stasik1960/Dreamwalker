package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses Minecraft's marker particle, range and random block tick cadence. */
@Mixin(ClientWorld.class)
public abstract class DwMarkerParticleMixin {
    @Inject(method="randomBlockDisplayTick",at=@At("RETURN"))
    private void dreamwalker$heldMarker(int centerX,int centerY,int centerZ,int radius,Random random,net.minecraft.block.Block block,BlockPos.Mutable pos,CallbackInfo ci){
        var world=(ClientWorld)(Object)this;var player=MinecraftClient.getInstance().player;if(player==null||!player.isCreative())return;
        var state=world.getBlockState(pos);if(!state.isOf(DwUtilityBlocks.BARRIER)&&!state.isOf(DwUtilityBlocks.LIGHT))return;
        var item=state.getBlock().asItem();
        if(player.getMainHandStack().isOf(item)||player.getOffHandStack().isOf(item))
            world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK_MARKER,state),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,0,0,0);
    }
}
