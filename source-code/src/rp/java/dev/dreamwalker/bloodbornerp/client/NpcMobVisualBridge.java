package dev.dreamwalker.bloodbornerp.client;

import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import java.lang.reflect.*;
import net.minecraft.entity.LivingEntity;

/** Optional Custom NPC access; cached once per NPC class, no common-side linkage. */
public final class NpcMobVisualBridge {
    private record Access(Field display,Method size,Method killed,Method attacking){}
    private static final ClassValue<Access> ACCESS=new ClassValue<>(){
        @Override protected Access computeValue(Class<?> type){
            try{var display=type.getField("display");return new Access(display,display.getType().getMethod("getSize"),type.getMethod("isKilled"),type.getMethod("isAttacking"));}
            catch(ReflectiveOperationException error){throw new IllegalStateException("Unsupported Custom NPC visual API",error);}
        }
    };
    private NpcMobVisualBridge(){}
    public static float bind(RpMobEntity model,LivingEntity npc){
        try{
            var a=ACCESS.get(npc.getClass());model.bindNpcVisualState(npc,(Boolean)a.killed.invoke(npc),(Boolean)a.attacking.invoke(npc));
            return Math.max(.01f,((Number)a.size.invoke(a.display.get(npc))).floatValue()*.2f);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Unable to read Custom NPC visual state",error);}
    }
}
