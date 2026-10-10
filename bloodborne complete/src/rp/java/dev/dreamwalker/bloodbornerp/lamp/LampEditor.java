package dev.dreamwalker.bloodbornerp.lamp;

import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Lamp command adapter. Every submitted UUID is an entity UUID, never a user-supplied node privilege. */
public final class LampEditor {
 private static final Map<UUID,Selection> SELECTIONS=new HashMap<>();
 private static final int SELECTION_TICKS=36000,MAX_SELECTIONS=256,PAGE_SIZE=64;
 private LampEditor(){}
 public enum Action {REGISTER,RENAME,CONNECT,SET_DIRECTION,UNLINK,CREATE_LINE,RENAME_LINE,DELETE_LINE,CANCEL}
 public record Request(Action action,UUID sourceLampEntity,UUID destinationLampEntity,String lineName,String lampName,boolean bidirectional,boolean confirmed){
  public Request(Action action,UUID source,UUID target,String line,String name,boolean both){this(action,source,target,line,name,both,false);}
 }
 public record Result(boolean success,String reason,Map<String,Object> view){}
 public static Result selectSource(ServerPlayerEntity player,UUID entityId){
  if(!allowed(player))return refused("Изменение фонарной сети доступно оператору уровня 2.");
  RpObjectEntity lamp=LampService.loadedLamp(player.getServerWorld(),entityId);
  if(!LampService.near(player,lamp))return refused("Нужен выбранный охотничий фонарь в пределах досягаемости.");
  LampState.Node node=LampService.register(player,lamp,defaultName(lamp));if(node==null)return refused("Достигнут предел зарегистрированных фонарей.");
  if(!SELECTIONS.containsKey(player.getUuid())&&SELECTIONS.size()>=MAX_SELECTIONS)return refused("Слишком много текущих выборов источника.");
  SELECTIONS.put(player.getUuid(),new Selection(entityId,node.dimension,player.getServer().getTicks()+SELECTION_TICKS));
  return new Result(true,"Источник выбран. Можно добавлять несколько назначений.",view(player,entityId));
 }
 public static Result edit(ServerPlayerEntity player,Request request){
  if(!allowed(player))return refused("Изменение фонарной сети доступно оператору уровня 2.");
  if(request==null||request.action()==null)return refused("Не выбрано действие фонарной сети.");
  if(request.action()==Action.CANCEL){clearPlayer(player.getUuid());return new Result(true,"Выбор источника отменён; созданные связи сохранены.",Map.of("selected",false));}
  UUID sourceId=request.sourceLampEntity();Selection selected=SELECTIONS.get(player.getUuid());if(sourceId==null&&selected!=null)sourceId=selected.entity;
  LampState graph=LampService.state(player.getServer());RpObjectEntity sourceLamp=sourceId==null?null:LampService.loadedLamp(player.getServerWorld(),sourceId);
  LampState.Node source=sourceId==null?null:graph.byEntity(sourceId);
  boolean authorized=LampService.near(player,sourceLamp)||(source!=null&&selected!=null&&selected.entity.equals(sourceId)&&selected.until>=player.getServer().getTicks()&&selected.dimension.equals(source.dimension));
  if(!authorized)return refused("Сначала выберите исходный фонарь в мире.");
  if(source==null){source=LampService.register(player,sourceLamp,LampPolicy.validName(request.lampName())?request.lampName():defaultName(sourceLamp));if(source==null)return refused("Не удалось зарегистрировать исходный фонарь.");}
  LampService.entityMoved(sourceLamp==null?sourceEntity(player,source):sourceLamp);
  String line=request.lineName()==null?"":request.lineName().strip();String name=request.lampName()==null?"":request.lampName().strip();
  boolean changed=false;String reason="";
  switch(request.action()){
   case REGISTER -> {reason="Фонарь зарегистрирован.";changed=true;}
   case RENAME -> {if(!LampPolicy.validName(name))return refused("Имя фонаря должно содержать 1–64 читаемых символа.");source.name=name;graph.markDirty();reason="Имя фонаря сохранено.";changed=true;}
   case CREATE_LINE -> {if(!LampPolicy.validName(line))return refused("Имя линии должно содержать 1–64 читаемых символа.");if(graph.byName(line)!=null)return refused("Такая линия уже существует; выберите её.");if(graph.line(line,true)==null)return refused("Достигнут предел 128 линий.");graph.markDirty();reason="Линия создана без автоматических связей.";changed=true;}
   case RENAME_LINE -> {LampState.Line selectedLine=graph.byName(line);if(selectedLine==null||!LampPolicy.validName(name))return refused("Укажите существующую линию и читаемое новое имя.");if(graph.byName(name)!=null&&!name.equals(line))return refused("Это имя линии уже занято.");selectedLine.name=name;graph.markDirty();reason="Линия переименована; её связи сохранены.";changed=true;}
   case DELETE_LINE -> {if(!request.confirmed())return refused("Подтвердите удаление всей линии и её связей в меню.");LampState.Line selectedLine=graph.byName(line);if(selectedLine==null)return refused("Линия не найдена.");graph.lines.remove(selectedLine.id);graph.rebuildRoutes();graph.markDirty();reason="Удалена только выбранная линия; остальные маршруты сохранены.";changed=true;}
   case CONNECT,SET_DIRECTION,UNLINK -> {
    if(!LampPolicy.validName(line)||request.destinationLampEntity()==null)return refused("Выберите именованную линию и фонарь назначения.");
    UUID targetId=request.destinationLampEntity();if(targetId.equals(sourceId))return refused("Источник и назначение должны быть разными экземплярами.");
    RpObjectEntity targetLamp=LampService.loadedLamp(player.getServerWorld(),targetId);LampState.Node target=graph.byEntity(targetId);
    // Existing routes can be edited from either loaded end; new target identities must be nearby.
    if(request.action()==Action.CONNECT){if(!LampService.near(player,targetLamp))return refused("Новый фонарь назначения должен быть выбран в пределах досягаемости.");if(graph.connectionCount()>=LampState.MAX_CONNECTIONS&&(target==null||graph.byName(line)==null||graph.connection(graph.byName(line),source.id,target.id)==null))return refused("Достигнут предел связей.");if(target==null)target=LampService.register(player,targetLamp,defaultName(targetLamp));}
    else if(target==null)return refused("Этот экземпляр назначения уже удалён или не зарегистрирован.");
    if(target==null)return refused("Достигнут предел зарегистрированных фонарей.");
    LampState.Line selectedLine=graph.byName(line);
    if(request.action()==Action.SET_DIRECTION&&(selectedLine==null||graph.connection(selectedLine,source.id,target.id)==null))return refused("Выбранная связь не существует в этой линии.");
    if(request.action()==Action.UNLINK)changed=graph.unlink(line,source.id,target.id,request.bidirectional());
    else changed=graph.setConnection(line,source.id,target.id,request.bidirectional());
    reason=changed?"Сохранена только выбранная связь и её направление.":"Указанное направление связи не найдено или достигнут предел сети.";
    if(changed&&!request.bidirectional()&&graph.connected(target.id,source.id))reason+=" Обратный путь остаётся по другой явно настроенной линии.";
   }
   default -> {return refused("Неподдерживаемое действие.");}
  }

  return new Result(changed,reason,view(player,sourceId));
 }
 public static Map<String,Object> view(ServerPlayerEntity player,UUID entityId){return view(player,entityId,0);}
 public static Map<String,Object> view(ServerPlayerEntity player,UUID entityId,int page){
  if(!allowed(player))return Map.of("success",false,"reason","Требуются права оператора уровня 2.");
  LampState graph=LampService.state(player.getServer());Selection selected=SELECTIONS.get(player.getUuid());if(entityId==null&&selected!=null)entityId=selected.entity;
  LampState.Node n=entityId==null?null:graph.byEntity(entityId);RpObjectEntity lamp=entityId==null?null:LampService.loadedLamp(player.getServerWorld(),entityId);
  if(n!=null&&!LampService.near(player,lamp)&&(selected==null||!selected.entity.equals(entityId)||selected.until<player.getServer().getTicks()))return Map.of("success",false,"reason","Фонарь не выбран или слишком далеко.");
  List<Map<String,Object>> lines=new ArrayList<>(),connections=new ArrayList<>();int total=0;page=Math.max(0,Math.min(page,LampState.MAX_CONNECTIONS/PAGE_SIZE));
  for(LampState.Line line:graph.lines.values()){
   lines.add(Map.of("lineUuid",line.id.toString(),"name",line.name,"connectionCount",line.connections.size()));
   if(n==null)continue;
   for(LampState.Connection c:line.connections.values())if(c.a.equals(n.id)||c.b.equals(n.id)){
    int index=total++;if(index<page*PAGE_SIZE||connections.size()>=PAGE_SIZE)continue;
    LampState.Node a=graph.nodes.get(c.a),b=graph.nodes.get(c.b);if(a==null||b==null)continue;
    UUID selectedSource=n.id,selectedTarget=c.a.equals(n.id)?c.b:c.a;
    boolean otherReverse=false;for(LampState.Line other:graph.lines.values())if(other!=line){LampState.Connection o=graph.connection(other,selectedSource,selectedTarget);if(o!=null&&(o.a.equals(selectedSource)?o.bToA:o.aToB)){otherReverse=true;break;}}
    connections.add(Map.of("connectionUuid",c.id.toString(),"lineName",line.name,"source",nodeView(player.getServer(),a),"destination",nodeView(player.getServer(),b),"aToB",c.aToB,"bToA",c.bToA,"reverseOtherLines",otherReverse));
   }
  }
  Map<String,Object> out=new LinkedHashMap<>();out.put("success",true);out.put("selected",n!=null);if(n!=null)out.put("node",nodeView(player.getServer(),n));out.put("lines",List.copyOf(lines));out.put("connections",List.copyOf(connections));out.put("connectionTotal",total);out.put("page",page);out.put("pageCount",Math.max(1,(total+PAGE_SIZE-1)/PAGE_SIZE));out.put("outgoingNames",n==null?List.of():LampService.destinations(player.getServer(),n).stream().map(x->x.name).toList());out.put("definition","Линия — имя группы явных связей; участие само по себе не создаёт переходов.");return Collections.unmodifiableMap(out);
 }
 private static Map<String,Object> nodeView(MinecraftServer server,LampState.Node n){ServerWorld world=LampService.world(server,n.dimension);return Map.of("nodeUuid",n.id.toString(),"entityUuid",n.lamp.toString(),"name",n.name,"dimension",n.dimension,"position",List.of(n.origin.x,n.origin.y,n.origin.z),"loaded",world!=null&&LampService.loadedLamp(world,n.lamp)!=null);}
 private static RpObjectEntity sourceEntity(ServerPlayerEntity p,LampState.Node n){ServerWorld world=LampService.world(p.getServer(),n.dimension);return world==null?null:LampService.loadedLamp(world,n.lamp);}
 private static String defaultName(RpObjectEntity lamp){String base="Фонарь ("+lamp.getBlockX()+", "+lamp.getBlockY()+", "+lamp.getBlockZ()+")";var graph=LampService.state(((ServerWorld)lamp.getWorld()).getServer());String name=base;for(int suffix=2;suffix<=LampState.MAX_NODES+1;suffix++){String candidate=name;if(graph.nodes.values().stream().noneMatch(n->n.name.equals(candidate)))return name;name=base+" №"+suffix;}return name;}
 private static boolean allowed(ServerPlayerEntity p){return p!=null&&p.getServer()!=null&&p.hasPermissionLevel(2);}
 private static Result refused(String reason){return new Result(false,reason,Map.of("success",false,"reason",reason));}
 static void expire(MinecraftServer server){SELECTIONS.entrySet().removeIf(e->e.getValue().until<server.getTicks()||server.getPlayerManager().getPlayer(e.getKey())==null);}
 static void removed(UUID entity){SELECTIONS.entrySet().removeIf(e->e.getValue().entity.equals(entity));}
 static void clearPlayer(UUID player){SELECTIONS.remove(player);}
 static void clearAll(){SELECTIONS.clear();}
 private record Selection(UUID entity,String dimension,long until){}
}
