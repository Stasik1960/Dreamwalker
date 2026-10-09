package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.mount.VerticalMount;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderRuntime;
import dev.dreamwalker.bloodbornedw.architecture.wall.*;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.diagnostics.DwDiagnostics;
import dev.dreamwalker.bloodbornedw.link.*;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornerp.lamp.LampEditor;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;

/** Every GUI mutation is resolved and authorised again on the server thread. */
public final class BuilderServer {
    public static final Identifier REQUEST=new Identifier("bloodborne_dw","builder_request"),VIEW=new Identifier("bloodborne_dw","builder_view");
    public static final int MAX_REQUEST=12000,MAX_VIEW=300000;
    private static final Gson JSON=new Gson();
    private static final java.util.concurrent.ConcurrentHashMap<UUID,java.util.concurrent.atomic.AtomicInteger> REQUEST_PENDING=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static boolean initialized;
    private static final class Session {
        MechanismLinks.TargetRef target,source; boolean pinned,success=true,globalRules; long expires,lastRequest,ackRequest;
        int lastClick=-100,lampPage,rulePage,affectedPage; UUID rule;
        Draft draft; final BuilderHistory history=new BuilderHistory();
        final Set<UUID> editable=new LinkedHashSet<>(); List<MechanismLinks.TargetRef> candidates=List.of(); String message="";
    }
    private static final class Draft {
        String name="",condition="ANY",effect="TOGGLE";
        final LinkedHashMap<String,MechanismLinks.TargetRef> sources=new LinkedHashMap<>(),targets=new LinkedHashMap<>();
    }
    private BuilderServer(){}
    public static void initialize(){if(initialized)return;initialized=true;
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(server,player,handler,buf,sender)->{int size=buf.readableBytes();String raw;try{raw=buf.readString(MAX_REQUEST);}catch(RuntimeException invalid){return;}DwDiagnostics.network("server","receive",REQUEST.toString(),size);var pending=REQUEST_PENDING.computeIfAbsent(player.getUuid(),id->new java.util.concurrent.atomic.AtomicInteger());if(pending.incrementAndGet()>4){pending.decrementAndGet();return;}server.execute(()->{try{request(player,JsonParser.parseString(raw).getAsJsonObject());}catch(RuntimeException invalid){DwDiagnostics.error(player.getServerWorld(),"UNASSIGNED",player.getUuidAsString(),player.getBlockPos(),"BUILDER_REQUEST","Rejected invalid builder request",invalid);Session current=session(player);current.success=false;current.message="Некорректные параметры меню; изменение отклонено.";notice(player,current.message);send(player,current,false);}finally{pending.decrementAndGet();}});});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clearPlayer(handler.player.getUuid()));ServerLifecycleEvents.SERVER_STOPPED.register(server->{SESSIONS.clear();REQUEST_PENDING.clear();});
    }
    private static void clearPlayer(UUID id){SESSIONS.remove(id);REQUEST_PENDING.remove(id);}
    private static Session session(ServerPlayerEntity player){
        if(SESSIONS.size()>=256&&!SESSIONS.containsKey(player.getUuid()))SESSIONS.remove(SESSIONS.keySet().iterator().next());
        // Online sessions retain their pin/drafts; disconnect/stop are the explicit lifecycle boundary.
        return SESSIONS.computeIfAbsent(player.getUuid(),i->new Session());
    }
    private static boolean linkContext(ServerPlayerEntity p){return Set.of(BuildingTool.Action.LINK,BuildingTool.Action.UNLINK,BuildingTool.Action.LAMP_SOURCE,BuildingTool.Action.LAMP_TARGET).contains(BuildingTool.action(p.getMainHandStack()));}
    private static boolean related(MechanismRules.Rule r,MechanismLinks.TargetRef t){return t!=null&&(r.targets.containsKey(t.key())||r.sources.containsKey(t.instance()));}
    public static void open(ServerPlayerEntity player){if(!BuildingTool.mainHeld(player))return;dev.dreamwalker.bloodbornerp.lamp.LampService.cancelTravel(player);Session s=session(player);s.candidates=candidates(player);if(!s.pinned&&s.target==null&&!s.candidates.isEmpty()){s.target=s.candidates.get(0);s.pinned=true;}restoreRule(player,s);send(player,s,true);}
    private static void restoreRule(ServerPlayerEntity p,Session s){var n=p.getMainHandStack().getOrCreateNbt();if(n.containsUuid("BuilderRule")){UUID id=n.getUuid("BuilderRule");if(allowedRule(p,s,id))s.rule=id;}}
    private static boolean allowedRule(ServerPlayerEntity p,Session s,UUID id){var r=MechanismRules.rule(p.getServer(),id);return r!=null&&(s.globalRules&&p.hasPermissionLevel(2)||s.target!=null&&(r.targets.containsKey(s.target.key())||r.sources.containsKey(s.target.instance()))||linkContext(p)&&s.source!=null&&(r.targets.containsKey(s.source.key())||r.sources.containsKey(s.source.instance())));}
    private static void request(ServerPlayerEntity p,JsonObject q){
        if(!BuildingTool.isHeld(p))return; Session s=session(p);String op=text(q,"op",40);
        long requestId=q.has("requestId")?q.get("requestId").getAsLong():0;
        if(requestId>0){s.ackRequest=requestId;if(requestId<=s.lastRequest){send(p,s,false);return;}s.lastRequest=requestId;}
        s.message="";s.success=true;
        if(!BuildingTool.mainHeld(p)){fail(p,s,"Перенесите инструмент 90009 в основную руку.");return;}
        if(op.equals("open")){open(p);return;}if(op.equals("refresh")){send(p,s,false);return;}
        if(!BuildPermissions.canEdit(p)){fail(p,s,"Редактирование доступно в Creative или оператору.");return;}
        if(Set.of("rule_save","rule_apply","rule_remove_member","rule_delete","rule_reset").contains(op)&&q.has("expectedRuleId")){
            String actual=s.draft!=null?"draft":s.rule==null?"":s.rule.toString();if(!actual.equals(text(q,"expectedRuleId",40))){fail(p,s,"Выбранное правило изменилось. Старый запрос не применён к другой группе.");return;}
        }
        if(Set.of("object","commands","rule_save","rule_apply","rule_remove_member","rule_delete","rule_reset","lamp","undo").contains(op)){
            if(q.has("expectedKey")&&(s.target==null||!text(q,"expectedKey",256).equals(s.target.key()))){fail(p,s,"Выбор изменился. Перечитайте форму; данные другого экземпляра сохранены.");return;}
            if(q.has("expectedVersion")&&s.target!=null&&!text(q,"expectedVersion",256).equals(version(p,s.target))){fail(p,s,"Параметры изменены другим игроком. Черновик сохранён; перечитайте текущие данные.");return;}
            if(q.has("expectedFormVersion")&&!text(q,"expectedFormVersion",256).equals(formVersion(p,s))){fail(p,s,"Связи или форма изменились. Перечитайте данные перед сохранением.");return;}
        }
        ItemStack tool=p.getMainHandStack();
        switch(op){
            case "click" -> {click(p,s,q);return;}
            case "select_cycle" -> {s.candidates=candidates(p);if(s.candidates.isEmpty()){s.success=false;s.message="Под прицелом нет объекта мода.";}else{int index=-1;for(int i=0;i<s.candidates.size();i++)if(s.target!=null&&s.candidates.get(i).key().equals(s.target.key()))index=i;s.target=s.candidates.get((index+1)%s.candidates.size());s.pinned=true;validateMode(p,s);s.message="Выбран перекрывающийся экземпляр.";}}
            case "mode" -> {BuildingTool.Action action=BuildingTool.Action.valueOf(text(q,"action",40));BuildingTool.setAction(tool,action);if(Set.of(BuildingTool.Action.LINK,BuildingTool.Action.UNLINK,BuildingTool.Action.LAMP_TARGET).contains(action)&&q.has("line")){tool.getOrCreateNbt().putString("BuilderLampLine",text(q,"line",64));tool.getOrCreateNbt().putBoolean("BuilderLampOneWay",!q.get("both").getAsBoolean());}s.message="Действие: "+action.label;}
            case "cycle_mode" -> {List<BuildingTool.Action> modes=quickActions(p,s.target);int index=modes.indexOf(BuildingTool.action(tool));BuildingTool.Action action=modes.get(Math.floorMod(Math.max(0,index)+(q.has("delta")?Math.max(-8,Math.min(8,q.get("delta").getAsInt())):1),modes.size()));BuildingTool.setAction(tool,action);s.message="Действие: "+action.label;}
            case "step_cycle" -> {double[] steps={.0625,.125,.25,1};int index=1;for(int i=0;i<4;i++)if(step(tool)==steps[i])index=i;tool.getOrCreateNbt().putDouble("BuilderStep",steps[Math.floorMod(index+(q.has("delta")?Math.max(-8,Math.min(8,q.get("delta").getAsInt())):1),4)]);}
            case "step" -> {double value=q.get("value").getAsDouble();if(value!=.0625&&value!=.125&&value!=.25&&value!=1)throw new IllegalArgumentException("step");tool.getOrCreateNbt().putDouble("BuilderStep",value);}
            case "select" -> {String key=text(q,"key",256);s.candidates=candidates(p);s.target=s.candidates.stream().filter(t->t.key().equals(key)).findFirst().orElse(null);s.pinned=s.target!=null;validateMode(p,s);s.success=s.pinned;s.message=s.pinned?"Выбор закреплён.":"Объект больше не находится под прицелом.";}
            case "cancel_selection" -> {s.target=null;s.source=null;s.pinned=false;s.rule=null;tool.getOrCreateNbt().remove("BuilderRule");MechanismBuilder.cancel(p,tool);LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.CANCEL,null,null,"","",true));s.message="Рабочий выбор отменён; сохранённые связи остались.";}
            case "undo" -> {var edit=s.history.last();if(edit==null||!near(p,edit.target())||!BuildPermissions.canEdit(p.getWorld(),p,BlockPos.fromLong(edit.target().root()))){s.success=false;s.message="Нет доступного изменения для отмены.";}else{s.message=s.history.undo(p);s.success=s.message.startsWith("Отменено:");}}
            case "object" -> objectForm(p,s,q);
            case "rule_create" -> {if(s.draft==null){s.draft=new Draft();s.draft.name=text(q,"name",80);s.rule=null;}s.message="Черновик: выберите источники и цели, затем сохраните. В мире пустого правила нет.";}
            case "rule_cancel" -> {s.draft=null;s.message="Черновик правила отменён; сохранённые правила остались.";}
            case "rule_select" -> {if(s.draft!=null){s.success=false;s.message="Сохраните или отмените текущий черновик перед выбором другого правила.";break;}UUID id=UUID.fromString(text(q,"id",40));if(allowedRule(p,s,id)){s.rule=id;s.draft=null;s.editable.add(id);tool.getOrCreateNbt().putUuid("BuilderRule",id);}else{s.success=false;s.message="Это правило не относится к выбранному экземпляру.";}}
            case "rule_save", "rule_apply" -> saveRule(p,s,q);
            case "rule_remove_member" -> {if(s.draft!=null){(q.get("source").getAsBoolean()?s.draft.sources:s.draft.targets).remove(text(q,"key",256));s.message="Участник удалён из черновика.";}else if(ruleAllowed(p,s)){var r=MechanismRules.rule(p.getServer(),s.rule);boolean source=q.get("source").getAsBoolean();if(!source&&r.sources.size()>1&&!(q.has("confirmed")&&q.get("confirmed").getAsBoolean())){s.success=false;s.message="Удаление цели затронет все источники этой группы. Требуется подтверждение.";}else{s.success=MechanismRules.removeMember(p,s.rule,text(q,"key",256),source);s.message=s.success?"Участник удалён из правила.":"Удаление отклонено.";}}}
            case "rule_reset" -> {if(ruleAllowed(p,s)&&q.has("confirmed")&&q.get("confirmed").getAsBoolean()){s.success=MechanismRules.reset(p,s.rule);s.message="Логические признаки сброшены; группы «Все» пересчитаны.";}}
            case "rule_delete" -> {if(s.draft!=null){s.draft=null;s.message="Черновик удалён; сохранённые правила остались.";}else if(ruleAllowed(p,s)&&q.has("confirmed")&&q.get("confirmed").getAsBoolean()){s.success=MechanismRules.delete(p,s.rule);s.rule=null;tool.getOrCreateNbt().remove("BuilderRule");s.message="Правило удалено; физические объекты сохранены.";}}
            case "commands" -> saveCommands(p,s,q);
            case "rule_scope" -> {s.globalRules=p.hasPermissionLevel(2)&&q.has("global")&&q.get("global").getAsBoolean();s.rulePage=0;}
            case "rule_page" -> s.rulePage=Math.max(0,Math.min(63,q.get("page").getAsInt()));
            case "affected_page" -> s.affectedPage=Math.max(0,Math.min(63,q.get("page").getAsInt()));
            case "lamp_page" -> s.lampPage=Math.max(0,Math.min(1023,q.get("page").getAsInt()));
            case "lamp_parameters" -> {String line=text(q,"line",64);if(line.isBlank())line=LampEditor.DEFAULT_LINE;tool.getOrCreateNbt().putString("BuilderLampLine",line);tool.getOrCreateNbt().putBoolean("BuilderLampOneWay",!q.get("both").getAsBoolean());s.message="Параметры следующего назначения сохранены.";}
            case "lamp" -> lampForm(p,s,q);
            case "diagnostics" -> diagnostics(p,s,q);
            default -> throw new IllegalArgumentException("Unknown builder operation");
        }
        p.getInventory().markDirty();if(!s.message.isBlank())notice(p,s.message);if(Set.of("commands","rule_save","rule_delete","rule_remove_member","lamp","diagnostics").contains(op)&&!s.message.isBlank())p.sendMessage(Text.literal(s.message),false);send(p,s,false);
    }
    private static void fail(ServerPlayerEntity p,Session s,String message){s.success=false;s.message=message;notice(p,message);send(p,s,false);}
    private static List<String> commandLines(JsonArray array){List<String> out=new ArrayList<>();if(array==null||array.size()>32)throw new IllegalArgumentException("commands");for(JsonElement e:array){String line=e.getAsString();if(line.length()>2048||line.contains("\n")||line.contains("\r"))throw new IllegalArgumentException("command line");out.add(line);}return out;}
    private static void saveCommands(ServerPlayerEntity p,Session s,JsonObject q){if(!validTarget(p,s)||!p.hasPermissionLevel(4)){s.success=false;s.message="Нужен доступный открываемый объект и права администратора уровня 4.";return;}
        if(q.has("afterOpen")&&q.has("afterClose")){var opened=commandLines(q.getAsJsonArray("afterOpen"));var closed=commandLines(q.getAsJsonArray("afterClose"));s.success=ObjectPolicies.setCommandLists(p,s.target,opened,closed,ObjectPolicies.fingerprint(p.getServer(),s.target));}
        else s.success=ObjectPolicies.setCommands(p,s.target,q.get("open").getAsBoolean(),commandLines(q.getAsJsonArray("commands")));
        s.message=s.success?"Списки команд сохранены для выбранного UUID.":"Сохранение команд отклонено.";
    }
    private static void saveRule(ServerPlayerEntity p,Session s,JsonObject q){String name=text(q,"name",80);var condition=MechanismRules.Condition.valueOf(text(q,"condition",10));var effect=MechanismRules.Effect.valueOf(text(q,"effect",10));MechanismRules.EditResult result;
        if(s.draft!=null)result=MechanismRules.createValid(p,name,condition,effect,s.draft.sources.values(),s.draft.targets.values());
        else if(ruleAllowed(p,s)){var r=MechanismRules.rule(p.getServer(),s.rule);result=MechanismRules.saveValid(p,s.rule,MechanismRules.fingerprint(p.getServer(),s.rule),name,condition,effect,r.sources.values(),r.targets.values());}
        else{s.success=false;s.message="Сначала выберите правило или создайте черновик.";return;}
        s.success=result.success();s.message=result.reason();if(result.success()){if(s.draft!=null&&!s.draft.sources.isEmpty()){s.target=s.draft.sources.values().iterator().next();s.pinned=true;}s.rule=result.rule();s.draft=null;s.editable.add(s.rule);p.getMainHandStack().getOrCreateNbt().putUuid("BuilderRule",s.rule);}
    }
    private static boolean ruleAllowed(ServerPlayerEntity p,Session s){return s.rule!=null&&allowedRule(p,s,s.rule);}
    private static void click(ServerPlayerEntity p,Session s,JsonObject q){
        if(s.lastClick==p.getServer().getTicks()){s.message="Операция этого тика уже принята.";send(p,s,false);return;}s.lastClick=p.getServer().getTicks();
        s.candidates=candidates(p);var action=BuildingTool.action(p.getMainHandStack());boolean reverse=q.has("reverse")&&q.get("reverse").getAsBoolean();
        boolean linking=Set.of(BuildingTool.Action.LINK,BuildingTool.Action.UNLINK,BuildingTool.Action.RULE_SOURCE,BuildingTool.Action.RULE_TARGET,BuildingTool.Action.LAMP_SOURCE,BuildingTool.Action.LAMP_TARGET).contains(action);
        MechanismLinks.TargetRef hit=s.candidates.isEmpty()?null:s.candidates.get(0);
        if(action==BuildingTool.Action.SELECT||action==BuildingTool.Action.CONNECTIONS){if(hit==null){fail(p,s,"Под прицелом нет подходящего объекта. Закреплённый выбор сохранён.");return;}s.target=hit;s.pinned=true;validateMode(p,s);s.message="Объект закреплён. ПКМ — настройки.";}
        else if(linking){if(hit==null){fail(p,s,"Наведите прицел на подходящую цель связи.");return;}
            if(action==BuildingTool.Action.RULE_SOURCE||action==BuildingTool.Action.RULE_TARGET){boolean source=action==BuildingTool.Action.RULE_SOURCE;
                if(s.draft!=null){boolean supported=source?isLever(p,hit):MechanismLinks.inspect(p.getServer(),hit).availability()==MechanismLinks.Availability.LOADED;var members=source?s.draft.sources:s.draft.targets;if(!supported||members.size()>=64){s.success=false;s.message="Неподходящий участник или достигнут предел 64.";}else{members.put(hit.key(),hit);s.message="Участник черновика добавлен. Сохраните состав в меню.";}}
                else if(ruleAllowed(p,s)){var result=MechanismRules.addValid(p,s.rule,hit,source,MechanismRules.fingerprint(p.getServer(),s.rule));s.success=result.success();s.message=result.reason();}else{s.success=false;s.message="Сначала создайте черновик правила.";}}
            else if(action==BuildingTool.Action.LAMP_SOURCE){s.source=hit;var result=LampEditor.selectSource(p,hit.kind()==MechanismLinks.Kind.RP?hit.instance():null);s.success=result.success();s.message=result.reason();if(!s.success)s.source=null;else{s.target=hit;s.pinned=true;}}
            else if(s.source==null){if(!isLever(p,hit)&&!isLamp(hit)){fail(p,s,"Сначала выберите рычаг или фонарь как источник.");return;}s.source=hit;s.target=hit;s.pinned=true;if(isLamp(hit)){var result=LampEditor.selectSource(p,hit.instance());s.success=result.success();s.message=result.reason();if(!s.success)s.source=null;}else s.message="Источник закреплён. Следующие ЛКМ добавляют цели; задержка рычага 70 тиков.";}
            else if(isLamp(s.source)){String line=p.getMainHandStack().getOrCreateNbt().getString("BuilderLampLine");if(line.isBlank())line="Охотничья линия";var result=action==BuildingTool.Action.LAMP_TARGET?LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.CONNECT,s.source.instance(),hit.kind()==MechanismLinks.Kind.RP?hit.instance():null,line,"",!p.getMainHandStack().getOrCreateNbt().getBoolean("BuilderLampOneWay"))):LampEditor.simplePair(p,s.source.instance(),hit.kind()==MechanismLinks.Kind.RP?hit.instance():null,line,action==BuildingTool.Action.UNLINK);s.success=result.success();s.message=result.reason();if(result.success())p.getMainHandStack().getOrCreateNbt().putString("BuilderLampLine",line);}
            else{var result=action==BuildingTool.Action.UNLINK?MechanismRules.simpleUnlink(p,s.source,hit):MechanismRules.simpleConnect(p,s.source,hit);s.success=result.success();s.message=result.reason();}
        }
        else if(s.target==null){if(q.has("expectedKey")&&!text(q,"expectedKey",256).isBlank()){fail(p,s,"Прежний закреплённый экземпляр недоступен. Снимите выбор клавишей X и выберите новый.");return;}if(hit==null){fail(p,s,"Первый клик выбирает объект; наведите прицел на объект мода.");return;}s.target=hit;s.pinned=true;validateMode(p,s);s.message="Объект выбран. Следующий ЛКМ изменит его.";}
        else if(!validTarget(p,s)){fail(p,s,"Закреплённый экземпляр недоступен или вне досягаемости. Выберите другой средней кнопкой.");return;}
        else if(q.has("expectedKey")&&!text(q,"expectedKey",256).equals(s.target.key())){fail(p,s,"Клик относится к прежнему выбору; изменение отменено.");return;}
        else{
            var before=BuilderHistory.capture(p,s.target);boolean undoable=true;s.message="";
            if(action==BuildingTool.Action.UP||action==BuildingTool.Action.DOWN){double sign=action==BuildingTool.Action.DOWN?-1:1;if(reverse)sign=-sign;s.success=setOffset(p,s.target,offset(p,s.target)+sign*step(p.getMainHandStack()));}
            else if(action==BuildingTool.Action.DIAGNOSTICS){undoable=false;if(p.hasPermissionLevel(2)){s.success=snapshot(p,s.target,"Снимок инструментом");s.message=s.success?"Снимок сохранён; запись не запущена.":"Снимок содержит ошибку; подробности записаны в диагностику.";}else{s.success=false;s.message="Диагностика доступна оператору уровня 2.";}}
            else if(s.target.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(s.target.instance()) instanceof RpObjectEntity rp){
                if(action==BuildingTool.Action.ROTATE)s.success=rp.rotateByBuilder(p,rp.getYaw()+(reverse?-45:45));
                else if(action==BuildingTool.Action.DOGS)s.success=rp.setDogsVisible(!rp.dogsVisible());
                else{s.success=false;s.message="Этот RP-объект не поддерживает выбранное действие.";}}
            else{s.success=BuildingTool.applyBlock(p,BlockPos.fromLong(s.target.root()),action,reverse).isAccepted();undoable=action!=BuildingTool.Action.POSE;}
            if(s.success&&undoable)s.history.record(p,s.target,before,action.label);
            if(s.message.isBlank())s.message=s.success?"Применено: "+action.label+". UUID и связи сохранены.":"Изменение отклонено: неподдерживаемая операция, препятствие или права.";
        }
        p.getInventory().markDirty();notice(p,s.message);send(p,s,false);
    }
    private static boolean isLamp(MechanismLinks.TargetRef t){return t!=null&&t.kind()==MechanismLinks.Kind.RP&&t.registryId().equals("hunterlamp");}
    private static boolean isLever(ServerPlayerEntity p,MechanismLinks.TargetRef t){return t!=null&&t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp&&rp.isMechanism();}
    private static List<BuildingTool.Action> quickActions(ServerPlayerEntity p,MechanismLinks.TargetRef t){var out=new ArrayList<BuildingTool.Action>();out.add(BuildingTool.Action.SELECT);if(t!=null){out.add(BuildingTool.Action.ROTATE);out.add(BuildingTool.Action.UP);if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp){if(rp.supportsDogVisibility())out.add(BuildingTool.Action.DOGS);}else{BlockState state=p.getWorld().getBlockState(BlockPos.fromLong(t.root()));if(BuildingTool.supports(state,BuildingTool.Action.PROFILE))out.add(BuildingTool.Action.PROFILE);if(state.contains(ThinWindowRootBlock.MOUNT))out.add(BuildingTool.Action.MOUNT);}var status=MechanismLinks.inspect(p.getServer(),t);if(isLever(p,t)||isLamp(t)||status.availability()==MechanismLinks.Availability.LOADED){out.add(BuildingTool.Action.LINK);out.add(BuildingTool.Action.UNLINK);}}else{out.add(BuildingTool.Action.LINK);out.add(BuildingTool.Action.UNLINK);}out.add(BuildingTool.Action.CONNECTIONS);return out;}
    private static void validateMode(ServerPlayerEntity p,Session s){var action=BuildingTool.action(p.getMainHandStack());boolean compatible=quickActions(p,s.target).contains(action)||action==BuildingTool.Action.DOWN||action==BuildingTool.Action.DIAGNOSTICS;if(s.target!=null&&s.target.kind()==MechanismLinks.Kind.ARCHITECTURE)compatible|=BuildingTool.supports(p.getWorld().getBlockState(BlockPos.fromLong(s.target.root())),action);if(!compatible){BuildingTool.setAction(p.getMainHandStack(),BuildingTool.Action.SELECT);s.message="Выбранный тип не поддерживает прежний режим. Переключено на «Выбор».";}}
    public static double step(ItemStack stack){double value=stack.getOrCreateNbt().getDouble("BuilderStep");return value==.0625||value==.125||value==.25||value==1?value:.125;}
    private static boolean validTarget(ServerPlayerEntity p,Session s){return s.target!=null&&near(p,s.target)&&BuildPermissions.canEdit(p.getWorld(),p,BlockPos.fromLong(s.target.root()));}
    private static boolean near(ServerPlayerEntity p,MechanismLinks.TargetRef t){
        if(t==null||!t.dimension().equals(p.getWorld().getRegistryKey().getValue().toString()))return false;
        if(t.kind()==MechanismLinks.Kind.RP){Entity e=p.getServerWorld().getEntity(t.instance());if(!(e instanceof RpObjectEntity rp)||rp.isRemoved()||!rp.assetId().equals(t.registryId()))return false;}
        else{BlockPos root=BlockPos.fromLong(t.root());if(!p.getWorld().isChunkLoaded(root))return false;var owner=VerticalMount.owner(p.getWorld(),root);if(owner==null||!owner.instanceId().equals(t.instance())||!owner.registryId().equals(t.registryId()))return false;}
        return boxes(p,t,false).stream().anyMatch(b->distance(p.getEyePos(),b)<=36);
    }
    private static List<Box> boxes(ServerPlayerEntity p,MechanismLinks.TargetRef t,boolean physical){
        if(!t.dimension().equals(p.getWorld().getRegistryKey().getValue().toString()))return List.of();
        if(t.kind()==MechanismLinks.Kind.RP){Entity e=p.getServerWorld().getEntity(t.instance());return e instanceof RpObjectEntity rp?(physical?rp.activePhysicalBoxes():rp.selectionBoxes()):List.of();}
        BlockPos root=BlockPos.fromLong(t.root());if(!p.getWorld().isChunkLoaded(root)||!(VerticalMount.loadedEntity(p.getWorld(),root) instanceof CompositeBlockEntity own)||own.resident()==null||!own.resident().instanceId().equals(t.instance()))return List.of();
        var object=CompositeRuntime.instance(p.getServerWorld(),root,p.getWorld().getBlockState(root),t.instance(),own.payload());var out=new ArrayList<Box>();
        for(var row:object.cells().entrySet())for(var box:physical?row.getValue().collision():row.getValue().selection()){
            BlockPos pos=CompositeData.pos(object.owner().root().add(row.getKey()));out.add(new Box(pos.getX()+box.minX(),pos.getY()+box.minY(),pos.getZ()+box.minZ(),pos.getX()+box.maxX(),pos.getY()+box.maxY(),pos.getZ()+box.maxZ()));
        }return out;
    }
    private static List<List<Double>> boxView(List<Box> boxes){return boxes.stream().map(b->List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();}
    private static String digest(String value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static String version(ServerPlayerEntity p,MechanismLinks.TargetRef t){var state=BuilderHistory.capture(p,t);return digest(t.key()+"|"+state+"|"+ObjectPolicies.fingerprint(p.getServer(),t));}
    private static String formVersion(ServerPlayerEntity p,Session s){return digest((s.rule==null?"":s.rule.toString()+"|"+MechanismRules.fingerprint(p.getServer(),s.rule))+"|"+(isLamp(s.target)?LampEditor.fingerprint(p.getServer(),s.target.instance()):"")+"|"+(s.draft==null?"":s.draft.name+s.draft.condition+s.draft.effect+s.draft.sources.keySet()+s.draft.targets.keySet()));}
    private static double distance(Vec3d p,Box b){double x=Math.max(Math.max(b.minX-p.x,p.x-b.maxX),0),y=Math.max(Math.max(b.minY-p.y,p.y-b.maxY),0),z=Math.max(Math.max(b.minZ-p.z,p.z-b.maxZ),0);return x*x+y*y+z*z;}
    private static double offset(ServerPlayerEntity p,MechanismLinks.TargetRef t){if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return rp.verticalOffset();return VerticalMount.offset(p.getWorld(),BlockPos.fromLong(t.root()));}
    private static boolean setOffset(ServerPlayerEntity p,MechanismLinks.TargetRef t,double value){if(!Double.isFinite(value))return false;if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return rp.setBuilderGeometry(value,null,p);return VerticalMount.setOffset(p.getServerWorld(),BlockPos.fromLong(t.root()),value,p);}
    private record ObjectFields(Double offset,Boolean manual,Boolean dogs,ThinWindowRootBlock.Mount mount) {}
    private static JsonPrimitive primitive(JsonElement value,String field){if(value==null||!value.isJsonPrimitive())throw new IllegalArgumentException("Invalid object field: "+field);return value.getAsJsonPrimitive();}
    private static double number(JsonElement value,String field){var p=primitive(value,field);if(!p.isNumber())throw new IllegalArgumentException("Expected numeric "+field);double result=p.getAsDouble();if(!Double.isFinite(result))throw new IllegalArgumentException("Non-finite "+field);return result;}
    private static boolean flag(JsonElement value,String field){var p=primitive(value,field);if(!p.isBoolean())throw new IllegalArgumentException("Expected boolean "+field);return p.getAsBoolean();}
    private static ThinWindowRootBlock.Mount plane(JsonElement value){var p=primitive(value,"mount");if(!p.isString())throw new IllegalArgumentException("Expected mounting plane");return ThinWindowRootBlock.Mount.valueOf(p.getAsString());}
    private static ObjectFields objectFields(JsonObject values){
        if(values.size()>4)throw new IllegalArgumentException("object fields");
        for(var entry:values.entrySet())if(!Set.of("offset","manual","dogs","mount").contains(entry.getKey()))throw new IllegalArgumentException("object field");
        return new ObjectFields(values.has("offset")?number(values.get("offset"),"offset"):null,values.has("manual")?flag(values.get("manual"),"manual"):null,values.has("dogs")?flag(values.get("dogs"),"dogs"):null,values.has("mount")?plane(values.get("mount")):null);
    }
    private static void objectForm(ServerPlayerEntity p,Session s,JsonObject q){
        if(!validTarget(p,s)){s.success=false;s.message="Выбранный экземпляр недоступен или вне досягаемости.";return;}
        ObjectFields fields;
        try{
            if(q.has("values")){if(!q.get("values").isJsonObject())throw new IllegalArgumentException("object values");fields=objectFields(q.getAsJsonObject("values"));}
            else{String field=text(q,"field",40);JsonObject values=new JsonObject();if(field.equals("reset_height"))values.addProperty("offset",0);else values.add(field,q.get("value"));fields=objectFields(values);}
        }catch(RuntimeException invalid){s.success=false;s.message="Некорректные поля объекта. Все прежние параметры сохранены.";return;}
        var target=currentRef(p,s.target);var before=BuilderHistory.capture(p,target);
        if(fields.manual()!=null&&!ObjectPolicies.canSetManual(p,target)){s.success=false;s.message="Ручное управление не поддерживается или сохранение недоступно. Параметры не изменены.";return;}
        if(target.kind()==MechanismLinks.Kind.RP){
            if(!(p.getServerWorld().getEntity(target.instance()) instanceof RpObjectEntity object)||fields.mount()!=null||fields.dogs()!=null&&!object.supportsDogVisibility()){s.success=false;s.message="Тип не поддерживает указанный монтаж или содержимое. Параметры не изменены.";return;}
            if((fields.offset()!=null||fields.dogs()!=null)&&!object.setBuilderGeometry(fields.offset()==null?object.verticalOffset():fields.offset(),fields.dogs(),p)){s.success=false;s.message="Геометрия отклонена: права, границы мира или живое препятствие. Параметры не изменены.";return;}
        }else{
            BlockPos root=BlockPos.fromLong(target.root());BlockState state=p.getWorld().getBlockState(root);
            if(fields.dogs()!=null||fields.mount()!=null&&!state.contains(ThinWindowRootBlock.MOUNT)){s.success=false;s.message="Тип не поддерживает указанный монтаж или содержимое. Параметры не изменены.";return;}
            BlockState next=fields.mount()==null?state:state.with(ThinWindowRootBlock.MOUNT,fields.mount());
            if((fields.offset()!=null||fields.mount()!=null)&&!VerticalMount.setGeometry(p.getServerWorld(),root,next,fields.offset()==null?VerticalMount.offset(p.getWorld(),root):fields.offset(),p)){s.success=false;s.message="Геометрия отклонена: права, препятствие или границы мира. Параметры не изменены.";return;}
        }
        // Read-only capacity/permission preflight and this commit run in the same server task.
        // Geometry never changes target identity, support for manual control, or policy capacity.
        if(fields.manual()!=null&&!ObjectPolicies.setManual(p,target,fields.manual())){
            boolean restored=restoreGeometry(p,target,before);s.success=false;s.message=restored?"Сохранение управления отклонено; прежняя геометрия восстановлена.":"Сохранение управления отклонено; восстановить геометрию не удалось. Перечитайте фактические параметры.";return;
        }
        s.success=true;s.history.record(p,target,before,"настройки объекта");s.message="Параметры экземпляра сохранены.";
    }
    private static boolean restoreGeometry(ServerPlayerEntity p,MechanismLinks.TargetRef t,BuilderHistory.State state){return BuilderHistory.restore(p,t,state);}
    private static boolean applyField(ServerPlayerEntity p,MechanismLinks.TargetRef t,String field,JsonElement value){
        // Keep the older single-field entrypoint strict as well; no coercion after a partial write.
        return switch(field){case "offset" -> setOffset(p,t,number(value,"offset"));case "reset_height" -> setOffset(p,t,0);
            case "manual" -> {boolean manual=flag(value,"manual");yield ObjectPolicies.canSetManual(p,t)&&ObjectPolicies.setManual(p,t,manual);}
            case "dogs" -> {boolean dogs=flag(value,"dogs");yield p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity object&&object.setBuilderGeometry(object.verticalOffset(),dogs,p);}
            case "mount" -> {var mount=plane(value);BlockPos root=BlockPos.fromLong(t.root());BlockState state=p.getWorld().getBlockState(root);yield state.contains(ThinWindowRootBlock.MOUNT)&&VerticalMount.setGeometry(p.getServerWorld(),root,state.with(ThinWindowRootBlock.MOUNT,mount),VerticalMount.offset(p.getWorld(),root),p);}
            default -> throw new IllegalArgumentException("object field");};
    }
    private static void lampForm(ServerPlayerEntity p,Session s,JsonObject q){String line=text(q,"line",64),name=text(q,"name",64);boolean both=q.has("both")&&q.get("both").getAsBoolean();LampEditor.Action action=LampEditor.Action.valueOf(text(q,"action",30));UUID source=null,destination=null;
        if(q.has("destination"))destination=UUID.fromString(text(q,"destination",40));if(q.has("source"))source=UUID.fromString(text(q,"source",40));else if((action==LampEditor.Action.RENAME||action==LampEditor.Action.REGISTER)&&validTarget(p,s)&&s.target.kind()==MechanismLinks.Kind.RP)source=s.target.instance();
        var result=LampEditor.edit(p,new LampEditor.Request(action,source,destination,line,name,both,q.has("confirmed")&&q.get("confirmed").getAsBoolean()));s.success=result.success();s.message=result.reason();if(result.success()){var n=p.getMainHandStack().getOrCreateNbt();if(action==LampEditor.Action.RENAME_LINE&&n.getString("BuilderLampLine").equals(line))n.putString("BuilderLampLine",name);else if(action==LampEditor.Action.CREATE_LINE)n.putString("BuilderLampLine",line);else if(Set.of(LampEditor.Action.CONNECT,LampEditor.Action.SET_DIRECTION,LampEditor.Action.UNLINK).contains(action)){n.putString("BuilderLampLine",line);if(q.has("both"))n.putBoolean("BuilderLampOneWay",!both);}}
    }
    private static void diagnostics(ServerPlayerEntity p,Session s,JsonObject q){
        if(!p.hasPermissionLevel(2)){s.success=false;s.message="Диагностика доступна оператору уровня 2.";return;}
        String action=text(q,"action",20),note=text(q,"note",512);
        switch(action){
            case "snapshot" -> {if(validTarget(p,s)){s.success=snapshot(p,s.target,note);s.message=s.success?"Снимок выбранного экземпляра сохранён.":"Снимок содержит ошибку объекта; подробности записаны в диагностику.";}else{s.success=false;s.message="Для снимка выберите доступный объект.";}}
            case "start" -> {boolean selected=q.has("selected")&&q.get("selected").getAsBoolean();if(selected&&!validTarget(p,s)){s.success=false;s.message="Выбранный экземпляр недоступен. Запись всего мода вместо него не запущена.";return;}DwDiagnostics.Filter filter=selected?new DwDiagnostics.Filter("object","",s.target.instance().toString(),s.target.dimension(),BlockPos.fromLong(s.target.root()),0):DwDiagnostics.Filter.all(p.getServerWorld());UUID id=DwDiagnostics.start(p.getServerWorld(),60,filter);s.message="Запись на 60 секунд: "+(selected?"выбранный экземпляр":"весь мод")+" · сеанс "+id;}
            case "stop" -> {boolean stopped=DwDiagnostics.stop(p.getServer(),"BUILDER_MENU_STOP");s.message=stopped?"Запись остановлена.":"Активной записи нет; параметры объекта не изменены.";}
            case "mark" -> {if(Boolean.TRUE.equals(DwDiagnostics.status(p.getServer()).get("enabled"))){DwDiagnostics.mark(p.getServerWorld(),note);s.message="Пометка сохранена в текущем сеансе диагностики.";}else{s.success=false;s.message="Пометка не записана: сначала начните запись.";}}
            case "export" -> {s.message="Отчёт записывается. Имя и путь появятся в личном чате после завершения.";DwDiagnostics.export(p.getServer()).thenAccept(path->p.getServer().execute(()->p.sendMessage(Text.literal(path==null?"Не удалось сохранить отчёт; мир продолжает работать.":"Серверный отчёт диагностики: "+path.toAbsolutePath()),false)));}
            case "status" -> s.message="Статус диагностики обновлён.";
            default -> throw new IllegalArgumentException("diagnostics");
        }
    }
    private static boolean snapshot(ServerPlayerEntity p,MechanismLinks.TargetRef t,String note){Map<String,Object> result;
        if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)result=DwDiagnostics.snapshot(p.getServerWorld(),p,rp,Map.of("note",note));else result=DwDiagnostics.snapshot(p.getServerWorld(),p,BlockPos.fromLong(t.root()),Map.of("note",note));return !"NOT_MEASURED_CORRUPT_OBJECT".equals(result.get("result"));
    }
    /** -1 keeps ordinary ray inspection when no tool pin exists. An unavailable pin never falls through. */
    public static int debugSelected(net.minecraft.server.command.ServerCommandSource command){
        if(!(command.getEntity() instanceof ServerPlayerEntity p)||!BuildingTool.mainHeld(p))return -1;
        Session s=SESSIONS.get(p.getUuid());if(s==null||!s.pinned||s.target==null)return -1;
        if(!validTarget(p,s)){command.sendError(Text.literal("Закреплённый экземпляр недоступен или вне досягаемости. /bb debug не выбирает другой объект вместо него."));return 0;}
        var t=currentRef(p,s.target);var view=targetView(p,t);var state=MechanismLinks.inspect(p.getServer(),t);
        String orientation=t.kind()==MechanismLinks.Kind.RP?String.valueOf(view.get("yaw")):p.getWorld().getBlockState(BlockPos.fromLong(t.root())).toString();
        var rules=MechanismRules.list(p.getServer()).stream().filter(r->related(r,t)).toList();
        String lampLinks="";
        if(isLamp(t)){Object raw=LampEditor.view(p,t.instance()).get("connections");if(raw instanceof List<?> rows)lampLinks="; маршруты фонаря="+rows.stream().limit(16).map(row->{if(!(row instanceof Map<?,?> c))return "неизвестная запись";return c.get("lineName")+" направления="+c.get("aToB")+"/"+c.get("bToA")+" "+c.get("source")+" → "+c.get("destination");}).toList();}
        String message="Закреплённый объект ["+view.get("id")+"] "+view.get("name")+"; UUID="+t.instance()+"; registry="+view.get("registry")+"; измерение="+t.dimension()+"; корень="+view.get("position")+"; смещение Y="+view.get("offset")+"; ориентация="+orientation+"; состояние="+state.availability()+", open="+view.getOrDefault("open","не поддерживается")+", импульс="+state.pulseOnly()+", только рычагами="+view.get("leversOnly")+", ожидает="+view.get("pending")+"; действия="+view.get("supportedActions")+"; правил="+rules.size()+", связи="+rules.stream().limit(16).map(r->r.name+" "+r.condition+"/"+r.effect+" источников="+r.sources.size()+" целей="+r.targets.size()).toList()+lampLinks+"; выбор="+boxes(p,t,false).size()+" фрагментов; коллизия игрока="+boxes(p,t,true).size()+" фрагментов";
        command.sendFeedback(()->Text.literal(message),false);
        boolean captured=snapshot(p,t,"/bb debug — закреплённый экземпляр");if(!captured)command.sendError(Text.literal("Снимок содержит ошибку объекта; подробности записаны в диагностику."));return captured?1:0;
    }
    private record Ray(MechanismLinks.TargetRef ref,double distance){}
    public static List<MechanismLinks.TargetRef> candidates(ServerPlayerEntity player){
        ServerWorld world=player.getServerWorld();Vec3d eye=player.getEyePos(),end=eye.add(player.getRotationVec(1).multiply(6));Map<String,Ray> nearest=new HashMap<>();
        java.util.function.BiConsumer<MechanismLinks.TargetRef,Double> accept=(ref,distance)->nearest.merge(ref.key(),new Ray(ref,distance),(a,b)->a.distance()<=b.distance()?a:b);
        for(RpObjectEntity object:dev.dreamwalker.bloodbornerp.object.RpObjectIndex.in(world,new Box(eye,end).expand(1)))if(!object.isRemoved()){
            var ref=MechanismLinks.TargetRef.rp(object);for(Box box:object.selectionBoxes())box.expand(.075).raycast(eye,end).ifPresent(point->accept.accept(ref,point.squaredDistanceTo(eye)));
        }
        Box region=new Box(eye,end).expand(.15);
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(region.minX,region.minY,region.minZ),BlockPos.ofFloored(region.maxX,region.maxY,region.maxZ))){
            if(!world.isChunkLoaded(pos))continue;
            for(var entry:CompositeLedger.get(world).at(CompositeData.cell(pos))){var hit=CompositeRuntime.shape(entry.shape().selection()).raycast(eye,end,pos);if(hit==null)continue;var owner=entry.owner();
                BlockPos resident=CompositeData.pos(owner.root());if(!world.isChunkLoaded(resident)||!owner.equals(VerticalMount.owner(world,resident)))continue;
                accept.accept(new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),CompositeData.pos(owner.root()).asLong(),owner.registryId()),hit.getPos().squaredDistanceTo(eye));
            }
        }
        HitResult nativeHit=player.raycast(6,1,false);
        if(nativeHit instanceof BlockHitResult hit&&nativeHit.getType()==HitResult.Type.BLOCK){BlockPos pos=hit.getBlockPos(),root=SourceLadderRuntime.resolveRoot(world,pos);if(root!=null)pos=root;var owner=VerticalMount.owner(world,pos);
            if(owner!=null)accept.accept(new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),pos.asLong(),owner.registryId()),hit.getPos().squaredDistanceTo(eye));
        }
        return nearest.values().stream().sorted(Comparator.comparingDouble(Ray::distance).thenComparing(hit->hit.ref().key())).limit(32).map(Ray::ref).toList();
    }
    private static Map<String,Object> targetView(ServerPlayerEntity p,MechanismLinks.TargetRef t){Map<String,Object> out=new LinkedHashMap<>();Identifier registry=new Identifier(t.kind()==MechanismLinks.Kind.RP?"bloodborne_rp:"+t.registryId():t.registryId());var entry=t.kind()==MechanismLinks.Kind.ARCHITECTURE?DebugCatalogue.entry(p.getServerWorld().getBlockState(BlockPos.fromLong(t.root()))):DebugCatalogue.entry(registry);out.put("key",t.key());out.put("name",entry==null?t.registryId():entry.name());out.put("id",entry==null?"?????":entry.temporaryId());out.put("kind",t.kind()==MechanismLinks.Kind.RP?"RP-объект":"Блок");out.put("instance",t.instance().toString());out.put("registry",registry.toString());out.put("position",BlockPos.fromLong(t.root()).toShortString());out.put("dimension",t.dimension());out.put("offset",offset(p,t));var status=MechanismLinks.inspect(p.getServer(),t);out.put("openable",status.availability()==MechanismLinks.Availability.LOADED&&!status.pulseOnly());out.put("pulse",status.pulseOnly());out.put("leversOnly",ObjectPolicies.leversOnly(p.getServer(),t));out.put("pending",MechanismLinks.pending(p.getServer(),t));out.put("rotationStep",90);out.put("profile",false);out.put("mount",false);out.put("dogs",false);out.put("pose",false);
        if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp){out.put("rotationStep",45);out.put("entityId",rp.getId());out.put("dogs",rp.supportsDogVisibility());out.put("yaw",rp.getYaw());out.put("lamp",rp.assetId().equals("hunterlamp"));out.put("lever",rp.isMechanism());out.put("open",rp.isOpen());out.put("dogsVisible",rp.dogsVisible());}
        else{BlockState state=p.getWorld().getBlockState(BlockPos.fromLong(t.root()));if(state.getBlock() instanceof PrototypeLadderBlock)out.put("yaw",MathHelper.wrapDegrees(180+45*PrototypeLadderBlock.yaw(state)));else if(state.contains(CompositeRootBlock.ROTATION))out.put("yaw",45*state.get(CompositeRootBlock.ROTATION));else if(state.contains(PrototypeWallBlock.ROTATION))out.put("yaw",45*state.get(PrototypeWallBlock.ROTATION));out.put("rotationStep",BuildingTool.rotationStep(state));out.put("profile",BuildingTool.supports(state,BuildingTool.Action.PROFILE));out.put("mount",state.contains(ThinWindowRootBlock.MOUNT));if(state.contains(ThinWindowRootBlock.MOUNT))out.put("mountValue",state.get(ThinWindowRootBlock.MOUNT).name());if(state.getBlock() instanceof CompositeRootBlock b)out.put("pose",b.spec.openable);if(state.contains(CompositeRootBlock.OPEN))out.put("open",state.get(CompositeRootBlock.OPEN));}
        out.put("anchor",List.of((double)BlockPos.fromLong(t.root()).getX()+.5,(double)BlockPos.fromLong(t.root()).getY(),(double)BlockPos.fromLong(t.root()).getZ()+.5));var selectedBoxes=boxes(p,t,false);var physicalBoxes=boxes(p,t,true);out.put("selectionBoxes",boxView(selectedBoxes.stream().limit(128).toList()));out.put("collisionBoxes",boxView(physicalBoxes.stream().limit(128).toList()));out.put("geometryTruncated",selectedBoxes.size()>128||physicalBoxes.size()>128);out.put("version",version(p,t));out.put("supportedActions",quickActions(p,t).stream().map(Enum::name).toList());if(p.hasPermissionLevel(4)&&Boolean.TRUE.equals(out.get("openable"))){out.put("afterOpen",ObjectPolicies.commands(p.getServer(),t,true));out.put("afterClose",ObjectPolicies.commands(p.getServer(),t,false));}return out;
    }
    private static Map<String,Object> draftView(ServerPlayerEntity p,Draft draft){var out=new LinkedHashMap<String,Object>();out.put("id","draft");out.put("draft",true);out.put("name",draft.name);out.put("condition",draft.condition);out.put("effect",draft.effect);out.put("sources",draft.sources.values().stream().map(t->memberView(p,t)).toList());out.put("targets",draft.targets.values().stream().map(t->memberView(p,t)).toList());out.put("active",0);out.put("total",draft.sources.size());out.put("order",0);return out;}
    private static Map<String,Object> memberView(ServerPlayerEntity p,MechanismLinks.TargetRef t){var out=referenceView(p,t);out.put("type",out.get("id"));out.put("pending",MechanismLinks.pending(p.getServer(),t));out.put("pulseOnly",MechanismLinks.inspect(p.getServer(),t).pulseOnly());return out;}
    private static Map<String,Object> referenceView(ServerPlayerEntity p,MechanismLinks.TargetRef t){
        if(near(p,t))return new LinkedHashMap<>(targetView(p,currentRef(p,t)));
        var out=new LinkedHashMap<String,Object>();Identifier id=new Identifier(t.kind()==MechanismLinks.Kind.RP?"bloodborne_rp:"+t.registryId():t.registryId());var entry=DebugCatalogue.entry(id);BlockPos pos=BlockPos.fromLong(t.root());out.put("key",t.key());out.put("name",entry==null?t.registryId():entry.name());out.put("id",entry==null?"?????":entry.temporaryId());out.put("registry",id.toString());out.put("instance",t.instance().toString());out.put("dimension",t.dimension());out.put("position",pos.toShortString());out.put("anchor",List.of(pos.getX()+.5,(double)pos.getY(),pos.getZ()+.5));out.put("loaded",false);return out;
    }
    private static List<Map<String,Object>> connectionViews(ServerPlayerEntity p,Session s,List<MechanismRules.Rule> related){
        var out=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();var focus=linkContext(p)&&s.source!=null?s.source:s.target;
        if(focus==null)return out;
        for(var rule:related)for(var source:rule.sources.values())for(var target:rule.targets.values()){
            if(!source.key().equals(focus.key())&&!target.key().equals(focus.key()))continue;var other=source.key().equals(focus.key())?target:source;if(!seen.add(other.key()))continue;
            out.add(Map.of("target",referenceView(p,other),"direction",source.key().equals(focus.key())?"out":"in","label",rule.name+" · "+rule.effect.name(),"loaded",near(p,other),"position",referenceView(p,other).get("anchor")));if(out.size()>=64)return out;
        }
        if(isLamp(focus)){var graph=LampEditor.view(p,focus.instance());Object raw=graph.get("connections");if(raw instanceof List<?> list)for(Object item:list){if(!(item instanceof Map<?,?> c))continue;Object rawA=c.get("source"),rawB=c.get("destination");if(!(rawA instanceof Map<?,?> a)||!(rawB instanceof Map<?,?> b))continue;var other=focus.instance().toString().equals(String.valueOf(a.get("entityUuid")))?b:a;String uuid=String.valueOf(other.get("entityUuid"));Object position=other.get("position");var ref=new LinkedHashMap<String,Object>();ref.put("name",String.valueOf(other.get("name")));ref.put("id","91063");ref.put("instance",uuid);ref.put("dimension",String.valueOf(other.get("dimension")));ref.put("anchor",position);ref.put("position",position);ref.put("loaded",Boolean.TRUE.equals(other.get("loaded")));var row=new LinkedHashMap<String,Object>();row.put("target",ref);boolean atA=focus.instance().toString().equals(String.valueOf(a.get("entityUuid")));boolean outward=Boolean.TRUE.equals(c.get(atA?"aToB":"bToA"));boolean inward=Boolean.TRUE.equals(c.get(atA?"bToA":"aToB"));row.put("direction",outward&&inward?"both":outward?"out":"in");row.put("loaded",Boolean.TRUE.equals(other.get("loaded")));row.put("label",String.valueOf(c.get("lineName")));out.add(row);if(out.size()>=64)break;}}
        return out;
    }
    private static void send(ServerPlayerEntity p,Session s,boolean open){if(!ServerPlayNetworking.canSend(p,VIEW))return;
        s.candidates=s.candidates.stream().filter(t->near(p,t)).map(t->currentRef(p,t)).toList();if(s.target!=null&&near(p,s.target))s.target=currentRef(p,s.target);
        var view=new LinkedHashMap<String,Object>();view.put("openMenu",open);view.put("editable",BuildPermissions.canEdit(p));view.put("admin",p.hasPermissionLevel(4));view.put("operator",p.hasPermissionLevel(2));view.put("action",BuildingTool.action(p.getMainHandStack()).name());view.put("actionLabel",BuildingTool.action(p.getMainHandStack()).label);view.put("step",step(p.getMainHandStack()));view.put("pinned",s.pinned);view.put("message",s.message);view.put("success",s.success);view.put("error",s.success?"":s.message);view.put("ackRequestId",s.ackRequest);view.put("undoCount",s.history.size());view.put("formVersion",formVersion(p,s));view.put("globalRules",s.globalRules);
        if(s.target!=null&&near(p,s.target))view.put("target",targetView(p,s.target));if(s.source!=null)view.put("source",referenceView(p,currentRef(p,s.source)));view.put("candidates",s.candidates.stream().limit(32).map(t->targetView(p,t)).toList());
        var related=MechanismRules.list(p.getServer()).stream().filter(r->s.globalRules||s.target!=null&&(r.sources.containsKey(s.target.instance())||r.targets.containsKey(s.target.key()))||linkContext(p)&&s.source!=null&&(r.sources.containsKey(s.source.instance())||r.targets.containsKey(s.source.key()))).toList();
        int pages=Math.max(1,(related.size()+63)/64);s.rulePage=Math.min(s.rulePage,pages-1);var visible=related.stream().skip(s.rulePage*64L).limit(64).toList();view.put("rulePage",s.rulePage);view.put("rulePages",pages);view.put("ruleTotal",related.size());view.put("rules",visible.stream().map(r->Map.of("id",r.id.toString(),"order",r.order,"name",r.name,"condition",r.condition.name(),"effect",r.effect.name(),"incomplete",r.incomplete)).toList());
        if(s.draft!=null)view.put("rule",draftView(p,s.draft));else if(ruleAllowed(p,s))view.put("rule",MechanismRules.view(p.getServer(),MechanismRules.rule(p.getServer(),s.rule),s.affectedPage));
        view.put("connections",connectionViews(p,s,related));view.put("lamps",LampEditor.view(p,isLamp(s.source)?s.source.instance():isLamp(s.target)?s.target.instance():null,s.lampPage));if(isLamp(s.target))view.put("currentLamp",LampEditor.view(p,s.target.instance()));view.put("lampLine",p.getMainHandStack().getOrCreateNbt().getString("BuilderLampLine"));view.put("lampBoth",!p.getMainHandStack().getOrCreateNbt().getBoolean("BuilderLampOneWay"));view.put("diagnostics",DwDiagnostics.status(p.getServer()));
        String json=JSON.toJson(view);if(json.length()>MAX_VIEW){notice(p,"Список слишком велик; выберите отдельное правило.");return;}var buf=PacketByteBufs.create();buf.writeString(json,MAX_VIEW);DwDiagnostics.network("server","send",VIEW.toString(),buf.readableBytes());ServerPlayNetworking.send(p,VIEW,buf);
    }
    private static MechanismLinks.TargetRef currentRef(ServerPlayerEntity p,MechanismLinks.TargetRef t){if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return MechanismLinks.TargetRef.rp(rp);return t;}
    private static String text(JsonObject q,String key,int limit){String value=q.has(key)?q.get(key).getAsString():"";if(value.length()>limit)throw new IllegalArgumentException(key);return value;}
    private static void notice(ServerPlayerEntity p,String text){if(text!=null&&!text.isBlank())p.sendMessage(Text.literal(text),true);}
}
