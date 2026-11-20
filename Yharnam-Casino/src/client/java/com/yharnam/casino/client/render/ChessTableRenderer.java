package com.yharnam.casino.client.render;

import com.yharnam.casino.ChessLayout;
import com.yharnam.casino.ChessTableEntity;
import com.yharnam.casino.GamingFurnitureBlock;
import com.yharnam.casino.YharnamCasino;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Draws the light, moving portion of a chess table. The table itself remains a
 * chunk-baked model, while the shared chessman meshes are instanced here.
 */
public final class ChessTableRenderer implements BlockEntityRenderer<ChessTableEntity> {
    private static final RenderLayer PIECES = RenderLayer.getEntityCutout(
            new Identifier(YharnamCasino.ID, "textures/machine/chess_table.png"));
    private static final float RELEASE_TICKS = 4.0f;
    private static final double LOD_DISTANCE_SQUARED = 9.0 * 9.0;

    private final Map<Character, MachineMesh.Part> pieces = new HashMap<>();
    private final Map<Character, MachineMesh.Part> lodPieces = new HashMap<>();
    private final MachineMesh.Part hint;
    private Map<Character, MachineMesh.Part> activePieces;

    public ChessTableRenderer(BlockEntityRendererFactory.Context context) {
        MachineMesh mesh = MachineMesh.load("chessmen");
        MachineMesh lod = MachineMesh.load("chessmen_lod");
        for (char side : new char[]{'w', 'b'}) {
            for (char type : "pnbrqk".toCharArray()) {
                char boardPiece = side == 'w' ? Character.toUpperCase(type) : type;
                pieces.put(boardPiece, mesh.part("" + side + type));
                lodPieces.put(boardPiece, lod.part("" + side + type));
            }
        }
        hint = MachineMesh.load("chess_hint").part("hint");
        activePieces = pieces;
    }

    @Override
    public void render(ChessTableEntity table, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        if (!table.installed() || table.getWorld() == null) {
            return;
        }

        double now = table.getWorld().getTime() + tickDelta;
        ChessTableEntity.Motion motion = table.motion();
        ChessTableEntity.ResetMotion reset = table.resetMotion();
        // A block-entity update may arrive just before the client's world clock
        // reaches the server start tick. Treat that short negative interval as
        // the first animation frame instead of flashing the final board once.
        boolean resetting = reset != null && now < reset.start() + ChessTableEntity.RESET_TICKS;
        boolean moving = motion != null && now < motion.start() + ChessTableEntity.MOVE_TICKS;
        if (resetting) {
            moving = false;
        }
        int selected = moving ? -1 : table.selection();
        int released = moving ? -1 : table.releasedSquare();
        float releasedProgress = released >= 0
                ? clamp((float) ((now - table.releasedAt()) / RELEASE_TICKS))
                : 1.0f;
        boolean releasing = released >= 0 && releasedProgress < 1.0f;

        matrices.push();
        matrices.translate(.5, 0, .5);
        float facing = table.getCachedState().get(GamingFurnitureBlock.FACING).asRotation();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing));
        matrices.translate(0, 0, .5);
        VertexConsumer vertices = consumers.getBuffer(PIECES);
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        var center = ChessLayout.center(table.getPos(),
                table.getCachedState().get(GamingFurnitureBlock.FACING)).add(0, ChessLayout.SURFACE, 0);
        activePieces = camera.squaredDistanceTo(center) > LOD_DISTANCE_SQUARED ? lodPieces : pieces;

        if (!moving && !resetting && selected >= 0) {
            renderTargets(table, matrices, vertices, light);
        }

        for (int square = 0; square < 64 && !resetting; square++) {
            char piece = table.piece(square);
            if (piece == '.') {
                continue;
            }
            if (moving && (square == motion.to() || square == motion.rookTo())) {
                continue;
            }
            if (square == selected) {
                renderSelected(table, piece, square, now, matrices, vertices, light);
            } else if (releasing && square == released) {
                float height = (float) table.loweringHeight(now);
                renderPiece(piece, ChessLayout.x(square), ChessLayout.z(square),
                        ChessLayout.SURFACE + height, 1.0f, matrices, vertices, light);
            } else {
                renderPiece(piece, ChessLayout.x(square), ChessLayout.z(square),
                        ChessLayout.SURFACE, 1.0f, matrices, vertices, light);
            }
        }

        if (resetting) {
            renderReset(table, reset, now, matrices, vertices, light);
        } else {
            renderCapturedPieces(table, motion, moving, matrices, vertices, light);
        }
        if (moving && !resetting) {
            renderMotion(table, motion, now, matrices, vertices, light);
        }
        if (!resetting && table.pendingPromotion()) {
            renderPromotionChoices(table, motion, matrices, vertices, light);
        }
        matrices.pop();
    }

    private void renderSelected(ChessTableEntity table, char piece, int square, double now,
                                MatrixStack matrices, VertexConsumer vertices, int light) {
        double lift = table.selectedLift(now);
        renderPiece(piece, ChessLayout.x(square), ChessLayout.z(square),
                ChessLayout.SURFACE + lift, 1.0f, matrices, vertices, light);
    }

    private void renderMotion(ChessTableEntity table, ChessTableEntity.Motion motion, double now,
                              MatrixStack matrices, VertexConsumer vertices, int light) {
        float raw = clamp((float) ((now - motion.start()) / ChessTableEntity.MOVE_TICKS));
        float progress = smooth(raw);
        float hop = Character.toLowerCase(motion.moving()) == 'n' ? .185f : .048f;
        double x = lerp(ChessLayout.x(motion.from()), ChessLayout.x(motion.to()), progress);
        double z = lerp(ChessLayout.z(motion.from()), ChessLayout.z(motion.to()), progress);
        double y = ChessLayout.SURFACE
                + motion.lift() * (1.0 - progress)
                + hop * Math.sin(Math.PI * raw);
        renderArrival(motion.moving(), table.piece(motion.to()), raw, x, z, y, 1, matrices, vertices, light);

        if (motion.rookFrom() >= 0 && motion.rookTo() >= 0) {
            char rook = Character.isUpperCase(motion.moving()) ? 'R' : 'r';
            double rookX = lerp(ChessLayout.x(motion.rookFrom()), ChessLayout.x(motion.rookTo()), progress);
            double rookZ = lerp(ChessLayout.z(motion.rookFrom()), ChessLayout.z(motion.rookTo()), progress);
            double arc = Math.sin(Math.PI * raw);
            rookZ += (rook=='R' ? .17 : -.17) * arc;
            double rookY = ChessLayout.SURFACE + .06 * arc;
            renderPiece(rook, rookX, rookZ, rookY, 1.0f, matrices, vertices, light);
        }

        if (motion.captured() != '.') {
            boolean white = Character.isUpperCase(motion.captured());
            double captureX = lerp(ChessLayout.x(motion.capturedSquare()),
                    ChessLayout.trayX(white, motion.rackSlot()), progress);
            double captureZ = lerp(ChessLayout.z(motion.capturedSquare()),
                    ChessLayout.trayZ(motion.rackSlot()), progress);
            double captureY = lerp(ChessLayout.SURFACE, ChessLayout.TRAY, progress) + .195 * Math.sin(Math.PI * raw);
            renderPiece(motion.captured(), captureX, captureZ, captureY,
                    1-.20f*progress, matrices, vertices, light);
        }
    }

    private void renderCapturedPieces(ChessTableEntity table, ChessTableEntity.Motion motion,
                                      boolean moving, MatrixStack matrices,
                                      VertexConsumer vertices, int light) {
        int whiteSlot = 0;
        int blackSlot = 0;
        String captured = table.captures();
        for (int index = 0; index < captured.length(); index++) {
            char piece = captured.charAt(index);
            boolean white = Character.isUpperCase(piece);
            int slot = white ? whiteSlot++ : blackSlot++;
            if (moving && motion.captured() == piece && motion.rackSlot() == slot) {
                continue;
            }
            renderPiece(piece, ChessLayout.trayX(white, slot), ChessLayout.trayZ(slot),
                    ChessLayout.TRAY, .80f, matrices, vertices, light);
        }
    }

    private void renderReset(ChessTableEntity table, ChessTableEntity.ResetMotion reset, double now,
                             MatrixStack matrices, VertexConsumer vertices, int light) {
        float raw = clamp((float) ((now - reset.start()) / ChessTableEntity.RESET_TICKS));
        float progress = smooth(raw);
        String origins = reset.origins();
        String previousPieces = reset.pieces();
        for (int destination = 0; destination < 64; destination++) {
            char targetPiece = table.piece(destination);
            if (targetPiece == '.') {
                continue;
            }

            int origin = destination < origins.length() ? origins.charAt(destination) : 255;
            char previous = destination < previousPieces.length()
                    ? previousPieces.charAt(destination) : targetPiece;
            double toX = ChessLayout.x(destination);
            double toZ = ChessLayout.z(destination);
            double fromX = toX;
            double fromZ = toZ;
            double fromY = ChessLayout.SURFACE;
            float fromScale = 1.0f;
            double arc = origin == destination ? 0 : .045;

            if (origin < 64) {
                fromX = ChessLayout.x(origin);
                fromZ = ChessLayout.z(origin);
                if (origin == reset.lifted()) {
                    fromY += reset.lift();
                }
            } else if (origin < 80) {
                int rackSlot = origin - 64;
                boolean white = Character.isUpperCase(previous);
                fromX = ChessLayout.trayX(white, rackSlot);
                fromZ = ChessLayout.trayZ(rackSlot);
                fromY = ChessLayout.TRAY;
                fromScale = .80f;
                arc = .11;
            } else {
                // A piece with no old physical counterpart grows into place at its home square.
                fromScale = 0.05f;
                arc = .02;
            }

            double x = lerp(fromX, toX, progress);
            double z = lerp(fromZ, toZ, progress);
            double y = lerp(fromY, ChessLayout.SURFACE, progress)
                    + arc * Math.sin(Math.PI * raw);
            float scale = fromScale + (1.0f - fromScale) * progress;
            renderArrival(previous, targetPiece, raw, x, z, y, scale, matrices, vertices, light);
        }
    }

    private void renderArrival(char before,char after,float time,double x,double z,double y,float scale,
                               MatrixStack matrices,VertexConsumer vertices,int light) {
        if(before==after || before=='.') {
            renderPiece(after,x,z,y,scale,matrices,vertices,light);
            return;
        }
        float swap=clamp((time-.56f)/.44f);
        boolean arrived=swap>=.5f;
        float size=.04f+.96f*smooth(Math.abs(2*swap-1));
        renderPiece(arrived?after:before,x,z,y,scale*size,matrices,vertices,light);
    }

    private void renderPromotionChoices(ChessTableEntity table, ChessTableEntity.Motion motion,
                                        MatrixStack matrices, VertexConsumer vertices, int light) {
        char selected = table.selection() >= 0 ? table.piece(table.selection()) : '.';
        boolean white = selected != '.' ? Character.isUpperCase(selected)
                : motion == null || Character.isUpperCase(motion.moving());
        for (int index = 0; index < ChessLayout.PROMOTIONS.length(); index++) {
            char piece = ChessLayout.PROMOTIONS.charAt(index);
            if (white) {
                piece = Character.toUpperCase(piece);
            }
            renderPiece(piece, ChessLayout.choiceX(index), .872, ChessLayout.TRAY,
                    .70f, matrices, vertices, light);
        }
    }

    private void renderTargets(ChessTableEntity table, MatrixStack matrices,
                               VertexConsumer vertices, int light) {
        long targets = table.targets();
        for (int square = 0; square < 64; square++) {
            if ((targets & 1L << square) == 0) {
                continue;
            }
            double x = ChessLayout.x(square);
            double z = ChessLayout.z(square);
            if (table.piece(square) == '.') {
                renderHint(x + .058, z + .058, matrices, vertices, light);
                continue;
            }
            for (double dx : new double[]{-.058, .058}) {
                for (double dz : new double[]{-.058, .058}) {
                    renderHint(x + dx, z + dz, matrices, vertices, light);
                }
            }
        }
    }

    private void renderHint(double x, double z, MatrixStack matrices,
                            VertexConsumer vertices, int light) {
        matrices.push();
        matrices.translate(x, ChessLayout.SURFACE + .001, z);
        SlotMachineRenderer.renderPart(hint, 0, matrices, vertices, light);
        matrices.pop();
    }

    private void renderPiece(char piece, double x, double z, double y, float scale,
                             MatrixStack matrices, VertexConsumer vertices, int light) {
        MachineMesh.Part part = activePieces.get(piece);
        if (part == null) {
            return;
        }
        matrices.push();
        matrices.translate(x, y, z);
        if (scale != 1.0f) {
            matrices.scale(scale, scale, scale);
        }
        SlotMachineRenderer.renderPart(part, 0, matrices, vertices, light);
        matrices.pop();
    }

    private static float smooth(float value) {
        return value * value * (3.0f - 2.0f * value);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static double lerp(double from, double to, float progress) {
        return from + (to - from) * progress;
    }

    @Override
    public boolean rendersOutsideBoundingBox(ChessTableEntity table) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 24;
    }
}
