package dev.nightshift.skate.client;

import dev.nightshift.skate.NightShiftSkate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Renders the ramps as continuous wedges in the world and inventory. */
public final class SkateRampModels implements ClientModInitializer {
    private static final double TOE_HEIGHT = 1.0 / 32.0;
    private static final SpriteIdentifier TOP = sprite("minecraft:block/polished_blackstone");
    private static final SpriteIdentifier SIDE = sprite("minecraft:block/deepslate_bricks");

    @Override
    public void onInitializeClient() {
        ModelLoadingPlugin.register(context -> context.modifyModelAfterBake().register((model, bakeContext) -> {
            if (model == null || !(bakeContext.id() instanceof ModelIdentifier id)
                    || !NightShiftSkate.ID.equals(id.getNamespace())) {
                return model;
            }
            double rise = switch (id.getPath()) {
                case "low_ramp" -> 0.5;
                case "steep_ramp" -> 1.0;
                default -> -1.0;
            };
            if (rise < 0.0) {
                return model;
            }
            String variant = id.getVariant();
            Direction facing = "inventory".equals(variant) ? Direction.NORTH : null;
            if (variant.startsWith("facing=")) {
                facing = Direction.byName(variant.substring("facing=".length()));
            }
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (facing == null || renderer == null) {
                return model;
            }
            return new RampModel(model, rise, facing,
                    bakeContext.textureGetter().apply(TOP), bakeContext.textureGetter().apply(SIDE), renderer);
        }));
    }

    private static SpriteIdentifier sprite(String id) {
        return new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, new Identifier(id));
    }

    private static final class RampModel implements BakedModel {
        private final BakedModel fallback;
        private final List<BakedQuad> quads;
        private final Sprite particle;

        private RampModel(BakedModel fallback, double rise, Direction facing,
                          Sprite top, Sprite side, Renderer renderer) {
            this.fallback = fallback;
            this.particle = side;
            RenderMaterial material = renderer.materialFinder().find();
            List<BakedQuad> baked = new ArrayList<>(9);
            double toeStartZ = 1.0 - TOE_HEIGHT / rise;

            baked.add(quad(renderer, material, top, facing, Direction.UP,
                    vertex(0, rise, 0, 0, 0), vertex(0, TOE_HEIGHT, toeStartZ, 0, toeStartZ),
                    vertex(1, TOE_HEIGHT, toeStartZ, 1, toeStartZ), vertex(1, rise, 0, 1, 0),
                    normal(0, 1, rise)));
            baked.add(quad(renderer, material, top, facing, Direction.UP,
                    vertex(0, TOE_HEIGHT, toeStartZ, 0, toeStartZ), vertex(0, TOE_HEIGHT, 1, 0, 1),
                    vertex(1, TOE_HEIGHT, 1, 1, 1), vertex(1, TOE_HEIGHT, toeStartZ, 1, toeStartZ),
                    normal(0, 1, 0)));
            baked.add(quad(renderer, material, side, facing, Direction.DOWN,
                    vertex(0, 0, 0, 0, 0), vertex(1, 0, 0, 1, 0),
                    vertex(1, 0, 1, 1, 1), vertex(0, 0, 1, 0, 1),
                    normal(0, -1, 0)));
            baked.add(quad(renderer, material, side, facing, Direction.NORTH,
                    vertex(0, 0, 0, 0, 1), vertex(0, rise, 0, 0, 1 - rise),
                    vertex(1, rise, 0, 1, 1 - rise), vertex(1, 0, 0, 1, 1),
                    normal(0, 0, -1)));
            baked.add(quad(renderer, material, side, facing, Direction.SOUTH,
                    vertex(0, 0, 1, 0, 1), vertex(1, 0, 1, 1, 1),
                    vertex(1, TOE_HEIGHT, 1, 1, 1 - TOE_HEIGHT), vertex(0, TOE_HEIGHT, 1, 0, 1 - TOE_HEIGHT),
                    normal(0, 0, 1)));
            baked.add(quad(renderer, material, side, facing, Direction.WEST,
                    vertex(0, 0, 0, 0, 1), vertex(0, 0, toeStartZ, toeStartZ, 1),
                    vertex(0, TOE_HEIGHT, toeStartZ, toeStartZ, 1 - TOE_HEIGHT), vertex(0, rise, 0, 0, 1 - rise),
                    normal(-1, 0, 0)));
            baked.add(quad(renderer, material, side, facing, Direction.WEST,
                    vertex(0, 0, toeStartZ, toeStartZ, 1), vertex(0, 0, 1, 1, 1),
                    vertex(0, TOE_HEIGHT, 1, 1, 1 - TOE_HEIGHT), vertex(0, TOE_HEIGHT, toeStartZ, toeStartZ, 1 - TOE_HEIGHT),
                    normal(-1, 0, 0)));
            baked.add(quad(renderer, material, side, facing, Direction.EAST,
                    vertex(1, 0, 0, 0, 1), vertex(1, rise, 0, 0, 1 - rise),
                    vertex(1, TOE_HEIGHT, toeStartZ, toeStartZ, 1 - TOE_HEIGHT), vertex(1, 0, toeStartZ, toeStartZ, 1),
                    normal(1, 0, 0)));
            baked.add(quad(renderer, material, side, facing, Direction.EAST,
                    vertex(1, 0, toeStartZ, toeStartZ, 1), vertex(1, TOE_HEIGHT, toeStartZ, toeStartZ, 1 - TOE_HEIGHT),
                    vertex(1, TOE_HEIGHT, 1, 1, 1 - TOE_HEIGHT), vertex(1, 0, 1, 1, 1),
                    normal(1, 0, 0)));
            quads = List.copyOf(baked);
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction face, Random random) {
            return face == null ? quads : List.of();
        }

        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean hasDepth() { return fallback.hasDepth(); }
        @Override public boolean isSideLit() { return fallback.isSideLit(); }
        @Override public boolean isBuiltin() { return false; }
        @Override public Sprite getParticleSprite() { return particle; }
        @Override public ModelTransformation getTransformation() { return fallback.getTransformation(); }
        @Override public ModelOverrideList getOverrides() { return fallback.getOverrides(); }
    }

    private static BakedQuad quad(Renderer renderer, RenderMaterial material, Sprite sprite,
                                  Direction facing, Direction baseFace,
                                  Vertex a, Vertex b, Vertex c, Vertex d, Normal baseNormal) {
        QuadEmitter emitter = renderer.meshBuilder().getEmitter();
        Vertex[] vertices = {a, b, c, d};
        Normal normal = rotate(baseNormal, facing);
        for (int index = 0; index < 4; index++) {
            Vertex vertex = rotate(vertices[index], facing);
            emitter.pos(index, vertex.x, vertex.y, vertex.z)
                    .uv(index, sprite.getFrameU(vertex.u * 16.0), sprite.getFrameV(vertex.v * 16.0))
                    .color(index, 0xFFFFFFFF)
                    .normal(index, normal.x, normal.y, normal.z);
        }
        emitter.material(material).nominalFace(rotate(baseFace, facing)).cullFace(null).colorIndex(-1);
        return emitter.toBakedQuad(sprite);
    }

    private static Vertex vertex(double x, double y, double z, double u, double v) {
        return new Vertex((float) x, (float) y, (float) z, (float) u, (float) v);
    }

    private static Normal normal(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new Normal((float) (x / length), (float) (y / length), (float) (z / length));
    }

    private static Vertex rotate(Vertex vertex, Direction facing) {
        return switch (facing) {
            case NORTH -> vertex;
            case EAST -> new Vertex(1 - vertex.z, vertex.y, vertex.x, vertex.u, vertex.v);
            case SOUTH -> new Vertex(1 - vertex.x, vertex.y, 1 - vertex.z, vertex.u, vertex.v);
            case WEST -> new Vertex(vertex.z, vertex.y, 1 - vertex.x, vertex.u, vertex.v);
            default -> vertex;
        };
    }

    private static Normal rotate(Normal normal, Direction facing) {
        return switch (facing) {
            case NORTH -> normal;
            case EAST -> new Normal(-normal.z, normal.y, normal.x);
            case SOUTH -> new Normal(-normal.x, normal.y, -normal.z);
            case WEST -> new Normal(normal.z, normal.y, -normal.x);
            default -> normal;
        };
    }

    private static Direction rotate(Direction direction, Direction facing) {
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        Direction result = direction;
        for (int turn = 0; turn < turns && result.getAxis().isHorizontal(); turn++) {
            result = result.rotateYClockwise();
        }
        return result;
    }

    private record Vertex(float x, float y, float z, float u, float v) {}
    private record Normal(float x, float y, float z) {}
}
