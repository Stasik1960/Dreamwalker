package com.coraxberg.dwlanguages;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** World-scoped state; write the candidate atomically before publishing it in memory. */
final class PlayerLanguages {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Map<UUID, State> players = new HashMap<>();
    private Path file;
    record State(String active, Map<String, Boolean> grants) {
        State { grants = Map.copyOf(grants); }
    }
    private static final State EMPTY = new State("common", Map.of());

    void load(Path path) throws IOException {
        Map<UUID, State> loaded = new HashMap<>();
        if (Files.exists(path)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : root.entrySet()) {
                    JsonObject data = entry.getValue().getAsJsonObject();
                    Map<String, Boolean> grants = new HashMap<>();
                    for (var grant : data.getAsJsonObject("grants").entrySet()) grants.put(grant.getKey(), grant.getValue().getAsBoolean());
                    loaded.put(UUID.fromString(entry.getKey()), new State(data.get("active").getAsString(), grants));
                }
            } catch (RuntimeException ex) { throw new IOException("Повреждён файл знаний: " + path, ex); }
        }
        file = path;
        players = loaded;
    }
    State get(UUID id) { return players.getOrDefault(id, EMPTY); }
    void select(UUID id, String code) throws IOException { put(id, new State(code, get(id).grants())); }
    void grant(UUID id, String code, boolean value) throws IOException {
        State previous = get(id);
        Map<String, Boolean> grants = new HashMap<>(previous.grants());
        grants.put(code, value);
        put(id, new State(!value && code.equals(previous.active()) ? "common" : previous.active(), grants));
    }
    private void put(UUID id, State state) throws IOException {
        if (file == null) throw new IOException("Данные мира ещё не загружены");
        Map<UUID, State> next = new HashMap<>(players);
        next.put(id, state);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "players-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(next), StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            players = next;
        } finally { Files.deleteIfExists(temporary); }
    }
    void clear() { players = new HashMap<>(); file = null; }
}
