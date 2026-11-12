"""Prepare the licensed field recording used by the continuous wheel loop.

Source: "skateboard rolling slow 2" by FOSSarts, Freesound sound 740125.
License: Creative Commons Zero 1.0.

The official Freesound preview represents the 96 kHz stereo recording described
on the source page. This script selects a steady pass, folds it to mono, removes
subsonic handling noise, and crossfades the loop boundary. It does not synthesize
or layer tones over the recording.
"""

from __future__ import annotations

import hashlib
import urllib.request
from pathlib import Path

import numpy as np
import soundfile as sf


SOURCE_URL = "https://cdn.freesound.org/previews/740/740125_14631530-lq.ogg"
SOURCE_SHA256 = "f54d7c2cffab290d9af40c7f5fc017817a8e832a479b1afdb12cfc7668a82e7b"
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/assets/night_shift_skate/sounds/skate_roll_loop.ogg"


def read_source() -> tuple[np.ndarray, int]:
    cache = ROOT / "build/audio-source/740125__fossarts__skateboard-rolling-slow-2.ogg"
    cache.parent.mkdir(parents=True, exist_ok=True)
    if not cache.is_file():
        urllib.request.urlretrieve(SOURCE_URL, cache)
    digest = hashlib.sha256(cache.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise RuntimeError(f"Source checksum mismatch: {digest}")
    audio, sample_rate = sf.read(cache, always_2d=True, dtype="float64")
    return audio.mean(axis=1), sample_rate


def high_pass(signal: np.ndarray, sample_rate: int, cutoff: float = 35.0) -> np.ndarray:
    coefficient = np.exp(-2.0 * np.pi * cutoff / sample_rate)
    filtered = np.empty_like(signal)
    filtered[0] = 0.0
    for index in range(1, len(signal)):
        filtered[index] = coefficient * (filtered[index - 1] + signal[index] - signal[index - 1])
    return filtered


def seamless_loop(source: np.ndarray, sample_rate: int) -> np.ndarray:
    # The opening impact has passed by 0.45 s. The following 2.40 s contains
    # stable urethane-on-pavement texture with several natural axle transients.
    clip = source[round(0.45 * sample_rate):round(2.85 * sample_rate)].copy()
    clip -= np.mean(clip)
    clip = high_pass(clip, sample_rate)

    overlap = round(0.22 * sample_rate)
    loop = clip[:-overlap].copy()
    phase = np.linspace(0.0, np.pi / 2.0, overlap, endpoint=False)
    loop[:overlap] = clip[-overlap:] * np.cos(phase) + clip[:overlap] * np.sin(phase)

    peak = float(np.max(np.abs(loop)))
    rms = float(np.sqrt(np.mean(loop * loop)))
    gain = min(0.78 / peak, 0.055 / rms)
    return np.asarray(loop * gain, dtype=np.float32)


def main() -> None:
    source, sample_rate = read_source()
    loop = seamless_loop(source, sample_rate)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sf.write(OUTPUT, loop, sample_rate, format="OGG", subtype="VORBIS")
    decoded, decoded_rate = sf.read(OUTPUT, always_2d=False)
    print(
        f"{OUTPUT}: {len(decoded) / decoded_rate:.3f}s, {decoded_rate}Hz, "
        f"peak={np.max(np.abs(decoded)):.3f}, rms={np.sqrt(np.mean(decoded * decoded)):.3f}"
    )


if __name__ == "__main__":
    main()
