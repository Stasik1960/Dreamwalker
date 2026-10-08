package dev.dreamwalker.bloodbornedw.link;

import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;

/** Instance policies are independent of the RP locked flag and of type IDs. */
public final class ObjectPolicies extends PersistentState {
    public record Initiator(UUID uuid,String name) {
        public NbtCompound write(){var n=new NbtCompound();n.putUuid("UUID",uuid);n.putString("Name",name);return n;}
        public static Initiator read(NbtCompound n){return n.containsUuid("UUID")?new Initiator(n.getUuid("UUID"),n.getString("Name")):null;}
    }
    private static final int LIMIT=4096,COMMAND_LIMIT=32;
    private static final Map<MinecraftServer,Map<String,Initiator>> CONTEXT=new WeakHashMap<>();
    private static final Map<UUID,Long> NOTICES=new LinkedHashMap<>();
    private static final ThreadLocal<Set<String>> EXECUTING=ThreadLocal.withInitial(HashSet::new);
    private final Map<String,Policy> entries=new LinkedHashMap<>();
    private static final class Policy { MechanismLinks.TargetRef ref; boolean leversOnly; List<String> opened=List.of(),closed=List.of(); }
    public static ObjectPolicies get(MinecraftServer server){return server.getOverworld().getPersistentStateManager().getOrCreate(ObjectPolicies::read,ObjectPolicies::new,"bloodborne_dw_object_policies");}
    public static void noteInitiator(ServerWorld world,MechanismLinks.TargetRef ref,ServerPlayerEntity player){context(world,ref,new Initiator(player.getUuid(),player.getGameProfile().getName()));}
    public static void context(ServerWorld world,MechanismLinks.TargetRef ref,Initiator initiator){var map=CONTEXT.computeIfAbsent(world.getServer(),s->new HashMap<>());if(initiator==null)map.remove(ref.key());else if(map.size()<LIMIT||map.containsKey(ref.key()))map.put(ref.key(),initiator);}
    public static boolean leversOnly(MinecraftServer server,MechanismLinks.TargetRef ref){Policy p=get(server).entries.get(ref.key());return p!=null&&p.leversOnly;}
    public static boolean allowManual(ServerPlayerEntity player,MechanismLinks.TargetRef ref){
        if(!leversOnly(player.getServer(),ref)){noteInitiator(player.getServerWorld(),ref,player);return true;}
        long now=System.nanoTime(),last=NOTICES.getOrDefault(player.getUuid(),0L);if(now-last>1_000_000_000L){player.sendMessage(Text.literal("не поддаётся"),false);if(NOTICES.size()>=256)NOTICES.remove(NOTICES.keySet().iterator().next());NOTICES.put(player.getUuid(),now);}return false;
    }
    public static boolean setManual(ServerPlayerEntity player,MechanismLinks.TargetRef ref,boolean only){
        if(!dev.dreamwalker.bloodbornedw.architecture.BuildPermissions.canEdit(player)||MechanismLinks.inspect(player.getServer(),ref).availability()!=MechanismLinks.Availability.LOADED)return false;
        ObjectPolicies s=get(player.getServer());if(s.entries.size()>=LIMIT&&!s.entries.containsKey(ref.key()))return false;Policy p=s.entries.computeIfAbsent(ref.key(),k->new Policy());p.ref=ref;boolean before=p.leversOnly;p.leversOnly=only;s.markDirty();event(player.getServerWorld(),ref,"manual_control",Map.of("leversOnly",before),Map.of("leversOnly",only),"COMMITTED","");return true;
    }
    public static List<String> commands(MinecraftServer server,MechanismLinks.TargetRef ref,boolean open){Policy p=get(server).entries.get(ref.key());return p==null?List.of():open?p.opened:p.closed;}
    public static boolean setCommands(ServerPlayerEntity player,MechanismLinks.TargetRef ref,boolean open,List<String> commands){
        if(!player.hasPermissionLevel(4)||MechanismLinks.inspect(player.getServer(),ref).availability()!=MechanismLinks.Availability.LOADED||MechanismLinks.inspect(player.getServer(),ref).pulseOnly()||commands.size()>COMMAND_LIMIT||commands.stream().anyMatch(c->c.length()>2048||c.contains("\n")||c.contains("\r")))return false;
        ObjectPolicies s=get(player.getServer());if(s.entries.size()>=LIMIT&&!s.entries.containsKey(ref.key()))return false;Policy p=s.entries.computeIfAbsent(ref.key(),k->new Policy());p.ref=ref;if(open)p.opened=List.copyOf(commands);else p.closed=List.copyOf(commands);s.markDirty();event(player.getServerWorld(),ref,"event_commands_edit",Map.of(),Map.of("phase",open?"open":"close","count",commands.size()),"COMMITTED","");return true;
    }
    /** Called only at an actual successful transition, never at a request, load or sync. */
    public static void changed(ServerWorld world,MechanismLinks.TargetRef ref,boolean before,boolean after){
        if(before==after)return;var contexts=CONTEXT.get(world.getServer());Initiator initiator=contexts==null?null:contexts.remove(ref.key());
        Policy p=get(world.getServer()).entries.get(ref.key());if(p==null)return;List<String> commands=after?p.opened:p.closed;Set<String> executing=EXECUTING.get();if(!executing.isEmpty()){event(world,ref,"event_commands","REENTRANT_TRANSITION_SKIPPED");return;}executing.add(ref.key());
        try{Vec3d pos=Vec3d.ofBottomCenter(BlockPos.fromLong(ref.root()));if(ref.kind()==MechanismLinks.Kind.RP&&world.getEntity(ref.instance()) instanceof RpObjectEntity rp)pos=rp.getPos();
            else {var root=BlockPos.fromLong(ref.root());if(world.getBlockEntity(root) instanceof dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity be){var payload=be.payload();double y=payload.getDouble("MountY")+payload.getDouble(dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount.KEY);var state=world.getBlockState(root);if(state.getBlock() instanceof dev.dreamwalker.bloodbornedw.composite.CompositeRootBlock){var shift=dev.dreamwalker.bloodbornedw.composite.CompositeSourceShift.world(state,payload);pos=pos.add(shift.x(),shift.y()+y,shift.z());}else pos=pos.add(0,y,0);}}
            var source=world.getServer().getCommandSource().withWorld(world).withPosition(pos).withLevel(4).withSilent();
            for(String template:commands){if(template.isBlank())continue;if(template.contains("{player}")&&(initiator==null||world.getServer().getPlayerManager().getPlayer(initiator.uuid())==null)){event(world,ref,"event_command","PLAYER_UNAVAILABLE_SKIPPED");continue;}
                String command=template.replace("{id}",type(ref)).replace("{uuid}",ref.instance().toString()).replace("{x}",Double.toString(pos.x)).replace("{y}",Double.toString(pos.y)).replace("{z}",Double.toString(pos.z)).replace("{dimension}",ref.dimension()).replace("{player}",initiator==null?"":initiator.name());
                try{int result=world.getServer().getCommandManager().executeWithPrefix(source,command);event(world,ref,"event_command",Map.of("phase",after?"open":"close"),Map.of("result",result),result>0?"COMMITTED":"FAILED","Server console; initiating player="+(initiator==null?"none":initiator.name()));if(result<=0)DwDiagnostics.error(world,type(ref),ref.instance().toString(),BlockPos.fromLong(ref.root()),"EVENT_COMMAND_FAILED","Command returned no success: "+template,null);}
                catch(RuntimeException failure){DwDiagnostics.error(world,type(ref),ref.instance().toString(),BlockPos.fromLong(ref.root()),"EVENT_COMMAND_FAILED","After-state command failed; object transition remains committed",failure);}
            }
        }finally{executing.remove(ref.key());}
    }
    public static void removed(MinecraftServer server,MechanismLinks.TargetRef ref){ObjectPolicies s=get(server);if(s.entries.remove(ref.key())!=null)s.markDirty();var map=CONTEXT.get(server);if(map!=null)map.remove(ref.key());}
    static String type(MechanismLinks.TargetRef ref){Identifier id=Identifier.tryParse(ref.kind()==MechanismLinks.Kind.RP?"bloodborne_rp:"+ref.registryId():ref.registryId());var entry=id==null?null:DebugCatalogue.entry(id);return entry==null?"UNASSIGNED":entry.temporaryId();}
    static void event(ServerWorld world,MechanismLinks.TargetRef ref,String action,String reason){event(world,ref,action,Map.of(),Map.of(),"SKIPPED",reason);}
    static void event(ServerWorld world,MechanismLinks.TargetRef ref,String action,Map<String,Object> before,Map<String,Object> after,String result,String reason){DwDiagnostics.record(world,type(ref),ref.instance().toString(),BlockPos.fromLong(ref.root()),action,before,after,result,reason);}
    private static ObjectPolicies read(NbtCompound n){ObjectPolicies s=new ObjectPolicies();for(NbtElement raw:n.getList("Entries",10)){if(s.entries.size()>=LIMIT)break;try{NbtCompound tag=(NbtCompound)raw;String key=MechanismLinks.TargetRef.read(tag.getCompound("Ref")).key();Policy p=new Policy();p.leversOnly=tag.getBoolean("LeversOnly");p.opened=strings(tag,"Open");p.closed=strings(tag,"Close");s.entries.put(key,p);p.ref=MechanismLinks.TargetRef.read(tag.getCompound("Ref"));}catch(RuntimeException failure){DwDiagnostics.error(null,"UNASSIGNED","policies",null,"POLICY_DATA","Malformed saved instance policy skipped",failure);}}return s;}
    private static List<String> strings(NbtCompound n,String field){var list=new ArrayList<String>();for(NbtElement raw:n.getList(field,8)){if(list.size()>=COMMAND_LIMIT)break;String command=raw.asString();if(command.length()<=2048)list.add(command);}return List.copyOf(list);}
    @Override public NbtCompound writeNbt(NbtCompound n){NbtList out=new NbtList();entries.forEach((key,p)->{var ref=p.ref;if(ref==null)return;var tag=new NbtCompound();tag.put("Ref",ref.write());tag.putBoolean("LeversOnly",p.leversOnly);NbtList a=new NbtList(),b=new NbtList();p.opened.forEach(c->a.add(NbtString.of(c)));p.closed.forEach(c->b.add(NbtString.of(c)));tag.put("Open",a);tag.put("Close",b);out.add(tag);});n.put("Entries",out);return n;}
}
