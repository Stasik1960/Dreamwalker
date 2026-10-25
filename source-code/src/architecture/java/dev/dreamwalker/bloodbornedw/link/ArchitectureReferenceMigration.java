package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.tool.ToolCommands;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;

/** Typed global-reference snapshots participating in one NEW assembly transaction.
 * No commands, pulses or object events are emitted during migration or rollback.
 */
public final class ArchitectureReferenceMigration {
    private ArchitectureReferenceMigration() {}
    private record State(NbtCompound links,NbtCompound rules,NbtCompound groups,NbtCompound policies,NbtCompound commands,
                         Map<String,ObjectPolicies.Initiator> context) {}
    public static final class Prepared {
        private final MinecraftServer server;
        private final State before,after;
        private final NbtCompound provenance;
        private boolean applied;
        private Prepared(MinecraftServer server,State before,State after,NbtCompound provenance){this.server=server;this.before=before;this.after=after;this.provenance=provenance;}
        public NbtCompound provenance(){return provenance.copy();}
        /** Called after exact world writes, before publication; failure rolls back both. */
        public void commit(){
            if(applied)return;
            State current=snapshot(server);
            if(!same(current,before))throw new IllegalStateException("assembly_references_changed_since_preflight");
            applied=true;
            try{restore(server,after);}catch(RuntimeException failure){restore(server,before);applied=false;throw failure;}
        }
        public void rollback(){if(applied){restore(server,before);applied=false;}}
    }
    public static Prepared prepare(MinecraftServer server,Map<MechanismLinks.TargetRef,MechanismLinks.TargetRef> changes){
        if(!server.isOnThread())throw new IllegalStateException("Assembly reference migration requires server thread");
        Map<String,MechanismLinks.TargetRef> byKey=new LinkedHashMap<>();
        changes.forEach((old,next)->{if(old.kind()!=MechanismLinks.Kind.ARCHITECTURE||next.kind()!=MechanismLinks.Kind.ARCHITECTURE||!old.dimension().equals(next.dimension()))throw new IllegalArgumentException("Only same-world architecture references may merge");if(byKey.putIfAbsent(old.key(),next)!=null)throw new IllegalArgumentException("Duplicate assembly reference");});
        State before=snapshot(server);
        NbtCompound links=before.links.copy(),rules=before.rules.copy(),groups=before.groups.copy(),policies=before.policies.copy(),commands=before.commands.copy();
        NbtCompound provenance=new NbtCompound();NbtList legacyOriginals=new NbtList(),policyOriginals=new NbtList(),commandOriginals=new NbtList();
        for(NbtElement raw:before.links.getList("Targets",10)){var entry=(NbtCompound)raw;if(byKey.containsKey(MechanismLinks.TargetRef.read(entry.getCompound("Ref")).key()))legacyOriginals.add(entry.copy());}
        for(NbtElement raw:before.policies.getList("Entries",10)){var entry=(NbtCompound)raw;if(byKey.containsKey(MechanismLinks.TargetRef.read(entry.getCompound("Ref")).key()))policyOriginals.add(entry.copy());}
        for(NbtElement raw:before.commands.getList("Entries",10)){var entry=(NbtCompound)raw;if(byKey.containsKey(ToolTarget.read(entry.getCompound("Target")).key()))commandOriginals.add(entry.copy());}
        provenance.put("LegacyTargets",legacyOriginals);provenance.put("Policies",policyOriginals);provenance.put("ToolCommands",commandOriginals);
        // Queue capacity is checked before any mutation. Every affected legacy
        // pending request is represented separately, without the ordinary
        // importLegacy pulse-clamp or a lost last-writer-wins target state.
        NbtList queue=rules.getList("Queue",10);long sequence=rules.getLong("Sequence");
        for(NbtElement raw:links.getList("Targets",10)){
            NbtCompound entry=(NbtCompound)raw;var old=MechanismLinks.TargetRef.read(entry.getCompound("Ref"));if(!byKey.containsKey(old.key())||!entry.getBoolean("Pending"))continue;
            long count=entry.getBoolean("PulseOnly")?entry.getLong("PendingPulses"):1;
            if(count<0||count>MechanismRules.LIMIT-queue.size())throw new IllegalStateException("assembly_pending_effect_queue_capacity");
            for(long i=0;i<count;i++){NbtCompound pending=new NbtCompound();pending.putLong("Number",++sequence);pending.put("Target",old.write());pending.putString("Effect",entry.getBoolean("PulseOnly")?"PULSE":entry.getBoolean("UnresolvedFlip")?"TOGGLE":entry.getBoolean("Desired")?"OPEN":"CLOSE");pending.putUuid("Rule",new UUID(0,0));queue.add(pending);}
            entry.putBoolean("Pending",false);entry.putLong("PendingPulses",0);
        }
        rules.put("Queue",queue);rules.putLong("Sequence",sequence);
        Map<String,NbtCompound> targets=new LinkedHashMap<>();
        for(NbtElement raw:links.getList("Targets",10)){
            NbtCompound entry=(NbtCompound)raw;entry.put("Ref",mapped(entry.getCompound("Ref"),byKey));
            String key=MechanismLinks.TargetRef.read(entry.getCompound("Ref")).key();NbtCompound previous=targets.get(key);
            if(previous==null||entry.getLong("Revision")>previous.getLong("Revision"))targets.put(key,entry);
        }
        NbtList targetList=new NbtList();targets.values().forEach(targetList::add);links.put("Targets",targetList);
        for(NbtElement raw:links.getList("Levers",10)){NbtCompound lever=(NbtCompound)raw;LinkedHashSet<String> keys=new LinkedHashSet<>();for(NbtElement key:lever.getList("Targets",8)){String text=key.asString();keys.add(byKey.containsKey(text)?byKey.get(text).key():text);}NbtList out=new NbtList();keys.forEach(k->out.add(NbtString.of(k)));lever.put("Targets",out);}
        for(NbtElement raw:rules.getList("Rules",10)){NbtCompound rule=(NbtCompound)raw;NbtList out=new NbtList();for(NbtElement target:rule.getList("Targets",10))out.add(mapped((NbtCompound)target,byKey));rule.put("Targets",out);}
        for(NbtElement raw:rules.getList("Queue",10)){NbtCompound entry=(NbtCompound)raw;entry.put("Target",mapped(entry.getCompound("Target"),byKey));}
        NbtList barriers=new NbtList();for(NbtElement key:rules.getList("MigrationImportedPending",8)){String text=key.asString();barriers.add(NbtString.of(byKey.containsKey(text)?byKey.get(text).key():text));}rules.put("MigrationImportedPending",barriers);
        for(NbtElement raw:groups.getList("Groups",10)){NbtCompound group=(NbtCompound)raw;group.put("Target",mapped(group.getCompound("Target"),byKey));}
        remapRecords(policies.getList("Entries",10),"Ref",false,byKey);
        remapRecords(commands.getList("Entries",10),"Target",true,byKey);
        Map<String,ObjectPolicies.Initiator> context=new LinkedHashMap<>();before.context.forEach((key,value)->context.putIfAbsent(byKey.containsKey(key)?byKey.get(key).key():key,value));
        return new Prepared(server,before,new State(links,rules,groups,policies,commands,context),provenance);
    }
    private static NbtCompound mapped(NbtCompound ref,Map<String,MechanismLinks.TargetRef> byKey){MechanismLinks.TargetRef old=MechanismLinks.TargetRef.read(ref);var next=byKey.get(old.key());return next==null?ref.copy():next.write();}
    /** Keep independent event/command records and counters, even on one target. */
    private static void remapRecords(NbtList entries,String field,boolean tool,Map<String,MechanismLinks.TargetRef> byKey){
        Set<String> primaryKeys=new HashSet<>(),used=new HashSet<>();
        for(NbtElement raw:entries){NbtCompound entry=(NbtCompound)raw;String old=key(entry.getCompound(field),tool);var next=byKey.get(old);if(!entry.contains("AssemblyStorageKey",8)&&(next==null||next.key().equals(old)))primaryKeys.add(old);}
        for(NbtElement raw:entries){NbtCompound entry=(NbtCompound)raw;String old=key(entry.getCompound(field),tool);var next=byKey.get(old);String target=next==null?old:next.key();
            String storage=entry.contains("AssemblyStorageKey",8)?entry.getString("AssemblyStorageKey"):target;
            if(!storage.startsWith("assembly:")&&(!old.equals(target)&&primaryKeys.contains(target)||used.contains(storage)))storage="assembly:"+old;
            if(!used.add(storage))throw new IllegalStateException("assembly_duplicate_reference_storage_key");
            if(next!=null)entry.put(field,tool?ToolTarget.mechanism(next).write():next.write());
            if(storage.equals(target))entry.remove("AssemblyStorageKey");else entry.putString("AssemblyStorageKey",storage);
        }
    }
    private static String key(NbtCompound ref,boolean tool){return tool?ToolTarget.read(ref).key():MechanismLinks.TargetRef.read(ref).key();}
    private static State snapshot(MinecraftServer server){return new State(MechanismState.get(server).writeNbt(new NbtCompound()),MechanismRules.migrationSnapshot(server),MechanismGroups.get(server).writeNbt(new NbtCompound()),ObjectPolicies.get(server).writeNbt(new NbtCompound()),ToolCommands.get(server).writeNbt(new NbtCompound()),ObjectPolicies.migrationContext(server));}
    private static boolean same(State a,State b){return a.links.equals(b.links)&&a.rules.equals(b.rules)&&a.groups.equals(b.groups)&&a.policies.equals(b.policies)&&a.commands.equals(b.commands)&&a.context.equals(b.context);}
    private static void restore(MinecraftServer server,State value){
        var state=MechanismState.get(server);var restored=MechanismState.fromNbt(value.links);state.levers.clear();state.levers.putAll(restored.levers);state.targets.clear();state.targets.putAll(restored.targets);state.removed.clear();state.removed.addAll(restored.removed);state.revision=restored.revision;state.markDirty();
        MechanismRules.restoreMigration(server,value.rules);MechanismGroups.restoreMigration(server,value.groups);ObjectPolicies.restoreMigration(server,value.policies);ObjectPolicies.migrationContext(server,value.context);ToolCommands.restoreAssemblyMigration(server,value.commands);
    }
}
