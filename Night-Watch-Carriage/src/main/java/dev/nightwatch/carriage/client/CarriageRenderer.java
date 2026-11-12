package dev.nightwatch.carriage.client;

import dev.nightwatch.carriage.CarriageEntity;
import dev.nightwatch.carriage.NightWatchCarriage;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public final class CarriageRenderer extends EntityRenderer<CarriageEntity> {
    private static final Identifier MODEL = NightWatchCarriage.id("meshes/carriage.nwc");
    private static final Identifier WHITE = NightWatchCarriage.id("textures/entity/white.png");
    private static final float AXLE_Y = .860f;
    private static final float AXLE_Z = -.338f;
    private final Mesh body, leftWheel, rightWheel, lampGlass;
    private final Mesh[] hood;

    public CarriageRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 1.15f;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
            new GZIPInputStream(context.getResourceManager().open(MODEL))))) {
            if (input.readInt() != 0x4E574331) throw new IOException("Unsupported carriage mesh format");
            int frames = input.readInt();
            if (frames < 2 || frames > 32) throw new IOException("Invalid number of canopy poses");
            body = Mesh.read(input);
            leftWheel = Mesh.read(input);
            rightWheel = Mesh.read(input);
            lampGlass = Mesh.read(input);
            hood = new Mesh[frames];
            for (int i = 0; i < frames; i++) {
                hood[i] = Mesh.read(input);
                if (hood[i].colors.length != hood[0].colors.length) throw new IOException("Canopy topology differs");
            }
            if (input.read() != -1) throw new IOException("Unexpected mesh data");
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + MODEL, e);
        }
    }

    @Override public Identifier getTexture(CarriageEntity entity) { return WHITE; }

    @Override
    public boolean shouldRender(CarriageEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x,y,z) && frustum.isVisible(entity.getBoundingBox().expand(2.0, .8, 2.0));
    }

    @Override
    public void render(CarriageEntity carriage, float yaw, float delta, MatrixStack matrices,
                       VertexConsumerProvider providers, int packedLight) {
        matrices.push();
        float heading = MathHelper.lerpAngleDegrees(delta, carriage.prevYaw, carriage.getYaw());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-heading));
        VertexConsumer out = providers.getBuffer(RenderLayer.getEntitySolid(WHITE));
        float roll = carriage.visualWheel(delta);
        matrices.push();
        rotateAxle(matrices, roll);
        draw(leftWheel, leftWheel, 0, matrices, out, packedLight, -1);
        draw(rightWheel, rightWheel, 0, matrices, out, packedLight, -1);
        matrices.pop();

        rotateAxle(matrices, carriage.visualPitch(delta));
        draw(body, body, 0, matrices, out, packedLight, -1);
        boolean lit = carriage.lanternsOn();
        draw(lampGlass, lampGlass, 0, matrices, out,
            lit ? LightmapTextureManager.MAX_LIGHT_COORDINATE : packedLight, lit ? -1 : 0x523B2F);
        float frame = (1f - carriage.visualHood(delta)) * (hood.length - 1);
        int first = MathHelper.clamp(MathHelper.floor(frame), 0, hood.length - 1);
        int next = Math.min(first + 1, hood.length - 1);
        draw(hood[first], hood[next], frame - first, matrices, out, packedLight, -1);
        matrices.pop();
        super.render(carriage, yaw, delta, matrices, providers, packedLight);
    }

    private static void rotateAxle(MatrixStack matrices, float degrees) {
        matrices.translate(0, AXLE_Y, AXLE_Z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
        matrices.translate(0, -AXLE_Y, -AXLE_Z);
    }

    private static void draw(Mesh a, Mesh b, float blend, MatrixStack matrices, VertexConsumer out,
                             int light, int colorOverride) {
        Matrix4f position = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();
        boolean animate = a != b && blend > 0f;
        for (int i = 0; i < a.colors.length; i++) {
            int offset = 6 * i;
            float x = a.vertices[offset];
            float y = a.vertices[offset + 1];
            float z = a.vertices[offset + 2];
            float nx = a.vertices[offset + 3];
            float ny = a.vertices[offset + 4];
            float nz = a.vertices[offset + 5];
            if (animate) {
                x = MathHelper.lerp(blend, x, b.vertices[offset]);
                y = MathHelper.lerp(blend, y, b.vertices[offset + 1]);
                z = MathHelper.lerp(blend, z, b.vertices[offset + 2]);
                nx = MathHelper.lerp(blend, nx, b.vertices[offset + 3]);
                ny = MathHelper.lerp(blend, ny, b.vertices[offset + 4]);
                nz = MathHelper.lerp(blend, nz, b.vertices[offset + 5]);
            }
            int rgb = colorOverride >= 0 ? colorOverride : a.colors[i];
            vertex(out, position, normal, x, y, z, nx, ny, nz, rgb, light);
            // The vanilla entity-solid layer consumes quads. Its second triangle is degenerate.
            if (i % 3 == 2) vertex(out, position, normal, x, y, z, nx, ny, nz, rgb, light);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f position, Matrix3f normal, float x, float y, float z,
                               float nx, float ny, float nz, int rgb, int light) {
        out.vertex(position, x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
            .texture(.5f, .5f).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, nx, ny, nz).next();
    }

    private record Mesh(float[] vertices, int[] colors) {
        static Mesh read(DataInputStream input) throws IOException {
            int count = input.readInt();
            if (count <= 0 || count > 250_000 || count % 3 != 0) throw new IOException("Invalid carriage mesh size");
            float[] vertices = new float[count * 6];
            int[] colors = new int[count];
            for (int i = 0; i < count; i++) {
                for (int component = 0; component < 6; component++) {
                    vertices[i * 6 + component] = input.readFloat();
                }
                colors[i] = input.readInt();
            }
            return new Mesh(vertices, colors);
        }
    }
}
