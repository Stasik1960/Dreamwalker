package com.coraxberg.poolbilliards.client;

/** Geometry in Minecraft's scaled GUI coordinates, also used by hit testing. */
public record PoolScreenLayout(int x, int y, int width, int height,
                               int tableX, int tableY, int tableWidth, int tableHeight,
                               int rail, int footerY, int sidebarX, int resetX, int resetY,
                               int resetWidth, boolean sidebar) {
    public static PoolScreenLayout fit(int screenWidth, int screenHeight) {
        int w = Math.max(1, Math.min(1040, screenWidth - 16));
        int h = Math.max(1, Math.min(680, screenHeight - 16));
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;
        boolean sidebar = w >= 700 && h >= 344;
        int rail = 8;
        int sideX = x + w - 198;
        int regionWidth = sidebar ? w - 208 : w;
        int footer = y + h - (sidebar ? 30 : 46);
        int availableW = Math.max(1, regionWidth - 2 * (rail + 10));
        int availableH = Math.max(1, footer - (y + 34) - rail * 2 - 6);
        int tw = Math.max(1, Math.min(availableW, (int) Math.floor(availableH * 900.0 / 490.0)));
        // Reserve the whole pocket, not just the felt, below the header.
        rail = Math.max(8, (int) Math.ceil(tw * 38.0 / 900.0) + 2);
        availableW = Math.max(1, regionWidth - 2 * (rail + 10));
        availableH = Math.max(1, footer - (y + 34) - rail * 2 - 6);
        tw = Math.max(1, Math.min(availableW, (int) Math.floor(availableH * 900.0 / 490.0)));
        int th = Math.max(1, (int) Math.round(tw * 490.0 / 900.0));
        int tx = x + (regionWidth - tw) / 2;
        int ty = y + 34 + rail + (availableH - th) / 2;
        int resetW = sidebar ? 168 : 110;
        int resetX = sidebar ? sideX + 10 : x + w - resetW - 8;
        return new PoolScreenLayout(x, y, w, h, tx, ty, tw, th, rail, footer,
                sideX, resetX, y + h - 26, resetW, sidebar);
    }

    public double pullDistance() {
        return Math.max(36.0, tableWidth * 0.30);
    }
}
