package com.coraxberg.poolbilliards.client.render;

import com.coraxberg.poolbilliards.PoolBilliardsMod;
import com.coraxberg.poolbilliards.block.BilliardsTableBlock;
import com.coraxberg.poolbilliards.block.BilliardsTableBlockEntity;
import com.coraxberg.poolbilliards.game.PoolBall;
import com.coraxberg.poolbilliards.game.PoolGameState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.UUID;

public class BilliardsTableBlockEntityRenderer implements BlockEntityRenderer<BilliardsTableBlockEntity> {
    private static final Identifier WHITE_TEXTURE = PoolBilliardsMod.id("textures/entity/white.png");

    public BilliardsTableBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(BilliardsTableBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (be.getWorld() == null) return;
        Direction facing = be.getCachedState().contains(BilliardsTableBlock.FACING)
                ? be.getCachedState().get(BilliardsTableBlock.FACING)
                : Direction.NORTH;

        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE));

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationFor(facing)));

        // Игровое сукно занимает X [-1.25; 1.25], Z [-0.25; 1.25].
        // Эти координаты должны повторять oversized-модель блока.
        // Важно: для EAST/WEST вращение в BER идёт в противоположную сторону относительно blockstate y,
        // поэтому rotationFor ниже использует зеркальные значения для боковых направлений.
        for (PoolBall ball : be.getGame().balls) {
            if (ball.pocketed) continue;
            float localX = (float) (-1.25 + (ball.x / PoolGameState.TABLE_W) * 2.50);
            float localZ = (float) (-0.25 + (ball.y / PoolGameState.TABLE_H) * 1.50);
            float size = ball.id == 0 ? 0.075f : 0.082f;
            float y = 0.485f + size / 2.0f;
            drawCube(matrices, vertices, localX, y, localZ, size, ballColor(ball.id), light);
        }

        drawPlayerCues(be, tickDelta, matrices, vertices, light);
        matrices.pop();
    }

    private static void drawPlayerCues(BilliardsTableBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumer vertices, int light) {
        PoolGameState game = be.getGame();
        ArrayList<UUID> active = new ArrayList<>(game.activePlayers);
        UUID turn = game.getCurrentPlayerId();
        float time = (be.getWorld().getTime() + tickDelta) * 0.10f;

        for (int i = 0; i < active.size(); i++) {
            UUID id = active.get(i);
            if (id.equals(turn) && !game.gameOver) {
                PoolBall cueBall = game.getBall(0);
                if (cueBall != null && !cueBall.pocketed) {
                    float bx = (float) (-1.25 + (cueBall.x / PoolGameState.TABLE_W) * 2.50);
                    float bz = (float) (-0.25 + (cueBall.y / PoolGameState.TABLE_H) * 1.50);
                    float pull = (float) Math.sin(time * 2.2f) * 0.045f;
                    // Косметический замах: кий лежит за битком и слегка ходит вперёд-назад.
                    drawCue(matrices, vertices, bx - 0.33f - pull, 0.66f, bz + 0.18f, 62.0f, 0.86f, 0.026f, light);
                }
            } else {
                // Ожидающие игроки: кий стоит/лежит у края стола, показывая занятое место.
                float[][] spots = new float[][]{
                        {-1.36f, 0.65f, 0.10f, 16.0f},
                        {1.36f, 0.65f, 0.90f, -16.0f},
                        {0.00f, 0.65f, 1.38f, 90.0f}
                };
                float[] s = spots[Math.min(i, spots.length - 1)];
                drawCue(matrices, vertices, s[0], s[1], s[2], s[3], 0.95f, 0.024f, light);
            }
        }
    }

    private static float rotationFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            // Block model y-rotation and MatrixStack positive Y rotation have opposite visual handedness here.
            // Without this swap, balls on EAST/WEST tables appear offset/rotated relative to the felt.
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    private static int ballColor(int id) {
        return switch (id) {
            case 0 -> 0xFFF2F2F2;
            case 1, 9 -> 0xFFFFD84D;
            case 2, 10 -> 0xFF3E7BFF;
            case 3, 11 -> 0xFFFF4F4F;
            case 4, 12 -> 0xFF7B3EFF;
            case 5, 13 -> 0xFFFF9F2E;
            case 6, 14 -> 0xFF32B36B;
            case 7, 15 -> 0xFF8B3E2A;
            case 8 -> 0xFF050505;
            default -> 0xFFFFFFFF;
        };
    }

    private static void drawCue(MatrixStack matrices, VertexConsumer vertices, float cx, float cy, float cz, float angleDeg, float length, float thickness, int light) {
        matrices.push();
        matrices.translate(cx, cy, cz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angleDeg));
        drawCuboid(matrices, vertices, -thickness / 2.0f, -thickness / 2.0f, -length / 2.0f, thickness / 2.0f, thickness / 2.0f, length / 2.0f, 0xFFC48A47, light);
        drawCuboid(matrices, vertices, -thickness / 2.5f, -thickness / 2.5f, length / 2.0f - 0.06f, thickness / 2.5f, thickness / 2.5f, length / 2.0f, 0xFFEAD8AA, light);
        drawCuboid(matrices, vertices, -thickness / 2.2f, -thickness / 2.2f, -length / 2.0f, thickness / 2.2f, thickness / 2.2f, -length / 2.0f + 0.06f, 0xFF3A2518, light);
        matrices.pop();
    }

    private static void drawCube(MatrixStack matrices, VertexConsumer vertices, float cx, float cy, float cz, float size, int color, int light) {
        float h = size / 2.0f;
        drawCuboid(matrices, vertices, cx - h, cy - h, cz - h, cx + h, cy + h, cz + h, color, light);
    }

    private static void drawCuboid(MatrixStack matrices, VertexConsumer vertices, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int color, int light) {
        int a = (color >>> 24) & 255;
        int r = (color >>> 16) & 255;
        int g = (color >>> 8) & 255;
        int b = color & 255;

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        Matrix3f normal = entry.getNormalMatrix();

        quad(vertices, matrix, normal, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ, 0, 1, 0, r, g, b, a, light);
        quad(vertices, matrix, normal, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ, 0, -1, 0, r, g, b, a, light);
        quad(vertices, matrix, normal, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX, minY, minZ, 0, 0, -1, r, g, b, a, light);
        quad(vertices, matrix, normal, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ, 0, 0, 1, r, g, b, a, light);
        quad(vertices, matrix, normal, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ, -1, 0, 0, r, g, b, a, light);
        quad(vertices, matrix, normal, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, maxX, minY, minZ, 1, 0, 0, r, g, b, a, light);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, Matrix3f normal,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz,
                             int r, int g, int b, int a, int light) {
        vertex(vertices, matrix, normal, x1, y1, z1, 0, 0, nx, ny, nz, r, g, b, a, light);
        vertex(vertices, matrix, normal, x2, y2, z2, 1, 0, nx, ny, nz, r, g, b, a, light);
        vertex(vertices, matrix, normal, x3, y3, z3, 1, 1, nx, ny, nz, r, g, b, a, light);
        vertex(vertices, matrix, normal, x4, y4, z4, 0, 1, nx, ny, nz, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz,
                               int r, int g, int b, int a, int light) {
        vertices.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal, nx, ny, nz)
                .next();
    }
}
