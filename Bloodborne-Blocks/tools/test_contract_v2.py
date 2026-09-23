"""Contract V2 checks with frozen POC projections and synthetic Anvil worlds.

No source city extraction or conversion. Fixtures use exact raw vanilla states;
unrepresentable original rotations are explicitly absent, never fabricated.
"""
from __future__ import annotations
from copy import deepcopy
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from logical_contract_v2 import load_contracts, master_origin, rotate_cell, rotate_box, direct_rules
from convert_logical_world import World, convert, PART, hash_tree
from check_logical_world import check
from test_logical_world import write_chunk, entity
from world_io import NbtFile, Tag, TAG_COMPOUND, write_nbt

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT/"src/main/resources/bloodborne_blocks/logical"
DIM = "minecraft:overworld"
POC_IDS = {"o_dead_tree_planter", "o_cases_0", "o_wall_deco_1", "o_iron_gate", "o_iron_railing"}
BATCH_IDS = {"o_c001_a", "o_c001_b", "o_c009_a", "o_c009_b", "o_c002", "o_c003", "o_c008", "o_c471", "o_c046", "o_c1680", "o_c474", "o_c1962", "o_c1979", "o_c028", "o_c282", "o_c561", "o_c618", "o_c654", "o_c1319", "o_c1491"}
ORIGINAL_FAMILY_IDS = POC_IDS | BATCH_IDS | {"o_c001"}


def nightmare_output_ids():
    """Read the reviewed split outputs instead of maintaining a second ID list."""
    report = json.loads((ROOT / "docs/nightmare-qa-report.json").read_text(encoding="utf-8"))
    if report.get("compiler_owner") != "nightmare_qa" or not isinstance(report.get("families"), list):
        raise ValueError("invalid NightmareRunning QA report")
    outputs = set()
    for row in report["families"]:
        if not isinstance(row.get("outputs"), list) or not row["outputs"]:
            raise ValueError("QA report family lacks outputs")
        for ident in row["outputs"]:
            if not isinstance(ident, str) or not ident.startswith("o_"):
                raise ValueError("QA report output is not a logical ID")
            outputs.add(ident)
    return outputs


def canonical_digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def visual_base_projection(definition, contract):
    """Remove the additive visual dimension before comparing frozen POC data."""
    definition, contract = deepcopy(definition), deepcopy(contract)
    if "visual" not in definition["properties"]:
        return definition, contract
    definition["properties"].pop("visual")
    definition["default"].pop("visual")
    definition.pop("visual_models", None)

    def strip(state):
        return ",".join(part for part in state.split(",") if part != "visual=base")

    definition["models"] = {strip(state): mesh for state, mesh in definition["models"].items() if "visual=base" in state}
    definition["states"] = {strip(state): value for state, value in definition["states"].items() if "visual=base" in state}
    contract["states"] = {strip(state): value for state, value in contract["states"].items() if "visual=base" in state}
    return definition, contract


def railing_north_projection(definition, contract):
    """The facing migration is explicit; frozen POC evidence is its north view."""
    definition, contract = deepcopy(definition), deepcopy(contract)
    definition["properties"].pop("facing"); definition["default"].pop("facing"); definition["extra_facing"] = False
    def strip(state):
        return ",".join(part for part in state.split(",") if part != "facing=north")
    definition["models"] = {strip(state): mesh for state, mesh in definition["models"].items() if "facing=north" in state}
    definition["states"] = {strip(state): value for state, value in definition["states"].items() if "facing=north" in state}
    contract["states"] = {strip(state): value for state, value in contract["states"].items() if "facing=north" in state}
    return definition, contract


class ContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data, cls.transform = load_contracts(RES)
        cls.rules, cls.defaults = direct_rules(RES, poc_only=True)
        cls.families = {f["id"]:f for f in cls.data["families"]}
        cls.rows = []

    def test_shared_vectors_and_all_family_origins(self):
        expected = ORIGINAL_FAMILY_IDS | nightmare_output_ids()
        self.assertEqual(len(self.families),47)
        self.assertEqual(set(self.families), expected)
        for f in self.families.values():
            for rotation in (0,90,180,270):
                # Same canonical master for all orientations when anchor point
                # is located according to the SAME explicit transform.
                master = (103,68,-47)
                rotated = rotate_cell(f["canonical_anchor"]["cell"],rotation,self.transform)
                placement = tuple(master[i]+rotated[i] for i in range(3))
                self.assertEqual(master_origin(placement,f["canonical_anchor"]["cell"],rotation,self.transform),master)
            for key,state in f["states"].items():
                self.assertEqual(len(state["selection_footprint"]["boxes"]),1)
                self.assertIn([0,0,0],state["interaction_footprint"]["cells"])
        box = [-1,-1,.25,2,7,.75]
        rotated = box
        for _ in range(4):
            rotated = rotate_box(rotated,90,self.transform)
        self.assertEqual(rotated,box)

    def test_contract_mutations_rejected(self):
        # Validate the safety boundary without rewriting checked-in resources.
        with tempfile.TemporaryDirectory() as folder:
            out = Path(folder)
            for name in ("definitions.json","transform-v2.json"):
                (out/name).write_bytes((RES/name).read_bytes())
            mutations = [lambda f: f["states"][next(iter(f["states"]))]["collision_footprint"].update(boxes=[[0,0,0,1,1,1]]*3),
                         lambda f: f["states"][next(iter(f["states"]))]["interaction_footprint"].update(cells=[[0,0,0]]),
                         lambda f: f["canonical_anchor"].update(cell=[0,.5,0]),
                         lambda f: f.update(mirror_policy="unreviewed_reflection")]
            for mutate in mutations:
                data = deepcopy(self.data)
                mutate(data["families"][0])
                (out/"contracts-v2.json").write_text(json.dumps(data),encoding="utf-8")
                with self.assertRaises((ValueError,KeyError)):
                    load_contracts(out)
            data = deepcopy(self.data)
            data["families"] = [family for family in data["families"] if family["id"] != "o_dead_tree_planter"]
            (out/"contracts-v2.json").write_text(json.dumps(data),encoding="utf-8")
            with self.assertRaises(ValueError):
                load_contracts(out)

    def test_frozen_poc_projection_preserved(self):
        baseline = json.loads((ROOT/"docs/reviewed-batch-02-poc-baseline.json").read_text(encoding="utf-8"))["poc"]
        definitions = {row["id"]: row for row in json.loads((RES/"definitions.json").read_text(encoding="utf-8"))["blocks"]}
        contracts = {row["id"]: row for row in self.data["families"]}
        with gzip.open(RES/"meshes.json.gz", "rt", encoding="utf-8") as stream:
            meshes = json.load(stream)
        for ident, expected in baseline.items():
            if ident == "o_wall_deco_1":
                continue  # explicit reviewed floor correction has its own evidence below
            definition, contract = visual_base_projection(definitions[ident], contracts[ident])
            if ident == "o_iron_railing":
                definition, contract = railing_north_projection(definition, contract)
            self.assertEqual(canonical_digest(definition), expected["definition"], ident)
            self.assertEqual(canonical_digest(contract), expected["contract"], ident)
            self.assertEqual({state: canonical_digest(meshes[mesh]) for state, mesh in definition["models"].items()}, expected["meshes"], ident)

    def test_wall_floor_correction_keeps_explicit_old_baseline_evidence(self):
        with gzip.open(ROOT / "docs/nightmare-qa-baseline.json.gz", "rt", encoding="utf-8") as stream:
            baseline = json.load(stream)
        old_definition = next(row for row in baseline["definitions"] if row["id"] == "o_wall_deco_1")
        old_contract = next(row for row in baseline["families"] if row["id"] == "o_wall_deco_1")
        self.assertEqual(old_definition["models"]["facing=north"], "o_5157f2ac712421b0b3b9")
        self.assertEqual(old_contract["states"]["facing=north"]["render_mesh"]["bounds"][1], -1.0)
        self.assertEqual(old_contract["collision_policy"], "NONE")
        wall = self.families["o_wall_deco_1"]
        self.assertEqual(wall["collision_policy"], "NONE")
        projected_definition, projected_wall = visual_base_projection(
            next(row for row in json.loads((RES / "definitions.json").read_text(encoding="utf-8"))["blocks"] if row["id"] == "o_wall_deco_1"), wall)
        self.assertEqual(projected_definition["properties"].get("visual"), None)
        for state, current in projected_wall["states"].items():
            previous = old_contract["states"][state]
            self.assertEqual(previous["migration_source_pattern"][0]["components"][0]["offset"][1], 0)
            self.assertEqual(current["migration_source_pattern"][0]["components"][0]["offset"][1], 0)
            self.assertEqual(current["collision_footprint"]["boxes"], [])
            self.assertEqual(current["render_mesh"]["bounds"][1], 0.0)

    def test_direct_migration_fixture(self):
        root = (8,68,8)
        with tempfile.TemporaryDirectory(prefix="bloodborne-contract-poc-") as folder:
            base = Path(folder)
            reports = base/"reports"
            reports.mkdir()
            for rule in self.rules:
                ident = rule.target[0].split(":",1)[1]
                family = self.families[ident]
                key = ",".join(f"{k}={v}" for k,v in rule.target[1])
                state = family["states"][key]
                pieces = (rule.source,)+rule.members
                placed = {tuple(root[i]+p.offset[i] for i in range(3)):(p.state[0],dict(p.state[1])) for p in pieces}
                # A light/block occupies a VISUAL-only old cell for tree, cases,
                # wall. Other families also preserve an unrelated nearby stone.
                rotation = state["rotation"]
                if ident == "o_dead_tree_planter":
                    foreign_offset = (-1,3,0)
                elif ident in ("o_cases_0","o_wall_deco_1"):
                    foreign_offset = rotate_cell((-1,0,0),rotation,self.transform)
                else:
                    foreign_offset = (3,0,3)
                self.assertNotIn(foreign_offset,rule.shape)
                foreign_pos = tuple(root[i]+foreign_offset[i] for i in range(3))
                foreign = ("minecraft:light",{"level":"15","waterlogged":"false"}) if ident=="o_dead_tree_planter" else ("minecraft:stone",{})
                placed[foreign_pos] = foreign
                source, output = base/f"source-{rule.number}",base/f"converted-{rule.number}"
                source.mkdir()
                write_nbt(source/"level.dat",NbtFile("",Tag(TAG_COMPOUND,{"Data":Tag(TAG_COMPOUND,{})})))
                write_chunk(source/"region/r.0.0.mca",0,0,placed)
                hashes = hash_tree(source)
                report_path = reports/f"{rule.number}.json"
                report = convert(source,output,resources=RES,report_path=report_path,report_root=reports,source_mode="original-v2-poc")
                self.assertEqual(report["counts"]["converted"],1,(ident,key,report["rejected"]))
                self.assertEqual(hash_tree(source),hashes)
                after = World(output,self.defaults)
                self.assertEqual(after.get(DIM,root),rule.target)
                self.assertEqual(after.get(DIM,foreign_pos),(foreign[0],tuple(sorted(foreign[1].items()))))
                for offset in rule.shape:
                    pos = tuple(root[i]+offset[i] for i in range(3))
                    self.assertEqual(after.get(DIM,pos),rule.target if offset==(0,0,0) else (PART,()))
                for p in pieces:
                    if p.offset not in rule.shape:
                        self.assertEqual(after.get(DIM,tuple(root[i]+p.offset[i] for i in range(3)))[0],"minecraft:air")
                check(source,output,report_path,RES)
                repeated = base/f"repeat-{rule.number}"
                second = convert(output,repeated,resources=RES,report_path=reports/f"repeat-{rule.number}.json",report_root=reports,source_mode="original-v2-poc")
                self.assertEqual(second["counts"]["converted"],0)
                self.assertEqual(hash_tree(output),hash_tree(repeated))
                rotated_anchor = rotate_cell(family["canonical_anchor"]["cell"],state["rotation"],self.transform)
                placement = tuple(root[i]+rotated_anchor[i] for i in range(3))
                self.assertEqual(master_origin(placement,family["canonical_anchor"]["cell"],state["rotation"],self.transform),root)
                self.rows.append({"family":ident,"state":key,"sourcePattern":[{"id":p.state[0],"properties":dict(p.state[1]),"relative":list(p.offset)} for p in pieces],
                                  "master":list(root),"manualPlacementCell":list(placement),"foreignPreserved":True,"idempotent":True,"independentLedgerCheck":True})
            # Partial tree must not consume its first two source components.
            source = base/"partial"
            source.mkdir()
            write_nbt(source/"level.dat",NbtFile("",Tag(TAG_COMPOUND,{"Data":Tag(TAG_COMPOUND,{})})))
            partial = {root:("minecraft:white_wool",{}),(8,71,8):("minecraft:orange_wool",{})}
            write_chunk(source/"region/r.0.0.mca",0,0,partial)
            output = base/"partial-out"
            report = convert(source,output,resources=RES,report_path=reports/"partial.json",report_root=reports,source_mode="original-v2-poc")
            self.assertEqual(report["counts"]["converted"],0)
            self.assertEqual(hash_tree(source),hash_tree(output))
            # Foreign real physics cell is a conflict, unlike foreign visual cell.
            blocked = base/"blocked"
            blocked.mkdir()
            write_nbt(blocked/"level.dat",NbtFile("",Tag(TAG_COMPOUND,{"Data":Tag(TAG_COMPOUND,{})})))
            placed = {root:("minecraft:white_wool",{}),(8,71,8):("minecraft:orange_wool",{}),(8,74,8):("minecraft:magenta_wool",{}),(8,69,8):("minecraft:stone",{})}
            write_chunk(blocked/"region/r.0.0.mca",0,0,placed)
            report = convert(blocked,base/"blocked-out",resources=RES,report_path=reports/"blocked.json",report_root=reports,source_mode="original-v2-poc")
            self.assertEqual(report["counts"]["converted"],0)
            self.assertTrue(any(x["reason"]=="target_would_overwrite_foreign_block" for x in report["rejected"]))
        # Small machine-readable proof, not a world or a historical city ledger.
        report_path = ROOT/"build/test-results/contract-poc-v2.json"
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps({"result":"passed","fixture":"synthetic one-chunk Anvil cases; city untouched","directCases":self.rows},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
