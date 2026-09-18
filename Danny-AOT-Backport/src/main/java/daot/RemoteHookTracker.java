package daot;

import daot.network.ODMHookSyncPayload;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class RemoteHookTracker {
   private static final Map<Integer, RemoteHookTracker.RemoteHookData> remoteHooks = new ConcurrentHashMap<>();

   public static void updateFromPayload(ODMHookSyncPayload payload) {
      RemoteHookTracker.RemoteHookData data = remoteHooks.computeIfAbsent(payload.playerId(), k -> new RemoteHookTracker.RemoteHookData());
      long now = System.currentTimeMillis();
      boolean leftStateChanged = data.leftActive != payload.leftActive()
         || data.leftExtending != payload.leftExtending()
         || data.leftRetracting != payload.leftRetracting();
      data.leftActive = payload.leftActive();
      data.leftExtending = payload.leftExtending();
      data.leftRetracting = payload.leftRetracting();
      data.leftPosition = payload.getLeftPosition();
      data.leftStartPosition = payload.getLeftStartPosition();
      data.leftLastSyncTime = now;
      if (leftStateChanged) {
         data.leftUpdateTime = now;
      }

      boolean rightStateChanged = data.rightActive != payload.rightActive()
         || data.rightExtending != payload.rightExtending()
         || data.rightRetracting != payload.rightRetracting();
      data.rightActive = payload.rightActive();
      data.rightExtending = payload.rightExtending();
      data.rightRetracting = payload.rightRetracting();
      data.rightPosition = payload.getRightPosition();
      data.rightStartPosition = payload.getRightStartPosition();
      data.rightLastSyncTime = now;
      if (rightStateChanged) {
         data.rightUpdateTime = now;
      }

      data.isBoosting = payload.isBoosting();
   }

   public static RemoteHookTracker.RemoteHookData getHookData(int playerId) {
      return remoteHooks.get(playerId);
   }

   public static Map<Integer, RemoteHookTracker.RemoteHookData> getAllHooks() {
      return remoteHooks;
   }

   public static void removePlayer(int playerId) {
      remoteHooks.remove(playerId);
   }

   public static void clear() {
      remoteHooks.clear();
   }

   @Environment(EnvType.CLIENT)
   public static class RemoteHookData {
      public boolean leftActive;
      public boolean leftExtending;
      public boolean leftRetracting;
      public Vec3d leftPosition = Vec3d.ZERO;
      public Vec3d leftStartPosition = Vec3d.ZERO;
      public long leftUpdateTime;
      public long leftLastSyncTime;
      public boolean rightActive;
      public boolean rightExtending;
      public boolean rightRetracting;
      public Vec3d rightPosition = Vec3d.ZERO;
      public Vec3d rightStartPosition = Vec3d.ZERO;
      public long rightUpdateTime;
      public long rightLastSyncTime;
      public boolean isBoosting;

      public Vec3d getLeftCurrentPosition() {
         if (this.leftRetracting) {
            float progress = getRetractionProgress(this.leftUpdateTime);
            return this.leftPosition.lerp(this.leftStartPosition, progress);
         } else if (this.leftExtending) {
            float progress = getExtensionProgress(this.leftUpdateTime);
            return this.leftStartPosition.lerp(this.leftPosition, progress);
         } else {
            return this.leftPosition;
         }
      }

      public Vec3d getRightCurrentPosition() {
         if (this.rightRetracting) {
            float progress = getRetractionProgress(this.rightUpdateTime);
            return this.rightPosition.lerp(this.rightStartPosition, progress);
         } else if (this.rightExtending) {
            float progress = getExtensionProgress(this.rightUpdateTime);
            return this.rightStartPosition.lerp(this.rightPosition, progress);
         } else {
            return this.rightPosition;
         }
      }

      public float getLeftExtensionProgress() {
         return !this.leftExtending ? 1.0F : getExtensionProgress(this.leftUpdateTime);
      }

      public float getLeftRetractionProgress() {
         return !this.leftRetracting ? 0.0F : getRetractionProgress(this.leftUpdateTime);
      }

      public float getRightExtensionProgress() {
         return !this.rightExtending ? 1.0F : getExtensionProgress(this.rightUpdateTime);
      }

      public float getRightRetractionProgress() {
         return !this.rightRetracting ? 0.0F : getRetractionProgress(this.rightUpdateTime);
      }

      private static float getExtensionProgress(long updateTime) {
         long elapsed = System.currentTimeMillis() - updateTime;
         return Math.min(1.0F, (float)elapsed / 150.0F);
      }

      private static float getRetractionProgress(long updateTime) {
         long elapsed = System.currentTimeMillis() - updateTime;
         return Math.min(1.0F, (float)elapsed / 200.0F);
      }
   }
}
