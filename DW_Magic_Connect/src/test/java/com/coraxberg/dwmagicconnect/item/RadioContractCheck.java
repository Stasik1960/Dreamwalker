package com.coraxberg.dwmagicconnect.item;

import com.coraxberg.dwmagicconnect.client.ClockMath;
import com.coraxberg.dwmagicconnect.client.RadioScreenLayout;
import com.coraxberg.dwmagicconnect.item.MagicConnectData.Frequency;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.List;
import java.util.UUID;

public final class RadioContractCheck {
    public static void main(String[] args) {
        checkGeometry();
        checkSingleFrequency();
        checkV3Migration();
        checkLayout();
        System.out.println("Radio checks passed: clock, NBT v4, v3 migration, radius, GUI bounds.");
    }

    private static void checkGeometry() {
        for (int steps : new int[]{12, 60}) for (int i = 0; i < steps; i++) {
            check(ClockMath.snap(ClockMath.x(i, steps, 50), ClockMath.y(i, steps, 50), steps) == i, "round trip");
        }
        check(ClockMath.snap(-.01, -50, 60) == 0 && ClockMath.snap(.01, -50, 60) == 0, "wrap twelve");
        for (int r : new int[]{31, 75}) for (int h = 0; h < 12; h++) for (int m = 0; m < 60; m++) {
            check(ClockMath.pick(ClockMath.x(h, 12, r * .48), ClockMath.y(h, 12, r * .48), h, m, r) == 0, "hour grab");
            check(ClockMath.pick(ClockMath.x(m, 60, r * .78), ClockMath.y(m, 60, r * .78), h, m, r) == 1, "minute grab");
        }
        check(ClockMath.pick(0, 0, 0, 0, 31) == -1, "pivot");
        check(ClockMath.pick(100, 100, 0, 0, 31) == -1, "outside");
    }

    private static void checkSingleFrequency() {
        Frequency frequency = new Frequency(11, 59, 3, 17);
        check(frequency.equals(new Frequency(11, 59, 3, 17)), "matching hands");
        for (int hand = 0; hand < 4; hand++) check(!frequency.equals(frequency.withHand(hand, 0)), "every hand matters");
        check(Frequency.valid(-1, -1, -1, -1) && !Frequency.valid(-1, 0, 0, 0), "empty or complete");
        check(!Frequency.valid(12, 0, 0, 0) && !Frequency.valid(0, 60, 0, 0), "moon bounds");
        check(!Frequency.valid(0, 0, 12, 0) && !Frequency.valid(0, 0, 0, 60), "sun bounds");

        NbtCompound tag = new NbtCompound();
        UUID id = new UUID(0, 1);
        MagicConnectData.saveTag(tag, true, frequency, true, 1);
        var minimum = MagicConnectData.readTag(tag.copy(), id);
        check(minimum.equals(new MagicConnectData.RadioState(true, frequency, true, 1, id)), "v4 roundtrip minimum");
        MagicConnectData.saveTag(tag, false, Frequency.empty(), false, 10);
        check(MagicConnectData.readTag(tag.copy(), id).radius() == 10, "v4 maximum radius");
        expectInvalid(() -> MagicConnectData.saveTag(tag, true, frequency, true, 0));
        expectInvalid(() -> MagicConnectData.saveTag(tag, true, frequency, true, 11));
    }

    private static void checkV3Migration() {
        List<Frequency> oldChannels = List.of(
                new Frequency(1, 2, 3, 4),
                new Frequency(5, 6, 7, 8),
                new Frequency(9, 10, 11, 12)
        );
        NbtCompound tag = new NbtCompound();
        tag.putInt("ClockFormat", 3);
        tag.putBoolean("Enabled", true);
        tag.putBoolean("MagicRadio", true);
        tag.putInt("TransmitIndex", 1);
        NbtList channels = new NbtList();
        for (Frequency frequency : oldChannels) {
            NbtCompound channel = new NbtCompound();
            channel.putInt("MoonHour", frequency.moonHour());
            channel.putInt("MoonMinute", frequency.moonMinute());
            channel.putInt("SunHour", frequency.sunHour());
            channel.putInt("SunMinute", frequency.sunMinute());
            channels.add(channel);
        }
        tag.put("Channels", channels);
        UUID id = UUID.randomUUID();
        tag.putUuid("DeviceId", id);

        var migrated = MagicConnectData.readTag(tag, id);
        check(migrated.enabled(), "enabled preserved");
        check(migrated.frequency().equals(oldChannels.get(1)), "selected transmit frequency preserved");
        check(!migrated.speaker() && migrated.radius() == 10, "new fields defaulted");
        check(tag.getInt("ClockFormat") == 4 && !tag.contains("Channels") && !tag.contains("TransmitIndex"), "v3 removed");
        check(tag.getUuid("DeviceId").equals(id), "identity preserved");
    }

    private static void checkLayout() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {640, 360}, {960, 540}}) {
            var layout = RadioScreenLayout.fit(size[0], size[1]);
            check(layout.top() >= 0 && layout.footer() + 12 <= size[1], "vertical bounds");
            check(size[0] / 2 - layout.offset() - layout.radius() - 5 >= 0, "left clock");
            check(size[0] / 2 + layout.offset() + layout.radius() + 5 <= size[0], "right clock");
        }
        check(RadioScreenLayout.fit(320, 240).radius() == 31, "compact radius");
        check(RadioScreenLayout.fit(1200, 800).radius() == 75, "maximum radius");
    }

    private static void expectInvalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid settings");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
