package daot;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class HookPoint {
   public Vec3d position;
   public boolean active = false;
   public boolean isExtending = false;
   public boolean isRetracting = false;
   public Vec3d startPosition;
   public long shootTime;
   public long retractTime;
   public Vec3d playerVelocity = Vec3d.ZERO;
   public double maxRopeLength = 0.0;
   public Entity hookedEntity = null;
   public Vec3d entityOffset = Vec3d.ZERO;
   public String hookedBoneName = null;
   public static final long SHOOT_DURATION = 150L;
   public static final long RETRACT_DURATION = 200L;

   public void setHook(Vec3d targetPos, Vec3d startPos) {
      this.position = targetPos;
      this.startPosition = startPos;
      this.active = true;
      this.isExtending = true;
      this.isRetracting = false;
      this.shootTime = System.currentTimeMillis();
   }

   public void setHookOnEntity(Vec3d targetPos, Vec3d startPos, Entity entity) {
      this.position = targetPos;
      this.startPosition = startPos;
      this.active = true;
      this.isExtending = true;
      this.isRetracting = false;
      this.shootTime = System.currentTimeMillis();
      this.hookedEntity = entity;
      this.entityOffset = targetPos.subtract(entity.getPos());
      this.hookedBoneName = null;
   }

   public void setHookOnTitanBone(Vec3d targetPos, Vec3d startPos, Entity titan, String boneName) {
      this.position = targetPos;
      this.startPosition = startPos;
      this.active = true;
      this.isExtending = true;
      this.isRetracting = false;
      this.shootTime = System.currentTimeMillis();
      this.hookedEntity = titan;
      this.hookedBoneName = boneName;
      this.entityOffset = Vec3d.ZERO;
   }

   public void updateEntityPosition() {
      if (this.hookedEntity != null && !this.hookedEntity.isRemoved()) {
         this.position = this.hookedEntity.getPos().add(this.entityOffset);
      } else if (this.hookedEntity != null && this.hookedEntity.isRemoved()) {
         this.release();
      }
   }

   public void startRetract(Vec3d playerPos) {
      this.isRetracting = true;
      this.isExtending = false;
      this.retractTime = System.currentTimeMillis();
      this.startPosition = playerPos;
   }

   public void release() {
      this.active = false;
      this.isExtending = false;
      this.isRetracting = false;
      this.playerVelocity = Vec3d.ZERO;
      this.maxRopeLength = 0.0;
      this.hookedEntity = null;
      this.entityOffset = Vec3d.ZERO;
      this.hookedBoneName = null;
   }

   public void updateVelocity(Vec3d velocity) {
      this.playerVelocity = velocity;
   }

   public float getExtensionProgress() {
      if (!this.isExtending) {
         return 1.0F;
      } else {
         long elapsed = System.currentTimeMillis() - this.shootTime;
         float progress = Math.min(1.0F, (float)elapsed / 150.0F);
         if (progress >= 1.0F) {
            this.isExtending = false;
         }

         return progress;
      }
   }

   public float getRetractionProgress() {
      if (!this.isRetracting) {
         return 0.0F;
      } else {
         long elapsed = System.currentTimeMillis() - this.retractTime;
         float progress = Math.min(1.0F, (float)elapsed / 200.0F);
         if (progress >= 1.0F) {
            this.release();
         }

         return progress;
      }
   }

   public Vec3d getCurrentPosition() {
      if (this.isRetracting) {
         float progress = this.getRetractionProgress();
         return this.position.lerp(this.startPosition, progress);
      } else if (this.isExtending) {
         float progress = this.getExtensionProgress();
         return this.startPosition.lerp(this.position, progress);
      } else {
         return this.position;
      }
   }
}
