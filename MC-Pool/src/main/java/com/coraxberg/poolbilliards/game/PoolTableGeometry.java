package com.coraxberg.poolbilliards.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generated model geometry, loaded once; no world or client is required. */
public final class PoolTableGeometry {
    public record Rail(double x0, double y0, double x1, double y1) {}
    public record Pocket(double x, double y, double rx, double ry, double innerY) {
        public boolean captures(double bx, double by) {
            double r = PoolGameState.BALL_R;
            // Account for the whole ball and the model's half-pixel arc strips.
            double dx = (bx - x) / (rx - r - 1.6);
            double dy = (by - y) / (ry - r - 1.6);
            if (dx * dx + dy * dy > 1) return false;
            return Double.isNaN(innerY) || (y < PoolGameState.TABLE_H / 2
                    ? by + r <= innerY : by - r >= innerY);
        }
    }
    public static final List<Rail> RAILS;
    public static final List<Pocket> POCKETS;
    private static double x(double model) { return (model + 27.2) / 70.4 * PoolGameState.TABLE_W; }
    private static double y(double model) { return (model + 11.2) / 38.4 * PoolGameState.TABLE_H; }
    static {
        try (var reader = new InputStreamReader(Objects.requireNonNull(PoolTableGeometry.class
                .getResourceAsStream("/assets/poolbilliards/table_geometry.json")), StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            var rails = new ArrayList<Rail>();
            for (JsonElement element : data.getAsJsonArray("rails")) {
                JsonArray a = element.getAsJsonArray();
                rails.add(new Rail(x(a.get(0).getAsDouble()), y(a.get(1).getAsDouble()),
                        x(a.get(2).getAsDouble()), y(a.get(3).getAsDouble())));
            }
            var pockets = new ArrayList<Pocket>();
            for (JsonElement element : data.getAsJsonArray("pockets")) {
                JsonObject p = element.getAsJsonObject();
                double radius = p.get("radius").getAsDouble();
                pockets.add(new Pocket(x(p.get("x").getAsDouble()), y(p.get("z").getAsDouble()),
                        radius / 70.4 * PoolGameState.TABLE_W, radius / 38.4 * PoolGameState.TABLE_H,
                        p.get("innerZ").isJsonNull() ? Double.NaN : y(p.get("innerZ").getAsDouble())));
            }
            RAILS = List.copyOf(rails);
            POCKETS = List.copyOf(pockets);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private PoolTableGeometry() {}
}
