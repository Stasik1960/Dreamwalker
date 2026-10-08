package dev.dreamwalker.bloodbornedw.diagnostics;

import dev.dreamwalker.bloodbornedw.architecture.PrototypeLadderBlock;
import dev.dreamwalker.bloodbornedw.architecture.wall.PrototypeWallBlock;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

/** Palette counts run only on request, never by looking up a chunk being loaded. */
public final class ArchitectureMetrics {
    private static final int LIMIT=4096;
    private static final Map<ServerWorld,Map<Long,WeakReference<WorldChunk>>> LOADED=new WeakHashMap<>();
    private static boolean initialized;
    public static void initialize(){if(initialized)return;initialized=true;
        ServerChunkEvents.CHUNK_LOAD.register((world,chunk)->{var entries=LOADED.computeIfAbsent(world,w->new LinkedHashMap<>());if(entries.size()<LIMIT||entries.containsKey(chunk.getPos().toLong()))entries.put(chunk.getPos().toLong(),new WeakReference<>(chunk));});
        ServerChunkEvents.CHUNK_UNLOAD.register((world,chunk)->{var entries=LOADED.get(world);if(entries!=null)entries.remove(chunk.getPos().toLong());});
        ServerWorldEvents.UNLOAD.register((server,world)->LOADED.remove(world));
    }
    public static Map<String,Object> loadedNative(ServerWorld world){
        Map<String,Integer> counts=new TreeMap<>();int chunks=0;var entries=LOADED.getOrDefault(world,Map.of());
        for(var ref:entries.values()){WorldChunk chunk=ref.get();if(chunk==null)continue;chunks++;
            for(var section:chunk.getSectionArray())if(section!=null&&!section.isEmpty())section.getBlockStateContainer().count((state,count)->{
                if(state.getBlock() instanceof PrototypeWallBlock||state.getBlock() instanceof PrototypeLadderBlock)counts.merge(ArchitectureDiagnostics.type(state),count,Integer::sum);
            });
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("nativeObjectsByType",counts);result.put("nativeObjectCount",counts.values().stream().mapToInt(Integer::intValue).sum());
        result.put("paletteChunksMeasured",chunks);result.put("loadedChunkIndexSize",entries.size());result.put("loadedChunkIndexLimit",LIMIT);
        result.put("nativeWallJunctionCacheEntries",PrototypeWallBlock.diagnosticJunctionCacheSize());result.put("nativeWallJunctionCacheLimit",4096);
        result.put("coverage",entries.size()>=LIMIT?"bounded4096chunkSubset; beyondIndexNotMeasured":"knownLoadedWorldChunks; directChunkPalettes; noChunkForcing");return result;
    }
    private ArchitectureMetrics(){}
}
