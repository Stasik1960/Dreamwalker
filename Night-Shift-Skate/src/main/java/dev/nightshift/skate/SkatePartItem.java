package dev.nightshift.skate;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

final class SkatePartItem extends Item {
    private final SkatePart part;

    SkatePartItem(SkatePart part, Settings settings) {
        super(settings);
        this.part = part;
    }

    SkatePart part() {
        return part;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        BoardSetup setup = BoardSetup.DEFAULT.with(part);
        for (BoardStat stat : BoardStat.values()) {
            if (stat.type() == part.type()) {
                tooltip.add(Text.translatable("screen.night_shift_skate.stat_value", stat.label(), stat.value(setup))
                        .formatted(Formatting.GRAY));
            }
        }
    }
}
