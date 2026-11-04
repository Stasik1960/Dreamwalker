package dev.dreamwalker.bloodbornedw.composite;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates the terrain sample before adding the transparent-pixel depth guard. */
public final class SodiumShaderPatch {
    private static final Pattern SAMPLE = Pattern.compile(
            "(?m)^[\\t ]*vec4\\s+diffuseColor\\s*=\\s*texture\\s*\\(\\s*"
            + "u_BlockTex\\s*,\\s*v_TexCoord\\s*,\\s*v_MaterialMipBias\\s*\\)\\s*;");
    private static final String INSERTION = "\n#ifdef DW_TRANSLUCENT_PASS\n"
            + "    if (diffuseColor.a <= 0.0) discard;\n#endif";

    private SodiumShaderPatch() {}

    public static String apply(String source, String versions) {
        if (source == null) throw unsupported(versions, "shader source is null");
        Matcher matcher = SAMPLE.matcher(maskCommentsAndStrings(source));
        if (!matcher.find()) throw unsupported(versions, "terrain texture sample was not found");
        int end = matcher.end();
        if (matcher.find()) throw unsupported(versions, "terrain texture sample is ambiguous");
        String remainder = source.substring(end);
        String separator = remainder.isEmpty() || remainder.startsWith("\n") || remainder.startsWith("\r") ? "" : "\n";
        return source.substring(0, end) + INSERTION + separator + remainder;
    }

    private static IllegalStateException unsupported(String versions, String reason) {
        return new IllegalStateException("Cannot apply DreamWalker translucent depth fix: " + reason
                + ". Installed " + versions + "; verified with Sodium 0.5.13+mc1.20.1, "
                + "Indium 1.0.36+mc1.20.1 and Iris 1.7.6+mc1.20.1.");
    }

    // Retain offsets and line breaks, but never accept a sample inside a comment or quoted text.
    private static String maskCommentsAndStrings(String source) {
        char[] masked = source.toCharArray();
        boolean lineComment = false, blockComment = false, escaped = false;
        char quote = 0;
        for (int i = 0; i < masked.length; i++) {
            char c = source.charAt(i);
            char next = i + 1 < masked.length ? source.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n' || c == '\r') lineComment = false;
                else masked[i] = ' ';
            } else if (blockComment) {
                if (c == '*' && next == '/') {
                    masked[i] = masked[++i] = ' ';
                    blockComment = false;
                } else if (c != '\n' && c != '\r') masked[i] = ' ';
            } else if (quote != 0) {
                if (c != '\n' && c != '\r') masked[i] = ' ';
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '/' && next == '/') {
                masked[i] = masked[++i] = ' ';
                lineComment = true;
            } else if (c == '/' && next == '*') {
                masked[i] = masked[++i] = ' ';
                blockComment = true;
            } else if (c == '"' || c == '\'') {
                masked[i] = ' ';
                quote = c;
            }
        }
        return new String(masked);
    }
}
