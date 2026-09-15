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
        checkGeometry(); checkChannels(); checkReset(); checkLayout();
        System.out.println("Clock checks passed: snapping, overlapping hands, channels, NBT reset, GUI bounds.");
    }
    private static void checkGeometry() {
        for (int steps : new int[]{12, 60}) for (int i = 0; i < steps; i++) {
            check(ClockMath.snap(ClockMath.x(i, steps, 50), ClockMath.y(i, steps, 50), steps) == i, "round trip");
        }
        check(ClockMath.snap(-.01, -50, 60) == 0 && ClockMath.snap(.01, -50, 60) == 0, "wrap twelve");
        for (int r : new int[]{41, 64}) for (int h = 0; h < 12; h++) for (int m = 0; m < 60; m++) {
            check(ClockMath.pick(ClockMath.x(h, 12, r * .48), ClockMath.y(h, 12, r * .48), h, m, r) == 0, "hour grab");
            check(ClockMath.pick(ClockMath.x(m, 60, r * .78), ClockMath.y(m, 60, r * .78), h, m, r) == 1, "minute grab");
        }
        check(ClockMath.pick(0, 0, 0, 0, 41) == -1, "pivot");
        check(ClockMath.pick(100, 100, 0, 0, 41) == -1, "outside");
    }
    private static void checkChannels() {
        Frequency f = new Frequency(11, 59, 3, 17);
        check(f.equals(new Frequency(11, 59, 3, 17)), "matching hands");
        for (int hand = 0; hand < 4; hand++) check(!f.equals(f.withHand(hand, 0)), "every hand affects frequency");
        check(Frequency.valid(-1, -1, -1, -1) && !Frequency.valid(-1, 0, 0, 0), "empty or complete");
        check(!Frequency.valid(12, 0, 0, 0) && !Frequency.valid(0, 60, 0, 0), "moon bounds");
        check(!Frequency.valid(0, 0, 12, 0) && !Frequency.valid(0, 0, 0, 60), "sun bounds");
        check(!Frequency.valid(Integer.MAX_VALUE, 0, 0, 0), "oversized payload");
        check(Frequency.empty().withHand(1, 5).equals(new Frequency(0, 5, 0, 0)), "activate");
        NbtCompound tag = new NbtCompound();
        List<Frequency> channels = List.of(f, new Frequency(0, 0, 0, 0), new Frequency(2, 4, 6, 8));
        for (int tx = 0; tx < 3; tx++) {
            MagicConnectData.saveTag(tag, true, channels, tx);
            var state = MagicConnectData.readTag(tag.copy(), new UUID(0, 1));
            check(state.channels().equals(channels), "NBT roundtrip");
            check(state.listeningFrequencies().equals(channels), "three receivers");
            check(state.transmitFrequency().orElseThrow().equals(channels.get(tx)), "one transmitter");
        }
        MagicConnectData.saveTag(tag, false, channels, 0);
        var off = MagicConnectData.readTag(tag, new UUID(0, 1));
        check(off.listeningFrequencies().isEmpty() && off.transmitFrequency().isEmpty(), "off");
        expectInvalid(() -> MagicConnectData.saveTag(tag, true, MagicConnectData.emptyChannels(), 0));
        expectInvalid(() -> MagicConnectData.saveTag(tag, false, channels, 3));
        tag.put("Channels", new NbtList()); tag.putBoolean("Enabled", true);
        check(MagicConnectData.readTag(tag, new UUID(0, 1)).configuredChannels().isEmpty(), "missing hands");
    }
    private static void checkReset() {
        for (boolean oldList : new boolean[]{false, true}) {
            NbtCompound tag = new NbtCompound();
            tag.putBoolean("Enabled", true); tag.putInt("FrequencyA", 123); tag.putInt("FrequencyB", 321);
            tag.putBoolean("Configured", true);
            UUID id = UUID.randomUUID(); tag.putUuid("DeviceId", id);
            if (oldList) {
                NbtList list = new NbtList(); NbtCompound codes = new NbtCompound();
                codes.putString("A", "ABC"); codes.putString("B", "123"); list.add(codes); tag.put("Channels", list);
            }
            var state = MagicConnectData.readTag(tag, id);
            check(!state.enabled() && state.configuredChannels().isEmpty(), "old codes reset");
            check(tag.getUuid("DeviceId").equals(id), "identity preserved");
            check(!tag.contains("FrequencyA") && tag.getInt("ClockFormat") == 3, "format updated");
        }
    }
    private static void checkLayout() {
        for (int[] size : new int[][]{{320,240},{427,240},{640,360},{960,540}}) {
            var l = RadioScreenLayout.fit(size[0], size[1]);
            check(l.top() >= 0 && l.footer() + 64 <= size[1], "vertical bounds");
            check(size[0] / 2 - l.offset() - l.radius() - 5 >= 0, "left clock");
            check(size[0] / 2 + l.offset() + l.radius() + 5 <= size[0], "right clock");
        }
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid settings");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
