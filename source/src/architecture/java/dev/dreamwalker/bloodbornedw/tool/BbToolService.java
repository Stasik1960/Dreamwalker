package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import dev.dreamwalker.bloodbornerp.lamp.LampService;
import java.util.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/** Five server-authoritative actions; no undo, selection overlay or input interception. */
public final class BbToolService {
    public static final Identifier ID=new Identifier("bloodborne_dw","bb_tool");
    public static final Identifier OPEN=new Identifier("bloodborne_dw","tool_commands_open"),SAVE=new Identifier("bloodborne_dw","tool_commands_save");
    public static BbToolItem ITEM;
    private static final class Selection {final List<UUID> levers=new ArrayList<>();ToolTarget lamp;int radius;}
    private record Pending(UUID token,ToolTarget target,int expires) {}
    private static final Map<MinecraftServer,Map<UUID,Selection>> SELECTED=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Pending>> EDITS=new WeakHashMap<>();
    private BbToolService(){}
    public static boolean holding(PlayerEntity player){return player.getMainHandStack().getItem() instanceof BbToolItem;}
    public static boolean canEdit(PlayerEntity player){return BuildPermissions.canEdit(player);}
    private static Selection selection(ServerPlayerEntity player){return SELECTED.computeIfAbsent(player.getServer(),s->new HashMap<>()).computeIfAbsent(player.getUuid(),s->new Selection());}
    public static void clearSelection(ServerPlayerEntity player){var s=selection(player);s.levers.clear();s.lamp=null;}
    public static void initialize(){
        if(ITEM!=null)return;ITEM=Registry.register(Registries.ITEM,ID,new BbToolItem());BbAlternateService.initialize();
        // Register before composite callbacks: tool RMB always changes mode rather than using a target.
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(hand!=Hand.MAIN_HAND)return holding(player)?ActionResult.FAIL:ActionResult.PASS;
            if(holding(player)){if(player instanceof ServerPlayerEntity server)BbToolItem.cycle(server);return ActionResult.SUCCESS;}
            if(player instanceof ServerPlayerEntity server)ToolCommands.fire(server,ToolTarget.block(server.getServerWorld(),hit.getBlockPos(),player));
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(hand!=Hand.MAIN_HAND)return holding(player)?ActionResult.FAIL:ActionResult.PASS;
            if(holding(player)){if(player instanceof ServerPlayerEntity server)BbToolItem.cycle(server);return ActionResult.SUCCESS;}
            // RP interactAt returns PASS; its following position-less INTERACT is the real use.
            // Count there once even when the two packets land on different server ticks.
            if(player instanceof ServerPlayerEntity server&&(!(entity instanceof RpObjectEntity)&&!(entity instanceof dev.dreamwalker.bloodbornerp.mob.RpMobEntity)||hit==null))ToolCommands.fire(server,ToolTarget.entity(entity));
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->{
            if(!holding(player))return ActionResult.PASS;
            if(player instanceof ServerPlayerEntity server)apply(server,ToolTarget.block(server.getServerWorld(),pos,player));
            return ActionResult.SUCCESS;
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!holding(player))return ActionResult.PASS;
            if(player instanceof ServerPlayerEntity server)apply(server,ToolTarget.entity(entity));
            return ActionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,entity)->{if(world instanceof ServerWorld serverWorld)ToolCommands.removedBlock(serverWorld,pos);});
        ServerEntityEvents.ENTITY_UNLOAD.register((entity,world)->{if(entity.getRemovalReason()!=null&&entity.getRemovalReason().shouldDestroy())ToolCommands.removedEntity(world.getServer(),entity.getUuid());});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var select=SELECTED.get(server);if(select!=null)select.remove(handler.player.getUuid());var edits=EDITS.get(server);if(edits!=null)edits.remove(handler.player.getUuid());ToolCommands.forgetPlayer(server,handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STARTED.register(ToolCommands::get);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{SELECTED.remove(server);EDITS.remove(server);ToolCommands.stopped(server);});
        ServerPlayNetworking.registerGlobalReceiver(SAVE,(server,player,handler,buf,response)->{
            try{if(buf.readableBytes()>40000)return;UUID token=buf.readUuid();String text=buf.readString(ToolCommands.MAX_TEXT);int personal=buf.readVarInt(),global=buf.readVarInt();if(buf.isReadable())return;
                server.execute(()->save(player,token,text,personal,global));
            }catch(RuntimeException invalid){dev.dreamwalker.bloodbornedw.DreamwalkerBb.LOG.warn("Invalid BB tool command packet rejected");}
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("bb").requires(source->source.getEntity() instanceof ServerPlayerEntity player&&canEdit(player))
            .then(literal("select").then(argument("radius",IntegerArgumentType.integer(1,BbAlternateService.MAX_RADIUS)).executes(context->{var player=context.getSource().getPlayerOrThrow();selection(player).radius=IntegerArgumentType.getInteger(context,"radius");player.sendMessage(Text.literal("BB: замена вида в радиусе "+selection(player).radius+" блоков."),false);return 1;})))
            .then(literal("alt").then(argument("radius",IntegerArgumentType.integer(1,BbAlternateService.MAX_RADIUS)).executes(context->BbAlternateService.start(context.getSource().getPlayerOrThrow(),"alt",IntegerArgumentType.getInteger(context,"radius"))?1:0)))
            .then(literal("base").then(argument("radius",IntegerArgumentType.integer(1,BbAlternateService.MAX_RADIUS)).executes(context->BbAlternateService.start(context.getSource().getPlayerOrThrow(),"base",IntegerArgumentType.getInteger(context,"radius"))?1:0)))
            .then(literal("cancel").executes(context->{var player=context.getSource().getPlayerOrThrow();boolean cancelled=BbAlternateService.cancel(player.getServer());player.sendMessage(Text.literal(cancelled?"BB: замена вида остановлена; уже выполненные изменения сохранены.":"BB: активной замены вида нет."),false);return cancelled?1:0;}))
            .then(literal("status").executes(context->{var player=context.getSource().getPlayerOrThrow();var status=BbAlternateService.status(player.getServer());String phase=BbAlternateService.discovering(player.getServer())?"поиск сохранённых чанков":status.running()?"переключение профиля выполняется":"переключение профиля не выполняется";player.sendMessage(Text.literal("BB: "+phase+"; изменено "+status.changed()+", отклонено "+status.failed()+", чанки "+status.chunksDone()+" / "+status.chunksTotal()+"."),false);return 1;}))
            .then(literal("none").executes(context->{var player=context.getSource().getPlayerOrThrow();selection(player).radius=0;clearSelection(player);player.sendMessage(Text.literal("BB: выбор одного объекта."),false);return 1;}))));
    }
    public static void apply(ServerPlayerEntity player,ToolTarget target){
        if(!holding(player)||!canEdit(player)||target==null||!target.valid(player.getServer())||!target.dimension().equals(player.getServerWorld().getRegistryKey().getValue().toString()))return;
        if(!player.getServerWorld().canPlayerModifyAt(player,BlockPos.ofFloored(target.position(player.getServer()))))return;
        var mode=BbToolItem.mode(player.getMainHandStack());boolean success=switch(mode){
            case UP->move(player,target,1);case DOWN->move(player,target,-1);case SPECIAL->special(player,target);case COMMANDS->openCommands(player,target);case CLEAR->clear(player,target);
        };
        if(mode==BbToolItem.Mode.UP||mode==BbToolItem.Mode.DOWN)player.sendMessage(Text.literal(success?"Объект перемещён на "+(mode==BbToolItem.Mode.UP?"+1":"−1")+" блок.":"Не удалось переместить объект."),true);
    }
    public static boolean move(ServerPlayerEntity player,ToolTarget target,int delta){
        if(!canEdit(player)||Math.abs(delta)!=1||!target.valid(player.getServer()))return false;
        if(target.kind()==ToolTarget.Kind.RP&&target.entity(player.getServer()) instanceof RpObjectEntity rp)return rp.setVerticalOffset(rp.verticalOffset()+delta,player);
        if(target.kind()==ToolTarget.Kind.ARCHITECTURE){var world=target.world(player.getServer());var root=BlockPos.fromLong(target.root());return VerticalMount.setOffset(world,root,VerticalMount.offset(world,root)+delta,player);}
        if(target.kind()==ToolTarget.Kind.BLOCK){
            var world=target.world(player.getServer());var root=BlockPos.fromLong(target.root());var state=world.getBlockState(root);
            if(ImportedCompactBlock.compact(state)){
                if(!world.isInBuildLimit(root.up(delta)))return false;
                NbtCompound commands=ToolCommands.get(player.getServer()).writeNbt(new NbtCompound());
                var owner=ImportedCompactBlock.materialize(world,root,player);if(owner==null)return false;
                var own=new ToolTarget(ToolTarget.Kind.ARCHITECTURE,target.dimension(),owner.instanceId(),root.asLong(),owner.registryId());
                ToolCommands.restoreBlockRetarget(player.getServer(),commands,target,own);
                return VerticalMount.setOffset(world,root,VerticalMount.offset(world,root)+delta,player);
            }
            return UtilityToolMoves.move(player,target,delta);
        }
        if(target.kind()==ToolTarget.Kind.ENTITY){var entity=target.entity(player.getServer());var next=entity.getBoundingBox().offset(0,delta,0);if(dev.dreamwalker.bloodbornerp.object.RpPlacementRules.bounds(entity.getWorld(),next)!=null)return false;entity.refreshPositionAfterTeleport(entity.getX(),entity.getY()+delta,entity.getZ());return true;}
        return false;
    }
    public static boolean special(ServerPlayerEntity player,ToolTarget target){
        var select=selection(player);
        if(target.kind()==ToolTarget.Kind.RP&&target.entity(player.getServer()) instanceof RpObjectEntity rp){
            if(rp.isMechanism()){
                select.lamp=null;
                if(select.levers.contains(rp.getUuid())){player.sendMessage(Text.literal("Рычаг уже выбран."),true);return true;}
                if(select.levers.size()>=6){player.sendMessage(Text.literal("слишком много рычагов в связке!"),false);return false;}
                select.levers.add(rp.getUuid());player.sendMessage(Text.literal("Выбрано рычагов: "+select.levers.size()+" / 6. Теперь выберите открывающийся объект."),false);return true;
            }
            if(rp.supportsDogVisibility()){clearSelection(player);boolean accepted=rp.setDogsVisible(!rp.dogsVisible());player.sendMessage(Text.literal(rp.dogsVisible()?"Собака возвращена.":"Собака скрыта."),true);return accepted;}
            if(rp.assetId().equals("hunterlamp")){
                select.levers.clear();if(select.lamp==null){select.lamp=target;player.sendMessage(Text.literal("Выбран фонарь. Выберите второй фонарь."),false);return true;}
                if(select.lamp.uuid().equals(target.uuid())){player.sendMessage(Text.literal("Выберите другой фонарь."),true);return false;}
                boolean linked=LampService.pairLamps(player,select.lamp.uuid(),target.uuid());if(linked){select.lamp=null;player.sendMessage(Text.literal("Фонари связаны в обе стороны."),false);}return linked;
            }
        }
        var ref=target.mechanism();
        if(ref!=null&&MechanismLinks.inspect(player.getServer(),ref).availability()==MechanismLinks.Availability.LOADED){
            if(!select.levers.isEmpty()){boolean linked=MechanismLinks.linkLevers(player,List.copyOf(select.levers),ref);if(linked){clearSelection(player);player.sendMessage(Text.literal("Связка рычагов создана."),false);}else player.sendMessage(Text.literal("Не удалось создать связку."),false);return linked;}
            clearSelection(player);boolean only=!ObjectPolicies.leversOnly(player.getServer(),ref);boolean changed=ObjectPolicies.setManual(player,ref,only);if(changed)player.sendMessage(Text.literal(only?"Объект открывается только рычагом.":"Объект открывается и закрывается на ПКМ."),false);return changed;
        }
        clearSelection(player);return alternate(player,target,select.radius);
    }
    private static String catalogueId(BlockState state){var entry=dev.dreamwalker.bloodbornedw.debug.DebugCatalogue.entry(state);return entry==null?Registries.BLOCK.getId(state.getBlock()).toString():entry.temporaryId();}
    public static boolean alternate(ServerPlayerEntity player,ToolTarget target,int radius){
        if(radius<0||radius>BbAlternateService.MAX_RADIUS)return false;
        String current=BbAlternateService.profile(target,player.getServer());if(current==null){player.sendMessage(Text.literal("Альтернативного вида нет."),true);return false;}
        String next=current.equals("alt")?"base":"alt";if(radius>0)return BbAlternateService.start(player,next,radius,target);
        boolean changed=BbAlternateService.change(player,target,next);player.sendMessage(Text.literal("Вид "+next+": изменено объектов — "+(changed?1:0)+"."),false);return changed;
    }
    public static boolean clear(ServerPlayerEntity player,ToolTarget target){
        if(target.kind()==ToolTarget.Kind.BLOCK){var world=target.world(player.getServer());var root=BlockPos.fromLong(target.root());if(DwUtilityBlocks.marker(world.getBlockState(root)))return DwUtilityBlocks.remove(world,root,player);}
        var server=player.getServer();if(target.uuid()!=null){
            MechanismLinks.removeWholeGroup(server,target.uuid());LampService.removeWholeGroup(server,target.uuid());}
        ToolCommands.remove(server,target);
        if(target.mechanism()!=null)ObjectPolicies.removed(server,target.mechanism());clearSelection(player);player.sendMessage(Text.literal("Связи и команды удалены."),false);return true;
    }
    private static boolean openCommands(ServerPlayerEntity player,ToolTarget target){
        if(!player.hasPermissionLevel(4)){player.sendMessage(Text.literal("Привязка консольных команд доступна только OP4."),false);return false;}
        if(!ServerPlayNetworking.canSend(player,OPEN)){player.sendMessage(Text.literal("Для настройки команд нужен клиент Dreamwalker BB V12."),false);return false;}
        var token=UUID.randomUUID();EDITS.computeIfAbsent(player.getServer(),s->new HashMap<>()).put(player.getUuid(),new Pending(token,target,player.getServer().getTicks()+6000));
        var view=ToolCommands.view(player.getServer(),target);var out=PacketByteBufs.create();out.writeUuid(token);out.writeString(target.registry(),256);out.writeString(view.text(),ToolCommands.MAX_TEXT);out.writeVarInt(view.personalLimit());out.writeVarInt(view.globalLimit());out.writeVarInt(view.globalUsed());ServerPlayNetworking.send(player,OPEN,out);return true;
    }
    public static boolean save(ServerPlayerEntity player,UUID token,String text,int personal,int global){
        var edits=EDITS.get(player.getServer());var pending=edits==null?null:edits.get(player.getUuid());
        if(pending==null||!pending.token.equals(token)||pending.expires<player.getServer().getTicks()||!holding(player)||BbToolItem.mode(player.getMainHandStack())!=BbToolItem.Mode.COMMANDS||!player.hasPermissionLevel(4)||!pending.target.valid(player.getServer())){player.sendMessage(Text.literal("Настройка устарела. Снова выберите объект инструментом."),false);return false;}
        try{boolean saved=ToolCommands.save(player,pending.target,text,personal,global);if(saved){edits.remove(player.getUuid());player.sendMessage(Text.literal("Команды сохранены. ПКМ по объекту срабатывает дополнительно к обычному действию."),false);}else player.sendMessage(Text.literal("Не удалось сохранить команды: проверьте лимиты 1–999999."),false);return saved;}
        catch(IllegalArgumentException invalid){player.sendMessage(Text.literal(invalid.getMessage()),false);return false;}
    }
}
