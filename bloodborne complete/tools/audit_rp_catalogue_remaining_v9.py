"""Read-only evidence snapshot of unresolved RP source correspondence and review scope."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
NON_ENTITIES = {"sawcleaver_false", "sawcleaver_true", "sawspear_false", "sawspear_true",
                "boomhammer_false", "boomhammer_true", "bullet", "blood_puddle"}


def ref(path):
    path = Path(path).resolve()
    data = path.read_bytes()
    return {"path": str(path), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def read(path):
    return json.loads(Path(path).read_text(encoding="utf8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native-outcomes", type=Path, default=ROOT/"reports/RP_V9_RUNTIME_OUTCOMES.json")
    parser.add_argument("--ordinary-author", type=Path, default=ROOT/"reports/FIRST_SET_MINIMAL_AUTHOR_V9_RELEASE4.json")
    parser.add_argument("--ordinary-reopen", type=Path, default=ROOT/"reports/FIRST_SET_MINIMAL_GAMEPLAY_REOPEN_V9_RELEASE4.json")
    parser.add_argument("--production-reopen", type=Path, default=ROOT/"reports/FIRST_SET_MINIMAL_PRODUCTION_REOPEN_V9_RELEASE4.json")
    parser.add_argument("--compatibility-diagnosis", type=Path, default=ROOT/"reports/RP_LITHIUM_COLLISION_V9_DIAGNOSIS.json")
    parser.add_argument("--output", type=Path, default=ROOT/"reports/RP_CATALOGUE_REMAINING_V9.json")
    args = parser.parse_args()
    native = read(args.native_outcomes)
    artifact = Path(native["productionArtifact"]["path"])
    assert ref(artifact)["sha256"] == native["productionArtifact"]["sha256"], "Frozen native report references another JAR"
    author = read(args.ordinary_author)
    observed = author["ordinary_gameplay_output"]["result"]
    assert observed["status"].startswith("PASS_") and all(row["pass"] for row in observed["checks"])
    actual_types = sorted({row["asset"] for row in observed["ordinaryItemPlacements"]})
    assert observed["newLampLighting"] == "UNCHANGED_NO_AUTOMATIC_SOURCE_TECHNICAL_LIGHT"
    audit = read(ROOT/"reports/RP_V9_OBJECT_AUDIT.json")
    catalogue = read(ROOT/"src/rp/resources/assets/bloodborne_rp/catalog.json")
    entity_types = set(catalogue) - NON_ENTITIES
    aliases = {row["legacyAssetId"] for row in audit["canonicalAliasMap"]}
    assert len(entity_types) == 94 and len(entity_types-aliases) == 87 and len(aliases) == 7
    assert catalogue["trapdoor"]["clips"] == {}, "Conditional trapdoor exception must be re-reviewed if authored clips change"
    original_jar = Path("C:/Users/vakir/Downloads/Bloodborne_X_Minecraft_mod_6.0.jar")
    original_entry = "com/potomy/bloodborne/common/entity/decorations/HunterLampEntity.class"
    with ZipFile(original_jar) as source:
        bytecode_hash = hashlib.sha256(source.read(original_entry)).hexdigest()
    lamp_bytecode = ROOT/"reports/SOURCE_HUNTERLAMP_ENTITY_V9_BYTECODE.txt"
    bytecode_text = lamp_bytecode.read_bytes().decode("utf8", errors="replace")
    assert all(key in bytecode_text for key in ("HunterLampEntity", "spawnLightSource", "destroyLightSource", "HUNTER_LAMP_LIGHT_SOURCE", "IsActivated"))
    current_author = author["artifact_sha256"] == native["productionArtifact"]["sha256"]
    reopening, production_reopening = read(args.ordinary_reopen), read(args.production_reopen)
    minimal_lifecycle = current_author and all(
        row["artifact_sha256"] == native["productionArtifact"]["sha256"] and row["status"].startswith("PASS")
        for row in (author, reopening, production_reopening))
    compatibility = read(args.compatibility_diagnosis)
    report = {
        "schema": "dreamwalker-rp-catalogue-remaining-v9-v1",
        "status": "INCOMPLETE_CATALOGUE_REVIEW_WITH_EXPLICIT_CONFIRMED_GAP_AND_NOT_RUN_SCOPES",
        "productionArtifact": native["productionArtifact"],
        "fullRpCatalogueComplete": False,
        "fullCityConversionComplete": False,
        "manualVisualAcceptance": "PENDING_USER_REVIEW",
        "evidence": {
            "nativeRequestedRpChecks": ref(args.native_outcomes),
            "exactResourcesAndSevenAliases": ref(ROOT/"reports/RP_V9_OBJECT_AUDIT.json"),
            "originalWoodGateMeasuredAndNativeLocalFix": ref(ROOT/"reports/SOURCE_WOODGATE_V9_COMPARISON.json"),
            "observedOrdinaryAuthorWrapper": ref(args.ordinary_author),
            "observedOrdinaryAuthorRawOutput": ref(author["ordinary_gameplay_output"]["path"]),
            "observedOrdinaryGameplayReopen": ref(args.ordinary_reopen),
            "observedProductionReopen": ref(args.production_reopen),
            "fullProfileCollisionFailureDiagnosis": ref(args.compatibility_diagnosis),
            "preparedSourceOnly": ref(ROOT/"reports/SOURCE_REVIEW_PRELOAD_INDEPENDENT_V9_FINAL1.json"),
            "originalLampTechnicalBlock": ref(ROOT/"reports/SOURCE_LAMP_BLOCK_AUDIT.json"),
            "originalHunterLampBytecode": ref(lamp_bytecode),
            "currentLampService": ref(ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/lamp/LampService.java"),
            "currentRpObjectEntity": ref(ROOT/"src/rp/java/dev/dreamwalker/bloodbornerp/object/RpObjectEntity.java"),
            "currentClientModelProbe": ref(ROOT/"src/review/java/dev/dreamwalker/bloodbornedw/review/RpV9ClientReviewProbe.java")
        },
        "originalHunterLamp": {"jar": ref(original_jar), "classEntry": original_entry,
                               "classSha256": bytecode_hash,
                               "inspection": "javap -p -c: activate/IsActivated, spawnLightSource and destroyLightSource; source light block emission9 independently audited",
                               "originalRuntimeActivationMeasurement": "NOT_RUN_IN_THIS_READ_ONLY_AUDIT"},
        "covered": {
            "rpNativeChecksPassed": native["rpTestCount"], "rpNativeFailures": native["rpFailures"],
            "wholeNativeCount": native["wholeNativeTestCount"], "wholeNativeFailures": native["wholeNativeFailures"],
            "all102CataloguedAssetByteSignatures": "PASS_STATIC_ONLY",
            "707OriginalRpResourceBytes": "PASS",
            "sevenExactAliases": "PASS_STATIC_AND_NATIVE_REGISTRY_UUID_NAME_PAYLOAD_LINK_COMPATIBILITY",
            "originalWoodGatePhysics": "8_ACTUAL_FORGE_MOVES_WITH_NATIVE_CONTROL",
            "observedOrdinaryAuthor": {"artifactSha256": author["artifact_sha256"],
                "sameAsCurrentArtifact": current_author, "status": observed["status"],
                "passedChecks": len(observed["checks"]), "ordinaryItemPlacements": len(observed["ordinaryItemPlacements"]),
                "distinctEntityTypes": actual_types,
                "scope": "Production item/callback/server entity/lever/travel endpoint. Another artifact's result never proves current restart or client rendering."},
            "currentArtifactMinimalLifecycle": "PASS_BOUNDED_AUTHOR_GAMEPLAY_RESTART_AND_PRODUCTION_REOPEN" if minimal_lifecycle else "NOT_RUN_FOR_CURRENT_ARTIFACT"
        },
        "confirmedRemaining": [{
            "key": "hunterlamp_original_activation_light",
            "temporaryId": 91063, "registryId": "bloodborne_rp:hunterlamp",
            "status": "CONFIRMED_SOURCE_BEHAVIOUR_NOT_IMPLEMENTED_IN_NEW_PLACEMENT",
            "source": "Original HunterLampEntity has activated-state light creation/removal using its technical block with emission9.",
            "current": "Current entity interaction opens directed travel; new placement/activation has no technical-light write. The actual ordinary stand explicitly records UNCHANGED_NO_AUTOMATIC_SOURCE_TECHNICAL_LIGHT.",
            "scopeLimit": "Original activation sequence not newly measured; code and ordinary stand prove the current absence. Source two-cell preservation and travel are separate PASS domains.",
            "requiredNextProof": "Source-grounded activation/light lifecycle without replacing foreign blocks, with save/restart/removal and correct lighting. No new respawn system is authorized."
        }, {
            "key": "optimized_full_profile_rp_physics", "temporaryId": 91077,
            "registryId": "bloodborne_rp:npc_window",
            "status": "CONFIRMED_FAIL_ON_MEASURED_ARTIFACT_CORRECTED_RUNTIME_NOT_RUN",
            "measuredArtifactSha256": compatibility["failedProductionCopy"]["sha256"],
            "source": "Full83 release4 actual ordinary movement crossed the requested entire6m through npc_window.",
            "cause": "Lithium0.11.2 deferred engine entity-collision list bypasses the original default EntityView query hook; its explicit shared shape list still participates in clipping.",
            "proposal": "Narrow engine shape-list hook and exact-box dedup helper compiled; native/full-profile proofs of the corrected artifact are pending. No collider geometry or native step algorithm replacement.",
            "requiredNextProof": "Corrected full83 ordinary NPC/gate/stairs/ladder movement plus source/client regression, preserving the failed release4 evidence."
        }],
        "explicitExceptions": [{
            "key": "static_trapdoor", "temporaryId": 91088, "registryId": "bloodborne_rp:trapdoor",
            "status": "EXCLUDED_CONDITIONAL_ACTIVE_MECHANISM_STATIC_DECORATION",
            "sourceClips": {}, "current": "No invented animation, Open action or lever target; allowed exception in user point6.",
            "next": "Only add an active mechanism if an actual authored/source mechanism is found and proved."
        }],
        "notRunOrUnresolved": [
            {"key": "full_catalogue_item_to_render_lifecycle", "status": "NOT_RUN_FULL_CATALOGUE",
             "registeredEntityTypesIncludingLegacyAliases": 94, "canonicalEntityTypes": 87,
             "canonicalTypesOutsideObservedOrdinaryStand": sorted(entity_types-aliases-set(actual_types)),
             "remainingCount": len(entity_types-aliases-set(actual_types)),
             "required": "Creative item→ordinary placement→actual client model, yaw, both faces/alpha, middle pick/drop/reinstall, save/restart for each independent type.",
             "acceptedRpTree1": "User-accepted art remains unchanged; no renewed visual acceptance is demanded by this inventory."},
            {"key": "corrected_full_profile_complete_ordinary_restart", "status": "NOT_RUN_REPAIR_PENDING",
             "observedAuthorIsCurrentArtifact": current_author,
             "currentMinimalLifecycle": "PASS_BOUNDED_MINIMAL_ONLY" if minimal_lifecycle else "NOT_RUN_CURRENT_ARTIFACT",
             "required": "Corrected artifact's completed full83 author+production reopen+gameplay reopen wrappers; minimal PASS does not override actual full-profile movement FAIL."},
            {"key": "actual_client_rp_model_probe", "status": "NOT_RUN_AT_SNAPSHOT",
             "preparedProbe": "RpV9ClientReviewProbe.verify(client)",
             "required": "Eight actually loaded requested type models, original resource bytes, shared dog hidden/visible/hidden baked state, culling bounds and neutral loaded gate/ladder poses.",
             "manual": "Pending user visual acceptance even after automatic client PASS."},
            {"key": "active_animation_client_observation", "status": "NOT_RUN_TARGETED_TRANSITION_SEQUENCE",
             "required": "Actual client gate idle→single1.6s pulse→idle/held/offhand/rejoin phases and moving RP ladder bottom; native numeric keys and an idle loaded-model sample are narrower proofs."},
            {"key": "source_physics_correspondence_for_other_rp_types", "status": "NOT_RUN_FULL_ORIGINAL_FORGE_BASELINE",
             "measuredException": "wood_gate: eight actual Forge moves",
             "required": "Original collision/passability and source pose comparison for remaining types. Exact PNG/model/animation bytes do not prove original physical behaviour."},
            {"key": "source_v9_loaded_saved_entity_preservation", "status": "NOT_RUN_CURRENT_SAVE_BOUNDARY",
             "preparedProof": "FINAL1 pre-load PASS:15 RP entities,2 explicitly mapped light cells,49 byte-exact BEs; original typed fields checked separately.",
             "required": "Current source-author/reopen/replay plus independent typed Original/unhandled fields/UUID/registry/links and nonmember audits; full-city migration remains outside this bounded fixture."}
        ],
        "invariants": {"mainOrQaEditedByThisAudit": False, "minecraftOrGradleLaunched": False,
                       "originalInputsWritten": False, "acceptedRpTree1ArtChanged": False},
        "limits": ["This is an evidence snapshot, not a claim that every untested object is defective.",
                   "Travel, new lamp illumination and source lamp migration have separate verdicts.",
                   "Native suite and successful scene loading do not constitute full-catalogue or human acceptance."]
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf8")
    print(json.dumps({"status": report["status"], "confirmedRemaining": len(report["confirmedRemaining"]),
                      "notRunScopes": len(report["notRunOrUnresolved"]), "output": str(args.output)}))


if __name__ == "__main__":
    main()
