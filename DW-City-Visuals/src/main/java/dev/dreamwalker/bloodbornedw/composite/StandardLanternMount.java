package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;

/** Public bridge keeps package-private baseline placement types inside their package. */
public final class StandardLanternMount {
    private StandardLanternMount() {}
    public static Object align(CompositeSpec spec, BlockState state, NbtCompound payload, ItemPlacementContext context) {
        if (!spec.id.toString().equals("bloodborne_dw:source_standard_lantern")) return CompositeSurfacePlacement.align(spec, state, payload, context);
        var clicked = net.minecraft.util.math.BlockPos.ofFloored(context.getHitPos().subtract(context.getSide().getOffsetX() * 0.001, context.getSide().getOffsetY() * 0.001, context.getSide().getOffsetZ() * 0.001));
        var clickedId = Registries.BLOCK.getId(context.getWorld().getBlockState(clicked).getBlock()).toString();
        var target = context.getPlayer() == null ? null : CompositeRuntime.target(context.getWorld(), clicked, context.getPlayer());
        boolean postTarget = target != null && target.registryId().equals("bloodborne_dw:source_street_lamp_post");
        boolean post = postTarget || clickedId.equals("bloodborne_dw:source_street_lamp_post");
        boolean standing = context.getSide() == Direction.UP && !post;
        if (post) {
            var postRoot = postTarget ? CompositeData.pos(target.root()) : clicked;
            var postState = context.getWorld().getBlockState(postRoot);
            if (state.contains(CompositeRootBlock.ROTATION)
                    && postState.contains(CompositeRootBlock.ROTATION)) {
                state = state.with(CompositeRootBlock.ROTATION,
                        postState.get(CompositeRootBlock.ROTATION));
            }
        }
        state = state.with(CompositeRootBlock.VARIANT, standing ? 1 : 0);
        payload = payload == null ? new NbtCompound() : payload.copy();
        payload.remove("SourceShift");
        var hit = context.getHitPos();
        var root = context.getBlockPos();
        var localShift = post
                ? new CompositeSourceShift.Shift(0, 1.0 / 16.0, 0)
                : new CompositeSourceShift.Shift(
                        standing ? 0 : hit.x - root.getX() - 0.5,
                        hit.y - root.getY() - (standing ? 0 : 31.0 / 16.0),
                        standing ? 0 : hit.z - root.getZ() - 0.5)
                        .rotate(-state.get(CompositeRootBlock.ROTATION));
        NbtList shift = new NbtList();
        shift.add(NbtDouble.of(localShift.x()));
        shift.add(NbtDouble.of(localShift.y()));
        shift.add(NbtDouble.of(localShift.z()));
        payload.put("SourceShift", shift);
        return new CompositeSurfacePlacement.Placement(state, payload);
    }
}
