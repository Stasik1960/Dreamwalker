package dev.dreamwalker.bloodbornedw.composite;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;

/** One immutable manager/predicate pair per parsed component, released with
 * that resource-reload graph. No state-result, position or world cache. */
@Environment(EnvType.CLIENT)
public final class MultipartPredicateCache {
    private static final LongAdder COMPILED = new LongAdder();
    private MultipartPredicateCache() {}
    public record Entry(StateManager<Block,BlockState> manager, Predicate<BlockState> predicate) {}
    public static boolean owned(StateManager<Block,BlockState> manager) {
        String namespace = Registries.BLOCK.getId(manager.getOwner()).getNamespace();
        return namespace.equals("bloodborne_dw") || namespace.equals("bloodborne_rp");
    }
    public static Entry entry(StateManager<Block,BlockState> manager, Predicate<BlockState> predicate) {
        COMPILED.increment();
        return new Entry(manager,predicate);
    }
    public static long compiledOwnedComponentPredicates() { return COMPILED.sum(); }
}
