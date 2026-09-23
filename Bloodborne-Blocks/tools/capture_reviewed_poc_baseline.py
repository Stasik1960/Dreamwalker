"""One-time frozen Contract V2 POC evidence capture (not used by compilation)."""
from __future__ import annotations

import gzip
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/reviewed-batch-02-poc-baseline.json"
COMMIT = "277c150"
IDS = ("o_dead_tree_planter", "o_cases_0", "o_wall_deco_1", "o_iron_gate", "o_iron_railing")

def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()

def read(path, gzip_file=False):
    raw = subprocess.check_output(["git", "show", f"{COMMIT}:Bloodborne-Blocks/{path}"], cwd=ROOT.parent)
    return json.loads(gzip.decompress(raw) if gzip_file else raw)

def main():
    definitions = {row["id"]: row for row in read("src/main/resources/bloodborne_blocks/logical/definitions.json")["blocks"]}
    contracts = {row["id"]: row for row in read("src/main/resources/bloodborne_blocks/logical/contracts-v2.json")["families"]}
    meshes = read("src/main/resources/bloodborne_blocks/logical/meshes.json.gz", True)
    poc = {ident: {"definition": digest(definitions[ident]), "contract": digest(contracts[ident]),
                   "meshes": {state: digest(meshes[mesh]) for state, mesh in definitions[ident]["models"].items()}}
           for ident in IDS}
    value = {"schemaVersion": 1, "base_commit": COMMIT, "canonical_json": "utf8/sort_keys/separators", "poc": poc}
    text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if OUT.exists() and OUT.read_text(encoding="utf8") != text: raise ValueError("frozen POC baseline differs")
    OUT.write_text(text, encoding="utf8")

if __name__ == "__main__": main()
