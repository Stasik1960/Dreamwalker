package com.coraxberg.dwlanguages;

import com.google.gson.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Immutable validated language definition with a bounded per-language word cache. */
final class Language {
    private static final Pattern TOKEN = Pattern.compile(
            "https?://[^\\s]+|[@#][\\p{L}\\p{M}\\p{Nd}_]+|[&§][0-9a-fk-orA-FK-OR]|[\\p{L}\\p{M}\\p{Nd}]+", Pattern.UNICODE_CHARACTER_CLASS);
    final String code, name;
    final boolean enabled;
    private final String salt;
    private final Normalizer.Form normalization;
    private final Map<Integer, String> categories = new LinkedHashMap<>();
    private final List<String> syllables;
    private final List<SoundRule> rules = new ArrayList<>();
    private final Map<String, String> rewriteIn, orthography;
    private final Set<String> exceptions;
    private final int maxSyllables;
    private final Map<String, String> cache = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> entry) { return size() > 8192; }
    };

    Language(JsonObject json) {
        code = required(json, "locale");
        if (!code.matches("[a-z][a-z0-9_-]{0,31}") || Set.of("off", "common", "list", "reload", "add", "del").contains(code)) {
            throw new IllegalArgumentException("Недопустимый/зарезервированный locale: " + code);
        }
        name = required(json, "name");
        if (name.length() > 64 || name.matches(".*[\\p{Cntrl}§&\\[\\]].*")) throw new IllegalArgumentException("Недопустимое имя языка");
        enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
        salt = json.has("salt") ? json.get("salt").getAsString() : code;
        normalization = Normalizer.Form.valueOf(json.has("unicode_normalization") ? json.get("unicode_normalization").getAsString() : "NFC");
        if (normalization != Normalizer.Form.NFC && normalization != Normalizer.Form.NFD) throw new IllegalArgumentException("Только NFC/NFD");
        JsonObject cats = json.getAsJsonObject("categories");
        if (cats == null || cats.size() == 0 || cats.size() > 26) throw new IllegalArgumentException("Нужны categories");
        for (Map.Entry<String, JsonElement> entry : cats.entrySet()) {
            if (!entry.getKey().matches("[A-Z]")) throw new IllegalArgumentException("Категория должна быть одной латинской заглавной буквой");
            String values = entry.getValue().getAsString();
            if (values.isEmpty() || values.codePointCount(0, values.length()) > 128 || values.codePoints().anyMatch(cp -> !Character.isLetter(cp) && Character.getType(cp) != Character.NON_SPACING_MARK)) {
                throw new IllegalArgumentException("Категории содержат только буквы/диакритику, 1–128 символов");
            }
            categories.put(entry.getKey().codePointAt(0), values);
        }
        syllables = strings(json, "syllables");
        if (syllables.isEmpty() || syllables.size() > 32) throw new IllegalArgumentException("Нужно 1–32 шаблона syllables");
        for (String template : syllables) {
            if (template.isEmpty() || template.length() > 16) throw new IllegalArgumentException("Длина шаблона: 1–16");
            for (int cp : template.codePoints().toArray()) {
                if (!categories.containsKey(cp) && (!Character.isLetter(cp) || Character.isUpperCase(cp))) throw new IllegalArgumentException("Неизвестная категория в syllables");
            }
        }
        maxSyllables = json.has("max_syllables") ? json.get("max_syllables").getAsInt() : 4;
        if (maxSyllables < 1 || maxSyllables > 8) throw new IllegalArgumentException("max_syllables: 1–8");
        List<String> ruleStrings = strings(json, "rules");
        if (ruleStrings.size() > 128) throw new IllegalArgumentException("Не больше 128 правил");
        for (String rule : ruleStrings) rules.add(new SoundRule(rule, categories));
        rewriteIn = replacements(json, "rewrite_in");
        orthography = replacements(json, "orthography");
        exceptions = new HashSet<>();
        for (String word : strings(json, "exceptions")) exceptions.add(normalize(word));
        // Reject pathological growth in ordinary generated samples before installing the registry.
        for (int i = 0; i < 64; i++) transformWord("проверка" + i);
        cache.clear();
    }

    String transform(String text) {
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder output = new StringBuilder();
        int copied = 0;
        while (matcher.find()) {
            String token = matcher.group();
            String lower = token.toLowerCase(Locale.ROOT);
            boolean skip = lower.startsWith("http://") || lower.startsWith("https://") || token.startsWith("@") || token.startsWith("#")
                    || token.startsWith("&") || token.startsWith("§") || token.codePoints().allMatch(Character::isDigit)
                    || exceptions.contains(normalize(token));
            output.append(text, copied, matcher.start()).append(skip ? token : transformWord(token));
            copied = matcher.end();
            if (output.length() > 30000) throw new IllegalArgumentException("Сообщение после преобразования слишком длинное");
        }
        output.append(text.substring(copied));
        return output.toString();
    }

    String transformWord(String original) {
        String word = normalize(original);
        String transformed;
        synchronized (cache) { transformed = cache.get(word); }
        if (transformed == null) {
            Random random = new Random(seed(code + "\0" + salt + "\0" + word));
            int count = Math.max(1, Math.min(maxSyllables, (word.codePointCount(0, word.length()) + 2) / 3));
            StringBuilder generated = new StringBuilder();
            for (int i = 0; i < count; i++) {
                String template = syllables.get(random.nextInt(syllables.size()));
                for (int cp : template.codePoints().toArray()) {
                    String choices = categories.get(cp);
                    if (choices == null) generated.appendCodePoint(cp);
                    else {
                        int[] values = choices.codePoints().toArray();
                        generated.appendCodePoint(values[random.nextInt(values.length)]);
                    }
                }
            }
            transformed = replace(generated.toString(), rewriteIn);
            for (SoundRule rule : rules) transformed = rule.apply(transformed);
            transformed = replace(transformed, orthography);
            if (transformed.isEmpty()) transformed = "…";
            synchronized (cache) { cache.put(word, transformed); }
        }
        boolean hasLetters = original.codePoints().anyMatch(Character::isLetter);
        if (hasLetters && original.equals(original.toUpperCase(Locale.ROOT))) return transformed.toUpperCase(Locale.ROOT);
        if (!original.isEmpty() && Character.isUpperCase(original.codePointAt(0))) {
            int first = transformed.offsetByCodePoints(0, 1);
            return transformed.substring(0, first).toUpperCase(Locale.ROOT) + transformed.substring(first);
        }
        return transformed;
    }

    private String normalize(String word) { return Normalizer.normalize(word.toLowerCase(Locale.ROOT), normalization); }
    private static long seed(String text) {
        try { return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))).getLong(); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String replace(String word, Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            word = word.replace(entry.getKey(), entry.getValue());
            if (word.length() > 256) throw new IllegalArgumentException("Слово длиннее 256 символов");
        }
        return word;
    }
    private static Map<String, String> replacements(JsonObject json, String key) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!json.has(key)) return result;
        JsonObject map = json.getAsJsonObject(key);
        if (map.size() > 128) throw new IllegalArgumentException("Слишком много замен " + key);
        for (Map.Entry<String, JsonElement> e : map.entrySet()) {
            String value = e.getValue().getAsString();
            if (e.getKey().isEmpty() || e.getKey().length() > 32 || value.length() > 32 || value.matches(".*[\\p{Cntrl}§&].*")) throw new IllegalArgumentException("Недопустимая замена " + key);
            result.put(e.getKey(), value);
        }
        return result;
    }
    static String required(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).getAsString().isBlank()) throw new IllegalArgumentException("Отсутствует " + key);
        return json.get(key).getAsString();
    }
    private static List<String> strings(JsonObject json, String key) {
        List<String> result = new ArrayList<>();
        if (json.has(key)) for (JsonElement e : json.getAsJsonArray(key)) result.add(e.getAsString());
        return result;
    }
}
