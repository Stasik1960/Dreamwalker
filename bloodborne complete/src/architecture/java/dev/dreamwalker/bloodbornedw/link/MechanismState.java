package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.DreamwalkerBb;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/** UUID bindings and undelivered effects; stored once in the server's overworld. */
public final class MechanismState extends PersistentState {
    static final int LIMIT=4096;
    final Map<UUID,Lever> levers=new LinkedHashMap<>();
    final Map<String,Target> targets=new LinkedHashMap<>();
    final Set<String> removed=new LinkedHashSet<>();
    long revision;
    public static MechanismState get(MinecraftServer server){MechanismState state=server.getOverworld().getPersistentStateManager().getOrCreate(MechanismState::fromNbt,MechanismState::new,"bloodborne_dw_mechanism_links");return state;}
    public static MechanismState fromNbt(NbtCompound nbt){
        MechanismState result=new MechanismState();if(!nbt.contains("Schema",NbtElement.INT_TYPE)||nbt.getInt("Schema")!=1)savedDataWarning(null,"mechanism_links",null,"MECHANISM_LINKS_SCHEMA","Expected Schema INT=1; actual type="+type(nbt,"Schema")+", numeric value="+nbt.getInt("Schema"),null);if(nbt.getInt("Schema")!=1)return result;result.revision=Math.max(0,nbt.getLong("Revision"));
        for(NbtElement raw:nbt.getList("Removed",NbtElement.STRING_TYPE)){if(result.removed.size()>=LIMIT)break;String key=raw.asString();if(key.length()<=256)result.removed.add(key);}
        for(NbtElement raw:nbt.getList("Targets",NbtElement.COMPOUND_TYPE)){
            if(result.targets.size()>=LIMIT)break;NbtCompound tag=(NbtCompound)raw;NbtCompound reference=tag.getCompound("Ref");BlockPos root=BlockPos.fromLong(reference.getLong("Root"));String identity=reference.containsUuid("Instance")?reference.getUuid("Instance").toString():"mechanism_target:"+root.toShortString();try{MechanismLinks.TargetRef ref=MechanismLinks.TargetRef.read(reference);if(!reference.contains("Root",NbtElement.LONG_TYPE)||(ref.kind()==MechanismLinks.Kind.ARCHITECTURE&&reference.getString("Registry").isEmpty())||(!reference.getString("Registry").isEmpty()&&net.minecraft.util.Identifier.tryParse(reference.getString("Registry"))==null))savedDataWarning(reference,identity,root,"MECHANISM_LINK_TARGET_CORRUPTION","Target Root/Registry metadata malformed; prior normalization retained (Root type="+type(reference,"Root")+")",null);if(ref==null||result.removed.contains(ref.key()))continue;Target target=new Target(ref);target.desired=tag.getBoolean("Desired");target.known=tag.getBoolean("Known");target.pending=tag.getBoolean("Pending");target.unresolvedFlip=tag.getBoolean("UnresolvedFlip");target.pulseOnly=tag.getBoolean("PulseOnly");target.pendingPulses=Math.max(0,tag.getLong("PendingPulses"));target.revision=Math.max(0,tag.getLong("Revision"));result.targets.putIfAbsent(ref.key(),target);}catch(IllegalArgumentException failure){savedDataWarning(reference,identity,root,"MECHANISM_LINK_TARGET_CORRUPTION","Saved target UUID/Kind/Dimension cannot be decoded; original skip retained",failure);}
        }
        for(NbtElement raw:nbt.getList("Levers",NbtElement.COMPOUND_TYPE)){
            if(result.levers.size()>=LIMIT)break;NbtCompound tag=(NbtCompound)raw;BlockPos root=BlockPos.fromLong(tag.getLong("Pos"));String identity=tag.containsUuid("Id")?tag.getUuid("Id").toString():"mechanism_lever:"+root.toShortString();try{UUID id=tag.getUuid("Id");String dimension=tag.getString("Dimension"),asset=tag.getString("Asset");if(net.minecraft.util.Identifier.tryParse(dimension)==null||!Set.of("lever_1","lever_2").contains(asset)){savedDataWarning(tag,identity,root,"MECHANISM_LINK_LEVER_CORRUPTION","Saved lever Dimension/Asset is invalid; original skip retained",null);continue;}if(!tag.contains("Pos",NbtElement.LONG_TYPE))savedDataWarning(tag,identity,root,"MECHANISM_LINK_LEVER_CORRUPTION","Saved lever Pos expected LONG; actual type="+type(tag,"Pos")+"; prior normalization retained",null);Lever lever=new Lever(id,dimension,tag.getLong("Pos"),asset);for(NbtElement key:tag.getList("Targets",NbtElement.STRING_TYPE)){if(lever.targets.size()>=256)break;if(result.targets.containsKey(key.asString()))lever.targets.add(key.asString());}result.levers.putIfAbsent(id,lever);}catch(IllegalArgumentException failure){savedDataWarning(tag,identity,root,"MECHANISM_LINK_LEVER_CORRUPTION","Saved lever UUID cannot be decoded; original skip retained",failure);}
        }return result;
    }
    private static String type(NbtCompound tag,String key){NbtElement value=tag.get(key);return value==null?"MISSING":Byte.toString(value.getType());}
    private static void savedDataWarning(NbtCompound ignored,String instance,BlockPos root,String category,String reason,Throwable failure){DreamwalkerBb.LOG.warn("{} for {} at {}: {}", category, instance, root, reason, failure);}
    @Override public NbtCompound writeNbt(NbtCompound nbt){
        nbt.putInt("Schema",1);nbt.putLong("Revision",revision);NbtList targetsOut=new NbtList(),leversOut=new NbtList(),removedOut=new NbtList();
        for(Target target:targets.values()){NbtCompound tag=new NbtCompound();tag.put("Ref",target.ref.write());tag.putBoolean("Desired",target.desired);tag.putBoolean("Known",target.known);tag.putBoolean("Pending",target.pending);tag.putBoolean("UnresolvedFlip",target.unresolvedFlip);tag.putBoolean("PulseOnly",target.pulseOnly);tag.putLong("PendingPulses",target.pendingPulses);tag.putLong("Revision",target.revision);targetsOut.add(tag);}
        for(Lever lever:levers.values()){NbtCompound tag=new NbtCompound();tag.putUuid("Id",lever.id);tag.putString("Dimension",lever.dimension);tag.putLong("Pos",lever.pos);tag.putString("Asset",lever.asset);NbtList keys=new NbtList();for(String key:lever.targets)keys.add(NbtString.of(key));tag.put("Targets",keys);leversOut.add(tag);}
        for(String key:removed)removedOut.add(NbtString.of(key));nbt.put("Targets",targetsOut);nbt.put("Levers",leversOut);nbt.put("Removed",removedOut);return nbt;
    }
    static final class Lever {final UUID id;String dimension,asset;long pos;final Set<String> targets=new LinkedHashSet<>();Lever(UUID id,String dimension,long pos,String asset){this.id=id;this.dimension=dimension;this.pos=pos;this.asset=asset;}}
    static final class Target {MechanismLinks.TargetRef ref;boolean desired,known,pending,unresolvedFlip,pulseOnly;long revision,pendingPulses;Target(MechanismLinks.TargetRef ref){this.ref=ref;}}
}
