package dev.dreamwalker.bloodbornedw.tool;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.dreamwalker.bloodbornedw.architecture.DwUtilityBlocks;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;

/** Real native empty-region lifecycle plus strict malformed cold-index handling. */
public final class BbRegionDiscoveryV127GameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_tool_region_discovery")
    public void nativeEmptyRegionStubKeepsLoadedUnionAndIndexesTheFirstRealSave(TestContext context)throws Exception{
        var world=context.getWorld();var root=context.getAbsolutePos(new BlockPos(3,24,3));world.getChunk(root);var prior=world.getBlockState(root);
        Path directory=Files.createTempDirectory(world.getServer().getSavePath(WorldSavePath.ROOT),"bb-native-region-proof-");Path entities=Files.createDirectory(directory.resolve("entities"));Path file=entities.resolve("r.-2.3.mca");
        var saved=new ChunkPos(-64,96);long loaded=new ChunkPos(root).toLong();
        try(var nativeRegion=new RegionFile(file,entities,false)){
            context.assertTrue(Files.size(file)==0&&nativeRegion.getChunkInputStream(saved)==null,"Actual native read-created RegionFile has zero bytes and no saved cold chunk");
            world.setBlockState(root,DwUtilityBlocks.LIGHT.getDefaultState());
            context.assertTrue(BbAlternateService.savedChunks(file).isEmpty(),"A genuine native zero-length stub has an empty disk index");
            context.assertTrue(discovered(world,directory,Set.of(loaded)).equals(Set.of(loaded)),"Production discovery retains the actual live mod chunk even with the empty entity-region stub");
            try(var output=nativeRegion.getChunkOutputStream(saved)){output.writeInt(0x127BB);}
            context.assertTrue(Files.size(file)>=8192&&BbAlternateService.savedChunks(file).equals(Set.of(saved.toLong())),"The first actual native stream save writes the complete header and negative-coordinate chunk index");
            try(var input=nativeRegion.getChunkInputStream(saved)){context.assertTrue(input!=null&&input.readInt()==0x127BB,"Saved payload is readable through native RegionFile, not a synthetic header");}
            context.assertTrue(discovered(world,directory,Set.of(loaded)).equals(Set.of(loaded,saved.toLong())),"Discovery unions the saved cold target and loaded live target without omission or duplication");
            var proof=new JsonObject();proof.addProperty("status","PASS_NATIVE_EMPTY_REGION_FIRST_SAVE_AND_LOADED_UNION");proof.addProperty("nativeConstructorInitialBytes",0);proof.addProperty("nativeFirstSaveBytes",Files.size(file));proof.addProperty("loadedChunk",loaded);proof.addProperty("savedChunk",saved.toLong());proof.addProperty("diskAndLoadedUnion",true);
            Files.writeString(world.getServer().getSavePath(WorldSavePath.ROOT).resolve("v127-native-region-discovery-proof.json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        }finally{world.setBlockState(root,prior);Files.deleteIfExists(file);Files.deleteIfExists(entities);Files.deleteIfExists(directory);}
        context.complete();
    }

    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,tickLimit=100,batchId="v127_tool_region_discovery")
    public void nonzeroPartialColdHeadersFailInsteadOfSilentlySkippingSavedTargets(TestContext context)throws Exception{
        var world=context.getWorld();Path directory=Files.createTempDirectory(world.getServer().getSavePath(WorldSavePath.ROOT),"bb-partial-region-proof-");Path entities=Files.createDirectory(directory.resolve("entities"));Path file=entities.resolve("r.-2.3.mca");
        try{
            for(int length:new int[]{1,12,4095}){
                Files.write(file,new byte[length]);boolean readerFailed=false,discoveryFailed=false;
                try{BbAlternateService.savedChunks(file);}catch(IOException expected){readerFailed=expected.getMessage().contains("Truncated region header")&&expected.getMessage().contains(file.toString());}
                try{BbAlternateService.discoverDirectory(world,directory,Set.of());}catch(UncheckedIOException expected){discoveryFailed=expected.getCause().getMessage().contains("Truncated region header")&&expected.getCause().getMessage().contains(file.toString());}
                context.assertTrue(readerFailed&&discoveryFailed,"Nonempty malformed cold header remains an explicit reader/discovery error at length "+length);
            }
            Files.write(file,new byte[8192]);context.assertTrue(discovered(world,directory,Set.of()).isEmpty(),"An ordinary complete empty header also has no saved targets");
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(entities);Files.deleteIfExists(directory);}
        context.complete();
    }

    private static Set<Long> discovered(net.minecraft.server.world.ServerWorld world,Path directory,Set<Long> loaded){
        var result=new HashSet<Long>();for(var visit:BbAlternateService.discoverDirectory(world,directory,loaded)){if(visit.world()!=world)throw new AssertionError("Unexpected discovery world");if(!result.add(visit.chunk().toLong()))throw new AssertionError("Repeated saved/live chunk");}return result;
    }
}
