package dev.dreamwalker.bloodbornedw.tool.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.storage.ChunkDataAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Test-only access to native persisted entity-region IO, absent in production. */
@Mixin(ServerEntityManager.class)
public interface BbEntityStorageTestAccess {
    @Accessor("dataAccess")
    ChunkDataAccess<Entity> bloodborne_dw$testEntityStorage();
}
