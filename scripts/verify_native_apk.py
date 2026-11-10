"""Validate the fork's required native playback components before distributing an APK."""
from __future__ import annotations
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path

MACHINES = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40)}
REQUIRED = {"libdovi_bridge.so", "libffmpegJNI.so", "libass.so", "libasskt.so"}


def verify(apk: Path, abi: str) -> dict:
    elf_class, machine = MACHINES[abi]
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entries")
        corrupt = archive.testzip()
        if corrupt:
            raise ValueError(f"Corrupt ZIP entry: {corrupt}")
        libraries = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        missing = REQUIRED - {name.rsplit("/", 1)[-1] for name in libraries if name.startswith(f"lib/{abi}/")}
        if missing:
            raise ValueError(f"Missing native playback libraries for {abi}: {sorted(missing)}")
        hashes = {}
        for name in libraries:
            if not name.startswith(f"lib/{abi}/"):
                raise ValueError(f"Unexpected ABI in single-ABI APK: {name}")
            data = archive.read(name)
            if len(data) < 20 or data[:4] != b"\x7fELF" or data[4] != elf_class or data[5] != 1:
                raise ValueError(f"Invalid ELF class/encoding: {name}")
            if struct.unpack_from("<H", data, 18)[0] != machine:
                raise ValueError(f"Wrong native architecture: {name}")
            hashes[name] = hashlib.sha256(data).hexdigest()
        bridge = archive.read(f"lib/{abi}/libdovi_bridge.so")
        if b"dovi-bridge-libdovi-capi-0.2" not in bridge or b"dovi_convert_rpu_with_mode" not in bridge:
            raise ValueError("Dolby Vision bridge lacks real libdovi linkage")
    return {"apk": str(apk), "abi": abi, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(), "nativeLibraries": hashes}


# NuvioArcKai: one armeabi-v7a APK, with every library of the measured baseline still present.
KAI_ABI = "armeabi-v7a"
KAI_REQUIRED = REQUIRED | {"libnuviothumb.so", "libc++_shared.so"}
KAI_MPV_MARKERS = ("libmpv.so", "libplayer.so")


def verify_kai(apk: Path, baseline: Path | None = None) -> dict:
    """Kai rules on top of verify(): ARM 32-bit only, mpv and thumbnail libraries, nothing lost from the baseline."""
    result = verify(apk, KAI_ABI)
    names = {name.rsplit("/", 1)[-1] for name in result["nativeLibraries"]}
    missing = KAI_REQUIRED - names
    if missing:
        raise ValueError(f"Missing Kai native libraries: {sorted(missing)}")
    if not any(marker in names for marker in KAI_MPV_MARKERS):
        raise ValueError("Missing the mpv engine library")
    with zipfile.ZipFile(apk) as archive:
        sizes = {info.filename: info.file_size for info in archive.infolist()}
        if any(name.endswith(".aab") for name in sizes):
            raise ValueError("App bundle inside the APK")
    report = {
        "apk": result["apk"],
        "abi": KAI_ABI,
        "sha256": result["sha256"],
        "elf": {"class": "ELFCLASS32", "encoding": "little-endian", "e_machine": 40},
        "libraries": [
            {"name": name.rsplit("/", 1)[-1], "size": sizes[name], "sha256": digest}
            for name, digest in sorted(result["nativeLibraries"].items())
        ],
    }
    if baseline is not None:
        expected = {entry["name"] for entry in json.loads(baseline.read_text())["libraries"]}
        lost = sorted(expected - names)
        if lost:
            raise ValueError(f"Native libraries lost against the baseline: {lost}")
        report["addedSinceBaseline"] = sorted(names - expected)
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--abi", choices=MACHINES)
    parser.add_argument("--kai", action="store_true", help="NuvioArcKai rules (armeabi-v7a only)")
    parser.add_argument("--baseline", type=Path, help="docs/kai/baseline-native.json, with --kai")
    parser.add_argument("--output", type=Path, help="write the report here as well (native-inventory.json)")
    args = parser.parse_args()
    if not args.kai and args.abi is None:
        parser.error("--abi is required without --kai")
    try:
        report = verify_kai(args.apk, args.baseline) if args.kai else verify(args.apk, args.abi)
        text = json.dumps(report, indent=2)
        if args.output:
            args.output.write_text(text + "\n")
        print(text)
    except (ValueError, zipfile.BadZipFile, OSError) as error:
        parser.exit(1, f"Native APK validation failed: {error}\n")
