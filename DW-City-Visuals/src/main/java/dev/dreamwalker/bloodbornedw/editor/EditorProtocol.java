package dev.dreamwalker.bloodbornedw.editor;

import java.util.List;

/** Shared brush modes and the actions the server exposes. */
final class EditorProtocol {
    static final List<String> MODES = List.of("single", "plane", "area", "fill", "noise");
    static final List<String> COMMANDS = List.of("sample", "paint", "first", "second", "fill", "noise", "reset", "undo", "redo", "cancel");

    private EditorProtocol() {}

    static boolean acceptsAction(String action) {
        return COMMANDS.contains(action) || action.equals("config") || action.equals("endStroke");
    }
}
