package dev.dreamwalker.bloodbornedw.composite;

import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.ColorResolver;

/** The v15 grass palette belongs only to these imported artworks, never to vanilla blocks. */
final class RemainingSourceTint {
    private static final Set<String> GRASS_ART = Set.of("source_grass_block", "source_bricks_grass_block_0", "source_potted_fern");
    private static final Identifier PALETTE = new Identifier("bloodborne_dw", "textures/remaining/colormap/grass.png");
    private static final Map<Biome, double[]> CLIMATES = new IdentityHashMap<>();
    private static int[] grass;
    private static final ColorResolver GRASS = (biome, x, z) -> {
        int base = biome.getEffects().getGrassColor().orElseGet(() -> {
            double[] climate = CLIMATES.computeIfAbsent(biome, RemainingSourceTint::climate);
            return sample(climate[0], climate[1]);
        });
        return biome.getEffects().getGrassColorModifier().getModifiedGrassColor(x, z, base);
    };

    static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return new Identifier("bloodborne_dw", "remaining_source_grass_palette"); }
            @Override public void reload(ResourceManager manager) { load(manager); }
        });
    }

    static int color(CompositeRootBlock block, MinecraftClient client, BlockPos root) {
        if (!GRASS_ART.contains(block.spec.id.getPath())) return 0xFFFFFF;
        if (grass == null) load(client.getResourceManager());
        // All tinted faces of these exact source models use index 0. The baked renderer
        // applies RGB only to tinted quads; their untinted pot/dirt/side faces stay white.
        return root != null && client.world != null ? client.world.calculateColor(root, GRASS) : sample(.5, 1);
    }

    private static void load(ResourceManager manager) {
        try (var input = manager.getResourceOrThrow(PALETTE).getInputStream()) {
            BufferedImage image = ImageIO.read(input);
            if (image == null || image.getWidth() != 256 || image.getHeight() != 256)
                throw new IOException("The authored grass palette must be 256 by 256");
            int[] values = image.getRGB(0, 0, 256, 256, null, 0, 256);
            for (int i = 0; i < values.length; i++) values[i] &= 0xFFFFFF;
            grass = values;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load the imported v15 grass palette", failure);
        }
    }

    private static double[] climate(Biome biome) {
        // 1.20.1 exposes temperature but not downfall. Its network codec retains the
        // exact weather fields and requires no registry context; decode once per biome.
        var encoded = Biome.NETWORK_CODEC.encodeStart(JsonOps.INSTANCE, biome).result()
                .orElseThrow(() -> new IllegalStateException("Cannot read biome weather for source grass tint"));
        float downfall = encoded.getAsJsonObject().get("downfall").getAsFloat();
        return new double[] {MathHelper.clamp(biome.getTemperature(), 0, 1), MathHelper.clamp(downfall, 0, 1)};
    }

    private static int sample(double temperature, double humidity) {
        int x = (int) ((1 - temperature) * 255);
        int y = (int) ((1 - humidity * temperature) * 255);
        return grass[(y << 8) | x];
    }

    private RemainingSourceTint() {}
}
