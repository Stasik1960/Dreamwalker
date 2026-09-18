package daot.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

public class PortalLocationTracker extends PersistentState {
   private static final String DATA_NAME = "dannys_aot_portals";
   private final List<BlockPos> overworldDocks = new ArrayList<>();
   private final List<BlockPos> paradisReturnPillars = new ArrayList<>();
   private final List<BlockPos> scatteredStructures = new ArrayList<>();

   public static PortalLocationTracker load(NbtCompound tag) {
      PortalLocationTracker tracker = new PortalLocationTracker();
      NbtList docksList = tag.getList("overworld_docks", 10);

      for (int i = 0; i < docksList.size(); i++) {
         NbtCompound posTag = docksList.getCompound(i);
         tracker.overworldDocks.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
      }

      NbtList pillarsList = tag.getList("paradis_return_pillars", 10);

      for (int i = 0; i < pillarsList.size(); i++) {
         NbtCompound posTag = pillarsList.getCompound(i);
         tracker.paradisReturnPillars.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
      }

      if (tag.contains("scattered_structures")) {
         NbtList structuresList = tag.getList("scattered_structures", 10);

         for (int i = 0; i < structuresList.size(); i++) {
            NbtCompound posTag = structuresList.getCompound(i);
            tracker.scatteredStructures.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
         }
      }

      return tracker;
   }

   @Override
   public NbtCompound writeNbt(NbtCompound nbt) {
      NbtList docksList = new NbtList();

      for (BlockPos pos : this.overworldDocks) {
         NbtCompound posTag = new NbtCompound();
         posTag.putInt("x", pos.getX());
         posTag.putInt("y", pos.getY());
         posTag.putInt("z", pos.getZ());
         docksList.add(posTag);
      }

      nbt.put("overworld_docks", docksList);
      NbtList pillarsList = new NbtList();

      for (BlockPos pos : this.paradisReturnPillars) {
         NbtCompound posTag = new NbtCompound();
         posTag.putInt("x", pos.getX());
         posTag.putInt("y", pos.getY());
         posTag.putInt("z", pos.getZ());
         pillarsList.add(posTag);
      }

      nbt.put("paradis_return_pillars", pillarsList);
      NbtList structuresList = new NbtList();

      for (BlockPos pos : this.scatteredStructures) {
         NbtCompound posTag = new NbtCompound();
         posTag.putInt("x", pos.getX());
         posTag.putInt("y", pos.getY());
         posTag.putInt("z", pos.getZ());
         structuresList.add(posTag);
      }

      nbt.put("scattered_structures", structuresList);
      return nbt;
   }

   public void addOverworldDock(BlockPos pos) {
      this.overworldDocks.add(pos);
      this.markDirty();
   }

   public void addParadisReturnPillar(BlockPos pos) {
      this.paradisReturnPillars.add(pos);
      this.markDirty();
   }

   public BlockPos getRandomOverworldDock(Random random) {
      return this.overworldDocks.isEmpty() ? null : this.overworldDocks.get(random.nextInt(this.overworldDocks.size()));
   }

   public BlockPos getRandomParadisReturnPillar(Random random) {
      return this.paradisReturnPillars.isEmpty() ? null : this.paradisReturnPillars.get(random.nextInt(this.paradisReturnPillars.size()));
   }

   public boolean isNearExistingReturnPillar(BlockPos pos, int minDistance) {
      int minDistSq = minDistance * minDistance;

      for (BlockPos existing : this.paradisReturnPillars) {
         int dx = pos.getX() - existing.getX();
         int dz = pos.getZ() - existing.getZ();
         if (dx * dx + dz * dz < minDistSq) {
            return true;
         }
      }

      return false;
   }

   public void addScatteredStructure(BlockPos pos) {
      this.scatteredStructures.add(pos);
      this.markDirty();
   }

   public boolean isNearExistingScatteredStructure(BlockPos pos, int minDistance) {
      int minDistSq = minDistance * minDistance;

      for (BlockPos existing : this.scatteredStructures) {
         int dx = pos.getX() - existing.getX();
         int dz = pos.getZ() - existing.getZ();
         if (dx * dx + dz * dz < minDistSq) {
            return true;
         }
      }

      return false;
   }

   public boolean isNearExistingDock(BlockPos pos, int minDistance) {
      int minDistSq = minDistance * minDistance;

      for (BlockPos existing : this.overworldDocks) {
         int dx = pos.getX() - existing.getX();
         int dz = pos.getZ() - existing.getZ();
         if (dx * dx + dz * dz < minDistSq) {
            return true;
         }
      }

      return false;
   }

   public static PortalLocationTracker get(ServerWorld level) {
      ServerWorld overworld = level.getServer().getWorld(World.OVERWORLD);
      if (overworld == null) {
         overworld = level;
      }

      return overworld.getPersistentStateManager().getOrCreate(PortalLocationTracker::load, PortalLocationTracker::new, "dannys_aot_portals");
   }
}
