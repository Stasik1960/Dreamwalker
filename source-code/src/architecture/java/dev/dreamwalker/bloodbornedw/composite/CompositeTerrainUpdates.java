package dev.dreamwalker.bloodbornedw.composite;

import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.*;

/** Native rebuild work, coalesced once per tick. No meshes, owner index or
 * persistent dependency cache: loaded section palettes prove which sections
 * can contain architecture affected by a distant neighbour/light change. */
public final class CompositeTerrainUpdates {
    private static final int RADIUS=CompositeTerrain.UPDATE_SECTION_RADIUS,MAX_PENDING=4096;
    private static final Set<Long> PENDING=new HashSet<>();
    private static boolean initialized,whole;
    private static final ThreadLocal<Boolean> APPLYING=ThreadLocal.withInitial(()->false);
    private static long rebuilt;
    private static long visibilityScans,visibilityAdditions,lastVisibleOverhangs;
    private static final java.util.concurrent.atomic.LongAdder helperExclusions=new java.util.concurrent.atomic.LongAdder();
    private CompositeTerrainUpdates(){}
    public static void initialize(){if(initialized)return;initialized=true;ClientTickEvents.END_CLIENT_TICK.register(client->flush(client));ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());}
    public static synchronized void section(int x,int y,int z){if(APPLYING.get()||whole)return;if(PENDING.size()>=MAX_PENDING){whole=true;PENDING.clear();}else PENDING.add(ChunkSectionPos.asLong(x,y,z));}
    public static synchronized void all(){if(!APPLYING.get()){whole=true;PENDING.clear();}}
    public static void chunk(net.minecraft.client.world.ClientWorld world,ChunkPos pos){for(int y=world.getBottomSectionCoord();y<world.getTopSectionCoord();y++)section(pos.x,y,pos.z);}
    private static synchronized void clear(){PENDING.clear();whole=false;}
    private static void flush(MinecraftClient client){
        Set<Long> changed;boolean full;
        synchronized(CompositeTerrainUpdates.class){if(PENDING.isEmpty()&&!whole)return;changed=new HashSet<>(PENDING);PENDING.clear();full=whole;whole=false;}
        var world=client.world;if(world==null)return;
        Map<Long,BitSet> columns=new HashMap<>();int bottom=world.getBottomSectionCoord(),height=world.getTopSectionCoord()-bottom;
        if(full){
            BlockPos center=client.gameRenderer.getCamera().getBlockPos();int radius=client.options.getViewDistance().getValue()+RADIUS;
            for(int x=(center.getX()>>4)-radius;x<=(center.getX()>>4)+radius;x++)for(int z=(center.getZ()>>4)-radius;z<=(center.getZ()>>4)+radius;z++){
                if(world.getChunkManager().getWorldChunk(x,z)==null)continue;var bits=new BitSet(height);bits.set(0,height);columns.put(ChunkPos.toLong(x,z),bits);
            }
        }else for(long section:changed){
            int X=ChunkSectionPos.unpackX(section),Y=ChunkSectionPos.unpackY(section),Z=ChunkSectionPos.unpackZ(section);
            int from=Math.max(0,Y-RADIUS-bottom),to=Math.min(height,Y+RADIUS-bottom+1);if(from>=to)continue;
            for(int x=X-RADIUS;x<=X+RADIUS;x++)for(int z=Z-RADIUS;z<=Z+RADIUS;z++){
                long key=ChunkPos.toLong(x,z);var bits=columns.get(key);
                if(bits==null){if(world.getChunkManager().getWorldChunk(x,z)==null)continue;bits=new BitSet(height);columns.put(key,bits);}bits.set(from,to);
            }
        }
        APPLYING.set(true);
        try{for(var column:columns.entrySet()){
            int x=ChunkPos.getPackedX(column.getKey()),z=ChunkPos.getPackedZ(column.getKey());
            var chunk=world.getChunkManager().getWorldChunk(x,z);if(chunk==null)continue;
            var bits=column.getValue();for(int index=bits.nextSetBit(0);index>=0;index=bits.nextSetBit(index+1)){
                var section=chunk.getSectionArray()[index];
                if(!section.hasAny(state->CompositeTerrain.eligible(state)||state.getBlock() instanceof StaticArchitectureBlock&&StaticArchitectureCatalog.recipe(state).resident()>=0))continue;
                client.worldRenderer.scheduleBlockRender(x,index+bottom,z);rebuilt++;
            }
        }}finally{APPLYING.remove();}
    }
    public static synchronized void visibility(long tested,long added){visibilityScans+=tested;visibilityAdditions+=added;lastVisibleOverhangs=added;}
    public static void helperExcluded(){helperExclusions.increment();}
    public static synchronized Map<String,Object> metrics(){return Map.of("pendingSectionUpdates",PENDING.size(),"pendingWholeView",whole,"overhangNeighbourSectionRadius",RADIUS,"overhangRebuilds",rebuilt,"overhangVisibilitySectionScans",visibilityScans,"overhangVisibilityAdditions",visibilityAdditions,"visibleOverhangSectionsLast",lastVisibleOverhangs,"compiledNoOpHelperExclusions",helperExclusions.sum());}
}
