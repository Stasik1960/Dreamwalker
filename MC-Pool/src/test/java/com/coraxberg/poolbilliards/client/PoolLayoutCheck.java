package com.coraxberg.poolbilliards.client;

import com.coraxberg.poolbilliards.game.PoolTableGeometry;

/** Checks usable geometry at real GUI sizes, including both sides of the breakpoint. */
public final class PoolLayoutCheck {
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[][] sizes = {{320, 240}, {427, 240}, {640, 360}, {715, 360}, {716, 360},
                {960, 540}, {1920, 1080}, {3440, 1440}, {320, 900}, {1280, 240}};
        for (int[] size : sizes) {
            PoolScreenLayout l = PoolScreenLayout.fit(size[0], size[1]);
            String label = size[0] + "x" + size[1];
            require(l.x() >= 0 && l.y() >= 0 && l.x() + l.width() <= size[0]
                    && l.y() + l.height() <= size[1], "panel outside " + label);
            require(l.tableWidth() >= 200 && l.tableHeight() >= 100, "unusable table " + label);
            require(Math.abs(l.tableHeight() - l.tableWidth() * 490.0 / 900.0) <= 0.51,
                    "distorted playfield " + label);
            require(l.tableX() - l.rail() >= l.x() && l.tableY() - l.rail() >= l.y() + 30,
                    "table overlaps header " + label);
            require(l.tableY() + l.tableHeight() + l.rail() <= l.footerY(),
                    "table overlaps footer " + label);
            for (var p : PoolTableGeometry.POCKETS) {
                double cx = l.tableX() + p.x() * l.tableWidth() / 900;
                double cy = l.tableY() + p.y() * l.tableHeight() / 490;
                double rx = p.rx() * l.tableWidth() / 900;
                double ry = p.ry() * l.tableHeight() / 490;
                require(cx - rx >= l.tableX() - l.rail() && cx + rx <= l.tableX() + l.tableWidth() + l.rail()
                                && cy - ry >= l.tableY() - l.rail() && cy + ry <= l.tableY() + l.tableHeight() + l.rail(),
                        "model pocket escapes reserved rail " + label);
            }
            require(l.resetX() >= l.x() && l.resetX() + l.resetWidth() <= l.x() + l.width()
                    && l.resetY() + 20 <= l.y() + l.height(), "reset not accessible " + label);
            int styleWidth = Math.min(160, l.resetX() - (l.x() + 10) - 8);
            require(styleWidth >= 100 && l.x() + 10 + styleWidth < l.resetX(),
                    "ball style selector overlaps reset or is too small " + label);
            if (l.sidebar()) {
                require(l.tableX() + l.tableWidth() + l.rail() < l.sidebarX(),
                        "table overlaps sidebar " + label);
            } else {
                require(l.footerY() + 16 <= l.resetY(), "players overlap controls " + label);
            }
            require(l.pullDistance() < l.tableWidth() / 2.0, "full power unreachable " + label);
        }
        require(!PoolScreenLayout.fit(320, 240).sidebar(), "large GUI needs compact layout");
        require(PoolScreenLayout.fit(960, 540).sidebar(), "wide GUI should use sidebar");
        System.out.println("Pool layout checks passed: 10 viewport sizes, proportions, containment, controls");
    }
}
