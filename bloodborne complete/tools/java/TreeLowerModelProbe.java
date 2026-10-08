import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.render.model.CubeFace;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelElementTexture;
import net.minecraft.util.math.Direction;

/** Pure native parser/UV API probe; no Minecraft client or world is started. */
public class TreeLowerModelProbe {
    public static void main(String[] args) throws Exception {
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            ModelElementTexture uv = new ModelElementTexture(new float[]{0, 13, 1, 16}, 0);
            for (int vertex=0; vertex<4; vertex++) {
                int side = CubeFace.getFace(direction).getCorner(vertex).ySide;
                float expected = side==Direction.UP.getId()?13:16;
                if (side!=Direction.UP.getId() && side!=Direction.DOWN.getId()) throw new AssertionError("Unexpected vertical side");
                if (uv.getV(vertex)!=expected) throw new AssertionError("Wrong vertical UV direction: "+direction+"/"+vertex);
            }
            System.out.println("PASS_SIDE_UV "+direction+" top=13 bottom=16");
        }
        for (int index=1; index<args.length; index++) {
            if (JsonUnbakedModel.deserialize(Files.readString(Path.of(args[index]))).getElements().size()!=2) throw new AssertionError("Expected two crossed elements");
            System.out.println("PASS_VANILLA_PARSE "+Path.of(args[index]).getFileName());
        }
        try {
            JsonUnbakedModel.deserialize(Files.readString(Path.of(args[0])));
            throw new AssertionError("Historical invalid Y96 unexpectedly parsed");
        } catch (com.google.gson.JsonParseException expected) {
            if (!expected.getMessage().contains("'to' specifier exceeds the allowed boundaries")) throw expected;
            System.out.println("PASS_HISTORICAL_INVALID_Y96_REJECTED "+expected.getMessage());
        }
    }
}
