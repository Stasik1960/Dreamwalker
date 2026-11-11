package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The imported multi-lantern uses its existing open state as a persistent light switch. */
public final class HoldLanternGameplay {
    private static final String ID = "bloodborne_dw:source_hold_lanterns";
    private static final WeakHashMap<ServerPlayerEntity, Long> LAST_USE = new WeakHashMap<>();

    private HoldLanternGameplay() {}

    public static void initialize() {
        UseBlockCallback.EVENT.register(HoldLanternGameplay::use);
    }

    private static ActionResult use(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (!(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity actor)
                || hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
        if (player.shouldCancelInteraction()
                && (!player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty())) return ActionResult.PASS;
        BlockPos clicked = hit.getBlockPos();
        if (!server.isChunkLoaded(clicked) || actor.squaredDistanceTo(clicked.toCenterPos()) > 36) return ActionResult.PASS;
        Owner owner = CompositeRuntime.target(server, clicked, actor);
        if (owner == null || !ID.equals(owner.registryId())) return ActionResult.PASS;
        BlockPos root = CompositeData.pos(owner.root());
        if (!server.isChunkLoaded(root) || !server.getWorldBorder().contains(root)
                || !server.canPlayerModifyAt(actor, root)
                || !(server.getBlockEntity(root) instanceof CompositeBlockEntity entity)
                || !owner.equals(entity.resident())) return ActionResult.FAIL;
        var state = server.getBlockState(root);
        if (!(state.getBlock() instanceof CompositeRootBlock) || !state.contains(CompositeRootBlock.OPEN)) return ActionResult.FAIL;
        long now = server.getServer().getTicks();
        Long last = LAST_USE.get(actor);
        if (last != null && now - last < 2 && now >= last) return ActionResult.SUCCESS;
        LAST_USE.put(actor, now);
        var next = state.with(CompositeRootBlock.OPEN, !state.get(CompositeRootBlock.OPEN));
        var result = CompositeRuntime.transition(server, owner, next, actor);
        return result.outcome() == TransactionCore.Outcome.COMMITTED ? ActionResult.SUCCESS : ActionResult.FAIL;
    }
}
