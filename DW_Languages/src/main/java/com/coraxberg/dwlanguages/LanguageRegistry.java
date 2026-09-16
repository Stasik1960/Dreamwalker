package com.coraxberg.dwlanguages;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class LanguageRegistry {
    private Map<String, Language> languages = Map.of();

    void initialize(Path directory) throws IOException {
        // Seed once only: deleting a language later must not bring it back on restart.
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            for (String code : List.of("orc", "elf")) {
                try (InputStream input = LanguageRegistry.class.getResourceAsStream("/languages/" + code + ".json")) {
                    if (input == null) throw new IOException("Нет встроенного языка " + code);
                    Files.copy(input, directory.resolve(code + ".json"));
                }
            }
        }
        reload(directory);
    }

    void reload(Path directory) throws IOException {
        Map<String, Language> candidate = new TreeMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    if (Files.size(path) > 262144) throw new IOException("JSON больше 256 КБ");
                    Language language = new Language(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject());
                    if (candidate.putIfAbsent(language.code, language) != null) throw new IllegalArgumentException("Повтор locale " + language.code);
                } catch (Exception ex) { throw new IOException(path.getFileName() + ": " + ex.getMessage(), ex); }
            }
        }
        // No partial updates and no cache from the previous definitions.
        languages = Collections.unmodifiableMap(candidate);
    }

    Language get(String code) {
        Language language = languages.get(code);
        return language != null && language.enabled ? language : null;
    }
    Collection<Language> enabled() { return languages.values().stream().filter(l -> l.enabled).toList(); }
}
