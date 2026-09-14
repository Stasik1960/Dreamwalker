package com.coraxberg.rpchatui.client;

/** GUI-pixel geometry shared by painting, hit testing and the standalone layout checks. */
final class ChatLayout {
    record Rect(int x, int y, int width, int height) {}

    static Rect fit(int x, int y, int width, int height, int minW, int minH,
                    int screenW, int screenH, int rightReserve) {
        int availableW = Math.max(1, screenW - rightReserve - 8);
        int availableH = Math.max(1, screenH - 8);
        width = Math.min(availableW, Math.max(minW, width));
        height = Math.min(availableH, Math.max(minH, height));
        x = Math.max(4, Math.min(x, screenW - rightReserve - width - 4));
        y = Math.max(4, Math.min(y, screenH - height - 4));
        return new Rect(x, y, width, height);
    }

    static boolean compact(int width) { return width < 600; }
    static int toolbarY(int width) { return compact(width) ? 28 : 4; }
    static int toolbarX(int width) { return compact(width) ? 6 : width - 144; }
    static int baseHeader(int width) { return compact(width) ? 48 : 24; }
    static int header(int width, boolean search) { return baseHeader(width) + (search ? 36 : 0); }
    static int tabsEnd(int width) { return compact(width) ? width - 27 : width - 171; }
    static int tabWidth(int width, int desired) { return Math.min(desired, Math.max(1, tabsEnd(width) - 63)); }
    static int firstTab(int first, int selected, int width, int[] desiredWidths) {
        first = Math.max(0, Math.min(first, selected));
        while (first < selected) {
            int used = 0;
            for (int i = first; i <= selected; i++) used += tabWidth(width, desiredWidths[i]) + (i == first ? 0 : 4);
            if (used <= tabsEnd(width) - 63) break;
            first++;
        }
        return first;
    }

    static int firstInputLine(int[] lengths, int cursor, int visible) {
        int line = 0, offset = 0;
        while (line < lengths.length - 1 && offset + lengths[line] < cursor) offset += lengths[line++];
        return Math.max(0, line - visible + 1);
    }

    static int inputLines(int height, int header, int wrappedLines) {
        return Math.max(1, Math.min(wrappedLines, Math.min(5, (height - header - 42) / 12)));
    }
    static int categoryRows(int height) { return Math.max(1, (height - 146) / 22); }
}
