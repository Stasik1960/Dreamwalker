package com.coraxberg.dwlanguages;

import java.util.*;
import java.util.regex.*;

/** Bounded SCA-style rules. Literal syntax is escaped, never interpreted as arbitrary Java regex. */
final class SoundRule {
    private final Pattern target, left, right, exceptLeft, exceptRight;
    private final String replacement;
    private final Map<Integer, String> sourceGroups = new LinkedHashMap<>();
    private final Map<Integer, String> categories;

    SoundRule(String rule, Map<Integer, String> categories) {
        this.categories = categories;
        if (rule == null || rule.length() > 128) throw new IllegalArgumentException("Правило слишком длинное");
        String[] parts = rule.split("/", -1);
        if (parts.length < 3 || parts.length > 4) throw new IllegalArgumentException("Ожидается X/Y/L_R: " + rule);
        replacement = parts[1];
        StringBuilder regex = new StringBuilder();
        int group = 0;
        int[] points = parts[0].codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            int cp = points[i];
            if (categories.containsKey(cp)) {
                sourceGroups.put(++group, categories.get(cp));
                regex.append('(').append(characterClass(categories.get(cp))).append(')');
                if (i + 1 < points.length && points[i + 1] == '²') {
                    regex.append('\\').append(group); i++;
                }
            } else if (cp == '[') {
                StringBuilder list = new StringBuilder();
                while (++i < points.length && points[i] != ']') list.appendCodePoint(points[i]);
                if (i == points.length || list.isEmpty()) throw new IllegalArgumentException("Незакрытый/пустой список: " + rule);
                regex.append(characterClass(expandList(list.toString(), categories)));
            } else {
                rejectSpecial(cp);
                regex.append(Pattern.quote(Character.toString(cp)));
            }
        }
        target = Pattern.compile(regex.toString());
        String[] context = context(parts[2]);
        left = Pattern.compile(contextRegex(context[0], categories, true) + "$");
        right = Pattern.compile("^" + contextRegex(context[1], categories, false));
        if (parts.length == 4) {
            String[] except = context(parts[3]);
            exceptLeft = Pattern.compile(contextRegex(except[0], categories, true) + "$");
            exceptRight = Pattern.compile("^" + contextRegex(except[1], categories, false));
        } else { exceptLeft = null; exceptRight = null; }
        for (int cp : replacement.codePoints().toArray()) {
            if (Character.isUpperCase(cp) && !categories.containsKey(cp)) {
                throw new IllegalArgumentException("Неизвестная категория в замене: " + Character.toString(cp));
            }
            if (categories.containsKey(cp) && sourceGroups.isEmpty()) {
                throw new IllegalArgumentException("Замена категорией требует категории в цели: " + rule);
            }
        }
    }

    String apply(String word) {
        Matcher matcher = target.matcher(word);
        StringBuilder result = new StringBuilder();
        int copied = 0;
        while (matcher.find()) {
            int start = matcher.start(), end = matcher.end();
            // A zero-width insertion may not split a supplementary Unicode character.
            if (start > 0 && start < word.length() && Character.isLowSurrogate(word.charAt(start))) continue;
            String before = word.substring(0, start), after = word.substring(end);
            if (!left.matcher(before).find() || !right.matcher(after).find()) continue;
            if (exceptLeft != null && exceptLeft.matcher(before).find() && exceptRight.matcher(after).find()) continue;
            result.append(word, copied, start).append(replace(matcher));
            copied = end;
            if (result.length() > 256) throw new IllegalArgumentException("Результат правила длиннее 256 символов");
        }
        result.append(word.substring(copied));
        if (result.length() > 256) throw new IllegalArgumentException("Слово длиннее 256 символов");
        return result.toString();
    }

    private String replace(Matcher match) {
        if (replacement.equals("\\") || replacement.equals("\\\\")) {
            int[] cps = match.group().codePoints().toArray();
            StringBuilder reversed = new StringBuilder();
            for (int i = cps.length - 1; i >= 0; i--) reversed.appendCodePoint(cps[i]);
            return reversed.toString();
        }
        StringBuilder result = new StringBuilder();
        List<Integer> groups = new ArrayList<>(sourceGroups.keySet());
        int next = 0;
        String previous = "";
        for (int cp : replacement.codePoints().toArray()) {
            if (cp == '²') { result.append(previous); continue; }
            if (categories.containsKey(cp)) {
                int g = groups.get(Math.min(next++, groups.size() - 1));
                int index = codePointIndex(sourceGroups.get(g), match.group(g).codePointAt(0));
                int[] values = categories.get(cp).codePoints().toArray();
                previous = index >= 0 && index < values.length ? Character.toString(values[index]) : "";
            } else previous = Character.toString(cp);
            result.append(previous);
        }
        return result.toString();
    }

    private static int codePointIndex(String values, int wanted) {
        int[] cps = values.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) if (cps[i] == wanted) return i;
        return -1;
    }

    private static String[] context(String value) {
        String[] pair = value.split("_", -1);
        if (pair.length != 2) throw new IllegalArgumentException("В окружении нужен один _: " + value);
        return pair;
    }

    private static String contextRegex(String value, Map<Integer, String> categories, boolean leftSide) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        int[] cps = value.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            if (cp == '#') out.append(leftSide ? "^" : "$");
            else if (cp == '…') out.append(".*");
            else if (cp == '(') {
                if (++depth > 1) throw new IllegalArgumentException("Вложенные скобки не поддерживаются");
                out.append("(?:");
            } else if (cp == ')') {
                if (--depth < 0) throw new IllegalArgumentException("Лишняя скобка");
                out.append(")?");
            } else if (cp == '[') {
                StringBuilder list = new StringBuilder();
                while (++i < cps.length && cps[i] != ']') list.appendCodePoint(cps[i]);
                if (i == cps.length || list.isEmpty()) throw new IllegalArgumentException("Незакрытый/пустой список");
                out.append(characterClass(expandList(list.toString(), categories)));
            } else if (categories.containsKey(cp)) out.append(characterClass(categories.get(cp)));
            else { rejectSpecial(cp); out.append(Pattern.quote(Character.toString(cp))); }
        }
        if (depth != 0) throw new IllegalArgumentException("Незакрытая скобка");
        return out.toString();
    }

    private static String expandList(String list, Map<Integer, String> categories) {
        StringBuilder values = new StringBuilder();
        list.codePoints().forEach(cp -> values.append(categories.getOrDefault(cp, Character.toString(cp))));
        return values.toString();
    }

    private static String characterClass(String chars) {
        StringJoiner alternatives = new StringJoiner("|", "(?:", ")");
        chars.codePoints().forEach(cp -> alternatives.add(Pattern.quote(Character.toString(cp))));
        return alternatives.toString();
    }

    private static void rejectSpecial(int cp) {
        if (Character.isUpperCase(cp) || "²[]()\\_#…".indexOf(cp) >= 0) {
            throw new IllegalArgumentException("Неизвестная категория или недопустимый символ: " + Character.toString(cp));
        }
    }
}
