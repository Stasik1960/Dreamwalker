"""Verify additive own-sprite registration against a stopped ordinary client run."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ENTRY = "assets/minecraft/atlases/blocks.json"


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding="utf8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-report", type=Path, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    wrapper = read(args.client_report)
    artifact_sha = digest(args.artifact)
    assert wrapper["artifact_sha256"] == artifact_sha
    assert wrapper["status"] == "PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT" and wrapper["exit_code"] == 0
    assert wrapper["integrated_save_messages_present"] is True
    result = wrapper["client_review_output"]
    assert digest(result["path"]) == result["sha256"] and read(result["path"]) == result["result"]
    client = result["result"]
    assert client["status"] == "PASS_CLIENT_WORLD_RESOURCES_NORMAL_STOP_REQUESTED" and client["normalStopRequested"]
    offline_path = ROOT / "reports/BLOCK_ATLAS_ADDITIVE_IMPORT.json"
    offline = read(offline_path)
    metadata_path = ROOT / "src/architecture/resources" / ENTRY
    metadata = read(metadata_path)
    assert digest(metadata_path) == offline["metadata_sha256"]
    sources = metadata["sources"]
    assert len(sources) == offline["added_sprite_count"] == 16
    assert all(set(row) == {"type", "resource"} and row["type"] == "minecraft:single"
               and row["resource"].startswith("bloodborne_dw:") for row in sources)
    required = {row["resource"] for row in sources}
    assert len(required) == len(sources)
    with zipfile.ZipFile(args.artifact) as artifact:
        assert artifact.read(ENTRY) == metadata_path.read_bytes()
        assert not any(name.startswith(("assets/minecraft/models/", "assets/minecraft/textures/"))
                       for name in artifact.namelist()), "Architectural vanilla model/texture override present"
    models = client["actualBakedModels"]
    assert all(not row["missingModel"] and "minecraft:missingno" not in row["quadSpriteIds"] for row in models)
    stone = next(row for row in models if row["model"] == "vanilla-stone")
    assert stone["quadCount"] == 6 and stone["quadSpriteIds"] == ["minecraft:block/stone"]
    seen = {sprite for row in models for sprite in row["quadSpriteIds"]}
    assert required <= seen, "An added own sprite was not observed in actual baked quads"
    assert client["compositeSourceModelsChecked"] == 52
    assert client["ladderStateModelsChecked"] == 192
    assert client["wallRepresentativeStateModelsChecked"] == 512
    report = {
        "schema": "dreamwalker-additive-block-atlas-runtime-v1",
        "status": "PASS_ALL_ADDED_SPRITES_AND_VANILLA_STONE_IN_ORDINARY_CLIENT",
        "artifact": str(args.artifact.resolve()), "artifact_sha256": artifact_sha,
        "client_report": str(args.client_report.resolve()), "client_report_sha256": digest(args.client_report),
        "client_output": result["path"], "client_output_sha256": result["sha256"],
        "offline_report": str(offline_path), "offline_report_sha256": digest(offline_path),
        "metadata_sha256": offline["metadata_sha256"], "metadata_bytes_match_production_jar": True,
        "own_added_sprite_ids_observed": sorted(required), "vanilla_stone_sprite": "minecraft:block/stone",
        "counts": {"own_added_sprites_observed": len(required), "actual_baked_models": len(models),
                   "composite_source_models": 52, "ladder_state_models": 192, "wall_representative_states": 512},
        "missing_models_or_sprites": [], "ordinary_client_normal_exit": True,
        "visual_acceptance": "PENDING_USER_REVIEW",
        "limits": ["Actual baked-model sprites and normal world save/exit do not establish manual Creative UI, art or shader acceptance"]
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": report["status"], "artifact_sha256": artifact_sha, "counts": report["counts"]}))


if __name__ == "__main__":
    main()
