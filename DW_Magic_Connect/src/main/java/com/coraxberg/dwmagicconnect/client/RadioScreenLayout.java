package com.coraxberg.dwmagicconnect.client;

public record RadioScreenLayout(int diameter, int radius, int centerY, int controlsWidth) {
    public static RadioScreenLayout fit(int width, int height) {
        int diameter = Math.max(100, Math.min(560, Math.min(width - 8, height - 8)));
        int radius = Math.max(28, Math.min(64, (int) (diameter * .18)));
        int centerY = height / 2 + Math.max(-10, Math.min(20, (diameter - 232) / 8 - 10));
        return new RadioScreenLayout(diameter, radius, centerY, Math.min(280, 4 * radius + 28));
    }
    public int offset() { return radius + 10; }
    public int top() { return centerY - radius - 46; }
    public int footer() { return centerY + radius + 18; }
}
