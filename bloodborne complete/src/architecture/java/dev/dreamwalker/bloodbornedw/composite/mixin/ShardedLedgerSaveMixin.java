package dev.dreamwalker.bloodbornedw.composite.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeLedger;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Normal save-all/autosave/stop flushes dirty opt-in owner shards before the
 * world writes its compact shard-key PersistentState index. Legacy no-op. */
@Mixin(ServerWorld.class)
public abstract class ShardedLedgerSaveMixin {
    @Inject(method="save",at=@At("HEAD"))
    private void dreamwalker$flushOwnerShards(ProgressListener listener,boolean flush,boolean savingDisabled,CallbackInfo ci) {
        if(!savingDisabled)CompositeLedger.get((ServerWorld)(Object)this).flushShards();
    }
}
