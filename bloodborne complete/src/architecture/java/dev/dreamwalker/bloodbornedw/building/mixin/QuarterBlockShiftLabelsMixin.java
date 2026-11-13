package dev.dreamwalker.bloodbornedw.building.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "dev.dreamwalker.bloodbornedw.tool.DebugToolModes$Action", remap = false)
abstract class QuarterBlockShiftLabelsMixin {
    @ModifyConstant(method = "<clinit>", constant = {
            @Constant(stringValue = "Поднять модель на 0,5 блока"),
            @Constant(stringValue = "Опустить модель на 0,5 блока"),
            @Constant(stringValue = "Сдвинуть модель влево на 0,5 блока"),
            @Constant(stringValue = "Сдвинуть модель вправо на 0,5 блока"),
            @Constant(stringValue = "Сдвинуть модель вперёд на 0,5 блока"),
            @Constant(stringValue = "Сдвинуть модель назад на 0,5 блока")
    }, require = 6)
    private static String dw$labelQuarterBlockShift(String title) {
        return title.replace("0,5", "0,25");
    }
}
