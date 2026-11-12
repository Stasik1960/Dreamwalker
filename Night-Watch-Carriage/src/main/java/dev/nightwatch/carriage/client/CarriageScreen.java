package dev.nightwatch.carriage.client;

import dev.nightwatch.carriage.CarriageEntity;
import dev.nightwatch.carriage.client.CarriageDashboard.Control;
import dev.nightwatch.carriage.client.CarriageDashboard.Layout;
import dev.nightwatch.carriage.client.CarriageDashboard.Rect;
import dev.nightwatch.carriage.client.CarriageDashboard.State;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.EnumMap;

final class CarriageScreen extends Screen {
    private final CarriageEntity carriage;
    private final EnumMap<Control, DashboardButton> buttons = new EnumMap<>(Control.class);
    private Layout layout;

    CarriageScreen(CarriageEntity carriage) {
        super(Text.translatable("gui.nightwatchcarriage.title"));
        this.carriage = carriage;
    }

    @Override
    protected void init() {
        layout = CarriageDashboard.layout(client, width, height);
        buttons.clear();
        for (Control control : Control.values()) {
            Rect area = layout.button(control);
            DashboardButton button = addDrawableChild(new DashboardButton(area,
                widget -> CarriageClient.action(carriage, control.action)));
            button.sync(CarriageDashboard.state(client, carriage, control));
            buttons.put(control, button);
        }
    }

    @Override
    public void tick() {
        if (carriage.isRemoved() || carriage.seatOf(client.player) == null) {
            close();
            return;
        }
        buttons.forEach((control, button) -> button.sync(CarriageDashboard.state(client, carriage, control)));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CarriageDashboard.shell(context, textRenderer, layout, carriage, true);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (CarriageClient.panelKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldPause() { return false; }

    private static final class DashboardButton extends ButtonWidget {
        private final Rect area;
        private State state;

        DashboardButton(Rect area, PressAction action) {
            super(area.x(), area.y(), area.width(), area.height(), Text.empty(), action, DEFAULT_NARRATION_SUPPLIER);
            this.area = area;
            setTooltipDelay(350);
        }

        void sync(State update) {
            if (update.equals(state)) return;
            state = update;
            active = update.enabled();
            setMessage(update.details());
            setTooltip(Tooltip.of(update.details()));
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            CarriageDashboard.button(context, MinecraftClient.getInstance().textRenderer, area, state, isSelected());
        }
    }
}
