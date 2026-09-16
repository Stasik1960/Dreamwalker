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
    /**
     * x/y and rx/ry describe the visible opening from table_geometry.json.
     * innerY is the visible inner edge of a side-pocket mouth (NaN for corners).
     */
    public record Pocket(double x, double y, double rx, double ry, double innerY) {
        public boolean captures(double bx, double by) {
            // A sphere loses stable cloth support before its top-down disc fits
            // wholly inside the opening. Requiring a full ball-radius clearance
            // made a rendered ball travel visibly too far into a pocket. This
            // smaller inset approximates the support/contact patch while the
            // baked jaw rails still reject shots that catch either lip.
            double supportInset = PoolGameState.BALL_R * 0.35;
            double captureRx = rx - supportInset;
            double captureRy = ry - supportInset;
            if (captureRx <= 0 || captureRy <= 0) return false;
            double dx = (bx - x) / captureRx;
            double dy = (by - y) / captureRy;
            if (dx * dx + dy * dy > 1) return false;
            // innerY is already the visible loss-of-support edge. Compare the
            // centre, rather than waiting for the trailing edge of the ball.
            return Double.isNaN(innerY) || (y < PoolGameState.TABLE_H / 2
                    ? by <= innerY : by >= innerY);
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
