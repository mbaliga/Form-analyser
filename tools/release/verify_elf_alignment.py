#!/usr/bin/env python3
"""Fail release preflight when a packaged 64-bit ELF load segment is not 16 KB aligned."""

from __future__ import annotations

import struct
import sys
import zipfile
from pathlib import Path


PAGE_ALIGNMENT = 16 * 1024
ABI_64 = {"arm64-v8a", "x86_64"}
PT_LOAD = 1


class ElfAlignmentError(ValueError):
    """An ELF shared library is malformed or not compatible with 16 KB pages."""


def load_segment_alignments(binary: bytes) -> list[int]:
    if len(binary) < 64 or binary[:4] != b"\x7fELF":
        raise ElfAlignmentError("not a valid ELF shared library")

    elf_class, elf_data = binary[4], binary[5]
    if elf_class not in (1, 2) or elf_data not in (1, 2):
        raise ElfAlignmentError("unsupported ELF class or byte order")
    endian = "<" if elf_data == 1 else ">"

    try:
        if elf_class == 1:
            _, _, _, _, phoff, _, _, _, phentsize, phnum, _, _, _ = struct.unpack_from(
                endian + "HHIIIIIHHHHHH", binary, 16
            )
            expected_phentsize = 32
            ph_format = endian + "IIIIIIII"
            align_index = 7
        else:
            _, _, _, _, phoff, _, _, _, phentsize, phnum, _, _, _ = struct.unpack_from(
                endian + "HHIQQQIHHHHHH", binary, 16
            )
            expected_phentsize = 56
            ph_format = endian + "IIQQQQQQ"
            align_index = 7

        if phentsize < expected_phentsize or phoff + phentsize * phnum > len(binary):
            raise ElfAlignmentError("invalid ELF program-header table")

        alignments = []
        for index in range(phnum):
            header = struct.unpack_from(ph_format, binary, phoff + index * phentsize)
            if header[0] == PT_LOAD:
                alignments.append(header[align_index])
    except struct.error as error:
        raise ElfAlignmentError("truncated ELF header") from error

    if not alignments:
        raise ElfAlignmentError("ELF has no loadable segments")
    return alignments


def verify_entries(artifact: str, entries: dict[str, bytes]) -> int:
    checked = 0
    for name, contents in sorted(entries.items()):
        if not name.endswith(".so"):
            continue
        path_parts = name.split("/")
        abi = next((part for part in path_parts if part in ABI_64), None)
        if abi is None:
            continue

        alignments = load_segment_alignments(contents)
        bad = [alignment for alignment in alignments if alignment < PAGE_ALIGNMENT or alignment % PAGE_ALIGNMENT]
        if bad:
            rendered = ", ".join(f"0x{alignment:x}" for alignment in bad)
            raise ElfAlignmentError(
                f"{artifact}: {name} has PT_LOAD alignment(s) {rendered}; "
                f"requires multiples of 0x{PAGE_ALIGNMENT:x}"
            )
        checked += 1

    if checked == 0:
        raise ElfAlignmentError(f"{artifact}: no arm64-v8a or x86_64 shared libraries found")
    print(f"16 KB ELF alignment passed: {artifact} ({checked} 64-bit shared libraries)")
    return checked


def verify_archive(path: Path) -> int:
    try:
        with zipfile.ZipFile(path) as archive:
            entries = {
                name: archive.read(name)
                for name in archive.namelist()
                if name.endswith(".so")
                and any(f"/{abi}/" in name for abi in ABI_64)
            }
    except (OSError, zipfile.BadZipFile) as error:
        raise ElfAlignmentError(f"{path}: cannot read Android archive: {error}") from error
    return verify_entries(str(path), entries)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(f"usage: {Path(argv[0]).name} APK AAB", file=sys.stderr)
        return 2
    try:
        for artifact in argv[1:]:
            verify_archive(Path(artifact))
    except ElfAlignmentError as error:
        print(f"16 KB ELF alignment failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
