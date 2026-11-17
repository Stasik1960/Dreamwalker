package com.yharnam.casino;

import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/** Each deck item selects its own back; the common 52 faces and rules are shared. */
public final class CardDeckItem extends Item {
    private final Identifier back;
    public CardDeckItem(Identifier back) {
        super(new Settings().maxCount(1));
        this.back = back;
    }
    public Identifier backTexture() {
        return back;
    }
}
