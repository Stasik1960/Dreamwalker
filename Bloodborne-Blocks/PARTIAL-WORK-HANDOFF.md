# Partial beta checkpoint

- Agony / beta-pass base HEAD: `733c8323e455adb4ae811aa53dff69239ac2720f`.
- Branch: `main`; remote: `https://github.com/Stasik1960/Dreamwalker.git`.
- Pushed checkpoint HEAD: the commit containing this file (see `git log -1`).
- State at stop: 49 production families, 1644 states, 722 meshes; version is still `2.1.0-alpha.1`.

## Already changed after Agony

- Families: `o_ladder_01`, `o_ladder_03`, `o_lantern`, `o_shuttered_window`, and statue families `o_c008_1/2/3/5`.
- Runtime: transition preflight/persistence, owned-cell ladder contact, non-destructive ordinary window placement, and collision-boundary validation.
- Resources: generated definitions/contracts/geometry/meshes/visual slots plus affected blockstates/models. The window no longer has the `embedded` state and owns only a 1x2 column.
- Tooling/evidence: `tools/apply_beta_client_qa_patch.py`, `tools/test_beta_client_qa.py`, immutable beta fingerprints, allowlist, and patch report under `docs/beta-client-qa/`.

## Regression evidence at stop

- `tools/test_beta_client_qa.py`: PASS, 6/6.
- `tools/test_agony_patch.py`: PASS, 6/6.
- Fabric GameTests: PASS, 33/33 in `build/test-results/gametest/TEST-logical-gametest.xml`; the server printed `All 33 required tests passed`.
- The final Gradle process was stopped during server shutdown when the user requested handoff. A complete beta `check build` was **not** run, so no beta release claim is made.
- No graphical client acceptance was run.

## Exact stop point / remaining work

Stage 1 implementation and its targeted regressions were present, with the last ladder contact test passing. Work stopped before independent review, full `check build`, beta version/changelog/JAR/tag, gallery/client QA, modded-world conversion, ALT kit, and final HANDOFF update. Do not assume the partial implementation is released.

Latest verified modded input is `reference-inputs/latest-modded-world.zip`, SHA-256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`, `MODDED_WORLD=true`. It is an immutable LFS input and was not converted. The vanilla `reference-inputs/source-world.zip` remains reference-only.
