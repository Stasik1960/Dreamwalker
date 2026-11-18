"""Synthesize the Bestiary's original sound cues and encode them as Vorbis."""

import argparse
import math
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import wave

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/dw_npc_bestiary/sounds/mob"
RATE = 44100
DURATIONS = {"idle": 1.8, "hurt": 0.55, "death": 1.45, "attack": 0.8}


def synthesize(actor, cue, duration):
    weaver = actor == "mire_weaver"
    rng = random.Random(f"{actor}/{cue}")
    phase = low_noise = 0.0
    for index in range(int(RATE * duration)):
        t = index / RATE
        envelope = min(1.0, t / 0.025, (duration - t) / 0.12)
        noise = rng.uniform(-1, 1)
        low_noise += 0.19 * (noise - low_noise)
        if cue == "idle":
            frequency = (91 if weaver else 173) * (1 + 0.13 * math.sin(t * (14 if weaver else 21)))
            flutter = 0.54 + 0.46 * math.sin(t * (28 if weaver else 47)) ** 2
            noise_gain = 0.22 * flutter
        elif cue == "hurt":
            frequency = (435 if weaver else 655) * math.exp(-3.1 * t) + 68
            flutter = math.exp(-1.6 * t)
            noise_gain = 0.22
        elif cue == "attack":
            frequency = (95 if weaver else 180) + 420 * math.exp(-8.1 * t)
            flutter = math.exp(-3.4 * t)
            noise_gain = 0.41 * math.exp(-((t - 0.32) / 0.13) ** 2)
        else:
            frequency = (214 if weaver else 358) * math.exp(-2.75 * t) + 38
            flutter = math.exp(-1.4 * t) * (0.62 + 0.38 * math.sin(16 * t) ** 2)
            noise_gain = 0.24 * math.exp(-2.3 * t)
        phase += math.tau * frequency / RATE
        harmonics = 0.39 * math.sin(phase)
        if weaver:
            harmonics += 0.17 * math.sin(phase * 2) + 0.09 * math.sin(phase * 4)
        else:
            harmonics += 0.17 * math.sin(phase * 1.51 + 0.3) + 0.08 * math.sin(phase * 2.98)
        yield int(28000 * envelope * (flutter * harmonics + noise_gain * low_noise))


def write_wav(path, samples):
    with wave.open(str(path), "wb") as output:
        output.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        output.writeframes(b"".join(struct.pack("<h", value) for value in samples))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default="ffmpeg", help="FFmpeg executable with libvorbis support")
    options = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as temporary:
        wav = Path(temporary) / "cue.wav"
        for actor in ("mire_weaver", "vesper_warden"):
            for cue, duration in DURATIONS.items():
                write_wav(wav, synthesize(actor, cue, duration))
                subprocess.run([
                    options.ffmpeg, "-y", "-loglevel", "error", "-i", str(wav),
                    "-c:a", "libvorbis", "-q:a", "5", "-fflags", "+bitexact",
                    str(OUT / f"{actor}_{cue}.ogg"),
                ], check=True)


if __name__ == "__main__":
    main()
