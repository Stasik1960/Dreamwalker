package dev.dreamwalker.bloodbornedw.diagnostics;

import com.google.gson.*;
import dev.dreamwalker.bloodbornedw.architecture.*;
import dev.dreamwalker.bloodbornedw.architecture.ladder_source.SourceLadderBlockEntity;
import dev.dreamwalker.bloodbornedw.link.MechanismLinks;
import dev.dreamwalker.bloodbornedw.link.MechanismState;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Real command, tool and ZIP paths; runtime-client proof remains a separate ordinary client run. */
public final class DwDiagnosticsGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=20,batchId="dw_diagnostics_buffers")
    public void boundedQuantilesKeepWholeCountsAndNeverSerializeFullNbt(TestContext context){
        var durations=new DiagnosticBuffers.Durations(4);for(long n=1;n<=10;n++)durations.add(n);
        var report=durations.report();context.assertTrue(((Number)report.get("allCount")).longValue()==10&&((Number)report.get("allSumNs")).longValue()==55,"whole count/sum remain exact after window eviction");
        context.assertTrue(((Number)report.get("retained")).intValue()==4&&((Number)report.get("dropped")).intValue()==6&&report.get("quantileScope").equals("RETAINED_WINDOW_ONLY"),"retained-window quantiles expose all dropped samples");
        context.assertTrue(((Number)report.get("p95Ns")).longValue()==10&&((Number)report.get("p99Ns")).longValue()==10,"bounded quantiles reflect retained population");
        NbtCompound secret=new NbtCompound();secret.putString("PrivateItemPayload","do-not-export");var safe=DiagnosticBuffers.safeMap(Map.of("payload",secret,"list",Collections.nCopies(100,"x")));
        context.assertTrue(String.valueOf(safe.get("payload")).equals("NOT_CAPTURED:NbtCompound")&&!DwDiagnostics.json(safe).contains("do-not-export"),"arbitrary NBT is excluded rather than traversed");
        context.assertTrue(((List<?>)safe.get("list")).size()==33,"event collections expose bounded truncation marker");context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=20,batchId="dw_diagnostics_corruption")
    public void corruptSavedBindingsAndSourcePairReportErrorsWithoutChangingPriorNormalization(TestContext context){
        BlockPos root=context.getAbsolutePos(new BlockPos(1,2,1));UUID owner=UUID.randomUUID(),lever=UUID.randomUUID();var ref=new MechanismLinks.TargetRef(MechanismLinks.Kind.ARCHITECTURE,"minecraft:overworld",owner,root.asLong(),"bloodborne_dw:prototype_double_door");
        NbtCompound graph=new NbtCompound();graph.putInt("Schema",1);NbtCompound target=new NbtCompound();target.put("Ref",ref.write());target.putBoolean("Desired",true);target.putBoolean("Known",true);target.putBoolean("Pending",true);NbtCompound damagedTarget=target.copy();damagedTarget.getCompound("Ref").putString("Instance","invalid-uuid");NbtList targets=new NbtList();targets.add(target);targets.add(damagedTarget);graph.put("Targets",targets);
        NbtCompound source=new NbtCompound();source.putUuid("Id",lever);source.putString("Dimension","minecraft:overworld");source.putString("Asset","lever_1");source.putLong("Pos",root.asLong());NbtList keys=new NbtList();keys.add(NbtString.of(ref.key()));source.put("Targets",keys);NbtCompound damagedLever=source.copy();damagedLever.putString("Id","invalid-uuid");NbtList levers=new NbtList();levers.add(source);levers.add(damagedLever);graph.put("Levers",levers);NbtCompound inputBefore=graph.copy();
        NbtCompound normalized=MechanismState.fromNbt(graph).writeNbt(new NbtCompound());context.assertTrue(graph.equals(inputBefore)&&normalized.getList("Targets",NbtElement.COMPOUND_TYPE).size()==1&&normalized.getList("Levers",NbtElement.COMPOUND_TYPE).size()==1,"actual saved graph reader keeps prior skip behavior and never edits its typed input");
        context.assertTrue(normalized.getList("Targets",NbtElement.COMPOUND_TYPE).getCompound(0).getCompound("Ref").equals(ref.write())&&normalized.getList("Targets",NbtElement.COMPOUND_TYPE).getCompound(0).getBoolean("Pending")&&normalized.getList("Levers",NbtElement.COMPOUND_TYPE).getCompound(0).getUuid("Id").equals(lever)&&normalized.getList("Levers",NbtElement.COMPOUND_TYPE).getCompound(0).getList("Targets",NbtElement.STRING_TYPE).getString(0).equals(ref.key()),"unaffected exact UUID binding and pending command survive neighboring corrupt records");
        context.assertTrue(DwDiagnostics.errors().stream().anyMatch(row->("mechanism_target:"+root.toShortString()).equals(row.get("instanceId"))&&"MECHANISM_LINK_TARGET_CORRUPTION".equals(row.get("category")))&&DwDiagnostics.errors().stream().anyMatch(row->("mechanism_lever:"+root.toShortString()).equals(row.get("instanceId"))&&"MECHANISM_LINK_LEVER_CORRUPTION".equals(row.get("category"))),"previously silent malformed UUID records identify both target and lever in the error journal");
        NbtCompound wrongSchema=graph.copy();wrongSchema.putInt("Schema",99);context.assertTrue(MechanismState.fromNbt(wrongSchema).writeNbt(new NbtCompound()).getList("Targets",NbtElement.COMPOUND_TYPE).isEmpty(),"unsupported schema retains previous empty graph fallback");context.assertTrue(DwDiagnostics.errors().stream().anyMatch(row->"MECHANISM_LINKS_SCHEMA".equals(row.get("category"))),"unsupported graph schema now has a diagnostic reason");
        var pair=new SourceLadderBlockEntity(root,PrototypeArchitecture.LADDER.getDefaultState().with(PrototypeLadderBlock.SOURCE_CLONE,true));pair.setWorld(context.getWorld());NbtCompound valid=new NbtCompound();valid.putUuid("Owner",owner);valid.putLong("Root",root.asLong());valid.putLong("FixedBacking",root.north().asLong());NbtCompound original=new NbtCompound();original.putString("PrivateSourcePayload","opaque-original-must-never-appear");valid.put("Original",original);pair.readNbt(valid);context.assertTrue(owner.equals(pair.owner())&&pair.root().equals(root)&&pair.backing().equals(root.north())&&pair.provenance().equals(original),"valid source-pair identity and opaque provenance retain their existing semantics");
        NbtCompound damaged=valid.copy();damaged.putString("Owner","invalid-uuid");damaged.putString("Root","wrong-type");damaged.putString("FixedBacking","wrong-type");damaged.putString("Original","opaque-original-must-never-appear");NbtCompound damagedBefore=damaged.copy();pair.readNbt(damaged);pair.readNbt(damaged);context.assertTrue(pair.owner()==null&&pair.root().equals(BlockPos.ORIGIN)&&pair.backing().equals(BlockPos.ORIGIN)&&pair.provenance().isEmpty()&&damaged.equals(damagedBefore),"source-pair malformed fields keep original normalization and do not rewrite source input");
        var errors=DwDiagnostics.errors().stream().filter(row->ArchitectureDiagnostics.rootId(root).equals(row.get("instanceId"))&&"SOURCE_LADDER_CORRUPTION".equals(row.get("category"))).toList();context.assertTrue(errors.size()==1&&((Number)errors.get(0).get("repeats")).longValue()==2&&!DwDiagnostics.json(errors).contains("opaque-original-must-never-appear"),"repeated corrupt source-pair reads deduplicate with counts and never capture original NBT");context.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=2000,batchId="dw_diagnostics_session")
    public void operatorCommandsFilterEventsSnapshotWithoutEditsAndExportAfterAutoStop(TestContext context){
        var world=context.getWorld();var server=world.getServer();DwDiagnostics.stop(server,"TEST_SETUP");context.assertTrue(!DwDiagnostics.enabled(world),"detailed diagnostics are disabled before command");
        var source=server.getCommandSource().withWorld(world);var node=server.getCommandManager().getDispatcher().getRoot().getChild("bb").getChild("diagnostics");
        context.assertTrue(node!=null&&!node.canUse(source.withLevel(0))&&node.canUse(source.withLevel(2)),"operator permission2 gates diagnostic controls");
        int result=server.getCommandManager().executeWithPrefix(source,"bb diagnostics start 3 id 90006");context.assertTrue(result==1&&DwDiagnostics.enabled(world),"actual registered start command enables bounded three-second session");
        BlockPos root=context.getAbsolutePos(new BlockPos(1,2,1));world.setBlockState(root,PrototypeArchitecture.LADDER.getDefaultState());world.setBlockState(root.east(),Blocks.CHEST.getDefaultState());var chest=world.getBlockEntity(root.east());NbtCompound chestBefore=chest.createNbt();ServerPlayerEntity player=context.createMockCreativeServerPlayerInWorld();player.getAbilities().creativeMode=true;
        var before=world.getBlockState(root);ItemStack tool=new ItemStack(PrototypeArchitecture.BUILDER_TOOL);tool.getOrCreateNbt().putInt("BuilderAction",BuildingTool.Action.DIAGNOSTICS.ordinal());player.setStackInHand(Hand.MAIN_HAND,tool);
        var hit=new BlockHitResult(Vec3d.ofCenter(root),Direction.NORTH,root,false);context.assertTrue(tool.getItem().useOnBlock(new ItemUsageContext(player,Hand.MAIN_HAND,hit)).isAccepted(),"V10 builderRMB entersmenu and doesnotmutateobject");
        DwDiagnostics.snapshot(world,player,root,Map.of("note","technicalserver snapshot; actual V10 GUI proof is separate"));
        context.assertTrue(world.getBlockState(root).equals(before)&&tool.getCount()==1,"snapshot neither edits target nor consumes item");
        long previous=((Number)((Map<?,?>)DwDiagnostics.status(server).get("events")).get("allCount")).longValue();DwDiagnostics.record(world,"90004","foreign",root,"refuse",Map.of(),Map.of(),"REFUSED","ordinary overlap");
        context.assertTrue(((Number)((Map<?,?>)DwDiagnostics.status(server).get("events")).get("allCount")).longValue()==previous,"ID filter excludes a different object type");
        int errorsBefore=DwDiagnostics.errors().size();for(int n=0;n<2200;n++)DwDiagnostics.record(world,"90006","root:"+root.getX()+","+root.getY()+","+root.getZ(),root,"refuse",Map.of(),Map.of(),"REFUSED","ordinary overlap");
        context.assertTrue(DwDiagnostics.errors().size()==errorsBefore,"ordinary collision refusal does not enter error journal");var counts=(Map<?,?>)DwDiagnostics.status(server).get("events");context.assertTrue(((Number)counts.get("retained")).intValue()==2048&&((Number)counts.get("dropped")).longValue()>0,"events cannot grow beyond2048 rows");
        String identity=UUID.randomUUID().toString();for(int n=0;n<2;n++)DwDiagnostics.error(world,"90006",identity,root,"TEST_INVALID_ID","intentional diagnostic test",null);
        long repetitions=DwDiagnostics.errors().stream().filter(e->identity.equals(e.get("instanceId"))).mapToLong(e->((Number)e.get("repeats")).longValue()).sum();context.assertTrue(repetitions==2,"identical errors are deduplicated with repeat count");
        server.getCommandManager().executeWithPrefix(source,"bb diagnostics mark test-mark");
        final CompletableFuture<Path>[] export=new CompletableFuture[2];final boolean[] requested={false};final long waitedFrom=System.nanoTime();
        context.runAtTick(1900,()->{throw new AssertionError("Real-time diagnostic session did not complete: elapsedNs="+(System.nanoTime()-waitedFrom)+" status="+DwDiagnostics.json(DwDiagnostics.status(server))+" exportRequested="+requested[0]+" exportReady="+(export[0]!=null&&export[0].isDone())+" failedIoReady="+(export[1]!=null&&export[1].isDone()));});
        context.runAtEveryTick(()->{
            if(!requested[0]&&!Boolean.TRUE.equals(DwDiagnostics.status(server).get("enabled"))){requested[0]=true;context.assertTrue(DwDiagnostics.status(server).get("stopReason").equals("AUTO_DURATION_EXPIRED"),"real server tick automatically stops elapsed wall-clock session");export[0]=DwDiagnostics.export(server,Path.of("diagnostics-gametest"));try{Path notDirectory=Files.createTempFile("dw-diagnostics-not-directory-",".tmp");export[1]=DwDiagnostics.export(server,notDirectory);export[1].thenRun(()->{try{Files.deleteIfExists(notDirectory);}catch(Exception ignored){}});}catch(Exception error){throw new AssertionError(error);}}
            if(export[0]!=null&&export[0].isDone()&&export[1]!=null&&export[1].isDone()){
                Path path=export[0].join();context.assertTrue(path!=null&&Files.isRegularFile(path),"asynchronous local ZIP completed without blocking world");
                context.assertTrue(export[1].join()==null&&world.getBlockEntity(root.east())==chest&&chest.createNbt().equals(chestBefore),"real report I/O failure returns nonfatal result and preserves foreign state/NBT/instance");
                try(ZipFile zip=new ZipFile(path.toFile())){context.assertTrue(zip.getEntry("events.jsonl")!=null&&zip.getEntry("object-snapshots.json")!=null&&zip.getEntry("measurements.json")!=null,"ZIP contains events, metrics and object snapshots");String data=new String(zip.getInputStream(zip.getEntry("events.jsonl")).readAllBytes(),StandardCharsets.UTF_8);for(String line:data.lines().toList())JsonParser.parseString(line).getAsJsonObject();var session=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("session.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();context.assertTrue(session.get("stopReason").getAsString().equals("AUTO_DURATION_EXPIRED"),"archive records actual automatic-stop reason");String summary=new String(zip.getInputStream(zip.getEntry("summary.md")).readAllBytes(),StandardCharsets.UTF_8);context.assertTrue(summary.contains(session.get("sessionId").getAsString())&&summary.contains("Server ticks:")&&summary.contains("GPU timing: NOT_MEASURED.")&&summary.contains("DataTracker")&&!summary.contains("\uFFFD")&&!summary.contains("\u0420\u0491\u0420"),"actual ZIP summary is readable, identifies its session and states unavailable GPU/network scopes");}catch(Exception error){throw new AssertionError("Diagnostic ZIP validation failed",error);}
                world.setBlockState(root,Blocks.AIR.getDefaultState());world.setBlockState(root.east(),Blocks.AIR.getDefaultState());player.discard();context.complete();
            }
        });
    }
}
