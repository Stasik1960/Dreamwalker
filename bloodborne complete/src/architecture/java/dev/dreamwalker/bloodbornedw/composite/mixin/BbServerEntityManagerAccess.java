package dev.dreamwalker.bloodbornedw.composite.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Entity storage finishes independently from the block-chunk FULL future. */
@Mixin(ServerWorld.class)
public interface BbServerEntityManagerAccess {
    @Accessor("entityManager")
    ServerEntityManager<Entity> bloodborne_dw$profileEntityManager();
}
