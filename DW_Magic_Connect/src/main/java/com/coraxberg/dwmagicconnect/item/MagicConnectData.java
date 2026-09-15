package com.coraxberg.dwmagicconnect.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Optional;
import java.util.UUID;

/**
 * Owns the namespaced NBT stored directly on an arbitrary radio ItemStack.
 */
public final class MagicConnectData {
    public static final String ROOT_KEY = "DWMagicConnect";

    public static final int MIN_FREQUENCY = 0;
    public static final int MAX_FREQUENCY = 9_999;

    private static final String MAGIC_RADIO_KEY = "MagicRadio";
    private static final String ENABLED_KEY = "Enabled";
    private static final String FREQUENCY_A_KEY = "FrequencyA";
    private static final String FREQUENCY_B_KEY = "FrequencyB";
    private static final String CONFIGURED_KEY = "Configured";
    private static final String DEVICE_ID_KEY = "DeviceId";

    private MagicConnectData() {
    }

    public static boolean isRadio(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(ROOT_KEY, NbtElement.COMPOUND_TYPE)) return false;

        return root.getCompound(ROOT_KEY).getBoolean(MAGIC_RADIO_KEY);
    }

    public static boolean createRadio(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isRadio(stack)) return false;

        NbtCompound radio = new NbtCompound();
        radio.putBoolean(MAGIC_RADIO_KEY, true);
        radio.putBoolean(ENABLED_KEY, false);
        radio.putInt(FREQUENCY_A_KEY, 0);
        radio.putInt(FREQUENCY_B_KEY, 0);
        radio.putBoolean(CONFIGURED_KEY, false);
        radio.putUuid(DEVICE_ID_KEY, UUID.randomUUID());
        stack.getOrCreateNbt().put(ROOT_KEY, radio);
        return true;
    }

    public static boolean removeRadio(ItemStack stack) {
        if (!isRadio(stack)) return false;

        NbtCompound root = stack.getNbt();
        if (root == null) return false;
        root.remove(ROOT_KEY);
        if (root.isEmpty()) {
            stack.setNbt(null);
        }
        return true;
    }

    public static UUID ensureDeviceId(ItemStack stack) {
        if (!isRadio(stack)) {
            throw new IllegalArgumentException("ItemStack is not a DW Magic Connect radio");
        }

        NbtCompound radio = stack.getOrCreateNbt().getCompound(ROOT_KEY);
        if (!radio.containsUuid(DEVICE_ID_KEY)) {
            radio.putUuid(DEVICE_ID_KEY, UUID.randomUUID());
        }
        return radio.getUuid(DEVICE_ID_KEY);
    }

    public static Optional<UUID> deviceId(ItemStack stack) {
        NbtCompound radio = radioTag(stack);
        if (radio == null || !radio.containsUuid(DEVICE_ID_KEY)) return Optional.empty();
        return Optional.of(radio.getUuid(DEVICE_ID_KEY));
    }

    public static RadioState read(ItemStack stack) {
        NbtCompound radio = radioTag(stack);
        if (radio == null) {
            throw new IllegalArgumentException("ItemStack is not a DW Magic Connect radio");
        }

        UUID deviceId = ensureDeviceId(stack);
        int frequencyA = radio.getInt(FREQUENCY_A_KEY);
        int frequencyB = radio.getInt(FREQUENCY_B_KEY);
        boolean configured = radio.getBoolean(CONFIGURED_KEY)
                && radio.contains(FREQUENCY_A_KEY, NbtElement.INT_TYPE)
                && radio.contains(FREQUENCY_B_KEY, NbtElement.INT_TYPE)
                && isFrequencyValid(frequencyA)
                && isFrequencyValid(frequencyB);

        return new RadioState(
                radio.getBoolean(ENABLED_KEY),
                configured,
                frequencyA,
                frequencyB,
                deviceId
        );
    }

    public static void saveSettings(ItemStack stack, boolean enabled, int frequencyA, int frequencyB) {
        if (!isRadio(stack)) {
            throw new IllegalArgumentException("ItemStack is not a DW Magic Connect radio");
        }
        if (!isFrequencyValid(frequencyA) || !isFrequencyValid(frequencyB)) {
            throw new IllegalArgumentException("Frequency is outside the allowed range");
        }

        NbtCompound radio = stack.getOrCreateNbt().getCompound(ROOT_KEY);
        ensureDeviceId(stack);
        radio.putBoolean(ENABLED_KEY, enabled);
        radio.putInt(FREQUENCY_A_KEY, frequencyA);
        radio.putInt(FREQUENCY_B_KEY, frequencyB);
        radio.putBoolean(CONFIGURED_KEY, true);
    }

    public static Optional<Frequency> activeFrequency(ItemStack stack) {
        if (!isRadio(stack)) return Optional.empty();

        RadioState state = read(stack);
        if (!state.enabled() || !state.configured()) return Optional.empty();
        return Optional.of(new Frequency(state.frequencyA(), state.frequencyB()));
    }

    public static boolean isFrequencyValid(int value) {
        return value >= MIN_FREQUENCY && value <= MAX_FREQUENCY;
    }

    private static NbtCompound radioTag(ItemStack stack) {
        if (!isRadio(stack)) return null;
        return stack.getNbt().getCompound(ROOT_KEY);
    }

    public record Frequency(int a, int b) {
    }

    public record RadioState(
            boolean enabled,
            boolean configured,
            int frequencyA,
            int frequencyB,
            UUID deviceId
    ) {
    }
}
