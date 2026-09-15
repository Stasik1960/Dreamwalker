package com.coraxberg.rpchatui.client;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatContentCheck {
    public static void main(String[] args) {
        Text message = Text.empty().append(Text.literal("Кура").formatted(Formatting.RED))
                .append(Text.literal(": ")).append(Text.literal("жёлтый текст").formatted(Formatting.YELLOW))
                .styled(style -> style.withInsertion("rpchat:body:жёлтый текст"));
        Text decoded = Text.Serializer.fromJson(Text.Serializer.toJson(message));
        require(ChatMessageContent.body(decoded).equals("жёлтый текст"), "Copy includes nickname");
        require(decoded.getSiblings().get(0).getStyle().getColor().getRgb() == Formatting.RED.getColorValue(), "Nickname color lost");
        require(decoded.getSiblings().get(2).getStyle().getColor().getRgb() == Formatting.YELLOW.getColorValue(), "Body color lost");
        require(ChatMessageContent.body(Text.translatable("chat.type.text", Text.literal("Player"), Text.literal("body"))).equals("body"),
                "Vanilla chat author was copied");
        require(ChatMessageContent.body(Text.literal("Ошибка: подробности")).equals("Ошибка: подробности"), "System prose was truncated");
        require(ChatMessageContent.body(Text.literal("[Рация] ").append(Text.literal("Player")).append(Text.literal(": "))
                .append(Text.literal("hello: world"))).equals("hello: world"), "Legacy radio author was copied");
        System.out.println("Chat content checks passed: serialized colors, copy body, vanilla chat, radio, system text.");
    }
    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
