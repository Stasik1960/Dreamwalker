package com.coraxberg.dwmagicconnect.item;

import com.coraxberg.dwmagicconnect.client.RadioScreenLayout;
import com.coraxberg.dwmagicconnect.item.MagicConnectData;
import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import net.minecraft.nbt.NbtCompound;

import java.util.List;
import java.util.UUID;

/** Small executable contract check for the radio model; it does not launch Minecraft. */
public final class RadioContractCheck {
    private RadioContractCheck() {
    }

    public static void main(String[] args) {
        checkChannels();
        checkTransmitAndListenSlots();
        checkLegacyMigration();
        checkLayoutSizes();
        System.out.println("Radio checks passed: validation, channels, transmission, NBT migration and four GUI sizes.");
    }

    private static void checkChannels() {
        check(MagicConnectData.isChannelValid("A1Ж"), "Latin/Cyrillic channel should be valid");
        check(!MagicConnectData.isChannelValid("A1Ж4"), "four-character channel should be rejected");
        check(!MagicConnectData.isChannelValid("A-"), "punctuation should be rejected");
        check(!MagicConnectData.isFrequencyPairValid("A", ""), "partial channel pair should be rejected");
        Frequency frequency = new Frequency("aж1", "б2");
        check(frequency.a().equals("AЖ1") && frequency.b().equals("Б2"), "channels should canonicalize to uppercase");
        check(MagicConnectData.isFrequencyPairValid("", ""), "empty channel slot should be valid");
    }

    private static void checkTransmitAndListenSlots() {
        NbtCompound radio = new NbtCompound();
        List<Frequency> channels = List.of(new Frequency("a", "1"), new Frequency("b", "2"), new Frequency("", ""));
        MagicConnectData.saveTag(radio, true, channels, 1);
        check(MagicConnectData.readTag(radio, new UUID(0, 1)).transmitFrequency().orElseThrow().equals(new Frequency("B", "2")),
                "selected slot should transmit");
        check(MagicConnectData.readTag(radio, new UUID(0, 1)).listeningFrequencies().equals(List.of(new Frequency("A", "1"), new Frequency("B", "2"))),
                "all configured slots should listen");

        List<Frequency> three = List.of(new Frequency("a", "1"), new Frequency("b", "2"), new Frequency("c", "3"));
        for (int slot = 0; slot < 3; slot++) {
            MagicConnectData.saveTag(radio, true, three, slot);
            var state = MagicConnectData.readTag(radio.copy(), new UUID(0, 1));
            check(state.listeningFrequencies().size() == 3, "all three slots must listen after NBT reload");
            check(state.transmitFrequency().orElseThrow().equals(three.get(slot)), "only selected slot transmits");
        }
        expectIllegalArgument(() -> MagicConnectData.saveTag(radio, true, channels, 2));
        expectIllegalArgument(() -> MagicConnectData.saveTag(radio, true, channels, 3));

        MagicConnectData.saveTag(radio, false, channels, 1);
        check(MagicConnectData.readTag(radio, new UUID(0, 1)).transmitFrequency().isEmpty(), "disabled radio should not transmit");
        check(MagicConnectData.readTag(radio, new UUID(0, 1)).listeningFrequencies().isEmpty(), "disabled radio should not listen");

        expectIllegalArgument(() -> MagicConnectData.saveTag(
                radio, true, List.of(new Frequency("A", ""), new Frequency("", ""), new Frequency("", "")), 0));
    }

    private static void checkLegacyMigration() {
        NbtCompound migrated = legacyRadio(12, 345);
        MagicConnectData.RadioState state = MagicConnectData.readTag(migrated, new UUID(0, 1));
        check(state.enabled(), "safe legacy configuration should preserve enabled state");
        check(state.channels().get(0).equals(new Frequency("12", "345")), "legacy values should migrate to slot zero");
        check(migrated.contains("Channels"), "migration should write channel slots");

        NbtCompound preserved = legacyRadio(1000, 1);
        String before = preserved.toString();
        MagicConnectData.RadioState preservedState = MagicConnectData.readTag(preserved, new UUID(0, 1));
        NbtCompound preservedTag = preserved;
        check(!preservedState.enabled() && preservedState.channels().stream().noneMatch(Frequency::configured),
                "out-of-range legacy configuration should be disabled and unconfigured");
        check(!preservedTag.contains("Channels") && preservedTag.getInt("FrequencyA") == 1000,
                "out-of-range legacy NBT should remain untouched until save");
        check(before.equals(preservedTag.toString()), "legacy preservation should not rewrite the original NBT");
    }

    private static NbtCompound legacyRadio(int a, int b) {
        NbtCompound radio = new NbtCompound();
        radio.putBoolean("MagicRadio", true);
        radio.putBoolean("Enabled", true);
        radio.putInt("FrequencyA", a);
        radio.putInt("FrequencyB", b);
        radio.putBoolean("Configured", true);
        radio.putUuid("DeviceId", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        return radio;
    }

    private static void checkLayoutSizes() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            RadioScreenLayout layout = RadioScreenLayout.fit(size[0], size[1]);
            check(layout.controlsWidth() >= 190, "layout controls should remain usable");
            check(layout.top() - 17 >= 0, "layout title should remain on-screen");
            check(layout.top() + 161 <= size[1], "layout buttons should remain on-screen");
        }
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
