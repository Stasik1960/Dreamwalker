package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

/** Stable creation order, shared persistent source flags, and ordered per-target delivery. */
public final class MechanismRules extends PersistentState {
    public enum Condition { ANY, ALL }
    public enum Effect { TOGGLE, OPEN, CLOSE, PULSE }
    public static final int SCHEMA=2,LIMIT=4096,MEMBERS=64;
    public static final class Rule {
        public final UUID id;public final long order;public String name;public Condition condition=Condition.ANY;public Effect effect=Effect.TOGGLE;
        public final LinkedHashMap<UUID,MechanismLinks.TargetRef> sources=new LinkedHashMap<>();
        public final LinkedHashMap<String,MechanismLinks.TargetRef> targets=new LinkedHashMap<>();
        public boolean satisfied,incomplete,simple;
        Rule(UUID id,long order,String name){this.id=id;this.order=order;this.name=name;}
    }
    private record Pending(long number,MechanismLinks.TargetRef target,Effect effect,ObjectPolicies.Initiator initiator,UUID rule){}
    private final LinkedHashMap<UUID,Rule> rules=new LinkedHashMap<>();
    private final Map<UUID,Boolean> flags=new LinkedHashMap<>();
    private final Map<UUID,ObjectPolicies.Initiator> initiators=new LinkedHashMap<>();
    private final Set<UUID> imported=new LinkedHashSet<>();
    private final Set<String> importedPending=new LinkedHashSet<>();
    private final ArrayDeque<Pending> queue=new ArrayDeque<>();
    private long sequence,creation;private boolean delivering;
    private static final Map<MinecraftServer,MechanismRules> OBSERVED=Collections.synchronizedMap(new WeakHashMap<>());
    public static Map<String,Object> metrics(MinecraftServer server){var s=OBSERVED.get(server);if(s==null)return Map.of("status","NOT_MEASURED_RULES_NOT_LOADED");return Map.of("rules",s.rules.size(),"persistentSourceFlags",s.flags.size(),"queuedEffects",s.queue.size(),"queueMaximum",LIMIT,"incompleteRules",s.rules.values().stream().filter(r->r.incomplete).count());}
    public static MechanismRules get(MinecraftServer server){var s=server.getOverworld().getPersistentStateManager().getOrCreate(MechanismRules::fromNbt,MechanismRules::new,"bloodborne_dw_mechanism_rules");s.importLegacy(MechanismState.get(server));OBSERVED.put(server,s);return s;}
    private void importLegacy(MechanismState old){
        for(var lever:old.levers.values())if(!imported.contains(lever.id)&&rules.size()<LIMIT){imported.add(lever.id);if(!lever.targets.isEmpty()){Rule r=new Rule(lever.id,++creation,"Связи V9");r.simple=true;r.sources.put(lever.id,new MechanismLinks.TargetRef(MechanismLinks.Kind.RP,lever.dimension,lever.id,lever.pos,lever.asset));for(String key:lever.targets){var t=old.targets.get(key);if(t!=null)r.targets.put(key,t.ref);}rules.put(r.id,r);flags.putIfAbsent(lever.id,false);}markDirty();}
        for(var target:old.targets.values())if(target.pending&&importedPending.add(target.ref.key())){int count=target.pulseOnly?(int)Math.min(target.pendingPulses,MEMBERS):1;for(int i=0;i<count;i++)enqueue(target.ref,target.pulseOnly?Effect.PULSE:target.unresolvedFlip?Effect.TOGGLE:target.desired?Effect.OPEN:Effect.CLOSE,null,new UUID(0,0));target.pending=false;target.pendingPulses=0;old.markDirty();markDirty();}
    }
    public static void noteInitiator(RpObjectEntity source,ServerPlayerEntity player){var s=get(player.getServer());if((s.initiators.size()<LIMIT||s.initiators.containsKey(source.getUuid()))&&(s.flags.size()<LIMIT||s.flags.containsKey(source.getUuid()))){s.flags.putIfAbsent(source.getUuid(),false);s.initiators.put(source.getUuid(),new ObjectPolicies.Initiator(player.getUuid(),player.getGameProfile().getName()));s.markDirty();}}
    public static void legacyLinked(RpObjectEntity source,MechanismLinks.TargetRef ref){var s=get(((ServerWorld)source.getWorld()).getServer());Rule r=s.rules.get(source.getUuid());if(r==null&&s.rules.size()<LIMIT){r=new Rule(source.getUuid(),++s.creation,"Простая связь");r.simple=true;r.sources.put(source.getUuid(),MechanismLinks.TargetRef.rp(source));s.rules.put(r.id,r);}if(r!=null&&r.targets.size()<MEMBERS)r.targets.put(ref.key(),ref);s.flags.putIfAbsent(source.getUuid(),false);s.imported.add(source.getUuid());s.markDirty();}
    public static void legacyUnlinked(RpObjectEntity source,String key){var s=get(((ServerWorld)source.getWorld()).getServer());Rule r=s.rules.get(source.getUuid());if(r!=null){r.targets.remove(key);if(r.targets.isEmpty())s.rules.remove(r.id);s.markDirty();}}
    /** User edits are atomic. Temporary drafts live in the player's tool session, not this saved graph. */
    public record EditResult(boolean success,boolean changed,String reason,UUID rule){}
    public static EditResult createValid(ServerPlayerEntity player,String name,Condition condition,Effect effect,Collection<MechanismLinks.TargetRef> sources,Collection<MechanismLinks.TargetRef> targets){
        return saveValid(player,null,null,name,condition,effect,sources,targets);
    }
    public static EditResult saveValid(ServerPlayerEntity player,UUID id,String expectedFingerprint,String name,Condition condition,Effect effect,Collection<MechanismLinks.TargetRef> sources,Collection<MechanismLinks.TargetRef> targets){
        if(!builder(player))return refused("Нет прав на редактирование связей.");
        var s=get(player.getServer());Rule old=id==null?null:s.rules.get(id);
        if(id!=null&&(old==null||expectedFingerprint==null||!expectedFingerprint.equals(fingerprint(player.getServer(),id))))return refused("Правило изменено другим игроком или удалено. Перечитайте его перед сохранением.");
        if(condition==null||effect==null||sources==null||targets==null||sources.isEmpty()||targets.isEmpty())return refused("Для сохранения нужны хотя бы один рычаг и одна цель. Черновик не записан.");
        var src=new LinkedHashMap<UUID,MechanismLinks.TargetRef>();var dst=new LinkedHashMap<String,MechanismLinks.TargetRef>();
        for(var ref:sources){
            if(ref==null)return refused("Источник не выбран.");RpObjectEntity loaded=sourceEntity(player,ref);
            boolean unchanged=old!=null&&ref.equals(old.sources.get(ref.instance()));
            if(loaded==null&&!(unchanged&&unchangedSourceUnloaded(player,ref)))return refused("Новый источник должен быть загруженным рычагом с правами редактирования; выгруженные прежние участники сохраняются.");
            src.put(ref.instance(),loaded==null?ref:MechanismLinks.TargetRef.rp(loaded));
        }
        for(var ref:targets){
            if(ref==null)return refused("Цель не выбрана.");var status=MechanismLinks.inspect(player.getServer(),ref);
            boolean unchanged=old!=null&&ref.equals(old.targets.get(ref.key()));
            boolean unloaded=unchanged&&status.availability()==MechanismLinks.Availability.UNLOADED&&protectedRefAllowed(player,ref);
            if(!editableTarget(player,ref)&&!unloaded)return refused("Новая цель недоступна, не поддерживает связь или защищена; выгруженные прежние участники сохраняются.");
            boolean pulse=status.pulseOnly()||unloaded&&(old.effect==Effect.PULSE||ref.kind()==MechanismLinks.Kind.RP&&ref.registryId().equals("wood_gate"));
            if(effect==Effect.PULSE&&!pulse)return refused("Импульс допускается только для целей с однократной анимацией.");
            var w=world(player.getServer(),ref.dimension());var entity=ref.kind()==MechanismLinks.Kind.RP&&w!=null?w.getEntity(ref.instance()):null;
            dst.put(ref.key(),entity instanceof RpObjectEntity rp?MechanismLinks.TargetRef.rp(rp):ref);
        }
        if(src.size()>MEMBERS||dst.size()>MEMBERS)return refused("В правиле допускается не более 64 источников и 64 целей.");
        if(old==null&&s.rules.size()>=LIMIT)return refused("Достигнут предел сохранённых правил.");
        if(src.keySet().stream().anyMatch(k->!s.flags.containsKey(k))&&s.flags.size()+src.keySet().stream().filter(k->!s.flags.containsKey(k)).count()>LIMIT)return refused("Достигнут предел логических признаков источников.");
        String conflict=s.conflict(id,condition,effect,src.keySet(),dst.keySet());if(!conflict.isEmpty())return refused(conflict);
        Rule r=old==null?new Rule(UUID.randomUUID(),++s.creation,""):old;
        var oldSources=List.copyOf(r.sources.values());var oldTargetKeys=List.copyOf(r.targets.keySet());
        r.name=cleanName(name,r.order);r.condition=condition;r.effect=effect;r.sources.clear();r.sources.putAll(src);r.targets.clear();r.targets.putAll(dst);r.incomplete=false;r.simple=false;src.keySet().forEach(k->s.flags.putIfAbsent(k,false));r.satisfied=s.all(r);s.rules.put(r.id,r);for(var source:oldSources)for(String key:oldTargetKeys)if(!src.containsKey(source.instance())||!dst.containsKey(key))MechanismLinks.forgetLegacyPair(player.getServer(),source,key);s.queue.removeIf(p->p.rule().equals(r.id)&&!dst.containsKey(p.target().key()));s.markDirty();
        return new EditResult(true,true,"Правило сохранено. Игровых срабатываний не было.",r.id);
    }
    public static EditResult addValid(ServerPlayerEntity player,UUID id,MechanismLinks.TargetRef ref,boolean source,String expectedFingerprint){
        Rule r=rule(player.getServer(),id);if(r==null||ref==null)return refused("Правило или участник больше не существует.");
        var sources=new LinkedHashMap<>(r.sources);var targets=new LinkedHashMap<>(r.targets);if(source)sources.put(ref.instance(),ref);else targets.put(ref.key(),ref);
        return saveValid(player,id,expectedFingerprint,r.name,r.condition,r.effect,sources.values(),targets.values());
    }
    public static EditResult simpleConnect(ServerPlayerEntity player,MechanismLinks.TargetRef source,MechanismLinks.TargetRef target){
        if(!builder(player)||source==null||sourceEntity(player,source)==null)return refused("Выберите существующий загруженный рычаг с правами редактирования.");
        if(target==null||!editableTarget(player,target))return refused("Цель недоступна, защищена или не поддерживает открытие/импульс.");
        if(source.instance().equals(target.instance()))return refused("Рычаг нельзя связать с самим собой.");
        var s=get(player.getServer());
        for(Rule r:s.rules.values())if(r.sources.containsKey(source.instance())&&r.targets.containsKey(target.key()))return new EditResult(true,false,"Связь уже существует: «"+r.name+"» (порядок "+r.order+").",r.id);
        String conflict=s.conflict(null,Condition.ANY,Effect.TOGGLE,Set.of(source.instance()),Set.of(target.key()));if(!conflict.isEmpty())return refused(conflict);
        Rule r=s.rules.values().stream().filter(q->q.simple&&q.condition==Condition.ANY&&q.effect==Effect.TOGGLE&&q.sources.size()==1&&q.sources.containsKey(source.instance())).findFirst().orElse(null);
        if(r==null){if(s.rules.size()>=LIMIT)return refused("Достигнут предел сохранённых правил.");r=new Rule(UUID.randomUUID(),++s.creation,"Простые связи рычага");r.simple=true;r.sources.put(source.instance(),source);}
        if(r.targets.size()>=MEMBERS)return refused("Достигнут предел 64 целей этого рычага.");
        if(!s.flags.containsKey(source.instance())&&s.flags.size()>=LIMIT)return refused("Достигнут предел логических признаков источников.");
        r.targets.put(target.key(),target);s.rules.put(r.id,r);s.flags.putIfAbsent(source.instance(),false);s.markDirty();
        return new EditResult(true,true,MechanismLinks.inspect(player.getServer(),target).pulseOnly()?"Связь сохранена: рычаг → один импульс после 70 тиков.":"Связь сохранена: рычаг → открывать/закрывать после 70 тиков.",r.id);
    }
    public static EditResult simpleUnlink(ServerPlayerEntity player,MechanismLinks.TargetRef source,MechanismLinks.TargetRef target){
        if(!builder(player)||source==null||sourceEntity(player,source)==null||target==null)return refused("Выберите существующий рычаг и конкретную цель связи.");
        var s=get(player.getServer());List<Rule> pairs=s.rules.values().stream().filter(r->r.sources.containsKey(source.instance())&&r.targets.containsKey(target.key())).toList();
        List<Rule> simple=pairs.stream().filter(r->r.simple&&r.condition==Condition.ANY&&r.effect==Effect.TOGGLE&&r.sources.size()==1).toList();
        if(simple.isEmpty())return refused(pairs.isEmpty()?"У выбранной пары нет простой связи.":"Пара входит в групповое правило. Удалите участника в меню: это затронет все источники группы.");
        for(Rule r:simple){r.targets.remove(target.key());s.queue.removeIf(p->p.rule().equals(r.id)&&p.target().key().equals(target.key()));if(r.targets.isEmpty())s.rules.remove(r.id);}
        MechanismLinks.forgetLegacyPair(player.getServer(),source,target.key());s.markDirty();
        return new EditResult(true,true,"Удалена только простая связь выбранной пары; остальные цели и правила сохранены.",simple.get(0).id);
    }
    private String conflict(UUID except,Condition condition,Effect effect,Set<UUID> sources,Set<String> targets){
        var conflicts=rules.values().stream().filter(r->!r.id.equals(except)&&r.targets.keySet().stream().anyMatch(targets::contains)&&(r.condition!=condition||r.effect!=effect||r.sources.keySet().stream().anyMatch(sources::contains))).toList();
        if(conflicts.isEmpty())return "";
        return "Конфликт связей: "+conflicts.stream().limit(5).map(r->"«"+r.name+"» ["+r.order+", "+r.condition+"/"+r.effect+"]").reduce((a,b)->a+", "+b).orElse("")+". Действия исполняются по этому порядку; старые правила сохранены. Измените состав или действие в меню.";
    }
    private static RpObjectEntity sourceEntity(ServerPlayerEntity player,MechanismLinks.TargetRef ref){
        if(ref.kind()!=MechanismLinks.Kind.RP)return null;var w=world(player.getServer(),ref.dimension());var e=w==null?null:w.getEntity(ref.instance());
        return e instanceof RpObjectEntity rp&&!rp.isRemoved()&&rp.isMechanism()&&rp.assetId().equals(ref.registryId())&&dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(w,player,rp.getBlockPos())?rp:null;
    }
    private static boolean protectedRefAllowed(ServerPlayerEntity player,MechanismLinks.TargetRef ref){var w=world(player.getServer(),ref.dimension());return w!=null&&dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(w,player,net.minecraft.util.math.BlockPos.fromLong(ref.root()));}
    private static boolean unchangedSourceUnloaded(ServerPlayerEntity player,MechanismLinks.TargetRef ref){var w=world(player.getServer(),ref.dimension());if(w==null||ref.kind()!=MechanismLinks.Kind.RP||!Set.of("lever_1","lever_2").contains(ref.registryId())||!protectedRefAllowed(player,ref))return false;var e=w.getEntity(ref.instance());return e==null||e.isRemoved()&&(e.getRemovalReason()==null||!e.getRemovalReason().shouldDestroy());}
    private static boolean editableTarget(ServerPlayerEntity player,MechanismLinks.TargetRef ref){var w=world(player.getServer(),ref.dimension());if(w==null||MechanismLinks.inspect(player.getServer(),ref).availability()!=MechanismLinks.Availability.LOADED)return false;var e=ref.kind()==MechanismLinks.Kind.RP?w.getEntity(ref.instance()):null;var pos=e==null?net.minecraft.util.math.BlockPos.fromLong(ref.root()):e.getBlockPos();return dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(w,player,pos);}
    private static String cleanName(String name,long order){return name==null||name.isBlank()?"Правило "+order:name.substring(0,Math.min(80,name.length()));}
    private static EditResult refused(String reason){return new EditResult(false,false,reason,null);}
    /** Configuration digest excludes source flags and animation state, which are not form edits. */
    public static String fingerprint(MinecraftServer server,UUID id){Rule r=rule(server,id);if(r==null)return "missing";var n=new NbtCompound();n.putString("Name",r.name);n.putString("Condition",r.condition.name());n.putString("Effect",r.effect.name());n.putBoolean("Incomplete",r.incomplete);NbtList a=new NbtList(),b=new NbtList();r.sources.values().forEach(v->a.add(v.write()));r.targets.values().forEach(v->b.add(v.write()));n.put("Sources",a);n.put("Targets",b);return digest(n.toString());}
    static String digest(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    public static Rule create(ServerPlayerEntity player,String name){var s=get(player.getServer());if(s.rules.size()>=LIMIT)return null;Rule r=new Rule(UUID.randomUUID(),++s.creation,name.isBlank()?"Правило "+s.creation:name.substring(0,Math.min(80,name.length())));s.rules.put(r.id,r);s.markDirty();return r;}
    public static List<Rule> list(MinecraftServer server){return List.copyOf(get(server).rules.values());}
    public static Rule rule(MinecraftServer server,UUID id){return get(server).rules.get(id);}
    public static boolean flag(MinecraftServer server,UUID id){return get(server).flags.getOrDefault(id,false);}
    public static boolean configure(ServerPlayerEntity player,UUID id,String name,Condition condition,Effect effect){var s=get(player.getServer());Rule r=s.rules.get(id);if(r==null||!builder(player)||r.incomplete&&!player.hasPermissionLevel(2))return false;if(effect==Effect.PULSE&&r.targets.values().stream().anyMatch(t->!MechanismLinks.inspect(player.getServer(),t).pulseOnly()))return false;r.name=name.substring(0,Math.min(80,name.length()));r.condition=condition;r.effect=effect;r.simple=r.simple&&condition==Condition.ANY&&effect==Effect.TOGGLE&&r.sources.size()==1;r.satisfied=s.all(r);s.markDirty();return true;}
    public static boolean add(ServerPlayerEntity player,UUID id,MechanismLinks.TargetRef ref,boolean source){var s=get(player.getServer());Rule r=s.rules.get(id);if(r==null||!builder(player)||r.incomplete&&!player.hasPermissionLevel(2))return false;if(source&&!s.flags.containsKey(ref.instance())&&s.flags.size()>=LIMIT)return false;var status=MechanismLinks.inspect(player.getServer(),ref);if(source){var world=world(player.getServer(),ref.dimension());if(world==null||!(world.getEntity(ref.instance()) instanceof RpObjectEntity rp)||!rp.isMechanism()||r.sources.size()>=MEMBERS)return false;r.sources.put(ref.instance(),ref);if(r.sources.size()>1)r.simple=false;s.flags.putIfAbsent(ref.instance(),false);}else{if(status.availability()!=MechanismLinks.Availability.LOADED||r.targets.size()>=MEMBERS)return false;if(!status.pulseOnly()&&r.effect==Effect.PULSE)return false;r.targets.put(ref.key(),ref);}r.incomplete=false;r.satisfied=s.all(r);s.markDirty();ObjectPolicies.event(player.getServerWorld(),ref,"rule_member_add",Map.of(),Map.of("rule",id.toString(),"source",source),"COMMITTED","");return true;}
    public static boolean removeMember(ServerPlayerEntity player,UUID id,String key,boolean source){
        var s=get(player.getServer());Rule r=s.rules.get(id);if(r==null||!builder(player)||r.incomplete&&!player.hasPermissionLevel(2))return false;
        var oldSources=List.copyOf(r.sources.values());var oldTargets=List.copyOf(r.targets.values());boolean changed;
        if(source)changed=r.sources.entrySet().removeIf(e->e.getValue().key().equals(key));else changed=r.targets.remove(key)!=null;
        if(!changed)return false;
        for(var a:oldSources)for(var b:oldTargets)if(!r.sources.containsKey(a.instance())||!r.targets.containsKey(b.key()))MechanismLinks.forgetLegacyPair(player.getServer(),a,b.key());
        s.queue.removeIf(p->p.rule().equals(id)&&(r.sources.isEmpty()||!r.targets.containsKey(p.target().key())));
        if(r.sources.isEmpty()||r.targets.isEmpty())s.rules.remove(id);else r.satisfied=s.all(r);s.markDirty();return true;
    }
    public static boolean delete(ServerPlayerEntity player,UUID id){var s=get(player.getServer());if(!builder(player))return false;Rule r=s.rules.remove(id);if(r==null)return false;for(var source:r.sources.values())for(String key:List.copyOf(r.targets.keySet()))MechanismLinks.forgetLegacyPair(player.getServer(),source,key);s.queue.removeIf(p->p.rule().equals(id));s.markDirty();return true;}
    public static List<UUID> affected(MinecraftServer server,UUID rule){var s=get(server);Rule r=s.rules.get(rule);return r==null?List.of():s.rules.values().stream().filter(q->q.condition==Condition.ALL&&q.sources.keySet().stream().anyMatch(r.sources::containsKey)).map(q->q.id).toList();}
    public static boolean reset(ServerPlayerEntity player,UUID id){var s=get(player.getServer());Rule r=s.rules.get(id);if(!builder(player)||r==null)return false;for(UUID source:r.sources.keySet())s.flags.put(source,false);var initiator=new ObjectPolicies.Initiator(player.getUuid(),player.getGameProfile().getName());for(Rule q:s.rules.values())if(q.condition==Condition.ALL&&q.sources.keySet().stream().anyMatch(r.sources::containsKey))s.evaluate(q,initiator);s.markDirty();retry(player.getServer(),256);return true;}
    public static void pulse(RpObjectEntity source){if(!(source.getWorld() instanceof ServerWorld world))return;var s=get(world.getServer());UUID id=source.getUuid();if(s.flags.size()<LIMIT||s.flags.containsKey(id))s.flags.put(id,!s.flags.getOrDefault(id,false));ObjectPolicies.Initiator initiator=s.initiators.remove(id);
        for(Rule r:s.rules.values())if(r.sources.containsKey(id)){if(r.incomplete||r.targets.isEmpty())continue;if(r.condition==Condition.ANY){for(var ref:r.targets.values())s.enqueue(ref,r.effect,initiator,r.id);}else s.evaluate(r,initiator);}s.markDirty();retry(world.getServer(),256);
        ObjectPolicies.event(world,MechanismLinks.TargetRef.rp(source),"lever_logical_activation",Map.of(),Map.of("active",s.flags.getOrDefault(id,false),"pending",s.queue.size()),"COMMITTED","70-tick source procedure delivered once");}
    private boolean all(Rule r){return !r.incomplete&&!r.sources.isEmpty()&&r.sources.keySet().stream().allMatch(id->flags.getOrDefault(id,false));}
    private void evaluate(Rule r,ObjectPolicies.Initiator initiator){boolean value=all(r);if(value==r.satisfied)return;r.satisfied=value;if(r.incomplete)return;Effect action=r.effect==Effect.TOGGLE?(value?Effect.OPEN:Effect.CLOSE):r.effect;if(value||r.effect==Effect.TOGGLE)for(var ref:r.targets.values()){boolean pulse=MechanismLinks.Kind.RP==ref.kind()&&ref.registryId().equals("wood_gate");if(pulse&&!value)continue;enqueue(ref,pulse?Effect.PULSE:action,initiator,r.id);}}
    private void enqueue(MechanismLinks.TargetRef target,Effect effect,ObjectPolicies.Initiator initiator,UUID rule){if(queue.size()>=LIMIT){DwDiagnostics.error(null,ObjectPolicies.type(target),target.instance().toString(),net.minecraft.util.math.BlockPos.fromLong(target.root()),"RULE_QUEUE_FULL","4096 pending effects; new operation refused",null);return;}if(target.kind()==MechanismLinks.Kind.RP&&target.registryId().equals("wood_gate"))effect=Effect.PULSE;queue.addLast(new Pending(++sequence,target,effect,initiator,rule));markDirty();}
    public static int retry(MinecraftServer server,int limit){var s=get(server);if(s.delivering||limit<=0)return 0;s.delivering=true;Set<String> blocked=new HashSet<>();int count=0;
        try{for(Pending p:List.copyOf(s.queue)){if(count>=limit)break;if(!s.queue.contains(p)||blocked.contains(p.target().key()))continue;count++;var status=MechanismLinks.inspect(server,p.target());if(status.availability()==MechanismLinks.Availability.STALE){s.queue.remove(p);s.markDirty();continue;}if(status.availability()!=MechanismLinks.Availability.LOADED||status.locked()){blocked.add(p.target().key());continue;}ServerWorld world=world(server,p.target().dimension());boolean open=p.effect()==Effect.OPEN||p.effect()==Effect.TOGGLE&&!status.open();ObjectPolicies.context(world,p.target(),p.initiator());boolean accepted=false;
            try{accepted=MechanismLinks.applyEffect(server,p.target(),open,p.effect()==Effect.PULSE);}catch(RuntimeException failure){DwDiagnostics.error(world,ObjectPolicies.type(p.target()),p.target().instance().toString(),net.minecraft.util.math.BlockPos.fromLong(p.target().root()),"RULE_EFFECT_FAILED","Actual target effect failed; pending operation retained",failure);}finally{ObjectPolicies.context(world,p.target(),null);}
            ObjectPolicies.event(world,p.target(),"rule_effect",Map.of("open",status.open()),Map.of("operation",p.number(),"rule",p.rule().toString(),"effect",p.effect().name()),accepted?"COMMITTED":"DEFERRED",accepted?"Actual state accepted":"Awaiting target, including living-safe close");
            if(accepted){s.queue.remove(p);s.markDirty();}else blocked.add(p.target().key());}
        }finally{s.delivering=false;}return count;
    }
    public static boolean pending(MinecraftServer server,MechanismLinks.TargetRef ref){return get(server).queue.stream().anyMatch(p->p.target().key().equals(ref.key()));}
    public static void removed(MinecraftServer server,MechanismLinks.TargetRef ref){var s=get(server);for(Rule r:s.rules.values()){boolean source=r.sources.remove(ref.instance())!=null;boolean target=r.targets.remove(ref.key())!=null;if(source&&r.condition==Condition.ALL)r.incomplete=true;if(target&&r.condition==Condition.ALL)r.incomplete=true;if(source||target)r.satisfied=s.all(r);}Set<UUID> pruned=new HashSet<>();s.rules.values().removeIf(r->{boolean remove=r.condition==Condition.ANY&&(r.sources.isEmpty()||r.targets.isEmpty());if(remove)pruned.add(r.id);return remove;});s.queue.removeIf(p->p.target().key().equals(ref.key())||pruned.contains(p.rule()));s.flags.remove(ref.instance());s.initiators.remove(ref.instance());s.markDirty();ObjectPolicies.removed(server,ref);}
    public static Map<String,Object> view(MinecraftServer server,Rule r){return view(server,r,0);}
    public static Map<String,Object> view(MinecraftServer server,Rule r,int affectedPage){var s=get(server);Map<String,Object> out=new LinkedHashMap<>();out.put("id",r.id.toString());out.put("fingerprint",fingerprint(server,r.id));out.put("simple",r.simple);out.put("order",r.order);out.put("name",r.name);out.put("condition",r.condition.name());out.put("effect",r.effect.name());out.put("incomplete",r.incomplete);out.put("satisfied",r.satisfied);out.put("active",r.sources.keySet().stream().filter(i->s.flags.getOrDefault(i,false)).count());out.put("total",r.sources.size());out.put("sources",r.sources.values().stream().map(t->member(server,t,true)).toList());out.put("targets",r.targets.values().stream().map(t->member(server,t,false)).toList());var related=affected(server,r.id);int pages=Math.max(1,(related.size()+63)/64),page=Math.max(0,Math.min(pages-1,affectedPage));var displayed=related.stream().skip(page*64L).limit(64).toList();out.put("affectedReset",displayed.stream().map(UUID::toString).toList());out.put("affectedResetNames",displayed.stream().map(id->s.rules.get(id).name).toList());out.put("affectedResetTotal",related.size());out.put("affectedResetPage",page);out.put("affectedResetPages",pages);return out;}
    private static Map<String,Object> member(MinecraftServer server,MechanismLinks.TargetRef ref,boolean source){var status=MechanismLinks.inspect(server,ref);if(source){var w=world(server,ref.dimension());var e=w==null?null:w.getEntity(ref.instance());status=new MechanismLinks.Status(e instanceof RpObjectEntity rp&&rp.isMechanism()?MechanismLinks.Availability.LOADED:MechanismLinks.Availability.UNLOADED,false,false);}var id=new Identifier(ref.kind()==MechanismLinks.Kind.RP?"bloodborne_rp:"+ref.registryId():ref.registryId());var entry=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(id);var out=new LinkedHashMap<String,Object>();out.put("name",entry==null?ref.registryId():entry.name());out.put("key",ref.key());out.put("instance",ref.instance().toString());out.put("type",ObjectPolicies.type(ref));out.put("registry",ref.registryId());out.put("position",net.minecraft.util.math.BlockPos.fromLong(ref.root()).toShortString());out.put("active",source&&flag(server,ref.instance()));out.put("availability",status.availability().name());out.put("pending",pending(server,ref));out.put("pulseOnly",status.pulseOnly());out.put("supportedAction",status.pulseOnly()?"Одиночная анимация":"Открыть / закрыть");out.put("conflictingRules",list(server).stream().filter(r->r.targets.containsKey(ref.key())).count());return out;}
    private static boolean builder(ServerPlayerEntity p){return dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(p);}
    private static ServerWorld world(MinecraftServer server,String dimension){return server.getWorld(RegistryKey.of(RegistryKeys.WORLD,new Identifier(dimension)));}
    public static MechanismRules fromNbt(NbtCompound n){MechanismRules s=new MechanismRules();if(n.getInt("Schema")!=1&&n.getInt("Schema")!=SCHEMA){DwDiagnostics.error(null,"UNASSIGNED","mechanism_rules",null,"RULE_SCHEMA","Unsupported saved rule schema; expected1or2",null);return s;}s.sequence=Math.max(0,n.getLong("Sequence"));s.creation=Math.max(0,n.getLong("Creation"));try{for(NbtElement raw:n.getList("Imported",11)){if(s.imported.size()>=LIMIT)break;var tag=new NbtCompound();tag.put("Id",raw);s.imported.add(tag.getUuid("Id"));}for(NbtElement raw:n.getList("Flags",10)){if(s.flags.size()>=LIMIT)break;var t=(NbtCompound)raw;s.flags.put(t.getUuid("Id"),t.getBoolean("Active"));if(t.contains("Initiator",10)){var i=ObjectPolicies.Initiator.read(t.getCompound("Initiator"));if(i!=null)s.initiators.put(t.getUuid("Id"),i);}}
            for(NbtElement raw:n.getList("Rules",10)){if(s.rules.size()>=LIMIT)break;var t=(NbtCompound)raw;Rule r=new Rule(t.getUuid("Id"),t.getLong("Order"),t.getString("Name"));r.condition=Condition.valueOf(t.getString("Condition"));r.effect=Effect.valueOf(t.getString("Effect"));r.satisfied=t.getBoolean("Satisfied");r.incomplete=t.getBoolean("Incomplete");r.simple=t.contains("Simple")?t.getBoolean("Simple"):r.id.equals(t.getList("Sources",10).isEmpty()?new UUID(0,0):t.getList("Sources",10).getCompound(0).getUuid("Instance"))&&r.condition==Condition.ANY&&r.effect==Effect.TOGGLE;for(NbtElement v:t.getList("Sources",10)){if(r.sources.size()>=MEMBERS)break;var ref=MechanismLinks.TargetRef.read((NbtCompound)v);r.sources.put(ref.instance(),ref);}for(NbtElement v:t.getList("Targets",10)){if(r.targets.size()>=MEMBERS)break;var ref=MechanismLinks.TargetRef.read((NbtCompound)v);r.targets.put(ref.key(),ref);}s.rules.put(r.id,r);}
            for(NbtElement raw:n.getList("Queue",10)){if(s.queue.size()>=LIMIT)break;var t=(NbtCompound)raw;s.queue.add(new Pending(t.getLong("Number"),MechanismLinks.TargetRef.read(t.getCompound("Target")),Effect.valueOf(t.getString("Effect")),ObjectPolicies.Initiator.read(t.getCompound("Initiator")),t.getUuid("Rule")));}
        }catch(RuntimeException failure){DwDiagnostics.error(null,"UNASSIGNED","mechanism_rules",null,"RULE_DATA","Malformed saved rule; valid preceding entries retained",failure);}var sorted=s.rules.values().stream().sorted(Comparator.comparingLong(r->r.order)).toList();s.rules.clear();sorted.forEach(r->s.rules.put(r.id,r));return s;}
    @Override public NbtCompound writeNbt(NbtCompound n){n.putInt("Schema",SCHEMA);n.putLong("Sequence",sequence);n.putLong("Creation",creation);NbtList a=new NbtList(),b=new NbtList(),c=new NbtList(),d=new NbtList();for(UUID id:imported){var t=new NbtCompound();t.putUuid("Id",id);a.add(t.get("Id"));}flags.forEach((id,value)->{var t=new NbtCompound();t.putUuid("Id",id);t.putBoolean("Active",value);if(initiators.containsKey(id))t.put("Initiator",initiators.get(id).write());b.add(t);});for(Rule r:rules.values()){var t=new NbtCompound();t.putUuid("Id",r.id);t.putLong("Order",r.order);t.putString("Name",r.name);t.putString("Condition",r.condition.name());t.putString("Effect",r.effect.name());t.putBoolean("Satisfied",r.satisfied);t.putBoolean("Incomplete",r.incomplete);t.putBoolean("Simple",r.simple);NbtList src=new NbtList(),dst=new NbtList();r.sources.values().forEach(v->src.add(v.write()));r.targets.values().forEach(v->dst.add(v.write()));t.put("Sources",src);t.put("Targets",dst);c.add(t);}for(Pending p:queue){var t=new NbtCompound();t.putLong("Number",p.number());t.put("Target",p.target().write());t.putString("Effect",p.effect().name());t.putUuid("Rule",p.rule());if(p.initiator()!=null)t.put("Initiator",p.initiator().write());d.add(t);}n.put("Imported",a);n.put("Flags",b);n.put("Rules",c);n.put("Queue",d);return n;}
}
