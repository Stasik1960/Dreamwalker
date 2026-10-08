package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import dev.dreamwalker.bloodbornedw.diagnostics.ArchitectureDiagnostics;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native one-cell architecture has root coordinates rather than fabricated owner UUIDs. */
@Mixin(AbstractBlock.class)
public abstract class ArchitectureBreakDiagnosticsMixin {
    @Inject(method="onStateReplaced",at=@At("HEAD"))
    private void dreamwalker$nativeRemoval(BlockState before,World world,BlockPos root,BlockState after,boolean moved,CallbackInfo ci){
        if(before.getBlock()==after.getBlock()||!ArchitectureDiagnostics.enabled(world))return;
        if(before.getBlock() instanceof PrototypeLadderBlock||before.getBlock() instanceof PrototypeWallBlock)
            ArchitectureDiagnostics.event(world,before,ArchitectureDiagnostics.rootId(root),root,"destroy",ArchitectureDiagnostics.state(before),ArchitectureDiagnostics.state(after),"COMMITTED",moved?"native_move_or_piston":"native_block_replacement; actor_not_measured",null);
    }
}
