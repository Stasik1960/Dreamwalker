package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class StandardLanternGameplay implements ModInitializer {
    private static final String ID = "bloodborne_dw:source_standard_lantern";
    private static volatile java.util.function.BooleanSupplier clientAttackGate = () -> true;
    public static void setClientAttackGate(java.util.function.BooleanSupplier gate) { clientAttackGate = java.util.Objects.requireNonNull(gate); }
    private record Hit(long first, long last, int count) {}
    private static final Map<ServerWorld, LinkedHashMap<UUID, Hit>> HITS = new WeakHashMap<>();
    public static boolean isLantern(Owner owner) { return owner != null && ID.equals(owner.registryId()); }
    public static boolean standing(ServerWorld world, Owner owner) {
        var pos = CompositeData.pos(owner.root()); var state = world.getBlockState(pos);
        return state.contains(CompositeRootBlock.VARIANT) && state.get(CompositeRootBlock.VARIANT) == 1;
    }
    @Override public void onInitialize() {
        HoldLanternGameplay.initialize();
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient || player.isSpectator() || player.isCreative()) return ActionResult.PASS;
            var owner = CompositeRuntime.target(world, pos, player);
            if (!isLantern(owner)) return ActionResult.PASS;
            return clientAttackGate.getAsBoolean() ? ActionResult.SUCCESS : ActionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!ID.equals(Registries.ITEM.getId(stack.getItem()).toString()) || player.raycast(5.0, 1.0f, false).getType() != HitResult.Type.MISS) return TypedActionResult.pass(stack);
            if (!world.isClient && !player.isSpectator()) {
                var tag = stack.getOrCreateSubNbt("BlockStateTag");
                tag.putString("open", Boolean.toString(!"true".equals(tag.getString("open"))));
            }
            return TypedActionResult.success(stack, world.isClient);
        });
    }
    /** Called only on the server, with the target recomputed from the player's sight line. */
    public static boolean attack(ServerPlayerEntity player, BlockPos clicked, boolean start) {
        ServerWorld world = player.getServerWorld();
        if (player.isSpectator() || player.isCreative() || !world.isChunkLoaded(clicked) || !world.getWorldBorder().contains(clicked) || player.squaredDistanceTo(clicked.toCenterPos()) > 36) return false;
        Owner owner = CompositeRuntime.target(world, clicked, player);
        if (!isLantern(owner)) return false;
        BlockPos root = CompositeData.pos(owner.root());
        if (!world.isChunkLoaded(root) || !(world.getBlockEntity(root) instanceof CompositeBlockEntity entity) || !owner.equals(entity.resident())) return true;
        boolean standing = standing(world, owner);
        if (!start) return true;
        if (standing) { remove(world, player, owner, false); return true; }
        long now = world.getTime();
        var hits = HITS.computeIfAbsent(world, unused -> new LinkedHashMap<>());
        var hit = hits.get(owner.instanceId());
        if (hit != null && hit.last == now) return true;
        if (hit == null || now - hit.first > 100 || now < hit.first) hit = new Hit(now, now, 1);
        else hit = new Hit(hit.first, now, hit.count + 1);
        hits.put(owner.instanceId(), hit);
        if (hits.size() > 512) { hits.entrySet().removeIf(e -> now - e.getValue().last > 100 || now < e.getValue().last); while (hits.size() > 512) hits.remove(hits.keySet().iterator().next()); }
        world.playSound(null, root, SoundEvents.BLOCK_LANTERN_HIT, SoundCategory.BLOCKS, .45f, .9f + world.random.nextFloat() * .2f);
        for (var tracking : PlayerLookup.tracking(world, root)) { var buf = PacketByteBufs.create(); buf.writeIdentifier(world.getRegistryKey().getValue()); buf.writeBlockPos(root); ServerPlayNetworking.send(tracking, LanternMotion.PACKET, buf); }
        if (hit.count >= 5 && remove(world, player, owner, true)) hits.remove(owner.instanceId());
        return true;
    }
    private static boolean remove(ServerWorld world, ServerPlayerEntity player, Owner owner, boolean extinguish) {
        var access = new FabricCompositeWorld(world, Map.of());
        var preparation = CompositeRuntime.core().removal(access, owner);
        if (!preparation.accepted()) return false;
        for (var cell : preparation.plan().writes().keySet()) {
            var pos = CompositeData.pos(cell);
            if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos) || !world.canPlayerModifyAt(player, pos)) return false;
            var state = world.getBlockState(pos); var entity = world.getBlockEntity(pos);
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, state, entity)) {
                PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(world, player, pos, state, entity); return false;
            }
        }
        var rechecked = CompositeRuntime.core().removal(access, owner);
        if (!rechecked.accepted() || !preparation.plan().equals(rechecked.plan())) return false;
        if (extinguish) {
            var root = CompositeData.pos(owner.root());
            var rootState = world.getBlockState(root);
            if (!rootState.contains(CompositeRootBlock.OPEN)) return false;
            if (!rootState.get(CompositeRootBlock.OPEN) && CompositeRuntime.transition(world, owner, rootState.with(CompositeRootBlock.OPEN, true), null).outcome() != TransactionCore.Outcome.COMMITTED) return false;
        }
        ItemStack item = CompositeRuntime.pick(world, owner);
        var result = CompositeRuntime.remove(world, owner, null, false);
        if (result.outcome() != TransactionCore.Outcome.COMMITTED) return false;
        // This is an explicit pickup action. The city disables ordinary tile drops.
        if (!item.isEmpty()) {
            var root = CompositeData.pos(owner.root());
            var dropped = new ItemEntity(world, root.getX() + .5, root.getY() + .5, root.getZ() + .5, item);
            dropped.setToDefaultPickupDelay();
            world.spawnEntity(dropped);
        }
        return true;
    }
}


