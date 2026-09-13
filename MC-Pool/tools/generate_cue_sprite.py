"""Create a small transparent cue sprite for the aiming overlay."""

import struct
import zlib
from pathlib import Path


WIDTH, HEIGHT = 128, 16
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/poolbilliards/textures/gui/cue.png"
OUT.parent.mkdir(parents=True, exist_ok=True)


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


rows = bytearray()
for y in range(HEIGHT):
    rows.append(0)
    for x in range(WIDTH):
        radius = 2.1 + min(3.4, x * 0.027)
        distance = abs(y - 7.5)
        if distance > radius:
            color = (0, 0, 0, 0)
        elif x < 5:
            color = (91, 156, 151, 255)  # chalked leather tip
        elif x < 12:
            color = (232, 220, 179, 255)  # ivory ferrule
        elif x < 90:
            shade = int((radius - distance) * 5)
            color = (195 + shade, 145 + shade, 79 + shade // 2, 255)
        elif x < 95 or x >= 123:
            color = (189, 150, 79, 255)  # metal rings
        else:
            shade = int((radius - distance) * 3)
            color = (66 + shade, 38 + shade, 29 + shade, 255)
        rows.extend(color)

png = b"\x89PNG\r\n\x1a\n"
png += chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(bytes(rows), 9))
png += chunk(b"IEND", b"")
OUT.write_bytes(png)
print(f"Wrote {OUT}")
