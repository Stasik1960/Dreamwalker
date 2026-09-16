package com.coraxberg.dwlanguages;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LanguageCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Map<Integer, String> cats = Map.of((int)'V', "aeiou", (int)'S', "ptc", (int)'Z', "bdg", (int)'F', "ie", (int)'C', "ptkbdg", (int)'M', "mn");
        rule("c/g/V_V", "aca", "aga", cats);
        rule("[sm]//_#", "focus", "focu", cats);
        rule("/j/_kt", "akta", "ajkta", cats);
        rule("nt/\\/_V", "anta", "atna", cats);
        rule("k/s/_[ie]", "kika", "sika", cats);
        rule("k/s/_F/#s_", "skie", "skie", cats);
        rule("k/s/_F/#s_", "akie", "asie", cats);
        rule("S/Z/_…V", "pxta", "bxda", cats);
        rule("S/Z/V_V", "apata", "abada", cats);
        rule("Z/S/_", "bdg", "ptc", cats);
        rule("S/V/_", "ptc", "aei", cats);
        rule("V/S/_", "aeiou", "ptc", cats);
        rule("k/s/#_", "kak", "sak", cats);
        rule("u/o/_#", "ulu", "ulo", cats);
        rule("M/M²/_", "mana", "mmanna", cats);
        rule("M²/M/_", "mmnmnnaa", "mnmnaa", cats);
        rule("u/o/_C(C)F", "upti", "opti", cats);
        rule("u/o/_C(C)F", "uti", "oti", cats);
        rule("/j/#_", "kt", "jkt", cats);
        expectFailure(() -> new SoundRule("k/s/no-position", cats), "invalid rule rejected");
        expectFailure(() -> new SoundRule("Q/a/_", cats), "unknown category rejected");

        JsonObject orcJson = resource("orc");
        Language orc = new Language(orcJson);
        Language elf = new Language(resource("elf"));
        String word = orc.transform("мясо");
        equal(orc.transform("мясо, мясо!"), word + ", " + word + "!", "context independent words");
        equal(orc.transform("МЯСО"), word.toUpperCase(Locale.ROOT), "uppercase");
        equal(orc.transform("Мясо"), word.substring(0,1).toUpperCase(Locale.ROOT) + word.substring(1), "title case");
        equal(orc.transform("&cмясо&r 123 😀 @Steve https://example.com/a?b=c Minecraft"),
                "&c" + word + "&r 123 😀 @Steve https://example.com/a?b=c Minecraft", "formatting and exceptions");
        equal(orc.transform("é"), orc.transform("e\u0301"), "Unicode normalization");
        equal(new Language(orcJson).transform("мясо"), word, "restart stable seed");
        require(!orc.transform("мясо лежит на бочке").equals(elf.transform("мясо лежит на бочке")), "distinct languages");
        for (int i = 0; i < 9000; i++) orc.transformWord("cache" + i);
        equal(orc.transform("мясо"), word, "eviction does not change word");
        JsonObject changed = orcJson.deepCopy(); changed.addProperty("salt", "v2");
        require(!new Language(changed).transform("мясо лежит на бочке").equals(orc.transform("мясо лежит на бочке")), "salt changes lexicon");

        Path root = Files.createTempDirectory("dw-language-check-");
        try {
            Path dir = root.resolve("languages");
            LanguageRegistry registry = new LanguageRegistry(); registry.initialize(dir);
            require(registry.enabled().size() == 2, "bundled languages");
            Files.writeString(dir.resolve("bad.json"), "{");
            expectFailure(() -> registry.reload(dir), "malformed reload rejected");
            require(registry.enabled().size() == 2, "atomic registry rollback");
            Files.delete(dir.resolve("bad.json"));
            Files.writeString(dir.resolve("duplicate.json"), orcJson.toString());
            expectFailure(() -> registry.reload(dir), "duplicate code rejected");
            Files.delete(dir.resolve("duplicate.json"));
            JsonObject disabled = orcJson.deepCopy(); disabled.addProperty("enabled", false);
            Files.writeString(dir.resolve("orc.json"), disabled.toString()); registry.reload(dir);
            require(registry.get("orc") == null, "disabled language unavailable");
            Files.delete(dir.resolve("elf.json")); registry.initialize(dir);
            require(registry.get("elf") == null, "deleted examples not restored");

            UUID id = UUID.randomUUID(); Path file = root.resolve("world/players.json");
            PlayerLanguages players = new PlayerLanguages(); players.load(file);
            players.grant(id, "orc", true); players.select(id, "orc");
            PlayerLanguages restarted = new PlayerLanguages(); restarted.load(file);
            equal(restarted.get(id).active(), "orc", "active language persists");
            require(Boolean.TRUE.equals(restarted.get(id).grants().get("orc")), "knowledge persists");
            restarted.grant(id, "orc", false);
            equal(restarted.get(id).active(), "common", "revoke resets active language");
            require(Boolean.FALSE.equals(restarted.get(id).grants().get("orc")), "explicit deny persists");
            restarted.clear(); restarted.load(root.resolve("otherworld/players.json"));
            require(restarted.get(id).grants().isEmpty(), "world isolation");
            Files.writeString(file, "not json");
            expectFailure(() -> restarted.load(file), "corrupt state rejected without overwrite");
            equal(Files.readString(file), "not json", "corrupt state preserved");
            PlayerLanguages unwritable = new PlayerLanguages(); unwritable.load(root.resolve("blocked/players.json"));
            Files.writeString(root.resolve("blocked"), "file");
            expectFailure(() -> unwritable.grant(id, "orc", true), "write failure reported");
            require(unwritable.get(id).grants().isEmpty(), "failed save does not mutate memory");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("DW Languages: " + checks + " checks passed.");
        System.out.println("Orc sample: " + orc.transform("Мясо лежит на бочке!"));
        System.out.println("Elf sample: " + elf.transform("Мясо лежит на бочке!"));
    }
    private static JsonObject resource(String code) throws Exception {
        try (var input = LanguageCheck.class.getResourceAsStream("/languages/" + code + ".json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void rule(String rule, String input, String output, Map<Integer, String> cats) {
        equal(new SoundRule(rule, cats).apply(input), output, rule);
    }
    private static void equal(Object actual, Object expected, String label) {
        require(Objects.equals(actual, expected), label + ": expected " + expected + ", got " + actual);
    }
    private static void require(boolean value, String label) {
        checks++; if (!value) throw new AssertionError(label);
    }
    private static void expectFailure(Checked action, String label) throws Exception {
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError(label);
    }
    @FunctionalInterface interface Checked { void run() throws Exception; }
}
