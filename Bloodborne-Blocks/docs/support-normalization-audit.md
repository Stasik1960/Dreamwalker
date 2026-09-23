# Contract V2 support-plane / collision audit

Generation-time only. No source pattern, master position, mesh payload or semantic boundary changed.

Collision counts below are global primitives **per declared state**, not clipped helper-cell storage slices.
A source cuboid count cannot be reconstructed from an untagged polygon mesh; `render_elements` counts actual render polygons.

```json
{
  "families": 47,
  "states": 740,
  "ground_families": 43,
  "below_support_families": 13,
  "automatically_corrected_families": 13,
  "ambiguous_mount_families": 4,
  "old_collision_boxes": 1020,
  "new_collision_boxes": 1020,
  "max_ordinary_collision_boxes": 2,
  "families_with_old_helpers_below": 2,
  "families_with_helpers_below": 0,
  "fail_families": 0
}
```

| ID | Placement | old minY | Y correction | old → new boxes (sum) | Policy | helpers (max) | below before → after | Status |
| --- | --- | ---: | ---: | ---: | --- | ---: | ---: | --- |
| o_dead_tree_planter (hidden) | FLOOR | -1 | 1 | 8 → 8 | TRUNK | 15 | 9 → 0 | PASS |
| o_cases_0  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 0 | 0 → 0 | PASS |
| o_iron_gate  | FLOOR | -0.8125 | 0.8125 | 32 → 32 | GATE | 48 | 8 → 0 | PASS |
| o_iron_railing  | FLOOR | 0 | 0 | 384 → 384 | FENCE | 1 | 0 → 0 | PASS |
| o_c001_a (hidden) | FLOOR | -1 | 1 | 12 → 12 | TRUNK | 7 | 0 → 0 | PASS |
| o_c001_b (hidden) | FLOOR | -1 | 1 | 4 → 4 | TRUNK | 7 | 0 → 0 | PASS |
| o_c003  | FLOOR | -0.0625 | 0.0625 | 16 → 16 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c008 (hidden) | FLOOR | -0.982371 | 0.982371 | 8 → 8 | TWO_BOX | 2 | 0 → 0 | PASS |
| o_c009_a (hidden) | FLOOR | -1 | 1 | 4 → 4 | TRUNK | 7 | 0 → 0 | PASS |
| o_c009_b (hidden) | FLOOR | -1 | 1 | 4 → 4 | TRUNK | 7 | 0 → 0 | PASS |
| o_c471 (hidden) | FLOOR | -1 | 1 | 4 → 4 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c046  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 0 | 0 → 0 | PASS |
| o_c474  | FLOOR | 0 | 0 | 8 → 8 | POST | 15 | 0 → 0 | PASS |
| o_c1962 (hidden) | FLOOR | -0.0625 | 0.0625 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c1979 (hidden) | FLOOR | 0 | 0 | 16 → 16 | TWO_BOX | 3 | 0 → 0 | PASS |
| o_c282 (hidden) | FLOOR | -1 | 1 | 24 → 24 | DOOR | 24 | 0 → 0 | PASS |
| o_c561  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 0 | 0 → 0 | PASS |
| o_c654 (hidden) | WALL_ADJACENT | 0.1875 | 0 | 8 → 8 | SIMPLE_BOX | 0 | 0 → 0 | WARN |
| o_c1319  | FLOOR | -0.053125 | 0.053125 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c1491 (hidden) | FLOOR | -1 | 1 | 4 → 4 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_wall_deco_1  | WALL_ADJACENT | 0 | 0 | 0 → 0 | NONE | 0 | 0 → 0 | WARN |
| o_c002  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 0 | 0 → 0 | PASS |
| o_c008_1  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c008_2  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c008_3  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c008_4  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c008_5  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c471_a  | FLOOR | 0 | 0 | 2 → 2 | SIMPLE_BOX | 2 | 0 → 0 | PASS |
| o_c471_b  | FLOOR | 0 | 0 | 2 → 2 | SIMPLE_BOX | 2 | 0 → 0 | PASS |
| o_c1680  | FLOOR | 0 | 0 | 16 → 16 | TWO_BOX | 4 | 0 → 0 | PASS |
| o_c1962_a  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 0 | 0 → 0 | PASS |
| o_c1962_b  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 0 | 0 → 0 | PASS |
| o_c1979_1  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c1979_2  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c1979_3  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 2 | 0 → 0 | PASS |
| o_c1979_4  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c1979_5  | FLOOR | 0 | 0 | 16 → 16 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c028  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c282_a  | FLOOR | 0 | 0 | 64 → 64 | DOOR | 11 | 0 → 0 | PASS |
| o_c282_b  | FLOOR | 0 | 0 | 64 → 64 | DOOR | 18 | 0 → 0 | WARN |
| o_c618  | FLOOR | 0 | 0 | 32 → 32 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c654_a  | WALL_ADJACENT | 0.1875 | 0 | 16 → 16 | SIMPLE_BOX | 9 | 0 → 0 | WARN |
| o_c654_b  | WALL_ADJACENT | 0.1875 | 0 | 8 → 8 | SIMPLE_BOX | 8 | 0 → 0 | WARN |
| o_c1491_a  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 2 | 0 → 0 | PASS |
| o_c1491_b  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 2 | 0 → 0 | PASS |
| o_c1491_c  | FLOOR | 0 | 0 | 8 → 8 | SIMPLE_BOX | 1 | 0 → 0 | PASS |
| o_c001  | FLOOR | 0 | 0 | 48 → 48 | TRUNK | 9 | 0 → 0 | PASS |

## Manual review / warnings

- `o_c654`: AMBIGUOUS_MOUNT_POLICY. wall/ceiling/hanging/support policy has no explicit plane; no floor inference
- `o_wall_deco_1`: AMBIGUOUS_MOUNT_POLICY. wall/ceiling/hanging/support policy has no explicit plane; no floor inference
- `o_c282_b`: COLLISION_EXCESSIVE_EMPTY_VOLUME.
- `o_c654_a`: AMBIGUOUS_MOUNT_POLICY. wall/ceiling/hanging/support policy has no explicit plane; no floor inference
- `o_c654_b`: AMBIGUOUS_MOUNT_POLICY. wall/ceiling/hanging/support policy has no explicit plane; no floor inference

Already-simple authored collision is retained, not modified to manufacture before/after reductions.
Fallback simplifier tests include synthetic 27 → 1 evidence; those are not claimed as existing family counts.
Wall-adjacent families without an explicit mount plane remain unchanged and AMBIGUOUS_MOUNT_POLICY.
