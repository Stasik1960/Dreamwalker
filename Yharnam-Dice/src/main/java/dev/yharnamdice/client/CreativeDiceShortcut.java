package dev.yharnamdice.client;

import dev.yharnamdice.DiceMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class CreativeDiceShortcut extends ButtonWidget {
    private final ItemStack icon = new ItemStack(DiceMod.POUCH);

    public CreativeDiceShortcut(int x, int y, Text title, PressAction press) {
        super(x, y, 23, 23, title, press, DEFAULT_NARRATION_SUPPLIER);
        setTooltip(Tooltip.of(title));
    }

    @Override protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY();
        context.fill(x,y,x+23,y+23,isSelected() ? 0xffe4b681 : 0xffaa8259);
        context.fill(x+1,y+1,x+22,y+22,isSelected() ? 0xff62382f : 0xff341c20);
        context.drawItem(icon,x+4,y+3);
    }
}
