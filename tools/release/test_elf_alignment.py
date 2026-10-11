#!/usr/bin/env python3
"""Regression coverage for the 16 KB native-library release gate."""

import struct
import unittest

from verify_elf_alignment import ElfAlignmentError, load_segment_alignments, verify_entries


def elf64(*alignments: int) -> bytes:
    ident = b"\x7fELF" + bytes((2, 1, 1, 0, 0)) + bytes(7)
    header = struct.pack("<HHIQQQIHHHHHH", 3, 183, 1, 0, 64, 0, 0, 64, 56, len(alignments), 0, 0, 0)
    segments = b"".join(
        struct.pack("<IIQQQQQQ", 1, 5, 0, 0, 0, 0x1000, 0x1000, alignment)
        for alignment in alignments
    )
    return ident + header + segments


def elf32(alignment: int) -> bytes:
    ident = b"\x7fELF" + bytes((1, 1, 1, 0, 0)) + bytes(7)
    header = struct.pack("<HHIIIIIHHHHHH", 3, 40, 1, 0, 52, 0, 0, 52, 32, 1, 0, 0, 0)
    segment = struct.pack("<IIIIIIII", 1, 0, 0, 0, 0x1000, 0x1000, 5, alignment)
    return ident + header + segment


class ElfAlignmentTest(unittest.TestCase):
    def test_accepts_16_kb_aligned_elf64_load_segments(self) -> None:
        self.assertEqual(load_segment_alignments(elf64(0x4000, 0x10000)), [0x4000, 0x10000])

    def test_rejects_4_kb_aligned_64_bit_library(self) -> None:
        with self.assertRaisesRegex(ElfAlignmentError, "requires multiples of 0x4000"):
            verify_entries("candidate.apk", {"lib/arm64-v8a/libbad.so": elf64(0x1000)})

    def test_rejects_misaligned_load_segment_even_when_another_is_aligned(self) -> None:
        with self.assertRaises(ElfAlignmentError):
            verify_entries("candidate.aab", {"base/lib/x86_64/libbad.so": elf64(0x4000, 0x1000)})

    def test_requires_at_least_one_64_bit_shared_library(self) -> None:
        with self.assertRaisesRegex(ElfAlignmentError, "no arm64-v8a or x86_64"):
            verify_entries("candidate.apk", {"lib/armeabi-v7a/liblegacy.so": elf32(0x1000)})

    def test_rejects_truncated_or_non_elf_inputs(self) -> None:
        for data in (b"not an elf", elf64(0x4000)[:70]):
            with self.subTest(data=data[:12]), self.assertRaises(ElfAlignmentError):
                load_segment_alignments(data)


if __name__ == "__main__":
    unittest.main()
