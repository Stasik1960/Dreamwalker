package dev.dreamwalker.bloodbornedw.composite;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

/** Metadata follows Sodium's immutable cloned SECTIONS, including their exact
 * revisions. Workers never query the live ClientWorld or a coordinate cache. */
public final class NativeVisualSodiumSnapshot implements NativeVisualView {
    public interface Section {
        NativeVisualData nativeVisualSectionData();
        ChunkSectionPos nativeVisualSectionPos();
    }
    public interface Context { NativeVisualSodiumSnapshot nativeVisualSnapshot(); }
    private record ContextAccess(java.lang.reflect.Method origin,java.lang.reflect.Method sections){}
    private static final ClassValue<ContextAccess> ACCESS=new ClassValue<>(){
        @Override protected ContextAccess computeValue(Class<?> type){
            try{return new ContextAccess(type.getMethod("getOrigin"),type.getMethod("getSections"));}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported Sodium snapshot context",failure);}
        }
    };
    public static final NativeVisualSodiumSnapshot EMPTY=new NativeVisualSodiumSnapshot(0,0,0,new NativeVisualData[27]);
    private final int x,y,z;
    private final NativeVisualData[] sections;
    private NativeVisualSodiumSnapshot(int x,int y,int z,NativeVisualData[] sections){this.x=x;this.y=y;this.z=z;this.sections=sections;}
    private int local(int X,int Y,int Z){int a=X-x,b=Y-y,c=Z-z;return a<0||a>=3||b<0||b>=3||c<0||c>=3?-1:a+3*(c+3*b);}
    public static NativeVisualSodiumSnapshot captureContext(Object context){
        var access=ACCESS.get(context.getClass());
        try{return capture((ChunkSectionPos)access.origin().invoke(context),(Object[])access.sections().invoke(context));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot capture Sodium's immutable section context",failure);}
    }
    public static NativeVisualSodiumSnapshot capture(ChunkSectionPos origin,Object[] cloned){
        var result=new NativeVisualSodiumSnapshot(origin.getSectionX()-1,origin.getSectionY()-1,origin.getSectionZ()-1,new NativeVisualData[27]);
        for(Object raw:cloned){
            if(raw==null)continue;
            if(!(raw instanceof Section section))throw new IllegalStateException("Sodium cloned section lacks its visual snapshot");
            var pos=section.nativeVisualSectionPos();int at=result.local(pos.getSectionX(),pos.getSectionY(),pos.getSectionZ());
            if(at<0)throw new IllegalStateException("Sodium visual snapshot outside its cloned volume");
            result.sections[at]=section.nativeVisualSectionData();
        }
        return result;
    }
    @Override public int nativeVisualIndex(BlockPos pos){int at=local(pos.getX()>>4,pos.getY()>>4,pos.getZ()>>4);var data=at<0?null:sections[at];return data==null?-1:data.at(pos);}
}
