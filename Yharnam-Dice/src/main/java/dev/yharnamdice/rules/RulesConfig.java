package dev.yharnamdice.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

public final class RulesConfig {
    public enum Game { FREE, DICE_POKER, FARKLE, TWENTY_ONE }
    public enum HostExit { CANCEL, TRANSFER, BETWEEN_GAMES }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String id = "free";
    public String name = "Свободный бросок";
    public Game game = Game.FREE;
    public int minPlayers = 1;
    public int maxPlayers = 4;
    public int dicePerPlayer = 20;
    public HostExit hostExit = HostExit.TRANSFER;
    public int target = 4000;
    public int maxRerolls = 1;
    public boolean hotDice = true;
    public boolean allowDuplicateDice = true;

    public void validate() {
        if (id == null || !id.matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException("id: use 1–32 lowercase letters, digits or underscores");
        if (name == null || name.isBlank() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("name: enter 1–64 printable characters");
        if (game == null || hostExit == null) throw new IllegalArgumentException("game and hostExit must be defined");
        if (minPlayers < 1 || maxPlayers < minPlayers || maxPlayers > 4)
            throw new IllegalArgumentException("player limits: 1 <= minPlayers <= maxPlayers <= 4");
        if (dicePerPlayer < 1 || dicePerPlayer > 20) throw new IllegalArgumentException("dicePerPlayer: 1–20");
        if (target < 1 || target > 1_000_000) throw new IllegalArgumentException("target: 1–1000000");
        if (maxRerolls < 0 || maxRerolls > 20) throw new IllegalArgumentException("maxRerolls: 0–20");
        if (game == Game.DICE_POKER && dicePerPlayer != 5) throw new IllegalArgumentException("DICE_POKER requires five dice");
        if (game == Game.FARKLE && dicePerPlayer != 6) throw new IllegalArgumentException("FARKLE requires six dice");
        if (game == Game.TWENTY_ONE && dicePerPlayer > 6) throw new IllegalArgumentException("TWENTY_ONE uses 1–6 dice per throw");
    }

    public static RulesConfig fromJson(String json) {
        if (json == null || json.length() > 16_384) throw new IllegalArgumentException("rules document exceeds 16,384 characters");
        RulesConfig rule = new RulesConfig();
        Set<String> keys = new HashSet<>();
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!keys.add(key)) throw new IllegalArgumentException("duplicate field: " + key);
                switch (key) {
                    case "id" -> rule.id = readString(reader, key);
                    case "name" -> rule.name = readString(reader, key);
                    case "game" -> rule.game = parseEnum(Game.class, reader, key);
                    case "minPlayers" -> rule.minPlayers = readInt(reader, key);
                    case "maxPlayers" -> rule.maxPlayers = readInt(reader, key);
                    case "dicePerPlayer" -> rule.dicePerPlayer = readInt(reader, key);
                    case "hostExit" -> rule.hostExit = parseEnum(HostExit.class, reader, key);
                    case "target" -> rule.target = readInt(reader, key);
                    case "maxRerolls" -> rule.maxRerolls = readInt(reader, key);
                    case "hotDice" -> rule.hotDice = readBoolean(reader, key);
                    case "allowDuplicateDice" -> rule.allowDuplicateDice = readBoolean(reader, key);
                    default -> throw new IllegalArgumentException("unknown field: " + key.substring(0,Math.min(key.length(),80)));
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("unexpected data after the JSON object");
        } catch (IOException | IllegalStateException invalid) {
            throw new IllegalArgumentException("expected one valid JSON object");
        }
        for (String key : Set.of("id","name","game")) {
            if (!keys.contains(key)) throw new IllegalArgumentException("missing field: " + key);
        }
        if (!keys.contains("dicePerPlayer")) rule.dicePerPlayer = switch (rule.game) {
            case FREE -> 20;
            case DICE_POKER -> 5;
            case FARKLE -> 6;
            case TWENTY_ONE -> 2;
        };
        if (!keys.contains("target") && rule.game == Game.TWENTY_ONE) rule.target = 21;
        rule.validate();
        return rule;
    }

    private static String readString(JsonReader r, String key) throws IOException {
        if (r.peek() != JsonToken.STRING) throw new IllegalArgumentException(key + " must be a string");
        return r.nextString();
    }

    private static int readInt(JsonReader r, String key) throws IOException {
        if (r.peek() != JsonToken.NUMBER) throw new IllegalArgumentException(key + " must be an integer");
        try { return Integer.parseInt(r.nextString()); }
        catch (NumberFormatException bad) { throw new IllegalArgumentException(key + " must be an integer"); }
    }

    private static boolean readBoolean(JsonReader r, String key) throws IOException {
        if (r.peek() != JsonToken.BOOLEAN) throw new IllegalArgumentException(key + " must be true or false");
        return r.nextBoolean();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, JsonReader r, String key) throws IOException {
        String text = readString(r,key);
        try { return Enum.valueOf(type,text); }
        catch (IllegalArgumentException bad) { throw new IllegalArgumentException("unsupported value for " + key); }
    }

    public String toJson() { validate(); return GSON.toJson(this); }
}
