package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import dev.dreamwalker.bloodbornedw.composite.ImportedCollisionMath.Box;

/** Boundary cases prove six independent protrusion distances, not model size. */
public final class ImportedCollisionMathChecks {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static boolean same(double a, double b) { return Math.abs(a-b) < 1e-8; }
    private static double[] values(Box b) { return new double[]{b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1()}; }
    public static void main(String[] args) {
        for (int face = 0; face < 6; face++) for (double distance : new double[]{.1,.49,.5-1e-10,.5,.5+1e-10,.51,2}) {
            double[] raw={.1,.1,.1,.9,.9,.9}; raw[face]=face<3?-distance:1+distance;
            var out=ImportedCollisionMath.transform(List.of(new Box(raw[0],raw[1],raw[2],raw[3],raw[4],raw[5])),0,0);
            check(out.size()==1, "One box required face="+face);
            double wanted=distance<.5?(face<3?0:1):raw[face];
            check(same(values(out.get(0))[face],wanted), "Strict face threshold face="+face+" distance="+distance);
            for (int unchanged=0;unchanged<6;unchanged++) if (unchanged!=face)
                check(same(values(out.get(0))[unchanged],raw[unchanged]), "Other face changed="+unchanged);
        }
        var asymmetric=ImportedCollisionMath.transform(List.of(new Box(-.2,-.5,-2,3,1.1,4)),0,0).get(0);
        check(same(asymmetric.x0(),0)&&same(asymmetric.y0(),-.5)&&same(asymmetric.z0(),-2)&&same(asymmetric.x1(),3)&&same(asymmetric.y1(),1)&&same(asymmetric.z1(),4), "Six sides must trim independently for multiblock object");
        var tiny=ImportedCollisionMath.transform(List.of(new Box(.25,0,.25,.75,.4,.75)),0,0).get(0);
        check(tiny.equals(new Box(.25,0,.25,.75,.4,.75)), "Small model cannot become a full cube");
        var diagonal=ImportedCollisionMath.transform(List.of(new Box(0,0,0,1,1,1)),1,0);
        check(diagonal.size()==1&&same(diagonal.get(0).x0(),0)&&same(diagonal.get(0).x1(),1)&&same(diagonal.get(0).z0(),0)&&same(diagonal.get(0).z1(),1), "45-degree cube has one ordinary cropped box, no raster strips");
        var longDiagonal=ImportedCollisionMath.transform(List.of(new Box(0,0,0,8,1,2)),1,0);
        check(longDiagonal.size()==1&&longDiagonal.get(0).x1()>1.5&&longDiagonal.get(0).z1()>1.5, "Long diagonal object must keep multiblock dimensions");
        var longParts=ImportedCollisionMath.transform(List.of(new Box(0,0,0,1.2,.5,1),new Box(1.2,.5,0,3,1,1)),0,0);
        check(longParts.size()==2&&same(longParts.get(0).x1(),1.2)&&same(longParts.get(1).x1(),3), "Internal small pieces of long stairs must not be individually cropped to root cell");
        check(ImportedCollisionMath.transform(List.of(),7,.25).isEmpty(), "Passable empty geometry stays empty");
        var mounted=ImportedCollisionMath.transform(List.of(new Box(0,0,0,1,.5,1)),0,.25).get(0);
        check(same(mounted.y0(),.25)&&same(mounted.y1(),.75), "Mount height remains an instance transform");
        var shifted=ImportedCollisionMath.transform(List.of(new Box(-.4,0,0,1.4,.5,1)),0,0,true,.25,0,0).get(0);
        check(same(shifted.x0(),0)&&same(shifted.x1(),1.65), "SourceShift must precede protrusion test; an earlier crop would lose the preserved long side");
        var climbing=ImportedCollisionMath.transform(List.of(new Box(0,0,0,1,1,1)),1,0,false).get(0);
        check(climbing.x0()<0&&climbing.x1()>1&&climbing.z0()<0&&climbing.z1()>1, "Climbing interaction volume is not cropped as physical collision");
        System.out.println("PASS "+checks+" checks: all six strict thresholds, small models, 45 degrees, multiblock pieces, passability and mount shift");
    }
}
