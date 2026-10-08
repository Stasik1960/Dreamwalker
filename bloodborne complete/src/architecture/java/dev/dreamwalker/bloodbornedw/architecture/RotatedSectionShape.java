package dev.dreamwalker.bloodbornedw.architecture;

import dev.dreamwalker.bloodbornedw.runtime.ObjectGeometry.Box;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Thin local physics. Render protrusions never become extra occupied block cells. */
final class RotatedSectionShape {
    private static final int STRIPS = 32;
    private RotatedSectionShape() {}
    static VoxelShape rotate(Box box, int yaw) {
        if ((yaw & 1) == 0) {
            Box rotated = box.rotate(yaw / 2);
            return VoxelShapes.cuboid(rotated.minX(),rotated.minY(),rotated.minZ(),rotated.maxX(),rotated.maxY(),rotated.maxZ());
        }
        double angle=yaw*Math.PI/4,c=Math.cos(angle),s=Math.sin(angle);
        double[][] polygon=new double[4][2],corners={{box.minX(),box.minZ()},{box.maxX(),box.minZ()},{box.maxX(),box.maxZ()},{box.minX(),box.maxZ()}};
        for(int i=0;i<4;i++){double x=corners[i][0]-.5,z=corners[i][1]-.5;polygon[i][0]=.5+c*x-s*z;polygon[i][1]=.5+s*x+c*z;}
        VoxelShape result=VoxelShapes.empty();
        for(int strip=0;strip<STRIPS;strip++){
            double first=strip/(double)STRIPS,last=(strip+1)/(double)STRIPS;List<Double> intersections=new ArrayList<>();
            for(double x:new double[]{first,last})for(int i=0;i<4;i++){
                double[] a=polygon[i],b=polygon[(i+1)%4];
                if(Math.abs(a[0]-b[0])<1e-10){if(Math.abs(x-a[0])<1e-9){intersections.add(a[1]);intersections.add(b[1]);}}
                else{double t=(x-a[0])/(b[0]-a[0]);if(t>=-1e-9&&t<=1+1e-9)intersections.add(a[1]+t*(b[1]-a[1]));}
            }
            for(double[] point:polygon)if(point[0]>=first&&point[0]<=last)intersections.add(point[1]);
            if(intersections.isEmpty())continue;
            double bottom=clamp(intersections.stream().mapToDouble(Double::doubleValue).min().orElseThrow()),top=clamp(intersections.stream().mapToDouble(Double::doubleValue).max().orElseThrow());
            if(top-bottom>1e-9)result=VoxelShapes.union(result,VoxelShapes.cuboid(first,box.minY(),bottom,last,box.maxY(),top));
        }
        return result;
    }
    private static double clamp(double value){return Math.max(0,Math.min(1,value));}
}
