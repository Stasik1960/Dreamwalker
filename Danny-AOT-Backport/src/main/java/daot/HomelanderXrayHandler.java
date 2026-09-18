package daot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

@Environment(EnvType.CLIENT)
public final class HomelanderXrayHandler {
   private static final double MIN_RANGE = 5.0;
   private static final double MAX_RANGE = 20.0;
   private static final double RANGE_PER_SCROLL_NOTCH = 2.0;
   private static final double HALF_ANGLE_DEG = 14.0;
   private static double currentRangeBlocks = 20.0;
   private static final int FADE_DURATION_TICKS = 6;
   private static final int BLOCKS_PER_TICK_CAP = 600;
   private static final Map<BlockPos, BlockState> hiddenBlocks = new HashMap<>();
   private static boolean xHeldLast = false;
   private static float radiusProgress = 0.0F;

   private HomelanderXrayHandler() {
   }

   public static boolean shouldInterceptScroll() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || mc.currentScreen != null) {
         return false;
      } else if (mc.getWindow() == null) {
         return false;
      } else if (BloodlineClientData.get(mc.player.getUuid()) != BloodlineType.HOMELANDER) {
         return false;
      } else {
         return ModEffects.isPowerDisabled(mc.player) ? false : InputUtil.isKeyPressed(mc.getWindow().getHandle(), 88);
      }
   }

   public static void adjustRange(double scrollSign) {
      double delta = scrollSign > 0.0 ? 2.0 : -2.0;
      currentRangeBlocks = Math.max(5.0, Math.min(20.0, currentRangeBlocks + delta));
   }

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(HomelanderXrayHandler::tick);
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> restoreAll());
   }

   private static void tick(MinecraftClient mc) {
      ClientPlayerEntity player = mc.player;
      ClientWorld level = mc.world;
      if (player != null && level != null) {
         boolean isHomelander = BloodlineClientData.get(player.getUuid()) == BloodlineType.HOMELANDER;
         if (ModEffects.isPowerDisabled(player)) {
            isHomelander = false;
         }

         boolean inGui = mc.currentScreen != null;
         boolean xHeld = !inGui && mc.getWindow() != null && isHomelander && InputUtil.isKeyPressed(mc.getWindow().getHandle(), 88);
         if (xHeld && !xHeldLast) {
            currentRangeBlocks = 20.0;
         }

         xHeldLast = xHeld;
         float step = 0.16666667F;
         if (xHeld) {
            radiusProgress = Math.min(1.0F, radiusProgress + step);
         } else {
            radiusProgress = Math.max(0.0F, radiusProgress - step);
         }

         if (!(radiusProgress <= 0.0F) || !hiddenBlocks.isEmpty()) {
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVec(1.0F);
            if (look.lengthSquared() < 1.0E-4) {
               restoreAll();
            } else {
               look = look.normalize();
               double range = currentRangeBlocks * radiusProgress;
               double tanAngle = Math.tan(Math.toRadians(14.0));
               Set<BlockPos> target = collectConeBlocks(level, eye, look, range, tanAngle);
               Iterator<Entry<BlockPos, BlockState>> it = hiddenBlocks.entrySet().iterator();

               while (it.hasNext()) {
                  Entry<BlockPos, BlockState> entry = it.next();
                  if (!target.contains(entry.getKey())) {
                     level.setBlockState(entry.getKey(), entry.getValue(), 11);
                     it.remove();
                  }
               }

               int added = 0;

               for (BlockPos pos : target) {
                  if (added >= 600) {
                     break;
                  }

                  if (!hiddenBlocks.containsKey(pos)) {
                     BlockState current = level.getBlockState(pos);
                     if (!current.isAir() && current.getFluidState().isEmpty() && !isCopperBlock(current)) {
                        hiddenBlocks.put(pos.toImmutable(), current);
                        level.setBlockState(pos, Blocks.AIR.getDefaultState(), 11);
                        added++;
                     }
                  }
               }
            }
         }
      } else {
         restoreAll();
         xHeldLast = false;
         radiusProgress = 0.0F;
      }
   }

   private static boolean isCopperBlock(BlockState state) {
      if (state.getBlock() == Blocks.LIGHTNING_ROD) {
         return true;
      } else {
         Identifier key = Registries.BLOCK.getId(state.getBlock());
         return key.getPath().contains("copper");
      }
   }

   private static Set<BlockPos> collectConeBlocks(ClientWorld level, Vec3d eye, Vec3d look, double range, double tanAngle) {
      Set<BlockPos> result = new HashSet<>();
      if (range < 0.5) {
         return result;
      } else {
         double radialMax = range * tanAngle + 0.5;
         Vec3d farEnd = eye.add(look.multiply(range));
         int minX = (int)Math.floor(Math.min(eye.x, farEnd.x) - radialMax);
         int maxX = (int)Math.ceil(Math.max(eye.x, farEnd.x) + radialMax);
         int minY = (int)Math.floor(Math.min(eye.y, farEnd.y) - radialMax);
         int maxY = (int)Math.ceil(Math.max(eye.y, farEnd.y) + radialMax);
         int minZ = (int)Math.floor(Math.min(eye.z, farEnd.z) - radialMax);
         int maxZ = (int)Math.ceil(Math.max(eye.z, farEnd.z) + radialMax);
         Mutable cursor = new Mutable();

         for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = minZ; z <= maxZ; z++) {
                  double cx = x + 0.5;
                  double cy = y + 0.5;
                  double cz = z + 0.5;
                  double dx = cx - eye.x;
                  double dy = cy - eye.y;
                  double dz = cz - eye.z;
                  double along = dx * look.x + dy * look.y + dz * look.z;
                  if (!(along < 0.0) && !(along > range)) {
                     double perpX = dx - look.x * along;
                     double perpY = dy - look.y * along;
                     double perpZ = dz - look.z * along;
                     double perpSq = perpX * perpX + perpY * perpY + perpZ * perpZ;
                     double maxPerp = along * tanAngle + 0.5;
                     if (!(perpSq > maxPerp * maxPerp)) {
                        cursor.set(x, y, z);
                        result.add(cursor.toImmutable());
                     }
                  }
               }
            }
         }

         return result;
      }
   }

   private static void restoreAll() {
      if (!hiddenBlocks.isEmpty()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         ClientWorld level = mc.world;
         if (level != null) {
            for (Entry<BlockPos, BlockState> entry : hiddenBlocks.entrySet()) {
               level.setBlockState(entry.getKey(), entry.getValue(), 11);
            }
         }

         hiddenBlocks.clear();
         radiusProgress = 0.0F;
      }
   }
}
