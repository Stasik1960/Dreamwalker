package dev.dreamwalker.bloodbornerp.object;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** RP editing checks bounds/permission; native blocks and other RP are never replacement targets. */
public final class RpPlacementRules {
    private RpPlacementRules() {}
    public static String bounds(World world,Box box){
        if(!Double.isFinite(box.minX)||!Double.isFinite(box.minY)||!Double.isFinite(box.minZ)||!Double.isFinite(box.maxX)||!Double.isFinite(box.maxY)||!Double.isFinite(box.maxZ))return "non_finite_visual_bounds";
        if(box.minY<world.getBottomY()||box.maxY>world.getTopY())return "visual_bounds_outside_build_height";
        if(!world.getWorldBorder().contains(box))return "visual_bounds_outside_world_border";
        for(int x=MathHelper.floor(box.minX)>>4;x<=MathHelper.floor(box.maxX-1e-7)>>4;x++)
            for(int z=MathHelper.floor(box.minZ)>>4;z<=MathHelper.floor(box.maxZ-1e-7)>>4;z++)
                if(!world.isChunkLoaded(x,z))return "visual_chunk_not_loaded";
        return null;
    }
    public static boolean canEdit(PlayerEntity player,RpObjectEntity object){
        if(player==null||player.getWorld()!=object.getWorld()||!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(player))return false;
        var nearest=RpObjectSelection.nearestPoint(object,player.getEyePos());
        return nearest!=null&&nearest.squaredDistanceTo(player.getEyePos())<=64&&player.getWorld().canPlayerModifyAt(player,BlockPos.ofFloored(nearest));
    }
}
