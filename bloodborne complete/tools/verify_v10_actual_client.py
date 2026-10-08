"""Small independent read-only V10 client/diagnostic proof; no game/world/JAR work."""
from __future__ import annotations
import argparse
import collections
import hashlib
import json
import math
from pathlib import Path
import time
import zipfile

from verify_v10_partial_client import ROOT, read, sha, require
from verify_v10_middle_pick import check_middle_picks

def member(archive, name):
    return json.loads(archive.read(name))

def timing_stats(row):
    count, retained, dropped = row["measurements"], row["retainedSamples"], row["droppedSamples"]
    require(count == retained+dropped and count >= 0, "Actual timing population does not reconcile")
    if "rawRetainedSampleNs" in row:
        raw = row["rawRetainedSampleNs"]
        require(len(raw) == retained <= 512 and all(isinstance(x, int) and x >= 0 for x in raw), "Local raw sample bounds/types invalid")
        require(sum(raw) <= row["totalNs"], "Retained elapsed sum exceeds all sampled elapsed")
        if dropped == 0:
            require(sum(raw) == row["totalNs"], "Lossless retained elapsed sum differs")

def check_diagnostics(wrapper, result):
    d = result["diagnosticsActualClient"]
    require(d["status"] == "PASS_ACTUAL_ORDINARY_ITEM_SERVER_ACK_RENDER_ACK_CLEANUP_TELEMETRY_EXPORT_OFF_ON_OFF", "Actual diagnostic client review failed")
    session, instance = d["sessionId"], d["instanceId"]
    for flag in ("recordingInitiallyOff", "recordingFinallyOff", "matchedCameraUnchanged", "originalHandRestoredBeforeOnWindow", "originalJoinedGameModeRestored", "explicitIsolatedCreativeSetupBeforeAllComparisonWindows", "ordinaryCreativeBreakPacketSent", "temporaryLedgerRemoved", "activeNativeSaveResult"):
        require(d[flag] is True, "Missing actual diagnostic cleanup/observer state: "+flag)
    require(d["temporaryNativeCellsRestored"] > 0, "Actual native-state/BE cleanup count missing")
    windows = d["matchedPresentationWindows"]
    require([w["label"] for w in windows] == ["OFF_BEFORE", "ON_IDENTICAL_CLEANED_SCENE", "OFF_AFTER"], "Actual labelled presentation windows missing")
    require([w["diagnosticsEnabledAtEnd"] for w in windows] == [False, True, False], "Actual OFF/ON/OFF state mismatch")
    table = []
    for w in windows:
        raw = w["rawPresentationIntervalsNs"]
        require(len(raw) == w["retainedSamples"] and w["measurements"] == w["retainedSamples"]+w["droppedSamples"], "Actual frame population mismatch")
        require(sum(raw) <= w["totalNs"] and len(raw) > 0, "Actual frame intervals unavailable")
        if w["droppedSamples"] == 0:
            require(sum(raw) == w["totalNs"], "Actual untruncated frame sum mismatch")
        require(4e9 <= w["windowNs"] <= 6e9, "Actual100tick comparison window is not approximately5 seconds")
        table.append({"label": w["label"], "actualWindowSeconds": w["windowNs"]/1e9, "framesObserved": w["measurements"], "retained": w["retainedSamples"], "dropped": w["droppedSamples"],
                      "meanFrameMs": w["averageNs"]/1e6, "p95FrameMs": w["p95Ns"]/1e6, "p99FrameMs": w["p99Ns"]/1e6,
                      "timeWeightedPresentationFps": w["fpsAverage"], "renderThreadCpuMeanMs": w["renderThreadCpuIntervals"]["averageNs"]/1e6})
    accepted = d["actualServerPlacementAcknowledgement"]
    renderer = d["actualRendererAcknowledgement"]
    require(accepted["sessionId"] == renderer["session"] == session and accepted["instanceId"] == renderer["instanceId"] == instance, "Actual session/instance chain mismatch")
    require(renderer["serverPlacementAcknowledgement"] == accepted and accepted["action"] == "place" and accepted["result"] == "COMMITTED", "Original accepted operation does not match actual renderer chain")
    require(accepted["before"]["heldItem"] == accepted["after"]["registry"] == renderer["clientRegistry"] == "bloodborne_dw:prototype_glass_window_02", "Actual held-item/server/client registry chain differs")
    require(accepted["before"]["heldTypeId"] == accepted["typeId"] == "90010" and renderer["purpose"] == "world-root-render", "Actual type/world render observation differs")
    client_path, server_path = Path(d["clientExport"]), Path(d["serverExport"])
    require(client_path.stat().st_size == d["clientExportBytes"] and server_path.stat().st_size == d["serverExportBytes"], "Actual local exported byte counts differ")
    with zipfile.ZipFile(client_path) as cz, zipfile.ZipFile(server_path) as sz:
        summary, runtime, local = member(cz,"summary.json"), member(cz,"runtime.json"), member(cz,"local-timings.json")
        partial = member(cz,"partial-window.json")
        acks, batches = member(cz,"placement-render-acknowledgements.json"), member(cz,"client-batches.json")
        server_session, delivered = member(sz,"session.json"), member(sz,"client-batches.json")
        require(runtime["productionArtifactSha256"] == wrapper["artifact_sha256"] == server_session["environment"]["productionArtifactSha256"], "Actual export current artifact binding differs")
        require(summary["session"] == local["session"] == server_session["sessionId"] == session, "Export sessions differ")
        require(renderer in acks, "Full actual ordinary placement/render ACK not retained locally")
        require(batches == d["actualClientBatches"] and len(batches) > 0, "Actual local wire history differs from QA observation")
        require(len(delivered) == len(batches) and all(row["batch"] in batches for row in delivered), "Actual delivered server telemetry differs from exact local sent batches")
        require(local["windowLimit"] == 64 and local["sectionLimit"] == 128 and local["sampleLimitPerSection"] == 512, "Bounded local timing contract changed")
        require(local["windowsRetained"] == len(local["windows"]) <= 64 and len(local["sessionSampledTimings"]) <= 128, "Local window/section bounds exceeded")
        require(local["windowsDropped"] == 0 and local["sectionObservationsDropped"] == 0 and local["windowSectionObservationsDroppedCumulative"] == 0, "This small-session reconciliation requires all sampled windows/sections retained")
        window_counts = collections.Counter()
        for window in local["windows"]:
            require(window["session"] == session and len(window["timings"]) <= 128, "Local timing window identity/bounds differ")
            for row in window["timings"]:
                timing_stats(row); window_counts[row["typeChunkSection"]] += row["measurements"]
        session_counts = {}
        for row in local["sessionSampledTimings"]:
            timing_stats(row); session_counts[row["typeChunkSection"]] = row["measurements"]
        partial_counts = collections.Counter()
        for row in partial["timings"]:
            timing_stats(row); partial_counts[row["typeChunkSection"]] += row["measurements"]
        require(dict(window_counts+partial_counts) == session_counts, "Each local invocation must occur once: completed windows plus one untransmitted partial tail, not session totals added twice")
        require(sum(session_counts.values()) > 0, "Actual sampled timing groups are empty")
        for batch in batches:
            require(batch["session"] == session and len(batch) <= 32 and len(json.dumps(batch,ensure_ascii=False,separators=(",",":"))) <= 16384, "Actual batch identity/receiver field/character bounds differ")
            require(batch["runtimeMetadata"]["productionArtifactSha256"] == wrapper["artifact_sha256"], "Compact wire environment current artifact differs")
            require(batch["wireBudget"]["wholeJsonSerializations"] <= 2 and len(batch["timings"]) > 0, "Actual post-budget telemetry loses timing rows or repeats whole serialization")
        require(all(row["utf8PayloadBytes"] <= 60000 for row in delivered), "Actual UTF8 channel payload exceeds receiver bound")
        require(server_session["secondsRequested"] == 20 and server_session["stopReason"] == "AUTO_DURATION_EXPIRED" and not server_session["enabled"], "Actual20second recording failed to stop")
        require(d["serverStatusAfterAutomaticExpiry"]["stopReason"] == "AUTO_DURATION_EXPIRED", "QA actual expiry readback differs")
        client_errors, server_errors = member(cz,"errors.json"), member(sz,"errors.json")
        return {"status": "PASS_CURRENT_ACTUAL_ACK_LOCAL_RETENTION_OFF_ON_OFF_EXPORTS", "sessionId": session, "instanceId": instance,
                "acceptedOperation": accepted["operation"], "actualItemServerRendererType": "90010", "requestedRecordingSeconds": server_session["secondsRequested"],
                "actualRecordingSeconds": server_session["elapsedNs"]/1e9, "comparisonWindowTicksRequested": 100, "comparisonWindows": table,
                "deliveredBatches": len(delivered), "allBatchesHaveSampledTimingGroups": True, "localWindows": len(local["windows"]), "sessionSampledSections": len(session_counts),
                "sampledInvocations": sum(session_counts.values()), "completedWindowSampledInvocations": sum(window_counts.values()), "untransmittedPartialTailSampledInvocations": sum(partial_counts.values()),
                "rawRetainedSamples": sum(x["retainedSamples"] for x in local["sessionSampledTimings"]), "rawDroppedSamples": sum(x["droppedSamples"] for x in local["sessionSampledTimings"]),
                "wireOmittedRowsCumulative": summary["wireBudgetRowsOmittedCumulative"], "ownFlushInclusiveWallMilliseconds": summary["flushOverheadNs"]/1e6,
                "nativeCellsRestored": d["temporaryNativeCellsRestored"], "temporaryLedgerRemoved": True,
                "clientErrors": client_errors, "serverErrors": server_errors,
                "exports": [{"path": str(p), "bytes": p.stat().st_size, "sha256": sha(p)} for p in (client_path, server_path)],
                "limits": ["Actual20second recording, three approximately5second windows. No20seconds-per-window claim.", "Sampled hook elapsed intervals may nest; cannot sum them as unique CPU costs.", "Render-thread CPU has host clock resolution; presentation includes wait. GPU duration NOT_MEASURED; no causal overhead percentage.", "Formal ordinary ACK chain is90010; no full all69RP diagnostics-render-chain coverage claim."]}

def main():
    started = time.perf_counter()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--wrapper", required=True, type=Path)
    parser.add_argument("--catalogue-audit", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--reenter-wrapper", type=Path)
    parser.add_argument("--require-middle-pick", action="store_true", help="Require the actual 69 canonical keys, AUTHOR seven old-registry keys/re-placement, and inventory restoration")
    args = parser.parse_args()
    require(not args.output.exists(), "Refuse to overwrite prior actual proof")
    wrapper = read(args.wrapper)
    require(wrapper["status"] == "PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT" and wrapper["exit_code"] == 0, "Whole ordinary client normal success required")
    raw_path = Path(wrapper["client_review_output"]["path"])
    result = read(raw_path)
    require(result == wrapper["client_review_output"]["result"] and sha(raw_path) == wrapper["client_review_output"]["sha256"], "Actual final raw binding differs")
    require(result["status"] == "PASS_ACTUAL_CLIENT_V10_MENUS_ORDINARY_RP_PARTS_HEIGHT_NATIVE_OVERLAP_AND_SAVE" and result["actualIntegratedSave"] is True, "Whole actual input/save review failed")
    audit = read(args.catalogue_audit)
    require(wrapper["artifact_sha256"] == result["productionJarSha256"] == audit["productionJarSha256"], "Current artifact binding differs")
    canonical = {x["asset"] for x in audit["rows"] if x["offeredCanonical"]}
    aliases = set(audit["existingAliasImplementation"]["mapping"])
    middle = check_middle_picks(result, audit, "AUTHOR") if args.require_middle_pick else {"status": "NOT_REQUIRED_HISTORICAL_CLIENT_SCHEMA"}
    primary = [x for x in result["ordinaryRpCases"] if x["part"] == "authored-first-part"]
    require(len(primary) == 69 and {x["asset"] for x in primary} == canonical and not ({x["asset"] for x in primary}&aliases), "Actual canonical69/hidden7 runtime construction roster differs")
    require(len({x["uuid"] for x in primary}) == 69, "Real construction cases must use69 distinct UUIDs")
    for row in primary:
        require(row["status"] == "PASS_ACTUAL_ORDINARY_PLACE_AND_CREATIVE_PART_ATTACK" and row["serverRegistry"] == "bloodborne_rp:"+row["asset"], "Canonical ordinary type/action mismatch")
        require(all(row[k] is True for k in ("nativeBlockItemPlaced","nativePlacementSameServerClientUuid","nativeBlockSurvivesRpAttack","nativeBlockOrdinaryCleanup")), "Real native placement/UUID/delete/cleanup proof missing")
        require(row["nativeStableRpBefore"] and row["nativeStableRpBefore"] == row["nativeStableRpAfter"], "Actual stable typed RP changed under BlockItem")
    entries = read(ROOT/"src/architecture/resources/bloodborne_dw/debug_catalogue.json")["entries"]
    architecture = result["ordinaryArchitectureCases"]
    require(len(architecture) == 18 and {x["registry"] for x in architecture} == {x["registryId"] for x in entries if x["kind"] == "architecture"}, "Exact actual architecture18 roster differs")
    for row in architecture:
        require(row["status"].startswith("PASS_ACTUAL_ORDINARY_ARCHITECTURE") and row["serverThreadLedgerCleanup"]["remainingOwnerContributions"] == 0, "Actual architecture lifecycle/ledger cleanup failed")
    extras = [x for x in result["ordinaryRpCases"] if x["part"] != "authored-first-part"]
    require(len(extras) == 4 and {(x["asset"],x["part"]) for x in extras} == {("door_1","opened-moving-part"),("ladder","collapsed-moving-lower"),("ladder","upper-platform"),("ladder","remote-working-lower")}, "Four exact actual source-purpose extras missing")
    for row in extras:
        require(row["status"].startswith("PASS_ACTUAL_ORDINARY_PLACE") and not row["serverThreadRemovalObservation"]["present"], "Actual source-purpose Creative removal failed")
        camera = row["actualSourcePartCameraCandidate"]
        require(camera["sourceHitUuid"] == row["uuid"] and camera["requestedPurpose"] == row["part"] and all(camera[k] is True for k in ("hitInsideRequestedPurposeWithNativeTargetMargin","nativeActorCollisionFree","sourcePhysicalActorCollisionFree")), "Actual intended source-part selection/body clearance differs")
    door = next(x for x in extras if x["asset"] == "door_1")
    require(not door["closedSourceAnimatedLeafSelection"]["actualServerTrackedClientOpen"] and door["openedSourceAnimatedLeafSelection"]["actualServerTrackedClientOpen"], "Actual ordinary opened leaf state not observed")
    require(door["openedSourceAnimatedLeafSelection"]["sourceAnimatedLeafCubeCount"] == 15, "Actual selected leaf source correspondence changed")
    walk = next(x["actualPlatformWalk"] for x in extras if x["part"] == "upper-platform")
    require(walk["status"] == "PASS_ACTUAL_SURVIVAL_PLATFORM_WALK" and walk["serverMovedZ"] > .8 and abs(walk["serverFeetY"]-walk["platformTopY"]) < .06 and abs(walk["clientFeetY"]-walk["platformTopY"]) < .06, "Actual ordinary Survival platform/gravity proof failed")
    require(result["temporaryConstructionFixtureImmutable"] and result["preConstructionFixtureState"] == result["postConstructionFixtureState"], "Known pre-existing fixture semantic state differs")
    require(result["firstEntrySnapshot"]["capturedBeforeAnyPrepare"] and result["originalActorInventoryCameraRestoredBeforeMenus"], "First-entry or actor restoration proof missing")
    menus = result["menusActual"]
    require(menus["status"] == "PASS_ACTUAL_CLIENT_MENUS_PACKETS_RULES_COMMANDS" and menus["completedSteps"] == 237, "Actual GUI237 stage failed")
    diagnostics = check_diagnostics(wrapper,result)
    persisted = {"status": "PENDING_SUCCESSFUL_REENTER_WRAPPER"}
    if args.reenter_wrapper:
        rw = read(args.reenter_wrapper); rr = rw["client_review_output"]["result"]
        require(rw["status"] == "PASS_CLIENT_WORLD_LOAD_SAVE_NORMAL_EXIT" and rw["exit_code"] == 0 and rw["artifact_sha256"] == wrapper["artifact_sha256"], "Actual current reentry failed")
        rm = rr["menusActual"]
        if args.require_middle_pick:
            middle["reenter"] = check_middle_picks(rr, audit, "REENTER")
        require(menus["persistedStateAfter"] == rm["persistedStateBefore"] == rm["persistedStateAfter"], "Actual AUTHOR-after and REENTER-before/after persisted state differ")
        persisted = {"status": "PASS_ACTUAL_AUTHOR_AFTER_EQUALS_REENTER_BEFORE_AFTER", "wrapper": str(args.reenter_wrapper), "sha256": sha(args.reenter_wrapper), "scope": "structured exact fixture UUID/rule/policy/command/lamp graph readbacks; no whole-world byte identity"}
    creative_path = ROOT/"src/architecture/java/dev/dreamwalker/bloodbornedw/debug/UnifiedCreativeCatalogue.java"
    output = {"schema": "dw-v10-independent-actual-client-roster-parts-diagnostics-v1", "status": "PASS_CURRENT_ACTUAL_CLIENT_ROSTER_PARTS_DIAGNOSTICS_AND_NORMAL_EXIT_PENDING_USER_REVIEW", "wholeOriginalTask": "NOT_COMPLETE_PENDING_USER_REVIEW_AND_REMAINING_SCOPE",
              "productionJarSha256": wrapper["artifact_sha256"], "wholeClientStatus": result["status"], "wrapperStatus": wrapper["status"], "exitCode": 0,
              "actualCanonicalRoster": {"rp": sorted(canonical), "rpCount": 69, "hiddenAliasesAbsent": sorted(aliases), "architecture": sorted(x["registry"] for x in architecture), "architectureCount": 18,
                                        "nativeRelationCounts": dict(collections.Counter(x["nativeBlockRelation"] for x in primary))},
              "creativeScope": {"canonicalConstructionItemsActual": "PASS69_REAL_ORDINARY_CLIENT_ITEMS", "hiddenRetiredAliasPredicate": "SOURCE_VERIFIED_CANONICAL_PROVIDER_EXCLUDES7_OLD_PLACERS", "actualOpenedCreativeTabEntries": "NOT_OBSERVED_BY_THIS_QA", "providerSource": str(creative_path.relative_to(ROOT)), "providerSourceSha256": sha(creative_path)},
              "actualExtraParts": [{"asset": x["asset"],"part": x["part"],"uuid": x["uuid"],"camera": x["actualSourcePartCameraCandidate"]} for x in extras],
              "actualSurvivalPlatformWalk": walk, "menusActualSteps": 237, "knownFixtureTypedStateEqual": True,
              "diagnostics": diagnostics, "persistedGuiIdentity": persisted,
              "actualMiddleKey": middle, "middlePickRequired": args.require_middle_pick,
              "inputs": [{"path": str(p.resolve()),"sha256": sha(p)} for p in (args.wrapper,raw_path,args.catalogue_audit)],
              "elapsedVerifierMilliseconds": (time.perf_counter()-started)*1000,
              "limits": ["Closed/open door selection uses declared authored leaf and bounded conservative motion envelopes, not an exact per-frame pixel mesh.", "Ordinary native-placement63 selection-intersection +6 adjacent-root cases do not claim all69 visible-mesh-interior placements.", "Runtime catalogue/packet coverage does not imply human visual acceptance or full-city conversion readiness.", "Saved hidden alias disk/read/drop/debug proof belongs to its separate actual pipeline; this audit does not invent it."]}
    if args.require_middle_pick:
        output["status"] = "PASS_CURRENT_ACTUAL_CLIENT_ROSTER_PARTS_MIDDLE_KEY_DIAGNOSTICS_AND_NORMAL_EXIT_PENDING_USER_REVIEW"
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(output,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"status":output["status"],"elapsedMilliseconds":output["elapsedVerifierMilliseconds"],"path":str(args.output)},ensure_ascii=True))

if __name__ == "__main__":
    main()
