package com.yharnam.casino.client.render;

import com.yharnam.casino.ChipCabinetBlock;
import com.yharnam.casino.ChipCabinetEntity;
import com.yharnam.casino.YharnamCasino;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class ChipCabinetRenderer implements BlockEntityRenderer<ChipCabinetEntity> {
    private static final RenderLayer STOCK = RenderLayer.getEntityCutout(
            YharnamCasino.id("textures/entity/chip_cabinet_stock.png"));
    // Shelf and lane coordinates match tools/chip_cabinet_layout.py.
    private static final float[] X = {-3.75F,0,3.75F,-2.47F,2.47F,-2.47F,2.47F};
    private static final float[] Y = {8.235F,8.235F,8.235F,15.105F,15.105F,21.985F,21.985F};
    private static final float[] RADIAL_X = new float[8];
    private static final float[] RADIAL_Y = new float[8];
    private static final float[] NORMAL_X = new float[8];
    private static final float[] NORMAL_Y = new float[8];
    private static final int[][] CAP = {{0,1,2,3},{0,3,4,5},{0,5,6,7}};
    private static final float RADIUS = .23F / 16;

    static {
        for (int point=0; point<8; point++) {
            double angle = Math.PI / 8 + point * Math.PI / 4;
            RADIAL_X[point] = (float)Math.cos(angle);
            RADIAL_Y[point] = (float)Math.sin(angle);
            NORMAL_X[point] = (float)Math.cos(angle+Math.PI/8);
            NORMAL_Y[point] = (float)Math.sin(angle+Math.PI/8);
        }
    }

    public ChipCabinetRenderer(BlockEntityRendererFactory.Context context) { }

    @Override
    public void render(ChipCabinetEntity cabinet, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light, int overlay) {
        matrices.push();
        matrices.translate(.5,0,.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                -cabinet.getCachedState().get(ChipCabinetBlock.FACING).asRotation()));
        VertexConsumer consumer = null;
        for (int denomination=0; denomination<ChipCabinetEntity.DENOMINATIONS; denomination++) {
            int chips = cabinet.visibleCount(denomination);
            for (int lane=0; chips>0 && lane<ChipCabinetEntity.LANES; lane++) {
                int layers = Math.min(chips,64);
                if (consumer == null) {
                    consumer = vertices.getBuffer(STOCK);
                }
                drawRow(consumer,matrices.peek(),denomination,lane,layers,light);
                chips -= layers;
            }
        }
        matrices.pop();
    }

    private static void drawRow(VertexConsumer vertices, MatrixStack.Entry matrices,
                                int denomination, int lane, int layers, int light) {
        float x = (X[denomination]+(lane-2.5F)*.55F)/16;
        float y = (Y[denomination]+.33F)/16;
        float front = (11.43F-8)/16;
        float back = front-layers*.060F/16;
        float u0 = (denomination*16+.5F)/128;
        float u1 = (denomination*16+15.5F)/128;
        float v0 = (272-layers*4+.25F)/512;
        float v1 = 271.75F/512;
        for (int side=0; side<8; side++) {
            int next = (side+1)&7;
            float nx = NORMAL_X[side];
            float ny = NORMAL_Y[side];
            corner(vertices,matrices,x,y,back,side,u0,v1,nx,ny,0,light);
            corner(vertices,matrices,x,y,back,next,u1,v1,nx,ny,0,light);
            corner(vertices,matrices,x,y,front,next,u1,v0,nx,ny,0,light);
            corner(vertices,matrices,x,y,front,side,u0,v0,nx,ny,0,light);
        }
        for (int[] quad : CAP) {
            for (int point : quad) {
                cap(vertices,matrices,x,y,front,denomination,point,1,light);
            }
            for (int corner=quad.length-1; corner>=0; corner--) {
                cap(vertices,matrices,x,y,back,denomination,quad[corner],-1,light);
            }
        }
    }

    private static void cap(VertexConsumer vertices, MatrixStack.Entry matrices, float x, float y, float z,
                            int denomination, int point, int normal, int light) {
        float u = (denomination*16+8+7.25F*RADIAL_X[point])/128;
        float v = (8-7.25F*RADIAL_Y[point])/512;
        corner(vertices,matrices,x,y,z,point,u,v,0,0,normal,light);
    }

    private static void corner(VertexConsumer vertices, MatrixStack.Entry matrices, float x, float y, float z,
                               int point, float u, float v, float nx, float ny, float nz, int light) {
        vertices.vertex(matrices.getPositionMatrix(),x+RADIUS*RADIAL_X[point],y+RADIUS*RADIAL_Y[point],z)
                .color(255,255,255,255).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(matrices.getNormalMatrix(),nx,ny,nz).next();
    }

    @Override
    public int getRenderDistance() {
        return 36;
    }
}
