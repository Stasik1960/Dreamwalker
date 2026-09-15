package com.coraxberg.dwmagicconnect.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Owns the namespaced NBT stored directly on an arbitrary radio ItemStack. */
public final class MagicConnectData {
    public static final String ROOT_KEY = "DWMagicConnect";
    public static final int CHANNEL_COUNT = 3;
    public static final int MAX_CHANNEL_LENGTH = 3;

    private static final String MAGIC_RADIO_KEY = "MagicRadio";
    private static final String ENABLED_KEY = "Enabled";
    private static final String CHANNELS_KEY = "Channels";
    private static final String CHANNEL_A_KEY = "A";
    private static final String CHANNEL_B_KEY = "B";
    private static final String TRANSMIT_INDEX_KEY = "TransmitIndex";
    private static final String DEVICE_ID_KEY = "DeviceId";
    private static final String LEGACY_FREQUENCY_A_KEY = "FrequencyA";
    private static final String LEGACY_FREQUENCY_B_KEY = "FrequencyB";
    private static final String LEGACY_CONFIGURED_KEY = "Configured";

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
        radio.put(CHANNELS_KEY, emptyChannelsTag());
        radio.putInt(TRANSMIT_INDEX_KEY, 0);
        radio.putUuid(DEVICE_ID_KEY, UUID.randomUUID());
        stack.getOrCreateNbt().put(ROOT_KEY, radio);
        return true;
    }

    public static boolean removeRadio(ItemStack stack) {
        if (!isRadio(stack)) return false;
        NbtCompound root = stack.getNbt();
        if (root == null) return false;
        root.remove(ROOT_KEY);
        if (root.isEmpty()) stack.setNbt(null);
        return true;
    }

    public static UUID ensureDeviceId(ItemStack stack) {
        if (!isRadio(stack)) throw new IllegalArgumentException("ItemStack is not a DW Magic Connect radio");
        NbtCompound radio = stack.getOrCreateNbt().getCompound(ROOT_KEY);
        if (!radio.containsUuid(DEVICE_ID_KEY)) radio.putUuid(DEVICE_ID_KEY, UUID.randomUUID());
        return radio.getUuid(DEVICE_ID_KEY);
    }

    public static Optional<UUID> deviceId(ItemStack stack) {
        NbtCompound radio = radioTag(stack);
        if (radio == null || !radio.containsUuid(DEVICE_ID_KEY)) return Optional.empty();
        return Optional.of(radio.getUuid(DEVICE_ID_KEY));
    }

    /** Reads immutable state, migrating safe legacy numeric values on first read. */
    public static RadioState read(ItemStack stack) {
        NbtCompound radio = radioTag(stack);
        if (radio == null) throw new IllegalArgumentException("ItemStack is not a DW Magic Connect radio");
        return readTag(radio, ensureDeviceId(stack));
    }

    static RadioState readTag(NbtCompound radio, UUID deviceId) {
        if (!radio.contains(CHANNELS_KEY, NbtElement.LIST_TYPE)) {
            RadioState migrated = readLegacy(radio, deviceId);
            if (migrated != null) {
                writeChannels(radio, migrated.channels());
                radio.putInt(TRANSMIT_INDEX_KEY, migrated.transmitIndex());
                radio.putBoolean(ENABLED_KEY, migrated.enabled());
                return migrated;
            }
            // Do not truncate an out-of-range legacy value or rewrite its NBT.
            return new RadioState(false, emptyChannels(), 0, deviceId);
        }

        return new RadioState(
                radio.getBoolean(ENABLED_KEY),
                readChannels(radio.getList(CHANNELS_KEY, NbtElement.COMPOUND_TYPE)),
                validTransmitIndex(radio.getInt(TRANSMIT_INDEX_KEY)),
                deviceId
        );
    }

    /** Saves all three slots and the separately selected transmit slot. */
    public static void saveSettings(ItemStack stack, boolean enabled, List<Frequency> channels, int transmitIndex) {
        if (!isRadio(stack)) throw new IllegalArgumentException("ItemStack is not a DW Magic Connect radio");
        ensureDeviceId(stack);
        saveTag(stack.getOrCreateNbt().getCompound(ROOT_KEY), enabled, channels, transmitIndex);
    }

    static void saveTag(NbtCompound radio, boolean enabled, List<Frequency> channels, int transmitIndex) {
        List<Frequency> normalized = normalizeChannels(channels);
        if (transmitIndex < 0 || transmitIndex >= CHANNEL_COUNT) {
            throw new IllegalArgumentException("Transmit slot is outside the allowed range");
        }
        if (enabled && !normalized.get(transmitIndex).configured()) {
            throw new IllegalArgumentException("Enabled radio must transmit on a configured slot");
        }
        radio.putBoolean(ENABLED_KEY, enabled);
        writeChannels(radio, normalized);
        radio.putInt(TRANSMIT_INDEX_KEY, transmitIndex);
        radio.remove(LEGACY_FREQUENCY_A_KEY);
        radio.remove(LEGACY_FREQUENCY_B_KEY);
        radio.remove(LEGACY_CONFIGURED_KEY);
    }

    public static Optional<Frequency> transmitFrequency(ItemStack stack) {
        if (!isRadio(stack)) return Optional.empty();
        RadioState state = read(stack);
        return state.transmitFrequency();
    }

    public static List<Frequency> listeningFrequencies(ItemStack stack) {
        if (!isRadio(stack)) return List.of();
        RadioState state = read(stack);
        return state.listeningFrequencies();
    }

    /** A channel is either an empty slot or a complete pair of valid values. */
    public static boolean isFrequencyPairValid(String a, String b) {
        boolean emptyA = a == null || a.isEmpty();
        boolean emptyB = b == null || b.isEmpty();
        return emptyA == emptyB && (emptyA || (isChannelValid(a) && isChannelValid(b)));
    }

    public static boolean isChannelValid(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_CHANNEL_LENGTH) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (!isAllowedChannelCharacter(character)) return false;
        }
        return true;
    }

    public static List<Frequency> emptyChannels() {
        return List.of(new Frequency("", ""), new Frequency("", ""), new Frequency("", ""));
    }

    private static boolean isAllowedChannelCharacter(char character) {
        return character >= '0' && character <= '9'
                || character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '\u0410' && character <= '\u042F'
                || character >= '\u0430' && character <= '\u044F'
                || character == '\u0401' || character == '\u0451';
    }

    private static String canonicalChannel(String value) {
        if (value == null || value.isEmpty()) return "";
        if (!isChannelValid(value)) {
            throw new IllegalArgumentException("Channel values must contain 1-3 Latin/Cyrillic letters or digits");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static List<Frequency> normalizeChannels(List<Frequency> channels) {
        if (channels == null || channels.size() != CHANNEL_COUNT) {
            throw new IllegalArgumentException("Exactly three channel slots are required");
        }
        List<Frequency> normalized = new ArrayList<>(CHANNEL_COUNT);
        for (Frequency frequency : channels) {
            if (frequency == null || !isFrequencyPairValid(frequency.a(), frequency.b())) {
                throw new IllegalArgumentException("Each channel slot must be empty or a complete valid pair");
            }
            normalized.add(new Frequency(frequency.a(), frequency.b()));
        }
        return Collections.unmodifiableList(normalized);
    }

    private static List<Frequency> readChannels(NbtList channelsTag) {
        List<Frequency> channels = new ArrayList<>(CHANNEL_COUNT);
        for (int index = 0; index < CHANNEL_COUNT; index++) {
            if (index >= channelsTag.size() || !(channelsTag.get(index) instanceof NbtCompound channelTag)) {
                channels.add(new Frequency("", ""));
                continue;
            }
            String a = channelTag.getString(CHANNEL_A_KEY);
            String b = channelTag.getString(CHANNEL_B_KEY);
            channels.add(isFrequencyPairValid(a, b) ? new Frequency(a, b) : new Frequency("", ""));
        }
        return Collections.unmodifiableList(channels);
    }

    private static RadioState readLegacy(NbtCompound radio, UUID deviceId) {
        if (!radio.contains(LEGACY_FREQUENCY_A_KEY, NbtElement.INT_TYPE)
                || !radio.contains(LEGACY_FREQUENCY_B_KEY, NbtElement.INT_TYPE)) {
            return new RadioState(radio.getBoolean(ENABLED_KEY), emptyChannels(), 0, deviceId);
        }
        int legacyA = radio.getInt(LEGACY_FREQUENCY_A_KEY);
        int legacyB = radio.getInt(LEGACY_FREQUENCY_B_KEY);
        if (legacyA < 0 || legacyA > 999 || legacyB < 0 || legacyB > 999) return null;

        List<Frequency> channels = new ArrayList<>(emptyChannels());
        boolean configured = radio.getBoolean(LEGACY_CONFIGURED_KEY);
        if (configured) channels.set(0, new Frequency(Integer.toString(legacyA), Integer.toString(legacyB)));
        return new RadioState(radio.getBoolean(ENABLED_KEY) && configured, channels, 0, deviceId);
    }

    private static int validTransmitIndex(int index) {
        return index >= 0 && index < CHANNEL_COUNT ? index : 0;
    }

    private static NbtList emptyChannelsTag() {
        NbtList channels = new NbtList();
        for (int index = 0; index < CHANNEL_COUNT; index++) {
            NbtCompound channel = new NbtCompound();
            channel.putString(CHANNEL_A_KEY, "");
            channel.putString(CHANNEL_B_KEY, "");
            channels.add(channel);
        }
        return channels;
    }

    private static void writeChannels(NbtCompound radio, List<Frequency> channels) {
        NbtList channelsTag = new NbtList();
        for (Frequency frequency : channels) {
            NbtCompound channel = new NbtCompound();
            channel.putString(CHANNEL_A_KEY, frequency.a());
            channel.putString(CHANNEL_B_KEY, frequency.b());
            channelsTag.add(channel);
        }
        radio.put(CHANNELS_KEY, channelsTag);
    }

    private static NbtCompound radioTag(ItemStack stack) {
        if (!isRadio(stack)) return null;
        return stack.getNbt().getCompound(ROOT_KEY);
    }

    public record Frequency(String a, String b) {
        public Frequency {
            a = canonicalChannel(a);
            b = canonicalChannel(b);
        }

        public boolean configured() {
            return !a.isEmpty() && !b.isEmpty();
        }
    }

    public record RadioState(boolean enabled, List<Frequency> channels, int transmitIndex, UUID deviceId) {
        public RadioState {
            if (channels == null || channels.size() != CHANNEL_COUNT) {
                throw new IllegalArgumentException("Exactly three channel slots are required");
            }
            channels = Collections.unmodifiableList(new ArrayList<>(channels));
        }

        public Optional<Frequency> transmitFrequency() {
            Frequency frequency = channels.get(transmitIndex);
            return enabled && frequency.configured() ? Optional.of(frequency) : Optional.empty();
        }

        public List<Frequency> listeningFrequencies() {
            return enabled ? configuredChannels() : List.of();
        }

        public List<Frequency> configuredChannels() {
            return channels.stream().filter(Frequency::configured).toList();
        }
    }
}
