package daot;

import daot.world.ParadisChunkGenerator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.ServerWorldAccess;

public class BreachManager extends PersistentState {
   private static final String KEY = "dmnk_breach";
   public static final String[] WALL_NAMES = new String[]{"Sina", "Rose", "Maria"};
   public static final String[] DISTRICT_NAMES = new String[]{
      "Orvud", "Stohess", "Ehrmich", "Yarckcel", "Utopia", "Karanes", "Trost", "Krovla", "Excalibur", "Holst", "Shiganshina", "Quinta"
   };
   private final Set<Integer> breachedWalls = new LinkedHashSet<>();
   private final Set<Integer> breachedDistricts = new LinkedHashSet<>();

   public static int wallIndexByName(String name) {
      for (int i = 0; i < WALL_NAMES.length; i++) {
         if (WALL_NAMES[i].equalsIgnoreCase(name)) {
            return i;
         }
      }

      return -1;
   }

   public static int districtIdByName(String name) {
      for (int i = 0; i < DISTRICT_NAMES.length; i++) {
         if (DISTRICT_NAMES[i].equalsIgnoreCase(name)) {
            return i;
         }
      }

      return -1;
   }

   public static String wallName(int ring) {
      return WALL_NAMES[ring];
   }

   public static String districtName(int id) {
      return DISTRICT_NAMES[id];
   }

   public static int wallOfDistrict(int id) {
      return id / 4;
   }

   public boolean isWallBreached(int ring) {
      return this.breachedWalls.contains(ring);
   }

   public boolean isDistrictBreached(int id) {
      return this.breachedDistricts.contains(id);
   }

   public Set<Integer> getBreachedWalls() {
      return this.breachedWalls;
   }

   public Set<Integer> getBreachedDistricts() {
      return this.breachedDistricts;
   }

   public int[] breachedWallsArray() {
      return toIntArray(this.breachedWalls);
   }

   public int[] breachedDistrictsArray() {
      return toIntArray(this.breachedDistricts);
   }

   public boolean breachWall(int ring) {
      if (this.breachedWalls.add(ring)) {
         this.markDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean clearWall(int ring) {
      if (this.breachedWalls.remove(ring)) {
         this.markDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean breachDistrict(int id) {
      if (this.breachedDistricts.add(id)) {
         this.markDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean clearDistrict(int id) {
      if (this.breachedDistricts.remove(id)) {
         this.markDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean clearAll() {
      if (this.breachedWalls.isEmpty() && this.breachedDistricts.isEmpty()) {
         return false;
      } else {
         this.breachedWalls.clear();
         this.breachedDistricts.clear();
         this.markDirty();
         return true;
      }
   }

   @Override
   public NbtCompound writeNbt(NbtCompound nbt) {
      nbt.putIntArray("walls", toIntArray(this.breachedWalls));
      nbt.putIntArray("districts", toIntArray(this.breachedDistricts));
      return nbt;
   }

   public static BreachManager load(NbtCompound tag) {
      BreachManager data = new BreachManager();

      for (int w : tag.getIntArray("walls")) {
         if (w >= 0 && w < WALL_NAMES.length) {
            data.breachedWalls.add(w);
         }
      }

      for (int d : tag.getIntArray("districts")) {
         if (d >= 0 && d < DISTRICT_NAMES.length) {
            data.breachedDistricts.add(d);
         }
      }

      return data;
   }

   private static int[] toIntArray(Set<Integer> set) {
      int[] arr = new int[set.size()];
      int i = 0;

      for (int v : set) {
         arr[i++] = v;
      }

      return arr;
   }

   public static BreachManager get(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(BreachManager::load, BreachManager::new, "dmnk_breach");
   }

   public static boolean isSpawnZone(int x, int z, Set<Integer> breachedWalls, Set<Integer> breachedDistricts) {
      int district = ParadisChunkGenerator.districtIndexAt(x, z);
      if (district >= 0) {
         return breachedDistricts.contains(district);
      } else {
         int band = ParadisChunkGenerator.spawnBandIndex(x, z);
         return band < 0 ? true : breachedWalls.contains(band);
      }
   }

   public boolean isSpawnZone(int x, int z) {
      return isSpawnZone(x, z, this.breachedWalls, this.breachedDistricts);
   }

   public static boolean isFogZone(MinecraftServer server, int x, int z) {
      return FogEventState.isActive() && get(server).isSpawnZone(x, z);
   }

   public static boolean isNaturalSpawnAllowed(EntityType<?> type, ServerWorldAccess level, int x, int z) {
      if (!ParadisChunkGenerator.isDmnkActive()) {
         return !ParadisChunkGenerator.isPositionInsideAnyVillage(x, z);
      } else if (ParadisChunkGenerator.isOnMegaRingWall(x, z)) {
         return false;
      } else {
         return !get(level.toServerWorld().getServer()).isSpawnZone(x, z) ? false : !FogEventState.isActive() || type == DannysAot.ABNORMAL_TITAN;
      }
   }
}
