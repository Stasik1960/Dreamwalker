package dev.dreamwalker.bloodbornedw.compat.worldedit.mixin;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import dev.dreamwalker.bloodbornedw.compat.worldedit.WorldEditBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional pinned WorldEdit 7.2 reads call World directly, outside its extent
 * event chain. Preserve complete logical objects for real clipboard copies. */
@Mixin(value=EditSession.class,remap=false)
public abstract class WorldEditEditSessionMixin {
    @Shadow @Final protected World world;
    @Inject(method="getFullBlock",at=@At("RETURN"),cancellable=true)
    private void dw$logicalFullRead(BlockVector3 position,CallbackInfoReturnable<BaseBlock> cir){cir.setReturnValue(WorldEditBridge.logicalRead(world,position,cir.getReturnValue()));}
    @Inject(method="getBlock",at=@At("RETURN"),cancellable=true)
    private void dw$logicalStateRead(BlockVector3 position,CallbackInfoReturnable<BlockState> cir){cir.setReturnValue(WorldEditBridge.logicalState(world,position,cir.getReturnValue()));}
}
