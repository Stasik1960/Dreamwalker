package com.coraxberg.rpchat;

import net.minecraft.text.MutableText;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class RpChatServerLogicCheck {
    private RpChatServerLogicCheck() {
    }

    public static void main(String[] args) {
        checkClassification();
        checkDice();
        require(RpChatSyntax.parseDice("5д20+3").equals(RpChatSyntax.parseDice("5d20+3")), "Russian alias");
        require(RpChatSyntax.parseDice("Д20") != null, "uppercase Russian alias");
        require(!RpChatListening.receives(true, 16, 18, true, false, 3), "listen limits normal speech");
        require(RpChatListening.receives(true, 9, 18, true, false, 3), "inclusive boundary");
        require(RpChatListening.receives(true, 10000, 3, true, false, 100), "listen expands whisper");
        require(!RpChatListening.receives(true, 10201, 500, true, false, 100), "listen limits shout");
        require(!RpChatListening.receives(false, 1, 18, true, false, 100), "dimension boundary");

        checkFormattingIsolation();
        var distant = RpChatListening.distant(
                net.minecraft.text.Text.literal("Nick").formatted(Formatting.GREEN)
                    .append(net.minecraft.text.Text.literal(": hello").formatted(Formatting.RED)),
                net.minecraft.text.Text.literal("Nick").formatted(Formatting.GREEN), "20.0 блоков");
        require(distant.getString().equals("Nick (20.0 блоков): hello"), "distance follows nickname");
        distant.visit((style, part) -> {
            if (part.contains("h")) require(style.getColor().equals(TextColor.fromFormatting(Formatting.DARK_GRAY)), "distant body dark gray");
            if (part.equals("N")) require(style.getColor().equals(TextColor.fromFormatting(Formatting.GREEN)), "nickname color retained");
            return java.util.Optional.empty();
        }, net.minecraft.text.Style.EMPTY);

        checkSubmissionGuard();
        checkLanguageIntegration();
        System.out.println("RP Chat server logic checks passed.");
    }

    private static void checkLanguageIntegration() {
        var unchanged = com.coraxberg.rpchat.api.RpChatEvents.prepareIcBody(null, "hello");
        require(unchanged.forRecipient(null).equals("hello"), "no language mod preserves original");
        var knows = new java.util.concurrent.atomic.AtomicBoolean(false);
        var preparations = new AtomicInteger();
        com.coraxberg.rpchat.api.RpChatEvents.PREPARE_IC_BODY.register((sender, original, previous) -> {
            preparations.incrementAndGet();
            return recipient -> "[Orc] " + (knows.get() ? previous.forRecipient(recipient) : "gruk");
        });
        var prepared = com.coraxberg.rpchat.api.RpChatEvents.prepareIcBody(null, "secret");
        var event = new com.coraxberg.rpchat.api.RpChatEvents.LocalIcMessage(null, "secret", 18, "", prepared);
        require(event.body().forRecipient(null).equals("[Orc] gruk"), "radio receives concealed speech");
        knows.set(true);
        require(event.body().forRecipient(null).equals("[Orc] secret"), "known language uses original");
        knows.set(false);
        require(prepared.forRecipient(null).equals("[Orc] gruk"), "listeners do not share knowledge");
        require(preparations.get() == 1, "local and radio share one preparation");

        com.coraxberg.rpchat.api.RpChatEvents.PREPARE_IC_BODY.register((sender, original, previous) -> {
            return recipient -> { throw new IllegalStateException("intentional recipient failure"); };
        });
        String failed = com.coraxberg.rpchat.api.RpChatEvents.prepareIcBody(null, "secret").forRecipient(null);
        require(!failed.contains("secret"), "failure never reveals original speech");
        com.coraxberg.rpchat.api.RpChatEvents.CHAT_CONTROL.register((sender, raw) -> raw.equals("+orc"));
        require(com.coraxberg.rpchat.api.RpChatEvents.CHAT_CONTROL.invoker().handle(null, "+orc"), "language control consumed");
        require(!com.coraxberg.rpchat.api.RpChatEvents.CHAT_CONTROL.invoker().handle(null, "ordinary speech"), "ordinary chat preserved");
    }

    private static void checkClassification() {
        assertClassified("*машет", RpChatSyntax.MessageKind.ACTION, "машет");
        assertClassified("*машет*", RpChatSyntax.MessageKind.ACTION, "машет");
        assertClassified("#ночь сгущается", RpChatSyntax.MessageKind.NARRATION, "ночь сгущается");
        assertClassified("-секрет GM", RpChatSyntax.MessageKind.GM, "секрет GM");
        assertClassified("d20", RpChatSyntax.MessageKind.DICE, "d20");

        String volumeAction = "!!*кричит и машет*";
        int prefixLength = RpChatSyntax.leadingModifierLength(volumeAction);
        require(prefixLength == 2, "volume prefix length");
        RpChatSyntax.ClassifiedMessage classified = RpChatSyntax.classify(volumeAction.substring(prefixLength));
        require(classified.kind() == RpChatSyntax.MessageKind.ACTION, "volume-prefixed action kind");
        require("кричит и машет".equals(classified.body()), "volume-prefixed action body");

        for (String special : new String[]{"*action", "#narration", "-gm", "5d20+3"}) {
            require(!RpChatSyntax.classify(special).firesLocalIcEvent(), special + " must not fire local speech event");
        }
        require(RpChatSyntax.classify("обычная речь").firesLocalIcEvent(), "ordinary speech event");
        require(RpChatSyntax.parseDice("обычный ответ") == null, "/r non-dice remains reply");
    }

    private static void checkDice() {
        require(RpChatSyntax.parseDice("d2") != null, "d2 boundary");
        require(RpChatSyntax.parseDice("d100") != null, "d100 boundary");
        require(RpChatSyntax.parseDice("100d100-100000") != null, "upper dice boundaries");
        for (String invalid : new String[]{"d1", "d101", "0d20", "101d20", "d20+100001", "1000d20"}) {
            require(RpChatSyntax.parseDice(invalid) == null, "invalid dice accepted: " + invalid);
        }

        RpChatSyntax.DiceExpression dice = RpChatSyntax.parseDice("5d20+3");
        int[] values = {5, 20, 1, 13, 7};
        AtomicInteger index = new AtomicInteger();
        RpChatSyntax.RollResult result = RpChatSyntax.roll(dice, sides -> values[index.getAndIncrement()]);
        require(result.rolls().toString().equals("[5, 20, 1, 13, 7]"), "individual roll list");
        require(result.total() == 49, "5d20+3 deterministic total");
        require(RpChatSyntax.formatRoll(dice, result).equals(
                "бросает 5d20+3, выпадает: (5, 20, 1, 13, 7) + 3 = 49"), "exact roll output");
    }

    private static void checkFormattingIsolation() {
        MutableText nickname = RpChatTextParser.parse("&aNick", Formatting.GREEN, false);
        MutableText body = RpChatTextParser.parse("&cкрасный&r белый", Formatting.WHITE, false);
        nickname.append(Formatting.WHITE + ": ").append(body);

        require(colorName(nickname).equals("green"), "nickname color changed by message body");
        require(colorName(body).equals("red"), "message color code");
        require(body.getSiblings().size() == 1, "message reset segment");
        require(colorName(body.getSiblings().get(0)).equals("white"), "message reset restores body default");
        MutableText styled = RpChatTextParser.parse("&lжирный&r обычный", Formatting.WHITE, false);
        require(styled.getStyle().isBold(), "message style code");
        require(!styled.getSiblings().get(0).getStyle().isBold(), "style reset");
        require(RpChatTextParser.plain("&cкрасный&r белый").equals("красный белый"), "plain copy body");
    }

    private static void checkSubmissionGuard() {
        require(RpChatSyntax.containsControlCharacters("строка\nдругая"), "newline control rejection");
        require(!RpChatSyntax.containsControlCharacters("обычный текст §a и emoji 😀"), "valid unicode accepted");
        for (String allowed : new String[]{"/m Player текст", "/r ответ", "/roll d20", "/me машет", "/rename Player Ник"}) {
            require(RpChatSyntax.isAllowedCustomCommand(allowed), "allowed custom command rejected: " + allowed);
        }
        for (String rejected : new String[]{"/op Player", "/execute run say hi", "/rpchat reload", "/roll", "/me   "}) {
            require(!RpChatSyntax.isAllowedCustomCommand(rejected), "unsafe custom command accepted: " + rejected);
        }

        RpChatSubmissionLimiter limiter = new RpChatSubmissionLimiter(2.0, 2.0);
        UUID player = UUID.randomUUID();
        require(limiter.tryAcquire(player, 0L), "first burst token");
        require(limiter.tryAcquire(player, 0L), "second burst token");
        require(!limiter.tryAcquire(player, 0L), "burst limit");
        require(limiter.tryAcquire(player, 500_000_000L), "token refill");
        limiter.remove(player);
        require(limiter.trackedPlayers() == 0, "disconnect cleanup");
    }

    private static void assertClassified(String input, RpChatSyntax.MessageKind kind, String body) {
        RpChatSyntax.ClassifiedMessage classified = RpChatSyntax.classify(input);
        require(classified.kind() == kind, input + " kind");
        require(classified.body().equals(body), input + " body");
    }

    private static String colorName(net.minecraft.text.Text text) {
        TextColor color = text.getStyle().getColor();
        return color == null ? "" : color.getName();
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
