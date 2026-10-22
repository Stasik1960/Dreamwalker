package dev.dreamwalker.bloodbornedw.composite;

import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Storage and interaction remain owned. Only the rendering of immobile source
 * architecture moves to Minecraft's existing section buffers. */
public final class CompositeTerrain {
    public static final int UPDATE_SECTION_RADIUS=4;
    private static final boolean ENABLED=Boolean.parseBoolean(System.getProperty("dreamwalker.terrain.enabled","true"));
    private static final int[] PILOT=pilot();
    private static final java.util.concurrent.atomic.LongAdder rejectedRegions=new java.util.concurrent.atomic.LongAdder();
    private CompositeTerrain(){}
    private static int[] pilot(){
        String value=System.getProperty("dreamwalker.terrain.pilot","").trim();if(value.isEmpty())return null;
        String[] fields=value.split(",");if(fields.length!=4)throw new IllegalArgumentException("dreamwalker.terrain.pilot requires minX,minZ,maxX,maxZ");
        int[] bounds=new int[4];for(int i=0;i<4;i++)bounds[i]=Integer.parseInt(fields[i].trim());
        if(bounds[0]>bounds[2]||bounds[1]>bounds[3])throw new IllegalArgumentException("Reversed terrain pilot bounds");return bounds;
    }
    public static boolean eligible(BlockState state){
        if(!ENABLED||!(state.getBlock() instanceof CompositeRootBlock block))return false;
        if(ImportedCompactBlock.compact(state))return false;
        var spec=block.spec;
        return spec.id.getNamespace().equals("bloodborne_dw")&&spec.id.getPath().startsWith("source_")
            &&!(block instanceof ImportedLadderBlock)&&!(block instanceof ThinWindowRootBlock)
            &&!spec.openable&&!spec.functionalDoorPhysics&&spec.importedClimbingBounds==null&&!spec.requiredSupport;
    }
    public static boolean at(BlockState state,BlockPos root){return eligible(state)&&(PILOT==null||root.getX()>=PILOT[0]&&root.getZ()>=PILOT[1]&&root.getX()<=PILOT[2]&&root.getZ()<=PILOT[3]);}
    public static String scope(){return !ENABLED?"DISABLED":PILOT==null?"ALL_IMMOBILE_SOURCE_ROOTS":Arrays.toString(PILOT);}
    public static long rejectedRegionRevisions(){return rejectedRegions.sum();}

    /** The compound belongs to an immutable BE revision: the BE replaces its
     * payload on every write and exposes only copies to editing code. No world,
     * entity, GPU buffer or second per-position cache is retained here. */
    public static final class Snapshot {
        private final BlockState state;
        private final Owner owner;
        private final NbtCompound payload;
        private final boolean meshable;
        private final String rejectionReason;
        Snapshot(BlockState state,Owner owner,NbtCompound payload,BlockPos root){this.state=state;this.owner=owner;this.payload=payload;rejectionReason=owner==null?"NO_OWNER":CompositeTerrain.rejectionReason(state,payload,root);meshable=rejectionReason==null;if("OUTSIDE_NATIVE_RENDER_REGION".equals(rejectionReason)||"OUTSIDE_NATIVE_UPDATE_NEIGHBOURHOOD".equals(rejectionReason))rejectedRegions.increment();}
        public BlockState state(){return state;}
        public Owner owner(){return owner;}
        public boolean meshable(){return meshable;}
        public String rejectionReason(){return rejectionReason;}
        NbtCompound payload(){return payload;}
    }
    /** A vanilla rebuild snapshots three complete chunks in each horizontal
     * direction. Check every projected source-neighbour sample before choosing
     * terrain: exceptional remote pivots retain the old renderer, without
     * reading a live world or pretending a loaded occluder is air. */
    private static String rejectionReason(BlockState state,NbtCompound payload,BlockPos root){
        var block=(CompositeRootBlock)state.getBlock();
        if(block.spec.simplePhysicalCollision&&ImportedTypeAliases.needsMigration(state,payload)){payload=ImportedTypeAliases.transformPayload(state,payload);state=ImportedTypeAliases.target(state);block=(CompositeRootBlock)state.getBlock();}
        if(!eligible(state))return "FUNCTIONAL_ALIAS";
        var shift=CompositeSourceShift.world(state,payload);
        Matrix4f outer=new Matrix4f().translation((float)shift.x(),(float)(payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY)+shift.y()),(float)shift.z());
        rotate(outer,.5,.5,.5,-state.get(CompositeRootBlock.ROTATION)*45,false);
        double minX=((root.getX()>>4)-1)*16.0,maxX=minX+48,minZ=((root.getZ()>>4)-1)*16.0,maxZ=minZ+48;
        var point=new Vector3f();
        for(var part:ImportedStairLegacyArt.selectedParts(block,state,payload)){
            var matrix=new Matrix4f(outer).translate((float)(part.offset().x()/16),(float)(part.offset().y()/16),(float)(part.offset().z()/16));
            rotate(matrix,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.yaw(),false);
            rotate(matrix,part.pivot().x()/16,part.pivot().y()/16,part.pivot().z()/16,-part.pitch(),true);
            rotate(matrix,part.extraPivot().x()/16,part.extraPivot().y()/16,part.extraPivot().z()/16,part.extraYaw(),false);
            // Wider than vanilla's face + two AO side neighbours. Integer
            // world projection floors these transformed block centres.
            for(int corner=0;corner<8;corner++){
                point.set((corner&1)==0?-1.5f:2.5f,(corner&2)==0?-1.5f:2.5f,(corner&4)==0?-1.5f:2.5f).mulPosition(matrix);double X=root.getX()+point.x,Z=root.getZ()+point.z;
                if(!Float.isFinite(point.x)||!Float.isFinite(point.y)||!Float.isFinite(point.z)||X<minX||X>=maxX||Z<minZ||Z>=maxZ)return "OUTSIDE_NATIVE_RENDER_REGION";
                if(Math.abs(((int)Math.floor((root.getY()+point.y)/16.0))-(root.getY()>>4))>UPDATE_SECTION_RADIUS)return "OUTSIDE_NATIVE_UPDATE_NEIGHBOURHOOD";
            }
        }
        return null;
    }
    private static void rotate(Matrix4f matrix,double x,double y,double z,double degrees,boolean pitch){if(degrees==0)return;matrix.translate((float)x,(float)y,(float)z);if(pitch)matrix.rotateX((float)Math.toRadians(degrees));else matrix.rotateY((float)Math.toRadians(degrees));matrix.translate((float)-x,(float)-y,(float)-z);}
}
