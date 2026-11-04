"""Verify the combined RGB JAR offline against its pinned City Editor baseline."""
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import argparse
import json

from PIL import Image

ALT = "dw_city_visuals/alt/"
ORIGINAL = "dw_city_visuals/original/"
POLICY = "bloodborne_dw/rgb-phase1.json"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--revision-suffix", default="-rgb.1")
    parser.add_argument("--catalogue", type=Path, help="offline geometry evidence for the audited catalogue expansion")
    args = parser.parse_args()
    with ZipFile(args.jar) as jar, ZipFile(args.previous) as previous:
        assert jar.testzip() is None, "Invalid ZIP CRC"
        names = set(jar.namelist())
        old_names = set(previous.namelist())
        mod = json.loads(jar.read("fabric.mod.json"))
        assert mod["id"] == "bloodborne_dw" and mod["version"].endswith(args.revision_suffix)
        assert not any("dw-editorqa" in name or "/editorqa/" in name for name in names)
        item_model = json.loads(jar.read("assets/bloodborne_dw/models/item/nrp_pickaxe.json"))
        assert item_model["parent"] == "minecraft:item/handheld"
        namespace, sprite = item_model["textures"]["layer0"].split(":", 1)
        pixels = Image.open(BytesIO(jar.read(f"assets/{namespace}/textures/{sprite}.png"))).convert("RGBA")
        assert pixels.size == (16,16) and pixels.getchannel("A").getextrema() == (0,255)
        assert any(r > g+25 and b > g+10 and a == 255 for r, g, b, a in pixels.getdata())
        ru = json.loads(jar.read("assets/bloodborne_dw/lang/ru_ru.json"))
        assert ru["item.bloodborne_dw.nrp_pickaxe"] == "НРП-Кирка"
        registered = set(mod["entrypoints"]["main"])
        assert "dev.dreamwalker.bloodbornedw.item.NrpPickaxe" in registered
        configs = {m if isinstance(m,str) else m["config"]: m for m in mod["mixins"]}
        mining_configs = ["dw-nrp-mining.mixins.json", "dw-nrp-combat.mixins.json"]
        if args.catalogue:
            client_config = "dw-nrp-mining-client.mixins.json"
            mining_configs.append(client_config)
            assert configs[client_config]["environment"] == "client"
            client = json.loads(jar.read(client_config))
            common = json.loads(jar.read("dw-nrp-mining.mixins.json"))
            assert client["client"] == ["ClientMiningMixin"] and not common.get("client")
            assert client["refmap"] == "client-dw-altvisual-overlay-refmap.json"
            assert common["refmap"] == "dw-altvisual-overlay-refmap.json"
            prefix = client["package"].replace(".", "/") + "/"
            client_refmap = json.loads(jar.read(client["refmap"]))["mappings"][prefix + "ClientMiningMixin"]
            assert {"attackBlock", "breakBlock"} <= client_refmap.keys()
            assert any("isBlockBreakingRestricted" in key for key in client_refmap)
            assert "ServerMiningMixin" in common["mixins"]
            assert prefix + "ServerMiningMixin" in json.loads(jar.read(common["refmap"]))["mappings"]
        for config_name in mining_configs:
            assert config_name in configs
            config = json.loads(jar.read(config_name))
            assert config["required"]
            for class_name in config.get("mixins",[]) + config.get("client",[]):
                path = config["package"].replace(".","/") + "/" + class_name + ".class"
                assert path in names, path
        protected = {name for name in old_names if name.startswith(ORIGINAL) and not name.endswith("/")}
        protected.update(name for name in old_names if name.startswith("assets/bloodborne_dw/native_grid/")
                         and not name.endswith("/"))
        if args.catalogue:
            protected.update(name for name in old_names if name.startswith(ALT) and not name.endswith("/"))
            protected.update(name for name in old_names if "whiteness" in name and not name.endswith("/"))
        for name in protected:
            assert jar.read(name) == previous.read(name), name
        policy = json.loads(jar.read(POLICY))
        assert policy["schema"] == (2 if args.catalogue else 1)
        if POLICY in old_names:
            previous_policy = json.loads(previous.read(POLICY))
            def scope(table):
                return {key: (row["block"], row["variants"], row["nativeMeshes"], row["models"], set(row["paints"]))
                        for key, row in table["profiles"].items()}
            if args.catalogue:
                old_scope = scope(previous_policy)
                assert all(scope(policy)[key] == definition for key, definition in old_scope.items())
            else:
                assert scope(policy) == scope(previous_policy), "RGB profile scope changed"
        materials = {}
        rgba_mips = 0
        all_assets = policy.get("assets", {})
        for profile in policy["profiles"].values():
            assets = {key: all_assets[key] for key in profile["assets"]} if args.catalogue else profile["assets"]
            for identifier, expected in assets.items():
                ns, resource = identifier.split(":", 1)
                path = f"assets/{ns}/{resource}"
                actual = next((name for name in (ALT + path, path) if name in names), None)
                if not expected:
                    assert actual is None, path
                else:
                    assert actual and sha256(jar.read(actual)).hexdigest() == expected, path
            for row in profile["paints"].values():
                neutral = row["neutral"]
                if neutral in materials:
                    assert args.catalogue and materials[neutral] == (row["size"], row["mipLevels"])
                    continue
                materials[neutral] = (row["size"], row["mipLevels"])
                ns, texture = neutral.split(":", 1)
                assert f"{ALT}assets/{ns}/textures/{texture}.png" in names
                width, height = row["size"]
                rgba_mips += sum(max(1,width >> level) * max(1,height >> level) * 4
                                 for level in range(row["mipLevels"]+1))
        resource_names = {n for n in names if n.startswith(ALT) and "/textures/block/editor2_rgb/" in n and n.endswith(".png")}
        assert len(materials) == len(resource_names)
        coverage = None
        native_cells = {}
        for profile_name, profile in policy["profiles"].items():
            for index, mesh in profile.get("nativeCells", ()):
                assert isinstance(index, int) and isinstance(mesh, int) and index >= 0 and mesh >= 0
                assert index not in native_cells, (profile_name, index)
                native_cells[index] = mesh
        if args.catalogue:
            rows = json.loads(args.catalogue.read_bytes())["items"]
            assert len(rows) == 416 and len({row["catalogueKey"] for row in rows}) == 416
            exact = {}
            for key, profile in policy["profiles"].items():
                for variant in profile["variants"]:
                    owner = (profile["block"], variant)
                    assert owner not in exact, owner
                    exact[owner] = key
            coverage = {"readyForGameCheck": 0, "excludedWithEvidence": 0}
            for row in rows:
                for candidate in row["variants"]:
                    key = (row["id"], candidate["variant"])
                    if candidate["status"] == "READY_FOR_GAME_CHECK":
                        assert exact[key] == candidate["profile"], key
                        if candidate["recorded"]:
                            coverage["readyForGameCheck"] += 1
                    elif candidate["status"] == "EXCLUDED_WITH_EVIDENCE":
                        assert key not in exact and candidate["keepEvidence"], key
                        if candidate["recorded"]:
                            coverage["excludedWithEvidence"] += 1
                    else:
                        raise AssertionError(("Unresolved catalogue variant", key))
            assert sum(coverage.values()) == 418, coverage
        result = {"status": "PASS", "profiles": len(policy["profiles"]), "neutralSprites": len(materials),
                  "nativeLegacyMeshes": len(policy["nativeAudit"]),
                  "nativeExactCells": len(native_cells), "nativeExactMeshes": len(set(native_cells.values())),
                  "protectedFilesIdentical": len(protected),
                  "fullMipRgbaBytesEstimate": rgba_mips, "version": mod["version"],
                  "nrpPickaxeAssets": "PASS", "catalogueGeometry": coverage, "gameplay": "not run", "fpsAndStalls": "not measured"}
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
