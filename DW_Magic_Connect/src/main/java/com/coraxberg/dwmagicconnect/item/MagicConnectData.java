package com.coraxberg.dwmagicconnect.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Optional;
import java.util.UUID;

/** Persistent state of a magical radio item. */
public final class MagicConnectData {
    public static final String ROOT_KEY = "DWMagicConnect";
    public static final int DEFAULT_RADIUS = 10;
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 10;

    private static final int FORMAT = 4;
    private static final int MULTI_CHANNEL_FORMAT = 3;
    private static final String[] HAND_KEYS = {"MoonHour", "MoonMinute", "SunHour", "SunMinute"};

    private MagicConnectData() {
    }

    public static boolean isRadio(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getNbt() != null
                && stack.getNbt().contains(ROOT_KEY, NbtElement.COMPOUND_TYPE)
                && stack.getNbt().getCompound(ROOT_KEY).getBoolean("MagicRadio");
    }

    public static boolean createRadio(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isRadio(stack)) return false;
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("MagicRadio", true);
        tag.putUuid("DeviceId", UUID.randomUUID());
        saveTag(tag, false, Frequency.empty(), false, DEFAULT_RADIUS);
        stack.getOrCreateNbt().put(ROOT_KEY, tag);
        return true;
    }

    public static boolean removeRadio(ItemStack stack) {
        if (!isRadio(stack)) return false;
        stack.getNbt().remove(ROOT_KEY);
        if (stack.getNbt().isEmpty()) stack.setNbt(null);
        return true;
    }

    public static UUID ensureDeviceId(ItemStack stack) {
        if (!isRadio(stack)) throw new IllegalArgumentException("Not a radio");
        NbtCompound tag = stack.getNbt().getCompound(ROOT_KEY);
        if (!tag.containsUuid("DeviceId")) tag.putUuid("DeviceId", UUID.randomUUID());
        return tag.getUuid("DeviceId");
    }

    public static Optional<UUID> deviceId(ItemStack stack) {
        if (!isRadio(stack)) return Optional.empty();
        NbtCompound tag = stack.getNbt().getCompound(ROOT_KEY);
        return tag.containsUuid("DeviceId") ? Optional.of(tag.getUuid("DeviceId")) : Optional.empty();
    }

    public static RadioState read(ItemStack stack) {
        UUID id = ensureDeviceId(stack);
        return readTag(stack.getNbt().getCompound(ROOT_KEY), id);
    }

    static RadioState readTag(NbtCompound tag, UUID id) {
        if (tag.getInt("ClockFormat") == MULTI_CHANNEL_FORMAT) {
            migrateV3(tag);
        } else if (tag.getInt("ClockFormat") != FORMAT) {
            saveTag(tag, false, Frequency.empty(), false, DEFAULT_RADIUS);
        }

        Frequency frequency = readFrequency(tag);
        int radius = validRadius(tag.getInt("Radius")) ? tag.getInt("Radius") : DEFAULT_RADIUS;
        if (radius != tag.getInt("Radius")) tag.putInt("Radius", radius);
        return new RadioState(tag.getBoolean("Enabled"), frequency, tag.getBoolean("Speaker"), radius, id);
    }

    private static void migrateV3(NbtCompound tag) {
        NbtList channels = tag.getList("Channels", NbtElement.COMPOUND_TYPE);
        int selected = tag.getInt("TransmitIndex");
        if (selected < 0 || selected >= channels.size()) selected = 0;

        Frequency frequency = selected < channels.size()
                ? readFrequency(channels.getCompound(selected))
                : Frequency.empty();
        boolean enabled = tag.getBoolean("Enabled");
        saveTag(tag, enabled, frequency, false, DEFAULT_RADIUS);
    }

    private static Frequency readFrequency(NbtCompound tag) {
        int[] hands = new int[HAND_KEYS.length];
        for (int index = 0; index < HAND_KEYS.length; index++) {
            if (!tag.contains(HAND_KEYS[index], NbtElement.INT_TYPE)) return Frequency.empty();
            hands[index] = tag.getInt(HAND_KEYS[index]);
        }
        return Frequency.valid(hands[0], hands[1], hands[2], hands[3])
                ? new Frequency(hands[0], hands[1], hands[2], hands[3])
                : Frequency.empty();
    }

    public static void saveSettings(ItemStack stack, boolean enabled, Frequency frequency, boolean speaker, int radius) {
        ensureDeviceId(stack);
        saveTag(stack.getNbt().getCompound(ROOT_KEY), enabled, frequency, speaker, radius);
    }

    static void saveTag(NbtCompound tag, boolean enabled, Frequency frequency, boolean speaker, int radius) {
        if (frequency == null || !validRadius(radius)) throw new IllegalArgumentException("Invalid radio settings");

        tag.putInt("ClockFormat", FORMAT);
        for (int index = 0; index < HAND_KEYS.length; index++) tag.putInt(HAND_KEYS[index], frequency.hand(index));
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Speaker", speaker);
        tag.putInt("Radius", radius);

        tag.remove("Channels");
        tag.remove("TransmitIndex");
        tag.remove("FrequencyA");
        tag.remove("FrequencyB");
        tag.remove("Configured");
    }

    public static boolean validRadius(int radius) {
        return radius >= MIN_RADIUS && radius <= MAX_RADIUS;
    }

    public static Optional<Frequency> activeFrequency(ItemStack stack) {
        if (!isRadio(stack)) return Optional.empty();
        RadioState state = read(stack);
        return state.enabled() && state.frequency().configured() ? Optional.of(state.frequency()) : Optional.empty();
    }

    public record Frequency(int moonHour, int moonMinute, int sunHour, int sunMinute) {
        public Frequency {
            if (!valid(moonHour, moonMinute, sunHour, sunMinute)) throw new IllegalArgumentException("Invalid hands");
        }

        public static boolean valid(int mh, int mm, int sh, int sm) {
            return (mh == -1 && mm == -1 && sh == -1 && sm == -1)
                    || (mh >= 0 && mh < 12 && mm >= 0 && mm < 60
                    && sh >= 0 && sh < 12 && sm >= 0 && sm < 60);
        }

        public static Frequency empty() { return new Frequency(-1, -1, -1, -1); }
        public boolean configured() { return moonHour >= 0; }

        public int hand(int index) {
            return switch (index) {
                case 0 -> moonHour;
                case 1 -> moonMinute;
                case 2 -> sunHour;
                case 3 -> sunMinute;
                default -> throw new IllegalArgumentException("Invalid hand index");
            };
        }

        public Frequency withHand(int index, int value) {
            int[] hands = configured() ? new int[]{moonHour, moonMinute, sunHour, sunMinute} : new int[4];
            hands[index] = value;
            return new Frequency(hands[0], hands[1], hands[2], hands[3]);
        }
    }

    public record RadioState(boolean enabled, Frequency frequency, boolean speaker, int radius, UUID deviceId) {
        public RadioState {
            if (frequency == null || deviceId == null || !validRadius(radius)) {
                throw new IllegalArgumentException("Invalid radio state");
            }
        }
    }
}
