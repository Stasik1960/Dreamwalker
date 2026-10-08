package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** New construction anchors use actual transformed bounds; source migration bypasses this. */
public final class GlazingMount {
    private GlazingMount() {}
    public static BlockState placementState(BlockState state,ItemPlacementContext context) {
        Direction face=context.getSide();
        ThinWindowRootBlock.Mount mount=face==Direction.DOWN?ThinWindowRootBlock.Mount.CEILING
                :face==Direction.UP&&!context.getPlayer().isSneaking()?ThinWindowRootBlock.Mount.FLOOR:ThinWindowRootBlock.Mount.VERTICAL;
        state=state.with(ThinWindowRootBlock.MOUNT,mount);
        if(face.getAxis().isHorizontal())state=state.with(CompositeRootBlock.ROTATION,switch(face){case NORTH->0;case EAST->2;case SOUTH->4;case WEST->6;default->0;});
        else state=state.with(CompositeRootBlock.ROTATION,GlazingTypes.placementRotation(context.getPlayerYaw()));
        return state;
    }
    public static NbtCompound placementPayload(ItemPlacementContext context,NbtCompound supplied) {
        NbtCompound payload=supplied==null?new NbtCompound():supplied.copy();
        payload.remove("MountY");payload.remove("SourceShift");payload.remove("GlazingSurfaceY");
        payload.putBoolean("GlazingMounted",true);payload.putBoolean("GlazingFreshPlacement",true);
        payload.putDouble("GlazingClickY",context.getHitPos().y-context.getBlockPos().getY());
        payload.putString("MountFace",context.getSide().asString());return payload;
    }
    public static void rememberAnchor(BlockState before,NbtCompound payload) {
        if(!payload.getBoolean("GlazingMounted")||payload.contains("GlazingSurfaceY",99)||payload.getBoolean("GlazingFreshPlacement"))return;
        var bounds=((CompositeRootBlock)before.getBlock()).spec.mountedBounds(before);
        payload.putDouble("GlazingSurfaceY",payload.getDouble("MountY")+
                (ThinWindowRootBlock.mount(before)==ThinWindowRootBlock.Mount.CEILING?bounds.to().y():bounds.from().y())/16);
    }
    public static boolean seat(World world,BlockPos root,BlockState state,NbtCompound payload) {
        if(!payload.getBoolean("GlazingMounted"))return true;
        CompositeSpec spec=((CompositeRootBlock)state.getBlock()).spec;
        var b=spec.mountedBounds(state);var mode=ThinWindowRootBlock.mount(state);
        Direction face=Direction.byName(payload.getString("MountFace"));if(face==null)face=Direction.UP;
        if(payload.getBoolean("GlazingFreshPlacement")){
            double surface;
            if(mode==ThinWindowRootBlock.Mount.CEILING){Double bottom=CompositeRuntime.bottomHeight(world,root.up());surface=bottom==null?payload.getDouble("GlazingClickY"):1+bottom;}
            else if(mode==ThinWindowRootBlock.Mount.FLOOR||!face.getAxis().isHorizontal()){
                Double top=CompositeRuntime.topHeight(world,root.down(),null);surface=top==null?payload.getDouble("GlazingClickY"):top-1;
            }else surface=0;
            payload.putDouble("GlazingSurfaceY",surface);payload.remove("GlazingFreshPlacement");payload.remove("GlazingClickY");
        }else if(!payload.contains("GlazingSurfaceY",99)){
            // Old V8 mounted blocks retain their saved world pose even when
            // their clicked surface was removed before this update/restart.
            payload.putDouble("GlazingSurfaceY",payload.getDouble("MountY")+
                    (mode==ThinWindowRootBlock.Mount.CEILING?b.to().y():b.from().y())/16);
        }
        double surface=payload.getDouble("GlazingSurfaceY");
        payload.putDouble("MountY",surface-(mode==ThinWindowRootBlock.Mount.CEILING?b.to().y():b.from().y())/16);
        payload.remove("SourceShift");
        if(mode==ThinWindowRootBlock.Mount.VERTICAL&&face.getAxis().isHorizontal()){
            NbtList shift=new NbtList();shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(0));shift.add(NbtDouble.of(1-b.to().z()/16));payload.put("SourceShift",shift);
        }
        return true;
    }
}
