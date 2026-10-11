package dev.dreamwalker.bloodbornedw.tool;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.PersistentState;

/** Main-hand right-click command batches, with durable per-player and shared use counters. */
public final class ToolCommands extends PersistentState {
    public static final int MAX_USES=999999,MAX_TEXT=8192,MAX_COMMANDS=32;
    public record View(String text,int personalLimit,int globalLimit,int globalUsed) {}
    private static final class Binding {
        ToolTarget target;String text;int personalLimit,globalLimit,globalUsed;
        final Map<UUID,Integer> personal=new LinkedHashMap<>();
    }
    private record LastUse(String key,int tick) {}
    private static final Map<MinecraftServer,Map<UUID,LastUse>> LAST=new WeakHashMap<>();
    private static final Map<MinecraftServer,ToolCommands> LOADED=new WeakHashMap<>();
    private static final ThreadLocal<Boolean> EXECUTING=ThreadLocal.withInitial(()->false);
    private final Map<String,Binding> entries=new LinkedHashMap<>();
    public static ToolCommands get(MinecraftServer server){var state=server.getOverworld().getPersistentStateManager().getOrCreate(ToolCommands::read,ToolCommands::new,"bloodborne_dw_tool_commands");LOADED.put(server,state);return state;}
    public static List<String> parse(String text){
        if(text==null||text.length()>MAX_TEXT||text.indexOf('\0')>=0)throw new IllegalArgumentException("Команды слишком длинные");
        var commands=text.lines().map(String::trim).filter(s->!s.isEmpty()).toList();
        if(commands.size()>MAX_COMMANDS||commands.stream().anyMatch(s->s.length()>2048))throw new IllegalArgumentException("Допускается до 32 команд, по одной в строке");
        return commands;
    }
    public static boolean save(ServerPlayerEntity player,ToolTarget target,String text,int personal,int global){
        if(!player.hasPermissionLevel(4)||!target.valid(player.getServer())||personal<1||personal>MAX_USES||global<1||global>MAX_USES)return false;
        var commands=parse(text);var state=get(player.getServer());
        if(commands.isEmpty()){state.entries.remove(target.key());state.markDirty();return true;}
        if(state.entries.size()>=4096&&!state.entries.containsKey(target.key()))return false;
        var binding=new Binding();binding.target=target;binding.text=String.join("\n",commands);binding.personalLimit=personal;binding.globalLimit=global;
        state.entries.put(target.key(),binding);state.markDirty();return true;
    }
    public static View view(MinecraftServer server,ToolTarget target){var b=get(server).entries.get(target.key());return b==null?new View("",MAX_USES,MAX_USES,0):new View(b.text,b.personalLimit,b.globalLimit,b.globalUsed);}
    public static boolean fire(ServerPlayerEntity player,ToolTarget target){
        if(target==null||EXECUTING.get())return false;var server=player.getServer();var state=get(server);var b=state.entries.get(target.key());
        if(b==null||!target.valid(server))return false;
        boolean sameRegistry=b.target.kind()==ToolTarget.Kind.ARCHITECTURE&&target.kind()==ToolTarget.Kind.ARCHITECTURE
                ?dev.dreamwalker.bloodbornedw.composite.ImportedTypeAliases.equivalent(b.target.registry(),target.registry())
                :b.target.registry().equals(target.registry());
        if(!sameRegistry||!b.target.valid(server)){state.entries.remove(target.key());state.markDirty();return false;}
        var last=LAST.computeIfAbsent(server,s->new HashMap<>());var previous=last.get(player.getUuid());
        if(previous!=null&&previous.tick==server.getTicks()&&previous.key.equals(target.key()))return false;
        last.put(player.getUuid(),new LastUse(target.key(),server.getTicks()));
        int used=b.personal.getOrDefault(player.getUuid(),0);
        if(b.globalUsed>=b.globalLimit||used>=b.personalLimit||b.personal.size()>=4096&&!b.personal.containsKey(player.getUuid()))return false;
        b.globalUsed++;b.personal.put(player.getUuid(),used+1);state.markDirty();
        var source=server.getCommandSource().withWorld(target.world(server)).withPosition(target.position(server)).withEntity(player).withLevel(4).withSilent();
        EXECUTING.set(true);
        try{int number=0;for(String command:parse(b.text)){number++;String value=command.replace("{player}",player.getGameProfile().getName()).replace("{uuid}",target.uuid()==null?"":target.uuid().toString());
            try{if(server.getCommandManager().executeWithPrefix(source,value)<=0)player.sendMessage(Text.literal("Команда №"+number+" не выполнена."),false);}
            catch(RuntimeException failure){player.sendMessage(Text.literal("Команда №"+number+" не выполнена."),false);dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("BB tool command execution failed at {}",target.key(),failure);}
        }}finally{EXECUTING.set(false);}return true;
    }
    public static void remove(MinecraftServer server,ToolTarget target){if(target==null)return;var state=get(server);if(state.entries.remove(target.key())!=null)state.markDirty();}
    public static void removedEntity(MinecraftServer server,UUID uuid){var state=get(server);if(state.entries.values().removeIf(b->uuid.equals(b.target.uuid())))state.markDirty();}
    public static void removedBlock(net.minecraft.server.world.ServerWorld world,net.minecraft.util.math.BlockPos pos){var state=get(world.getServer());String dim=world.getRegistryKey().getValue().toString();if(state.entries.values().removeIf(b->b.target.kind()==ToolTarget.Kind.BLOCK&&b.target.root()==pos.asLong()&&b.target.dimension().equals(dim)))state.markDirty();}
    public static void forgetPlayer(MinecraftServer server,UUID uuid){var map=LAST.get(server);if(map!=null)map.remove(uuid);}
    public static void stopped(MinecraftServer server){LAST.remove(server);LOADED.remove(server);}
    public static void blockChanged(net.minecraft.server.world.ServerWorld world,net.minecraft.util.math.BlockPos pos,net.minecraft.block.BlockState next){
        var state=LOADED.get(world.getServer());if(state==null||state.entries.isEmpty())return;
        String key=ToolTarget.Kind.BLOCK+"|"+world.getRegistryKey().getValue()+"|"+pos.asLong();var binding=state.entries.get(key);
        if(binding!=null&&!binding.target.registry().equals(net.minecraft.registry.Registries.BLOCK.getId(next.getBlock()).toString())){state.entries.remove(key);state.markDirty();}
    }
    static ToolCommands read(NbtCompound n){var state=new ToolCommands();for(NbtElement raw:n.getList("Entries",NbtElement.COMPOUND_TYPE)){if(state.entries.size()>=4096)break;try{
        var tag=(NbtCompound)raw;var b=new Binding();b.target=ToolTarget.read(tag.getCompound("Target"));b.text=String.join("\n",parse(tag.getString("Commands")));b.personalLimit=tag.getInt("PersonalLimit");b.globalLimit=tag.getInt("GlobalLimit");b.globalUsed=tag.getInt("GlobalUsed");
        if(b.personalLimit<1||b.personalLimit>MAX_USES||b.globalLimit<1||b.globalLimit>MAX_USES||b.globalUsed<0||b.globalUsed>b.globalLimit||b.text.isBlank())continue;
        for(NbtElement entry:tag.getList("Players",10)){if(b.personal.size()>=4096)break;var player=(NbtCompound)entry;if(player.containsUuid("UUID")){int used=player.getInt("Used");if(used>=0&&used<=b.personalLimit)b.personal.put(player.getUuid("UUID"),used);}}
        state.entries.put(b.target.key(),b);
    }catch(IllegalArgumentException invalid){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Ignoring invalid saved BB tool command binding",invalid);}}return state;}
    @Override public NbtCompound writeNbt(NbtCompound n){var entries=new NbtList();for(var b:this.entries.values()){var tag=new NbtCompound();tag.put("Target",b.target.write());tag.putString("Commands",b.text);tag.putInt("PersonalLimit",b.personalLimit);tag.putInt("GlobalLimit",b.globalLimit);tag.putInt("GlobalUsed",b.globalUsed);var players=new NbtList();b.personal.forEach((uuid,used)->{var player=new NbtCompound();player.putUuid("UUID",uuid);player.putInt("Used",used);players.add(player);});tag.put("Players",players);entries.add(tag);}n.put("Entries",entries);return n;}
}
