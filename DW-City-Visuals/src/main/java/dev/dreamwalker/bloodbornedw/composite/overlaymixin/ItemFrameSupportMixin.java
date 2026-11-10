package dev.dreamwalker.bloodbornedw.composite.overlaymixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFrameEntity.class)
public abstract class ItemFrameSupportMixin extends AbstractDecorationEntity {
    protected ItemFrameSupportMixin(EntityType<? extends AbstractDecorationEntity> type, World world) {
        super(type, world);
    }

    @WrapOperation(method = "canStayAttached", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/block/BlockState;isSolid()Z"))
    private boolean dw$acceptModelSurface(BlockState state, Operation<Boolean> original) {
        if (original.call(state)) return true;
        if (!Registries.BLOCK.getId(state.getBlock()).getNamespace().equals("bloodborne_dw")) return false;

        BlockPos support = attachmentPos.offset(facing.getOpposite());
        Direction.Axis axis = facing.getAxis();
        // The centre of a vanilla frame sits on the boundary of its supporting cell.
        // Accept narrow model faces at that boundary, while rejecting holes in the model.
        double edge = facing.getDirection() == Direction.AxisDirection.POSITIVE ? 1 - 1e-6 : 1e-6;
        double x = axis == Direction.Axis.X ? edge : 0.5;
        double y = axis == Direction.Axis.Y ? edge : 0.5;
        double z = axis == Direction.Axis.Z ? edge : 0.5;
        return state.getOutlineShape(getWorld(), support).getBoundingBoxes().stream()
            .anyMatch(box -> box.contains(x, y, z));
    }
}
