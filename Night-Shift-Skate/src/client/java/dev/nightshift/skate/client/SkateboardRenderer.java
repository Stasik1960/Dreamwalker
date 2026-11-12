package dev.nightshift.skate.client;

import dev.nightshift.skate.BoardSetup;
import dev.nightshift.skate.BoardStyle;
import dev.nightshift.skate.SkatePart;
import dev.nightshift.skate.SkateTrick;
import dev.nightshift.skate.SkateboardEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Compact render mesh derived from the Night Shift Blender source.
 *
 * <p>The Blender scene contains 80 editable parts. Re-emitting that mesh for every entity would be excessive,
 * so this renderer preserves the measured 8.25 inch silhouette, asymmetrical double kick, trucks, 54 mm
 * wheels, bearings and the packed underside artwork with a low-poly runtime mesh.</p>
 */
public final class SkateboardRenderer extends EntityRenderer<SkateboardEntity> {
    private static final Identifier TEXTURE = new Identifier("night_shift_skate", "textures/entity/night_shift.png");
    private static final Identifier WHITE_TEXTURE = new Identifier("night_shift_skate", "textures/entity/board_white.png");
    private static final float LENGTH = 1.02F;
    private static final float HALF_LENGTH = LENGTH / 2.0F;
    private static final float HALF_WIDTH = 0.132F;
    private static final float DECK_BOTTOM = 0.126F;
    private static final float DECK_THICKNESS = 0.022F;
    private static final int DECK_STATIONS = 24;
    private static final int WHEEL_SEGMENTS = 12;
    private static final float[] DECK_Z = new float[DECK_STATIONS + 1];
    private static final float[] DECK_WIDTH = new float[DECK_STATIONS + 1];
    private static final float[] DECK_BOTTOM_Y = new float[DECK_STATIONS + 1];
    private static final float[][] CIRCLE_10 = circle(10);
    private static final float[][] CIRCLE_12 = circle(12);

    // UV centres for the colour chips in the lower half of the atlas.
    private static final Uv GRIP = new Uv(0.0625F, 0.625F);
    private static final Uv MAPLE = new Uv(0.1875F, 0.625F);
    private static final Uv CAST = new Uv(0.3125F, 0.625F);
    private static final Uv STEEL = new Uv(0.4375F, 0.625F);
    private static final Uv URETHANE = new Uv(0.5625F, 0.625F);
    private static final Uv BUSHING = new Uv(0.6875F, 0.625F);
    private static final Uv BEARING = new Uv(0.8125F, 0.625F);
    private static final Uv BLACK = new Uv(0.9375F, 0.625F);

    static {
        for (int i = 0; i <= DECK_STATIONS; i++) {
            DECK_Z[i] = -HALF_LENGTH + LENGTH * i / DECK_STATIONS;
            float normalized = Math.abs(DECK_Z[i]) / HALF_LENGTH;
            float tip = MathHelper.clamp((normalized - 0.76F) / 0.24F, 0.0F, 1.0F);
            DECK_WIDTH[i] = HALF_WIDTH * (float) Math.sqrt(Math.max(0.0025F, 1.0F - tip * tip * 0.9975F));
            DECK_BOTTOM_Y[i] = deckBottom(DECK_Z[i]);
        }
    }

    public SkateboardRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 0.55F;
        this.shadowOpacity = 0.55F;
    }

    @Override
    public void render(SkateboardEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - SkatePose.travelYaw(entity, tickDelta)));
        BoardSetup setup = entity.getSetup();
        applyTrickTransform(entity, setup, tickDelta, matrices);
        renderBoard(setup, matrices, vertexConsumers, light);
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    static void renderBoard(BoardSetup setup, MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers, int light) {
        BoardStyle preset = setup.matchingPreset();
        if (preset != null) {
            renderPreset(preset, matrices, vertexConsumers, light);
            return;
        }

        matrices.push();
        matrices.translate(0, setup.deckBase(), 0);
        renderPart(setup.deck(), matrices, vertexConsumers, light);
        for (int end = -1; end <= 1; end += 2) {
            matrices.push();
            matrices.translate(0, 0, end * BoardSetup.WHEELBASE_HALF);
            if (end == -1) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            renderPart(setup.trucks(), matrices, vertexConsumers, light);
            for (int side = -1; side <= 1; side += 2) {
                matrices.push();
                matrices.translate(side * setup.trucks().halfTrack(), -setup.trucks().axleDrop(), 0);
                if (side == -1) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                renderPart(setup.wheels(), matrices, vertexConsumers, light);
                matrices.pop();
            }
            matrices.pop();
        }
        matrices.pop();
    }

    static void renderPart(SkatePart part, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        matrices.push();
        if (part.id().equals("night_shift")) {
            VertexConsumer consumer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
            switch (part.type()) {
                case DECK -> {
                    matrices.translate(0, -DECK_BOTTOM, 0);
                    renderDeck(matrices, consumer, light);
                    renderBolts(matrices, consumer, light, (float) -BoardSetup.WHEELBASE_HALF);
                    renderBolts(matrices, consumer, light, (float) BoardSetup.WHEELBASE_HALF);
                }
                case TRUCKS -> {
                    matrices.translate(0, -DECK_BOTTOM, 0);
                    renderTruckHardware(matrices, consumer, light, 0);
                }
                case WHEELS -> wheel(matrices, consumer, light, 0, 0, 0);
            }
        } else {
            BoardMesh mesh = BoardMesh.Library.get(part);
            if (part instanceof SkatePart.Trucks trucks) {
                matrices.scale(1, (float) (trucks.axleDrop() / 0.065), 1);
            } else if (part instanceof SkatePart.Wheels wheels) {
                float scale = (float) (wheels.radius() / mesh.groundRadius());
                matrices.scale(1, scale, scale);
            }
            VertexConsumer consumer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE));
            mesh.render(matrices, consumer, light);
        }
        matrices.pop();
    }

    private static void renderPreset(BoardStyle style, MatrixStack matrices,
                            VertexConsumerProvider vertexConsumers, int light) {
        if (style != BoardStyle.NIGHT_SHIFT) {
            VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE));
            BoardMesh.Library.get(style).render(matrices, consumer, light);
            return;
        }
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        renderDeck(matrices, consumer, light);
        renderTruck(matrices, consumer, light, -0.285F);
        renderTruck(matrices, consumer, light, 0.285F);
    }

    private static void applyTrickTransform(SkateboardEntity entity, BoardSetup setup,
                                            float tickDelta, MatrixStack matrices) {
        if (entity.isGrinding()) {
            // Roll around the travel axis before turning the deck across the rail. Positive balance is
            // steer-right, so the negative roll lowers travel-right. The last translation keeps the
            // original deck-bottom centre on the rail through both rotations.
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-entity.getGrindLean() * 12.0F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            matrices.translate(0, -setup.deckBase(), 0);
        } else {
            matrices.translate(0, -entity.getRampDrop(), 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.getRampPitch()));
            SkatePose.wallTilt(matrices, entity.getWallRideLean(), 0.0f);
            float manual = entity.getManualPitch();
            if (manual != 0.0f) {
                float axle = Math.copySign(SkatePose.manualAxle(setup), manual);
                matrices.translate(0, 0, axle);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(manual));
                matrices.translate(0, 0, -axle);
            } else {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-entity.getSteeringLean() * 4.0f));
            }
            float ollie = SkatePose.ollieProgress(entity, tickDelta);
            if (ollie < 1.0f) {
                float pitch = MathHelper.sin(ollie * MathHelper.TAU) * (1.0f - ollie) * 30.0f;
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            }
            animateAirTrick(entity, setup, tickDelta, matrices);
        }
        if (entity.isDeckReversed()) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        }
    }

    private static void animateAirTrick(SkateboardEntity board, BoardSetup setup, float tickDelta, MatrixStack matrices) {
        SkateTrick trick = board.getTrick();
        if (trick == SkateTrick.NONE) return;

        float progress = SkatePose.trickProgress(board, tickDelta);
        float rotation = SkatePose.smoothStep(progress);
        float grab = trick.isGrab() ? SkatePose.grabAmount(progress) : 0.0f;
        double pivot = setup.riderHeight() * 0.70;
        matrices.translate(0, pivot + grab * 0.17f, 0);
        switch (trick) {
            case POP_SHOVE_IT -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f * rotation));
            case KICKFLIP -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(360.0f * rotation));
            case HEELFLIP -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-360.0f * rotation));
            case SHOVE_IT_360 -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(360.0f * rotation));
            case VARIAL_KICKFLIP, FLIP_360 -> {
                float spin = trick == SkateTrick.FLIP_360 ? 360.0f : 180.0f;
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin * rotation));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(360.0f * rotation));
            }
            case INDY -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(26.0f * grab));
            case MELON -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30.0f * grab));
            case NOSEGRAB -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(27.0f * grab));
            case TAILGRAB -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30.0f * grab));
            default -> {}
        }
        matrices.translate(0, -pivot, 0);
    }

    private static float deckBottom(float z) {
        float kickStart = z > 0.0F ? 0.55F : 0.58F;
        float kickHeight = z > 0.0F ? 0.058F : 0.047F;
        float ramp = Math.max(0.0F, (Math.abs(z) / HALF_LENGTH - kickStart) / (1.0F - kickStart));
        return DECK_BOTTOM + kickHeight * (float) Math.pow(ramp, 1.62);
    }

    private static void renderDeck(MatrixStack matrices, VertexConsumer consumer, int light) {
        for (int i = 0; i < DECK_STATIONS; i++) {
            float u0 = (0.5F + 255 * i / (float) DECK_STATIONS) / 256;
            float u1 = (0.5F + 255 * (i + 1) / (float) DECK_STATIONS) / 256;
            float y0 = DECK_BOTTOM_Y[i];
            float y1 = DECK_BOTTOM_Y[i + 1];
            float v0 = 31.5F * DECK_WIDTH[i] / HALF_WIDTH / 256;
            float v1 = 31.5F * DECK_WIDTH[i + 1] / HALF_WIDTH / 256;

            // Underside uses the original Blender artwork stored in the upper atlas band.
            quad(matrices, consumer, light,
                    -DECK_WIDTH[i], y0, DECK_Z[i], u0, 0.125F - v0,
                    -DECK_WIDTH[i + 1], y1, DECK_Z[i + 1], u1, 0.125F - v1,
                    DECK_WIDTH[i + 1], y1, DECK_Z[i + 1], u1, 0.125F + v1,
                    DECK_WIDTH[i], y0, DECK_Z[i], u0, 0.125F + v0,
                    0.0F, -1.0F, 0.0F);

            solidQuad(matrices, consumer, light, GRIP,
                    DECK_WIDTH[i], y0 + DECK_THICKNESS, DECK_Z[i],
                    DECK_WIDTH[i + 1], y1 + DECK_THICKNESS, DECK_Z[i + 1],
                    -DECK_WIDTH[i + 1], y1 + DECK_THICKNESS, DECK_Z[i + 1],
                    -DECK_WIDTH[i], y0 + DECK_THICKNESS, DECK_Z[i],
                    0.0F, 1.0F, 0.0F);
            solidQuad(matrices, consumer, light, MAPLE,
                    -DECK_WIDTH[i], y0, DECK_Z[i],
                    -DECK_WIDTH[i], y0 + DECK_THICKNESS, DECK_Z[i],
                    -DECK_WIDTH[i + 1], y1 + DECK_THICKNESS, DECK_Z[i + 1],
                    -DECK_WIDTH[i + 1], y1, DECK_Z[i + 1],
                    -1.0F, 0.0F, 0.0F);
            solidQuad(matrices, consumer, light, MAPLE,
                    DECK_WIDTH[i + 1], y1, DECK_Z[i + 1],
                    DECK_WIDTH[i + 1], y1 + DECK_THICKNESS, DECK_Z[i + 1],
                    DECK_WIDTH[i], y0 + DECK_THICKNESS, DECK_Z[i],
                    DECK_WIDTH[i], y0, DECK_Z[i],
                    1.0F, 0.0F, 0.0F);
        }

        capDeck(matrices, consumer, light, DECK_Z[0], DECK_WIDTH[0], DECK_BOTTOM_Y[0], -1.0F);
        capDeck(matrices, consumer, light, DECK_Z[DECK_STATIONS], DECK_WIDTH[DECK_STATIONS],
                DECK_BOTTOM_Y[DECK_STATIONS], 1.0F);
    }

    private static void capDeck(MatrixStack matrices, VertexConsumer consumer, int light,
                                float z, float width, float bottom, float normalZ) {
        solidQuad(matrices, consumer, light, MAPLE,
                -width, bottom, z,
                width, bottom, z,
                width, bottom + DECK_THICKNESS, z,
                -width, bottom + DECK_THICKNESS, z,
                0.0F, 0.0F, normalZ);
    }

    private static void renderTruck(MatrixStack matrices, VertexConsumer consumer, int light, float z) {
        renderTruckHardware(matrices, consumer, light, z);
        wheel(matrices, consumer, light, -0.108F, 0.035F, z);
        wheel(matrices, consumer, light, 0.108F, 0.035F, z);
        renderBolts(matrices, consumer, light, z);
    }

    private static void renderTruckHardware(MatrixStack matrices, VertexConsumer consumer, int light, float z) {
        box(matrices, consumer, light, CAST, -0.043F, 0.110F, z - 0.041F, 0.086F, 0.017F, 0.082F);
        box(matrices, consumer, light, CAST, -0.087F, 0.028F, z - 0.020F, 0.174F, 0.026F, 0.040F);
        cylinderX(matrices, consumer, light, STEEL, 0.0F, 0.035F, z, 0.008F, 0.266F, 10);
        cylinderY(matrices, consumer, light, BUSHING, 0.0F, 0.082F, z, 0.021F, 0.060F, 10);

    }

    private static void renderBolts(MatrixStack matrices, VertexConsumer consumer, int light, float z) {
        for (int side = -1; side <= 1; side += 2) {
            float x = 0.027F * side;
            bolt(matrices, consumer, light, x, z - 0.031F);
            bolt(matrices, consumer, light, x, z + 0.031F);
        }
    }

    private static void bolt(MatrixStack matrices, VertexConsumer consumer, int light, float x, float z) {
        float y = deckBottom(z) + DECK_THICKNESS + 0.001F;
        float r = 0.005F;
        solidQuad(matrices, consumer, light, BEARING,
                x - r, y, z + r, x + r, y, z + r, x + r, y, z - r, x - r, y, z - r, 0, 1, 0);
    }

    private static void wheel(MatrixStack matrices, VertexConsumer consumer, int light, float x, float y, float z) {
        cylinderX(matrices, consumer, light, URETHANE, x, y, z, 0.034F, 0.041F, WHEEL_SEGMENTS);
        cylinderX(matrices, consumer, light, BEARING, x + Math.copySign(0.021F, x), y, z, 0.012F, 0.003F, 12);
        cylinderX(matrices, consumer, light, BLACK, x + Math.copySign(0.023F, x), y, z, 0.0045F, 0.004F, 10);
    }

    private static void box(MatrixStack matrices, VertexConsumer consumer, int light, Uv uv,
                            float x, float y, float z, float width, float height, float depth) {
        float x1 = x + width, y1 = y + height, z1 = z + depth;
        solidQuad(matrices, consumer, light, uv, x, y, z, x1, y, z, x1, y1, z, x, y1, z, 0, 0, -1);
        solidQuad(matrices, consumer, light, uv, x1, y, z1, x, y, z1, x, y1, z1, x1, y1, z1, 0, 0, 1);
        solidQuad(matrices, consumer, light, uv, x, y, z1, x, y, z, x, y1, z, x, y1, z1, -1, 0, 0);
        solidQuad(matrices, consumer, light, uv, x1, y, z, x1, y, z1, x1, y1, z1, x1, y1, z, 1, 0, 0);
        solidQuad(matrices, consumer, light, uv, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1, 0, 1, 0);
        solidQuad(matrices, consumer, light, uv, x, y, z1, x1, y, z1, x1, y, z, x, y, z, 0, -1, 0);
    }

    private static void cylinderX(MatrixStack matrices, VertexConsumer consumer, int light, Uv uv,
                                  float centerX, float centerY, float centerZ, float radius, float length, int segments) {
        float left = centerX - length / 2.0F;
        float right = centerX + length / 2.0F;
        float[][] circle = circleFor(segments);
        for (int i = 0; i < segments; i++) {
            float y0 = centerY + radius * circle[i][0];
            float z0 = centerZ + radius * circle[i][1];
            float y1 = centerY + radius * circle[i + 1][0];
            float z1 = centerZ + radius * circle[i + 1][1];
            solidQuad(matrices, consumer, light, uv, left, y0, z0, right, y0, z0, right, y1, z1, left, y1, z1,
                    0.0F, (circle[i][0] + circle[i + 1][0]) * 0.5F, (circle[i][1] + circle[i + 1][1]) * 0.5F);
            faceTriangle(matrices, consumer, light, uv, left, centerY, centerZ, left, y1, z1, left, y0, z0, -1, 0, 0);
            faceTriangle(matrices, consumer, light, uv, right, centerY, centerZ, right, y0, z0, right, y1, z1, 1, 0, 0);
        }
    }

    private static void cylinderY(MatrixStack matrices, VertexConsumer consumer, int light, Uv uv,
                                  float centerX, float centerY, float centerZ, float radius, float length, int segments) {
        float low = centerY - length / 2.0F;
        float high = centerY + length / 2.0F;
        float[][] circle = circleFor(segments);
        for (int i = 0; i < segments; i++) {
            float x0 = centerX + radius * circle[i][0];
            float z0 = centerZ + radius * circle[i][1];
            float x1 = centerX + radius * circle[i + 1][0];
            float z1 = centerZ + radius * circle[i + 1][1];
            solidQuad(matrices, consumer, light, uv, x0, low, z0, x1, low, z1, x1, high, z1, x0, high, z0,
                    (circle[i][0] + circle[i + 1][0]) * 0.5F, 0.0F, (circle[i][1] + circle[i + 1][1]) * 0.5F);
        }
    }

    private static float[][] circleFor(int segments) {
        return segments == 10 ? CIRCLE_10 : CIRCLE_12;
    }

    private static float[][] circle(int segments) {
        float[][] result = new float[segments + 1][2];
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            result[i][0] = (float) Math.cos(angle);
            result[i][1] = (float) Math.sin(angle);
        }
        return result;
    }

    private static void faceTriangle(MatrixStack matrices, VertexConsumer consumer, int light, Uv uv,
                                     float ax, float ay, float az, float bx, float by, float bz,
                                     float cx, float cy, float cz, float nx, float ny, float nz) {
        // Entity cutout uses quads. A duplicated third point produces a valid fan triangle without changing topology.
        solidQuad(matrices, consumer, light, uv, ax, ay, az, bx, by, bz, cx, cy, cz, cx, cy, cz, nx, ny, nz);
    }

    private static void solidQuad(MatrixStack matrices, VertexConsumer consumer, int light, Uv uv,
                                  float ax, float ay, float az, float bx, float by, float bz,
                                  float cx, float cy, float cz, float dx, float dy, float dz,
                                  float nx, float ny, float nz) {
        quad(matrices, consumer, light,
                ax, ay, az, uv.u, uv.v, bx, by, bz, uv.u, uv.v,
                cx, cy, cz, uv.u, uv.v, dx, dy, dz, uv.u, uv.v, nx, ny, nz);
    }

    private static void quad(MatrixStack matrices, VertexConsumer consumer, int light,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv,
                             float nx, float ny, float nz) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();
        vertex(consumer, position, normal, light, ax, ay, az, au, av, nx, ny, nz);
        vertex(consumer, position, normal, light, bx, by, bz, bu, bv, nx, ny, nz);
        vertex(consumer, position, normal, light, cx, cy, cz, cu, cv, nx, ny, nz);
        vertex(consumer, position, normal, light, dx, dy, dz, du, dv, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f position, Matrix3f normal, int light,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        consumer.vertex(position, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal, nx, ny, nz)
                .next();
    }

    @Override
    public Identifier getTexture(SkateboardEntity entity) {
        return TEXTURE;
    }

    private record Uv(float u, float v) {
    }
}
