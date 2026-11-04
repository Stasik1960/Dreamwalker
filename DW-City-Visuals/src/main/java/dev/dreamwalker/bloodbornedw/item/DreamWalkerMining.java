package dev.dreamwalker.bloodbornedw.item;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** Server authority and narrow Adventure-mode exception for the NRP pickaxe. */
public final class DreamWalkerMining {
    private static final Identifier LANTERN = new Identifier("bloodborne_dw", "source_standard_lantern");

    private DreamWalkerMining() {}

    public static boolean isDreamWalkerBlock(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw");
    }

    public static boolean isLantern(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (Registries.BLOCK.getId(state.getBlock()).equals(LANTERN)) return true;
        ObjectInstance.Owner owner = owner(world, pos, player);
        return owner != null && LANTERN.toString().equals(owner.registryId());
    }

    /** V12 RP objects and their physical cells stay Creative-only. */
    public static boolean isRpObjectProxy(World world, BlockPos pos, PlayerEntity player) {
        ObjectInstance.Owner owner = owner(world, pos, player);
        if (owner != null && owner.registryId().startsWith("bloodborne_rp:")) return true;
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity entity && entity.resident() != null
                && entity.resident().registryId().startsWith("bloodborne_rp:")) return true;
        for (var contribution : CompositeRuntime.contributions(world, pos))
            if (contribution.owner().registryId().startsWith("bloodborne_rp:")) return true;
        return false;
    }

    private static ObjectInstance.Owner owner(World world, BlockPos pos, PlayerEntity player) {
        ObjectInstance.Owner direct = CompositeRuntime.target(world, pos, player);
        if (direct != null) return direct;
        if (world.getBlockEntity(pos) instanceof CompositeBlockEntity entity && entity.resident() != null)
            return entity.resident();
        List<dev.dreamwalker.bloodbornedw.composite.CompositeData.Contribution> contributions = CompositeRuntime.contributions(world, pos);
        ObjectInstance.Owner found = null;
        for (var contribution : contributions) {
            if (found != null && !found.equals(contribution.owner())) return null;
            found = contribution.owner();
        }
        return found;
    }

    public static boolean permits(PlayerEntity player, World world, BlockPos pos, BlockState state) {
        if (!isDreamWalkerBlock(state) || isLantern(world, pos, state, player)) return true;
        if (player.isCreative()) return true;
        if (isRpObjectProxy(world, pos, player)) return false;
        return player.getMainHandStack().isOf(NrpPickaxe.ITEM);
    }

    public static boolean bypassAdventureRestriction(PlayerEntity player, World world, BlockPos pos, GameMode mode) {
        if (mode != GameMode.ADVENTURE || !player.getMainHandStack().isOf(NrpPickaxe.ITEM)) return false;
        BlockState state = world.getBlockState(pos);
        return isDreamWalkerBlock(state) && !isLantern(world,pos,state,player) && !isRpObjectProxy(world,pos,player);
    }
}
