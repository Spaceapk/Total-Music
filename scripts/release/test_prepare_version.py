"""Check version preparation on isolated release metadata, never the real checkout."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class PrepareVersionTest(unittest.TestCase):
    def run_preparation(self, *args):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / 'scripts/release/prepare_version.py'
            script.parent.mkdir(parents=True)
            shutil.copyfile(Path(__file__).with_name('prepare_version.py'), script)
            metadata = root / 'total-music-release.properties'
            original = 'versionName=1.8.1\nversionCode=16\napplicationId=org.nexlibre.totalmusic\n'
            metadata.write_text(original)
            result = subprocess.run([sys.executable, str(script), *args], capture_output=True)
            return result.returncode, metadata.read_text(), original

    def test_normal_release_increases_name_and_code(self):
        code, text, _ = self.run_preparation('1.8.2')
        self.assertEqual(code, 0)
        self.assertIn('versionName=1.8.2\nversionCode=17', text)

    def test_explicit_revision_keeps_name_and_increases_code(self):
        code, text, _ = self.run_preparation('1.8.1', '--revision-code', '17')
        self.assertEqual(code, 0)
        self.assertIn('versionName=1.8.1\nversionCode=17', text)

    def test_rejects_downgrade_or_ambiguous_revision_without_modifying_metadata(self):
        for args in [('1.8.1',), ('1.8.0',), ('1.8.1', '--revision-code', '16'), ('1.8.1', '--revision-code', '15'), ('1.8.2', '--revision-code', '17')]:
            with self.subTest(args=args):
                code, text, original = self.run_preparation(*args)
                self.assertNotEqual(code, 0)
                self.assertEqual(text, original)


if __name__ == '__main__':
    unittest.main()
