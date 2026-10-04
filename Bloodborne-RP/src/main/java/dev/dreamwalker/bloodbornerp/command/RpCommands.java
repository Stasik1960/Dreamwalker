package dev.dreamwalker.bloodbornerp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.dreamwalker.bloodbornerp.lamp.LampService;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** OP-only world-edit endpoints. Targeting is constrained to a loaded nearby RP object. */
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
  lamp.then(routeCommand("link",true)).then(routeCommand("unlink",false));
  var route=literal("route");route.then(routeCommand("open",true)).then(routeCommand("close",false));lamp.then(route);root.then(lamp);dispatcher.register(root);
 });}
 private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> routeCommand(String name,boolean open){return literal(name).then(argument("from",StringArgumentType.word()).then(argument("to",StringArgumentType.word()).executes(c->lampRoute(c.getSource(),StringArgumentType.getString(c,"from"),StringArgumentType.getString(c,"to"),open))));}
 private static int object(ServerCommandSource source,String operation,String target){if(!(source.getEntity() instanceof ServerPlayerEntity player)){source.sendError(Text.translatable("command.bloodborne_rp.player_required"));return 0;}RpObjectEntity object=ObjectRegistry.objectAt(player);if(object==null){source.sendError(Text.translatable("command.bloodborne_rp.object_required"));return 0;}if(operation.equals("rotate")){object.setYaw(object.getYaw()+90);object.refreshCollider();source.sendFeedback(()->Text.translatable("command.bloodborne_rp.done"),false);return 1;}if(operation.equals("delete")){LampService.removeByEntity(source.getServer(),object.getUuid());object.discard();return 1;}if(operation.equals("lock")){object.setLocked(true);return 1;}if(operation.equals("unlock")){object.setLocked(false);return 1;}if(operation.equals("list")){source.sendFeedback(()->Text.literal(object.links().toString()),false);return 1;}try{UUID id=UUID.fromString(target);if(operation.equals("unlink")&&object.removeLink(id))return 1;var entity=player.getServerWorld().getEntity(id);if(entity instanceof RpObjectEntity destination&&object.addLink(destination))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.link_rejected"));return 0;}
 private static int lampRegister(ServerCommandSource source,String name){if(!(source.getEntity() instanceof ServerPlayerEntity player)||name.isBlank()||name.length()>64||!LampService.register(player,name)){source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}return 1;}
 private static int lampRename(ServerCommandSource source,String raw,String name){try{if(LampService.rename(source.getServer(),UUID.fromString(raw),name))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}
 private static int lampRemove(ServerCommandSource source,String raw){try{if(LampService.remove(source.getServer(),UUID.fromString(raw)))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}
 private static int lampRoute(ServerCommandSource source,String from,String to,boolean open){try{if(LampService.route(source.getServer(),UUID.fromString(from),UUID.fromString(to),open))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.lamp_rejected"));return 0;}
 private static int lampList(ServerCommandSource source){for(LampService.LampInfo lamp:LampService.list(source.getServer()))source.sendFeedback(()->Text.literal(lamp.id()+" "+lamp.name()),false);return 1;}
}
