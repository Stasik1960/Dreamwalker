package com.coraxberg.dwmagicconnect.client;

/** All controls share the texture coordinate system, including at large GUI scales. */
public record RadioScreenLayout(int diameter, int radius, int centerY, int controlsWidth) {
    public static RadioScreenLayout fit(int width, int height) {
        int diameter = Math.max(64, Math.min(560, Math.min(width - 16, height - 8)));
        int radius = Math.max(8, (int) (diameter * .135));
        return new RadioScreenLayout(diameter, radius, height / 2 + (int) (diameter * .045), 4 * radius + 20);
    }
    public int offset() { return (int) (diameter * .153); }
    public int top() { return centerY - radius - 12; }
    public int footer() { return centerY + radius + 5; }
    public int x(int width, double u) { return (width - diameter) / 2 + (int) Math.round(diameter * u); }
    public int y(int height, double v) { return (height - diameter) / 2 + (int) Math.round(diameter * v); }
}
