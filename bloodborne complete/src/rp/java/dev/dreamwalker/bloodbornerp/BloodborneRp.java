package dev.dreamwalker.bloodbornerp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.mob.MobRegistry;
import dev.dreamwalker.bloodbornerp.weapon.WeaponRegistry;
import dev.dreamwalker.bloodbornerp.object.ObjectRegistry;
import dev.dreamwalker.bloodbornerp.lamp.LampService;
import dev.dreamwalker.bloodbornerp.command.RpCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class BloodborneRp implements ModInitializer {
 public static final String ID="bloodborne_rp";
 public static final Logger LOGGER=LoggerFactory.getLogger(ID);
 public static Identifier id(String path) { return new Identifier(ID,path); }
 @Override public void onInitialize() {
  AssetCatalog.load(); RpConfig.load(); dev.dreamwalker.bloodbornerp.content.RpSounds.register();
  MobRegistry.register(); WeaponRegistry.register(); ObjectRegistry.register();
  Registry.register(Registries.ITEM,id("blood_vial"),new dev.dreamwalker.bloodbornerp.content.BloodVialItem());
  LampService.initialize(); RpCommands.register();
  LOGGER.info("Bloodborne RP initialized: standard Minecraft health; independent namespace");
 }
}
