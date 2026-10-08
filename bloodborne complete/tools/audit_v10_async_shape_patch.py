"""Small source-only manifest; no Minecraft/build or artifact hashing."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATHS = [
    "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeShapeSnapshots.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeRuntime.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeLedger.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeBlockEntity.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/CompositeArchitecture.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/composite/mixin/CompositeShapeMixin.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/mount/VerticalMount.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/ladder_source/SourceLadderBlockEntity.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/ladder_source/SourceBackingBlock.java",
    "src/architecture/java/dev/dreamwalker/bloodbornedw/architecture/wall/PrototypeWallBlock.java",
    "src/gametest/java/dev/dreamwalker/bloodbornedw/gametest/CompositeAsyncShapeGameTests.java",
    "src/gametest/resources/fabric.mod.json",
]


def main():
    baseline = json.loads((ROOT / "build/frozen-artifacts/v10-attempt-7/manifest.json").read_text(encoding="utf-8"))
    previous = {row["path"]: row for row in baseline["mainSourceInputs"]}
    rows = []
    for name in PATHS:
        data = (ROOT / name).read_bytes()
        rows.append({"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                     "main7SourceSha256": previous.get(name, {}).get("sha256"),
                     "baselineScope": "native test/metadata not in production-source manifest" if name.startswith("src/gametest/") else "new file" if name not in previous else "actual main7 frozen source manifest"})
    report = {
        "schema": "dw-v10-async-shape-patch-prebuild-v1",
        "status": "SOURCE_FROZEN_PEER_READ_ONLY_REVIEW_COMPLETE_BUILD_AND_RUNTIME_NOT_RUN",
        "previousArtifactSha256": baseline["sha256"],
        "failureEvidence": "reports/V10_FULL_CLIENT3_LOADING_SHAPE_DEADLOCK_AUDIT.json",
        "historicalFailureWrapper": "reports/V10_CLIENT_FULL_RELEASE3_ATTEMPT_1.json",
        "files": rows,
        "nativeTests": ["workerLightingQueriesMatchRootHelperForeignAndMountedNativeWithoutChunkLoads",
                        "savedDeferredOwnersKnownUuidReplacementsAndRollbackPublishExactShapeRevisions"],
        "expectedAdditionalNativeTests": 2,
        "currentBuild": "NOT_RUN; parent exclusively builds candidate8",
        "currentRuntime": "NOT_RUN; main7 successes are historical and not evidence of candidate8",
        "publication": {
            "world": "weak keys; no World/BE reference in published or staged values; explicit UNLOAD retirement",
            "read": "immutable volatile revision; known carrier passed by mixin; no forced state/BE/chunk/persistent access",
            "write": "per-world synchronized nested batch depth; worker attachments cannot flush a main partial write",
            "commitRollback": "one revision in execute finally after final committed or restored cells; only touched chunk maps copied",
            "load": "saved ledger before spawn lighting via ServerWorld LOAD; BE read/set/setWorld/setCachedState publication",
            "retire": "token-aware markRemoved retires actual BE metadata; saved owner contributions retained for deferred unload",
            "metrics": ["shapeSnapshotChunks", "shapeSnapshotCells", "shapeSnapshotPendingCells", "shapeSnapshotBatchDepth"],
        },
        "limits": [
            "Observed and guarded async scope is ServerWorld off its server thread; non-World prefetched BlockViews retain local BE access.",
            "Known published different resident UUID rejects old contributions; absent/unloaded root metadata is deferred until authoritative main validation/cleanup.",
            "External vanilla replacement without published replacement metadata can remain deferred in worker snapshot until main cleanup; no worker mutation is scheduled.",
            "Unshifted native wall worker collision returns native raw guard without neighbor chunk reads; global collider union preserved, per-cell seam attribution belongs to main thread.",
            "Runtime full-client saved-scene proof is still required; removing mods was not used as a fix.",
        ],
        "officialApiReference": "https://maven.fabricmc.net/docs/yarn-1.20.1%2Bbuild.10/net/minecraft/server/world/ServerChunkManager.html",
        "apiEvidenceScope": "Nullable getWorldChunk API; off-thread/future behavior established by actual local bytecode and the preserved runtime thread dump, not inferred from Javadoc.",
    }
    output = ROOT / "reports/V10_ASYNC_SHAPE_SNAPSHOT_FIX_PREBUILD.json"
    if output.exists():
        raise SystemExit("Refusing to overwrite existing prebuild evidence")
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "report": str(output), "files": len(rows)}))


if __name__ == "__main__":
    main()
