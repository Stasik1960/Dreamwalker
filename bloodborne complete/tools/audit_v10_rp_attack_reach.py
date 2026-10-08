"""Read-only installed-bytecode/saved-state analysis of the FULL4 ATT3 RP attack refusal."""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import subprocess
import zipfile
from pathlib import Path

from world_io import compound, read_nbt

ROOT = Path(__file__).resolve().parents[1]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_evidence(path: Path) -> dict:
    data = path.read_bytes()
    return {"path": str(path.resolve()), "sha256": sha(data), "bytes": len(data)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wrapper", type=Path, default=ROOT / "reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_3.json")
    parser.add_argument("--output", type=Path, default=ROOT / "reports/V10_FULL4_ATT3_RP_ATTACK_REACH_ANALYSIS.json")
    args = parser.parse_args()
    wrapper = json.loads(args.wrapper.read_text(encoding="utf-8"))
    raw_path = Path(wrapper["client_review_output"]["path"])
    raw = json.loads(raw_path.read_text(encoding="utf-8"))
    run = raw_path.parent
    assert wrapper["status"] == "FAIL_CLIENT_REVIEW" and wrapper["exit_code"] == 0
    assert wrapper["artifact_sha256"] == raw["productionJarSha256"]
    case = raw["currentCaseAttempt"]
    assert case["asset"] == "main_gate" and "attack packet main_gate" in raw["failure"]
    camera = case["actualSourcePartCameraCandidate"]
    origin = [-64.0, 100.0, 64.0]
    eye, feet = camera["eye"], camera["feet"]
    assert case["nativeStableRpBefore"] == case["nativeStableRpAfter"]
    assert case["actualMiddlePick"]["status"].startswith("PASS_")
    player_path = run / "saves/prototype-fixture/level.dat"
    player = compound(compound(compound(read_nbt(player_path).root)["Data"])["Player"])
    saved_pos = [node.value for node in player["Pos"].value]
    assert saved_pos == feet
    attrs = {}
    for node in player["Attributes"].value:
        fields = compound(node)
        name = fields["Name"].value
        if name in ("reach-entity-attributes:attack_range", "reach-entity-attributes:reach"):
            attrs[name] = {key: value.value for key, value in fields.items()}
    attack = attrs["reach-entity-attributes:attack_range"]
    assert attack["Base"] == 0.0 and "Modifiers" not in attack
    feet_distance = sum((feet[i] - origin[i]) ** 2 for i in range(3))
    eye_distance = sum((eye[i] - origin[i]) ** 2 for i in range(3))
    assert feet_distance > 64.0 and eye_distance == case["attackEyeToSourceOriginSquared"]

    java_home = Path("C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma")
    javap = java_home / "bin/javap.exe"
    scratch = ROOT / "build/diagnostics/v10-full4att3-server-reach"
    scratch.mkdir(parents=True, exist_ok=True)
    primary = ROOT / "reports/runtime"
    primary.mkdir(parents=True, exist_ok=True)
    mods = run / "mods"
    create = mods / "create-fabric-6.0.8.1+build.1744-mc1.20.1.jar"
    immersive = mods / "immersive-portals-5.2.0-mc1.20.1-fabric.jar"
    extracted = []
    for parent, member in [(create, "META-INF/jars/reach-entity-attributes-2.4.0.jar"), (immersive, "META-INF/jars/imm_ptl_core-5.2.0.jar")]:
        with zipfile.ZipFile(parent) as archive:
            data = archive.read(member)
        destination = scratch / Path(member).name
        destination.write_bytes(data)
        extracted.append({"parent": file_evidence(parent), "member": member, "sha256": sha(data), "bytes": len(data), "extractedPath": str(destination)})
    reach = scratch / "reach-entity-attributes-2.4.0.jar"
    with zipfile.ZipFile(reach) as archive:
        reach_metadata = json.loads(archive.read("fabric.mod.json"))
        reach_mixins = json.loads(archive.read("mixins.reach-entity-attributes.json"))
    mc = next((ROOT / ".gradle/loom-cache/minecraftMaven").rglob("minecraft-merged-*.jar"))
    disassembly = []
    texts = {}
    for cp, name, label in [
        (mc, "net.minecraft.server.network.ServerPlayNetworkHandler", "V10_FULL4_SERVER_HANDLER_VANILLA"),
        (mc, "net.minecraft.server.network.ServerPlayNetworkHandler$1", "V10_FULL4_SERVER_HANDLER_ATTACK_VANILLA"),
        (reach, "com.jamieswhiteshirt.reachentityattributes.mixin.PlayerEntityInteractionHandlerMixin", "V10_FULL4_REACH_ATTRIBUTES_INNER_ATTACK"),
        (reach, "com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes", "V10_FULL4_REACH_ATTRIBUTES_API"),
        (scratch / "imm_ptl_core-5.2.0.jar", "qouteall.imm_ptl.core.mixin.common.networking.MixinServerGamePacketListenerImpl_N", "V10_FULL4_IMMERSIVE_PORTALS_NETWORKING"),
    ]:
        process = subprocess.run([str(javap), "-classpath", str(cp), "-p", "-c", "-v", name], capture_output=True, check=True)
        # Windows javap's localized header uses the Windows code page; JVM names/instructions are ASCII.
        text = process.stdout.decode("cp1251").replace("\r\r\n", "\n").replace("\r\n", "\n")
        target = primary / (label + ".txt")
        target.write_text(text, encoding="utf-8")
        texts[label] = text
        disassembly.append({"class": name, "classpathSha256": sha(cp.read_bytes()), "javap": str(javap), "primary": file_evidence(target)})
    vanilla = texts["V10_FULL4_SERVER_HANDLER_VANILLA"]
    inner = texts["V10_FULL4_REACH_ATTRIBUTES_INNER_ATTACK"]
    api = texts["V10_FULL4_REACH_ATTRIBUTES_API"]
    ip = texts["V10_FULL4_IMMERSIVE_PORTALS_NETWORKING"]
    assert "Box.squaredMagnitude" in vanilla
    assert 'method=["attack()V"]' in inner and "CallbackInfo.cancel" in inner
    assert "ReachEntityAttributes.isWithinAttackRange" in inner
    assert "class_1657.method_5858" in api and "double 64.0d" in api
    assert "handleCustomPayload" in ip and "onPlayerInteractEntity" not in ip
    log = (run / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
    loaded_lines = [line.strip() for line in log.splitlines() if line.strip().startswith(("|-- reach-entity-attributes", "\\-- reach-entity-attributes"))]
    assert loaded_lines
    source_files = [ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java", ROOT / "src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectSelection.java"]
    output = {
        "schema": "dw-v10-installed-rp-attack-reach-analysis-v1",
        "status": "CONFIRMED_INSTALLED_ORIGIN_REACH_GUARD_CONFLICT_WITH_SAVED_ACTOR_STATE",
        "productionJarSha256": wrapper["artifact_sha256"],
        "qaJarSha256": wrapper["extra_mods"][0]["sha256"],
        "wholeClientStatus": wrapper["status"], "exitCode": wrapper["exit_code"],
        "scope": "Read-only installed class disassembly plus actual failed client report and the normally saved player attributes; no replay, runtime mutation, build or code changes",
        "wrapper": file_evidence(args.wrapper), "rawClient": file_evidence(raw_path),
        "savedPlayerFile": file_evidence(player_path),
        "actualFailure": raw["failure"], "actualCase": case,
        "actualCompletedCanonicalCases": len(raw["ordinaryRpCases"]),
        "actualCompletedArchitectureCases": len(raw["ordinaryArchitectureCases"]),
        "distance": {"origin": origin, "feet": feet, "eye": eye,
                     "savedPosEqualsFailedFeet": saved_pos == feet,
                     "feetToOriginSquared": feet_distance, "eyeToOriginSquared": eye_distance,
                     "savedAttackAttribute": attack, "librarySquaredThreshold": 64.0,
                     "originCheckResult": False, "additionalReachNeededForOrigin": math.sqrt(feet_distance) - 8.0,
                     "actualPartHit": case["actualMiddlePick"]["actualSourceHit"]},
        "installedLibrary": {"id": reach_metadata["id"], "version": reach_metadata["version"], "loadedLogLines": loaded_lines, "mixinConfiguration": reach_mixins},
        "extractedInputs": extracted, "primaryBytecode": disassembly,
        "ownSource": [file_evidence(path) for path in source_files],
        "callPath": [
            "ServerPlayNetworkHandler.onPlayerInteractEntity: forceMainThread, resolve packet Entity, worldBorder(origin), visual/query AABB distance to eye",
            "PlayerInteractEntityC2SPacket.handle -> ServerPlayNetworkHandler$1.attack",
            "reach-entity-attributes PlayerEntityInteractionHandlerMixin HEAD ensureWithinAttackRange",
            "ReachEntityAttributes.isWithinAttackRange: player.squaredDistanceTo(entity) <= (sqrt(64)+ATTACK_RANGE)^2",
            "On this saved actor/default attribute: 119.841978... > 64, so CallbackInfo.cancel before vanilla player.attack",
            "RpObjectEntity.damage's exact 6m source-part ray, native occlusion, Creative/no-tool and edit-permission checks are downstream"
        ],
        "minimalProposal": {
            "status": "PROPOSED_NOT_IMPLEMENTED_NOT_TESTED",
            "target": "Optional @Pseudo mixin into ReachEntityAttributes.isWithinAttackRange only for server Creative RpObjectEntity editing",
            "conditions": ["same world/live exact target", "BuildingTool absent from both hands", "unchanged RpObjectSelection.playerTarget(player,6) returns this exact UUID", "allowModifyWorld and world.canPlayerModifyAt at actual hit", "eye-to-that-hit squared distance <= installed getSquaredAttackRange(LivingEntity,64), preserving effective modifiers", "return ordinary library behavior for every other entity/player action"],
            "preserve": ["normal vanilla attack dispatch and Fabric/mod callbacks", "existing RP damage guard", "reach 6m and native occlusion", "entity origin/UUID/source geometry/physical collision", "no global reach or attribute adjustment", "no base-part fallback"],
            "requiredProof": ["native valid far-origin part plus wrong UUID/out-of-reach/occlusion/permission/tool/Survival negative controls", "fresh full provided mod profile actual upper main_gate attack through normal packet", "fresh minimal and full actual all canonical case gates"]
        },
        "limitations": ["No instrumented capture of this precise callback execution in the stopped process; identification is installed bytecode + matching default saved attribute/arithmetic", "Player transient state at packet receive was not separately journaled", "This report does not promote the failed whole client or remaining catalogue/menu/diagnostic phases to PASS", "Manual visual acceptance and full original task remain pending"]
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": output["status"], "output": str(args.output), "sha256": sha(args.output.read_bytes()), "feetToOriginSquared": feet_distance, "threshold": 64.0}, indent=2))


if __name__ == "__main__":
    main()
