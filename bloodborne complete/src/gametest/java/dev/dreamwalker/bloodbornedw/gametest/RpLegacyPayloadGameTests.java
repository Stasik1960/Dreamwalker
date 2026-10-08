package dev.dreamwalker.bloodbornedw.gametest;

import dev.dreamwalker.bloodbornerp.content.SourceLegacyPayload;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.mob.RpMobEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.test.*;
import net.minecraft.util.Identifier;

/** Real entity NBT round trips; no visual or source-city acceptance is inferred. */
public final class RpLegacyPayloadGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="rp_legacy_payload")
    public void actualObjectRetainsAllTypedUnknownFieldsAndEnvelopeWithoutNesting(TestContext context){
        RpObjectEntity entity=(RpObjectEntity)create("small_gate",context);NbtCompound source=entity.writeNbt(new NbtCompound());
        source.remove("Open");source.putInt("AnimationId",7);source.putInt("EntityState",1);source.putBoolean("CanUpdate",false);
        source.putBoolean("IsActivated",true);source.putIntArray("Id",new int[]{1,2,3,4});source.putString("ConnectionId","");source.put("Opaque",opaque());
        entity.readNbt(source.copy());context.assertTrue(entity.isOpen(),"Legacy gate state must retain existing RP open behavior");
        NbtCompound saved=entity.writeNbt(new NbtCompound());for(String key:new String[]{"AnimationId","EntityState","CanUpdate","IsActivated","Id","ConnectionId","Opaque"})context.assertTrue(source.get(key).equals(saved.get(key)),"Original typed field missing or changed: "+key);
        context.assertTrue(saved.getCompound(SourceLegacyPayload.KEY).getCompound("Original").equals(source),"Full original typed snapshot must remain live in the reserved payload");
        entity.setOpen(false);NbtCompound changed=entity.writeNbt(new NbtCompound());context.assertTrue(!changed.getBoolean("Open"),"Opaque source state must not override current functional gate state");
        entity.readNbt(changed);NbtCompound second=entity.writeNbt(new NbtCompound());context.assertTrue(second.getCompound(SourceLegacyPayload.KEY).equals(saved.getCompound(SourceLegacyPayload.KEY)),"Repeated save must reuse one original envelope without recursive growth");context.assertTrue(!entity.isOpen(),"Current explicit Open state takes precedence over archived EntityState");context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="rp_legacy_payload")
    public void actualMobKeepsSourcePayloadWhileCurrentHealthAndAiRemainLive(TestContext context){
        RpMobEntity entity=(RpMobEntity)create("cleric_beast",context);NbtCompound source=entity.writeNbt(new NbtCompound());source.putInt("AnimationId",3);source.putBoolean("CanUpdate",true);source.put("ForgeCaps",opaque());
        entity.readNbt(source.copy());float health=entity.getHealth();entity.setHealth(health-2);NbtCompound saved=entity.writeNbt(new NbtCompound());
        context.assertTrue(saved.getFloat("Health")==health-2,"Legacy provenance must not restore stale health");context.assertTrue(!entity.isFrozen(),"Source retention must not freeze mobs or replace AI behavior");context.assertTrue(saved.get("ForgeCaps").equals(source.get("ForgeCaps")),"Opaque ForgeCaps must remain typed and untouched");context.assertTrue(saved.getCompound(SourceLegacyPayload.KEY).getCompound("Original").equals(source),"Mob original snapshot lost");context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=40,batchId="rp_legacy_payload")
    public void ordinaryNewEntitiesDoNotAcquireLegacyEnvelope(TestContext context){Entity entity=create("hunterlamp",context);NbtCompound normal=entity.writeNbt(new NbtCompound());entity.readNbt(normal);context.assertTrue(!entity.writeNbt(new NbtCompound()).contains(SourceLegacyPayload.KEY),"New RP entities should not acquire a fabricated source history");context.complete();}
    private static Entity create(String id,TestContext context){Entity entity=Registries.ENTITY_TYPE.get(new Identifier("bloodborne_rp",id)).create(context.getWorld());if(entity==null)throw new IllegalStateException("Actual RP entity factory unavailable");return entity;}
    private static NbtCompound opaque(){NbtCompound value=new NbtCompound();value.putByte("byte",(byte)9);value.putShort("short",(short)300);value.putInt("int",40000);value.putLong("long",1L<<40);value.putFloat("float",.25f);value.putDouble("double",.125);value.putByteArray("bytes",new byte[]{1,2,-3});value.putString("string","bloodborne:opaque-must-not-be-rewritten");NbtList list=new NbtList();list.add(NbtInt.of(12));value.put("list",list);value.putIntArray("ints",new int[]{1,2,3});value.putLongArray("longs",new long[]{1,1L<<39});return value;}
}
