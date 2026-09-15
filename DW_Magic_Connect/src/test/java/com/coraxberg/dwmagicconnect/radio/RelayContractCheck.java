package com.coraxberg.dwmagicconnect.radio;

import com.coraxberg.dwmagicconnect.item.MagicConnectData;

import java.util.UUID;

public final class RelayContractCheck {
    public static void main(String[] args) {
        var frequency = new MagicConnectData.Frequency(0, 10, 0, 40);
        var ordinary = new MagicConnectData.RadioState(true, frequency, false, 10, UUID.randomUUID());
        var speaker = new MagicConnectData.RadioState(true, frequency, true, 3, UUID.randomUUID());
        check(MagicRelayService.captures(ordinary, true, true, true, 0), "held owner transmits");
        check(!MagicRelayService.captures(ordinary, false, true, true, 0), "ordinary inventory is silent");
        check(!MagicRelayService.captures(ordinary, true, false, true, 0), "ordinary does not capture another speaker");
        check(MagicRelayService.captures(speaker, false, false, true, 9), "speaker captures at edge");
        check(!MagicRelayService.captures(speaker, false, false, true, 9.01), "speaker radius enforced");
        check(!MagicRelayService.captures(speaker, false, false, false, 0), "speaker world enforced");
        check(MagicRelayService.frequencyLabel(frequency).equals("[12:10 - 12:40] "), "holder label");
        check(MagicRelayService.preferHolderFrequency(null, frequency).equals(frequency), "holder label wins dedup");
        check(MagicRelayService.preferHolderFrequency(frequency, null).equals(frequency), "holder label stays on dedup");
        System.out.println("Relay checks passed: held/speaker capture, radius, labels, dedup preference.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
