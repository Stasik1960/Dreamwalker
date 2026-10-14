package dev.dreamwalker.bloodbornedw.tool;

import dev.dreamwalker.bloodbornedw.composite.*;
import dev.dreamwalker.bloodbornedw.runtime.ObjectInstance.Owner;
import dev.dreamwalker.bloodbornedw.runtime.TransactionCore;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.test.*;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.storage.RegionFile;

/** Production record selection, cancellation and native cold helper loading. */
public final class AlternateDiscoveryGameTests implements FabricGameTest {
    private static final ChunkTicketType<ChunkPos> PROOF_TICKET=ChunkTicketType.create("dw-alt-helper-proof",Comparator.comparingLong(ChunkPos::toLong),1200);

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_discovery_records")
    public void nativeEmptyRegionStubAndFirstFullSaveRemainReadOnly(TestContext t)throws Exception {
        Path directory=Files.createTempDirectory(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT),"final-empty-region-");
        Path file=directory.resolve("r.-2.3.mca");var chunk=new ChunkPos(-64,96);
        try {
            try(var nativeRegion=new RegionFile(file,directory,false)){
                t.assertTrue(Files.size(file)==0&&nativeRegion.getChunkInputStream(chunk)==null,"Native read-created region is a legitimate zero-byte stub");
                t.assertTrue(BbAlternateService.savedChunks(file).isEmpty()&&BbAlternateService.savedFullChunks(file).isEmpty(),"Both bounded discovery readers accept the real empty stub");
                var data=new NbtCompound();data.putString("Status","minecraft:full");data.putInt("xPos",chunk.x);data.putInt("zPos",chunk.z);
                try(var output=nativeRegion.getChunkOutputStream(chunk)){NbtIo.write(data,output);}
            }
            byte[] before=Files.readAllBytes(file);
            t.assertTrue(before.length>=8192&&BbAlternateService.savedChunks(file).equals(Set.of(chunk.toLong()))&&BbAlternateService.savedFullChunks(file).equals(Set.of(chunk.toLong())),"Actual native first FULL save is discovered at its negative chunk coordinates");
            t.assertTrue(Arrays.equals(before,Files.readAllBytes(file)),"Header and FULL status readers perform no native region writes");
            t.complete();
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(directory);}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_discovery_records")
    public void partialNonemptyRegionHeadersAreRejectedByBothBoundedReaders(TestContext t)throws Exception {
        Path directory=Files.createTempDirectory(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT),"final-partial-region-");Path file=directory.resolve("r.-2.3.mca");
        try{
            for(int length:new int[]{1,12,4095}){
                Files.write(file,new byte[length]);boolean header=false,full=false;
                try{BbAlternateService.savedChunks(file);}catch(IOException expected){header=expected.getMessage().contains("Truncated region header")&&expected.getMessage().contains(file.toString());}
                try{BbAlternateService.savedFullChunks(file);}catch(IOException expected){full=expected.getMessage().contains("Truncated region header")&&expected.getMessage().contains(file.toString());}
                t.assertTrue(header&&full,"A partial nonempty header cannot silently lose saved targets: "+length);
            }
            Files.write(file,new byte[8192]);t.assertTrue(BbAlternateService.savedChunks(file).isEmpty()&&BbAlternateService.savedFullChunks(file).isEmpty(),"A complete native empty header remains an empty index");t.complete();
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(directory);}
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="selective_alt_discovery_records")
    public void boundedCandidateReadsSkipOutsideNbtAndStillApproveExactColdHelpers(TestContext t)throws Exception {
        Path directory=Files.createTempDirectory(t.getWorld().getServer().getSavePath(WorldSavePath.ROOT),"selective-filtered-region-");
        Path file=directory.resolve("r.0.0.mca");
        var candidate=new ChunkPos(0,0);var helper=new ChunkPos(1,0);var proto=new ChunkPos(2,0);var outside=new ChunkPos(3,0);
        try {
            try(var writer=new RegionFile(file,directory,false)) {
                for(var chunk:List.of(candidate,helper,proto,outside)) {
                    var data=new NbtCompound();data.putString("Status",chunk.equals(proto)?"minecraft:noise":"minecraft:full");
                    data.putInt("xPos",chunk.x);data.putInt("zPos",chunk.z);
                    try(var output=writer.getChunkOutputStream(chunk)){NbtIo.write(data,output);}
                }
            }
            // Unsupported compression is a sentinel: decoding outside records
            // would fail the test, while their cheap saved-header index remains valid.
            try(var record=new RandomAccessFile(file.toFile(),"rw")) {
                record.seek(3*4L);int sector=record.readInt()>>>8;record.seek((long)sector*4096+4);record.writeByte(127);
            }
            byte[] before=Files.readAllBytes(file);
            t.assertTrue(BbAlternateService.savedChunks(file).equals(Set.of(candidate.toLong(),helper.toLong(),proto.toLong(),outside.toLong())),
                    "Header index includes cold helpers without decoding any chunk payload");
            t.assertTrue(BbAlternateService.savedFullChunks(file,key->key==candidate.toLong(),()->false).equals(Set.of(candidate.toLong())),
                    "Selected radius candidate decodes while malformed outside NBT is never touched");
            t.assertTrue(BbAlternateService.savedFullChunk(file,helper,()->false),"Out-of-radius FULL helper remains independently approvable");
            t.assertTrue(!BbAlternateService.savedFullChunk(file,proto,()->false),"Proto helper is refused before any native FULL request");
            boolean sentinel=false;try{BbAlternateService.savedFullChunk(file,outside,()->false);}catch(IOException expected){sentinel=true;}
            t.assertTrue(sentinel,"Outside sentinel really fails if its record is decoded");
            t.assertTrue(Arrays.equals(before,Files.readAllBytes(file)),"Discovery and helper approvals never write region data");

            var cancelled=new AtomicBoolean();boolean stopped=false;
            try {
                BbAlternateService.savedFullChunks(file,key->{if(key==candidate.toLong())return true;cancelled.set(true);return false;},cancelled::get);
            } catch(CancellationException expected) { stopped=true; }
            t.assertTrue(stopped,"Cancellation stops before another record is inflated");
            stopped=false;try{BbAlternateService.savedFullChunk(file,helper,()->true);}catch(CancellationException expected){stopped=true;}
            t.assertTrue(stopped,"A cancelled queued helper read exits before file IO");
            t.complete();
        } finally {
            Files.deleteIfExists(file);Files.deleteIfExists(directory);
        }
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=1600,batchId="selective_alt_cold_helper")
    public void radiusOneLoadsSavedPhysicalHelperOutsideItsCandidateCircleAndReleasesTickets(TestContext t) {
        new ColdHelperProof(t).prepare();
    }
    private static final class ColdHelperProof {
        final TestContext t;final ServerWorld world;final ServerPlayerEntity player;final ChunkPos rootChunk,helperChunk;final BlockPos pos;
        Owner owner;NbtCompound payload;List<CompositeData.Contribution> originalHelper;int phase;long phaseStart;boolean held;
        ColdHelperProof(TestContext t) {
            this.t=t;world=t.getWorld();player=BbToolGameTests.player(t);
            var remote=new ChunkPos(t.getAbsolutePos(new BlockPos(12288,0,-12288)));
            rootChunk=remote;helperChunk=new ChunkPos(remote.x+1,remote.z);
            pos=new BlockPos(remote.getStartX()+15,160,remote.getStartZ()+8);
            // Root centre is exactly one block away; the helper chunk starts
            // 1.5 blocks away and therefore is excluded from radius-one discovery.
            player.setPosition(pos.getX()-.5,pos.getY(),pos.getZ()+.5);
        }
        void prepare() {
            t.assertTrue(world.getChunkManager().getWorldChunk(rootChunk.x,rootChunk.z)==null
                    &&world.getChunkManager().getWorldChunk(helperChunk.x,helperChunk.z)==null,"Both native fixture chunks begin cold");
            world.getChunk(rootChunk.x,rootChunk.z);world.getChunk(helperChunk.x,helperChunk.z);hold();
            var block=CompositeArchitecture.kindBlock("source_1_spirelamp_001");
            var extra=new NbtCompound();extra.putLong("ColdHelperLong",Long.MIN_VALUE+93);extra.putString("ColdHelperLabel","сохранить helper");
            var placed=CompositeRuntime.place(world,pos,block.getDefaultState(),UUID.randomUUID(),player,extra);
            t.assertTrue(placed.outcome()==TransactionCore.Outcome.COMMITTED,"Actual owned source fixture crosses its native chunk boundary: "+placed.reason());
            var own=(CompositeBlockEntity)world.getBlockEntity(pos);owner=own.resident();payload=own.payload();
            originalHelper=List.copyOf(CompositeRuntime.contributions(world,pos.east()));
            t.assertTrue(originalHelper.stream().anyMatch(entry->entry.owner().equals(owner)&&!entry.shape().collision().isEmpty()),"Out-of-radius chunk contains genuine physical guest geometry");
            phaseStart=t.getTick();again();
        }
        void step() { try {
            t.assertTrue(t.getTick()-phaseStart<390,"Native cold-helper phase completes without pending-target rejection: "+phase);
            var entities=((dev.dreamwalker.bloodbornedw.composite.mixin.BbServerEntityManagerAccess)world).bloodborne_dw$profileEntityManager();
            if(phase==0) {
                if(!entities.isLoaded(rootChunk.toLong())||!entities.isLoaded(helperChunk.toLong())){again();return;}
                world.save(null,true,false);release();
                for(var chunk:List.of(rootChunk,helperChunk))world.getChunkManager().removeTicket(ChunkTicketType.UNKNOWN,chunk,0,chunk);
                phase=1;phaseStart=t.getTick();again();return;
            }
            if(phase==1) {
                if(loaded(rootChunk)||loaded(helperChunk)||entities.isLoaded(rootChunk.toLong())||entities.isLoaded(helperChunk.toLong())){again();return;}
                Path directory=DimensionType.getSaveDirectory(world.getRegistryKey(),world.getServer().getSavePath(WorldSavePath.ROOT)).resolve("region");
                for(var chunk:List.of(rootChunk,helperChunk)) {
                    Path file=directory.resolve("r."+chunk.getRegionX()+"."+chunk.getRegionZ()+".mca");
                    try{t.assertTrue(BbAlternateService.savedChunks(file).contains(chunk.toLong()),"Native saved header retains unloaded owned helper chunk");}
                    catch(IOException failure){throw new UncheckedIOException(failure);}
                }
                t.assertTrue(player.getX()+1<helperChunk.getStartX(),"Helper chunk is truly beyond the radius-one candidate circle");
                t.assertTrue(BbAlternateService.start(player,"alt",1),"Production radius-one discovery starts from both saved cold chunks");
                t.assertTrue(BbAlternateService.discovering(world.getServer()),"Diagnostics expose the discovery phase separately from zero candidates");
                phase=2;phaseStart=t.getTick();again();return;
            }
            if(phase==2) {
                if(!held&&loaded(rootChunk)&&loaded(helperChunk))hold();
                var status=BbAlternateService.status(world.getServer());if(status.running()){again();return;}
                t.assertTrue(held&&status.changed()==1&&status.failed()==0,"Pending native target waits for exact lazy FULL helper approval: "+status);
                var own=(CompositeBlockEntity)world.getBlockEntity(pos);
                t.assertTrue("alt".equals(BbAlternateService.profile(world.getBlockState(pos)))&&own.resident().equals(owner)&&own.payload().equals(payload),
                        "Cold profile transition preserves owner UUID and exact typed root payload");
                var changedHelper=CompositeRuntime.contributions(world,pos.east());
                t.assertTrue(changedHelper.size()==originalHelper.size()&&changedHelper.get(0).owner().equals(originalHelper.get(0).owner())
                        &&changedHelper.get(0).shape().equals(originalHelper.get(0).shape())&&"alt".equals(changedHelper.get(0).rootData().properties().get("profile")),
                        "Cold outside helper retains physical geometry and receives the atomic updated root profile");
                release();phase=3;phaseStart=t.getTick();again();return;
            }
            if(loaded(rootChunk)||loaded(helperChunk)||entities.isLoaded(rootChunk.toLong())||entities.isLoaded(helperChunk.toLong())){again();return;}
            t.assertTrue(!BbAlternateService.status(world.getServer()).running(),"Completed radius job releases root and helper tickets");
            player.discard();t.complete();
        } catch(RuntimeException|Error failure) {
            BbAlternateService.cancel(world.getServer());release();player.discard();throw failure;
        }}
        boolean loaded(ChunkPos chunk){return world.getChunkManager().getWorldChunk(chunk.x,chunk.z)!=null;}
        void again(){t.runAtTick(t.getTick()+1,this::step);}
        void hold(){for(var chunk:List.of(rootChunk,helperChunk))world.getChunkManager().addTicket(PROOF_TICKET,chunk,0,chunk);held=true;}
        void release(){if(held)for(var chunk:List.of(rootChunk,helperChunk))world.getChunkManager().removeTicket(PROOF_TICKET,chunk,0,chunk);held=false;}
    }
}
