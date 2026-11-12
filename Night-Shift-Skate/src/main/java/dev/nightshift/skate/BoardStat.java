package dev.nightshift.skate;

import java.util.Locale;
import java.util.function.ToDoubleFunction;
import net.minecraft.text.Text;

public enum BoardStat {
    POP(SkatePart.Type.DECK, setup -> setup.deck().pop() * 100),
    FLIP(SkatePart.Type.DECK, setup -> setup.deck().flipTicks()),
    STEERING(SkatePart.Type.TRUCKS, setup -> setup.trucks().steering() * 100),
    BALANCE(SkatePart.Type.TRUCKS, setup -> setup.trucks().balance() * 100),
    SPEED(SkatePart.Type.WHEELS, setup -> setup.topSpeed() * 20),
    ACCELERATION(SkatePart.Type.WHEELS, setup -> setup.wheels().acceleration() * 100),
    COAST(SkatePart.Type.WHEELS, setup -> Math.pow(setup.coastRetention(BoardSetup.FIRM_COAST_RETENTION), 160) * 100);

    private final SkatePart.Type type;
    private final ToDoubleFunction<BoardSetup> metric;
    private final String key = "screen.night_shift_skate.stat." + name().toLowerCase(Locale.ROOT);

    BoardStat(SkatePart.Type type, ToDoubleFunction<BoardSetup> metric) {
        this.type = type;
        this.metric = metric;
    }

    public SkatePart.Type type() {
        return type;
    }

    public Text label() {
        return Text.translatable(key);
    }

    public double measure(BoardSetup setup) {
        return metric.applyAsDouble(setup);
    }

    public Text value(BoardSetup setup) {
        double value = measure(setup);
        return switch (this) {
            case SPEED -> Text.translatable(key + ".unit", String.format(Locale.ROOT, "%.1f", value));
            case FLIP -> Text.translatable(key + ".unit", Math.round(value));
            default -> Text.literal(Math.round(value) + "%");
        };
    }

    public boolean improves(double change) {
        return this == FLIP ? change < 0 : change > 0;
    }
}
