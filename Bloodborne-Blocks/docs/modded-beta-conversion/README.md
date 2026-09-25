# MODDED beta conversion

Input is only latest-modded-world.zip, SHA-256 c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9. The original source world is reference-only.

The existing converter now accepts --source-mode modded and --conflict-policy conservative|aggressive. Frozen v2 carrier-to-module evidence is composed with current reviewed Contract V2 patterns; it never performs raw vanilla conversion. Exact modular groups are atomic. Unknown states stay unchanged and are reported as incompatible with beta.

Use --inventory build/modded-input-inspection/inventory.json for rare source anchors; ZIP input requires --expected-source-sha256. Reports include coordinate ledgers and Markdown conflict lists. Aggressive mode only allows proven target cells occupied by Bloodborne blocks without unrelated block entities/ticks/helper ownership; foreign blocks remain untouched.

Old embedded windows have explicit offline migration. Other retired logical o_* merges are not implemented in this adapter: none occur in the supplied modded input. Their historical evidence remains available; no guessed mappings are added.

Tool tests: test_modded_world_adapter 3/3; existing test_logical_world, test_split_transactions 7/7 and test_restored_migration 5/5 passed. Actual output QA and reference comparison are recorded with the converted copies, not inferred from these fixtures.
