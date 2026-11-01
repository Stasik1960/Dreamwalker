package dev.dreamwalker.bloodbornedw.visual.mixin;

import dev.dreamwalker.bloodbornedw.visual.AltTreeBounds;
import dev.dreamwalker.bloodbornedw.visual.CityVisualClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "dev.dreamwalker.bloodbornerp.object.RpObjectEntity")
public abstract class AltTreeBoundsMixin extends Entity {
    @Shadow(remap = false) public abstract String assetId();
    @Shadow(remap = false) public abstract float objectScale();
    @Unique private volatile AltTreeBounds.Snapshot dw$bounds;

    protected AltTreeBoundsMixin(EntityType<?> type, World world) { super(type, world); }

    @Override public Box getVisibilityBoundingBox() {
        Box original = super.getVisibilityBoundingBox();
        if (!CityVisualClient.isAlt()) return original;
        Box local = AltTreeBounds.local(assetId());
        if (local == null) return original;
        double x = getX(), y = getY(), z = getZ();
        float yaw = getYaw(), pitch = getPitch(), scale = objectScale();
        AltTreeBounds.Snapshot old = dw$bounds;
        if (old != null && old.local() == local && old.x() == x && old.y() == y && old.z() == z && old.yaw() == yaw
                && old.pitch() == pitch && old.scale() == scale && old.original().equals(original)) return old.result();
        Box result = AltTreeBounds.world(local, x, y, z, yaw, pitch, scale, original);
        dw$bounds = new AltTreeBounds.Snapshot(x, y, z, yaw, pitch, scale, local, original, result);
        return result;
    }

}
