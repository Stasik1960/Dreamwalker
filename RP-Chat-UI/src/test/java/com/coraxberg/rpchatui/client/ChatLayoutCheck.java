package com.coraxberg.rpchatui.client;

/** Checks real production geometry without launching Minecraft or substituting a second layout. */
public final class ChatLayoutCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int cases = 0;
        for (int[] screen : new int[][] {{320,240}, {427,240}, {640,360}, {960,540}}) {
            for (int requested : new int[] {260, 520, 600, 1400}) {
                for (int[] position : new int[][] {{-900,-900}, {20,20}, {1800,1800}}) {
                    ChatLayout.Rect r = ChatLayout.fit(position[0], position[1], requested, 900, 260, 170, screen[0], screen[1], 34);
                    check(r.x() >= 0 && r.y() >= 0 && r.x() + r.width() + 34 <= screen[0] && r.y() + r.height() <= screen[1], "Main window or side buttons outside screen");
                    check(ChatLayout.toolbarX(r.width()) + 136 <= r.width(), "Toolbar clipped");
                    check(ChatLayout.tabsEnd(r.width()) + 23 <= r.width(), "Next tab clipped");
                    if (!ChatLayout.compact(r.width())) check(ChatLayout.tabsEnd(r.width()) + 23 < ChatLayout.toolbarX(r.width()), "Tabs overlap toolbar");
                    for (boolean search : new boolean[] {false,true}) {
                        int header = ChatLayout.header(r.width(), search);
                        int lines = ChatLayout.inputLines(r.height(), header, 100);
                        check(lines >= 1 && lines <= 5, "Invalid input height");
                        check(r.height() - header - (18 + 12 * lines) >= 24, "Input hides messages");
                        check(header + 78 <= r.height(), "Side buttons below window");
                        if (search) check(ChatLayout.baseHeader(r.width()) + 10 + 24 <= header, "Search overlaps messages");
                    }
                    int[] widths = {142, 142, 58, 142, 120, 142, 58, 142, 142, 142};
                    int first = 0;
                    // Last tab must remain reachable; walking backwards must bring earlier tabs back.
                    for (int selected : new int[] {0,1,2,3,4,5,6,7,8,9,8,7,6,5,4,3,2,1,0}) {
                        first = ChatLayout.firstTab(first, selected, r.width(), widths);
                        int end = 63;
                        for (int i = first; i <= selected; i++) end += ChatLayout.tabWidth(r.width(), widths[i]) + (i == first ? 0 : 4);
                        check(first <= selected && end <= ChatLayout.tabsEnd(r.width()), "Selected tab hidden");
                    }
                    for (int minWidth : new int[] {260,280}) {
                        ChatLayout.Rect modal = ChatLayout.fit(position[0], position[1], requested, 900, minWidth, 200, screen[0], screen[1], 0);
                        check(modal.x() >= 0 && modal.y() >= 0 && modal.x() + modal.width() <= screen[0] && modal.y() + modal.height() <= screen[1], "Settings outside screen");
                        int rows = ChatLayout.categoryRows(modal.height());
                        check(88 + (rows - 1) * 22 + 18 < modal.height() - 54, "Categories overlap pagination");
                        check(114 + 20 < modal.height() - 28, "Chat settings overlap footer");
                        check(modal.width() - 96 >= 100, "Tint name too narrow");
                    }
                    cases++;
                }
            }
        }
        int[] longInput = new int[40];
        java.util.Arrays.fill(longInput, 30);
        check(ChatLayout.firstInputLine(longInput, 1200, 3) == 37, "End of pasted input is hidden");
        check(ChatLayout.firstInputLine(longInput, 0, 3) == 0, "Home does not reveal beginning");
        check(ChatLayout.firstInputLine(longInput, 600, 3) == 17, "Caret in middle is hidden");
        // GUI scale must project the saved geometry, not replace it with the fitted result.
        ChatLayout.Rect preferred = new ChatLayout.Rect(110, 100, 600, 320);
        for (int reserve : new int[] {0, 34}) {
            ChatLayout.Rect original = ChatLayout.fit(preferred.x(), preferred.y(), preferred.width(), preferred.height(), 260, 200, 960, 540, reserve);
            ChatLayout.Rect small = ChatLayout.fit(preferred.x(), preferred.y(), preferred.width(), preferred.height(), 260, 200, 320, 240, reserve);
            ChatLayout.Rect restored = ChatLayout.fit(preferred.x(), preferred.y(), preferred.width(), preferred.height(), 260, 200, 960, 540, reserve);
            check(small.width() < original.width() && small.height() < original.height(), "Small GUI does not fit");
            check(restored.equals(original) && restored.equals(preferred), "GUI scale roundtrip changed preference");
        }
        System.out.println("Chat layout checks passed: " + cases + " screen/size/position combinations, tab navigation, modal bounds, input viewport and GUI scale roundtrip.");
    }
}
