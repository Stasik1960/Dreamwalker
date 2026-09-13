"""Generate small, seamless procedural textures for the billiards table."""

import math
import random
import struct
import zlib
from pathlib import Path


OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/poolbilliards/textures/block"
SIZE = 64


def png_chunk(kind, payload):
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))


def save(name, pixel):
    rng = random.Random(4107)
    rows = bytearray()
    for y in range(SIZE):
        rows.append(0)
        for x in range(SIZE):
            rows.extend(max(0, min(255, round(c))) for c in pixel(x, y, rng))
    data = b"\x89PNG\r\n\x1a\n"
    data += png_chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0))
    data += png_chunk(b"IDAT", zlib.compress(bytes(rows), 9))
    data += png_chunk(b"IEND", b"")
    (OUT / name).write_bytes(data)


def grain(x, y, rng, base, strength):
    wave = math.sin(y * 0.74 + math.sin(x * 0.19) * 1.3)
    fine = math.sin(y * 1.89 + x * 0.12)
    noise = rng.uniform(-2.5, 2.5)
    value = wave * strength + fine * strength * 0.28 + noise
    return tuple(channel + value * factor for channel, factor in zip(base, (1, 0.68, 0.42)))


save("pool_wood.png", lambda x, y, rng: grain(x, y, rng, (104, 54, 32), 7.0))
save("pool_rail_top.png", lambda x, y, rng: grain(x, y, rng, (137, 76, 43), 6.0))
save("pool_leg.png", lambda x, y, rng: grain(x, y, rng, (65, 38, 28), 4.5))


def felt(x, y, rng):
    fleck = rng.uniform(-5.0, 5.0)
    weave = (1 if (x + y) % 2 else -1) * 1.4
    return (10 + fleck * 0.35, 88 + fleck + weave, 65 + fleck * 0.72)


save("pool_felt.png", felt)


def leather(x, y, rng):
    speck = rng.uniform(-2.0, 2.0)
    return (18 + speck, 17 + speck, 16 + speck)


save("pool_black.png", leather)
print("Generated five 64x64 table textures")
