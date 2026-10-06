package dev.dreamwalker.bloodbornedw.client;

import dev.dreamwalker.bloodbornedw.block.Visual;
import dev.dreamwalker.bloodbornedw.visual.VisualService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.ModelIdentifier;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class BloodborneDwClient implements ClientModInitializer {
    private static final Map<String, String> BASE_LAYERS = new java.util.HashMap<>();
    private static volatile Map<String, VisualService.RuleSet> snapshots = Map.of();
    private static final AtomicInteger WRAPPED_BLOCKSTATE_MODELS = new AtomicInteger();
    @Override public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, bake) -> {
            if (!isDwBlockState(bake.id()) || model instanceof VisualBakedModel) return model;
            WRAPPED_BLOCKSTATE_MODELS.incrementAndGet();
            return new VisualBakedModel(model);
        }));
        configureRenderLayers();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES).registerReloadListener(
            new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                public Identifier getFabricId() { return new Identifier("bloodborne_dw", "alt_render_layers"); }
                public void reload(net.minecraft.resource.ResourceManager manager) {
                    configureRenderLayers();
                    Map<String, String> alternatives = new java.util.HashMap<>();
                    for (var resource : manager.getAllResources(new Identifier("bloodborne_dw", "alt_render_layers.json"))) {
                        try (var reader = resource.getReader()) {
                            for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                                var block = dev.dreamwalker.bloodbornedw.block.DwBlocks.byId(entry.getKey());
                                if (block == null) throw new IllegalArgumentException("Unknown ALT layer block " + entry.getKey());
                                String layer = entry.getValue().getAsString();
                                if (!java.util.Set.of("solid", "cutout", "translucent").contains(layer)) throw new IllegalArgumentException("Unknown render layer " + layer);
                                alternatives.put(entry.getKey(), layer);
                            }
                        } catch (java.io.IOException exception) { throw new IllegalStateException("Could not read ALT render layers", exception); }
                    }
                    alternatives.forEach((id, layer) -> BlockRenderLayerMap.INSTANCE.putBlock(dev.dreamwalker.bloodbornedw.block.DwBlocks.byId(id).block(), renderLayer(unionLayer(BASE_LAYERS.getOrDefault(id, "solid"), layer))));
                }
            });
        // Keep the original carrier's tint rules for models with tintindex.
        for (var entry : dev.dreamwalker.bloodbornedw.block.DwBlocks.entries()) {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                (state, view, pos, tint) -> MinecraftClient.getInstance().getBlockColors().getColor(
                    dev.dreamwalker.bloodbornedw.block.DwBlocks.sourceState(state), view, pos, tint), entry.block());
        }
        ClientPlayNetworking.registerGlobalReceiver(VisualService.SNAPSHOT_PACKET, (client, handler, buffer, response) -> {
            String dimension = buffer.readString(128); NbtCompound rules = buffer.readNbt();
            if (rules == null || rules.getList("rules", 10).size() > 4096) return;
            VisualService.RuleSet compiled = VisualService.compile(rules);
            client.execute(() -> { java.util.HashMap<String, VisualService.RuleSet> next = new java.util.HashMap<>(snapshots); next.put(dimension, compiled); snapshots = Map.copyOf(next); if (client.world != null && dimension.equals(client.world.getRegistryKey().getValue().toString())) client.worldRenderer.reload(); });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> snapshots = Map.of());
    }
    public static Visual effective(BlockPos pos, String id, Visual stored) { if (MinecraftClient.getInstance().world == null) return stored; VisualService.RuleSet rules=snapshots.get(MinecraftClient.getInstance().world.getRegistryKey().getValue().toString()); return rules == null ? stored : rules.effective(pos,id,stored); }
    static String id(net.minecraft.block.BlockState state) { return dev.dreamwalker.bloodbornedw.visual.VisualService.id(state); }
    /** Exposed for the client smoke: this must be nonzero after numeric carrier states bake. */
    public static int wrappedBlockstateModels() { return WRAPPED_BLOCKSTATE_MODELS.get(); }
    private static boolean isDwBlockState(Identifier id) { return id instanceof ModelIdentifier model && "bloodborne_dw".equals(model.getNamespace()) && model.getPath().matches("\\d{5}") && model.getVariant().contains("visual="); }
    private static void configureRenderLayers() {
        try (var input = BloodborneDwClient.class.getResourceAsStream("/assets/bloodborne_dw/catalog.json")) {
            if (input == null) return;
            var blocks = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("blocks");
            for (var value : blocks) { var block = value.getAsJsonObject(); String id = block.get("id").getAsString(); var entry = dev.dreamwalker.bloodbornedw.block.DwBlocks.byId(id); if (entry == null) continue; String layer = block.has("render_layer") ? block.get("render_layer").getAsString() : "solid"; BASE_LAYERS.put(id, layer); BlockRenderLayerMap.INSTANCE.putBlock(entry.block(), renderLayer(layer)); }
        } catch (Exception exception) { throw new IllegalStateException("Invalid DW client catalog render layers", exception); }
    }
    static String unionLayer(String base, String alt) {
        if ("translucent".equals(base) || "translucent".equals(alt)) return "translucent";
        return "cutout".equals(base) || "cutout".equals(alt) ? "cutout" : "solid";
    }
    private static RenderLayer renderLayer(String layer) { return "translucent".equals(layer) ? RenderLayer.getTranslucent() : "cutout".equals(layer) ? RenderLayer.getCutout() : RenderLayer.getSolid(); }
}
