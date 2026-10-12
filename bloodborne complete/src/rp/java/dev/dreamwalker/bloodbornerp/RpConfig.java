package dev.dreamwalker.bloodbornerp;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
public final class RpConfig {
 public int schemaVersion=1;
 public boolean bloodVialsEnabled=false;
 public float bloodVialHealing=4.0f;
 public int weaponTransformCooldownTicks=12;
 public int lampTravelCooldownTicks=100;
 public int maxLampDestinations=256;
 public int maxMechanismLinks=16;
 public boolean playerTravelEnabled=true;
 public boolean monstersAttackPlayers=true;
 public static RpConfig INSTANCE=new RpConfig();
 public static void load() {
  var path=FabricLoader.getInstance().getConfigDir().resolve("bloodborne-rp.json");
  var gson=new GsonBuilder().setPrettyPrinting().create();
  try {
   if(Files.exists(path)) { var parsed=gson.fromJson(Files.readString(path,StandardCharsets.UTF_8),RpConfig.class); if(parsed==null) throw new IllegalArgumentException("Empty config"); INSTANCE=parsed; }
   else { Files.createDirectories(path.getParent()); Files.writeString(path,gson.toJson(INSTANCE)+"\n",StandardCharsets.UTF_8); }
   INSTANCE.validate();
  } catch(Exception e) { throw new IllegalStateException("Invalid bloodborne-rp.json",e); }
 }
 public void validate() {
  if(schemaVersion!=1 || !Float.isFinite(bloodVialHealing) || bloodVialHealing<0 || bloodVialHealing>20
   || weaponTransformCooldownTicks<1 || weaponTransformCooldownTicks>200 || lampTravelCooldownTicks<1 || lampTravelCooldownTicks>12000
   || maxLampDestinations<1 || maxLampDestinations>256 || maxMechanismLinks<1 || maxMechanismLinks>64) throw new IllegalArgumentException("RP config outside allowed bounds");
 }
}
