"""Focused invariant checks for the generated creative equivalence map."""
from __future__ import annotations

import gzip
import json
import tempfile
from pathlib import Path

from build_creative_equivalence import build, canonical
from creative_model_evidence import ModelEvidence

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/bloodborne_blocks/creative-equivalence.json"
REPORT = ROOT / "docs/catalog-city-continuation/catalog-equivalence-report.json.gz"


def main() -> None:
    # Content, rather than an asset path, is the texture identity.  Animation
    # metadata is content too, and installed coordinates remain exact.
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        texture_root = root / "src/main/resources/assets/example/textures"
        texture_root.mkdir(parents=True)
        (texture_root / "a.png").write_bytes(b"same-png")
        (texture_root / "b.png").write_bytes(b"same-png")
        mesh_a = {"polygons": [{"texture": "example:a", "vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 1, 0], [1, 1, 0, 1, 1]]}]}
        mesh_b = {"polygons": [{"texture": "example:b", "vertices": [[0, 0, 0, 0, 0], [1, 0, 0, 1, 0], [1, 1, 0, 1, 1]]}]}
        resources = root / "src/main/resources"
        meshes = {"a": mesh_a, "b": mesh_b}
        proof = ModelEvidence(resources, meshes, {})
        assert proof.mesh("a") == proof.mesh("b"), "texture paths cannot create an artistic difference"
        models = resources / "assets/example/models"
        models.mkdir(parents=True)
        (models / "base.json").write_text(json.dumps({"bloodborne_mesh": "a", "bloodborne_texture_slots": {"example:a": "#color"}, "textures": {"color": "example:a"}}))
        (models / "alt.json").write_text(json.dumps({"parent": "example:base"}))
        assert proof.visual("example:base", "a") == proof.visual("example:alt", "a"), "plain ALT inheritance resolves to BASE"
        (models / "alt.json").write_text(json.dumps({"parent": "example:base", "textures": {"color": "example:b"}}))
        proof = ModelEvidence(resources, meshes, {})
        assert proof.visual("example:base", "a") == proof.visual("example:alt", "a"), "content-equal inherited texture overrides resolve equally"
        (texture_root / "b.png.mcmeta").write_text('{"animation":{"frametime":2}}', encoding="utf-8")
        proof = ModelEvidence(resources, meshes, {})
        assert proof.mesh("a") != proof.mesh("b"), "texture animation is visible behavior"
        assert proof.visual("example:base", "a") != proof.visual("example:alt", "a"), "changed inherited texture creates distinct art on a new build"
        (texture_root / "b.png.mcmeta").unlink()
        mesh_b["polygons"][0]["vertices"][1][0] = 0.75
        proof = ModelEvidence(resources, meshes, {})
        assert proof.mesh("a") != proof.mesh("b"), "installed coordinates are not normalized"
        mesh_b["polygons"][0]["vertices"][1][0] = 1
        mesh_b["polygons"][0]["vertices"][1][3] = 0.5
        proof = ModelEvidence(resources, meshes, {})
        assert proof.mesh("a") != proof.mesh("b"), "UVs participate in equivalence"
        assert "assets/example/textures/a.png" in proof.source_hashes
        mesh_b["polygons"][0]["vertices"][1][3] = 1
        (texture_root / "glow-a.png").write_bytes(b"red-glow")
        (texture_root / "glow-b.png").write_bytes(b"blue-glow")
        proof = ModelEvidence(resources, meshes, {}, {"example:a": "example:glow-a", "example:b": "example:glow-b"})
        assert proof.mesh("a") == proof.mesh("b"), "unused glow cannot split non-emissive artwork"
        assert proof.mesh("a", True) != proof.mesh("b", True), "same base with different effective glow is not equivalent"
        import copy
        opposite = copy.deepcopy(mesh_a['polygons'][0])
        for vertex in opposite['vertices']:
            vertex[2] += 1
        ordered = {'polygons': [mesh_a['polygons'][0], opposite]}
        reversed_faces = {'polygons': list(reversed(ordered['polygons']))}
        proof = ModelEvidence(resources, {'ordered': ordered, 'reversed': reversed_faces}, {})
        assert proof.mesh('ordered') != proof.mesh('reversed'), "draw order is retained by default"
        assert proof.mesh('ordered', allow_face_reorder=True) == proof.mesh('reversed', allow_face_reorder=True), "disjoint planar faces can be proven independently of listing order"
        overlapping = copy.deepcopy(ordered)
        for vertex in overlapping['polygons'][1]['vertices']:
            vertex[2] -= 1
            vertex[3] += 0.25
        proof = ModelEvidence(resources, {'ordered': overlapping, 'reversed': {'polygons': list(reversed(overlapping['polygons']))}}, {})
        assert proof.mesh('ordered', allow_face_reorder=True) != proof.mesh('reversed', allow_face_reorder=True), "overlapping UV-different faces preserve order"
    runtime = json.loads(OUTPUT.read_text(encoding="utf-8"))
    report = json.loads(gzip.decompress(REPORT.read_bytes()))
    regenerated_runtime, regenerated_report = build()
    assert canonical(regenerated_runtime) + b"\n" == OUTPUT.read_bytes(), "runtime map is stale"
    assert canonical(regenerated_report) == gzip.decompress(REPORT.read_bytes()), "report is stale"
    assert runtime["schemaVersion"] == 1
    redirects = runtime["redirects"]
    assert all(target not in redirects for target in redirects.values()), "redirect chains are forbidden"
    assert runtime["summary"] == report["summary"]
    assert runtime["summary"]["visibleEntries"] + runtime["summary"]["hiddenEquivalent"] == runtime["summary"]["candidateArt"]
    assert all("facing=" not in key and "open=" not in key and "lit=" not in key and "connection=" not in key and "root_anchor=" not in key for key in redirects)
    assert not any("building_stone_brick_wall" in row["source"]["id"] for row in report["redirects"])
    registered = sum(len(json.loads((ROOT / "src/main/resources/bloodborne_blocks" / scope / "definitions.json").read_bytes())["blocks"]) for scope in ("logical", "city"))
    assert runtime["summary"]["registryIds"] == registered
    expected = {
        "o_shuttered_window|variant=|visual=alt": "o_shuttered_window|variant=|visual=base",
        "o_c1979_2|variant=canonical|visual=base": "o_c1979_1|variant=canonical|visual=base",
    }
    for source, target in expected.items():
        assert redirects.get(source) == target, f"missing approved equivalence: {source}"
    # Canonical serialization gives the source result a stable byte form; the
    # command-level --check covers a fresh-process regeneration separately.
    assert canonical(regenerated_runtime) == canonical(json.loads(canonical(regenerated_runtime)))
    assert canonical(regenerated_report) == canonical(json.loads(canonical(regenerated_report)))
    print("creative equivalence PASS", json.dumps(runtime["summary"], sort_keys=True))


if __name__ == "__main__":
    main()
