package com.coraxberg.rpchat;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;

/** Parses legacy ampersand/section formatting into styled Text siblings. */
final class RpChatTextParser {
    private RpChatTextParser() {
    }

    static MutableText parse(String raw, Formatting defaultColor, boolean includeLegacyCodes) {
        MutableText result = null;
        StringBuilder segment = new StringBuilder();

        Formatting currentColor = defaultColor;
        boolean obfuscated = false;
        boolean bold = false;
        boolean strikethrough = false;
        boolean underline = false;
        boolean italic = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < raw.length()) {
                Formatting formatting = legacyFormatting(raw.charAt(i + 1));
                if (formatting != null) {
                    result = appendStyledSegment(result, segment, currentColor, obfuscated, bold,
                            strikethrough, underline, italic, includeLegacyCodes);

                    if (formatting == Formatting.RESET) {
                        currentColor = defaultColor;
                        obfuscated = false;
                        bold = false;
                        strikethrough = false;
                        underline = false;
                        italic = false;
                    } else if (formatting.isColor()) {
                        currentColor = formatting;
                        obfuscated = false;
                        bold = false;
                        strikethrough = false;
                        underline = false;
                        italic = false;
                    } else {
                        switch (formatting) {
                            case OBFUSCATED -> obfuscated = true;
                            case BOLD -> bold = true;
                            case STRIKETHROUGH -> strikethrough = true;
                            case UNDERLINE -> underline = true;
                            case ITALIC -> italic = true;
                            default -> {
                            }
                        }
                    }

                    i++;
                    continue;
                }
            }
            segment.append(c);
        }

        result = appendStyledSegment(result, segment, currentColor, obfuscated, bold,
                strikethrough, underline, italic, includeLegacyCodes);
        if (result == null) result = Text.empty().formatted(defaultColor);
        if (includeLegacyCodes) result.append(Text.literal("§r").formatted(Formatting.RESET));
        return result;
    }

    static String plain(String raw) {
        StringBuilder plain = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if ((current == '&' || current == '§') && i + 1 < raw.length()
                    && legacyFormatting(raw.charAt(i + 1)) != null) {
                i++;
                continue;
            }
            plain.append(current);
        }
        return plain.toString();
    }

    private static MutableText appendStyledSegment(MutableText result, StringBuilder segment, Formatting color,
                                                    boolean obfuscated, boolean bold, boolean strikethrough,
                                                    boolean underline, boolean italic, boolean includeLegacyCodes) {
        if (segment.isEmpty()) return result;
        ArrayList<Formatting> formats = new ArrayList<>();
        if (color != null) formats.add(color);
        if (obfuscated) formats.add(Formatting.OBFUSCATED);
        if (bold) formats.add(Formatting.BOLD);
        if (strikethrough) formats.add(Formatting.STRIKETHROUGH);
        if (underline) formats.add(Formatting.UNDERLINE);
        if (italic) formats.add(Formatting.ITALIC);

        String prefix = includeLegacyCodes
                ? legacyCodes(color, obfuscated, bold, strikethrough, underline, italic)
                : "";
        MutableText part = Text.literal(prefix + segment).formatted(formats.toArray(new Formatting[0]));
        segment.setLength(0);
        return result == null ? part : result.append(part);
    }

    private static String legacyCodes(Formatting color, boolean obfuscated, boolean bold, boolean strikethrough,
                                      boolean underline, boolean italic) {
        StringBuilder codes = new StringBuilder();
        Character colorCode = legacyCode(color);
        if (colorCode != null) codes.append('§').append(colorCode);
        if (obfuscated) codes.append("§k");
        if (bold) codes.append("§l");
        if (strikethrough) codes.append("§m");
        if (underline) codes.append("§n");
        if (italic) codes.append("§o");
        return codes.toString();
    }

    private static Formatting legacyFormatting(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> Formatting.BLACK; case '1' -> Formatting.DARK_BLUE;
            case '2' -> Formatting.DARK_GREEN; case '3' -> Formatting.DARK_AQUA;
            case '4' -> Formatting.DARK_RED; case '5' -> Formatting.DARK_PURPLE;
            case '6' -> Formatting.GOLD; case '7' -> Formatting.GRAY;
            case '8' -> Formatting.DARK_GRAY; case '9' -> Formatting.BLUE;
            case 'a' -> Formatting.GREEN; case 'b' -> Formatting.AQUA;
            case 'c' -> Formatting.RED; case 'd' -> Formatting.LIGHT_PURPLE;
            case 'e' -> Formatting.YELLOW; case 'f' -> Formatting.WHITE;
            case 'k' -> Formatting.OBFUSCATED; case 'l' -> Formatting.BOLD;
            case 'm' -> Formatting.STRIKETHROUGH; case 'n' -> Formatting.UNDERLINE;
            case 'o' -> Formatting.ITALIC; case 'r' -> Formatting.RESET;
            default -> null;
        };
    }

    private static Character legacyCode(Formatting formatting) {
        if (formatting == null) return null;
        return switch (formatting) {
            case BLACK -> '0'; case DARK_BLUE -> '1'; case DARK_GREEN -> '2'; case DARK_AQUA -> '3';
            case DARK_RED -> '4'; case DARK_PURPLE -> '5'; case GOLD -> '6'; case GRAY -> '7';
            case DARK_GRAY -> '8'; case BLUE -> '9'; case GREEN -> 'a'; case AQUA -> 'b';
            case RED -> 'c'; case LIGHT_PURPLE -> 'd'; case YELLOW -> 'e'; case WHITE -> 'f';
            case OBFUSCATED -> 'k'; case BOLD -> 'l'; case STRIKETHROUGH -> 'm';
            case UNDERLINE -> 'n'; case ITALIC -> 'o'; case RESET -> 'r';
        };
    }
}
