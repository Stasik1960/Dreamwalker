package dev.dreamwalker.bloodbornedw.client;

import dev.dreamwalker.bloodbornedw.block.DwBlocks;
import dev.dreamwalker.bloodbornedw.block.Visual;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Verifies numeric carrier states have both baked visual variants after client resource reload. */
public final class DwClientSmoke implements ClientModInitializer {
    public static volatile boolean passed;
    private static final long TIMEOUT_NANOS = 120_000_000_000L;
    private final long started = System.nanoTime();
    private boolean done;
    @Override public void onInitializeClient() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }
    private void tick(MinecraftClient client) {
        if (done) return;
        if (client.getOverlay() != null) return;
        if (BloodborneDwClient.wrappedBlockstateModels() < DwBlocks.entries().size() * 2) {
            if (System.nanoTime() - started < TIMEOUT_NANOS) return;
            fail(client, "wrapped=" + BloodborneDwClient.wrappedBlockstateModels() + " catalog=" + DwBlocks.entries().size()); return;
        }
        int errors = 0;
        for (DwBlocks.Entry entry : DwBlocks.entries()) for (Visual visual : Visual.values()) {
            var state = entry.block().getDefaultState().with(DwBlocks.VISUAL, visual);
            if (!(client.getBlockRenderManager().getModel(state) instanceof VisualBakedModel)) errors++;
        }
        if (errors != 0) { fail(client, "numeric_state_models_missing=" + errors); return; }
        if (Boolean.getBoolean("dw.smoke.alt")) {
            try (var input = DwClientSmoke.class.getResourceAsStream("/assets/bloodborne_dw/catalog.json")) {
                var catalog = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                int checked = 0;
                for (var row : catalog.getAsJsonArray("blocks")) {
                    var block = row.getAsJsonObject(); String layer = block.get("render_layer").getAsString();
                    if (!layer.equals("solid")) {
                        var entry = DwBlocks.byId(block.get("id").getAsString());
                        var actual = net.minecraft.client.render.RenderLayers.getBlockLayer(entry.block().getDefaultState());
                        var expected = layer.equals("translucent") ? net.minecraft.client.render.RenderLayer.getTranslucent() : net.minecraft.client.render.RenderLayer.getCutout();
                        if (actual != expected) throw new IllegalStateException("BASE alpha lost for " + entry.id());
                        checked++;
                    }
                }
                dev.dreamwalker.bloodbornerp.BloodborneRp.LOGGER.info("DW_ALT_LAYER_SMOKE preserved_base_alpha={}", checked);
            } catch (Exception exception) { fail(client, exception.toString()); return; }
        }
        done = true; passed = true;
        dev.dreamwalker.bloodbornerp.BloodborneRp.LOGGER.info("DW_SMOKE complete catalog={} wrapped={}", DwBlocks.entries().size(), BloodborneDwClient.wrappedBlockstateModels());
        if (System.getProperty("dw.smoke.world") == null && dev.dreamwalker.bloodbornerp.clienttest.RpClientSmoke.passed) client.scheduleStop();
    }
    private void fail(MinecraftClient client, String message) { done = true; client.scheduleStop(); throw new IllegalStateException("DW client smoke " + message); }
}
