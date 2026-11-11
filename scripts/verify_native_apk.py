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


def verify(apk: Path, abi: str, required: set[str] = REQUIRED) -> dict:
    elf_class, machine = MACHINES[abi]
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entries")
        corrupt = archive.testzip()
        if corrupt:
            raise ValueError(f"Corrupt ZIP entry: {corrupt}")
        libraries = [name for name in names if name.startswith("lib/") and name.endswith(".so")]
        missing = required - {name.rsplit("/", 1)[-1] for name in libraries if name.startswith(f"lib/{abi}/")}
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
# libass ships statically linked inside libasskt.so (nuvio-libass AAR) and libmpv.so; there is no libass.so to require.
# Instead verify_libass() checks the symbols and that nothing depends on a missing libass.so.
KAI_REQUIRED = (REQUIRED - {"libass.so"}) | {"libnuviothumb.so", "libc++_shared.so", "libmpv.so"}
KAI_MPV_MARKERS = ("libmpv.so", "libplayer.so")
LIBASS_SYMBOLS = {"ass_library_init", "ass_render_frame"}
# Libraries every Android 7+ device provides (NDK stable system libraries).
ANDROID_SYSTEM_LIBRARIES = {
    "libc.so", "libm.so", "libdl.so", "liblog.so", "libz.so", "libandroid.so", "libjnigraphics.so",
    "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so", "libOpenMAXAL.so",
    "libmediandk.so", "libvulkan.so", "libstdc++.so", "libnativewindow.so", "libaaudio.so", "libcamera2ndk.so",
    "libsync.so", "libicu.so", "libamidi.so", "libbinder_ndk.so", "libneuralnetworks.so",
}


def elf_dynamic(data: bytes) -> tuple[list[str], set[str]]:
    """NEEDED entries and defined dynamic symbols of a 32- or 64-bit little-endian ELF."""
    is64 = data[4] == 2
    if data[5] != 1:
        raise ValueError("Big-endian ELF")
    if is64:
        shoff, = struct.unpack_from("<Q", data, 0x28)
        shentsize, shnum = struct.unpack_from("<HH", data, 0x3A)
    else:
        shoff, = struct.unpack_from("<I", data, 0x20)
        shentsize, shnum = struct.unpack_from("<HH", data, 0x2E)
    sections = []
    for index in range(shnum):
        base = shoff + index * shentsize
        if is64:
            _, stype, _, _, offset, size, link, _, _, entsize = struct.unpack_from("<IIQQQQIIQQ", data, base)
        else:
            _, stype, _, _, offset, size, link, _, _, entsize = struct.unpack_from("<IIIIIIIIII", data, base)
        sections.append((stype, offset, size, link, entsize))
    needed, symbols = [], set()

    def string(table, at):
        start = sections[table][1] + at
        return data[start:data.index(b"\0", start)].decode("utf-8", "replace")

    for stype, offset, size, link, entsize in sections:
        if stype == 6:  # SHT_DYNAMIC
            step = 16 if is64 else 8
            for at in range(offset, offset + size, step):
                tag, value = struct.unpack_from("<qQ" if is64 else "<iI", data, at)
                if tag == 0:
                    break
                if tag == 1:  # DT_NEEDED
                    needed.append(string(link, value))
        elif stype == 11:  # SHT_DYNSYM
            step = entsize or (24 if is64 else 16)
            for at in range(offset + step, offset + size, step):
                if is64:
                    name, info, _, shndx = struct.unpack_from("<IBBH", data, at)
                else:
                    name, _, _, info, _, shndx = struct.unpack_from("<IIIBBH", data, at)
                if shndx != 0 and name:
                    symbols.add(string(link, name))
    return needed, symbols


def verify_libass(libraries: dict[str, bytes]) -> dict:
    """libass is present through libasskt.so and libmpv.so, and no library needs anything the APK or system lacks."""
    if "libasskt.so" not in libraries:
        raise ValueError("Missing libasskt.so (libass for ExoPlayer)")
    parsed = {name: elf_dynamic(data) for name, data in libraries.items()}
    asskt_needed, asskt_symbols = parsed["libasskt.so"]
    if not LIBASS_SYMBOLS <= asskt_symbols:
        raise ValueError(f"libasskt.so does not export {sorted(LIBASS_SYMBOLS - asskt_symbols)}")
    if "libass.so" in asskt_needed and "libass.so" not in libraries:
        raise ValueError("libasskt.so needs a libass.so the APK lacks")
    if "libmpv.so" in parsed:
        mpv_needed, mpv_symbols = parsed["libmpv.so"]
        if not LIBASS_SYMBOLS <= mpv_symbols and not all(n in libraries or n in ANDROID_SYSTEM_LIBRARIES for n in mpv_needed):
            raise ValueError("libmpv.so neither carries libass nor finds all its dependencies")
    unresolved = sorted(
        f"{name} -> {dep}" for name, (needed, _) in parsed.items() for dep in needed
        if dep not in libraries and dep not in ANDROID_SYSTEM_LIBRARIES
    )
    if unresolved:
        raise ValueError(f"Unresolved NEEDED entries: {unresolved}")
    return {
        "libasskt.so": {"exports": sorted(LIBASS_SYMBOLS), "needsLibass": "libass.so" in asskt_needed},
        "libmpv.so": {"assSymbols": len([s for s in parsed.get("libmpv.so", ([], set()))[1] if s.startswith("ass_")])},
        "unresolvedNeeded": [],
    }


def verify_kai(apk: Path, baseline: Path | None = None) -> dict:
    """Kai rules on top of verify(): ARM 32-bit only, mpv and thumbnail libraries, nothing lost from the baseline."""
    result = verify(apk, KAI_ABI, required=KAI_REQUIRED)
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
        libass = verify_libass({name.rsplit("/", 1)[-1]: archive.read(name) for name in result["nativeLibraries"]})
    report = {
        "apk": result["apk"],
        "abi": KAI_ABI,
        "sha256": result["sha256"],
        "elf": {"class": "ELFCLASS32", "encoding": "little-endian", "e_machine": 40},
        "libass": libass,
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
