package com.coraxberg.dwmagicconnect.client;

/** Uses actual GUI pixels so text remains readable at the largest GUI scale. */
public record RadioScreenLayout(int diameter, int controlsWidth, int top) {
    public static RadioScreenLayout fit(int width, int height) {
        int diameter = Math.min(480, Math.min(width - 8, height - 8));
        int controls = Math.max(190, Math.min(260, (int) (diameter * .66)));
        return new RadioScreenLayout(diameter, controls, height / 2 - 67);
    }
}
