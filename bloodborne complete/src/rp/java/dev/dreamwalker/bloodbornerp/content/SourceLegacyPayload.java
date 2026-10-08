package dev.dreamwalker.bloodbornerp.content;

import java.util.Set;
import net.minecraft.nbt.*;

/** Opaque typed source provenance plus passthrough for fields the port does not handle. */
public final class SourceLegacyPayload {
    public static final String KEY="SourceLegacyPayload";
    private static final Set<String> ENGINE_FIELDS=Set.of(
        "id","UUID","Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown",
        "CustomName","CustomNameVisible","Silent","NoGravity","Glowing","Tags","Passengers","HasVisualFire","TicksFrozen",
        "Health","HurtTime","HurtByTimestamp","DeathTime","AbsorptionAmount","Attributes","ActiveEffects","FallFlying",
        "SleepingX","SleepingY","SleepingZ","Brain","HandItems","HandDropChances","ArmorItems","ArmorDropChances",
        "Leash","PersistenceRequired","CanPickUpLoot","LeftHanded","NoAI","DeathLootTable","DeathLootTableSeed",
        "Patrolling","PatrolTarget","PatrolLeader","Inventory","AngerTime","AngryAt");
    private SourceLegacyPayload(){}
    /** Validate the persistence envelope without changing the original read/fallback rules. */
    public static NbtCompound read(NbtCompound incoming,net.minecraft.entity.Entity entity){
        if(incoming.contains(KEY)){
            boolean valid=incoming.contains(KEY,NbtElement.COMPOUND_TYPE);
            NbtCompound envelope=incoming.getCompound(KEY);
            valid=valid&&envelope.contains("Schema",NbtElement.INT_TYPE)&&envelope.getInt("Schema")==1&&envelope.contains("Original",NbtElement.COMPOUND_TYPE);
            if(!valid)dev.dreamwalker.bloodbornerp.object.RpDiagnostics.error(entity,"source_payload_corruption","SourceLegacyPayload requires Schema INT 1 and Original COMPOUND; existing fallback behavior retained",null);
            else if(envelope.getCompound("Original").contains(KEY))dev.dreamwalker.bloodbornerp.object.RpDiagnostics.error(entity,"source_payload_recursion","Original provenance unexpectedly contains another SourceLegacyPayload envelope",null);
        }
        try{return read(incoming);}catch(RuntimeException failure){dev.dreamwalker.bloodbornerp.object.RpDiagnostics.error(entity,"source_payload_read","Unable to read typed source provenance",failure);throw failure;}
    }
    /** Reuse the original envelope without recursively snapshotting subsequent saves. */
    public static NbtCompound read(NbtCompound incoming){
        if(incoming.contains(KEY,NbtElement.COMPOUND_TYPE)){
            NbtCompound envelope=incoming.getCompound(KEY);
            if(envelope.getInt("Schema")==1&&envelope.contains("Original",NbtElement.COMPOUND_TYPE))return envelope.getCompound("Original").copy();
        }
        if(!(incoming.contains("AnimationId")||incoming.contains("EntityState")||incoming.contains("CanUpdate")||incoming.contains("IsActivated")))return null;
        return incoming.copy();
    }
    /** Current vanilla health/AI/position and current role fields always win. */
    public static void write(NbtCompound outgoing,NbtCompound original){
        if(original==null)return;
        for(String key:original.getKeys())if(!key.equals(KEY)&&!ENGINE_FIELDS.contains(key)&&!outgoing.contains(key))outgoing.put(key,original.get(key).copy());
        NbtCompound envelope=new NbtCompound();envelope.putInt("Schema",1);envelope.put("Original",original.copy());outgoing.put(KEY,envelope);
    }
    public static void write(NbtCompound outgoing,NbtCompound original,net.minecraft.entity.Entity entity){
        try{write(outgoing,original);}catch(RuntimeException failure){dev.dreamwalker.bloodbornerp.object.RpDiagnostics.error(entity,"source_payload_write","Unable to preserve typed source provenance during save",failure);throw failure;}
    }
}
