# Production test reconciliation

The live resources now represent only the user-approved production palette.
Historical QA2/Nightmare compiler tests must not reconstruct or assert retired
IDs against that live palette. Their evidence is retained in
`production-authoring-inputs.json.gz`; this is a test-scope change, not a claim
that historical evidence was deleted or disproved.

## Current `check` Python coverage

- Generic safety remains: `test_region_padding`, `test_logical_world`,
  `test_source_variant_rng`, `test_fixture_level_metadata`, and
  `test_support_collision` use synthetic fixtures or general codecs.
- `test_source_review_decisions` remains a pure historical-decision unit test;
  it does not read current resources or compile live output.
- `test_visual_slots` remains: it uses a temporary fixture and keeps BASE/ALT
  behavior independent of the former all-palette inventory.
- `test_build_nightmare_contracts`, `test_nightmare_geometry_recipes`,
  `test_nightmare_functional_recipes`, `test_nightmare_composition_recipes`,
  and `test_export_alt_visual_starter` remain: they use temporary fixtures or
  source-pack inputs and do not write/compile the live palette.
- `test_support_normalization` remains against current production Contract V2.
  Its frozen source comparison permits only the documented C282 semantic
  correction and production state-mesh deduplication identities; its synthetic
  support, helper, collision, yaw, and anchor cases remain unchanged.
- `check_contract_orientation` remains against current Contract V2 resources.
  It is the live textured mesh/orientation, collision, selection, helper and
  anchor gate.
- `check_logical_resources` and `test_production_palette` are the live
  manifest/resource gates; `test_production_door`, `test_production_inventory`,
  `test_production_geometry`, and `test_production_gallery` cover the bounded
  production behavior and generated fixture.

## Excluded live-palette historical checks

`test_contract_v2`, `test_reviewed_migration`, `test_reviewed_source_examples`,
`test_reviewed_gallery`, `test_qa2_trees`, `test_qa2_checkpoint`,
`test_old_logical_migrations` and `test_nightmare_gallery` assert historical
families, compatibility IDs, QA galleries, or a pre-pruning all-V2 baseline.
They are not run by `check` against current resources.

`test_split_transactions` remains in `check`. Its direct atomicity cases remain
synthetic; its former live-contract compiler cases now write a temporary fixture
from `production-authoring-inputs.json.gz`. The production manifest/output gate
separately covers current raw split outputs. Do not weaken collision, ownership,
or all-or-nothing checks.
