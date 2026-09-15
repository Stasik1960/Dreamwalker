package com.coraxberg.dwmagicconnect.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Four discrete hand positions identify a channel. Old code frequencies are intentionally reset. */
public final class MagicConnectData {
    public static final String ROOT_KEY = "DWMagicConnect";
    public static final int CHANNEL_COUNT = 3;
    private static final int FORMAT = 3;
    private static final String[] HAND_KEYS = {"MoonHour", "MoonMinute", "SunHour", "SunMinute"};
    private MagicConnectData() {}

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
        saveTag(tag, false, emptyChannels(), 0);
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
        if (tag.getInt("ClockFormat") != FORMAT) {
            saveTag(tag, false, emptyChannels(), 0);
        }
        List<Frequency> channels = new ArrayList<>();
        NbtList list = tag.getList("Channels", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            NbtCompound channel = i < list.size() ? list.getCompound(i) : new NbtCompound();
            int[] hands = new int[4];
            boolean complete = true;
            for (int h = 0; h < 4; h++) {
                complete &= channel.contains(HAND_KEYS[h], NbtElement.INT_TYPE);
                hands[h] = channel.getInt(HAND_KEYS[h]);
            }
            channels.add(complete && Frequency.valid(hands[0], hands[1], hands[2], hands[3])
                    ? new Frequency(hands[0], hands[1], hands[2], hands[3]) : Frequency.empty());
        }
        int tx = tag.getInt("TransmitIndex");
        if (tx < 0 || tx >= CHANNEL_COUNT) tx = 0;
        return new RadioState(tag.getBoolean("Enabled") && channels.get(tx).configured(), channels, tx, id);
    }
    public static void saveSettings(ItemStack stack, boolean enabled, List<Frequency> channels, int tx) {
        ensureDeviceId(stack);
        saveTag(stack.getNbt().getCompound(ROOT_KEY), enabled, channels, tx);
    }
    static void saveTag(NbtCompound tag, boolean enabled, List<Frequency> channels, int tx) {
        if (channels == null || channels.size() != CHANNEL_COUNT || channels.stream().anyMatch(java.util.Objects::isNull)
                || tx < 0 || tx >= CHANNEL_COUNT || (enabled && !channels.get(tx).configured())) {
            throw new IllegalArgumentException("Invalid clock channels");
        }
        NbtList list = new NbtList();
        for (Frequency frequency : channels) {
            NbtCompound channel = new NbtCompound();
            for (int h = 0; h < 4; h++) channel.putInt(HAND_KEYS[h], frequency.hand(h));
            list.add(channel);
        }
        tag.putInt("ClockFormat", FORMAT);
        tag.put("Channels", list);
        tag.putBoolean("Enabled", enabled);
        tag.putInt("TransmitIndex", tx);
        tag.remove("FrequencyA"); tag.remove("FrequencyB"); tag.remove("Configured");
    }
    public static List<Frequency> emptyChannels() {
        return List.of(Frequency.empty(), Frequency.empty(), Frequency.empty());
    }
    public static Optional<Frequency> transmitFrequency(ItemStack stack) {
        return isRadio(stack) ? read(stack).transmitFrequency() : Optional.empty();
    }
    public static List<Frequency> listeningFrequencies(ItemStack stack) {
        return isRadio(stack) ? read(stack).listeningFrequencies() : List.of();
    }
    public record Frequency(int moonHour, int moonMinute, int sunHour, int sunMinute) {
        public Frequency {
            if (!valid(moonHour, moonMinute, sunHour, sunMinute)) throw new IllegalArgumentException("Invalid hands");
        }
        public static boolean valid(int mh, int mm, int sh, int sm) {
            return (mh == -1 && mm == -1 && sh == -1 && sm == -1)
                    || (mh >= 0 && mh < 12 && mm >= 0 && mm < 60 && sh >= 0 && sh < 12 && sm >= 0 && sm < 60);
        }
        public static Frequency empty() { return new Frequency(-1, -1, -1, -1); }
        public boolean configured() { return moonHour >= 0; }
        public int hand(int index) {
            return switch (index) { case 0 -> moonHour; case 1 -> moonMinute; case 2 -> sunHour; case 3 -> sunMinute;
                default -> throw new IllegalArgumentException("Invalid hand index"); };
        }
        public Frequency withHand(int index, int value) {
            int[] hands = configured() ? new int[]{moonHour, moonMinute, sunHour, sunMinute} : new int[4];
            hands[index] = value;
            return new Frequency(hands[0], hands[1], hands[2], hands[3]);
        }
    }
    public record RadioState(boolean enabled, List<Frequency> channels, int transmitIndex, UUID deviceId) {
        public RadioState {
            channels = List.copyOf(channels);
            if (channels.size() != CHANNEL_COUNT || transmitIndex < 0 || transmitIndex >= CHANNEL_COUNT)
                throw new IllegalArgumentException("Invalid channels");
        }
        public Optional<Frequency> transmitFrequency() {
            Frequency f = channels.get(transmitIndex);
            return enabled && f.configured() ? Optional.of(f) : Optional.empty();
        }
        public List<Frequency> configuredChannels() { return channels.stream().filter(Frequency::configured).toList(); }
        public List<Frequency> listeningFrequencies() { return enabled ? configuredChannels() : List.of(); }
    }
}
