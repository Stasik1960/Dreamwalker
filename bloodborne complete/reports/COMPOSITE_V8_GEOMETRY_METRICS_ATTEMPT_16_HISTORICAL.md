# Native composite geometry metrics

Status: `PASS_ACTUAL_NATIVE_GAMETEST_GEOMETRY_AND_MICROBENCH`. Actual1280 canonical geometry state cases; newhorizontal mounts are excluded. Helper candidates are not actual owned helpers.

| Family | States before →after | Physical input max | Selection input max | Native physical max total | Native selection max total | Helper candidates max |
|---|---:|---:|---:|---:|---:|---:|
| prototype_double_door | 512 →512 | 6 →4 | 7 →4 | 220 →43 | 257 →52 | 28 →22 |
| prototype_wood_window | 512 →512 | 51 →9 | 51 →9 | 58 →23 | 58 →23 | 8 →10 |
| prototype_thin_window | 512 →1536 | 33 →5 | 33 →5 | 117 →42 | 117 →42 | 14 →17 |
| prototype_tree | 512 →512 | 1 →1 | 24 →2 | 30 →6 | 2718 →1242 | 377 →593 |
| prototype_roof | 512 →512 | 82 →8 | 143 →1 | 516 →43 | 622 →34 | 15 →17 |

Input counts describe authoring boxes. Native totals sum actual decomposed `VoxelShape.getBoundingBoxes()` over the object cells after the sameproduction16³ builder. Fullcell distributions and percell maxima remain inJSON.

| Query | Paired cell population | Iterations /round | Median CPU before →after | After /before |
|---|---:|---:|---:|---:|
| collision | 15828 | 100000 | 99.075ms →77.802ms | 0.785 |
| selection | 15828 | 100000 | 286.601ms →355.885ms | 1.242 |

The same native cachelookup, local mover, cellpopulation, axis andoffset run in one serverprocess after5 warmups; seven measuredrounds alternate order. CPU-only microbenchmark: noFPS,heap,TPS,city/worldrender ormanual gameplay acceptance claim.

Selection-shape query median **increased 24.2%**. After faster/slower/equal rounds: 0/7/0. These queries use `calculateMaxOffset` on selection shapes, not actual selection raycasts. Results establish this bounded CPU observation, not steady-state or whole-world speedup.

| Family | Paired cell rows | Selection nonempty before →after | Old empty →new nonempty | Distributed selection inputs before →after |
|---|---:|---:|---:|---:|
| prototype_double_door | 372 | 336 →268 | 36 | 2392 →610 |
| prototype_wood_window | 136 | 128 →136 | 8 | 2060 →432 |
| prototype_thin_window | 664 | 576 →648 | 72 | 5712 →1548 |
| prototype_tree | 14400 | 9216 →14400 | 5184 | 49536 →27072 |
| prototype_roof | 256 | 232 →256 | 24 | 16936 →496 |

Tree cells form 91.0% of this geometry-weighted population. Current measured tree outline uses2 boxes and adds5184 nonempty paired cells relative to v7; maximum helper candidates 377 →593. Two full-height broad planes add occupied selection cells. Former empty-list/empty-shape fast paths become cached nonempty GridShape queries. This is a source-backed workload change, not an isolated causal timing proof.

Collision median decreased by21.5%; after faster/slower/equal rounds: 6/1/0.
Collision round ranges vary by more than1.5×; JIT/runtime drift was not isolated. The median is not steady-state speedup certification.
Selection median increased by24.2%; after faster/slower/equal rounds: 0/7/0.

Historical two-plane outline was subsequently corrected to narrow lower/wide upper four planes; this historical timing result is retained.
For a later requested performance investigation, measure per-family cache and actual raycast queries alongside this proxy. Preserve all raw timings; do not infer FPS or compare independent server runs as a controlled within-process experiment.

The current user review accepts wooden window panels and the volumetric RP tree. The flat composite tree measured here remains under correction; native test PASS is not whole-set acceptance.

Raw actual data: `reports/COMPOSITE_V8_GEOMETRY_METRICS_ATTEMPT_16_HISTORICAL.json`; log `reports/FIRST_SET_GAMETEST_ATTEMPT_16.log`.
