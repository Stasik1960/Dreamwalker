package com.coraxberg.rpchat;

import java.util.function.IntUnaryOperator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

/** Pure parsing helpers shared by the server pipeline and its narrow checks. */
final class RpChatSyntax {
    static final int MAX_DICE_COUNT = 100;
    static final int MAX_DIE_SIDES = 100;
    static final int MAX_DICE_MODIFIER = 100_000;

    private static final Pattern DICE_PATTERN = Pattern.compile("(?i)^(\\d{0,3})d(\\d{1,3})([+-]\\d{1,6})?$");

    private RpChatSyntax() {
    }

    static ClassifiedMessage classify(String message) {
        if (message.startsWith("-") && message.length() > 1) {
            return new ClassifiedMessage(MessageKind.GM, message.substring(1).strip(), null);
        }

        if (message.startsWith("*")) {
            String action = message.substring(1).strip();
            if (action.endsWith("*")) action = action.substring(0, action.length() - 1).strip();
            return new ClassifiedMessage(MessageKind.ACTION, action, null);
        }

        if (message.startsWith("#") || message.startsWith("№")) {
            return new ClassifiedMessage(MessageKind.NARRATION, message.substring(1).strip(), null);
        }

        DiceExpression dice = parseDice(message);
        if (dice != null) return new ClassifiedMessage(MessageKind.DICE, message, dice);
        return new ClassifiedMessage(MessageKind.SPEECH, message, null);
    }

    static int leadingModifierLength(String raw) {
        int index = 0;
        while (index < raw.length()) {
            char current = raw.charAt(index);
            if (current == '=') {
                while (index < raw.length() && raw.charAt(index) == '=') index++;
                continue;
            }
            if (current == '!') {
                while (index < raw.length() && raw.charAt(index) == '!') index++;
                continue;
            }
            if (current == '_') {
                index++;
                continue;
            }
            break;
        }
        return index;
    }

    static boolean containsControlCharacters(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.getType(codePoint) == Character.CONTROL);
    }

    static boolean isAllowedCustomCommand(String raw) {
        if (!raw.startsWith("/")) return false;
        int separator = raw.indexOf(' ');
        if (separator < 2 || separator == raw.length() - 1 || raw.substring(separator + 1).isBlank()) return false;
        String command = raw.substring(1, separator).toLowerCase(Locale.ROOT);
        return command.equals("m") || command.equals("r") || command.equals("roll")
                || command.equals("me") || command.equals("rename");
    }

    static DiceExpression parseDice(String raw) {
        Matcher matcher = DICE_PATTERN.matcher(raw);
        if (!matcher.matches()) return null;

        int count = matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
        int sides = Integer.parseInt(matcher.group(2));
        int modifier = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        if (count < 1 || count > MAX_DICE_COUNT || sides < 2 || sides > MAX_DIE_SIDES
                || Math.abs((long) modifier) > MAX_DICE_MODIFIER) {
            return null;
        }

        String notation = (count == 1 ? "" : Integer.toString(count)) + "d" + sides;
        if (modifier > 0) notation += "+" + modifier;
        if (modifier < 0) notation += modifier;
        return new DiceExpression(count, sides, modifier, notation);
    }

    static RollResult roll(DiceExpression dice, IntUnaryOperator nextRoll) {
        int total = dice.modifier();
        List<Integer> rolls = new ArrayList<>(dice.count());
        for (int index = 0; index < dice.count(); index++) {
            int value = nextRoll.applyAsInt(dice.sides());
            if (value < 1 || value > dice.sides()) {
                throw new IllegalArgumentException("Roll outside die range: " + value);
            }
            rolls.add(value);
            total += value;
        }
        return new RollResult(List.copyOf(rolls), total);
    }

    static String formatRoll(DiceExpression dice, RollResult roll) {
        String list = roll.rolls().toString();
        StringBuilder result = new StringBuilder("бросает ")
                .append(dice.notation().replace('d', 'д'))
                .append(", выпадает: ")
                .append('(')
                .append(list, 1, list.length() - 1)
                .append(')');
        if (dice.modifier() > 0) result.append(" + ").append(dice.modifier());
        if (dice.modifier() < 0) result.append(" - ").append(Math.abs(dice.modifier()));
        return result.append(" = ").append(roll.total()).toString();
    }

    enum MessageKind {
        SPEECH,
        ACTION,
        NARRATION,
        GM,
        DICE
    }

    record ClassifiedMessage(MessageKind kind, String body, DiceExpression dice) {
        boolean firesLocalIcEvent() {
            return kind == MessageKind.SPEECH;
        }
    }

    record DiceExpression(int count, int sides, int modifier, String notation) {
    }

    record RollResult(List<Integer> rolls, int total) {
    }
}
