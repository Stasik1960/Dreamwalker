"""Small corrupt-input checks; run normally and with python -O. No game launch."""
import copy
import json
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest.mock import patch

import check_logical_mount_transition as transition
from check_packaged_resources import validate as validate_jar
from check_staged_resources import prune_stale_logical_models


class StagingPruneTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source, self.staged = self.root / 'source', self.root / 'build'
        self.source.mkdir()
        self.folder = self.staged / 'assets/bloodborne_blocks/models/block/logical'
        self.folder.mkdir(parents=True)
        self.obsolete = self.folder / ('o_' + 'a' * 20 + '.json')
        self.obsolete.write_text('{}')

    def test_removes_only_generated_extras(self):
        self.assertEqual(len(prune_stale_logical_models(self.source, self.staged)), 1)
        self.assertFalse(self.obsolete.exists())

    def test_foreign_extra_aborts_before_any_removal(self):
        (self.staged / 'user.json').write_text('{}')
        with self.assertRaisesRegex(AssertionError, 'no files pruned'):
            prune_stale_logical_models(self.source, self.staged)
        self.assertTrue(self.obsolete.exists())

    def test_source_is_never_a_staging_target(self):
        with self.assertRaises(AssertionError):
            prune_stale_logical_models(self.staged, self.staged)
        self.assertTrue(self.obsolete.exists())

    def test_current_model_is_retained(self):
        counterpart = self.source / self.obsolete.relative_to(self.staged)
        counterpart.parent.mkdir(parents=True)
        counterpart.write_text('{}')
        self.assertEqual(prune_stale_logical_models(self.source, self.staged), [])
        self.assertTrue(self.obsolete.exists())


class JarGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.resources, self.classes = self.root / "resources", self.root / "classes"
        self.resources.mkdir()
        self.classes.mkdir()
        self.inputs = {
            "fabric.mod.json": json.dumps({"version": "test", "mixins": ["mod.mixins.json"]}).encode(),
            "mod.mixins.json": b'{"refmap":"mod-refmap.json"}',
            "pack.mcmeta": b'{}',
        }
        for name, data in self.inputs.items():
            (self.resources / name).write_bytes(data)
        (self.classes / "Main.class").write_bytes(b"named bytecode fixture")
        (self.classes / "mod-refmap.json").write_bytes(b"{}")
        self.entries = {**self.inputs, "Main.class": b"remapped bytecode fixture",
                        "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\n", "mod-refmap.json": b"{}"}

    def check(self, entries=None, duplicate=False, generated_refmap=None):
        jar = self.root / "fixture.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for name, data in (self.entries if entries is None else entries).items():
                archive.writestr(name, data)
            if duplicate:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    archive.writestr("pack.mcmeta", b"{}")
        return validate_jar(jar, self.resources, self.classes, generated_refmap)

    def test_valid(self):
        self.assertTrue(self.check()["ok"])

    def test_changed_bytes(self):
        with self.assertRaisesRegex(AssertionError, "differs"):
            self.check({**self.entries, "pack.mcmeta": b'{"changed":true}'})

    def test_root_stale_resource(self):
        with self.assertRaisesRegex(AssertionError, "unexpected"):
            self.check({**self.entries, "old.mixins.json": b"{}"})

    def test_test_class_leak(self):
        with self.assertRaisesRegex(AssertionError, "classes differ"):
            self.check({**self.entries, "SomeCheck.class": b"fixture"})

    def test_missing_source(self):
        with self.assertRaisesRegex(AssertionError, "Missing resources"):
            self.check({k: v for k, v in self.entries.items() if k != "pack.mcmeta"})

    def test_missing_refmap(self):
        with self.assertRaisesRegex(AssertionError, "Missing generated"):
            self.check({k: v for k, v in self.entries.items() if k != "mod-refmap.json"})

    def test_duplicate_entry(self):
        with self.assertRaisesRegex(AssertionError, "Duplicate"):
            self.check(duplicate=True)

    def test_loom_refmap_injection(self):
        (self.resources / "mod.mixins.json").write_bytes(b'{}')
        self.assertTrue(self.check(generated_refmap="mod-refmap.json")["ok"])

    def test_loom_cannot_change_other_mixin_fields(self):
        entries = {**self.entries, "mod.mixins.json": b'{"refmap":"mod-refmap.json","required":false}'}
        with self.assertRaisesRegex(AssertionError, "mixin config differs"):
            self.check(entries)

    def test_loom_cannot_substitute_refmap(self):
        (self.resources / "mod.mixins.json").write_bytes(b'{}')
        with self.assertRaisesRegex(AssertionError, "mixin config differs"):
            self.check(generated_refmap="wrong-refmap.json")

    def test_loom_cannot_corrupt_generated_refmap(self):
        with self.assertRaisesRegex(AssertionError, "generated refmap differs"):
            self.check({**self.entries, "mod-refmap.json": b'{"changed":true}'}, generated_refmap="mod-refmap.json")

    def test_loom_requires_authoritative_refmap(self):
        (self.classes / "mod-refmap.json").unlink()
        with self.assertRaisesRegex(AssertionError, "Missing authoritative"):
            self.check(generated_refmap="mod-refmap.json")


class TransitionGuardTests(unittest.TestCase):
    def setUp(self):
        definition = {"id": "o_fixture", "behavior": "static", "semantic": "ornament",
                      "source": "minecraft:stone", "properties": {"facing": ["north"]},
                      "default": {"facing": "north"}, "states": {"facing=north": [0, 0, 0]},
                      "models": {"facing=north": "mesh"}}
        self.old = {"definitions.json": {"blocks": [definition]},
                    "meshes.json.gz": {"mesh": {"polygons": []}},
                    "geometry.json": {"blocks": {"o_fixture": {"states": {"facing=north": {"ref": "profile"}}}},
                                      "profiles": {"profile": {"cells": []}}},
                    "migration.json": {"schemaVersion": 1, "rules": [
                        {"source": {"id": "source", "properties": {}},
                         "target": {"id": "o_fixture", "properties": {"facing": "north"}}, "offset": [0, 0, 0]}]}}
        self.new = copy.deepcopy(self.old)

    def check(self):
        with patch.object(transition, "read", side_effect=lambda root, name: copy.deepcopy((self.old if root == "old" else self.new)[name])):
            return transition.validate("old", "new")

    def test_valid(self):
        self.assertTrue(self.check()["ok"])

    def test_extra_state(self):
        self.new["definitions.json"]["blocks"][0]["states"]["facing=south"] = [0, 0, 0]
        with self.assertRaisesRegex(AssertionError, "state set"):
            self.check()

    def test_extra_mesh(self):
        self.new["meshes.json.gz"]["stale"] = {"polygons": []}
        with self.assertRaisesRegex(AssertionError, "mesh entries"):
            self.check()

    def test_extra_profile(self):
        self.new["geometry.json"]["profiles"]["stale"] = {"cells": []}
        with self.assertRaisesRegex(AssertionError, "geometry profiles"):
            self.check()

    def test_behavior_change(self):
        self.new["definitions.json"]["blocks"][0]["behavior"] = "door"
        with self.assertRaisesRegex(AssertionError, "metadata changed"):
            self.check()

    def test_schema_change(self):
        self.new["migration.json"]["schemaVersion"] = 2
        with self.assertRaisesRegex(AssertionError, "Migration metadata"):
            self.check()

    def test_changed_mesh(self):
        self.new["meshes.json.gz"]["mesh"]["polygons"].append({"invalid": True})
        with self.assertRaisesRegex(AssertionError, "mesh changed"):
            self.check()

    def test_source_offset_change(self):
        self.new["migration.json"]["rules"][0]["offset"] = [1, 0, 0]
        with self.assertRaisesRegex(AssertionError, "source/offset/member"):
            self.check()


if __name__ == "__main__":
    unittest.main()
