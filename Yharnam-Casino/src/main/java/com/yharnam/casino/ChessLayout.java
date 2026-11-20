package com.yharnam.casino;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** Local X is toward file H and local +Z is toward the white player. */
public final class ChessLayout {
    public static final double CELL=.19, SURFACE=.75, TRAY=.754, LIFT=.046;
    public static final double BUTTON_X=1.14, BUTTON_Z=.838;
    public static final String PROMOTIONS="qrbn";
    private static final VoxelShape[][] TOPS=new VoxelShape[4][6];
    static {
        for(Direction f:Direction.Type.HORIZONTAL) for(int part=0;part<6;part++) {
            TOPS[f.getHorizontal()][part]=VoxelShapes.union(box(part,f,-1.44,-.97,0,1.44,.97,.722),
                    box(part,f,-.85,-.85,.722,.85,.85,SURFACE));
        }
    }

    private ChessLayout() {}
    public static double x(int square) { return ((square & 7)-3.5)*CELL; }
    public static double z(int square) { return (3.5-(square >> 3))*CELL; }
    public static int square(double x,double z) {
        int file=(int)Math.floor(x/CELL+4), rank=(int)Math.floor(-z/CELL+4);
        return file>=0 && file<8 && rank>=0 && rank<8 ? file+8*rank : -1;
    }
    public static String name(int square) { return ""+(char)('a'+(square&7))+(1+(square>>3)); }
    public static double trayX(boolean white,int slot) { return (white?-1:1)*(1.14+(slot/8-.5)*.209); }
    public static double trayZ(int slot) { return ((slot%8)-3.5)*.179; }
    public static double choiceX(int index) { return (index-1.5)*.215; }
    public static int choice(double x,double z) {
        if (Math.abs(z-.872)>.092) return -1;
        int i=(int)Math.floor(x/.215+2);
        return i>=0 && i<4 ? i : -1;
    }
    public static boolean button(double x,double z,int side) {
        return Math.abs(x-side*BUTTON_X)<=.121 && Math.abs(z-BUTTON_Z)<=.081;
    }
    public static Vec3d center(BlockPos base,Direction facing) {
        return Vec3d.ofBottomCenter(base).add(.5*facing.getOffsetX(),0,.5*facing.getOffsetZ());
    }
    public static Vec3d local(BlockPos base,Direction f,Vec3d hit) {
        var d=hit.subtract(center(base,f));var right=f.rotateYCounterclockwise();
        return new Vec3d(d.x*right.getOffsetX()+d.z*right.getOffsetZ(),d.y,d.x*f.getOffsetX()+d.z*f.getOffsetZ());
    }
    public static double height(char p) {
        return switch(Character.toLowerCase(p)) {case 'p'->.139;case 'r'->.158;case 'n','b','q'->.19;default->.217;};
    }
    // Clip each shape to its owning cell before rotating: a ray may enter any of the six cells first.
    static VoxelShape box(int part,Direction f,double x0,double z0,double y0,double x1,double z1,double y1) {
        int col=part%3-1,row=part/3;
        double l=Math.max(0,.5+x0-col),r=Math.min(1,.5+x1-col);
        double n=Math.max(0,1+z0-row),s=Math.min(1,1+z1-row);
        if(l>=r || n>=s) return VoxelShapes.empty();
        return switch(f) {
            case SOUTH -> VoxelShapes.cuboid(l,y0,n,r,y1,s);
            case NORTH -> VoxelShapes.cuboid(1-r,y0,1-s,1-l,y1,1-n);
            case WEST -> VoxelShapes.cuboid(1-s,y0,l,1-n,y1,r);
            case EAST -> VoxelShapes.cuboid(n,y0,1-r,s,y1,1-l);
            default -> throw new IllegalArgumentException();
        };
    }
    static VoxelShape tabletop(int part,Direction f) {
        return TOPS[f.getHorizontal()][part];
    }
}
