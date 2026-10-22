"""Write fresh candidate8 runtime argv; never launch Minecraft or a build.

Without --artifact-sha this is an explicit UNBOUND plan. Bind only after actual
root production/QA freezes; no old runtime PASS is copied into this document.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PYTHON = "C:/Users/vakir/miniconda3/python.exe"
EULA = "C:/Users/vakir/.codex/worktrees/bloodborne-material-review/DW/bloodborne complete/build/production-smoke/final-city/eula.txt"
JAR = "build/frozen-artifacts/v10-attempt-8/dreamwalker-bb-fabric-1.20.1-0.1.0-prototype.5.jar"
QA = "build/frozen-artifacts/v10-qa-22/dreamwalker-first-set-review-qa-0.1.0-prototype.5.jar"
TOKEN = "__FROZEN_MAIN8_SHA256__"


def command(tool, *args):
    return [PYTHON, "tools/" + tool, *map(str, args)]


def plan(jar: str, qa: str, sha: str, qa_sha: str | None) -> dict:
    author = "reports/V10_SERVER_AUTHOR_MIN_ATTEMPT_7.json"
    reopen = "reports/V10_SERVER_PRODUCTION_ONLY_REOPEN_RELEASE5.json"
    full = "reports/V10_SERVER_FULL_OWNED_SCENE_REOPEN_RELEASE4.json"
    author_output = "build/runtime-server-v10-author-min-attempt7/review-v10-scene-output.json"
    pristine = "build/runtime-server-v10-production-only-reopen-release5/isolated-smoke-world"
    baseline = "build/runtime-server-v10-author-min-attempt7/isolated-smoke-world"
    min_report = "reports/V10_CLIENT_MIN_ATTEMPT_15.json"
    reenter_report = "reports/V10_CLIENT_REENTER_RELEASE4_ATTEMPT_1.json"
    full_report = "reports/V10_CLIENT_FULL_RELEASE4_ATTEMPT_1.json"
    min_world = "build/runtime-client-v10-client-min-attempt15/saves/prototype-fixture"
    author_marker = "tools/review_v10_client_author_release4_input.json"
    reenter_marker = "tools/review_v10_client_reenter_release4_input.json"
    full_marker = "tools/review_v10_client_full_release4_input.json"
    server_base = command("run_review_v10_candidate_servers.py", "--jar", jar, "--qa-jar", qa,
                          "--artifact-sha", sha, "--scene-template", "tools/review_v10_scene_release3_input.json",
                          "--scene-input", "tools/review_v10_scene_release4_input.json", "--accepted-eula-file", EULA,
                          "--author-run-name", "v10-author-min-attempt7", "--author-report", author,
                          "--settings-report", "reports/V10_SCENE_BEFORE_FIRST_ENTRY_SETTINGS_7.json",
                          "--reopen-run-name", "v10-production-only-reopen-release5", "--reopen-report", reopen,
                          "--full-run-name", "v10-full-server-owned-scene-reopen-release4", "--full-report", full,
                          "--full-scene-copy", "--marker-dir", "tools/review-v10-diagnostics-markers-release4",
                          "--diagnostics-suffix", "release4", "--plan-output", "build/review-v10-candidate8-servers/plan.json")

    def client(profile, name, report, fixture, marker, heap):
        return command("run_final_client.py", "--jar", jar, "--profile", profile,
                       "--extra-mod", qa, "--fixture", fixture, "--quick-play", "--qa-input", marker,
                       "--launch", "--wait", "--timeout", "1200", "--heap-gb", heap,
                       "--run-name", name, "--report", report)

    alias = command("run_review_v10_alias_servers.py", "--jar", jar, "--qa-jar", qa,
                    "--artifact-sha", sha, "--accepted-eula-file", EULA, "--suffix", "release2")
    commands = {
        "prepare_server_plan_no_launch": server_base + ["--phase", "prepare"],
        "actual_author7_creativepatch_reopen5_full_owned_scene4_markers4": server_base + ["--phase", "author-validation"],
        "typed_minimal_reopen5_after_stop": command("verify_review_v10_pristine_reopen.py", "--author-report", author,
                                                  "--reopen-report", reopen, "--artifact-sha", sha,
                                                  "--output", "reports/V10_PRISTINE_BASELINE_REOPEN_INDEPENDENT_RELEASE5.json"),
        "typed_full_owned_scene4_after_stop": command("verify_review_v10_pristine_reopen.py", "--author-report", author,
                                                     "--reopen-report", full, "--artifact-sha", sha,
                                                     "--output", "reports/V10_FULL_OWNED_SCENE_REOPEN_INDEPENDENT_RELEASE4.json"),
        "prepare_min15_marker_after_actual_author": command("prepare_review_v10_client_marker.py", "--jar", jar,
                                                           "--author-output", author_output, "--mode", "AUTHOR", "--output", author_marker),
        "prepare_reenter4_marker_after_actual_author": command("prepare_review_v10_client_marker.py", "--jar", jar,
                                                              "--author-output", author_output, "--mode", "REENTER", "--output", reenter_marker),
        "prepare_full4_strict42_marker_after_actual_author": command("prepare_review_v10_client_marker.py", "--jar", jar,
                                                                   "--author-output", author_output, "--mode", "AUTHOR",
                                                                   "--shader-marker", "tools/review_v10_client_full_release3_input.json",
                                                                   "--output", full_marker),
        "actual_dedicated_off_on_reenter4_separate_root_go": server_base + ["--phase", "diagnostics"],
        "actual_min15": client("minimal", "v10-client-min-attempt15", min_report, baseline, author_marker, 4),
        "actual_reenter4_only_after_min15_normal_pass": client("minimal", "v10-client-reenter-release4-attempt1", reenter_report,
                                                              min_world, reenter_marker, 4),
        "actual_full4_only_after_previous_normal_pass": client("full_client", "v10-client-full-release4-attempt1", full_report,
                                                              baseline, full_marker, 6) + ["--shader-pack", "C:/Users/vakir/Downloads/Kappa_v5.2.zip",
                                                                                         "--shader-settings", "C:/Users/vakir/Downloads/Kappa_BB.zip (1).txt"],
        "independent_min15_reenter4_after_both_stop": command("verify_v10_actual_client.py", "--wrapper", min_report,
                                                            "--reenter-wrapper", reenter_report, "--catalogue-audit",
                                                            "reports/RP_TYPE_DUPLICATE_AUDIT_V10_CANDIDATE8_FINAL.json", "--output",
                                                            "reports/V10_CLIENT_MIN15_REENTER4_ROSTER_PARTS_DIAGNOSTICS_INDEPENDENT.json", "--require-middle-pick"),
        "independent_linked_diagnostics4_after_actual_server3_client2_stop": command("verify_review_v10_diagnostics.py", "--jar", jar,
                                 "--server-off-report", "reports/DIAGNOSTICS_SERVER_DISABLED_V10_RELEASE4.json",
                                 "--server-on-report", "reports/DIAGNOSTICS_SERVER_ENABLED_V10_RELEASE4.json",
                                 "--server-reenter-report", "reports/DIAGNOSTICS_SERVER_REENTER_V10_RELEASE4.json",
                                 "--client-report", min_report, "--client-reenter-report", reenter_report,
                                 "--artifact-sha", sha, "--output", "reports/REVIEW_V10_DIAGNOSTICS_RELEASE4.json"),
        "prepare_alias2_no_launch": alias,
        "actual_alias2_three_phases_separate_root_go": alias + ["--execute"],
        "independent_alias2_after_all_three_stop": command("verify_review_v10_alias_saved.py",
                                 "--author-report", "reports/V10_ALIAS_AUTHOR_RELEASE2.json",
                                 "--reenter-report", "reports/V10_ALIAS_REENTER_RELEASE2.json",
                                 "--production-report", "reports/V10_ALIAS_PRODUCTION_REOPEN_RELEASE2.json",
                                 "--artifact-sha", sha, "--report", "reports/V10_ALIAS_DISK_INDEPENDENT_RELEASE2.json"),
        "archive_pristine_reopen5_only_after_all_actual_required_gates": command("archive_review_v10_scene.py",
                                 "--jar", jar, "--author-report", author, "--author-client-report", min_report,
                                 "--client-reenter-report", reenter_report, "--server-report", reopen,
                                 "--world", pristine, "--fresh-baseline", "--output", "build/Review-v10-pristine-scene-release4.zip",
                                 "--report", "reports/REVIEW_V10_SCENE_ARCHIVE_RELEASE4.json"),
    }
    return {"schema": "dw-v10-candidate8-fresh-runtime-planning-v1",
            "status": "PREPARED_NOT_RUN_BOUND_CURRENT_FREEZE" if sha != TOKEN else "UNBOUND_MAIN8_QA22_NOT_BUILT_NOT_RUN",
            "productionJar": jar, "productionJarSha256": sha, "qaJar": qa, "qaJarSha256": qa_sha,
            "executionPerformed": False, "commands": commands,
            "requiredPriorRootFreeze": ["actual main8 binary/hash/manifest + native XML/core results", "actual supplied QA binary/hash/metadata includes unchanged QA20 alias inventory extension and QA21 middle-key probes with QA22 1.20.1 Text API correction"],
            "requiredCurrentArtifactStaticEvidence": ["resources/semantic correspondence and native source/tests must be checked against main8", "Candidate7 audit bytes may be retained as historical or referenced only with an explicit unchanged-input comparison; never overwrite their artifact hash"],
            "sequence": ["fresh author7 + Creative patch7 + QA-free minimal reopen5 + full owned-scene reopen4 + diagnostics markers4",
                         "typed restart audits while no cost windows run", "generate AUTHOR/REENTER/strict shader client markers4 from actual author7",
                         "dedicated diagnostics OFF60 / ON60 / REENTER8s, no overlapping games/build/heavy validators",
                         "MIN15 -> REENTER4 actual MIN15 saved-world copy -> FULL4 fresh pristine author7 copy; each must finish/save normally before next",
                         "alias2 independent three-process disk proof and final saved-world/ZIP audits only after cost windows finish",
                         "archive5 pristine scene; root/resource agent rebinds package argv after all new current proofs exist"],
            "canCombineIndependentAudits": ["pristine minimal/full typed audits can share one stopped-source inventory cache only if outputs retain both wrapper/run/profile identities", "native/XML/static/alias/provider roster checks may run in one non-performance Python stage; each distinct evidence status/hash must remain visible", "MIN15+REENTER4 exact menu snapshot comparisons and linked diagnostics exports can be collected in one post-stop stage"],
            "cannotSubstitute": ["full-server owned-scene restart is not an integrated Starlight client-worker proof", "MIN and FULL each need actual69+18/GUI/diagnostics plus69 real canonical middle-key checks,7 old-registry client middle-key checks and one actual picked-item re-placement; shader FULL additionally needs active Iris+42 getter proof", "same-session server/client ACK is90010 only, not all69 RP render-chain coverage", "client old-alias pick checks supplement, never replace the separate alias3-process disk/inventory/debug compatibility proof", "fresh alias fixture cannot stand in for untouched full source-city entity preservation", "GPU timing/causal FPS percentage/manual art acceptance remain NOT_PROVED"],
            "historicalFailure": {"wrapper": "reports/V10_CLIENT_FULL_RELEASE3_ATTEMPT_1.json", "kind": "root-owned FULL3 deadlock, forced termination; no normal-save claim", "oldCandidate7Results": "retain original hashes/statuses; no candidate8 relabel"},
            "manualReview": "NOT_RUN", "wholeOriginalTask": "NOT_COMPLETE_PENDING_USER_REVIEW_AND_REMAINING_SCOPE"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", default=JAR)
    parser.add_argument("--qa-jar", default=QA)
    parser.add_argument("--artifact-sha")
    parser.add_argument("--output", type=Path, default=ROOT / "tools/review_v10_candidate8_runtime_plan.json")
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to(ROOT) or output.exists():
        raise ValueError("Only a new project-local planning output is permitted")
    digest, qa_sha = TOKEN, None
    if args.artifact_sha:
        if not re.fullmatch("[0-9a-f]{64}", args.artifact_sha):
            raise ValueError("Exact frozen main8 SHA256 required")
        actual = hashlib.sha256((ROOT / args.jar).read_bytes()).hexdigest()
        if actual != args.artifact_sha:
            raise ValueError("Frozen JAR bytes do not match requested current hash")
        digest = actual
        qa_sha = hashlib.sha256((ROOT / args.qa_jar).read_bytes()).hexdigest()
    result = plan(args.jar, args.qa_jar, digest, qa_sha)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"status": result["status"], "path": str(output), "executionPerformed": False}))


if __name__ == "__main__":
    main()
