package com.yharnam.casino;

import net.minecraft.item.Item;

public final class ChipItem extends Item {
    private final int value;

    ChipItem(int value) {
        super(new Settings().maxCount(64));
        this.value = value;
    }

    public int value() {
        return value;
    }
}
