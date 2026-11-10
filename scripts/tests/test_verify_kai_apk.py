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


class KaiApkTest(unittest.TestCase):
    def archive(self, root, names=None, elf_class=1, machine=40, abi="armeabi-v7a"):
        path = Path(root) / "kai.apk"
        header = bytearray(64)
        header[:4] = b"\x7fELF"
        header[4:6] = bytes((elf_class, 1))
        struct.pack_into("<H", header, 18, machine)
        names = names if names is not None else sorted(module.KAI_REQUIRED | {"libmpv.so"})
        with zipfile.ZipFile(path, "w") as archive:
            for name in names:
                archive.writestr(f"lib/{abi}/{name}", header + b"dovi-bridge-libdovi-capi-0.2 dovi_convert_rpu_with_mode")
        return path

    def test_complete_armv7_apk_passes(self):
        with tempfile.TemporaryDirectory() as root:
            report = module.verify_kai(self.archive(root))
            self.assertEqual("armeabi-v7a", report["abi"])
            self.assertEqual(len(module.KAI_REQUIRED) + 1, len(report["libraries"]))

    def test_64_bit_or_other_abi_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                module.verify_kai(self.archive(root, elf_class=2, machine=183))
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                module.verify_kai(self.archive(root, abi="arm64-v8a"))

    def test_missing_mpv_or_thumbnails_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaisesRegex(ValueError, "mpv"):
                module.verify_kai(self.archive(root, names=sorted(module.KAI_REQUIRED)))
        with tempfile.TemporaryDirectory() as root:
            names = sorted((module.KAI_REQUIRED - {"libnuviothumb.so"}) | {"libmpv.so"})
            with self.assertRaisesRegex(ValueError, "libnuviothumb"):
                module.verify_kai(self.archive(root, names=names))

    def test_library_lost_against_baseline_is_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            baseline = Path(root) / "baseline.json"
            baseline.write_text(json.dumps({"libraries": [{"name": "libextra.so"}, {"name": "libmpv.so"}]}))
            with self.assertRaisesRegex(ValueError, "libextra"):
                module.verify_kai(self.archive(root), baseline)


if __name__ == "__main__":
    unittest.main()
