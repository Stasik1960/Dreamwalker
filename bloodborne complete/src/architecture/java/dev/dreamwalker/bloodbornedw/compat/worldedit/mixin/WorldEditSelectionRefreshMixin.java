package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import com.sk89q.worldedit.fabric.FabricWorldEdit;
import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import dev.dreamwalker.bloodbornedw.composite.ForcedBlockRemoval;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WorldEdit consumes the left click for wand selection. Minecraft can still
 * predict a creative break on the client, so restore the authoritative server
 * state to that player after WorldEdit reports a consumed click.
 */
@Mixin(value = FabricWorldEdit.class, remap = false)
public abstract class WorldEditSelectionRefreshMixin {
    @Unique private static final java.lang.reflect.Method dreamwalker$publishNative= dreamwalker$nativePublisher();

    @Unique private static java.lang.reflect.Method dreamwalker$nativePublisher(){
        try{return Class.forName("dev.dreamwalker.bloodbornedw.composite.NativeVisuals",false,WorldEditSelectionRefreshMixin.class.getClassLoader())
                .getMethod("publish",World.class,BlockPos.class);}
        catch(ReflectiveOperationException unavailable){return null;}
    }
    @Inject(method = "onLeftClickBlock", at = @At("RETURN"), remap = false)
    private void dreamwalker$refreshSelectedBlock(PlayerEntity player, World world, Hand hand,
                                                   BlockPos pos, Direction side,
                                                   CallbackInfoReturnable<ActionResult> callback) {
        if (callback.getReturnValue() != ActionResult.SUCCESS || !(player instanceof ServerPlayerEntity serverPlayer)
                || !ForcedBlockRemoval.isModBlock(world.getBlockState(pos))) return;
        serverPlayer.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, pos));
        var entity = world.getBlockEntity(pos);
        var update = entity == null ? null : entity.toUpdatePacket();
        if (update != null) serverPlayer.networkHandler.sendPacket(update);
        // Creative prediction can also discard the chunk's native visual index.
        if(dreamwalker$publishNative!=null)try{dreamwalker$publishNative.invoke(null,world,pos);}
        catch(ReflectiveOperationException failure){DreamwalkerBb.LOG.debug("Could not refresh selected native visual at {}",pos,failure);}
    }
}
