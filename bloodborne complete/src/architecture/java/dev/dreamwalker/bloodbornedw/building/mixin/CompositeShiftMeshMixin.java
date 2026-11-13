package dev.dreamwalker.bloodbornedw.building.mixin;

import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock;
import dev.dreamwalker.bloodbornedw.composite.CompositeWorldMesh;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CompositeWorldMesh.class, remap = false)
abstract class CompositeShiftMeshMixin {
    @Shadow @Final private static ConcurrentHashMap<Long, Long> SECTIONS;

    @Inject(method = "bake", at = @At("HEAD"))
    private static void dw$dependOnOwnerSection(MinecraftClient client, CompositeBlockEntity owner,
            CompositeRootBlock block, BlockState state, NbtCompound payload, int overlay,
            Map<Long, Long> dependencies, CallbackInfoReturnable<?> result) {
        // A debug pose changes the owner's section stamp. Some synchronous meshes
        // never sample lighting there, so register it before the retained upload.
        // The asynchronous snapshot already captures the root unconditionally.
        long section = ChunkSectionPos.from(owner.getPos()).asLong();
        dependencies.putIfAbsent(section, SECTIONS.getOrDefault(section, 0L));
    }
}
