package daot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public class FlyingThunderSpearTracker {
   private static final Identifier TEXTURE = new Identifier("dannys-aot", "textures/item/thunder_spear.png");
   private static final float WIRE_THICKNESS = 0.0045F;
   private static final int WIRE_R = 65;
   private static final int WIRE_G = 65;
   private static final int WIRE_B = 65;
   private static final int WIRE_A = 255;
   private static final double LATCH_OFFSET = 1.1;
   private static final int WIRE_SEGMENTS = 20;
   private static final double ZIGZAG_AMPLITUDE = 0.15;
   private static final int ZIGZAG_FREQUENCY = 3;
   private static final double ZIGZAG_REF_DISTANCE = 15.0;
   private static final int ZIGZAG_FADE_TICKS = 8;
   private static final int SNAP_ZIGZAG_TICKS = 13;
   private static final int MIN_VISUAL_FLIGHT_TICKS = 0;
   private static final double TRIGGER_STRETCH = 5.0;
   private static final double MIN_LODGE_WIRE = 5.0;
   private static final int LODGE_GRACE_TICKS = 10;
   private static final double MAX_RANGE = 100.0;
   private static final List<FlyingThunderSpearTracker.FlyingSpear> activeSpears = new ArrayList<>();
   private static FlyingThunderSpearTracker.FlyingSpearModel geoModel;

   private static FlyingThunderSpearTracker.FlyingSpearModel getModel() {
      if (geoModel == null) {
         geoModel = new FlyingThunderSpearTracker.FlyingSpearModel();
      }

      return geoModel;
   }

   public static void spawn(Vec3d position, Vec3d direction, float speed, boolean isLeftSide, Vec3d animDir) {
      Vec3d velocity = direction.normalize().multiply(speed);
      activeSpears.add(new FlyingThunderSpearTracker.FlyingSpear(position, velocity, isLeftSide, animDir));
   }

   public static void onServerLodge(double lx, double ly, double lz, int entityId) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Vec3d lodgePos = new Vec3d(lx, ly, lz);
      FlyingThunderSpearTracker.FlyingSpear closest = null;
      double closestDist = Double.MAX_VALUE;

      for (FlyingThunderSpearTracker.FlyingSpear s : activeSpears) {
         if (s.state == FlyingThunderSpearTracker.SpearState.FLYING) {
            double d = new Vec3d(s.x, s.y, s.z).distanceTo(lodgePos);
            if (d < closestDist) {
               closestDist = d;
               closest = s;
            }
         }
      }

      if (closest != null && closestDist < 30.0) {
         closest.x = lx;
         closest.y = ly;
         closest.z = lz;
         closest.prevX = lx;
         closest.prevY = ly;
         closest.prevZ = lz;
         if (entityId >= 0) {
            closest.lodgeEntityId = entityId;
            closest.lodgeEntityOffset = null;
         }

         if (closest.state == FlyingThunderSpearTracker.SpearState.FLYING) {
            lodgeSpear(closest, mc);
         }
      }
   }

   public static void onServerExplosion(double ex, double ey, double ez) {
      Vec3d explosionPos = new Vec3d(ex, ey, ez);
      FlyingThunderSpearTracker.FlyingSpear closest = null;
      double closestDist = Double.MAX_VALUE;

      for (FlyingThunderSpearTracker.FlyingSpear s : activeSpears) {
         double d = new Vec3d(s.x, s.y, s.z).distanceTo(explosionPos);
         if (d < closestDist) {
            closestDist = d;
            closest = s;
         }
      }

      if (closest != null && closestDist < 10.0) {
         activeSpears.remove(closest);
      }
   }

   public static void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      Iterator<FlyingThunderSpearTracker.FlyingSpear> it = activeSpears.iterator();

      while (it.hasNext()) {
         FlyingThunderSpearTracker.FlyingSpear s = it.next();
         s.ticksAlive++;
         if (s.state == FlyingThunderSpearTracker.SpearState.FLYING) {
            tickFlying(s, mc);
         } else if (s.state == FlyingThunderSpearTracker.SpearState.LODGED) {
            s.lodgeTick++;
            tickLodged(s, mc);
         } else if (s.state == FlyingThunderSpearTracker.SpearState.TRIGGERED) {
            s.snapTick++;
            if (s.snapTick >= 13) {
               it.remove();
               continue;
            }
         }

         if (s.ticksAlive > 600) {
            it.remove();
         }
      }
   }

   private static void tickFlying(FlyingThunderSpearTracker.FlyingSpear s, MinecraftClient mc) {
      s.prevX = s.x;
      s.prevY = s.y;
      s.prevZ = s.z;
      if (s.ticksAlive <= 8 && s.animDir != null) {
         float t = Math.min(1.0F, s.ticksAlive / 8.0F);
         t = t * t * (3.0F - 2.0F * t);
         double dx = s.startVx + (s.targetVx - s.startVx) * t;
         double dy = s.startVy + (s.targetVy - s.startVy) * t;
         double dz = s.startVz + (s.targetVz - s.startVz) * t;
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len > 0.001) {
            s.vx = dx / len * s.speed;
            s.vy = dy / len * s.speed;
            s.vz = dz / len * s.speed;
         }
      }

      double nextX = s.x + s.vx;
      double nextY = s.y + s.vy;
      double nextZ = s.z + s.vz;
      if (mc.world != null) {
         Vec3d from = new Vec3d(s.x, s.y, s.z);
         Vec3d to = new Vec3d(nextX, nextY, nextZ);
         Vec3d blockHitPos = null;
         BlockPos blockHitBlockPos = null;
         BlockHitResult blockHit = mc.world.raycast(new RaycastContext(from, to, ShapeType.COLLIDER, FluidHandling.NONE, mc.player));
         if (blockHit.getType() == Type.BLOCK) {
            blockHitPos = blockHit.getPos();
            blockHitBlockPos = blockHit.getBlockPos();
            to = blockHitPos;
         }

         Entity hitEntity = null;
         Vec3d entityHitPos = null;
         if (mc.player != null) {
            Box pathBox = new Box(
               Math.min(from.x, to.x) - 0.5,
               Math.min(from.y, to.y) - 0.5,
               Math.min(from.z, to.z) - 0.5,
               Math.max(from.x, to.x) + 0.5,
               Math.max(from.y, to.y) + 0.5,
               Math.max(from.z, to.z) + 0.5
            );
            double bestDist = Double.MAX_VALUE;

            for (Entity entity : mc.world.getOtherEntities(mc.player, pathBox)) {
               if (entity != mc.player) {
                  Box entityBox = entity.getBoundingBox().expand(0.3);
                  Optional<Vec3d> clip = entityBox.raycast(from, to);
                  if (clip.isPresent()) {
                     double dist = from.squaredDistanceTo(clip.get());
                     if (dist < bestDist) {
                        bestDist = dist;
                        hitEntity = entity;
                        entityHitPos = clip.get();
                     }
                  } else if (entityBox.contains(from)) {
                     double dist = from.squaredDistanceTo(entity.getPos());
                     if (dist < bestDist) {
                        bestDist = dist;
                        hitEntity = entity;
                        entityHitPos = from;
                     }
                  }
               }
            }
         }

         if (hitEntity != null) {
            Vec3d vel = new Vec3d(s.vx, s.vy, s.vz).normalize();
            s.x = entityHitPos.x + vel.x * 0.15;
            s.y = entityHitPos.y + vel.y * 0.15;
            s.z = entityHitPos.z + vel.z * 0.15;
            s.prevX = s.x;
            s.prevY = s.y;
            s.prevZ = s.z;
            s.lodgeEntityId = hitEntity.getId();
            lodgeSpear(s, mc);
            spawnBloodParticles(mc, entityHitPos);
            return;
         }

         if (blockHitPos != null) {
            Vec3d vel = new Vec3d(s.vx, s.vy, s.vz).normalize();
            s.x = blockHitPos.x + vel.x * 0.15;
            s.y = blockHitPos.y + vel.y * 0.15;
            s.z = blockHitPos.z + vel.z * 0.15;
            s.prevX = s.x;
            s.prevY = s.y;
            s.prevZ = s.z;
            lodgeSpear(s, mc);
            BlockState blockState = mc.world.getBlockState(blockHitBlockPos);
            if (!blockState.isAir()) {
               spawnBlockImpactParticles(mc, blockHitPos, blockState);
            }

            return;
         }
      }

      s.x = nextX;
      s.y = nextY;
      s.z = nextZ;
      double flightDist = new Vec3d(s.x, s.y, s.z).distanceTo(s.spawnPos);
      if (flightDist >= 100.0) {
         triggerSnap(s, mc);
      } else {
         if (mc.world != null) {
            Vec3d velDir = new Vec3d(s.vx, s.vy, s.vz);
            if (velDir.lengthSquared() > 1.0E-4) {
               velDir = velDir.normalize();
            }

            Vec3d latchPos = new Vec3d(s.x, s.y, s.z).subtract(velDir.multiply(1.1));
            mc.world.addParticle(daot.compat.BackportEffects.DUST_PLUME, latchPos.x, latchPos.y, latchPos.z, 0.0, 0.0, 0.0);
            mc.world.addParticle(ParticleTypes.FLAME, latchPos.x, latchPos.y, latchPos.z, 0.0, 0.0, 0.0);
         }
      }
   }

   private static void lodgeSpear(FlyingThunderSpearTracker.FlyingSpear s, MinecraftClient mc) {
      s.state = FlyingThunderSpearTracker.SpearState.LODGED;
      s.lodgeTick = 0;
      if (mc.player != null) {
         s.lodgeDistance = Math.max(mc.player.getPos().distanceTo(new Vec3d(s.x, s.y, s.z)), 5.0);
      }
   }

   private static void triggerSnap(FlyingThunderSpearTracker.FlyingSpear s, MinecraftClient mc) {
      s.state = FlyingThunderSpearTracker.SpearState.TRIGGERED;
      s.snapTick = 0;
      CameraShakeHandler.triggerShake(0.5F);
      BoostFovHandler.triggerThunderSpearSnapFov();
      if (mc.player != null) {
         boolean mainIsRight = mc.player.getMainArm() == Arm.RIGHT;
         Hand hand = s.isLeftSide == !mainIsRight ? Hand.MAIN_HAND : Hand.OFF_HAND;
         BladeAnimationHandler.triggerThunderSpearSnap(hand);
      }

      if (mc.player != null) {
         float pYaw = (float)Math.toRadians(mc.player.getYaw());
         Vec3d playerPos = mc.player.getPos();
         double sinY = Math.sin(pYaw);
         double cosY = Math.cos(pYaw);
         double sideOff = s.isLeftSide ? 0.25 : -0.25;
         s.triggerGripPos = playerPos.add(-sinY * 0.3 + cosY * sideOff, 1.1, cosY * 0.3 + sinY * sideOff);
      }
   }

   private static void spawnBlockImpactParticles(MinecraftClient mc, Vec3d hitPos, BlockState blockState) {
      if (mc.world != null) {
         for (int i = 0; i < 20; i++) {
            double ox = (Math.random() - 0.5) * 0.8;
            double oy = (Math.random() - 0.5) * 0.8;
            double oz = (Math.random() - 0.5) * 0.8;
            double vx = (Math.random() - 0.5) * 0.6;
            double vy = Math.random() * 0.6;
            double vz = (Math.random() - 0.5) * 0.6;
            mc.world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, blockState), hitPos.x + ox, hitPos.y + oy, hitPos.z + oz, vx, vy, vz);
         }
      }
   }

   private static void spawnBloodParticles(MinecraftClient mc, Vec3d hitPos) {
      if (mc.world != null) {
         BlockState redBlock = Blocks.REDSTONE_BLOCK.getDefaultState();

         for (int i = 0; i < 20; i++) {
            double ox = (Math.random() - 0.5) * 0.8;
            double oy = (Math.random() - 0.5) * 0.8;
            double oz = (Math.random() - 0.5) * 0.8;
            double vx = (Math.random() - 0.5) * 0.6;
            double vy = Math.random() * 0.6;
            double vz = (Math.random() - 0.5) * 0.6;
            mc.world.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, redBlock), hitPos.x + ox, hitPos.y + oy, hitPos.z + oz, vx, vy, vz);
         }
      }
   }

   private static void tickLodged(FlyingThunderSpearTracker.FlyingSpear s, MinecraftClient mc) {
      if (s.lodgeEntityId >= 0 && mc.world != null) {
         Entity entity = mc.world.getEntityById(s.lodgeEntityId);
         if (entity != null && entity.isAlive()) {
            if (s.lodgeEntityOffset == null) {
               s.lodgeEntityOffset = new Vec3d(s.x, s.y, s.z).subtract(entity.getPos());
            }

            Vec3d target = entity.getPos().add(s.lodgeEntityOffset);
            s.prevX = s.x;
            s.prevY = s.y;
            s.prevZ = s.z;
            s.x = target.x;
            s.y = target.y;
            s.z = target.z;
            if (mc.player != null) {
               double newDist = mc.player.getPos().distanceTo(target);
               s.lodgeDistance = Math.max(s.lodgeDistance, newDist);
            }
         }
      }

      if (mc.player != null && s.lodgeDistance >= 0.0 && s.lodgeTick > 10) {
         double current = mc.player.getPos().distanceTo(new Vec3d(s.x, s.y, s.z));
         if (current > s.lodgeDistance + 5.0) {
            triggerSnap(s, mc);
         }
      }
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(FlyingThunderSpearTracker::onWorldRender);
   }

   private static void onWorldRender(WorldRenderContext context) {
      if (!activeSpears.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            MatrixStack poseStack = context.matrixStack();
            Immediate bufferSource = mc.getBufferBuilders().getEntityVertexConsumers();
            Vec3d camPos = context.camera().getPos();
            float partialTick = context.tickDelta();
            FlyingThunderSpearTracker.FlyingSpearModel model = getModel();
            BakedGeoModel bakedModel = model.getBakedModel(model.getModelResource(null));
            if (bakedModel != null) {
               GeoBone groupBone = (GeoBone)bakedModel.getBone("group").orElse(null);
               GeoBone boneBone = (GeoBone)bakedModel.getBone("bone").orElse(null);
               if (groupBone != null) {
                  groupBone.setHidden(true);
               }

               if (boneBone != null) {
                  boneBone.setHidden(true);
               }

               RenderLayer renderType = RenderLayer.getEntityCutoutNoCull(TEXTURE);

               for (FlyingThunderSpearTracker.FlyingSpear s : activeSpears) {
                  double rx = lerpPos(s.prevX, s.x, partialTick);
                  double ry = lerpPos(s.prevY, s.y, partialTick);
                  double rz = lerpPos(s.prevZ, s.z, partialTick);
                  poseStack.push();
                  poseStack.translate(rx - camPos.x, ry - camPos.y, rz - camPos.z);
                  double vx = s.vx;
                  double vy = s.vy;
                  double vz = s.vz;
                  double hSpd = Math.sqrt(vx * vx + vz * vz);
                  float velYaw = (float)Math.toDegrees(Math.atan2(vx, vz));
                  float velPitch = (float)Math.toDegrees(Math.atan2(-vy, hSpd));
                  float yaw;
                  float pitch;
                  if (s.ticksAlive < 8 && s.animDir != null) {
                     double ax = s.animDir.x;
                     double ay = s.animDir.y;
                     double az = s.animDir.z;
                     double aHSpd = Math.sqrt(ax * ax + az * az);
                     float animYaw = (float)Math.toDegrees(Math.atan2(ax, az));
                     float animPitch = (float)Math.toDegrees(Math.atan2(-ay, aHSpd));
                     float t = Math.min(1.0F, (s.ticksAlive + partialTick) / 8.0F);
                     t = t * t * (3.0F - 2.0F * t);
                     yaw = lerpAngle(animYaw, velYaw, t);
                     pitch = animPitch + (velPitch - animPitch) * t;
                  } else {
                     yaw = velYaw;
                     pitch = velPitch;
                  }

                  poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw + 180.0F));
                  poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
                  double lx = rx;
                  double ly = ry;
                  double lz = rz;
                  Vec3d velNorm = new Vec3d(s.vx, s.vy, s.vz);
                  if (velNorm.lengthSquared() > 1.0E-4) {
                     velNorm = velNorm.normalize();
                     lx = rx - velNorm.x * 0.6;
                     ly = ry - velNorm.y * 0.6;
                     lz = rz - velNorm.z * 0.6;
                  }

                  BlockPos lightPos = BlockPos.ofFloored(lx, ly, lz);
                  int packedLight = WorldRenderer.getLightmapCoordinates(mc.world, lightPos);
                  int lightAbove = WorldRenderer.getLightmapCoordinates(mc.world, lightPos.up());
                  if (lightAbove > packedLight) {
                     packedLight = lightAbove;
                  }

                  VertexConsumer buf = bufferSource.getBuffer(renderType);
                  GeoModelHelper.renderModel(poseStack, bakedModel, buf, packedLight, OverlayTexture.DEFAULT_UV, -1);
                  poseStack.pop();
               }

               bufferSource.draw(renderType);
               if (mc.player != null) {
                  VertexConsumer wireBuf = bufferSource.getBuffer(RenderLayer.getDebugQuads());
                  Matrix4f wireMat = poseStack.peek().getPositionMatrix();
                  Vec3d playerPos = mc.player.getLerpedPos(partialTick);
                  float pYaw = (float)Math.toRadians(mc.player.getYaw());

                  for (FlyingThunderSpearTracker.FlyingSpear s : activeSpears) {
                     double sinY = Math.sin(pYaw);
                     double cosY = Math.cos(pYaw);
                     double sideOff = s.isLeftSide ? 0.25 : -0.25;
                     double fwdOff = 0.3;
                     Vec3d gripPos = playerPos.add(-sinY * fwdOff + cosY * sideOff, 1.1, cosY * fwdOff + sinY * sideOff);
                     double rxx = lerpPos(s.prevX, s.x, partialTick);
                     double ryx = lerpPos(s.prevY, s.y, partialTick);
                     double rzx = lerpPos(s.prevZ, s.z, partialTick);
                     Vec3d spearPos = new Vec3d(rxx, ryx, rzx);
                     Vec3d velDir = new Vec3d(s.vx, s.vy, s.vz);
                     if (velDir.lengthSquared() > 1.0E-4) {
                        velDir = velDir.normalize();
                     }

                     Vec3d latchPos = spearPos.subtract(velDir.multiply(1.1));
                     if (s.state == FlyingThunderSpearTracker.SpearState.TRIGGERED) {
                        float retractProgress = Math.min(1.0F, s.snapTick / 13.0F);
                        retractProgress = retractProgress * retractProgress * (3.0F - 2.0F * retractProgress);
                        Vec3d retractTarget = s.triggerGripPos != null ? s.triggerGripPos : gripPos;
                        latchPos = latchPos.add(retractTarget.subtract(latchPos).multiply(retractProgress));
                     }

                     Vec3d relStart = gripPos.subtract(camPos);
                     Vec3d relEnd = latchPos.subtract(camPos);
                     float zigzag = 0.0F;
                     if (s.state == FlyingThunderSpearTracker.SpearState.FLYING) {
                        zigzag = Math.max(0.0F, 1.0F - s.ticksAlive / 8.0F);
                     } else if (s.state == FlyingThunderSpearTracker.SpearState.TRIGGERED) {
                        zigzag = Math.min(1.0F, s.snapTick / 5.2000003F);
                     }

                     if (zigzag > 0.001F) {
                        renderZigzagWire(wireBuf, wireMat, relStart, relEnd, camPos, zigzag);
                     } else {
                        renderStraightWire(wireBuf, wireMat, relStart, relEnd, camPos);
                     }
                  }

                  bufferSource.draw(RenderLayer.getDebugQuads());
               }

               if (groupBone != null) {
                  groupBone.setHidden(false);
               }

               if (boneBone != null) {
                  boneBone.setHidden(false);
               }
            }
         }
      }
   }

   private static void renderStraightWire(VertexConsumer buf, Matrix4f mat, Vec3d start, Vec3d end, Vec3d camPos) {
      renderRibbon(buf, mat, new Vec3d[]{start, end});
   }

   private static void renderZigzagWire(VertexConsumer buf, Matrix4f mat, Vec3d start, Vec3d end, Vec3d camPos, float intensity) {
      double dist = start.distanceTo(end);
      if (dist < 0.1) {
         renderStraightWire(buf, mat, start, end, camPos);
      } else {
         Vec3d dir = end.subtract(start).normalize();
         Vec3d up = new Vec3d(0.0, 1.0, 0.0);
         Vec3d perp = dir.crossProduct(up).normalize();
         if (perp.lengthSquared() < 0.01) {
            perp = dir.crossProduct(new Vec3d(1.0, 0.0, 0.0)).normalize();
         }

         double effFreq = 3.0 * (dist / 15.0);
         Vec3d[] pts = new Vec3d[21];
         pts[0] = start;

         for (int i = 1; i <= 20; i++) {
            float t = i / 20.0F;
            Vec3d lin = start.add(end.subtract(start).multiply(t));
            double wave = Math.sin(t * Math.PI * effFreq * 2.0) * 0.15 * intensity;
            pts[i] = lin.add(perp.multiply(wave));
         }

         renderRibbon(buf, mat, pts);
      }
   }

   private static void renderRibbon(VertexConsumer buf, Matrix4f mat, Vec3d[] pts) {
      if (pts.length >= 2) {
         float half = 0.00225F;

         for (int i = 0; i < pts.length - 1; i++) {
            Vec3d p1 = pts[i];
            Vec3d p2 = pts[i + 1];
            Vec3d ld = p2.subtract(p1);
            if (!(ld.lengthSquared() < 1.0E-5)) {
               ld = ld.normalize();
               Vec3d mid = p1.add(p2).multiply(0.5);
               Vec3d toCam = mid.multiply(-1.0);
               Vec3d perp = ld.crossProduct(toCam);
               if (perp.lengthSquared() < 1.0E-5) {
                  perp = ld.crossProduct(new Vec3d(0.0, 1.0, 0.0));
               }

               perp = perp.normalize().multiply(half);
               Vec3d v1 = p1.add(perp);
               Vec3d v2 = p1.subtract(perp);
               Vec3d v3 = p2.subtract(perp);
               Vec3d v4 = p2.add(perp);
               buf.vertex(mat, (float)v1.x, (float)v1.y, (float)v1.z).color(65, 65, 65, 255).next();
               buf.vertex(mat, (float)v2.x, (float)v2.y, (float)v2.z).color(65, 65, 65, 255).next();
               buf.vertex(mat, (float)v3.x, (float)v3.y, (float)v3.z).color(65, 65, 65, 255).next();
               buf.vertex(mat, (float)v4.x, (float)v4.y, (float)v4.z).color(65, 65, 65, 255).next();
            }
         }
      }
   }

   private static double lerpPos(double prev, double cur, float pt) {
      return prev + (cur - prev) * pt;
   }

   private static float lerpAngle(float from, float to, float t) {
      float diff = to - from;

      while (diff > 180.0F) {
         diff -= 360.0F;
      }

      while (diff < -180.0F) {
         diff += 360.0F;
      }

      return from + diff * t;
   }

   @Environment(EnvType.CLIENT)
   static class FlyingSpear {
      static final int ROTATION_TRANSITION_TICKS = 8;
      double x;
      double y;
      double z;
      double prevX;
      double prevY;
      double prevZ;
      double vx;
      double vy;
      double vz;
      int ticksAlive;
      boolean isLeftSide;
      Vec3d spawnPos;
      Vec3d animDir;
      FlyingThunderSpearTracker.SpearState state = FlyingThunderSpearTracker.SpearState.FLYING;
      double lodgeDistance = -1.0;
      int lodgeTick = 0;
      int snapTick = 0;
      Vec3d triggerGripPos = null;
      boolean repelled = false;
      int lodgeEntityId = -1;
      Vec3d lodgeEntityOffset = null;
      boolean hasPendingLodge = false;
      double pendingLodgeX;
      double pendingLodgeY;
      double pendingLodgeZ;
      int pendingLodgeEntityId = -1;
      double startVx;
      double startVy;
      double startVz;
      double targetVx;
      double targetVy;
      double targetVz;
      double speed;

      FlyingSpear(Vec3d pos, Vec3d targetVelocity, boolean isLeftSide, Vec3d animDir) {
         this.x = pos.x;
         this.y = pos.y;
         this.z = pos.z;
         this.prevX = this.x;
         this.prevY = this.y;
         this.prevZ = this.z;
         this.speed = targetVelocity.length();
         this.targetVx = targetVelocity.x;
         this.targetVy = targetVelocity.y;
         this.targetVz = targetVelocity.z;
         if (animDir != null && animDir.lengthSquared() > 0.001) {
            Vec3d startVel = animDir.normalize().multiply(this.speed);
            this.vx = startVel.x;
            this.vy = startVel.y;
            this.vz = startVel.z;
         } else {
            this.vx = targetVelocity.x;
            this.vy = targetVelocity.y;
            this.vz = targetVelocity.z;
         }

         this.startVx = this.vx;
         this.startVy = this.vy;
         this.startVz = this.vz;
         this.isLeftSide = isLeftSide;
         this.spawnPos = pos;
         this.animDir = animDir;
      }
   }

   @Environment(EnvType.CLIENT)
   static class FlyingSpearModel extends GeoModel<ThunderSpearItem> {
      private static final Identifier MODEL = new Identifier("dannys-aot", "geo/thunder_spear.geo.json");

      public Identifier getModelResource(ThunderSpearItem a) {
         return MODEL;
      }

      public Identifier getTextureResource(ThunderSpearItem a) {
         return FlyingThunderSpearTracker.TEXTURE;
      }

      public Identifier getAnimationResource(ThunderSpearItem a) {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   static enum SpearState {
      FLYING,
      LODGED,
      TRIGGERED;
   }
}
