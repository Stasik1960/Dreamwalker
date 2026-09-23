package dev.dreamwalker.bloodborneblocks;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

final class BloodborneCommands {
 private BloodborneCommands(){}
 static void register(){
  CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->dispatcher.register(literal("bloodborne").requires(source->source.hasPermissionLevel(2)).then(literal("repair").then(argument("radius",IntegerArgumentType.integer(1,32))
   .then(literal("preview").executes(context->run(context.getSource(),IntegerArgumentType.getInteger(context,"radius"),false)))
   .then(literal("apply").executes(context->run(context.getSource(),IntegerArgumentType.getInteger(context,"radius"),true)))))
   .then(literal("update").then(argument("radius",IntegerArgumentType.integer(1,32))
    .then(literal("preview").executes(context->update(context.getSource(),IntegerArgumentType.getInteger(context,"radius"),false)))
    .then(literal("apply").executes(context->update(context.getSource(),IntegerArgumentType.getInteger(context,"radius"),true)))))
   .then(literal("debug").executes(context->debug(context.getSource())).then(literal("target").executes(context->debug(context.getSource()))))
   .then(LogicalVisualCommands.command())));
  LogicalVisualCommands.registerLifecycle();
 }
 private static int run(ServerCommandSource source,int radius,boolean apply){
  BlockPos center=BlockPos.ofFloored(source.getPosition());GeometryRuntime.RepairResult result=GeometryRuntime.repair(source.getWorld(),center,radius,apply);
  source.sendFeedback(()->Text.literal("Bloodborne repair "+(apply?"apply":"preview")+": roots="+result.roots()+", repaired="+result.repaired()+", conflicts="+result.conflicts()+", orphans="+result.orphans()),false);
  return result.conflicts()==0?result.roots():0;
 }
 private static int update(ServerCommandSource source,int radius,boolean apply){
  var result=PaletteMigration.update(source.getWorld(),BlockPos.ofFloored(source.getPosition()),radius,apply);
  source.sendFeedback(()->Text.literal("Bloodborne "+(apply?"обновлено":"предпросмотр")+": мусор="+result.removed()+", дубликаты="+result.replaced()+", восстановлено="+result.repaired()+", пропущены конфликты="+result.conflicts()+", потерянные части="+result.orphans()),false);
  return result.removed()+result.replaced()+result.repaired();
 }
 private static int debug(ServerCommandSource source){
  if(!(source.getEntity() instanceof ServerPlayerEntity player)){source.sendError(Text.literal("Bloodborne debug: player target required."));return 0;}
  String report=LogicalTargetDebug.inspect(player);
  source.sendFeedback(()->Text.literal(report).styled(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD,report))),false);
  return report.startsWith("Bloodborne debug:")?0:1;
 }
}
