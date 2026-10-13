package dev.dreamwalker.bloodbornedw.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.composite.CompositeArchitecture;
import dev.dreamwalker.bloodbornedw.composite.CompositeBlockEntity;
import dev.dreamwalker.bloodbornedw.composite.CompositeData;
import dev.dreamwalker.bloodbornedw.composite.CompositeRuntime;
import dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess;
import dev.dreamwalker.bloodbornedw.debug.DebugCatalogue;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import dev.dreamwalker.bloodbornedw.tool.ToolCommands;
import dev.dreamwalker.bloodbornedw.tool.ToolTarget;
import dev.dreamwalker.bloodbornerp.content.SourceLegacyPayload;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.object.RpObjectEntity;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Real optional WE parser, EditSession clipboard, numeric region mask and undo.
 * No WorldEdit API class is loaded when the optional mod is absent. */
public final class WorldEditRpV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="v127_worldedit_rp",tickLimit=240)
    public void numericRpClipboardAndSelectiveDeleteUndoKeepTypedSettingsAndCounters(TestContext context)throws Exception {
        if(!FabricLoader.getInstance().isModLoaded("worldedit")){
            System.out.println("V127 real WorldEdit RP roundtrip: NOT_RUN (optional worldedit absent)");
            context.complete();return;
        }
        new WithWorldEdit(context).prepare();
    }

    private static final class WithWorldEdit {
        final TestContext c;
        final ServerWorld world;
        final ServerPlayerEntity player;
        final BlockPos source,destination;
        final Map<ChunkPos,Boolean> forced=new LinkedHashMap<>();
        final List<ToolTarget> boundTargets=new ArrayList<>();
        com.sk89q.worldedit.world.World adapter;
        com.sk89q.worldedit.extension.input.ParserContext parser;
        com.sk89q.worldedit.EditSession deletion;
        String number;
        UUID sourceUuid,copyUuid;
        NbtCompound sourceData,sourceBinding,copyBinding,nativeData;
        net.minecraft.block.BlockState nativeState;
        Owner nativeOwner;
        boolean cleaned;

        WithWorldEdit(TestContext c){this.c=c;world=c.getWorld();player=UtilityV127GameTests.player(world);
            source=c.getAbsolutePos(new BlockPos(3,20,3));destination=source.add(16,0,0);}
        static com.sk89q.worldedit.math.BlockVector3 v(BlockPos pos){return com.sk89q.worldedit.math.BlockVector3.at(pos.getX(),pos.getY(),pos.getZ());}
        void prepare()throws Exception {
            for(BlockPos pos:List.of(source,destination))for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                var chunk=new ChunkPos(pos.add(x*16,0,z*16));
                forced.putIfAbsent(chunk,world.getForcedChunks().contains(chunk.toLong()));
                world.setChunkForced(chunk.x,chunk.z,true);world.getChunk(chunk.x,chunk.z);
            }
            for(var method:Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethods())
                if(method.getName().equals("adapt")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(world)
                    &&com.sk89q.worldedit.world.World.class.isAssignableFrom(method.getReturnType()))
                    adapter=(com.sk89q.worldedit.world.World)method.invoke(null,world);
            c.assertTrue(adapter!=null&&adapter.getClass().getMethod("getWorld").invoke(adapter)==world,"Actual public Fabric world adapter retains this exact native world");
            parser=new com.sk89q.worldedit.extension.input.ParserContext();parser.setWorld(adapter);
            for(var method:Class.forName("com.sk89q.worldedit.fabric.FabricAdapter").getMethods())
                if(method.getName().equals("adaptPlayer")&&method.getParameterCount()==1&&method.getParameterTypes()[0].isInstance(player)
                    &&com.sk89q.worldedit.extension.platform.Actor.class.isAssignableFrom(method.getReturnType()))
                    parser.setActor((com.sk89q.worldedit.extension.platform.Actor)method.invoke(null,player));
            c.assertTrue(parser.getActor()!=null,"Actual Fabric actor is supplied to the real numeric parser");
            var type=ObjectRegistry.TYPES.get("curtainsmall");var entry=DebugCatalogue.entry(Registries.ENTITY_TYPE.getId(type));
            c.assertTrue(type!=null&&entry!=null&&entry.registryId().equals(new Identifier("bloodborne_rp","curtainsmall")),"Frozen catalogue number belongs to the real canonical registered RP entity");
            number=entry.temporaryId();c.assertTrue(number.matches("[0-9]{5}"),"RP fixture uses its existing five-digit ID");
            later(1,this::awaitNativeTracking);
        }
        void awaitNativeTracking()throws Exception {
            var manager=((BbServerEntityManagerAccess)world).bloodborne_dw$profileEntityManager();
            if(!manager.isLoaded(new ChunkPos(source).toLong())||!manager.isLoaded(new ChunkPos(destination).toLong())){
                c.assertTrue(c.getTick()<100,"Native entity-manager load completes before RP edits");later(1,this::awaitNativeTracking);return;
            }
            c.assertTrue(rp(source).isEmpty()&&rp(destination).isEmpty(),"Isolated source and clipboard destination start without RP objects");
            var pattern=com.sk89q.worldedit.WorldEdit.getInstance().getBlockFactory().parseFromInput(number,parser);
            try(var edits=session()){c.assertTrue(edits.setBlock(v(source),pattern),"Real EditSession accepts the five-digit RP placement pattern");edits.flushSession();}
            later(2,this::captureAndCopy);
        }
        void captureAndCopy()throws Exception {
            var originals=rp(source);c.assertTrue(originals.size()==1,"Numeric pattern creates exactly one actual tracked RP instance");
            var object=originals.get(0);sourceUuid=object.getUuid();configure(object);
            var target=ToolTarget.entity(object);boundTargets.add(target);sourceBinding=seedBinding(target);
            c.assertTrue(target.valid(world.getServer()),"Command fixture uses the actual loaded RP target");
            try(var edits=session()){
                var clipboard=edits.getFullBlock(v(source));
                c.assertTrue(clipboard.getNbtData()!=null&&clipboard.getNbtData().getValue().get("DreamwalkerWorldEditSnapshot") instanceof com.sk89q.jnbt.StringTag,"Actual EditSession.getFullBlock returns logical RP clipboard NBT");
                var encoded=(com.sk89q.jnbt.StringTag)clipboard.getNbtData().getValue().get("DreamwalkerWorldEditSnapshot");
                var snapshot=CompositeData.nbt(Base64.getDecoder().decode(encoded.getValue()));
                c.assertTrue(snapshot.getList("Roots",10).isEmpty()&&snapshot.getList("Rp",10).size()==1,"Clipboard includes exactly this complete RP object, not a fake native barrier");
                sourceData=snapshot.getList("Rp",10).getCompound(0).copy();
                c.assertTrue(stable(sourceData).equals(stable(saved(object))),"Real clipboard preserves every stable typed RP field");
                c.assertTrue(snapshot.getCompound("References").getCompound("Commands").getList("Entries",10).getCompound(0).equals(sourceBinding),"Real clipboard captures exact command text, limits, global and both personal counters");
                c.assertTrue(edits.setBlock(v(destination),clipboard),"Real EditSession pastes the logical RP clipboard");edits.flushSession();
            }
            later(2,this::verifyCopyAndSelectiveDelete);
        }
        void verifyCopyAndSelectiveDelete()throws Exception {
            var copies=rp(destination);c.assertTrue(copies.size()==1,"Clipboard creates exactly one tracked destination RP object");
            var copy=copies.get(0);copyUuid=copy.getUuid();c.assertTrue(!sourceUuid.equals(copyUuid),"Clipboard copy receives an independent UUID");
            var expected=sourceData.copy();expected.putUuid("UUID",copyUuid);shift(expected,destination.subtract(source));
            c.assertTrue(stable(saved(copy)).equals(stable(expected)),"Clipboard copy preserves ALT, exact typed provenance, pose, name, scale, lock/open/offset and opaque settings");
            var copyTarget=ToolTarget.entity(copy);boundTargets.add(copyTarget);copyBinding=binding(copyTarget);
            var expectedBinding=sourceBinding.copy();expectedBinding.put("Target",copyTarget.write());
            c.assertTrue(copyBinding.equals(expectedBinding)&&!copyTarget.uuid().equals(boundTargets.get(0).uuid()),"Clipboard remaps only RP command target UUID/location; exact personal/global counters stay independent");
            c.assertTrue(binding(boundTargets.get(0)).equals(sourceBinding),"Copy does not reset or execute the source command binding");
            // Unrelated native owner is fixture setup; the operations under test
            // remain actual numeric WE region replacement and native WE undo.
            var block=CompositeArchitecture.kindBlock("source_addon_barrel_0");
            c.assertTrue(CompositeRuntime.place(world,source,block.getDefaultState(),UUID.randomUUID(),null).outcome()==TransactionCore.Outcome.COMMITTED,"Unrelated native owned BB root can coexist in the same RP cell");
            var nativeBe=(CompositeBlockEntity)world.getBlockEntity(source);nativeOwner=nativeBe.resident();nativeState=world.getBlockState(source);nativeData=nativeBe.createNbtWithIdentifyingData();
            var mask=com.sk89q.worldedit.WorldEdit.getInstance().getMaskFactory().parseFromInput(number,parser);
            c.assertTrue(mask.test(v(source)),"Real five-digit numeric mask selects the RP overlapping an unrelated native root");
            deletion=session();int removed=deletion.replaceBlocks(new com.sk89q.worldedit.regions.CuboidRegion(adapter,v(source),v(source)),mask,com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());deletion.flushSession();
            c.assertTrue(removed==1,"Native WE region visitor replaces the selected single cell once");
            later(2,this::verifyDeletionAndUndo);
        }
        void verifyDeletionAndUndo()throws Exception {
            c.assertTrue(world.getEntity(sourceUuid)==null&&rp(source).isEmpty(),"Numeric AIR replacement removes the selected RP instance");
            intactNative();c.assertTrue(binding(ToolTarget.entity(rp(destination).get(0))).equals(copyBinding),"Source deletion preserves copied RP settings and independent counters");
            try(var undo=session()){deletion.undo(undo);undo.flushSession();}
            later(2,this::verifyUndo);
        }
        void verifyUndo()throws Exception {
            c.assertTrue(world.getEntity(sourceUuid) instanceof RpObjectEntity,"Actual WE undo restores the original RP UUID");
            var restored=(RpObjectEntity)world.getEntity(sourceUuid);
            c.assertTrue(stable(saved(restored)).equals(stable(sourceData)),"Undo restores exact original typed source/ALT/settings and pose");
            c.assertTrue(binding(ToolTarget.entity(restored)).equals(sourceBinding),"Undo restores exact original command text, personal/global limits and used counters without firing");
            c.assertTrue(world.getEntity(copyUuid) instanceof RpObjectEntity&&binding(ToolTarget.entity((RpObjectEntity)world.getEntity(copyUuid))).equals(copyBinding),"Undo leaves independent clipboard copy and its counters intact");intactNative();
            var proof=new JsonObject();proof.addProperty("status","PASS_REAL_OPTIONAL_WORLDEDIT_RP_ROUNDTRIP");proof.addProperty("worldEditVersion",FabricLoader.getInstance().getModContainer("worldedit").orElseThrow().getMetadata().getVersion().getFriendlyString());
            proof.addProperty("fiveDigitId",number);proof.addProperty("sourceUuid",sourceUuid.toString());proof.addProperty("copiedUuid",copyUuid.toString());proof.addProperty("parserAndActor","ACTUAL_FABRIC_ADAPTER");proof.addProperty("numericPlacement","PASS");proof.addProperty("actualEditSessionGetFullBlock","PASS");proof.addProperty("exactTypedSettingsAndSourcePayload","PASS");proof.addProperty("independentCopiedUuidAndRemappedCounters","PASS");proof.addProperty("selectiveNumericMaskAirRemoval","PASS");proof.addProperty("unrelatedNativeOwnerPreserved","PASS");proof.addProperty("actualUndoOriginalUuidAndCounters","PASS");proof.addProperty("commandsExecuted",0);
            Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-worldedit-rp-native-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            cleanup();System.out.println("V127 real WorldEdit RP parser/clipboard/selective delete/undo/counters: PASS");c.complete();
        }
        void intactNative(){c.assertTrue(world.getBlockState(source).equals(nativeState)&&world.getBlockEntity(source) instanceof CompositeBlockEntity be&&nativeOwner.equals(be.resident())&&nativeData.equals(be.createNbtWithIdentifyingData()),"Selective RP delete/undo preserves unrelated native root UUID, exact state and typed block entity");}
        com.sk89q.worldedit.EditSession session(){return com.sk89q.worldedit.WorldEdit.getInstance().newEditSessionBuilder().world(adapter).maxBlocks(1000).build();}
        List<RpObjectEntity> rp(BlockPos cell){return world.getEntitiesByClass(RpObjectEntity.class,new net.minecraft.util.math.Box(cell).expand(2),entity->entity.assetId().equals("curtainsmall")&&BlockPos.ofFloored(entity.getPos()).equals(cell));}
        void configure(RpObjectEntity object){
            var original=new NbtCompound();original.putString("id","bloodborne_rp:curtainsmall");original.putInt("AnimationId",17);original.putBoolean("CanUpdate",false);original.putByte("OpaqueByte",(byte)91);original.putShort("OpaqueShort",(short)1234);original.putLong("OpaqueLong",912345678901L);original.putFloat("OpaqueFloat",.625f);original.putDouble("OpaqueDouble",1.25);original.putByteArray("OpaqueBytes",new byte[]{-7,0,64,127});original.putIntArray("OpaqueInts",new int[]{-19,0,65536});original.putLongArray("OpaqueLongs",new long[]{Long.MIN_VALUE,42});
            var nested=new NbtCompound();nested.putUuid("OpaqueUuid",UUID.randomUUID());nested.putString("OpaqueText","WE RP exact typed fixture");original.put("OpaqueNested",nested);var envelope=new NbtCompound();envelope.putInt("Schema",1);envelope.put("Original",original);
            var nbt=saved(object);nbt.put(SourceLegacyPayload.KEY,envelope);nbt.putBoolean("BbAltVisual",true);nbt.putBoolean("Open",true);nbt.putBoolean("Locked",true);nbt.putFloat("Scale",1.125f);nbt.putDouble("VerticalOffset",.25);object.readNbt(nbt);object.setYaw(45);object.setCustomName(Text.literal("WorldEdit RP typed proof"));
            c.assertTrue(saved(object).getCompound(SourceLegacyPayload.KEY).getCompound("Original").equals(original),"Native registered entity retains exact typed source envelope before WE operation");
        }
        NbtCompound seedBinding(ToolTarget target){
            var all=ToolCommands.get(world.getServer()).writeNbt(new NbtCompound());var row=new NbtCompound();row.put("Target",target.write());row.putString("Commands","say worldedit-rp-counter-proof");row.putInt("PersonalLimit",7);row.putInt("GlobalLimit",13);row.putInt("GlobalUsed",4);
            var players=new NbtList();for(var entry:Map.of(player.getUuid(),2,UUID.randomUUID(),1).entrySet()){var use=new NbtCompound();use.putUuid("UUID",entry.getKey());use.putInt("Used",entry.getValue());players.add(use);}row.put("Players",players);all.getList("Entries",10).add(row);ToolCommands.restoreAssemblyMigration(world.getServer(),all);return binding(target);
        }
        NbtCompound binding(ToolTarget target){for(var raw:ToolCommands.get(world.getServer()).writeNbt(new NbtCompound()).getList("Entries",10)){var row=(NbtCompound)raw;if(ToolTarget.read(row.getCompound("Target")).key().equals(target.key()))return row.copy();}throw new IllegalStateException("Actual typed command binding missing: "+target);}
        static NbtCompound saved(RpObjectEntity object){var nbt=new NbtCompound();if(!object.saveNbt(nbt))throw new IllegalStateException("Native RP entity must be saveable");return nbt;}
        static NbtCompound stable(NbtCompound source){var nbt=source.copy();for(String key:List.of("Motion","FallDistance","Fire","Air","OnGround","PortalCooldown"))nbt.remove(key);return nbt;}
        static void shift(NbtCompound data,BlockPos delta){var old=data.getList("Pos",6);var next=new NbtList();next.add(NbtDouble.of(old.getDouble(0)+delta.getX()));next.add(NbtDouble.of(old.getDouble(1)+delta.getY()));next.add(NbtDouble.of(old.getDouble(2)+delta.getZ()));data.put("Pos",next);}
        @FunctionalInterface interface Step{void run()throws Exception;}
        void later(int ticks,Step action){c.waitAndRun(ticks,()->{try{action.run();}catch(Exception failure){cleanup();throw new IllegalStateException("Native WE RP roundtrip failed",failure);}catch(Error failure){cleanup();throw failure;}});}
        void cleanup(){if(cleaned)return;cleaned=true;if(deletion!=null)deletion.close();for(UUID uuid:List.of(sourceUuid==null?new UUID(0,0):sourceUuid,copyUuid==null?new UUID(0,0):copyUuid))if(world.getEntity(uuid) instanceof RpObjectEntity object)object.removeObject();for(var target:boundTargets)ToolCommands.remove(world.getServer(),target);if(nativeOwner!=null&&world.getBlockEntity(source) instanceof CompositeBlockEntity be&&nativeOwner.equals(be.resident()))CompositeRuntime.remove(world,nativeOwner,null,false);player.discard();forced.forEach((chunk,wasForced)->{if(!wasForced)world.setChunkForced(chunk.x,chunk.z,false);});}
    }
}
