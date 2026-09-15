package com.coraxberg.rpchatui.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Editor wrapping must preserve every character, including spaces at a wrap. */
final class ChatInputLayout {
    record Line(int start, int end, String text) {}
    static List<Line> wrap(String text, int width, ToIntFunction<String> measure) {
        List<Line> lines = new ArrayList<>();
        if (text.isEmpty()) return List.of(new Line(0, 0, ""));
        int start = 0;
        while (start < text.length()) {
            int end = start;
            while (end < text.length()) {
                int next = text.offsetByCodePoints(end, 1);
                if (end > start && measure.applyAsInt(text.substring(start, next)) > width) break;
                end = next;
            }
            if (end < text.length()) {
                int space = text.lastIndexOf(' ', end - 1);
                if (space > start) end = space + 1;
            }
            lines.add(new Line(start, end, text.substring(start, end)));
            start = end;
        }
        return lines;
    }
    static int cursorLine(List<Line> lines, int cursor) {
        for (int i = 0; i < lines.size(); i++) {
            if (cursor < lines.get(i).end() || i == lines.size() - 1) return i;
        }
        return 0;
    }
}
