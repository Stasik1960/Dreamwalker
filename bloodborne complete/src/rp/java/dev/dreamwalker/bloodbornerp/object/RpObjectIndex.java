package dev.dreamwalker.bloodbornerp.object;

import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.EntityView;
import net.minecraft.world.World;

/** Loaded RP shapes and source model selection; no expanded origin search or chunk loads. */
public final class RpObjectIndex {
    private static final Map<World,Map<UUID,WeakReference<RpObjectEntity>>> WORLDS=new WeakHashMap<>();
    private static boolean serverInitialized;
    private RpObjectIndex() {}
    public static void initialize(){if(serverInitialized)return;serverInitialized=true;ServerEntityEvents.ENTITY_LOAD.register(RpObjectIndex::loaded);ServerEntityEvents.ENTITY_UNLOAD.register(RpObjectIndex::unloaded);ServerWorldEvents.UNLOAD.register((server,world)->clear(world));}
    public static synchronized void loaded(Entity entity,World world){if(entity instanceof RpObjectEntity rp)WORLDS.computeIfAbsent(world,key->new LinkedHashMap<>()).put(rp.getUuid(),new WeakReference<>(rp));}
    public static synchronized void unloaded(Entity entity,World world){var entries=WORLDS.get(world);if(entries==null)return;var reference=entries.get(entity.getUuid());if(reference!=null&&(reference.get()==null||reference.get()==entity))entries.remove(entity.getUuid());if(entries.isEmpty())WORLDS.remove(world);}
    public static synchronized void clear(World world){WORLDS.remove(world);}
    /** No chunk scans or origin-expanded search. Exact current bounds then physical/selection narrow phase. */
    public static synchronized List<RpObjectEntity> in(EntityView view,Box query){
        if(!(view instanceof World world))return List.of();var entries=WORLDS.get(world);if(entries==null)return List.of();List<RpObjectEntity> result=new ArrayList<>();
        var iterator=entries.entrySet().iterator();while(iterator.hasNext()){RpObjectEntity rp=iterator.next().getValue().get();if(rp==null||rp.isRemoved()||rp.getWorld()!=world){iterator.remove();continue;}if(rp.isAlive()&&rp.queryBounds().expand(rp.getTargetingMargin()).intersects(query))result.add(rp);}
        return List.copyOf(result);
    }
}
