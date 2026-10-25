import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from classfile_audit import scan_class, scan_jar
from metadata_audit import summarize, public_metadata
from client_linkage_audit import key, linkage


class AuditTests(unittest.TestCase):
    def test_metadata_never_treats_linux_arm64_as_android(self):
        result = summarize({"id": "fixture", "libraries": [{"name": "org.lwjgl:lwjgl:3.3.2:natives-linux-arm64"}]})
        self.assertEqual([], result["android_native_candidates"])
        self.assertFalse(result["game_files_downloaded"])

    def test_rejects_untrusted_metadata_urls_before_network(self):
        for url in ("http://piston-meta.mojang.com/file.json", "https://attacker.example/file.json",
                    "https://piston-meta.mojang.com/client.jar", "https://piston-meta.mojang.com:444/file.json",
                    "https://user@piston-meta.mojang.com/file.json"):
            with self.assertRaises(ValueError): public_metadata(url)

    def test_classfile_rejects_corruption(self):
        for data in (b"not-a-class", b"\xca\xfe\xba\xbe\x00"):
            with self.assertRaises(ValueError): scan_class(data)

    def test_compiled_fixture_exposes_desktop_and_jni_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(["javac", "--release", "17", "-d", directory,
                            str(ROOT / "tests/fixtures/CompatibilityFixture.java")], check=True)
            subprocess.run(["jar", "--create", "--file", str(pathlib.Path(directory) / "fixture.jar"),
                            "-C", directory, "fixture"], check=True)
            result = scan_jar(pathlib.Path(directory) / "fixture.jar")
            self.assertEqual({61: 1}, result["classfile_major_versions"])
            finding = result["platform_findings"]["fixture/CompatibilityFixture.class"]
            self.assertIn("java/awt/Desktop", finding["platform_references"])
            self.assertIn("desktopNativeHandle()J", finding["native_methods"])
            self.assertIn({"kind": "method", "owner": "java/lang/Class", "name": "forName",
                           "descriptor": "(Ljava/lang/String;)Ljava/lang/Class;"}, finding["member_references"])
            self.assertEqual("fixture/CompatibilityFixture", finding["class"])

    def test_descriptor_identity_distinguishes_overloads(self):
        base = {"owner": "org/lwjgl/glfw/GLFW", "kind": "method", "name": "glfwCreateWindow"}
        self.assertNotEqual(key({**base, "descriptor": "(IIJ)J"}), key({**base, "descriptor": "(IILjava/lang/CharSequence;JJ)J"}))


if __name__ == "__main__": unittest.main()
