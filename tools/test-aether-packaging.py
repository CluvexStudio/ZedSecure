#!/usr/bin/env python3
"""Offline integration checks of the real downloader with deterministic archives."""
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = {
    "android-arm64": "aether", "android-armv7": "aether", "android-x86_64": "aether",
    "linux-x86_64-musl": "aether", "windows-x86_64": "aether.exe",
    "macos-arm64": "aether", "macos-x86_64": "aether",
}


class PackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "tools").mkdir()
        shutil.copy(ROOT / "tools/get-aether.sh", self.root / "tools")
        self.assets = self.root / "assets"
        self.assets.mkdir()
        self.cache = self.root / "cache/CluvexStudio/Aether/v2.4.0"
        self.cache.mkdir(parents=True)
        sums = []
        for target, binary in ASSETS.items():
            asset = f"aether-{target}" + (".zip" if binary.endswith(".exe") else ".tar.gz")
            path = self.assets / asset
            files = {binary: b"fake aether executable", "LICENSES/aether-LICENSE.txt": b"AGPL-3.0-only", "LICENSES/THIRD-PARTY-NOTICES.txt": b"upstream notices"}
            if asset.endswith(".zip"):
                with zipfile.ZipFile(path, "w") as archive:
                    for name, content in files.items():
                        archive.writestr(name, content)
            else:
                with tarfile.open(path, "w:gz") as archive:
                    for name, content in files.items():
                        entry = tarfile.TarInfo(name)
                        entry.size = len(content)
                        entry.mode = 0o755 if name == binary else 0o644
                        archive.addfile(entry, io.BytesIO(content))
            sums.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {asset}\n")
            shutil.copy(path, self.cache)
        (self.root / "tools/aether-sha256.txt").write_text("".join(sums))
        shim = self.root / "shim"
        shim.mkdir()
        curl = shim / "curl"
        curl.write_text('#!/usr/bin/env python3\nimport sys, os, shutil\nfrom pathlib import Path\na=sys.argv\nshutil.copy(Path(os.environ["FIXTURE_ASSETS"])/a[-1].rsplit("/",1)[-1], a[a.index("-o")+1])\n')
        curl.chmod(0o755)
        self.env = dict(os.environ, CORES_CACHE=str(self.root / "cache"), FIXTURE_ASSETS=str(self.assets), PATH=str(shim) + os.pathsep + os.environ["PATH"])

    def tearDown(self):
        self.temp.cleanup()

    def run_target(self, target, success=True):
        result = subprocess.run(["bash", str(self.root / "tools/get-aether.sh"), target], env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result

    def test_android_installs_all_abis_in_executable_library_location(self):
        self.run_target("android")
        for abi in ["arm64-v8a", "armeabi-v7a", "x86_64"]:
            core = self.root / f"app/src/main/jniLibs/{abi}/libaether.so"
            self.assertEqual(core.read_bytes(), b"fake aether executable")
            self.assertTrue(os.access(core, os.X_OK))
            notices = self.root / f"app/src/main/assets/aether/{abi}/LICENSES"
            self.assertTrue((notices / "THIRD-PARTY-NOTICES.txt").is_file())

    def test_every_desktop_carries_the_core_and_notices(self):
        for target, sub, binary in [("linux", "linux", "aether"), ("windows", "windows", "aether.exe"), ("macos-arm64", "macos", "aether"), ("macos-x86_64", "macos", "aether")]:
            self.run_target(target)
            dest = self.root / f"desktop/src/main/resources/bin/{sub}"
            self.assertTrue(os.access(dest / binary, os.X_OK))
            self.assertEqual((dest / "aether-licenses/aether-LICENSE.txt").read_text(), "AGPL-3.0-only")

    def test_corrupt_cache_is_replaced_before_install(self):
        (self.cache / "aether-linux-x86_64-musl.tar.gz").write_bytes(b"corrupt")
        self.run_target("linux")
        self.assertEqual((self.cache / "aether-linux-x86_64-musl.tar.gz").read_bytes(), (self.assets / "aether-linux-x86_64-musl.tar.gz").read_bytes())

    def test_checksum_mismatch_never_installs_a_core(self):
        asset = "aether-linux-x86_64-musl.tar.gz"
        (self.cache / asset).unlink()
        (self.assets / asset).write_bytes(b"tampered archive")
        result = self.run_target("linux", success=False)
        self.assertIn("checksum mismatch", result.stderr)
        self.assertFalse((self.root / "desktop/src/main/resources/bin/linux/aether").exists())

    def test_unknown_target_fails(self):
        self.run_target("other", success=False)

    def test_localizations_are_valid_and_cover_the_sheet(self):
        for locale in ["values", "values-fa"]:
            xml = ET.parse(ROOT / f"shared/src/commonMain/composeResources/{locale}/strings.xml")
            names = [node.attrib.get("name") for node in xml.getroot()]
            self.assertEqual(len(names), len(set(names)))
            for name in ["aether_add_title", "aether_description", "aether_peer_hint", "aether_tls_verify", "aether_android_requirement"]:
                self.assertIn(name, names)


if __name__ == "__main__":
    unittest.main(verbosity=2)
