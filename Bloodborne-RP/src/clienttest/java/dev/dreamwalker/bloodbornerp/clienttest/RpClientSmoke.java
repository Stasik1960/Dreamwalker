package dev.dreamwalker.bloodbornerp.clienttest;

import dev.dreamwalker.bloodbornerp.BloodborneRp;
import dev.dreamwalker.bloodbornerp.content.AssetCatalog;
import dev.dreamwalker.bloodbornerp.content.AssetSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.GeckoLibCache;

/** Standalone development-client resource smoke test; it never opens a connection or accesses session data. */
public final class RpClientSmoke implements ClientModInitializer {
 private static final long TIMEOUT_NANOS = 120_000_000_000L;
 private static final int MIN_BAKED_MODELS = 101;
 private static final Identifier FALLBACK_MODEL = BloodborneRp.id("geo/fallback.geo.json");
 private static final Identifier FALLBACK_ANIMATION = BloodborneRp.id("animations/fallback.animation.json");
 private static final Set<String> WEAPON_FORMS = Set.of("sawcleaver_false", "sawcleaver_true", "sawspear_false", "sawspear_true", "boomhammer_false", "boomhammer_true");
 private final long started = System.nanoTime();
 private final boolean privateAssetsRequired = Boolean.getBoolean("bbrp.privateAssets.required");
 private boolean finished;

 @Override public void onInitializeClient() {
  ClientTickEvents.END_CLIENT_TICK.register(this::tick);
  BloodborneRp.LOGGER.info("BBRP_SMOKE start privateAssetsRequired={}", privateAssetsRequired);
 }

 private void tick(MinecraftClient client) {
  if (finished) return;
  try {
   Map<Identifier, ?> bakedModels = GeckoLibCache.getBakedModels();
   if (bakedModels.size() < MIN_BAKED_MODELS || !bakedModels.containsKey(FALLBACK_MODEL)) {
    if (System.nanoTime() - started > TIMEOUT_NANOS) fail(client, "reload_timeout models=" + bakedModels.size());
    return;
   }
   validate(client.getResourceManager(), bakedModels, GeckoLibCache.getBakedAnimations());
   finish(client, "ok models=" + bakedModels.size() + " animations=" + GeckoLibCache.getBakedAnimations().size()
    + " catalog=" + AssetCatalog.all().size() + " weapons=" + WEAPON_FORMS.size());
  } catch (RuntimeException exception) {
   fail(client, "validation_error " + exception.getMessage(), exception);
  }
 }

 private void validate(ResourceManager resources, Map<Identifier, ?> bakedModels, Map<Identifier, ?> bakedAnimations) {
  List<String> errors = new ArrayList<>();
  int fallbackModels = 0;
  int fallbackTextures = 0;
  for (AssetSpec spec : AssetCatalog.all().values()) {
   boolean weapon = WEAPON_FORMS.contains(spec.id());
   Identifier model = id(spec.model());
   Identifier texture = id(spec.texture());
   Identifier animation = id(spec.animation());
   boolean modelPresent = resources.getResource(model).isPresent();
   boolean texturePresent = resources.getResource(texture).isPresent();
   if (!modelPresent) fallbackModels++; else if (!bakedModels.containsKey(model)) errors.add(spec.id() + ":model_not_baked=" + model);
   if (!texturePresent) fallbackTextures++;
   if ((privateAssetsRequired || weapon) && !modelPresent) errors.add(spec.id() + ":model_missing=" + model);
   if ((privateAssetsRequired || weapon) && !texturePresent) errors.add(spec.id() + ":texture_missing=" + texture);
   if (!animation.equals(FALLBACK_ANIMATION)) {
    if (!resources.getResource(animation).isPresent()) errors.add(spec.id() + ":animation_missing=" + animation);
    else if (!bakedAnimations.containsKey(animation)) errors.add(spec.id() + ":animation_not_baked=" + animation);
    else {
     var baked=GeckoLibCache.getBakedAnimations().get(animation);
     for(var clip:spec.clips().values())if(baked.getAnimation(clip.name())==null)errors.add(spec.id()+":clip_not_baked="+clip.name());
    }
   }
  }
  if (!bakedModels.containsKey(FALLBACK_MODEL)) errors.add("fallback_model_not_baked");
  BloodborneRp.LOGGER.info("BBRP_SMOKE resources fallbackModels={} fallbackTextures={} strictPrivate={}", fallbackModels, fallbackTextures, privateAssetsRequired);
  if (!errors.isEmpty()) throw new IllegalStateException(String.join(";", errors));
 }

 private static Identifier id(String path) { return path.indexOf(':') >= 0 ? new Identifier(path) : BloodborneRp.id(path); }
 private void finish(MinecraftClient client, String result) {
  finished = true;
  BloodborneRp.LOGGER.info("BBRP_SMOKE complete {}", result);
  client.scheduleStop();
 }
 private void fail(MinecraftClient client, String result) { fail(client, result, null); }
 private void fail(MinecraftClient client, String result, RuntimeException exception) {
  finished = true;
  BloodborneRp.LOGGER.error("BBRP_SMOKE failed {}", result, exception);
  client.scheduleStop();
  throw exception == null ? new IllegalStateException(result) : exception;
 }
}
