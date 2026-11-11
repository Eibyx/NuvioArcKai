import importlib.util
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("verify_native_apk", Path(__file__).parents[1] / "verify_native_apk.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

MARKER = b"dovi-bridge-libdovi-capi-0.2 dovi_convert_rpu_with_mode"


def elf32(needed=(), symbols=(), machine=40, elf_class=1):
    """Minimal ELF32 LE with .dynstr, .dynsym and .dynamic sections."""
    strtab = b"\0"
    offsets = {}
    for name in list(needed) + list(symbols):
        offsets[name] = len(strtab)
        strtab += name.encode() + b"\0"
    dynsym = b"\0" * 16 + b"".join(struct.pack("<IIIBBH", offsets[s], 0x1000, 4, 0x12, 0, 9) for s in symbols)
    dynamic = b"".join(struct.pack("<iI", 1, offsets[n]) for n in needed) + struct.pack("<iI", 0, 0)
    body = bytearray(52)
    body[:4] = b"\x7fELF"
    body[4:6] = bytes((elf_class, 1))
    struct.pack_into("<H", body, 18, machine)
    data_start = len(body) + len(MARKER)
    payload = MARKER + strtab
    str_off = len(body) + len(MARKER)
    sym_off = str_off + len(strtab)
    payload += dynsym
    dyn_off = sym_off + len(dynsym)
    payload += dynamic
    sh_off = len(body) + len(payload)
    sections = [
        (0, 0, 0, 0, 0),
        (3, str_off, len(strtab), 0, 0),       # 1: .dynstr
        (11, sym_off, len(dynsym), 1, 16),     # 2: .dynsym
        (6, dyn_off, len(dynamic), 1, 8),      # 3: .dynamic
    ]
    table = b"".join(struct.pack("<IIIIIIIIII", 0, t, 0, 0, o, s, l, 0, 0, e) for t, o, s, l, e in sections)
    struct.pack_into("<I", body, 0x20, sh_off)
    struct.pack_into("<HH", body, 0x2E, 40, len(sections))
    return bytes(body) + payload + table


GOOD = {
    "libasskt.so": elf32(["libc.so", "libc++_shared.so"], ["ass_library_init", "ass_render_frame"]),
    "libmpv.so": elf32(["libc.so", "libavcodec.so"], ["ass_library_init", "ass_render_frame", "mpv_create"]),
    "libavcodec.so": elf32(["libc.so"]),
    "libdovi_bridge.so": elf32(["libc.so"]),
    "libffmpegJNI.so": elf32(["libc.so"]),
    "libnuviothumb.so": elf32(["libc.so"]),
    "libc++_shared.so": elf32(["libc.so"]),
}


class KaiApkTest(unittest.TestCase):
    def archive(self, root, libs=None, abi="armeabi-v7a"):
        path = Path(root) / "kai.apk"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in (libs if libs is not None else GOOD).items():
                archive.writestr(f"lib/{abi}/{name}", data)
        return path

    def test_complete_armv7_apk_passes_without_a_separate_libass(self):
        with tempfile.TemporaryDirectory() as root:
            report = module.verify_kai(self.archive(root))
            self.assertEqual("armeabi-v7a", report["abi"])
            self.assertFalse(report["libass"]["libasskt.so"]["needsLibass"])
            self.assertEqual(2, report["libass"]["libmpv.so"]["assSymbols"])

    def test_libasskt_without_libass_symbols_is_rejected(self):
        libs = dict(GOOD, **{"libasskt.so": elf32(["libc.so"], ["something_else"])})
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(ValueError, "does not export"):
                module.verify_kai(self.archive(root, libs))

    def test_dependency_on_a_missing_libass_is_rejected(self):
        libs = dict(GOOD, **{"libasskt.so": elf32(["libass.so"], ["ass_library_init", "ass_render_frame"])})
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(ValueError, "libass.so"):
                module.verify_kai(self.archive(root, libs))

    def test_unresolved_needed_entry_is_rejected(self):
        libs = dict(GOOD, **{"libavcodec.so": elf32(["libmissing.so"])})
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(ValueError, "libmissing"):
                module.verify_kai(self.archive(root, libs))

    def test_64_bit_or_other_abi_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                module.verify_kai(self.archive(root, abi="arm64-v8a"))
        libs = dict(GOOD, **{"libnuviothumb.so": elf32(machine=183)})
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                module.verify_kai(self.archive(root, libs))

    def test_missing_mpv_or_thumbnails_is_rejected(self):
        for name in ("libmpv.so", "libnuviothumb.so"):
            libs = {k: v for k, v in GOOD.items() if k != name}
            with tempfile.TemporaryDirectory() as root:
                with self.assertRaisesRegex(ValueError, name.replace(".so", "")):
                    module.verify_kai(self.archive(root, libs))

    def test_library_lost_against_baseline_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            baseline = Path(root) / "baseline.json"
            baseline.write_text(json.dumps({"libraries": [{"name": "libextra.so"}, {"name": "libmpv.so"}]}))
            with self.assertRaisesRegex(ValueError, "libextra"):
                module.verify_kai(self.archive(root), baseline)


if __name__ == "__main__":
    unittest.main()
