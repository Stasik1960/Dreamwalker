package com.coraxberg.rpchatui.client;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** Prefer explicit message metadata; never guess an author by splitting prose at a colon. */
final class ChatMessageContent {
    static String body(Text message) {
        String insertion = message.getStyle().getInsertion();
        if (insertion != null && insertion.startsWith("rpchat:body:")) return insertion.substring(12);
        if (message.getContent() instanceof TranslatableTextContent translated
                && (translated.getKey().equals("chat.type.text") || translated.getKey().equals("chat.type.emote"))
                && translated.getArgs().length > 1) {
            Object body = translated.getArgs()[1];
            return body instanceof Text text ? text.getString() : String.valueOf(body);
        }
        // Older RP Chat and radio messages use a separate ": " sibling.
        boolean afterName = false;
        StringBuilder body = new StringBuilder();
        for (Text sibling : message.getSiblings()) {
            if (afterName) body.append(sibling.getString());
            else if (sibling.getString().equals(": ")) afterName = true;
        }
        return afterName ? body.toString() : message.getString();
    }
}
