import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from check_release_package import validate


class ReleasePackageTests(unittest.TestCase):
    def fixture(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        (root / "build.gradle").write_text("version = '1.2.3'\n")
        (root / "VERSION").write_text("1.2.3\n")
        resources = root / "src/main/resources"
        resources.mkdir(parents=True)
        (resources / "fabric.mod.json").write_text(json.dumps({"version": "1.2.3"}))
        sources = root / "src/main/java"
        sources.mkdir(parents=True)
        (sources / "Example.java").write_text("class Example {}")
        jar = root / "bloodborne-blocks-1.2.3.jar"
        source_jar = root / "bloodborne-blocks-1.2.3-sources.jar"
        for path in (jar, source_jar):
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("fabric.mod.json", json.dumps({"version": "1.2.3"}))
                archive.writestr("Example.java" if path == source_jar else "Example.class", "class Example {}")
        return root, jar, source_jar

    def test_valid(self):
        root, jar, source = self.fixture()
        self.assertEqual(validate(jar, source, root)["version"], "1.2.3")

    def test_version_mismatches(self):
        for filename in ("VERSION", "src/main/resources/fabric.mod.json"):
            root, jar, source = self.fixture()
            (root / filename).write_text("9" if filename == "VERSION" else '{"version":"9"}')
            with self.assertRaises(ValueError):
                validate(jar, source, root)
        root, jar, source = self.fixture()
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("fabric.mod.json", '{"version":"9"}')
        with self.assertRaises(ValueError):
            validate(jar, source, root)

    def test_missing_or_misnamed_artifact(self):
        root, jar, source = self.fixture()
        with self.assertRaises(ValueError):
            validate(root / "wrong.jar", source, root)
        source.unlink()
        with self.assertRaises(ValueError):
            validate(jar, source, root)

    def test_sources_must_be_a_valid_production_source_archive(self):
        root, jar, source = self.fixture()
        source.write_bytes(b"")
        with self.assertRaises(zipfile.BadZipFile):
            validate(jar, source, root)
        for filename in ("x", "Example.class", "Unexpected.java"):
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("fabric.mod.json", '{"version":"1.2.3"}')
                archive.writestr(filename, "unexpected")
            with self.assertRaises(ValueError):
                validate(jar, source, root)


if __name__ == "__main__":
    unittest.main()
