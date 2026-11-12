package dev.nightshift.skate;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

import dev.nightshift.skate.SkatePart.Deck;
import dev.nightshift.skate.SkatePart.Trucks;
import dev.nightshift.skate.SkatePart.Wheels;

/** Saved as stable part IDs on the item; the tracked entity uses a compact index. */
public record BoardSetup(Deck deck, Trucks trucks, Wheels wheels) {
    private static final String NBT_KEY = "NightShiftParts";
    private static final Deck[] DECKS = Deck.values();
    private static final Trucks[] TRUCKS = Trucks.values();
    private static final Wheels[] WHEELS = Wheels.values();
    private static final BoardStyle[] STYLES = BoardStyle.values();
    private static final BoardSetup[] PACKED = createCombinations();
    public static final BoardSetup DEFAULT = PACKED[0];
    public static final double WHEELBASE_HALF = 0.285;
    public static final double FIRM_COAST_RETENTION = Math.pow(0.70, 1.0 / 160.0);

    private static BoardSetup[] createCombinations() {
        BoardSetup[] result = new BoardSetup[DECKS.length * TRUCKS.length * WHEELS.length];
        for (Deck deck : DECKS) {
            for (Trucks trucks : TRUCKS) {
                for (Wheels wheels : WHEELS) {
                    BoardSetup setup = new BoardSetup(deck, trucks, wheels);
                    result[setup.packed()] = setup;
                }
            }
        }
        return result;
    }

    public static BoardSetup preset(BoardStyle style) {
        return PACKED[switch (style) {
            case NIGHT_SHIFT -> pack(Deck.NIGHT_SHIFT, Trucks.NIGHT_SHIFT, Wheels.NIGHT_SHIFT);
            case IRON_VESPERS -> pack(Deck.IRON_VESPERS, Trucks.IRON_VESPERS, Wheels.IRON_VESPERS);
            case GRAVE_PROCESSION -> pack(Deck.GRAVE_PROCESSION, Trucks.GRAVE_PROCESSION, Wheels.GRAVE_PROCESSION);
        }];
    }

    public @Nullable BoardStyle matchingPreset() {
        for (BoardStyle style : STYLES) {
            if (equals(preset(style))) return style;
        }
        return null;
    }

    public static BoardSetup fromStack(ItemStack stack) {
        BoardSetup fallback = preset(BoardStyle.fromStack(stack));
        NbtCompound data = stack.getSubNbt(NBT_KEY);
        if (data == null) return fallback;
        Deck deck = readPart(data, SkatePart.Type.DECK, DECKS, fallback.deck());
        Trucks trucks = readPart(data, SkatePart.Type.TRUCKS, TRUCKS, fallback.trucks());
        Wheels wheels = readPart(data, SkatePart.Type.WHEELS, WHEELS, fallback.wheels());
        return PACKED[pack(deck, trucks, wheels)];
    }

    private static <T extends SkatePart> T readPart(NbtCompound data, SkatePart.Type type, T[] parts, T fallback) {
        if (data.contains(type.id(), NbtElement.STRING_TYPE)) {
            String id = data.getString(type.id());
            for (T part : parts) {
                if (part.id().equals(id)) return part;
            }
        }
        return fallback;
    }

    public ItemStack applyTo(ItemStack board) {
        ItemStack result = board.copyWithCount(1);
        if (equals(preset(BoardStyle.fromStack(board)))) {
            result.removeSubNbt(NBT_KEY);
        } else {
            NbtCompound data = result.getOrCreateSubNbt(NBT_KEY);
            for (SkatePart.Type type : SkatePart.Type.values()) {
                data.putString(type.id(), part(type).id());
            }
        }
        return result;
    }

    public SkatePart part(SkatePart.Type type) {
        return switch (type) {
            case DECK -> deck;
            case TRUCKS -> trucks;
            case WHEELS -> wheels;
        };
    }

    public BoardSetup with(SkatePart part) {
        return PACKED[pack(
                part instanceof Deck replacement ? replacement : deck,
                part instanceof Trucks replacement ? replacement : trucks,
                part instanceof Wheels replacement ? replacement : wheels)];
    }

    private static int pack(Deck deck, Trucks trucks, Wheels wheels) {
        return (deck.ordinal() * TRUCKS.length + trucks.ordinal()) * WHEELS.length + wheels.ordinal();
    }

    int packed() {
        return pack(deck, trucks, wheels);
    }

    static BoardSetup fromPacked(int value) {
        return value >= 0 && value < PACKED.length ? PACKED[value] : DEFAULT;
    }

    public double deckBase() {
        return wheels.radius() + trucks.axleDrop();
    }

    public double riderHeight() {
        BoardStyle preset = matchingPreset();
        return preset == null ? deckBase() + deck.standingHeight() : preset.riderHeight();
    }

    public double topSpeed() {
        return SkateboardEntity.MAX_SPEED * wheels.topSpeed();
    }

    public double coastRetention(double surfaceRetention) {
        return 1.0 - (1.0 - surfaceRetention) * wheels.drag();
    }
}
