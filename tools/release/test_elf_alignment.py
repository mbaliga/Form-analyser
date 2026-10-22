#!/usr/bin/env python3
"""Regression coverage for the 16 KB native-library release gate."""

import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_elf_alignment import (
    ElfAlignmentError,
    load_segment_alignments,
    verify_bundle_config,
    verify_archive,
    verify_entries,
)


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


def varint(value: int) -> bytes:
    encoded = bytearray()
    while value > 0x7F:
        encoded.append((value & 0x7F) | 0x80)
        value >>= 7
    encoded.append(value)
    return bytes(encoded)


def length_field(number: int, value: bytes) -> bytes:
    return varint((number << 3) | 2) + varint(len(value)) + value


def varint_field(number: int, value: int) -> bytes:
    return varint(number << 3) + varint(value)


def bundle_config(alignment: int = 2, enabled: int = 1) -> bytes:
    native = varint_field(1, enabled) + varint_field(2, alignment)
    optimizations = length_field(2, native)
    return length_field(2, optimizations)


class ElfAlignmentTest(unittest.TestCase):
    def test_accepts_bundle_requesting_uncompressed_16_kb_libraries(self) -> None:
        verify_bundle_config(bundle_config())

    def test_rejects_bundle_requesting_4_kb_alignment(self) -> None:
        with self.assertRaisesRegex(ElfAlignmentError, "expected PAGE_ALIGNMENT_16K"):
            verify_bundle_config(bundle_config(alignment=1))

    def test_rejects_bundle_that_compresses_native_libraries(self) -> None:
        with self.assertRaisesRegex(ElfAlignmentError, "must enable uncompressed"):
            verify_bundle_config(bundle_config(enabled=0))

    def test_rejects_missing_or_ambiguous_alignment_settings(self) -> None:
        with self.assertRaisesRegex(ElfAlignmentError, "missing or has ambiguous"):
            verify_bundle_config(length_field(2, length_field(2, varint_field(1, 1))))
        # A duplicate field in the nested native-library message must fail closed.
        native = varint_field(1, 1) + varint_field(2, 2) + varint_field(2, 2)
        with self.assertRaisesRegex(ElfAlignmentError, "ambiguous"):
            verify_bundle_config(length_field(2, length_field(2, native)))

    def test_rejects_malformed_bundle_config(self) -> None:
        with self.assertRaises(ElfAlignmentError):
            verify_bundle_config(b"\x12\x05\x12")

    def test_aab_requires_bundle_config(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            bundle_path = Path(temporary_directory) / "candidate.aab"
            with zipfile.ZipFile(bundle_path, "w") as archive:
                archive.writestr("base/lib/arm64-v8a/libsample.so", elf64(0x4000))
            with self.assertRaisesRegex(ElfAlignmentError, "missing BundleConfig.pb"):
                verify_archive(bundle_path)

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
