# Actual full-mod NBT compatibility evidence

`PASS_ACTUAL_LITHIUM_TYPED_EQUAL_RAW_REORDER_CANONICAL_EXACT_AND_FRESH_SCENE`. Current artifact `6cf52a3c91426e9ec3cf050d6949b94ebcc49e6168b6f497bb75aebacf174316`; actual selected-mod profile count83, Lithium0.11.2, normal server exit0 and91 authored objects.

All three actual runtime cases have semantic typed equality, different raw byte ordering, equal canonical bytes and exact typed round trips. Cases cover floor payload after SourceShift removal, vertical four-key payload, and nested byte/int/float/double values, primitive arrays and ordered list. Key class is actual fastutil Object2ObjectOpenHashMap. Raw/canonical SHA values and key iteration orders are retained inJSON.

The prior7f70 candidate rejected the first horizontal glass root `[22,64,18]` as `write_not_exact` after six objects. The current same-scene/same-selected-mod-profile run committed this root and all91 objects. The prior failed root did not dump its exact expected/read payload; this diagnostic limitation is retained. Recursive canonical encoding addresses the demonstrated byte-order instability without discarding typed payload checks.

Production reopen and manual visual acceptance remain separate gates. Standalone JShell simulation is separate from these actual runtime observations. Complete source reports: `reports/FIRST_SET_FULL_AUTHOR_V8_RELEASE.json`, `reports/FIRST_SET_FULL_AUTHOR_V8_FINAL.json`.
