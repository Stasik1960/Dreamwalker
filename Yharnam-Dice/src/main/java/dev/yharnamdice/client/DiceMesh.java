package dev.yharnamdice.client;

import com.google.gson.Gson;
import dev.yharnamdice.DiceMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class DiceMesh {
    static final RenderLayer LAYER = RenderLayer.getEntityCutout(DiceMod.id("textures/die/atlas.png"));
    private static final Face[] FACES = readMesh();
    private static final Vector3f POSITION = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private record Face(float[] normal, float[][] points) {}

    private DiceMesh() {}

    static void draw(VertexConsumer out, MatrixStack.Entry matrix, int skin, int light, int overlay) {
        if (skin<0 || skin>=DiceMod.SKINS.length) return;
        var position=matrix.getPositionMatrix();
        var normal=matrix.getNormalMatrix();
        int uBase=(skin%4)*256;
        int vBase=(skin/4)*256;
        for (Face face : FACES) {
            float[] n=face.normal;
            normal.transform(n[0], n[1], n[2], NORMAL);
            for (int i=0;i<4;i++) {
                float[] p=face.points[Math.min(i,face.points.length-1)];
                position.transformPosition(p[0], p[1], p[2], POSITION);
                out.vertex(POSITION.x, POSITION.y, POSITION.z, 1, 1, 1, 1,
                        (p[3]+uBase)/1024, (p[4]+vBase)/2048, overlay, light,
                        NORMAL.x, NORMAL.y, NORMAL.z);
            }
        }
    }

    private static Face[] readMesh() {
        try (InputStream stream=DiceMesh.class.getResourceAsStream("/d6-mesh.json")) {
            if (stream==null) throw new IllegalStateException("The bundled D6 mesh is missing");
            Face[] faces=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Face[].class);
            if (faces.length!=26) throw new IllegalStateException("The bundled D6 mesh must have 26 faces");
            return faces;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Could not read the bundled D6 mesh",error);
        }
    }
}
