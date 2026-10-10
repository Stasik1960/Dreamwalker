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
import net.minecraft.nbt.NbtCompound;
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
    private static final class Session { MechanismLinks.TargetRef target,source;boolean pinned,globalRules,accepted;long lastSequence;int lastClick=-100,lampPage,rulePage,affectedPage;UUID rule;MechanismRules.Rule draft;String ruleVersion="none";final Set<UUID> editable=new LinkedHashSet<>();final LinkedHashSet<String> requests=new LinkedHashSet<>();final BuilderUndoHistory<String,Geometry> undo=new BuilderUndoHistory<>();List<MechanismLinks.TargetRef> candidates=List.of();String message="",requestId=""; }
    private record Geometry(MechanismLinks.TargetRef target,BlockState state,NbtCompound payload,double offset,Vec3d position,float yaw,float scale,boolean dogs,boolean open) {}
    private BuilderServer(){}
    public static void initialize(){if(initialized)return;initialized=true;
        ServerPlayNetworking.registerGlobalReceiver(REQUEST,(server,player,handler,buf,sender)->{int size=buf.readableBytes();String raw;try{raw=buf.readString(MAX_REQUEST);}catch(RuntimeException invalid){return;}DwDiagnostics.network("server","receive",REQUEST.toString(),size);var pending=REQUEST_PENDING.computeIfAbsent(player.getUuid(),id->new java.util.concurrent.atomic.AtomicInteger());if(pending.incrementAndGet()>4){pending.decrementAndGet();return;}server.execute(()->{try{request(player,JsonParser.parseString(raw).getAsJsonObject());}catch(RuntimeException invalid){DwDiagnostics.error(player.getServerWorld(),"UNASSIGNED",player.getUuidAsString(),player.getBlockPos(),"BUILDER_REQUEST","Rejected invalid builder request",invalid);Session rejected=session(player);rejected.accepted=false;rejected.message="Некорректные параметры меню; черновик не сохранён.";chat(player,rejected.message);send(player,rejected,false);}finally{pending.decrementAndGet();}});});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clearPlayer(handler.player.getUuid()));ServerLifecycleEvents.SERVER_STOPPED.register(server->{SESSIONS.clear();REQUEST_PENDING.clear();});
    }
    private static void clearPlayer(UUID id){SESSIONS.remove(id);REQUEST_PENDING.remove(id);}
    private static Session session(ServerPlayerEntity player){return SESSIONS.computeIfAbsent(player.getUuid(),i->new Session());}
    public static void open(ServerPlayerEntity player){if(!BuildingTool.mainHeld(player))return;dev.dreamwalker.bloodbornerp.lamp.LampService.cancelTravel(player);Session s=session(player);s.candidates=candidates(player);if(s.target==null&&!s.candidates.isEmpty())selectTarget(player,s,s.candidates.get(0));restoreRule(player,s);send(player,s,true);}
    private static void restoreRule(ServerPlayerEntity p,Session s){if(s.draft!=null)return;var n=p.getMainHandStack().getOrCreateNbt();if(n.containsUuid("BuilderRule")){UUID id=n.getUuid("BuilderRule");if(allowedRule(p,s,id)){var r=MechanismRules.rule(p.getServer(),id);if(r!=null){s.rule=id;s.draft=MechanismRules.copy(r);s.ruleVersion=MechanismRules.version(r);}}}}
    private static boolean allowedRule(ServerPlayerEntity p,Session s,UUID id){var r=MechanismRules.rule(p.getServer(),id);return s.draft!=null&&s.draft.id.equals(id)||r!=null&&(s.globalRules&&p.hasPermissionLevel(2)||s.target!=null&&(r.targets.containsKey(s.target.key())||r.sources.containsKey(s.target.instance())));}
    private static void request(ServerPlayerEntity p,JsonObject q){if(!BuildingTool.isHeld(p))return;Session s=session(p);String op=text(q,"op",40);s.accepted=false;
        s.requestId=text(q,"requestId",64);if(!s.requestId.isBlank()){if(!s.requests.add(s.requestId)){s.message="Этот запрос уже обработан.";send(p,s,false);return;}if(s.requests.size()>128)s.requests.remove(s.requests.iterator().next());}
        if(q.has("sequence")){long sequence=q.get("sequence").getAsLong();if(sequence<=s.lastSequence){s.message="Повторный или устаревший запрос отклонён.";send(p,s,false);return;}s.lastSequence=sequence;}
        if(!requestMatches(p,s,q,op)){s.message="Данные изменились. Перечитайте форму; черновик сохранён.";chat(p,s.message);send(p,s,false);return;}
        if(op.equals("click")){click(p,s,q.has("reverse")&&q.get("reverse").getAsBoolean());return;}if(!BuildingTool.mainHeld(p)){notice(p,"Перенесите строительный инструмент в основную руку.");return;}
        if(op.equals("open")){open(p);return;}if(op.equals("refresh")){send(p,s,false);return;}
        if(op.equals("hud_refresh")){send(p,s,false,true);return;}
        if(op.equals("quick_cancel")){cancelSelection(p,s);send(p,s,false);return;}
        if(!BuildPermissions.canEdit(p)){notice(p,"Редактирование доступно в Creative или оператору.");send(p,s,false);return;}
        ItemStack tool=p.getMainHandStack();
        switch(op){
            case "undo" -> undo(p,s);
            case "quick_cycle" -> {int direction=q.has("direction")?q.get("direction").getAsInt():1;if(direction!=1&&direction!=-1)throw new IllegalArgumentException("direction");var quick=quickModes(p,s.target);int index=quick.indexOf(BuildingTool.action(tool));BuildingTool.Action next=quick.get(Math.floorMod(Math.max(0,index)+direction,quick.size()));setMode(p,next);s.message="Быстрый режим: "+next.label;}
            case "quick_step" -> {int direction=q.has("direction")?q.get("direction").getAsInt():1;if(direction!=1&&direction!=-1)throw new IllegalArgumentException("direction");double[] values={.0625,.125,.25,1};double current=step(tool);int index=1;for(int i=0;i<values.length;i++)if(values[i]==current){index=i;break;}double next=values[Math.floorMod(index+direction,values.length)];tool.getOrCreateNbt().putDouble("BuilderStep",next);s.message="Шаг высоты: "+next;}
            case "mode" -> {BuildingTool.Action action=BuildingTool.Action.valueOf(text(q,"action",40));if(!supported(p,s.target,action)&&!Set.of(BuildingTool.Action.RULE_SOURCE,BuildingTool.Action.RULE_TARGET,BuildingTool.Action.LAMP_SOURCE,BuildingTool.Action.LAMP_TARGET,BuildingTool.Action.DIAGNOSTICS).contains(action)){s.message="Тип не поддерживает действие. Возврат в Выбор.";setMode(p,BuildingTool.Action.SELECT);}else{setMode(p,action);s.message="Действие выбрано: "+action.label;}if(action==BuildingTool.Action.LAMP_TARGET&&q.has("line")){tool.getOrCreateNbt().putString("BuilderLampLine",text(q,"line",64));tool.getOrCreateNbt().putBoolean("BuilderLampOneWay",!q.get("both").getAsBoolean());}}
            case "step" -> {double step=q.get("value").getAsDouble();if(step!=.0625&&step!=.125&&step!=.25&&step!=1)throw new IllegalArgumentException("step");tool.getOrCreateNbt().putDouble("BuilderStep",step);}
            case "select" -> {String key=text(q,"key",256);s.candidates=candidates(p);var chosen=s.candidates.stream().filter(t->t.key().equals(key)).findFirst().orElse(null);if(chosen==null)s.message="Объект больше не находится под прицелом; прежний выбор сохранён.";else selectTarget(p,s,chosen);}
            case "pick" -> {s.candidates=candidates(p);if(s.candidates.isEmpty())s.message="Под прицелом нет объектов мода; прежний выбор сохранён.";else{int index=-1;for(int i=0;i<s.candidates.size();i++)if(s.target!=null&&s.target.key().equals(s.candidates.get(i).key())){index=i;break;}selectTarget(p,s,s.candidates.get((index+1)%s.candidates.size()));}}
            case "cancel_selection" -> cancelSelection(p,s);
            case "object" -> objectForm(p,s,q);
            case "rule_create" -> {if(s.draft==null||s.draft.order!=0){s.draft=MechanismRules.draft(text(q,"name",80));s.rule=s.draft.id;s.ruleVersion="none";}s.message="Черновик: выберите источники и цели, затем Сохранить. В мир ничего не записано.";}
            case "rule_select" -> {UUID id=UUID.fromString(text(q,"id",40));if(allowedRule(p,s,id)){var r=MechanismRules.rule(p.getServer(),id);if(r!=null){s.rule=id;s.draft=MechanismRules.copy(r);s.ruleVersion=MechanismRules.version(r);tool.getOrCreateNbt().putUuid("BuilderRule",id);s.message="Правило прочитано. Состав и имя изменяются в черновике.";}}}
            case "rule_apply" -> {if(ruleAllowed(p,s)&&s.draft!=null){s.draft.name=text(q,"name",80);s.draft.condition=MechanismRules.Condition.valueOf(text(q,"condition",10));s.draft.effect=MechanismRules.Effect.valueOf(text(q,"effect",10));var conflicts=MechanismRules.conflicts(p.getServer(),s.draft);boolean confirmed=q.has("confirmConflicts")&&q.get("confirmConflicts").getAsBoolean();boolean done=MechanismRules.saveDraft(p,s.draft,confirmed);s.accepted=done;if(done){var saved=MechanismRules.rule(p.getServer(),s.draft.id);s.draft=MechanismRules.copy(saved);s.ruleVersion=MechanismRules.version(saved);tool.getOrCreateNbt().putUuid("BuilderRule",saved.id);s.message="Правило сохранено; игровых срабатываний не было.";}else s.message=!conflicts.isEmpty()&&!confirmed?"Конфликт: "+String.join("; ",conflicts)+". Подтвердите порядок исполнения в форме.":"Не сохранено: нужны подходящие источники и цели, права и поддерживаемое действие.";chat(p,s.message);}}
            case "rule_remove_member" -> {if(ruleAllowed(p,s)&&s.draft!=null){String key=text(q,"key",256);if(q.get("source").getAsBoolean())s.draft.sources.entrySet().removeIf(e->e.getValue().key().equals(key));else s.draft.targets.remove(key);s.message="Участник убран из черновика. Изменение всех пар группы вступит в силу после Сохранить.";}}
            case "rule_discard" -> {s.draft=null;s.rule=null;s.ruleVersion="none";s.message="Черновик отменён; сохранённые правила не изменены.";}
            case "rules_scope" -> {s.globalRules=p.hasPermissionLevel(2)&&q.has("global")&&q.get("global").getAsBoolean();s.rulePage=0;s.rule=null;s.draft=null;}
            case "rule_reset" -> {if(ruleAllowed(p,s)&&q.has("confirmed")&&q.get("confirmed").getAsBoolean()){MechanismRules.reset(p,s.rule);s.message="Общие признаки сброшены; группы «Все» пересчитаны.";}}
            case "rule_delete" -> {if(ruleAllowed(p,s)&&q.has("confirmed")&&q.get("confirmed").getAsBoolean()){boolean done=MechanismRules.delete(p,s.rule);s.rule=null;s.draft=null;tool.getOrCreateNbt().remove("BuilderRule");s.message=done?"Правило и все его пары удалены; объекты сохранены.":"Правило уже отсутствует.";chat(p,s.message);}}
            case "commands" -> {if(validTarget(p,s)&&p.hasPermissionLevel(4)){List<String> lines=new ArrayList<>();for(JsonElement line:q.getAsJsonArray("commands")){if(lines.size()>=32)throw new IllegalArgumentException("commands");lines.add(line.getAsString());}s.accepted=ObjectPolicies.setCommands(p,s.target,q.get("open").getAsBoolean(),lines);s.message=s.accepted?"Список команд сохранён.":"Нужен действительно открываемый объект и права администратора уровня 4.";}}
            case "rule_page" -> {s.rulePage=Math.max(0,Math.min(63,q.get("page").getAsInt()));}
            case "affected_page" -> {s.affectedPage=Math.max(0,Math.min(63,q.get("page").getAsInt()));}
            case "lamp_page" -> {s.lampPage=Math.max(0,Math.min(1023,q.get("page").getAsInt()));}
            case "lamp_parameters" -> {String line=text(q,"line",64);if(line.isBlank())throw new IllegalArgumentException("line");tool.getOrCreateNbt().putString("BuilderLampLine",line);tool.getOrCreateNbt().putBoolean("BuilderLampOneWay",!q.get("both").getAsBoolean());s.accepted=true;s.message="Параметры следующего назначения сохранены.";}
            case "lamp" -> lampForm(p,s,q);
            case "diagnostics" -> diagnostics(p,s,q);
            default -> throw new IllegalArgumentException("Unknown builder operation");
        }
        if(Set.of("commands","lamp").contains(op))chat(p,s.message);else notice(p,s.message);p.getInventory().markDirty();send(p,s,false);
    }
    private static void cancelSelection(ServerPlayerEntity p,Session s){s.target=null;s.source=null;s.pinned=false;s.rule=null;s.draft=null;var n=p.getMainHandStack().getOrCreateNbt();n.remove("BuilderRule");n.remove("BuilderLever");LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.CANCEL,null,null,"","",true));s.message="Рабочий выбор отменён; готовые связи сохранены.";}
    private static boolean ruleAllowed(ServerPlayerEntity p,Session s){return s.rule!=null&&allowedRule(p,s,s.rule);}
    private static void setMode(ServerPlayerEntity p,BuildingTool.Action action){p.getMainHandStack().getOrCreateNbt().putInt("BuilderAction",action.ordinal());p.getInventory().markDirty();}
    private static List<BuildingTool.Action> quickModes(ServerPlayerEntity p,MechanismLinks.TargetRef target){return java.util.stream.Stream.of(BuildingTool.Action.SELECT,BuildingTool.Action.ROTATE,BuildingTool.Action.UP,BuildingTool.Action.PROFILE,BuildingTool.Action.DOGS,BuildingTool.Action.MOUNT,BuildingTool.Action.LINK,BuildingTool.Action.UNLINK,BuildingTool.Action.CONNECTIONS).filter(a->supported(p,target,a)).toList();}
    private static boolean supported(ServerPlayerEntity p,MechanismLinks.TargetRef target,BuildingTool.Action action){
        if(Set.of(BuildingTool.Action.SELECT,BuildingTool.Action.CONNECTIONS).contains(action))return true;
        if(target==null)return action==BuildingTool.Action.LINK||action==BuildingTool.Action.UNLINK;
        if(geometry(p,target)==null)return false;
        if(target.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(target.instance()) instanceof RpObjectEntity rp)return switch(action){case ROTATE,UP,DOWN->true;case DOGS->rp.supportsDogVisibility();case LINK,UNLINK->rp.isMechanism()||rp.canBeLinked()||rp.assetId().equals("hunterlamp");default->false;};
        BlockState state=p.getWorld().getBlockState(BlockPos.fromLong(target.root()));return switch(action){case ROTATE,UP,DOWN->true;case PROFILE->state.contains(CompositeRootBlock.PROFILE)||state.getBlock() instanceof PrototypeLadderBlock||state.getBlock() instanceof PrototypeWallBlock&&!PrototypeWallBlock.diagonalPost(state);case MOUNT->state.contains(ThinWindowRootBlock.MOUNT);case POSE->state.getBlock() instanceof CompositeRootBlock b&&b.spec.openable;case LINK,UNLINK->MechanismLinks.inspect(p.getServer(),target).availability()==MechanismLinks.Availability.LOADED;default->false;};
    }
    private static void selectTarget(ServerPlayerEntity p,Session s,MechanismLinks.TargetRef target){
        s.target=currentRef(p,target);s.pinned=true;s.rulePage=0;if(s.draft==null)s.rule=null;
        var action=BuildingTool.action(p.getMainHandStack());if(!supported(p,s.target,action)&&!Set.of(BuildingTool.Action.RULE_SOURCE,BuildingTool.Action.RULE_TARGET,BuildingTool.Action.LAMP_SOURCE,BuildingTool.Action.LAMP_TARGET,BuildingTool.Action.DIAGNOSTICS).contains(action)){setMode(p,BuildingTool.Action.SELECT);s.message="Экземпляр выбран. Прежний режим не поддерживается: Выбор.";}else s.message="Экземпляр закреплён. Следующий ЛКМ выполняет действие.";
    }
    private static String version(ServerPlayerEntity p,MechanismLinks.TargetRef target){
        Geometry g=target==null?null:geometry(p,target);if(g==null)return "unavailable";
        String data=String.valueOf(g.state())+"|"+g.offset()+"|"+g.yaw()+"|"+g.dogs()+"|"+g.position()+"|"+ObjectPolicies.leversOnly(p.getServer(),target)+"|"+ObjectPolicies.commands(p.getServer(),target,true)+"|"+ObjectPolicies.commands(p.getServer(),target,false);
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static boolean requestMatches(ServerPlayerEntity p,Session s,JsonObject q,String op){
        if(op.equals("lamp")&&(!q.has("expectedLampVersion")||!lampVersion(p,s).equals(text(q,"expectedLampVersion",80))))return false;
        if(Set.of("object","commands","click","lamp","diagnostics").contains(op)&&q.has("expectedTarget")){if(s.target==null||!s.target.instance().toString().equals(text(q,"expectedTarget",40)))return false;if(Set.of("object","commands","click").contains(op)&&q.has("expectedVersion")&&!version(p,s.target).equals(text(q,"expectedVersion",80)))return false;}
        if(Set.of("object","commands").contains(op)&&(!q.has("expectedTarget")||!q.has("expectedVersion")))return false;
        if(Set.of("rule_apply","rule_reset","rule_delete","rule_remove_member").contains(op)&&s.draft!=null){var actual=MechanismRules.rule(p.getServer(),s.draft.id);if(!s.ruleVersion.equals(MechanismRules.version(actual)))return false;if(!q.has("expectedRule")||!s.draft.id.toString().equals(text(q,"expectedRule",40)))return false;if(!q.has("expectedRuleVersion")||!s.ruleVersion.equals(text(q,"expectedRuleVersion",80)))return false;}
        return true;
    }
    private static String lampVersion(ServerPlayerEntity p,Session s){UUID id=s.source!=null&&s.source.registryId().equals("hunterlamp")?s.source.instance():s.target!=null&&s.target.registryId().equals("hunterlamp")?s.target.instance():null;String data=JSON.toJson(LampEditor.view(p,id,s.lampPage));try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static void click(ServerPlayerEntity p,Session s,boolean reverse){
        if(!BuildingTool.mainHeld(p)){notice(p,"Перенесите строительный инструмент в основную руку.");return;}if(!BuildPermissions.canEdit(p)){notice(p,"Редактирование доступно в Creative или оператору.");return;}
        if(s.lastClick==p.getServer().getTicks())return;s.lastClick=p.getServer().getTicks();List<MechanismLinks.TargetRef> hits=candidates(p);s.candidates=hits;BuildingTool.Action action=BuildingTool.action(p.getMainHandStack());boolean scene=Set.of(BuildingTool.Action.SELECT,BuildingTool.Action.LINK,BuildingTool.Action.UNLINK,BuildingTool.Action.RULE_SOURCE,BuildingTool.Action.RULE_TARGET,BuildingTool.Action.LAMP_SOURCE,BuildingTool.Action.LAMP_TARGET).contains(action);var target=scene?hits.isEmpty()?null:hits.get(0):s.target;
        if(!scene&&s.target==null){if(!hits.isEmpty()){selectTarget(p,s,hits.get(0));notice(p,s.message);send(p,s,false);}else notice(p,"Сначала выберите подходящий объект мода.");return;}
        if(target==null){notice(p,"Под прицелом нет подходящего объекта мода. Разрушение отменено.");return;}
        if(!near(p,target)){s.message="Закреплённый экземпляр недоступен или вне досягаемости. Выберите другой средней кнопкой или X.";notice(p,s.message);send(p,s,false);return;}
        if(action!=BuildingTool.Action.SELECT&&!BuildPermissions.canEdit(p.getWorld(),p,targetPosition(p,target))){s.message="Права редактирования выбранного экземпляра не сохранены.";notice(p,s.message);send(p,s,false);return;}
        if(!Set.of(BuildingTool.Action.RULE_SOURCE,BuildingTool.Action.RULE_TARGET,BuildingTool.Action.LAMP_SOURCE,BuildingTool.Action.LAMP_TARGET,BuildingTool.Action.DIAGNOSTICS).contains(action)&&!supported(p,target,action)){setMode(p,BuildingTool.Action.SELECT);s.message="Этот тип не поддерживает действие. Возврат в Выбор.";notice(p,s.message);send(p,s,false);return;}
        if(action==BuildingTool.Action.SELECT){selectTarget(p,s,target);notice(p,s.message);send(p,s,false);return;}
        boolean done=true;Geometry before=undoable(action)||action==BuildingTool.Action.POSE?geometry(p,target):null;
        if(action==BuildingTool.Action.SELECT||action==BuildingTool.Action.CONNECTIONS){s.message="Объект выбран. ПКМ — меню.";}
        else if(action==BuildingTool.Action.UP||action==BuildingTool.Action.DOWN){double current=offset(p,target),step=step(p.getMainHandStack());boolean up=action==BuildingTool.Action.UP;if(reverse)up=!up;done=setOffset(p,target,current+(up?step:-step));s.message=done?"Монтажное смещение: "+offset(p,target):"Не удалось изменить высоту в пределах мира.";}
        else if(action==BuildingTool.Action.RULE_SOURCE||action==BuildingTool.Action.RULE_TARGET){done=ruleAllowed(p,s)&&MechanismRules.draftAdd(p,s.draft,target,action==BuildingTool.Action.RULE_SOURCE);s.message=done?"Участник добавлен в черновик. Сохраните полный состав в Связях.":"Сначала создайте черновик и выберите подходящий источник/цель.";}
        else if(action==BuildingTool.Action.LAMP_SOURCE){var result=LampEditor.selectSource(p,target.kind()==MechanismLinks.Kind.RP?target.instance():null);s.message=result.reason();done=result.success();if(done){s.source=target;s.target=target;s.pinned=true;}}
        else if(action==BuildingTool.Action.LAMP_TARGET){String line=p.getMainHandStack().getOrCreateNbt().getString("BuilderLampLine");if(line.isBlank())line="Охотничья линия";boolean both=!p.getMainHandStack().getOrCreateNbt().getBoolean("BuilderLampOneWay");var result=LampEditor.edit(p,new LampEditor.Request(LampEditor.Action.CONNECT,null,target.kind()==MechanismLinks.Kind.RP?target.instance():null,line,"",both));s.message=result.reason();done=result.success();}
        else if(action==BuildingTool.Action.DIAGNOSTICS){if(p.hasPermissionLevel(2)){done=snapshot(p,target,"Снимок инструментом");s.message=done?"Снимок сохранён. Экспорт: Диагностика → Экспорт ZIP.":"Снимок не прочитан; см. ошибки диагностики.";chat(p,s.message);}else{s.message="Диагностика доступна оператору уровня 2.";done=false;}}
        else if(action==BuildingTool.Action.LINK||action==BuildingTool.Action.UNLINK){done=simpleLink(p,s,target,action==BuildingTool.Action.UNLINK);}
        else if(target.kind()==MechanismLinks.Kind.RP){Entity entity=p.getServerWorld().getEntity(target.instance());done=entity instanceof RpObjectEntity rp&&MechanismBuilder.entity(p,p.getMainHandStack(),rp,reverse).isAccepted();s.message=done?"Изменение применено: "+action.label:"Этот RP-объект не поддерживает действие или проверка отклонена: "+action.label;}
        else{done=BuildingTool.applyBlock(p,BlockPos.fromLong(target.root()),action,reverse).isAccepted();s.message=done?"Изменение применено: "+action.label:"Этот блок не поддерживает действие или изменение отклонено: "+action.label;}
        if(done&&before!=null)recordUndo(p,s,before,action==BuildingTool.Action.POSE?null:action==BuildingTool.Action.UP||action==BuildingTool.Action.DOWN?"offset":action==BuildingTool.Action.DOGS?"dogs":"geometry");notice(p,s.message);send(p,s,false);
    }
    private static boolean simpleLink(ServerPlayerEntity p,Session s,MechanismLinks.TargetRef target,boolean unlink){
        Entity entity=target.kind()==MechanismLinks.Kind.RP?p.getServerWorld().getEntity(target.instance()):null;
        if(entity instanceof RpObjectEntity rp&&(rp.isMechanism()||rp.assetId().equals("hunterlamp")&&(s.source==null||!s.source.registryId().equals("hunterlamp")||s.source.instance().equals(target.instance())))){
            if(!BuildPermissions.canEdit(p.getWorld(),p,rp.getBlockPos())){s.message="Нет прав редактирования источника.";return false;}
            if(rp.assetId().equals("hunterlamp")){var result=LampEditor.selectSource(p,rp.getUuid());if(!result.success()){s.message=result.reason();return false;}}
            else MechanismBuilder.entity(p,p.getMainHandStack(),rp);
            s.source=target;s.target=target;s.pinned=true;s.message=(rp.isMechanism()?"Рычаг":"Фонарь")+" — источник закреплён. ЛКМ по целям; X завершает выбор.";return true;
        }
        if(s.source==null){s.message="Сначала ЛКМ по рычагу или фонарю — источнику.";return false;}
        if(s.source.registryId().equals("hunterlamp")){
            if(!(entity instanceof RpObjectEntity rp)||!rp.assetId().equals("hunterlamp")){s.message="Источник — фонарь: нужна цель 91063.";return false;}
            String line=p.getMainHandStack().getOrCreateNbt().getString("BuilderLampLine");if(line.isBlank())line="Охотничья линия";boolean both=!p.getMainHandStack().getOrCreateNbt().getBoolean("BuilderLampOneWay");
            if(!unlink&&lampConnected(p,s.source.instance(),target.instance(),line)){s.message="Фонари уже связаны в этой линии. Направление меняется отдельно в Телепортах.";return true;}
            var result=LampEditor.edit(p,new LampEditor.Request(unlink?LampEditor.Action.UNLINK:LampEditor.Action.CONNECT,s.source.instance(),target.instance(),line,"",both));s.message=result.reason();if(unlink)chat(p,s.message);return result.success();
        }
        var source=MechanismBuilder.selected(p,p.getMainHandStack());if(source==null){s.message="Источник удалён, выгружен или права изменились; выберите его снова.";return false;}
        var status=MechanismLinks.inspect(p.getServer(),target);if(status.availability()!=MechanismLinks.Availability.LOADED||!BuildPermissions.canEdit(p.getWorld(),p,entity==null?BlockPos.fromLong(target.root()):entity.getBlockPos())){s.message="Нужна подходящая открываемая или импульсная цель с правами редактирования.";return false;}
        var simple=MechanismRules.rule(p.getServer(),source.getUuid());if(simple!=null&&(simple.sources.size()!=1||!simple.sources.containsKey(source.getUuid()))){s.message="Групповое правило: измените состав и подтвердите все затрагиваемые пары в Связях.";return false;}boolean exists=simple!=null&&simple.targets.containsKey(target.key());if(!unlink&&exists){s.message="Уже связано. Подключено целей: "+MechanismLinks.targets(source).size();return true;}
        boolean done=unlink?(entity instanceof RpObjectEntity rp?source.removeLink(rp.getUuid()):MechanismLinks.unlink(source,target.key())):MechanismLinks.link(source,target);s.message=done?(unlink?"Удалена только выбранная простая связь.":"Связано. Подключено целей: "+MechanismLinks.targets(source).size()+"; срабатывание рычага через 70 тиков."):"Связь отсутствует либо сервер отклонил изменение.";if(unlink)chat(p,s.message);return done;
    }
    private static boolean lampConnected(ServerPlayerEntity p,UUID source,UUID target,String line){for(int page=0;page<64;page++){JsonObject info=JSON.toJsonTree(LampEditor.view(p,source,page)).getAsJsonObject();if(info.has("connections"))for(var e:info.getAsJsonArray("connections")){var c=e.getAsJsonObject();if(c.get("lineName").getAsString().equals(line)&&(c.getAsJsonObject("source").get("entityUuid").getAsString().equals(target.toString())||c.getAsJsonObject("destination").get("entityUuid").getAsString().equals(target.toString())))return true;}if(!info.has("pageCount")||page+1>=info.get("pageCount").getAsInt())break;}return false;}
    public static double step(ItemStack stack){double value=stack.getOrCreateNbt().getDouble("BuilderStep");return value==.0625||value==.125||value==.25||value==1?value:.125;}
    private static boolean validTarget(ServerPlayerEntity p,Session s){return s.target!=null&&near(p,s.target)&&BuildPermissions.canEdit(p.getWorld(),p,targetPosition(p,s.target));}
    private static BlockPos targetPosition(ServerPlayerEntity p,MechanismLinks.TargetRef t){Entity e=t.kind()==MechanismLinks.Kind.RP?p.getServerWorld().getEntity(t.instance()):null;return e==null?BlockPos.fromLong(t.root()):e.getBlockPos();}
    private static double reach(ServerPlayerEntity p){return p.interactionManager.getGameMode().isCreative()?5:4.5;}
    private static boolean near(ServerPlayerEntity p,MechanismLinks.TargetRef t){return geometry(p,t)!=null&&boxes(p,t,false).stream().anyMatch(b->distance(p.getEyePos(),b)<=reach(p)*reach(p));}
    /** Resolve just the selected owner's authored footprint; never walk the world's ledger. */
    private static List<Box> boxes(ServerPlayerEntity p,MechanismLinks.TargetRef t,boolean physical){
        if(t==null||!t.dimension().equals(p.getWorld().getRegistryKey().getValue().toString()))return List.of();
        if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return (physical?rp.activePhysicalBoxes():rp.selectionBoxes()).stream().limit(128).toList();
        BlockPos root=BlockPos.fromLong(t.root());if(!p.getWorld().isChunkLoaded(root)||!(VerticalMount.loadedEntity(p.getWorld(),root) instanceof CompositeBlockEntity own)||own.resident()==null||!own.resident().instanceId().equals(t.instance()))return List.of();
        var object=CompositeRuntime.instance(p.getWorld(),root,p.getWorld().getBlockState(root),t.instance(),own.payload());List<Box> result=new ArrayList<>();for(var entry:object.cells().entrySet()){BlockPos cell=root.add(entry.getKey().x(),entry.getKey().y(),entry.getKey().z());for(var b:physical?entry.getValue().collision():entry.getValue().selection()){if(result.size()>=128)return List.copyOf(result);result.add(new Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()).offset(cell));}}return List.copyOf(result);
    }
    private static double distance(Vec3d p,Box b){double x=Math.max(Math.max(b.minX-p.x,p.x-b.maxX),0),y=Math.max(Math.max(b.minY-p.y,p.y-b.maxY),0),z=Math.max(Math.max(b.minZ-p.z,p.z-b.maxZ),0);return x*x+y*y+z*z;}
    private static double offset(ServerPlayerEntity p,MechanismLinks.TargetRef t){if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return rp.verticalOffset();return VerticalMount.offset(p.getWorld(),BlockPos.fromLong(t.root()));}
    private static boolean setOffset(ServerPlayerEntity p,MechanismLinks.TargetRef t,double value){if(!Double.isFinite(value))return false;if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return rp.setVerticalOffset(value,p);return VerticalMount.setOffset(p.getServerWorld(),BlockPos.fromLong(t.root()),value,p);}
    private static void objectForm(ServerPlayerEntity p,Session s,JsonObject q){if(!validTarget(p,s)){s.message="Выбранный экземпляр недоступен или вне досягаемости.";return;}String field=text(q,"field",40);boolean done=false;Geometry before=Set.of("offset","reset_height","mount","dogs").contains(field)?geometry(p,s.target):null;
        switch(field){case "offset" -> done=setOffset(p,s.target,q.get("value").getAsDouble());case "reset_height" -> done=setOffset(p,s.target,0);case "manual" -> done=ObjectPolicies.setManual(p,s.target,q.get("value").getAsBoolean());
            case "dogs" -> {Entity entity=p.getServerWorld().getEntity(s.target.instance());if(entity instanceof RpObjectEntity rp&&rp.supportsDogVisibility()){rp.setDogsVisible(q.get("value").getAsBoolean());done=true;}}
            case "mount" -> {BlockPos root=BlockPos.fromLong(s.target.root());BlockState state=p.getWorld().getBlockState(root);var owner=VerticalMount.owner(p.getWorld(),root);if(owner!=null&&state.contains(ThinWindowRootBlock.MOUNT)){BlockState next=state.with(ThinWindowRootBlock.MOUNT,ThinWindowRootBlock.Mount.valueOf(text(q,"value",20)));done=CompositeRuntime.transition(p.getServerWorld(),owner,next,p).outcome()==TransactionCore.Outcome.COMMITTED;}}
            default -> throw new IllegalArgumentException("object field");}
        if(done&&before!=null)recordUndo(p,s,before,field.equals("offset")||field.equals("reset_height")?"offset":field.equals("dogs")?"dogs":"geometry");s.accepted=done;s.message=done?"Настройка экземпляра применена.":"Настройка не поддерживается или отклонена сервером.";
    }
    private static boolean undoable(BuildingTool.Action action){return Set.of(BuildingTool.Action.ROTATE,BuildingTool.Action.PROFILE,BuildingTool.Action.MOUNT,BuildingTool.Action.UP,BuildingTool.Action.DOWN,BuildingTool.Action.DOGS).contains(action);}
    /** No entity/world NBT restoration: only geometry changed by the builder is kept. */
    private static Geometry geometry(ServerPlayerEntity p,MechanismLinks.TargetRef target){
        if(!target.dimension().equals(p.getWorld().getRegistryKey().getValue().toString()))return null;
        if(target.kind()==MechanismLinks.Kind.RP){Entity e=p.getServerWorld().getEntity(target.instance());if(!(e instanceof RpObjectEntity rp)||rp.isRemoved()||!rp.assetId().equals(target.registryId()))return null;return new Geometry(target,null,null,rp.verticalOffset(),rp.getPos(),rp.getYaw(),rp.objectScale(),rp.dogsVisible(),rp.isOpen());}
        BlockPos root=BlockPos.fromLong(target.root());if(!p.getWorld().isChunkLoaded(root))return null;var owner=VerticalMount.owner(p.getWorld(),root);if(owner==null||!owner.instanceId().equals(target.instance())||!owner.registryId().equals(target.registryId())||!(VerticalMount.loadedEntity(p.getWorld(),root) instanceof CompositeBlockEntity own))return null;
        NbtCompound payload=own.payload();payload.remove(VerticalMount.KEY);payload.remove("MountEdited");payload.remove("NativeGeometry");return new Geometry(target,p.getWorld().getBlockState(root),payload,own.verticalOffset(),null,0,0,false,false);
    }
    private static String undoKey(MechanismLinks.TargetRef target){return target.dimension()+"/"+target.instance();}
    private static void recordUndo(ServerPlayerEntity p,Session s,Geometry before,String operation){
        Geometry after=geometry(p,before.target());if(after==null||before.equals(after))return;String key=undoKey(before.target());
        var changed=touched(before,after,operation);if(changed.isEmpty())return;
        // Only overlapping fields invalidate another player's undo; unrelated work survives.
        for(var entry:SESSIONS.entrySet())if(!entry.getKey().equals(p.getUuid()))entry.getValue().undo.invalidate(key,e->!Collections.disjoint(changed,touched(e.before(),e.after(),e.operation())));
        if(operation!=null)s.undo.record(key,operation,before,after);
    }
    private static Set<String> touched(Geometry before,Geometry after,String operation){
        Set<String> fields=new HashSet<>();if("offset".equals(operation)){if(before.offset()!=after.offset())fields.add("offset");return fields;}if("dogs".equals(operation)){if(before.dogs()!=after.dogs())fields.add("dogs");return fields;}
        if(before.state()==null){if(before.yaw()!=after.yaw())fields.add("yaw");if(before.open()!=after.open())fields.add("open");return fields;}
        for(var property:before.state().getProperties())if(!Objects.equals(before.state().get(property),after.state().get(property)))fields.add("state:"+property.getName());
        if(before.payload()!=null&&after.payload()!=null){Set<String> keys=new HashSet<>(before.payload().getKeys());keys.addAll(after.payload().getKeys());for(String key:keys)if(!Objects.equals(before.payload().get(key),after.payload().get(key)))fields.add("payload:"+key);}
        return fields;
    }
    private static boolean undoMatches(Geometry current,Geometry before,Geometry after,String operation){
        for(String field:touched(before,after,operation)){
            if(field.equals("offset")&&current.offset()!=after.offset()||field.equals("dogs")&&current.dogs()!=after.dogs()||field.equals("yaw")&&current.yaw()!=after.yaw()||field.equals("open")&&current.open()!=after.open())return false;
            if(field.startsWith("state:")){var property=after.state().getBlock().getStateManager().getProperty(field.substring(6));if(current.state()==null||property==null||!Objects.equals(current.state().get(property),after.state().get(property)))return false;}
            if(field.startsWith("payload:")&&(current.payload()==null||!Objects.equals(current.payload().get(field.substring(8)),after.payload().get(field.substring(8)))))return false;
        }return true;
    }
    private static <T extends Comparable<T>> BlockState restoreProperty(BlockState current,BlockState before,net.minecraft.state.property.Property<T> property){return current.with(property,before.get(property));}
    private static BlockState restoredState(Geometry current,Geometry before,Geometry after){BlockState next=current.state();for(var property:before.state().getProperties())if(!Objects.equals(before.state().get(property),after.state().get(property)))next=restoreProperty(next,before.state(),property);return next;}
    private static void undo(ServerPlayerEntity p,Session s){
        var entry=s.undo.latest();if(entry==null){s.message="Нет доступных изменений для отмены.";notice(p,s.message);return;}
        Geometry before=entry.before();MechanismLinks.TargetRef target=before.target();Geometry current=geometry(p,target);
        if(current==null){s.undo.removeLatest();s.message="Отмена пропущена: экземпляр удалён или не загружен.";notice(p,s.message);return;}
        if(!near(p,target)||!BuildPermissions.canEdit(p.getWorld(),p,target.kind()==MechanismLinks.Kind.RP?BlockPos.ofFloored(current.position()):BlockPos.fromLong(target.root()))){s.message="Отмена недоступна: проверьте права и досягаемость объекта.";notice(p,s.message);return;}
        if(!undoMatches(current,before,entry.after(),entry.operation())){var affected=touched(before,entry.after(),entry.operation());s.undo.invalidate(entry.key(),e->!Collections.disjoint(affected,touched(e.before(),e.after(),e.operation())));s.message="Отмена отклонена: затрагиваемые данные экземпляра уже изменились.";notice(p,s.message);return;}
        boolean done=false;
        if(entry.operation().equals("offset"))done=setOffset(p,target,before.offset());
        else if(target.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(target.instance()) instanceof RpObjectEntity rp){
            if(entry.operation().equals("dogs")&&rp.supportsDogVisibility()){rp.setDogsVisible(before.dogs());done=true;}
            else if(entry.operation().equals("geometry"))done=rp.rotateByBuilder(p,before.yaw());
        }else{
            BlockPos root=BlockPos.fromLong(target.root());var owner=VerticalMount.owner(p.getWorld(),root);
            BlockState next=restoredState(current,before,entry.after());
            if(owner!=null&&VerticalMount.loadedEntity(p.getWorld(),root) instanceof CompositeBlockEntity own&&(current.state().getBlock() instanceof CompositeRootBlock||!own.contributions().isEmpty())){NbtCompound restored=own.payload();for(String field:touched(before,entry.after(),entry.operation()))if(field.startsWith("payload:")){String key=field.substring(8);if(before.payload().contains(key))restored.put(key,before.payload().get(key).copy());else restored.remove(key);}done=CompositeRuntime.transitionPayload(p.getServerWorld(),owner,next,restored,p).outcome()==TransactionCore.Outcome.COMMITTED;}
            else if(current.state().getBlock() instanceof PrototypeLadderBlock)done=PrototypeArchitecture.edit(p.getWorld(),root,current.state(),next,p);
            else if(current.state().getBlock() instanceof PrototypeWallBlock)done=PrototypeWallArchitecture.edit(p.getWorld(),root,current.state(),next,p);
        }
        if(done){s.undo.removeLatest();var affected=touched(before,entry.after(),entry.operation());for(var other:SESSIONS.entrySet())if(!other.getKey().equals(p.getUuid()))other.getValue().undo.invalidate(entry.key(),e->!Collections.disjoint(affected,touched(e.before(),e.after(),e.operation())));s.target=currentRef(p,target);s.pinned=true;}
        s.message=done?"Последнее изменение отменено.":"Отмена отклонена проверкой геометрии или прав сервера.";notice(p,s.message);
    }
    private static void lampForm(ServerPlayerEntity p,Session s,JsonObject q){String line=text(q,"line",64),name=text(q,"name",64);boolean both=q.has("both")&&q.get("both").getAsBoolean();LampEditor.Action action=LampEditor.Action.valueOf(text(q,"action",30));UUID source=null,destination=null;
        if(q.has("destination"))destination=UUID.fromString(text(q,"destination",40));if(q.has("source"))source=UUID.fromString(text(q,"source",40));else if((action==LampEditor.Action.RENAME||action==LampEditor.Action.REGISTER)&&validTarget(p,s)&&s.target.kind()==MechanismLinks.Kind.RP)source=s.target.instance();
        var result=LampEditor.edit(p,new LampEditor.Request(action,source,destination,line,name,both,q.has("confirmed")&&q.get("confirmed").getAsBoolean()));s.accepted=result.success();s.message=result.reason();if(result.success()){p.getMainHandStack().getOrCreateNbt().putString("BuilderLampLine",action==LampEditor.Action.RENAME_LINE?name:line);p.getMainHandStack().getOrCreateNbt().putBoolean("BuilderLampOneWay",!both);}
    }
    private static void diagnostics(ServerPlayerEntity p,Session s,JsonObject q){
        if(!p.hasPermissionLevel(2)){s.message="Диагностика доступна оператору уровня 2.";chat(p,s.message);return;}String action=text(q,"action",20),note=text(q,"note",512);
        switch(action){
            case "snapshot" -> s.message=validTarget(p,s)&&snapshot(p,s.target,note)?"Снимок выбранного UUID сохранён; для файла выполните Экспорт ZIP.":"Снимок не сохранён: объект недоступен или чтение завершилось ошибкой.";
            case "start" -> {boolean selected=q.has("selected")&&q.get("selected").getAsBoolean();if(selected&&!validTarget(p,s)){s.message="Запись не запущена: выбранный экземпляр недоступен.";break;}DwDiagnostics.Filter filter=selected?new DwDiagnostics.Filter("object","",s.target.instance().toString(),s.target.dimension(),BlockPos.fromLong(s.target.root()),0):DwDiagnostics.Filter.all(p.getServerWorld());UUID id=DwDiagnostics.start(p.getServerWorld(),60,filter);s.message="Запись 60 с: "+(selected?"выбранный UUID "+s.target.instance():"весь мод")+"; сеанс "+id+". Автоостановка включена.";}
            case "stop" -> s.message=DwDiagnostics.stop(p.getServer(),"BUILDER_MENU_STOP")?"Запись остановлена; данные доступны для экспорта.":"Активной записи нет.";
            case "mark" -> {if(Boolean.TRUE.equals(DwDiagnostics.status(p.getServer()).get("enabled"))){DwDiagnostics.mark(p.getServerWorld(),note);s.message="Пометка сохранена в активном сеансе.";}else s.message="Пометка не сохранена: запись выключена.";}
            case "export" -> {String id=String.valueOf(DwDiagnostics.status(p.getServer()).get("sessionId"));s.message="Экспорт отчёта: ожидается запись файла.";DwDiagnostics.export(p.getServer()).thenAccept(path->p.getServer().execute(()->{s.message=path==null?"Экспорт не выполнен; см. ошибки диагностики.":"Серверный отчёт: "+path.toAbsolutePath()+"; сеанс "+id+". Клиентские измерения отдельны: проверьте ZIP с этим номером в diagnostics клиента после завершения записи.";chat(p,s.message);send(p,s,false);}));}
            case "status" -> s.message="Статус диагностики обновлён; GPU-время не измерено.";
            default -> throw new IllegalArgumentException("diagnostics");
        }chat(p,s.message);
    }
    private static boolean snapshot(ServerPlayerEntity p,MechanismLinks.TargetRef t,String note){Map<String,Object> result=t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp?DwDiagnostics.snapshot(p.getServerWorld(),p,rp,Map.of("note",note)):DwDiagnostics.snapshot(p.getServerWorld(),p,BlockPos.fromLong(t.root()),Map.of("note",note));return "SNAPSHOT_READ_ONLY".equals(result.get("result"));}
    private record Ray(MechanismLinks.TargetRef ref,double distance){}
    public static List<MechanismLinks.TargetRef> candidates(ServerPlayerEntity player){ServerWorld world=player.getServerWorld();Vec3d eye=player.getEyePos(),end=eye.add(player.getRotationVector().multiply(reach(player)));List<Ray> hits=new ArrayList<>();
        // Packets carry view yaw/pitch; interpolated head animation can retain an unrelated direction.
        HitResult obstacle=world.raycast(new net.minecraft.world.RaycastContext(eye,end,net.minecraft.world.RaycastContext.ShapeType.OUTLINE,net.minecraft.world.RaycastContext.FluidHandling.NONE,player));if(obstacle instanceof BlockHitResult b&&obstacle.getType()==HitResult.Type.BLOCK&&!VerticalMount.isArchitecture(world.getBlockState(b.getBlockPos()))&&CompositeLedger.get(world).at(CompositeData.cell(b.getBlockPos())).isEmpty())end=obstacle.getPos();
        for(RpObjectEntity rp:dev.dreamwalker.bloodbornerp.object.RpObjectIndex.in(world,new Box(eye,end).expand(1)))if(!rp.isRemoved())for(Box box:rp.selectionBoxes()){var point=box.raycast(eye,end);if(point.isPresent()){hits.add(new Ray(MechanismLinks.TargetRef.rp(rp),point.get().squaredDistanceTo(eye)));break;}}
        Set<dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner> seen=new HashSet<>();BlockPos min=BlockPos.ofFloored(Math.min(eye.x,end.x)-1,Math.min(eye.y,end.y)-1,Math.min(eye.z,end.z)-1),max=BlockPos.ofFloored(Math.max(eye.x,end.x)+1,Math.max(eye.y,end.y)+1,Math.max(eye.z,end.z)+1);for(BlockPos pos:BlockPos.iterate(min,max)){if(!world.isChunkLoaded(pos))continue;for(var c:CompositeLedger.get(world).at(CompositeData.cell(pos))){var hit=CompositeRuntime.shape(c.shape().selection()).raycast(eye,end,pos);if(hit!=null&&seen.add(c.owner()))hits.add(new Ray(new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),c.owner().instanceId(),CompositeData.pos(c.owner().root()).asLong(),c.owner().registryId()),hit.getPos().squaredDistanceTo(eye)));}}
        HitResult nativeHit=obstacle;if(nativeHit instanceof BlockHitResult b&&nativeHit.getType()==HitResult.Type.BLOCK){BlockPos pos=b.getBlockPos(),root=SourceLadderRuntime.resolveRoot(world,pos);if(root!=null)pos=root;var owner=VerticalMount.owner(world,pos);if(owner!=null&&seen.add(owner))hits.add(new Ray(new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,world.getRegistryKey().getValue().toString(),owner.instanceId(),pos.asLong(),owner.registryId()),b.getPos().squaredDistanceTo(eye)));}
        hits.sort(Comparator.comparingDouble(Ray::distance));return hits.stream().map(Ray::ref).distinct().limit(32).toList();
    }
    private static Map<String,Object> targetView(ServerPlayerEntity p,MechanismLinks.TargetRef target,boolean overlay){
        MechanismLinks.TargetRef t=currentRef(p,target);Geometry g=geometry(p,t);Identifier registry=new Identifier(t.kind()==MechanismLinks.Kind.RP?"bloodborne_rp:"+t.registryId():t.registryId());var entry=g!=null&&t.kind()==MechanismLinks.Kind.ARCHITECTURE?DebugCatalogue.entry(g.state()):DebugCatalogue.entry(registry);Map<String,Object> out=new LinkedHashMap<>();
        out.put("key",t.key());out.put("name",entry==null?t.registryId():entry.name());out.put("id",entry==null?"?????":entry.temporaryId());out.put("kind",t.kind()==MechanismLinks.Kind.RP?"RP-объект":"Блок");out.put("instance",t.instance().toString());out.put("registry",registry.toString());out.put("dimension",t.dimension());out.put("position",BlockPos.fromLong(t.root()).toShortString());out.put("available",g!=null&&near(p,t));out.put("version",version(p,t));if(g==null)return out;
        out.put("offset",g.offset());var status=MechanismLinks.inspect(p.getServer(),t);out.put("openable",status.availability()==MechanismLinks.Availability.LOADED&&!status.pulseOnly());out.put("pulse",status.pulseOnly());out.put("leversOnly",ObjectPolicies.leversOnly(p.getServer(),t));out.put("pending",MechanismLinks.pending(p.getServer(),t));out.put("profile",supported(p,t,BuildingTool.Action.PROFILE));out.put("mount",supported(p,t,BuildingTool.Action.MOUNT));out.put("dogs",supported(p,t,BuildingTool.Action.DOGS));out.put("pose",supported(p,t,BuildingTool.Action.POSE));out.put("quickModes",quickModes(p,t).stream().map(Enum::name).toList());
        if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp){out.put("rotationStep",45);out.put("yaw",rp.getYaw());out.put("lamp",rp.assetId().equals("hunterlamp"));out.put("lever",rp.isMechanism());out.put("open",rp.isOpen());out.put("dogsVisible",rp.dogsVisible());out.put("busy",rp.animationBusy());}
        else{BlockState state=g.state();out.put("rotationStep",state.getBlock() instanceof ThinWindowRootBlock||state.getBlock() instanceof PrototypeWallBlock&&PrototypeWallBlock.retainedFence(state)?90:45);if(state.contains(ThinWindowRootBlock.MOUNT))out.put("mountValue",state.get(ThinWindowRootBlock.MOUNT).name());if(state.contains(CompositeRootBlock.OPEN))out.put("open",state.get(CompositeRootBlock.OPEN));}
        if(p.hasPermissionLevel(4)&&Boolean.TRUE.equals(out.get("openable"))){out.put("afterOpen",ObjectPolicies.commands(p.getServer(),t,true));out.put("afterClose",ObjectPolicies.commands(p.getServer(),t,false));}
        out.put("rootPoint",point(t,g));if(overlay){out.put("selectionBoxes",boxes(p,t,false).stream().map(BuilderServer::boxView).toList());out.put("collisionBoxes",boxes(p,t,true).stream().map(BuilderServer::boxView).toList());}return out;
    }
    private static List<Double> boxView(Box b){return List.of(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private static List<Double> point(MechanismLinks.TargetRef t,Geometry g){Vec3d pos=g!=null&&g.position()!=null?g.position():Vec3d.ofCenter(BlockPos.fromLong(t.root())).add(0,g==null?0:g.offset(),0);return List.of(pos.x,pos.y,pos.z);}
    private static Map<String,Object> lineView(ServerPlayerEntity p,MechanismLinks.TargetRef from,MechanismLinks.TargetRef to,String action){return Map.of("from",point(from,geometry(p,from)),"to",point(to,geometry(p,to)),"dimension",to.dimension(),"both",false,"action",action,"loaded",geometry(p,to)!=null,"pending",MechanismLinks.pending(p.getServer(),to));}
    private static List<Map<String,Object>> linkViews(ServerPlayerEntity p,Session s){
        List<Map<String,Object>> lines=new ArrayList<>();MechanismLinks.TargetRef selected=s.source!=null?s.source:s.target;if(selected==null)return lines;
        for(var r:MechanismRules.list(p.getServer()))if(r.sources.containsKey(selected.instance())||r.targets.containsKey(selected.key()))for(var source:r.sources.values())for(var target:r.targets.values()){if(lines.size()>=64)return lines;lines.add(lineView(p,source,target,r.effect.name()));}
        if(selected.registryId().equals("hunterlamp")){var lamp=JSON.toJsonTree(LampEditor.view(p,selected.instance())).getAsJsonObject();if(lamp.has("connections"))for(var e:lamp.getAsJsonArray("connections")){if(lines.size()>=64)break;var c=e.getAsJsonObject();var a=c.getAsJsonObject(c.get("aToB").getAsBoolean()?"source":"destination");var b=c.getAsJsonObject(c.get("aToB").getAsBoolean()?"destination":"source");lines.add(Map.of("from",JSON.fromJson(a.get("position"),List.class),"to",JSON.fromJson(b.get("position"),List.class),"dimension",b.get("dimension").getAsString(),"both",c.get("aToB").getAsBoolean()&&c.get("bToA").getAsBoolean(),"action","Телепорт","loaded",b.get("loaded").getAsBoolean(),"pending",false));}}
        return lines;
    }
    private static void send(ServerPlayerEntity p,Session s,boolean open){send(p,s,open,false);}
    private static void send(ServerPlayerEntity p,Session s,boolean open,boolean compact){
        if(!ServerPlayNetworking.canSend(p,VIEW))return;if(s.target!=null&&geometry(p,s.target)!=null)s.target=currentRef(p,s.target);Map<String,Object> view=new LinkedHashMap<>();
        view.put("openMenu",open);view.put("accepted",s.accepted);view.put("requestId",s.requestId);view.put("sequence",s.lastSequence);view.put("editable",BuildPermissions.canEdit(p));view.put("admin",p.hasPermissionLevel(4));view.put("operator",p.hasPermissionLevel(2));view.put("action",BuildingTool.action(p.getMainHandStack()).name());view.put("actionLabel",BuildingTool.action(p.getMainHandStack()).label);view.put("step",step(p.getMainHandStack()));view.put("pinned",s.pinned);view.put("message",s.message);view.put("undoCount",s.undo.size());view.put("globalRules",s.globalRules);view.put("quickModes",quickModes(p,s.target).stream().map(Enum::name).toList());
        if(s.target!=null)view.put("target",targetView(p,s.target,true));if(s.source!=null)view.put("source",targetView(p,s.source,true));view.put("links",linkViews(p,s));if(compact){view.put("compact",true);emit(p,view);return;}view.put("candidates",s.candidates.stream().filter(t->geometry(p,t)!=null).limit(32).map(t->targetView(p,t,false)).toList());
        var related=MechanismRules.list(p.getServer()).stream().filter(r->s.globalRules&&p.hasPermissionLevel(2)||s.target!=null&&(r.sources.containsKey(s.target.instance())||r.targets.containsKey(s.target.key()))).toList();int rulePages=Math.max(1,(related.size()+63)/64);s.rulePage=Math.min(s.rulePage,rulePages-1);view.put("rulePage",s.rulePage);view.put("rulePages",rulePages);view.put("ruleTotal",related.size());List<Map<String,Object>> listed=new ArrayList<>();for(int i=s.rulePage*64;i<Math.min(related.size(),(s.rulePage+1)*64);i++){var r=related.get(i);listed.add(Map.of("id",r.id.toString(),"order",i+1,"executionOrder",r.order,"name",r.name,"condition",r.condition.name(),"effect",r.effect.name(),"incomplete",r.incomplete));}view.put("rules",listed);
        if(ruleAllowed(p,s)&&s.draft!=null){var rv=new LinkedHashMap<>(MechanismRules.view(p.getServer(),s.draft,s.affectedPage));rv.put("draft",s.draft.order==0||!MechanismRules.version(s.draft).equals(s.ruleVersion));rv.put("version",s.ruleVersion);rv.put("conflicts",MechanismRules.conflicts(p.getServer(),s.draft));view.put("rule",rv);}
        UUID lampSource=s.source!=null&&s.source.registryId().equals("hunterlamp")?s.source.instance():s.target!=null&&s.target.registryId().equals("hunterlamp")?s.target.instance():null;view.put("lamps",LampEditor.view(p,lampSource,s.lampPage));view.put("lampVersion",lampVersion(p,s));if(s.target!=null&&s.target.registryId().equals("hunterlamp"))view.put("currentLamp",LampEditor.view(p,s.target.instance()));view.put("lampLine",p.getMainHandStack().getOrCreateNbt().getString("BuilderLampLine"));view.put("lampBoth",!p.getMainHandStack().getOrCreateNbt().getBoolean("BuilderLampOneWay"));view.put("diagnostics",DwDiagnostics.status(p.getServer()));
        emit(p,view);
    }
    private static void emit(ServerPlayerEntity p,Map<String,Object> view){
        String json=JSON.toJson(view);if(json.length()>MAX_VIEW){notice(p,"Список слишком велик; выберите отдельное правило.");return;}var buf=PacketByteBufs.create();buf.writeString(json,MAX_VIEW);DwDiagnostics.network("server","send",VIEW.toString(),buf.readableBytes());ServerPlayNetworking.send(p,VIEW,buf);
    }
    private static MechanismLinks.TargetRef currentRef(ServerPlayerEntity p,MechanismLinks.TargetRef t){if(t.kind()==MechanismLinks.Kind.RP&&p.getServerWorld().getEntity(t.instance()) instanceof RpObjectEntity rp)return MechanismLinks.TargetRef.rp(rp);return t;}
    private static String text(JsonObject q,String key,int limit){String value=q.has(key)?q.get(key).getAsString():"";if(value.length()>limit)throw new IllegalArgumentException(key);return value;}
    private static void notice(ServerPlayerEntity p,String text){if(text!=null&&!text.isBlank())p.sendMessage(Text.literal(text),true);}
    private static void chat(ServerPlayerEntity p,String text){notice(p,text);if(text!=null&&!text.isBlank())p.sendMessage(Text.literal("90009: "+text),false);}
}
