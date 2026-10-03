"""Small, dependency-free contract tests for the unified registry primitives."""
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from build_unified_registry import MeshStore, digest, full, key, plan, protected_model_dependencies, remap_document, rotate_mesh, whole_family


def _mesh(x=0.0):
    return {"polygons": [{"texture": "minecraft:block/stone_0", "vertices": [[x, 0, 0, 0, 0], [x + 1, 0, 0, 1, 0], [x + 1, 1, 0, 1, 1], [x, 1, 0, 0, 1]]}]}


def test_normalized_duplicate_keeps_exact_pose_and_position():
    a, b = _mesh(), _mesh()
    assert digest(a) == digest(b)
    assert digest(_mesh(0.125)) != digest(a)
    assert rotate_mesh(a, 0) == a
    assert rotate_mesh(a, 1)["polygons"][0]["vertices"][0][:3] == [1, 0, 0]


def test_canonical_state_key_is_deterministic():
    assert key({"variant": "2", "facing": "east"}) == "facing=east,variant=2"
    assert key({"facing": "east", "variant": "2"}) == key({"variant": "2", "facing": "east"})


def test_whole_family_preserves_appearance_and_authored_vertical_position():
    block={"kind":"generic","source":"minecraft:stone","semantic":"architecture"}
    geometry={"cells":{"0,0,0":{"collision":[[0,0,0,1,1,1]],"outline":[]}},"anchor":[0,0,0],"render_offset":[0,0,0]}
    row={"normalized_geometry":"same-shape","normalized_appearance":"stone-uv-a","normalization":{"turns":0,"shift":[0,0,0]}}
    top={**row,"normalization":{"turns":0,"shift":[0,1,0]}}
    texture={**row,"normalized_appearance":"stone-uv-b"}
    assert whole_family(block,row,_mesh(),geometry) != whole_family(block,top,_mesh(),geometry)
    assert whole_family(block,row,_mesh(),geometry) != whole_family(block,texture,_mesh(),geometry)


def test_whole_family_deduplicates_exact_quarter_rotations():
    block={"kind":"generic","source":"minecraft:stone","semantic":"architecture"}
    geometry={"cells":{"0,0,0":{"collision":[[0,0,0,1,1,1]],"outline":[]}},"anchor":[0,0,0],"render_offset":[0,0,0]}
    north={"normalized_geometry":"same-shape","normalized_appearance":"same-surface","normalization":{"turns":1,"shift":[-1,0,0]}}
    east={"normalized_geometry":"same-shape","normalized_appearance":"same-surface","normalization":{"turns":3,"shift":[-1,0,0]}}
    assert whole_family(block,north,_mesh(),geometry) == whole_family(block,east,rotate_mesh(_mesh(),3),geometry)


def test_protected_blockstate_model_dependencies_are_retained():
    with tempfile.TemporaryDirectory() as td:
        resources=Path(td);path=resources/'assets/bloodborne_blocks/blockstates/building_stone_brick_wall.json';path.parent.mkdir(parents=True)
        path.write_text(json.dumps({'variants':{'connection=low_0':{'model':'bloodborne_blocks:block/city/owner_retained'}}}),encoding='utf8')
        data={'blocks':[{'id':'building_stone_brick_wall','models':{'facing=north':'mesh'}}]}
        assert protected_model_dependencies(data,resources) == {'owner_retained'}


def test_meshstore_invalidation_and_protected_contract():
    with tempfile.TemporaryDirectory() as td:
        root = Path(td); source = root / "source"; source.mkdir()
        import gzip
        for name, payload in (("meshes.json.gz", {"a": _mesh()}), ("owner-meshes.json.gz", {})):
            with gzip.open(source / name, "wt", encoding="utf8") as fh: json.dump(payload, fh)
        db = root / "cache.sqlite"
        store = MeshStore(source, db)
        assert store.get("a") == _mesh()
        with gzip.open(source / "meshes.json.gz", "wt", encoding="utf8") as fh: json.dump({"a": _mesh(0.25)}, fh)
        refreshed = MeshStore(source, db)
        assert refreshed.get("a") == _mesh(0.25)
        refreshed.db.close()
        store.db.close()


def test_document_remap_preserves_legacy_components_and_splits_candidate_families():
    definitions={"blocks":[
        {"id":"mutable","default":{"facing":"north","variant":"0"}},
        {"id":"protected","default":{"variant":"0"},"document_item":True},
    ]}
    mapping={
        full("mutable","facing=north,variant=1"):{"id":"bloodborne_blocks:unified_first","properties":{"facing":"north","root_anchor":"canonical","variant":"7"},"rootOffset":[0,0,0]},
        full("mutable","facing=east,variant=2"):{"id":"bloodborne_blocks:unified_second","properties":{"facing":"east","root_anchor":"canonical","variant":"3"},"rootOffset":[0,0,0]},
        full("protected","variant=2"):{"id":"bloodborne_blocks:protected","properties":{"variant":"2"},"rootOffset":[0,0,0]},
    }
    document={"objects":[{"components":[
        {"id":"bloodborne_blocks:mutable","properties":{"variant":"1"}},
        {"id":"bloodborne_blocks:protected","properties":{"variant":"2"}},
    ],"choices":[{"id":"mutable","candidates":["variant=1","facing=east,variant=2","variant=1"]}]}]}
    result=remap_document(document,definitions,mapping)
    mutable,protected=result["objects"][0]["components"]
    assert mutable == {"id":"bloodborne_blocks:unified_first","properties":{"facing":"north","root_anchor":"canonical","variant":"7"},"legacyId":"bloodborne_blocks:mutable","legacyProperties":{"variant":"1"}}
    assert protected == {"id":"bloodborne_blocks:protected","properties":{"variant":"2"},"legacyId":"bloodborne_blocks:protected","legacyProperties":{"variant":"2"}}
    assert result["objects"][0]["choices"] == [
        {"id":"unified_first","candidates":["facing=north,root_anchor=canonical,variant=7"],"legacyCandidateId":"mutable"},
        {"id":"unified_second","candidates":["facing=east,root_anchor=canonical,variant=3"],"legacyCandidateId":"mutable"},
    ]
    assert document["objects"][0]["components"][0]["id"] == "bloodborne_blocks:mutable"


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_"): fn()
    print("ok: unified registry fixture tests")
