"""Focused negative cases for the accepted runtime provenance gate."""
from __future__ import annotations

import json
import gzip
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import check_accepted_runtime as gate


class AcceptedRuntimeTests(unittest.TestCase):
    def test_window_amendment_changes_only_open_collision(self):
        value={'families':[{'id':'o_shuttered_window','states':{
            'open=true':{'collision_footprint':{'boxes':[[0,0,0,1,2,1]]},'render_mesh':{'id':'frozen'}},
            'open=false':{'collision_footprint':{'boxes':[[0,0,0,1,2,1]]}}}}]}
        result=gate._logical_amendment(value,'contracts-v2.json',None)['families'][0]['states']
        self.assertEqual([],result['open=true']['collision_footprint']['boxes'])
        self.assertEqual({'id':'frozen'},result['open=true']['render_mesh'])
        self.assertEqual([[0,0,0,1,2,1]],result['open=false']['collision_footprint']['boxes'])

    def test_reviewed_java_hash_rejects_an_extra_line(self):
        import hashlib
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);name=next(iter(gate.ALLOWED_JAVA_AMENDMENTS))
            path=root/name;path.parent.mkdir(parents=True);path.write_text('class Reviewed {}\n')
            manifest=root/'docs/accepted-restore/runtime-amendments.json';manifest.parent.mkdir(parents=True)
            manifest.write_text(json.dumps({'java':{name:hashlib.sha256(path.read_bytes().replace(b'\r\n',b'\n')).hexdigest()}}))
            with patch.object(gate,'_tree',return_value=[name]):
                self.assertEqual([name],gate._accepted_java(root,root))
                path.write_text('class Reviewed {}\n// unreviewed\n')
                with self.assertRaisesRegex(AssertionError,'REVIEWED_RUNTIME_JAVA_CHANGED'):gate._accepted_java(root,root)

    def test_old_owner_mesh_cannot_be_amended(self):
        root,jar,sources,baseline=self.fixture()
        name=gate.CITY+'/owner-meshes.json.gz'
        baseline[name]=gzip.compress(b'{"old":{"polygons":[]}}')
        (root/name).write_bytes(gzip.compress(b'{"old":{"polygons":[1]}}'))
        with patch.object(gate,'_git',self._git(baseline)):
            with self.assertRaisesRegex(AssertionError,'ACCEPTED_OWNER_MESH_CHANGED'):gate._accepted_owner_contracts(root,root)
    def test_mcmeta_provenance_ignores_checkout_line_endings_only_semantically(self):
        left=gate._semantic(b'{\r\n"animation":{}\r\n}', 'lantern.png.mcmeta')
        self.assertEqual(left,gate._semantic(b'{\n"animation":{}\n}', 'lantern.png.mcmeta'))
        self.assertNotEqual(left,gate._semantic(b'{"animation":{"frametime":5}}', 'lantern.png.mcmeta'))

    def refresh_runtime_jar(self, root, jar):
        with zipfile.ZipFile(jar, "w") as archive:
            metadata = json.loads((root / "src/main/resources/fabric.mod.json").read_text())
            mixins = {item if isinstance(item, str) else item["config"] for item in metadata.get("mixins", [])}
            for path in (root / "src/main/resources").rglob("*"):
                if not path.is_file(): continue
                name = path.relative_to(root / "src/main/resources").as_posix()
                if name in mixins:
                    value = json.loads(path.read_bytes()); value.setdefault("refmap", "bloodborne-blocks-refmap.json")
                    archive.writestr(name, json.dumps(value))
                else:
                    archive.writestr(name, path.read_bytes())
            if mixins: archive.writestr("bloodborne-blocks-refmap.json", "{}")

    def fixture(self):
        temp = tempfile.TemporaryDirectory(); self.addCleanup(temp.cleanup)
        root = Path(temp.name) / "Bloodborne-Blocks"
        logical = root / gate.LOGICAL; city = root / gate.CITY; java = root / "src/main/java/example"
        logical.mkdir(parents=True); city.mkdir(parents=True); java.mkdir(parents=True)
        (root/'src/main/resources/fabric.mod.json').write_text('{"version":"1"}')
        ids = ["o_c001", "o_books", "o_shuttered_window"] + [f"o_{n}" for n in range(46)]
        (logical / "production-palette.json").write_text(json.dumps({"objects": [{"id": ident} for ident in ids]}))
        (logical / "contracts-v2.json").write_text('{"families":[]}')
        (city / "definitions.json").write_text('{"blocks":[{"id":"city_old","models":{"variant=0":"city_profile"},"states":{}}]}')
        (city / "geometry.json").write_text('{"blocks":{"city_old":{"states":{"variant=0":{"ref":"city_profile"}}}},"profiles":{"city_profile":{"collision":[[0,0,0,1,1,1]],"physical_footprint":{"note":"metadata"}}}}')
        (city / "owner-runtime-mappings.json").write_text('{"states":{}}')
        (city / "owner-meshes.json.gz").write_bytes(gzip.compress(b"{}"))
        (city / "reviewed-wall-family.json").write_text('{}')
        (java / "Runtime.java").write_text("class Runtime {}")
        baseline = {path.relative_to(root).as_posix(): path.read_bytes() for path in root.rglob("*") if path.is_file()}
        jar, sources = root / "runtime.jar", root / "sources.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for path, data in baseline.items():
                if path.startswith("src/main/"): archive.writestr(path.removeprefix("src/main/resources/"), data)
        with zipfile.ZipFile(sources, "w") as archive: archive.writestr("example/Runtime.java", baseline["src/main/java/example/Runtime.java"])
        return root, jar, sources, baseline

    def _git(self, baseline):
        def read(_repo, _commit, path): return baseline[path]
        return read

    def _tree(self, baseline):
        def list_tree(_repo, _commit, path): return sorted(name for name in baseline if name.startswith(path + "/"))
        return list_tree

    def test_valid_fixture(self):
        root, jar, sources, baseline = self.fixture()
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            self.assertEqual("PASS", gate.validate(jar, sources, root, root.parent)["result"])

    def test_rejects_logical_id_only_regression(self):
        root, jar, sources, baseline = self.fixture()
        (root / gate.LOGICAL / "contracts-v2.json").write_text('{"families":[{"id":"o_books","broken":true}]}')
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            with self.assertRaisesRegex(AssertionError, "ACCEPTED_LOGICAL_RESOURCE_CHANGED"):
                gate.validate(jar, sources, root, root.parent)

    def test_rejects_rc1_city_physics_mutation_but_allows_new_owner(self):
        root, jar, sources, baseline = self.fixture()
        definitions = json.loads((root / gate.CITY / "definitions.json").read_text()); definitions["blocks"].append({"id":"owner_new", "states":{}})
        (root / gate.CITY / "definitions.json").write_text(json.dumps(definitions))
        self.refresh_runtime_jar(root, jar)
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            self.assertEqual("PASS", gate.validate(jar, sources, root, root.parent)["result"])
        definitions["blocks"][0]["states"] = {"changed": True}; (root / gate.CITY / "definitions.json").write_text(json.dumps(definitions))
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            with self.assertRaisesRegex(AssertionError, "RC1_CITY_DEFINITION_CHANGED"):
                gate.validate(jar, sources, root, root.parent)

    def test_rejects_changed_referenced_city_profile_but_ignores_footprint_metadata(self):
        root, _jar, _sources, baseline = self.fixture()
        geometry = json.loads((root / gate.CITY / "geometry.json").read_text())
        geometry["profiles"]["city_profile"]["physical_footprint"] = {"note": "changed metadata"}
        (root / gate.CITY / "geometry.json").write_text(json.dumps(geometry))
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            self.assertEqual("PASS", gate.validate_sources(root, root.parent)["result"])
        geometry["profiles"]["city_profile"]["collision"] = [[0, 0, 0, .5, 1, 1]]
        (root / gate.CITY / "geometry.json").write_text(json.dumps(geometry))
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            with self.assertRaisesRegex(AssertionError, "RC1_CITY_PROFILE_PHYSICS_CHANGED"):
                gate.validate_sources(root, root.parent)

    def test_rejects_renamed_old_runtime_source(self):
        root, jar, sources, baseline = self.fixture()
        with zipfile.ZipFile(sources, "w") as archive: archive.writestr("example/Runtime.java", b"class Beta3 {}")
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            with self.assertRaisesRegex(AssertionError, "SOURCES_JAR_PROVENANCE_CHANGED"):
                gate.validate(jar, sources, root, root.parent)

    def test_accepts_loom_processed_mixin_refmap_with_named_sources(self):
        root, jar, sources, baseline = self.fixture()
        (root / "src/main/resources/fabric.mod.json").write_text('{"version":"1","mixins":["mixins.json"]}')
        (root / "src/main/resources/mixins.json").write_text('{"required":true}')
        self.refresh_runtime_jar(root, jar)
        with patch.object(gate, "_git", self._git(baseline)), patch.object(gate, "_tree", self._tree(baseline)):
            self.assertEqual("PASS", gate.validate(jar, sources, root, root.parent)["result"])


if __name__ == "__main__":
    unittest.main()
