package dev.dreamwalker.bloodbornedw.compat.collision;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.Optional;

/** Optional bridge, empty by default. No config scanning, hard ModelPacks dependency or runtime
 * physics hook. A future loader must install the same reviewed project on client and server,
 * provide exact catalog selectors, and use revision() in any derived player-shape cache.
 */
public final class CollisionReplacements {
    private record Snapshot(long revision,CollisionPack pack) {}
    private static volatile Snapshot current=new Snapshot(0,null);
    private CollisionReplacements() {}
    public static long revision() { return current.revision; }
    /** Atomically replace the whole previously registered project after successful validation. */
    public static synchronized long register(CollisionPack pack) {
        Objects.requireNonNull(pack);
        current=new Snapshot(Math.addExact(current.revision,1),pack);
        return current.revision;
    }
    public static synchronized long clear() {
        current=new Snapshot(Math.addExact(current.revision,1),null);
        return current.revision;
    }
    public static Optional<CollisionPack.Replacement> query(String registryId,JsonObject exactSelector) {
        Snapshot snapshot=current;
        return snapshot.pack==null?Optional.empty():snapshot.pack.replacement(registryId,exactSelector);
    }
}
