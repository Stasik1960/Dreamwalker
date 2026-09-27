# Intentional retirement: 23 IDs / 33 cells

2026-09-27. `BB-COMPOSITE-INPUT` closed for the new copy by explicit user approval
and the independent verifier. This is intentional retirement, not restoration:
all listed cells become `minecraft:air`; visual holes are accepted. No old model,
collision, helper layout or ownership was reconstructed. `--recover-city` was not used.

**This archive is still a legacy map before full-city conversion. It is not a
release map and must not be loaded with the rc.1 candidate.**

- Original immutable ZIP SHA-256:
  `c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9`.
- New `Bloodborne-MODDED-retired-composites-rc1.zip` SHA-256:
  `aed638e52143522abfbb3c63b85f377726727a5af039f532417ae0c6772504d1`.
- Independent comparison: exactly 33 cells in 21 chunks / four region files;
  all 10,009 chunks checked; zero additional terrain changes/deletions;
  unrelated typed NBT unchanged; all 170 nonterrain files byte-identical.
- All 23 retired IDs absent, including unused palette entries. Unknown IDs
  against the frozen legacy/v2 plus current schemas: zero. IDs absent from the
  current rc.1 registry: 43,634, requiring a separate conversion gate.
- 15,759 actual helper cells checked against saved root ownership and the
  state-specific **legacy** footprint: zero orphans, zero helper removals.
- Repeat invocation: zero changes; all 186 world files and the external ledger
  remain byte-identical. ZIP payload matches the verified directory.

[Ledger with old state → air and chunk/file hashes](evidence/retirement-rc1/retirement-ledger.json),
[independent full report](evidence/retirement-rc1/independent-verifier.json.gz),
[compact proof](evidence/retirement-rc1/summary.json),
[repeat command](evidence/retirement-rc1/idempotence.command.json).

## Exact authorized cells

Namespace: `bloodborne_blocks`; dimension: `minecraft:overworld` for every row.
Every row has reason `USER_APPROVED_RETIREMENT` and destination `minecraft:air`.
The table contains all 23 distinct IDs and all 33 positions. No other cell was
retired. Full saved states are also in the machine-readable ledger.

| ID | Position (x, y, z) | Saved facing |
|---|---|---|
| `m_002066f51dada272` | `(-570, 26, -204)` | `east` |
| `m_0268950fde384cd2` | `(-183, 37, -69)` | `west` |
| `m_083b37846b84dfe5` | `(-570, 26, -206)` | `south` |
| `m_0d33f9f2548b0bd4` | `(-442, 100, 1)` | `north` |
| `m_0d5afc55969e4401` | `(-444, 100, 0)` | `east` |
| `m_0ea41aaba3b1270b` | `(-564, 26, -222)` | `west` |
| `m_0ea41aaba3b1270b` | `(-388, 99, 6)` | `west` |
| `m_0ea41aaba3b1270b` | `(-324, 117, 45)` | `west` |
| `m_151c82c89bf3e903` | `(-469, 55, -167)` | `east` |
| `m_1c956864ebbe7275` | `(-442, 98, 1)` | `west` |
| `m_1d1b7a097ae079e4` | `(-303, 56, -138)` | `north` |
| `m_28eeb854521aed82` | `(-184, 36, -69)` | `west` |
| `m_2a7c34363b43c4c7` | `(-183, 38, -69)` | `west` |
| `m_33da6ee4228d8f7b` | `(-442, 99, 0)` | `south` |
| `m_35d489409e5bc984` | `(-442, 98, 0)` | `south` |
| `m_3b25c328c5fab38a` | `(-444, 99, 0)` | `north` |
| `m_3fffe3a64e33d90e` | `(-442, 100, 0)` | `south` |
| `m_4abc6814bf623d92` | `(-442, 99, 1)` | `north` |
| `m_500cb8aa0c17f2f8` | `(-367, 68, -148)` | `east` |
| `m_54ab4b8bd1db37ef` | `(-303, 56, -140)` | `south` |
| `m_64d5e280da6d5edc` | `(-403, 60, -194)` | `north` |
| `m_6e29addcc429a971` | `(-705, 38, -110)` | `north` |
| `m_6e29addcc429a971` | `(-698, 38, -247)` | `north` |
| `m_6e29addcc429a971` | `(-684, 38, -276)` | `north` |
| `m_6e29addcc429a971` | `(-660, 38, -318)` | `north` |
| `m_6e29addcc429a971` | `(-510, 53, -203)` | `north` |
| `m_6e29addcc429a971` | `(-303, 63, -257)` | `north` |
| `m_6e29addcc429a971` | `(-252, 71, -288)` | `north` |
| `m_7e28f29afb24c0b3` | `(-540, 123, 39)` | `south` |
| `m_7e28f29afb24c0b3` | `(-533, 35, -344)` | `south` |
| `m_7e28f29afb24c0b3` | `(-279, 45, -29)` | `south` |
| `m_9bbec750aad6d419` | `(-486, 54, -151)` | `north` |
| `m_9d9cec331c072bb9` | `(-367, 69, -148)` | `west` |

## Reproduce on a newly named copy

From `Bloodborne-Blocks`, with Python 3.11+ and `tools/requirements-ci.txt`:

```powershell
python -B -X utf8 tools/retire_missing_composites.py reference-inputs/latest-modded-world.zip build/retirement-review/Bloodborne-retired-review --retire-missing-composites --ledger build/retirement-review/ledger.json --expected-source-sha256 c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9
python -B -X utf8 tools/verify_composite_retirement.py reference-inputs/latest-modded-world.zip build/retirement-review/Bloodborne-retired-review --ledger build/retirement-review/ledger.json --report build/retirement-review/verifier.json --expected-source-sha256 c517dfeb52c4d13bdbe90e02a93ac00416354eb89313a9d1377f24823af6d0e9
```

The verifier also needs the immutable, hydrated historical 2.0.1 JAR at
`../releases/Bloodborne-Blocks/bloodborne-blocks-2.0.1-mc1.20.1.jar` (pinned SHA
`e400442c1711b013dd73d2a7763fc7c34e778af05876d90b69c2bedced3c1212`).
It checks legacy helpers; its PASS is not runtime compatibility proof. Repeat
the writer command unchanged to verify idempotence without writing to the copy.
A different source, list, state, altered output or unproven partial run fails closed.

Keep the source ZIP unchanged. Roll back by selecting the original backup and
creating another new copy. Do not merge chunks from a failed diagnostic attempt.
The full-city and runtime gates remain separately tracked in
[RELEASE-STATUS.md](../RELEASE-STATUS.md).
