"""Regression tests for safe trailing-sector handling in RegionFile."""

import struct
import unittest

from world_io import NbtError, NbtFile, RegionFile, TAG_COMPOUND, Tag


def region_with_chunk(*, sector=2, count=1, record=b"\x00\x00\x00\x01\x03"):
    data = bytearray(3 * RegionFile.SECTOR)
    data[:4] = ((sector << 8) | count).to_bytes(4, "big")
    start = sector * RegionFile.SECTOR
    if start < len(data):
        data[start:start + len(record)] = record
    return bytes(data)


class RegionPaddingTests(unittest.TestCase):
    def test_explicit_empty_or_short_file_is_not_a_new_region(self):
        for data in (b"", bytes(4096), bytes(8191)):
            with self.assertRaises(NbtError):
                RegionFile(data)

    def test_accepts_missing_unused_final_padding_and_preserves_bytes(self):
        source = region_with_chunk()[:-17]
        region = RegionFile(source)
        self.assertEqual(region.to_bytes(), source)
        self.assertEqual(region.get_chunk(0, 0).compression, 3)

    def test_rejects_unpaired_location_values(self):
        for value in ((2 << 8), 1):
            data = bytearray(2 * RegionFile.SECTOR)
            data[:4] = value.to_bytes(4, "big")
            with self.assertRaises(NbtError):
                RegionFile(bytes(data))

    def test_rejects_header_overlap_and_allocation_overlap(self):
        with self.assertRaises(NbtError):
            RegionFile(region_with_chunk(sector=1))
        data = bytearray(region_with_chunk())
        data[4:8] = ((2 << 8) | 1).to_bytes(4, "big")
        with self.assertRaises(NbtError):
            RegionFile(bytes(data))

    def test_rejects_truncated_payload_even_when_padding_would_cover_it(self):
        # Length says compression byte plus ten payload bytes, while actual EOF
        # comes before them.  Synthetic padding must never hide this corruption.
        record = struct.pack(">I", 11) + b"\x03abc"
        with self.assertRaises(NbtError):
            RegionFile(region_with_chunk(record=record)[:2 * RegionFile.SECTOR + len(record)])

    def test_rejects_invalid_length_and_beyond_eof_allocation(self):
        with self.assertRaises(NbtError):
            RegionFile(region_with_chunk(record=struct.pack(">I", 4097) + b"\x03"))
        with self.assertRaises(NbtError):
            RegionFile(region_with_chunk(sector=3)[:-1])

    def test_standard_writer_output_remains_aligned_and_readable(self):
        region = RegionFile()
        nbt = NbtFile("", Tag(TAG_COMPOUND, {}))
        region.set_chunk(0, 0, nbt, compression=3, timestamp=123)
        encoded = region.to_bytes()
        self.assertEqual(len(encoded) % RegionFile.SECTOR, 0)
        reread = RegionFile(encoded).get_chunk(0, 0)
        self.assertEqual(reread.timestamp, 123)
        self.assertEqual(reread.nbt(), nbt)

    def test_mutating_unpadded_file_aligns_only_output(self):
        region = RegionFile()
        nbt = NbtFile("", Tag(TAG_COMPOUND, {}))
        region.set_chunk(0,0,nbt,compression=3,timestamp=7)
        source = region.to_bytes()[:-17]
        reread = RegionFile(source)
        self.assertEqual(reread.get_chunk(0,0).nbt(),nbt)
        reread.set_chunk(1,0,nbt,compression=3,timestamp=8)
        self.assertNotEqual(reread.to_bytes(),source)
        self.assertEqual(len(reread.to_bytes()) % 4096,0)
        self.assertEqual(RegionFile(reread.to_bytes()).get_chunk(0,0).timestamp,7)
        self.assertEqual(len(source) % 4096,4096-17)


if __name__ == "__main__":
    unittest.main()
