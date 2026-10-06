package dev.dreamwalker.bloodbornedw.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractBlock.Settings.class)
public interface AbstractBlockSettingsAccessor {
    @Accessor("lootTableId") @Mutable void bloodborneDw$setLootTableId(Identifier id);
    @Accessor("allowsSpawningPredicate") AbstractBlock.TypedContextPredicate<EntityType<?>> bloodborneDw$allowsSpawningPredicate();
    @Accessor("solidBlockPredicate") AbstractBlock.ContextPredicate bloodborneDw$solidBlockPredicate();
    @Accessor("suffocationPredicate") AbstractBlock.ContextPredicate bloodborneDw$suffocationPredicate();
    @Accessor("blockVisionPredicate") AbstractBlock.ContextPredicate bloodborneDw$blockVisionPredicate();
    @Accessor("postProcessPredicate") AbstractBlock.ContextPredicate bloodborneDw$postProcessPredicate();
    @Accessor("allowsSpawningPredicate") @Mutable void bloodborneDw$setAllowsSpawningPredicate(AbstractBlock.TypedContextPredicate<EntityType<?>> value);
    @Accessor("solidBlockPredicate") @Mutable void bloodborneDw$setSolidBlockPredicate(AbstractBlock.ContextPredicate value);
    @Accessor("suffocationPredicate") @Mutable void bloodborneDw$setSuffocationPredicate(AbstractBlock.ContextPredicate value);
    @Accessor("blockVisionPredicate") @Mutable void bloodborneDw$setBlockVisionPredicate(AbstractBlock.ContextPredicate value);
    @Accessor("postProcessPredicate") @Mutable void bloodborneDw$setPostProcessPredicate(AbstractBlock.ContextPredicate value);
}
