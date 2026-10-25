package dev.dreamwalker.bloodbornedw.architecture;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Use the installed reach API, including its own scale integrations. Keep
 * vanilla defaults when absent; do not apply Pehkui a second time. */
public final class InteractionReach {
    private static final Method ATTACK=api("getAttackRange"),BLOCK=api("getReachDistance");
    private InteractionReach(){}
    private static Method api(String name){
        if(!FabricLoader.getInstance().isModLoaded("reach-entity-attributes"))return null;
        try{return Class.forName("com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes").getMethod(name,LivingEntity.class,double.class);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported ReachEntityAttributes API: "+name,e);}
    }
    private static double effective(Method method,PlayerEntity player,double base){
        double value=base;
        if(method!=null)try{value=((Number)method.invoke(null,player,base)).doubleValue();}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot query effective interaction reach",e);}
        return Double.isFinite(value)?Math.max(0,value):base;
    }
    public static double attack(PlayerEntity player){return effective(ATTACK,player,player.isCreative()?6:3);}
    public static double block(PlayerEntity player){return effective(BLOCK,player,player.isCreative()?5:4.5);}
}
