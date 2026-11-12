package dev.nightwatch.carriage.client;

import dev.nightwatch.carriage.CarriageEntity;
import dev.nightwatch.carriage.CarriageEntity.Seat;
import dev.nightwatch.carriage.CarriagePackets.Action;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

final class CarriageDashboard {
    private static final int IVORY = 0xFFF2DEC1;
    private static final int BRASS = 0xFFC79B66;
    private static final int BRONZE = 0xFF765638;
    private static final int MUTED = 0xFFAD9985;
    private static final int DARK = 0xFF171315;
    private static final int OFF = 0xFF594E49;
    private static final int AMBER = 0xFFFFC274;
    private static final int TEAL = 0xFF8CC8AD;
    private static final int MAX_SPEED = 20;

    enum Control {
        PULLER(Action.SEAT_PULLER, Seat.PULLER),
        LEFT(Action.SEAT_LEFT, Seat.LEFT),
        RIGHT(Action.SEAT_RIGHT, Seat.RIGHT),
        DRIVER(Action.TOGGLE_DRIVER, null),
        ROOF(Action.TOGGLE_HOOD, null),
        LAMPS(Action.TOGGLE_LAMPS, null);

        final Action action;
        final Seat seat;

        Control(Action action, Seat seat) {
            this.action = action;
            this.seat = seat;
        }
    }

    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }

    record Layout(int x, int bottom, int width, int wing) {
        int center() { return x + width / 2; }
        int left() { return x + 10; }
        int right() { return x + width - 10 - wing; }
        int top() { return bottom - 55; }

        Rect button(Control control) {
            int half = (wing - 5) / 2;
            return switch (control) {
                case PULLER -> new Rect(left(), top() + 16, wing, 14);
                case LEFT -> new Rect(left(), top() + 33, half, 14);
                case RIGHT -> new Rect(left() + half + 5, top() + 33, wing - half - 5, 14);
                case DRIVER -> new Rect(right(), top() + 16, wing, 14);
                case ROOF -> new Rect(right(), top() + 33, half, 14);
                case LAMPS -> new Rect(right() + half + 5, top() + 33, wing - half - 5, 14);
            };
        }
    }

    record State(Text label, Text details, boolean enabled, int indicator) { }

    private CarriageDashboard() { }

    static Layout layout(MinecraftClient client, int screenWidth, int screenHeight) {
        int width = Math.min(374, screenWidth - 16);
        int bottomMargin = client.interactionManager != null && client.interactionManager.hasStatusBars() ? 56 : 28;
        return new Layout((screenWidth - width) / 2, screenHeight - bottomMargin, width, (width - 102) / 2);
    }

    static State state(MinecraftClient client, CarriageEntity carriage, Control control) {
        Seat mine = carriage.seatOf(client.player);
        if (control.seat != null) {
            int id = carriage.occupant(control.seat);
            boolean current = mine == control.seat;
            Entity occupant = client.world == null || id < 0 ? null : client.world.getEntityById(id);
            Text description = current ? text("you") : occupant != null ? occupant.getName()
                : id < 0 ? text("free") : text("occupied");
            return new State(text(control.seat.key), seatName(control.seat).copy().append(" · ").append(description),
                mine != null && id < 0, current ? TEAL : id >= 0 ? BRONZE : OFF);
        }
        return switch (control) {
            case DRIVER -> new State(
                carriage.hasPuller() ? text("driver_short", text(carriage.controlSeat().key)) : text("no_puller"),
                carriage.hasPuller() ? text("driver_detail", seatName(carriage.controlSeat())) : text("need_puller"),
                mine == Seat.LEFT && carriage.hasPuller(),
                carriage.hasPuller() ? mine == carriage.controlSeat() ? TEAL : BRASS : OFF);
            case ROOF -> new State(text("roof"), text(carriage.hoodRaised() ? "fold" : "raise"),
                mine != null, carriage.hoodRaised() ? BRASS : OFF);
            case LAMPS -> new State(text("lights"), text(carriage.lanternsOn() ? "lamps_off" : "lamps_on"),
                mine != null, carriage.lanternsOn() ? AMBER : OFF);
            default -> throw new IllegalArgumentException("Missing dashboard control: " + control);
        };
    }

    static void shell(DrawContext context, TextRenderer font, Layout layout, CarriageEntity carriage, boolean cursor) {
        int x = layout.x(), top = layout.top(), right = x + layout.width(), bottom = layout.bottom();
        int center = layout.center(), dialY = bottom - 34;
        context.draw(() -> {
            rounded(context, x - 1, top + 2, right + 1, bottom + 2, 10, 0x820D0909);
            rounded(context, x, top, right, bottom, 9, BRONZE);
            rounded(context, x + 1, top + 1, right - 1, bottom - 1, 8, 0xFF30241E);
            rounded(context, x + 3, top + 3, right - 3, bottom - 3, 6, 0xF4211A18);
            context.fillGradient(x + 10, top + 13, right - 10, bottom - 7, 0x4E4F352B, 0x004F352B);
            context.fill(x + 10, top + 13, x + 10 + layout.wing(), top + 14, 0xFF5A4332);
            context.fill(layout.right(), top + 13, right - 10, top + 14, 0xFF5A4332);
            disc(context, center, dialY, 36, 0xFF211916);
            disc(context, center, dialY, 35, BRONZE);
            disc(context, center, dialY, 34, 0xFFCB9F6C);
            disc(context, center, dialY, 32, 0xFF49352A);
            disc(context, center, dialY, 31, DARK);
            drawNeedle(context, center, dialY, carriage.speed() * 3.6f);
        });
        tiny(context, font, text("crew"), layout.left(), top + 5, BRASS, false);
        tiny(context, font, text("controls"), layout.right(), top + 5, BRASS, false);
        Text key = CarriageClient.panelKey.getBoundKeyLocalizedText();
        String keyLabel = "[" + key.getString() + "]";
        int keyRoom = Math.max(14, (layout.wing() - 41) * 5 / 4);
        String cursorHint = text("cursor_key", keyLabel).getString();
        String visibleKey = font.getWidth(cursorHint) <= keyRoom ? cursorHint : font.trimToWidth(keyLabel, keyRoom);
        int keyX = layout.left() + layout.wing() - Math.round(font.getWidth(visibleKey) * .8f);
        tiny(context, font, Text.literal(visibleKey), keyX, top + 5, cursor ? TEAL : MUTED, false);
        tiny(context, font, Text.literal("0"), center - 21, dialY + 7, MUTED, true);
        tiny(context, font, Text.literal("10"), center, dialY - 18, MUTED, true);
        tiny(context, font, Text.literal("20"), center + 21, dialY + 7, MUTED, true);
        int tenths = Math.round(carriage.speed() * 36f);
        context.drawCenteredTextWithShadow(font, (tenths / 10) + text("decimal").getString() + (tenths % 10),
            center, dialY + 7, IVORY);
        tiny(context, font, text("units"), center, dialY + 19, BRASS, true);
    }

    static void button(DrawContext context, TextRenderer font, Rect rect, State state, boolean highlight) {
        boolean active = highlight && state.enabled();
        int edge = active ? BRASS : 0xFF624938;
        context.draw(() -> {
            rounded(context, rect.x(), rect.y(), rect.right(), rect.bottom(), 3, edge);
            rounded(context, rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1,
                2, active ? 0xFF594134 : 0xFF2C2321);
            int dotX = rect.x() + 6;
            int dotY = rect.y() + rect.height() / 2;
            disc(context, dotX, dotY, 2, 0xFF170F10);
            disc(context, dotX, dotY, 1, state.indicator());
        });
        int room = Math.max(0, (rect.width() - 15) * 5 / 4);
        String visible = font.trimToWidth(state.label().getString(), room);
        tiny(context, font, Text.literal(visible), rect.x() + (rect.width() + 8) / 2, rect.y() + 4,
            active || state.enabled() || state.indicator() != OFF ? IVORY : MUTED, true);
    }

    private static void drawNeedle(DrawContext context, int x, int y, float kmh) {
        for (int i = 0; i <= MAX_SPEED; i++) {
            boolean major = i % 5 == 0;
            radial(context, x, y, angle(i), major ? 24 : 27, 30, major ? 2 : 1, major ? BRASS : 0xFF7D6652);
        }
        double pointer = angle(MathHelper.clamp(kmh, 0f, MAX_SPEED));
        radial(context, x, y, pointer, -3, 23, 2, 0xFFC06D49);
        radial(context, x, y, pointer, 3, 24, 1, AMBER);
        disc(context, x, y, 3, 0xFF53392A);
        disc(context, x, y, 2, BRASS);
    }

    private static double angle(float kmh) {
        return 154 + kmh / MAX_SPEED * 232;
    }

    private static void radial(DrawContext context, int x, int y, double degrees, int start, int end, int width, int color) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) degrees));
        context.fill(start, -width / 2, end, (width + 1) / 2, color);
        matrices.pop();
    }

    private static void disc(DrawContext context, int x, int y, int r, int color) {
        for (int row = -r; row < r; row++) {
            double dy = row + .5;
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            context.fill(x - half, y + row, x + half, y + row + 1, color);
        }
    }

    private static void rounded(DrawContext context, int left, int top, int right, int bottom, int r, int color) {
        context.fill(left, top + r, right, bottom - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - .5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            context.fill(left + inset, top + row, right - inset, top + row + 1, color);
            context.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color);
        }
    }

    private static void tiny(DrawContext context, TextRenderer font, Text text, int x, int y, int color, boolean center) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(.8f, .8f, 1);
        context.drawText(font, text, center ? -font.getWidth(text) / 2 : 0, 0, color, false);
        matrices.pop();
    }

    private static Text text(String key, Object... args) {
        return Text.translatable("gui.nightwatchcarriage." + key, args);
    }

    private static Text seatName(Seat seat) {
        return Text.translatable("seat.nightwatchcarriage." + seat.key);
    }
}
