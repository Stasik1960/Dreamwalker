package dev.dreamwalker.bloodbornedw.link;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.dreamwalker.bloodbornedw.architecture.SourceConversionScope;
import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.debug.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.tool.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Independent saved V12.5 previews, transactional ownership and durable
 * instance configuration. Package-local codecs are used only to load genuine
 * old typed persistent records, including references to consumed fragments. */
public final class AssemblyV126GameTests implements FabricGameTest {
    private static final Map<String,String> HIDDEN=Map.of("93069","93066","90178","90179","90251","90301","90142","90143","90799","90797","90623","90031","90479","90001");
    private static List<JsonObject> assemblies() {
        var input=AssemblyV126GameTests.class.getResourceAsStream("/bloodborne_dw/v126-assembly-expectations.json");
        if(input==null)throw new IllegalStateException("Missing independently saved V12.5 assembly references");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){List<JsonObject> result=new ArrayList<>();for(var row:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("assemblies"))result.add(row.getAsJsonObject());return List.copyOf(result);}
        catch(Exception failure){throw new IllegalStateException("Invalid V12.5 assembly reference",failure);}
    }
    private static String registry(String number){return DebugCatalogue.entries().stream().filter(row->row.temporaryId().equals(number)).findFirst().orElseThrow().registryId().toString();}
    private static CompositeRootBlock block(String registry){return (CompositeRootBlock)Registries.BLOCK.get(new Identifier(registry));}
    private static BlockState pose(String registry,int pose,int yaw){var value=block(registry).getDefaultState().with(CompositeRootBlock.VARIANT,pose%16).with(CompositeRootBlock.ROTATION,yaw);if(value.getBlock() instanceof ImportedArtworkBlock)value=value.with(ImportedArtworkBlock.bankProperty(value),pose/16);return value;}
    private static ServerPlayerEntity player(ServerWorld world) {
        var value=new ServerPlayerEntity(world.getServer(),world,new GameProfile(UUID.randomUUID(),"Assembly126Probe"));
        var connection=new ClientConnection(NetworkSide.SERVERBOUND){@Override public void send(Packet<?> packet){}@Override public void send(Packet<?> packet,PacketCallbacks callbacks){}};
        value.networkHandler=new ServerPlayNetworkHandler(world.getServer(),connection,value);value.changeGameMode(net.minecraft.world.GameMode.CREATIVE);value.getAbilities().allowModifyWorld=true;value.refreshPositionAndAngles(0,180,0,0,0);return value;
    }
    private static void load(ServerWorld world,ObjectInstance value) {
        Set<ChunkPos> chunks=new HashSet<>();for(var cell:value.cells().keySet()){var pos=CompositeData.pos(value.owner().root().add(cell));for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)chunks.add(new ChunkPos((pos.getX()>>4)+dx,(pos.getZ()>>4)+dz));}
        for(var chunk:chunks){world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(new BlockPos(chunk.x<<4,0,chunk.z<<4));}
    }
    private static NbtList shift(JsonArray coordinates){var values=new NbtList();for(var value:coordinates)values.add(NbtDouble.of(value.getAsDouble()));return values;}
    /** Apply only a common extra yaw. The middle90797 section deliberately
     * keeps the opposite source yaw recorded in the actual old preview. */
    private static List<ObjectInstance> before(TestContext t,BlockPos root,JsonObject assembly,int extraYaw) {
        List<ObjectInstance> result=new ArrayList<>();int index=0;
        for(var raw:assembly.getAsJsonArray("components")) {
            var component=raw.getAsJsonObject();var xyz=component.getAsJsonArray("relativePosition");
            t.assertTrue(xyz.get(0).getAsInt()==0&&xyz.get(2).getAsInt()==0,"reviewed source fixture has exact vertical relative roots");
            var pos=root.up(xyz.get(1).getAsInt());var properties=component.getAsJsonObject("properties");
            int yaw=(properties.get("rotation").getAsInt()+extraYaw)&7;
            var state=pose(component.get("registryId").getAsString(),properties.get("variant").getAsInt(),yaw)
                .with(CompositeRootBlock.PROFILE,properties.get("profile").getAsString().equals("alt")?CompositeRootBlock.Profile.ALT:CompositeRootBlock.Profile.BASE)
                .with(CompositeRootBlock.OPEN,properties.get("open").getAsBoolean());
            var data=new NbtCompound();data.putInt(ImportedTypeAliases.VERSION_KEY,1);data.put("SourceShift",shift(component.getAsJsonArray("sourceShift")));
            data.putDouble("MountY",.125);data.putDouble("VerticalOffset",.375);data.putLong("OpaqueLong",Long.MIN_VALUE+index);data.putString("OpaqueLabel","часть "+index);data.putUuid("PrivateLinkedUuid",UUID.randomUUID());data.putIntArray("OpaqueArray",new int[]{index,-17,23});
            var value=CompositeRuntime.instance(t.getWorld(),pos,state,UUID.randomUUID(),data);load(t.getWorld(),value);result.add(value);index++;
        }
        var afterState=pose(assembly.get("primaryRegistryId").getAsString(),assembly.get("installationPose").getAsInt(),CompositeData.state(result.get(0).rootData()).get(CompositeRootBlock.ROTATION));
        load(t.getWorld(),CompositeRuntime.instance(t.getWorld(),root,afterState,result.get(0).owner().instanceId(),CompositeData.nbt(result.get(0).rootData().blockEntityNbt())));
        return List.copyOf(result);
    }
    private static void installSaved(TestContext t,List<ObjectInstance> values) {
        Set<UUID> initial=new HashSet<>();values.forEach(value->initial.add(value.owner().instanceId()));
        SourceConversionScope.initialInstances(initial,()->{
            for(var value:values) {
                var root=CompositeData.pos(value.owner().root());var state=CompositeData.state(value.rootData());var data=CompositeData.nbt(value.rootData().blockEntityNbt());
                var access=new FabricCompositeWorld(t.getWorld(),Map.of(value.owner(),value));
                t.assertTrue(CompositeRuntime.execute(access,CompositeRuntime.core().placement(access,value,List.of())).outcome()==TransactionCore.Outcome.COMMITTED,"typed V12.5 saved-preview fragment installs without ordinary-item payload normalization "+value.owner().registryId());
                var entity=(CompositeBlockEntity)t.getWorld().getBlockEntity(root);var saved=entity.createNbt();entity.readNbt(saved);
                t.assertTrue(entity.resident().equals(value.owner())&&entity.payload().equals(data),"old native saved root/typed payload reads without art canonicalization");
            }
            return true;
        });
        for(var value:values)t.assertTrue(!SourceConversionScope.initialInstance(value.owner().instanceId()),"initial saved-fixture overlap privilege ends before the actual migration");
    }
    private static ObjectInstance current(ServerWorld world,BlockPos root){var entity=(CompositeBlockEntity)world.getBlockEntity(root);return CompositeRuntime.instance(world,root,world.getBlockState(root),entity.resident().instanceId(),entity.payload());}
    private static void verifyMerged(TestContext t,JsonObject assembly,List<ObjectInstance> old,BlockPos root) {
        var world=t.getWorld();var entity=(CompositeBlockEntity)world.getBlockEntity(root);var owner=entity.resident();var state=world.getBlockState(root);
        t.assertTrue(owner.instanceId().equals(old.get(0).owner().instanceId())&&owner.root().equals(old.get(0).owner().root())&&owner.registryId().equals(assembly.get("primaryRegistryId").getAsString()),"complete assembly keeps the old anchor UUID/root and selected existing primary ID");
        t.assertTrue(CompositeSpec.poseVariant(state)==assembly.get("installationPose").getAsInt()&&state.get(CompositeRootBlock.ROTATION)==CompositeData.state(old.get(0).rootData()).get(CompositeRootBlock.ROTATION),"exact completed state retains source yaw");
        var anchor=CompositeData.nbt(old.get(0).rootData().blockEntityNbt());var data=entity.payload();for(String key:anchor.getKeys())t.assertTrue(anchor.get(key).equals(data.get(key)),"anchor's exact typed setting survives: "+key);
        var archive=data.getList(ImportedAssemblyV126.PARTS_KEY,10);t.assertTrue(archive.size()==old.size(),"every old part remains in typed provenance");
        for(var previous:old) {
            var saved=archive.stream().map(value->(NbtCompound)value).filter(value->CompositeData.owner(value.getCompound("Owner")).equals(previous.owner())).findFirst().orElseThrow();
            t.assertTrue(CompositeData.data(saved.getCompound("RootData")).equals(previous.rootData()),"each consumed member's exact old state/payload/UUID remains recoverable");
            for(var offset:previous.cells().keySet()) {
                var pos=CompositeData.pos(previous.owner().root().add(offset));
                t.assertTrue(CompositeRuntime.contributions(world,pos).stream().noneMatch(value->!value.owner().equals(owner)&&value.owner().equals(previous.owner())),"no consumed old owner contribution survives");
            }
            var oldRoot=CompositeData.pos(previous.owner().root());if(!oldRoot.equals(root))t.assertTrue(!(world.getBlockEntity(oldRoot) instanceof CompositeBlockEntity other)||other.resident()==null,"a consumed fragment root becomes only a helper or free cell");
        }
        var next=current(world,root);for(var entry:next.cells().entrySet()) {
            var pos=CompositeData.pos(owner.root().add(entry.getKey()));var actual=CompositeRuntime.contributions(world,pos).stream().filter(value->value.owner().equals(owner)).toList();
            t.assertTrue(actual.size()==1&&actual.get(0).shape().equals(entry.getValue()),"one complete owner has exact physical/selection contribution at every cell");
            t.assertTrue(entry.getValue().collision().isEmpty()||entry.getValue().selection().equals(entry.getValue().collision()),"completed object's outline follows its simplified actual collider");
        }
        var saved=entity.createNbt();var result=ImportedAssemblyV126.migrateLoadedRoot(world,owner);
        t.assertTrue(result.outcome()!=TransactionCore.Outcome.COMMITTED&&saved.equals(((CompositeBlockEntity)world.getBlockEntity(root)).createNbt()),"repeated migration is exactly idempotent");
        t.assertTrue(CompositeRuntime.remove(world,owner,null,false).outcome()==TransactionCore.Outcome.COMMITTED,"complete merged object removes in one transaction");CompositeRuntime.drain(world);
        for(var offset:next.cells().keySet())t.assertTrue(CompositeRuntime.contributions(world,CompositeData.pos(owner.root().add(offset))).stream().noneMatch(value->value.owner().equals(owner)),"whole removal leaves no helper orphan");
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=240,batchId="v126_exact_old_assembly_migration")
    public void allEightApprovedSavedCompositionsMergeAtEveryYawWithoutLosingMemberPayloadOrOwners(TestContext t) {
        var fixtures=assemblies();t.assertTrue(fixtures.size()==8,"all eight approved fixed compositions have exact independent old-preview fixtures");
        t.assertTrue(ImportedAssemblyV126.recipes().size()==9&&ImportedAssemblyV126.recipes().stream().map(ImportedAssemblyV126.Recipe::primaryRegistryId).distinct().count()==8,"eight functional types retain their approved recipes plus one90923 source-city mounting state");
        var root=t.getAbsolutePos(new BlockPos(20480,96,64));List<Runnable> cases=new ArrayList<>();
        for(var assembly:fixtures)for(int extra=0;extra<8;extra++){final int yaw=extra;cases.add(()->{
            var values=before(t,root,assembly,yaw);installSaved(t,values);
            var foreign=root.add(32,0,32);t.getWorld().getChunk(foreign);t.getWorld().setBlockState(foreign,Blocks.GOLD_BLOCK.getDefaultState(),Block.NOTIFY_ALL);
            var result=ImportedAssemblyV126.migrateLoadedRoot(t.getWorld(),values.get(0).owner());
            t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"exact V12.5 composition merges "+assembly.get("primaryId").getAsString()+" common yaw delta="+yaw+": "+result.reason());
            verifyMerged(t,assembly,values,root);t.assertTrue(t.getWorld().getBlockState(foreign).isOf(Blocks.GOLD_BLOCK),"assembly migration and full removal preserve unrelated native blocks");t.getWorld().removeBlock(foreign,false);
        });}
        for(int i=0;i<cases.size();i++){var action=cases.get(i);t.runAtTick(i+1,action);}t.runAtTick(cases.size()+2,t::complete);
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v126_lone_old_fragments")
    public void loneSavedFragmentsStayExactAndRejectedPairsAndSharedWindowPartsRemainIndependent(TestContext t) {
        var root=t.getAbsolutePos(new BlockPos(20544,96,64));
        for(var assembly:assemblies()) {
            var values=before(t,root,assembly,0);var lone=values.get(0);installSaved(t,List.of(lone));var entity=(CompositeBlockEntity)t.getWorld().getBlockEntity(root);var saved=entity.createNbt();var state=t.getWorld().getBlockState(root);
            var result=ImportedAssemblyV126.migrateLoadedRoot(t.getWorld(),lone.owner());
            t.assertTrue(result.outcome()!=TransactionCore.Outcome.COMMITTED&&state.equals(t.getWorld().getBlockState(root))&&saved.equals(((CompositeBlockEntity)t.getWorld().getBlockEntity(root)).createNbt()),"an incomplete isolated old part keeps exact art, UUID and payload "+assembly.get("primaryId").getAsString());
            CompositeRuntime.remove(t.getWorld(),lone.owner(),null,false);CompositeRuntime.drain(t.getWorld());
        }
        var offered=UnifiedCreativeCatalogue.canonicalItems();
        for(String number:List.of("90660","90023","93186","90507","90480","90839","93113","93117","93116","93115")) {
            String id=registry(number);t.assertTrue(!ImportedAssemblyV126.hidden(id),"explicitly rejected/shared part stays independent "+number);
            t.assertTrue(offered.stream().anyMatch(value->Registries.ITEM.getId(value.getItem()).toString().equals(id)),"explicitly kept part retains its own installation item "+number);
        }
        for(var recipe:ImportedAssemblyV126.recipes()) {
            Set<String> parts=new HashSet<>();recipe.components().forEach(value->parts.add(value.registryId()));
            for(List<String> rejected:List.of(List.of("90660","90023"),List.of("90023","93186"),List.of("90507","90023"),List.of("90480","90839")))t.assertTrue(!parts.containsAll(rejected.stream().map(AssemblyV126GameTests::registry).toList()),"rejected composition is absent from migration recipes: "+rejected);
            for(String window:List.of("93113","93117","93116","93115"))t.assertTrue(!parts.contains(registry(window)),"shared window part is never consumed "+window);
        }
        t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v126_unique_catalogue")
    public void workingNewTypesHaveUniqueNumbersAndHiddenPartItemsCanonicalizeWithoutRenumberingOrNbtLoss(TestContext t) {
        Set<String> allNumbers=new HashSet<>();for(var row:DebugCatalogue.entries())t.assertTrue(row.temporaryId().matches("[0-9]{5}")&&allNumbers.add(row.temporaryId()),"entire frozen reserved-number table has no duplicate ID");
        t.assertTrue(allNumbers.size()==1322,"V12.6 reuses existing primary numbers and keeps all old numbers reserved");
        Set<String> workingNumbers=new HashSet<>(),workingRegistries=new HashSet<>();int newTypes=0;
        for(var stack:UnifiedCreativeCatalogue.canonicalItems()) {
            var id=Registries.ITEM.getId(stack.getItem());if(!id.toString().startsWith("bloodborne_dw:source_"))continue;
            var row=DebugCatalogue.entry(stack);t.assertTrue(row!=null&&workingNumbers.add(row.temporaryId())&&workingRegistries.add(id.toString()),"each independent NEW installation item has one unique working number and registry ID");
            t.assertTrue(!ImportedAssemblyV126.hidden(id.getPath()),"old approved fragment is absent from working catalogue");
            if(stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof CompositeRootBlock block&&ImportedAssemblyV126.installationPose(id.getPath())>0)t.assertTrue(stack.getSubNbt("BlockStateTag")!=null&&stack.getSubNbt("BlockStateTag").getString("variant").equals(Integer.toString(ImportedAssemblyV126.installationPose(id.getPath())%16)),"one Creative item chooses the completed whole state "+row.temporaryId());
            newTypes++;
        }
        t.assertTrue(newTypes==544,"seven independent approved fragment types leave the previous551 working NEW types");
        HIDDEN.forEach((oldNumber,mainNumber)->{
            var old=new ItemStack(Registries.ITEM.get(new Identifier(registry(oldNumber))),7);old.setCustomName(Text.literal("старый предмет"));old.getOrCreateNbt().putLong("OpaqueLong",Long.MIN_VALUE);var art=old.getOrCreateSubNbt("BlockStateTag");art.putString("rotation","3");art.putString("profile","alt");
            var next=ImportedAssemblyV126.canonicalStack(old);
            t.assertTrue(Registries.ITEM.getId(next.getItem()).toString().equals(registry(mainNumber))&&next.getCount()==7&&next.getNbt().getLong("OpaqueLong")==Long.MIN_VALUE&&next.hasCustomName(),"old fragment item migrates to its existing primary number with exact count/name/typed NBT "+oldNumber);
            t.assertTrue(next.getSubNbt("BlockStateTag").getString("rotation").equals("3")&&next.getSubNbt("BlockStateTag").getString("profile").equals("alt"),"old item keeps yaw and material profile "+oldNumber);
            t.assertTrue(ItemStack.areEqual(next,ImportedAssemblyV126.canonicalStack(next)),"repeated item canonicalization is idempotent "+oldNumber);
            t.assertTrue(Registries.BLOCK.containsId(new Identifier(registry(oldNumber)))&&Registries.ITEM.containsId(new Identifier(registry(oldNumber))),"hidden old registration survives saved world/item loading "+oldNumber);
        });
        t.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=120,batchId="v126_explicit_90479_replacement")
    public void old90479BecomesExisting90001WithSameUuidExactLocalShiftAndPrivateSettings(TestContext t) {
        var world=t.getWorld();var root=t.getAbsolutePos(new BlockPos(20608,96,64));String oldId=registry("90479");
        for(int yaw=0;yaw<8;yaw++) {
            var state=pose(oldId,0,yaw);var data=new NbtCompound();data.putInt(ImportedTypeAliases.VERSION_KEY,1);var oldShift=new JsonArray();oldShift.add(.125);oldShift.add(0);oldShift.add(-.25);data.put("SourceShift",shift(oldShift));data.putLong("OpaqueLong",Long.MIN_VALUE);data.putUuid("PrivateLinkedUuid",UUID.randomUUID());
            var before=CompositeRuntime.instance(world,root,state,UUID.randomUUID(),data);load(world,before);
            var expected=data.copy();var moved=new JsonArray();moved.add(.125);moved.add(0);moved.add(.125);expected.put("SourceShift",shift(moved));
            load(world,CompositeRuntime.instance(world,root,block(registry("90001")).getDefaultState().with(CompositeRootBlock.ROTATION,yaw),before.owner().instanceId(),expected));world.setBlockState(root.down(),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            installSaved(t,List.of(before));var result=ImportedAssemblyV126.migrateLoadedRoot(world,before.owner());t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"explicit old90479 replacement commits at yaw"+yaw+": "+result.reason());
            var entity=(CompositeBlockEntity)world.getBlockEntity(root);var after=current(world,root);
            t.assertTrue(after.owner().registryId().equals(registry("90001"))&&after.owner().instanceId().equals(before.owner().instanceId())&&world.getBlockState(root).get(CompositeRootBlock.ROTATION)==yaw,"90479 uses the existing90001 registration/mechanism with exact old UUID and yaw");
            var shift=CompositeSourceShift.local(entity.payload());t.assertTrue(Math.abs(shift.x()-.125)<1e-8&&shift.y()==0&&Math.abs(shift.z()-.125)<1e-8,"full original door receives exactly localZ+.375 while preserving localX/Y");
            for(String key:List.of("OpaqueLong","PrivateLinkedUuid"))t.assertTrue(data.get(key).equals(entity.payload().get(key)),"explicit replacement retains private typed setting "+key);
            t.assertTrue(entity.payload().getList(ImportedAssemblyV126.PARTS_KEY,10).size()==1,"replaced source fragment is retained in exact provenance");
            CompositeRuntime.remove(world,after.owner(),null,false);CompositeRuntime.drain(world);world.removeBlock(root.down(),false);
        }
        t.complete();
    }

    private record Persistent(NbtCompound links,NbtCompound rules,NbtCompound groups,NbtCompound policies,NbtCompound commands,Map<String,ObjectPolicies.Initiator> context) {}
    private static Persistent snapshot(MinecraftServer server) {
        MechanismRules.get(server);
        return new Persistent(MechanismState.get(server).writeNbt(new NbtCompound()),MechanismRules.migrationSnapshot(server),MechanismGroups.get(server).writeNbt(new NbtCompound()),ObjectPolicies.get(server).writeNbt(new NbtCompound()),ToolCommands.get(server).writeNbt(new NbtCompound()),ObjectPolicies.migrationContext(server));
    }
    private static void restore(MinecraftServer server,Persistent saved) {
        var state=MechanismState.get(server);var old=MechanismState.fromNbt(saved.links);state.levers.clear();state.levers.putAll(old.levers);state.targets.clear();state.targets.putAll(old.targets);state.removed.clear();state.removed.addAll(old.removed);state.revision=old.revision;state.markDirty();
        MechanismRules.restoreMigration(server,saved.rules);MechanismGroups.restoreMigration(server,saved.groups);ObjectPolicies.restoreMigration(server,saved.policies);ToolCommands.restoreAssemblyMigration(server,saved.commands);ObjectPolicies.migrationContext(server,saved.context);
    }
    private static NbtList strings(String... values){var result=new NbtList();for(String value:values)result.add(NbtString.of(value));return result;}
    /** Simulate existing saved records, rather than asking today's editor to
     * create new bindings to an intentionally hidden historical fragment. */
    private static UUID savedReferences(MinecraftServer server,List<ObjectInstance> parts,ServerPlayerEntity player,String objective) {
        var saved=snapshot(server);var links=saved.links.copy();var rules=saved.rules.copy();var groups=saved.groups.copy();var policies=saved.policies.copy();var commands=saved.commands.copy();
        var refs=parts.stream().map(value->CompositeMechanismBridge.reference(player.getServerWorld(),value.owner())).toList();
        UUID source=UUID.randomUUID(),ruleId=UUID.randomUUID();String dimension=player.getServerWorld().getRegistryKey().getValue().toString();
        var sourceRef=new MechanismLinks.TargetRef(MechanismLinks.Kind.RP,dimension,source,CompositeData.pos(parts.get(0).owner().root()).add(32,0,32).asLong(),"lever_1");
        var targets=links.getList("Targets",10);var keys=new NbtList();int index=0;
        for(var ref:refs) {
            var target=new NbtCompound();target.put("Ref",ref.write());target.putBoolean("Known",true);target.putLong("Revision",index+1);targets.add(target);keys.add(NbtString.of(ref.key()));
            var policy=new NbtCompound();policy.put("Ref",ref.write());policy.putBoolean("LeversOnly",index==1);policy.put("Open",strings("scoreboard players add open"+index+" "+objective+" 1"));policy.put("Close",strings("scoreboard players add close"+index+" "+objective+" 1"));policies.getList("Entries",10).add(policy);
            var command=new NbtCompound();command.put("Target",ToolTarget.mechanism(ref).write());command.putString("Commands","scoreboard players add cmd"+index+" "+objective+" 1");command.putInt("PersonalLimit",7+index);command.putInt("GlobalLimit",11+index);command.putInt("GlobalUsed",3+index);
            var users=new NbtList();var user=new NbtCompound();user.putUuid("UUID",player.getUuid());user.putInt("Used",2+index);users.add(user);command.put("Players",users);commands.getList("Entries",10).add(command);
            var group=new NbtCompound();group.putUuid("Id",UUID.randomUUID());group.put("Target",ref.write());var groupSources=new NbtList();groupSources.add(new MechanismLinks.TargetRef(MechanismLinks.Kind.RP,dimension,UUID.randomUUID(),sourceRef.root(),"lever_2").write());group.put("Sources",groupSources);group.put("Active",new NbtList());groups.getList("Groups",10).add(group);
            index++;
        }
        var lever=new NbtCompound();lever.putUuid("Id",source);lever.putString("Dimension",dimension);lever.putLong("Pos",sourceRef.root());lever.putString("Asset","lever_1");lever.put("Targets",keys);links.getList("Levers",10).add(lever);
        var rule=new NbtCompound();rule.putUuid("Id",ruleId);rule.putLong("Order",rules.getLong("Creation")+1);rule.putString("Name","сохранённое правило V12.5");rule.putString("Condition","ALL");rule.putString("Effect","OPEN");var src=new NbtList();src.add(sourceRef.write());rule.put("Sources",src);var dst=new NbtList();refs.forEach(ref->dst.add(ref.write()));rule.put("Targets",dst);rules.getList("Rules",10).add(rule);rules.putLong("Creation",rules.getLong("Creation")+1);
        var uuidTag=new NbtCompound();uuidTag.putUuid("Id",source);rules.getList("Imported",11).add(uuidTag.get("Id"));
        index=0;long sequence=rules.getLong("Sequence");for(var ref:refs){var pending=new NbtCompound();pending.putLong("Number",++sequence);pending.put("Target",ref.write());pending.putString("Effect",index++==0?"OPEN":"CLOSE");pending.putUuid("Rule",ruleId);pending.put("Initiator",new ObjectPolicies.Initiator(player.getUuid(),player.getGameProfile().getName()).write());rules.getList("Queue",10).add(pending);}rules.putLong("Sequence",sequence);
        restore(server,new Persistent(links,rules,groups,policies,commands,saved.context));return ruleId;
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=160,batchId="v126_saved_reference_events_and_counters")
    public void mergeRemapsEveryOldMembersLinksPendingOrderEventsAndIndependentCommandCountersWithoutExecutingThem(TestContext t) {
        var world=t.getWorld();var server=world.getServer();var preserved=snapshot(server);var player=player(world);var root=t.getAbsolutePos(new BlockPos(20672,96,64));
        var assembly=assemblies().stream().filter(row->row.get("primaryId").getAsString().equals("90031")).findFirst().orElseThrow();
        var parts=before(t,root,assembly,0);installSaved(t,parts);ObjectInstance merged=null;
        String objective="dw"+UUID.randomUUID().toString().replace("-","").substring(0,10);var source=server.getCommandSource().withLevel(4).withSilent();
        server.getCommandManager().executeWithPrefix(source,"scoreboard objectives add "+objective+" dummy");
        for(int i=0;i<parts.size();i++)for(String event:List.of("open","close","cmd"))server.getCommandManager().executeWithPrefix(source,"scoreboard players set "+event+i+" "+objective+" 0");
        try {
            UUID ruleId=savedReferences(server,parts,player,objective);var pendingBefore=MechanismRules.get(server).writeNbt(new NbtCompound()).getList("Queue",10).stream().map(raw->((NbtCompound)raw).copy()).filter(row->row.getUuid("Rule").equals(ruleId)).toList();
            var result=ImportedAssemblyV126.migrateLoadedRoot(world,parts.get(0).owner());t.assertTrue(result.outcome()==TransactionCore.Outcome.COMMITTED,"saved configured90031+90623 composition atomically merges: "+result.reason());
            merged=current(world,root);var ref=CompositeMechanismBridge.reference(world,merged.owner());
            var migratedRule=MechanismRules.rule(server,ruleId);t.assertTrue(migratedRule!=null&&migratedRule.targets.size()==1&&migratedRule.targets.values().iterator().next().equals(ref),"rule's consumed UUID targets resolve to one complete current instance");
            var pendingAfter=MechanismRules.get(server).writeNbt(new NbtCompound()).getList("Queue",10).stream().map(raw->(NbtCompound)raw).filter(row->row.getUuid("Rule").equals(ruleId)).toList();
            t.assertTrue(pendingAfter.size()==pendingBefore.size(),"merging targets does not drop distinct pending events");
            for(int i=0;i<pendingBefore.size();i++){var old=pendingBefore.get(i);var next=pendingAfter.get(i);t.assertTrue(MechanismLinks.TargetRef.read(next.getCompound("Target")).equals(ref),"every pending entry targets the exact completed UUID");for(String key:List.of("Number","Effect","Rule","Initiator"))t.assertTrue(old.get(key).equals(next.get(key)),"ordered pending delivery preserves exact typed field "+key);}
            var linkRows=MechanismState.get(server).writeNbt(new NbtCompound());t.assertTrue(linkRows.getList("Targets",10).stream().map(raw->(NbtCompound)raw).anyMatch(row->MechanismLinks.TargetRef.read(row.getCompound("Ref")).equals(ref)),"legacy persistent target graph keeps the current root/registry/UUID");
            for(var raw:linkRows.getList("Levers",10)){var lever=(NbtCompound)raw;for(var key:lever.getList("Targets",8))for(var old:parts)if(key.asString().equals(CompositeMechanismBridge.reference(world,old.owner()).key()))t.assertTrue(old.owner().instanceId().equals(merged.owner().instanceId()),"no lever references a consumed old UUID");}
            var matchedGroups=MechanismGroups.get(server).writeNbt(new NbtCompound()).getList("Groups",10).stream().map(raw->(NbtCompound)raw).filter(row->MechanismLinks.TargetRef.read(row.getCompound("Target")).equals(ref)).toList();t.assertTrue(matchedGroups.size()==parts.size(),"all independent saved lever groups remain attached to the completed object");
            t.assertTrue(ObjectPolicies.leversOnly(server,ref)&&!ObjectPolicies.allowManual(player,ref),"a consumed member's manual restriction remains effective for the complete door");
            t.assertTrue(ObjectPolicies.commands(server,ref,true).size()==parts.size()&&ObjectPolicies.commands(server,ref,false).size()==parts.size(),"independent ordered open and close event batches are preserved");
            var bindings=ToolCommands.get(server).writeNbt(new NbtCompound()).getList("Entries",10).stream().map(raw->(NbtCompound)raw).filter(row->ref.instance().equals(ToolTarget.read(row.getCompound("Target")).uuid())).toList();t.assertTrue(bindings.size()==parts.size(),"command bindings do not overwrite another member's record during target merge");
            for(int i=0;i<parts.size();i++){final int number=i;var binding=bindings.stream().filter(row->row.getString("Commands").contains("cmd"+number+" ")).findFirst().orElseThrow();t.assertTrue(binding.getInt("PersonalLimit")==7+i&&binding.getInt("GlobalLimit")==11+i&&binding.getInt("GlobalUsed")==3+i&&binding.getList("Players",10).getCompound(0).getInt("Used")==2+i,"each source command keeps its independent exact shared/personal counter and limits");for(String event:List.of("open","close","cmd"))t.assertTrue(server.getCommandManager().executeWithPrefix(source,"scoreboard players get "+event+i+" "+objective)==0,"migration emits no event/command side effect");}
            t.assertTrue(MechanismLinks.applyEffect(server,ref,true,false)&&world.getBlockState(root).get(CompositeRootBlock.OPEN),"existing mechanism bridge opens the merged double door despite manual restriction");
            t.assertTrue(MechanismLinks.applyEffect(server,ref,false,false)&&!world.getBlockState(root).get(CompositeRootBlock.OPEN),"existing bridge closes the same full door");
            t.assertTrue(ToolCommands.fire(player,ToolTarget.mechanism(ref)),"normal debug tool callback executes all retained member command bindings");
            for(int i=0;i<parts.size();i++)for(String event:List.of("open","close","cmd"))t.assertTrue(server.getCommandManager().executeWithPrefix(source,"scoreboard players get "+event+i+" "+objective)==1,"each retained event/command runs exactly once after an actual action");
            var counters=ToolCommands.get(server).writeNbt(new NbtCompound()).getList("Entries",10).stream().map(raw->(NbtCompound)raw).filter(row->ref.instance().equals(ToolTarget.read(row.getCompound("Target")).uuid())).toList();
            for(int i=0;i<parts.size();i++){final int number=i;var binding=counters.stream().filter(row->row.getString("Commands").contains("cmd"+number+" ")).findFirst().orElseThrow();t.assertTrue(binding.getInt("GlobalUsed")==4+i&&binding.getList("Players",10).getCompound(0).getInt("Used")==3+i,"actual command use advances each own counter independently");}
        }finally {
            if(world.getBlockEntity(root) instanceof CompositeBlockEntity entity&&entity.resident()!=null)CompositeRuntime.remove(world,entity.resident(),null,false);
            for(var part:parts){var pos=CompositeData.pos(part.owner().root());if(world.getBlockEntity(pos) instanceof CompositeBlockEntity entity&&entity.resident()!=null&&entity.resident().equals(part.owner()))CompositeRuntime.remove(world,part.owner(),null,false);}
            CompositeRuntime.drain(world);restore(server,preserved);ToolCommands.forgetPlayer(server,player.getUuid());server.getCommandManager().executeWithPrefix(source,"scoreboard objectives remove "+objective);player.discard();
        }
        t.complete();
    }
}
