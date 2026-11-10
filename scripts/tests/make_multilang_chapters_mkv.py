#!/usr/bin/env python3
"""Builds app/src/test/resources/mkv-chapters/multilang.mkv (NuvioArcKai, skip-segment tests).

Takes front.mkv (same folder), keeps its header, tracks and clusters byte for byte, and swaps the
Chapters element for one default edition whose chapters each carry an English and a Spanish name:

    0 s     Prologue / Prólogo
    60 s    Opening  / Apertura
    150 s   Part A   / Parte A
    1200 s  Ending   / Créditos
    1290 s  Preview  / Avance

The new Chapters element plus an EBML Void fill exactly the bytes the old Void+Chapters+Void used, so
cluster offsets, Cues and the SeekHead position stay valid. Run from the repository root:

    python3 scripts/tests/make_multilang_chapters_mkv.py
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
FOLDER = ROOT / "app/src/test/resources/mkv-chapters"

CHAPTERS = [
    (0, "Prologue", "Prólogo"),
    (60, "Opening", "Apertura"),
    (150, "Part A", "Parte A"),
    (1200, "Ending", "Créditos"),
    (1290, "Preview", "Avance"),
]


def vint_size(n: int) -> bytes:
    for length in range(1, 9):
        if n < (1 << (7 * length)) - 1:
            return (n | (1 << (7 * length))).to_bytes(length, "big")
    raise ValueError(n)


def element(eid: int, body: bytes) -> bytes:
    return eid.to_bytes((eid.bit_length() + 7) // 8, "big") + vint_size(len(body)) + body


def uint(eid: int, value: int, width: int = 0) -> bytes:
    width = width or max(1, (value.bit_length() + 7) // 8)
    return element(eid, value.to_bytes(width, "big"))


def text(eid: int, value: str) -> bytes:
    return element(eid, value.encode("utf-8"))


def display(name: str, language: str, ietf: str) -> bytes:
    return element(0x80, text(0x85, name) + text(0x437C, language) + text(0x437D, ietf))


def chapters_element() -> bytes:
    atoms = b""
    for index, (start_s, english, spanish) in enumerate(CHAPTERS):
        atoms += element(
            0xB6,
            uint(0x73C4, 0x4B41490000000000 + index + 1, 8)
            + uint(0x91, start_s * 1_000_000_000)
            + display(english, "eng", "en")
            + display(spanish, "spa", "es"),
        )
    edition = element(0x45B9, uint(0x45DB, 1) + uint(0x45BC, 0x4B41490000000100, 8) + atoms)
    return element(0x1043A770, edition)


def read_vint(data: bytes, i: int, mask: bool = True):
    first = data[i]
    length, marker = 1, 0x80
    while not first & marker:
        marker >>= 1
        length += 1
    value = first & (marker - 1) if mask else first
    for k in range(1, length):
        value = (value << 8) | data[i + k]
    return value, length


def top_level(data: bytes, start: int, end: int):
    i = start
    while i < end:
        eid, l1 = read_vint(data, i, mask=False)
        size, l2 = read_vint(data, i + l1)
        yield eid, i, i + l1 + l2, size
        i = i + l1 + l2 + size


def void(total: int) -> bytes:
    # Void of exactly `total` bytes: 1-byte ID, 8-byte size field (fixed width keeps the arithmetic simple).
    if total < 9:
        raise ValueError("not enough room for a Void element")
    body = total - 9
    return b"\xEC" + (body | (1 << 56)).to_bytes(8, "big") + b"\x00" * body


def main() -> int:
    data = bytearray((FOLDER / "front.mkv").read_bytes())
    seg_id, seg_at, seg_body, seg_size = next(e for e in top_level(data, 0, len(data)) if e[0] == 0x18538067)
    children = list(top_level(data, seg_body, seg_body + seg_size))
    index = next(n for n, c in enumerate(children) if c[0] == 0x1043A770)
    before, chapters, after = children[index - 1], children[index], children[index + 1]
    assert before[0] == 0xEC and after[0] == 0xEC, "front.mkv layout changed: Void, Chapters, Void expected"
    region_start = before[1]
    region_end = after[2] + after[3]
    new_chapters = chapters_element()
    filler = region_end - region_start - len(new_chapters)
    replacement = new_chapters + void(filler)
    assert len(replacement) == region_end - region_start
    data[region_start:region_end] = replacement

    # SeekHead: point the Chapters entry at the new position (relative to the segment body).
    for eid, at, body, size in children:
        if eid != 0x114D9B74:
            continue
        for seek_id, seek_at, seek_body, seek_size in top_level(data, body, body + size):
            entries = list(top_level(data, seek_body, seek_body + seek_size))
            ident = next((e for e in entries if e[0] == 0x53AB), None)
            position = next((e for e in entries if e[0] == 0x53AC), None)
            if ident and position and bytes(data[ident[2]:ident[2] + ident[3]]) == bytes.fromhex("1043A770"):
                width = position[3]
                data[position[2]:position[2] + width] = (region_start - seg_body).to_bytes(width, "big")

    out = FOLDER / "multilang.mkv"
    out.write_bytes(bytes(data))
    print(f"wrote {out.relative_to(ROOT)} ({len(data)} bytes, chapters {len(new_chapters)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
