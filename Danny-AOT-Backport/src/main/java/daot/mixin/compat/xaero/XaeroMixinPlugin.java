package daot.mixin.compat.xaero;

import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

@Environment(EnvType.CLIENT)
public class XaeroMixinPlugin implements IMixinConfigPlugin {
   private static final String MINIMAP_MOD_ID = "xaerominimap";
   private static final String WORLDMAP_MOD_ID = "xaeroworldmap";
   private boolean minimapLoaded;
   private boolean worldmapLoaded;

   public void onLoad(String mixinPackage) {
      FabricLoader loader = FabricLoader.getInstance();
      this.minimapLoaded = loader.isModLoaded("xaerominimap");
      this.worldmapLoaded = loader.isModLoaded("xaeroworldmap");
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (mixinClassName.endsWith("RadarElementReaderMixin") || mixinClassName.endsWith("PlayerTrackerMinimapElementReaderMixin")) {
         return this.minimapLoaded;
      } else {
         return mixinClassName.endsWith("PlayerTrackerMapElementReaderMixin") ? this.worldmapLoaded : false;
      }
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
