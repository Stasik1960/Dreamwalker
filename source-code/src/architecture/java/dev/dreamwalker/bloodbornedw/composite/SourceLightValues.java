package dev.dreamwalker.bloodbornedw.composite;
import net.minecraft.util.math.MathHelper;
/** Bound normalized source AO before either chunk or retained-owner output
 * reaches a byte color packer. Ordinary in-range arrays are reused as-is. */
public final class SourceLightValues {
    private SourceLightValues(){}
    public static float[] normalized(float[] values){
        for(float v:values)if(v<0||v>1){float[] result=values.clone();for(int i=0;i<result.length;i++)result[i]=MathHelper.clamp(result[i],0,1);return result;}
        return values;
    }
}
