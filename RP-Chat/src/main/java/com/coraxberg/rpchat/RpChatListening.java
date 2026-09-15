package com.coraxberg.rpchat;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import java.util.Optional;

final class RpChatListening {
    static boolean receives(boolean sameWorld, double squaredDistance, int messageRadius,
                            boolean listening, boolean all, int listenRadius) {
        if (listening && all) return true;
        int radius = listening ? listenRadius : messageRadius;
        return sameWorld && (radius < 0 || squaredDistance <= (double) radius * radius);
    }

    static Text distant(Text original, Text nickname, String distance) {
        String plain = original.getString();
        String name = nickname.getString();
        int start = plain.startsWith(name) ? 0 : plain.startsWith("*" + name) ? 1 : -1;
        MutableText result = Text.empty();
        String marker = " (" + distance + ")";
        if (start < 0) result.append(nickname.copy()).append(Text.literal(marker + " ").formatted(Formatting.DARK_GRAY));
        int end = start < 0 ? -1 : start + name.length();
        int[] offset = {0};
        original.visit((style, part) -> {
            for (int i = 0; i < part.length();) {
                int size = Character.charCount(part.codePointAt(i));
                int position = offset[0] + i;
                result.append(Text.literal(part.substring(i, i + size)).setStyle(
                        position >= start && position < end ? style : style.withColor(Formatting.DARK_GRAY)));
                if (position + size == end) result.append(Text.literal(marker).formatted(Formatting.DARK_GRAY));
                i += size;
            }
            offset[0] += part.length();
            return Optional.empty();
        }, Style.EMPTY);
        return result.setStyle(result.getStyle().withInsertion(original.getStyle().getInsertion()));
    }
}
