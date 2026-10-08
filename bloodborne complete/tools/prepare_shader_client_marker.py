"""Create an explicit shader observation marker from exact supplied inputs."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from prepare_shader_profile import settings_static_audit

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, default=ROOT / "tools/first_set_client_input.json")
    parser.add_argument("--shader-pack", type=Path, required=True)
    parser.add_argument("--shader-settings", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "tools/first_set_client_shader_input.json")
    args = parser.parse_args()
    base = args.base.read_bytes()
    pack, settings = args.shader_pack.read_bytes(), args.shader_settings.read_bytes()
    audit = settings_static_audit(pack, settings)
    assert audit["status"] == "STATIC_DECLARATIONS_MATCH_RUNTIME_UNVERIFIED", audit
    options = {row["key"]: row["value"] for row in audit["checks"]}
    assert len(options) == audit["provided_setting_count"]
    marker = json.loads(base)
    assert marker["guard"] == "ISOLATED_SAVED_REVIEW_CLIENT_ONLY" and marker["schemaVersion"] == 1
    marker.update({"expectedShaderPack": args.shader_pack.name, "expectedShaderOptions": options,
                   "expectedShaderPackSha256": digest(pack), "expectedShaderSettingsSha256": digest(settings),
                   "shaderObservationSourceMarkerSha256": digest(base),
                   "shaderObservation": "ACTIVE_PIPELINE_AND_EFFECTIVE_OPTIONS; MANUAL_VISUAL_NOT_RUN"})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(marker, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    assert args.base.read_bytes() == base and args.shader_pack.read_bytes() == pack and args.shader_settings.read_bytes() == settings
    print(json.dumps({"marker": str(args.output.resolve()), "marker_sha256": digest(args.output.read_bytes()),
                      "expected_shader_pack": args.shader_pack.name, "expected_option_count": len(options),
                      "original_inputs_changed": False}))


if __name__ == "__main__":
    main()
