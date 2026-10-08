package dev.dreamwalker.bloodbornedw.diagnostics;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Operator controls; object snapshots do not edit state or grant build rights. */
final class DwDiagnosticCommands {
    private DwDiagnosticCommands(){}
    static LiteralArgumentBuilder<ServerCommandSource> tree(){
        var start=CommandManager.literal("start").executes(c->start(c.getSource(),60,"all","",0));
        start.then(filters(CommandManager.argument("seconds",IntegerArgumentType.integer(1,600))));
        start.then(CommandManager.literal("id").then(CommandManager.argument("type",StringArgumentType.word()).executes(c->start(c.getSource(),60,"id",StringArgumentType.getString(c,"type"),0))));
        start.then(CommandManager.literal("object").executes(c->object(c.getSource(),60)).then(CommandManager.argument("uuid",StringArgumentType.word()).executes(c->start(c.getSource(),60,"object",StringArgumentType.getString(c,"uuid"),0))));
        start.then(CommandManager.literal("area").then(CommandManager.argument("radius",DoubleArgumentType.doubleArg(1,128)).executes(c->start(c.getSource(),60,"area","",DoubleArgumentType.getDouble(c,"radius")))));
        return CommandManager.literal("diagnostics").requires(s->s.hasPermissionLevel(2)).then(start)
            .then(CommandManager.literal("stop").executes(c->{boolean done=DwDiagnostics.stop(c.getSource().getServer(),"OPERATOR_STOP");c.getSource().sendFeedback(()->Text.literal(done?"Диагностика остановлена.":"Активного сеанса нет."),false);return done?1:0;}))
            .then(CommandManager.literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal(DwDiagnostics.json(DwDiagnostics.status(c.getSource().getServer()))),false);return 1;}))
            .then(CommandManager.literal("mark").then(CommandManager.argument("note",StringArgumentType.greedyString()).executes(c->{DwDiagnostics.mark(c.getSource().getWorld(),StringArgumentType.getString(c,"note"));c.getSource().sendFeedback(()->Text.literal("Пометка сохранена, если сеанс активен."),false);return 1;})))
            .then(CommandManager.literal("snapshot").executes(c->DwDiagnostics.snapshotCommand(c.getSource(),"")).then(CommandManager.argument("note",StringArgumentType.greedyString()).executes(c->DwDiagnostics.snapshotCommand(c.getSource(),StringArgumentType.getString(c,"note")))))
            .then(CommandManager.literal("export").executes(c->{var source=c.getSource();DwDiagnostics.export(source.getServer()).thenAccept(path->source.getServer().execute(()->{if(path==null)source.sendError(Text.literal("ZIP не записан; мир продолжает работу. Причина в журнале диагностики."));else source.sendFeedback(()->Text.literal("Диагностический ZIP: "+path),false);}));source.sendFeedback(()->Text.literal("Экспорт поставлен в ограниченную очередь записи."),false);return 1;}));
    }
    private static RequiredArgumentBuilder<ServerCommandSource,Integer> filters(RequiredArgumentBuilder<ServerCommandSource,Integer> seconds){
        return seconds.executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"seconds"),"all","",0))
            .then(CommandManager.literal("id").then(CommandManager.argument("type",StringArgumentType.word()).executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"seconds"),"id",StringArgumentType.getString(c,"type"),0))))
            .then(CommandManager.literal("object").executes(c->object(c.getSource(),IntegerArgumentType.getInteger(c,"seconds"))).then(CommandManager.argument("uuid",StringArgumentType.word()).executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"seconds"),"object",StringArgumentType.getString(c,"uuid"),0))))
            .then(CommandManager.literal("area").then(CommandManager.argument("radius",DoubleArgumentType.doubleArg(1,128)).executes(c->start(c.getSource(),IntegerArgumentType.getInteger(c,"seconds"),"area","",DoubleArgumentType.getDouble(c,"radius")))));
    }
    private static int start(ServerCommandSource source,int seconds,String mode,String selected,double radius){
        try{if(mode.equals("object"))java.util.UUID.fromString(selected);var filter=new DwDiagnostics.Filter(mode,mode.equals("id")?selected:"",mode.equals("object")?selected:"",source.getWorld().getRegistryKey().getValue().toString(),BlockPos.ofFloored(source.getPosition()),radius);var id=DwDiagnostics.start(source.getWorld(),seconds,filter);source.sendFeedback(()->Text.literal("Диагностика "+id+" · "+seconds+"с · "+mode+". Автоматическая остановка по длительности."),false);return 1;}
        catch(IllegalArgumentException bad){source.sendError(Text.literal("Фильтр: ID должен содержать5 цифр; object — UUID; область1..128 блоков."));return 0;}
    }
    private static int object(ServerCommandSource source,int seconds){
        if(!(source.getEntity() instanceof ServerPlayerEntity)){source.sendError(Text.literal("Для выбранного объекта нужен игрок."));return 0;}
        var snapshot=DwDiagnosticSnapshots.selected(source,"filter-selected-object");if(snapshot.isEmpty()||!snapshot.containsKey("root")){source.sendError(Text.literal("Загруженный объект не выбран."));return 0;}
        var coords=(java.util.List<?>)snapshot.get("root");BlockPos root=new BlockPos(((Number)coords.get(0)).intValue(),((Number)coords.get(1)).intValue(),((Number)coords.get(2)).intValue());String instance=String.valueOf(snapshot.getOrDefault("instanceId",""));if(instance.startsWith("root:"))instance="";
        var filter=new DwDiagnostics.Filter("object","",instance,source.getWorld().getRegistryKey().getValue().toString(),root,0);var id=DwDiagnostics.start(source.getWorld(),seconds,filter);source.sendFeedback(()->Text.literal("Диагностика "+id+" · только выбранный объект."),false);return 1;
    }
}
