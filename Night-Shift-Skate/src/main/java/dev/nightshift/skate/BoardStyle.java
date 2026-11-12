package dev.nightshift.skate;

import net.minecraft.item.ItemStack;

public enum BoardStyle {
    NIGHT_SHIFT("skateboard", 0.155),
    IRON_VESPERS("iron_vespers", 0.130),
    GRAVE_PROCESSION("grave_procession", 0.212);

    private static final BoardStyle[] NETWORK_STYLES = values();
    private final String itemId;
    private final double riderHeight;

    BoardStyle(String itemId, double riderHeight) {
        this.itemId = itemId;
        this.riderHeight = riderHeight;
    }

    public String itemId() {
        return itemId;
    }

    public double riderHeight() {
        return riderHeight;
    }

    byte networkId() {
        return (byte) ordinal();
    }

    static BoardStyle fromNetworkId(byte id) {
        return id >= 0 && id < NETWORK_STYLES.length ? NETWORK_STYLES[id] : NIGHT_SHIFT;
    }

    public static BoardStyle fromStack(ItemStack stack) {
        return stack.getItem() instanceof SkateboardItem item ? item.style() : NIGHT_SHIFT;
    }
}
