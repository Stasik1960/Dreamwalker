package dev.dreamwalker.bloodbornerp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.dreamwalker.bloodbornerp.lamp.LampService;
import dev.dreamwalker.bloodbornerp.lamp.LampEditor;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** OP-only world-edit endpoints. Targeting follows the player's crosshair and block occlusion. */
public final class RpCommands {
 private static boolean registered; private RpCommands(){}
 public static void register(){if(registered)return;registered=true;CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
  var root=literal("bbrp").requires(source->source.hasPermissionLevel(2));
  root.then(literal("object").then(literal("rotate").executes(c->object(c.getSource(),"rotate",null))).then(literal("delete").executes(c->object(c.getSource(),"delete",null))).then(literal("lock").executes(c->object(c.getSource(),"lock",null))).then(literal("unlock").executes(c->object(c.getSource(),"unlock",null))).then(literal("list").executes(c->object(c.getSource(),"list",null))).then(literal("link").then(argument("target",StringArgumentType.word()).executes(c->object(c.getSource(),"link",StringArgumentType.getString(c,"target"))))).then(literal("unlink").then(argument("target",StringArgumentType.word()).executes(c->object(c.getSource(),"unlink",StringArgumentType.getString(c,"target"))))));
  var lamp=literal("lamp");
  lamp.then(literal("register").then(argument("name",StringArgumentType.greedyString()).executes(c->lampRegister(c.getSource(),StringArgumentType.getString(c,"name")))));
  lamp.then(literal("rename").then(argument("id",StringArgumentType.word()).then(argument("name",StringArgumentType.greedyString()).executes(c->lampRename(c.getSource(),StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"name"))))));
  lamp.then(literal("remove").then(argument("id",StringArgumentType.word()).executes(c->lampRemove(c.getSource(),StringArgumentType.getString(c,"id")))));
  lamp.then(literal("list").executes(c->lampList(c.getSource())));
  lamp.then(literal("select").executes(c->lampSelect(c.getSource())));
  lamp.then(literal("menu").executes(c->{var p=c.getSource().getPlayer();var clicked=ObjectRegistry.objectAt(p);LampService.open(p,clicked);return 1;}));
  var line=literal("line");
  line.then(literal("view").executes(c->lampLineView(c.getSource())));
  line.then(literal("create").then(argument("name",StringArgumentType.greedyString()).executes(c->lampEdit(c.getSource(),LampEditor.Action.CREATE_LINE,null,StringArgumentType.getString(c,"name"),"",true,false))));
  line.then(literal("rename").then(argument("line",StringArgumentType.string()).then(argument("name",StringArgumentType.greedyString()).executes(c->lampEdit(c.getSource(),LampEditor.Action.RENAME_LINE,null,StringArgumentType.getString(c,"line"),StringArgumentType.getString(c,"name"),true,false)))));
  line.then(literal("delete").then(argument("line",StringArgumentType.string()).then(literal("confirm").executes(c->lampEdit(c.getSource(),LampEditor.Action.DELETE_LINE,null,StringArgumentType.getString(c,"line"),"",true,true)))));
  for(var action:java.util.Map.of("connect",LampEditor.Action.CONNECT,"direction",LampEditor.Action.SET_DIRECTION,"unlink",LampEditor.Action.UNLINK).entrySet()){
   var named=argument("line",StringArgumentType.string()).executes(c->lampEdit(c.getSource(),action.getValue(),StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"line"),"",true,false));
   named.then(literal("both").executes(c->lampEdit(c.getSource(),action.getValue(),StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"line"),"",true,false)));
   named.then(literal("oneway").executes(c->lampEdit(c.getSource(),action.getValue(),StringArgumentType.getString(c,"target"),StringArgumentType.getString(c,"line"),"",false,false)));
   line.then(literal(action.getKey()).then(argument("target",StringArgumentType.word()).then(named)));
  }
  lamp.then(line);
  lamp.then(routeCommand("link",true)).then(routeCommand("unlink",false));
  var route=literal("route");route.then(routeCommand("open",true)).then(routeCommand("close",false));lamp.then(route);root.then(lamp);dispatcher.register(root);
 });}
 private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> routeCommand(String name,boolean open){return literal(name).then(argument("from",StringArgumentType.word()).then(argument("to",StringArgumentType.word()).executes(c->lampRoute(c.getSource(),StringArgumentType.getString(c,"from"),StringArgumentType.getString(c,"to"),open))));}
 private static int object(ServerCommandSource source,String operation,String target){if(!(source.getEntity() instanceof ServerPlayerEntity player)){source.sendError(Text.translatable("command.bloodborne_rp.player_required"));return 0;}RpObjectEntity object=ObjectRegistry.objectAt(player);if(object==null){source.sendError(Text.translatable("command.bloodborne_rp.object_required"));return 0;}if(operation.equals("rotate")){object.setYaw(object.getYaw()+90);object.refreshCollider();source.sendFeedback(()->Text.translatable("command.bloodborne_rp.done"),false);return 1;}if(operation.equals("delete")){object.removeObject();return 1;}if(operation.equals("lock")){object.setLocked(true);return 1;}if(operation.equals("unlock")){object.setLocked(false);return 1;}if(operation.equals("list")){source.sendFeedback(()->Text.literal(object.links().toString()),false);return 1;}try{UUID id=UUID.fromString(target);if(operation.equals("unlink")&&object.removeLink(id))return 1;var entity=player.getServerWorld().getEntity(id);if(entity instanceof RpObjectEntity destination&&object.addLink(destination))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.link_rejected"));return 0;}
 private static int lampRegister(ServerCommandSource source,String name){if(!(source.getEntity() instanceof ServerPlayerEntity player)||name.isBlank()||name.length()>64||!LampService.register(player,name)){source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}return 1;}
 private static int lampRename(ServerCommandSource source,String raw,String name){try{if(LampService.rename(source.getServer(),UUID.fromString(raw),name))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}
 private static int lampRemove(ServerCommandSource source,String raw){try{if(LampService.remove(source.getServer(),UUID.fromString(raw)))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}
 private static int lampRoute(ServerCommandSource source,String from,String to,boolean open){try{if(LampService.route(source.getServer(),UUID.fromString(from),UUID.fromString(to),open))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}
 private static int lampList(ServerCommandSource source){for(LampService.LampInfo lamp:LampService.list(source.getServer()))source.sendFeedback(()->Text.literal(lamp.id()+" "+lamp.name()),false);return 1;}
 private static int lampSelect(ServerCommandSource source){if(!(source.getEntity() instanceof ServerPlayerEntity p)){source.sendError(Text.literal("Нужен игрок и выбранный фонарь."));return 0;}RpObjectEntity lamp=ObjectRegistry.objectAt(p);if(lamp==null){source.sendError(Text.literal("Наведитесь на нужный фонарь."));return 0;}var result=LampEditor.selectSource(p,lamp.getUuid());source.sendFeedback(()->Text.literal(result.reason()),false);return result.success()?1:0;}
 private static int lampEdit(ServerCommandSource source,LampEditor.Action action,String target,String line,String name,boolean both,boolean confirm){if(!(source.getEntity() instanceof ServerPlayerEntity p)){source.sendError(Text.literal("Нужен игрок."));return 0;}UUID destination=null;try{if(target!=null)destination=UUID.fromString(target);}catch(IllegalArgumentException bad){source.sendError(Text.literal("Укажите UUID экземпляра фонаря назначения, не UUID узла."));return 0;}var result=LampEditor.edit(p,new LampEditor.Request(action,null,destination,line,name,both,confirm));source.sendFeedback(()->Text.literal(result.reason()),false);return result.success()?1:0;}
 private static int lampLineView(ServerCommandSource source){if(!(source.getEntity() instanceof ServerPlayerEntity p))return 0;var view=LampEditor.view(p,null);source.sendFeedback(()->Text.literal(new com.google.gson.Gson().toJson(view)),false);return Boolean.TRUE.equals(view.get("success"))?1:0;}
}
