package dev.dreamwalker.bloodbornerp.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** OP-only object commands; lamp authoring belongs to the construction tool. */
public final class RpCommands {
 private static boolean registered;private RpCommands(){}
 public static void register(){if(registered)return;registered=true;CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
  var root=literal("bbrp").requires(source->source.hasPermissionLevel(2));
  root.then(literal("object").then(literal("rotate").executes(c->object(c.getSource(),"rotate",null))).then(literal("delete").executes(c->object(c.getSource(),"delete",null))).then(literal("lock").executes(c->object(c.getSource(),"lock",null))).then(literal("unlock").executes(c->object(c.getSource(),"unlock",null))).then(literal("list").executes(c->object(c.getSource(),"list",null))).then(literal("link").then(argument("target",StringArgumentType.word()).executes(c->object(c.getSource(),"link",StringArgumentType.getString(c,"target"))))).then(literal("unlink").then(argument("target",StringArgumentType.word()).executes(c->object(c.getSource(),"unlink",StringArgumentType.getString(c,"target"))))));
  dispatcher.register(root);
 });}
 private static int object(ServerCommandSource source,String operation,String target){if(!(source.getEntity() instanceof ServerPlayerEntity player)){source.sendError(Text.translatable("command.bloodborne_rp.player_required"));return 0;}RpObjectEntity object=ObjectRegistry.objectAt(player);if(object==null){source.sendError(Text.translatable("command.bloodborne_rp.object_required"));return 0;}if(operation.equals("rotate")){object.setYaw(object.getYaw()+90);object.refreshCollider();source.sendFeedback(()->Text.translatable("command.bloodborne_rp.done"),false);return 1;}if(operation.equals("delete")){object.removeObject();return 1;}if(operation.equals("lock")){object.setLocked(true);return 1;}if(operation.equals("unlock")){object.setLocked(false);return 1;}if(operation.equals("list")){source.sendFeedback(()->Text.literal(object.links().toString()),false);return 1;}try{UUID id=UUID.fromString(target);if(operation.equals("unlink")&&object.removeLink(id))return 1;var entity=player.getServerWorld().getEntity(id);if(entity instanceof RpObjectEntity destination&&object.addLink(destination))return 1;}catch(IllegalArgumentException ignored){}source.sendError(Text.translatable("command.bloodborne_rp.link_rejected"));return 0;}
}
