# MODDED beta conversion

Input is only `reference-inputs/latest-modded-world.zip`, SHA-256 `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`. The original source world is reference-only.

The existing converter accepts `--source-mode modded` and `--conflict-policy conservative|aggressive`. Frozen modular mappings and named legacy carriers are composed with the current reviewed Contract V2 patterns. It never converts raw vanilla blocks. Unknown IDs remain untouched and explicitly fail beta registry compatibility; retaining a block is not proof that beta can load it.

Use `--inventory` with the inventory included in the world-report archive to select rare exact anchors. ZIP input requires `--expected-source-sha256`; a mismatch fails before copying. Output must be a fresh directory. Work happens in a temporary copy, and the source remains immutable.

Conservative mode rejects occupied targets atomically. Aggressive mode permits only proven target-cell replacements of Bloodborne states with no foreign block entities, scheduled ticks or unrelated helper ownership. It does not guess unknown families or positions. Exact assembly matches are applied through the existing world engine, including dependent passes when a conversion frees another object's target. The report records every pass and before/after change; a second invocation must convert zero objects.

Old embedded windows have explicit offline migration. Other retired logical `o_*` merges are absent from the supplied input and are not implemented by this adapter. Their historical evidence remains available; the adapter makes no claim to handle hypothetical saves containing them.

`check_logical_world.py` independently replays authorization and checks final NBT/helper ownership. `verify_modded_preservation.py` checks the cell-change chain and preservation outside the ledger, including chunk metadata and nonterrain files. These tools do not turn unknown legacy IDs into registered beta blocks.

`compare_modded_reference.py` reads only relevant regions from the hash-verified vanilla reference. Its frozen source-index evidence maps the modded overworld to original `eh_s2:yharnam`; XYZ is unchanged. It reports exact matches and mismatches rather than moving objects speculatively. No reference data is used as conversion input.

Actual counts, SHA-256 values, idempotence and QA limitations are published in `releases/Bloodborne-Blocks/2.1.0-beta.1/worlds-proof.json` and the accompanying world-report archive. Never treat an `UNRESOLVED` output as a playable beta city.
