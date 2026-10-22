# Native composite geometry metrics

Status: `PASS_ACTUAL_NATIVE_GAMETEST_GEOMETRY_AND_MICROBENCH`. Actual1280 canonical geometry state cases; newhorizontal mounts are excluded. Helper candidates are not actual owned helpers.

| Family | States before →after | Physical input max | Selection input max | Native physical max total | Native selection max total | Helper candidates max |
|---|---:|---:|---:|---:|---:|---:|
| prototype_double_door | 512 →512 | 6 →4 | 7 →4 | 220 →43 | 257 →52 | 28 →22 |
| prototype_wood_window | 512 →512 | 51 →9 | 51 →9 | 58 →23 | 58 →23 | 8 →10 |
| prototype_thin_window | 512 →1536 | 33 →5 | 33 →5 | 117 →42 | 117 →42 | 14 →17 |
| prototype_tree | 512 →512 | 1 →1 | 24 →4 | 30 →6 | 2718 →954 | 377 →449 |
| prototype_roof | 512 →512 | 82 →8 | 143 →1 | 516 →43 | 622 →34 | 15 →17 |

Input counts describe authoring boxes. Native totals sum actual decomposed `VoxelShape.getBoundingBoxes()` over the object cells after the sameproduction16³ builder. Fullcell distributions and percell maxima remain inJSON.

| Query | Paired cell population | Iterations /round | Median CPU before →after | After /before |
|---|---:|---:|---:|---:|
| collision | 12372 | 100000 | 77.022ms →52.939ms | 0.687 |
| selection | 12372 | 100000 | 358.056ms →347.789ms | 0.971 |

The same native cachelookup, local mover, cellpopulation, axis andoffset run in one serverprocess after5 warmups; seven measuredrounds alternate order. CPU-only microbenchmark: noFPS,heap,TPS,city/worldrender ormanual gameplay acceptance claim.

Selection-shape query median **decreased 2.9%**. After faster/slower/equal rounds: 6/1/0. These queries use `calculateMaxOffset` on selection shapes, not actual selection raycasts. Results establish this bounded CPU observation, not steady-state or whole-world speedup.

| Family | Paired cell rows | Selection nonempty before →after | Old empty →new nonempty | Distributed selection inputs before →after |
|---|---:|---:|---:|---:|
| prototype_double_door | 372 | 336 →268 | 36 | 2392 →610 |
| prototype_wood_window | 136 | 128 →136 | 8 | 2060 →432 |
| prototype_thin_window | 664 | 576 →648 | 72 | 5712 →1548 |
| prototype_tree | 10944 | 9216 →10944 | 1728 | 49536 →21312 |
| prototype_roof | 256 | 232 →256 | 24 | 16936 →496 |

Tree cells form 88.5% of this geometry-weighted population. Current measured tree outline uses4 boxes and adds1728 nonempty paired cells relative to v7; maximum helper candidates 377 →449. Four planes restrict lower0..6-block selection to3-block width while keeping upper6..18-block crown width9blocks. Coarse upper distribution still adds occupied cells versus v7. Fewer boxes and more nonempty calls have competing costs; the observed aggregate timing direction is reported separately, not assigned to one unmeasured cause.

Collision median decreased by31.3%; after faster/slower/equal rounds: 7/0/0.
Selection median decreased by2.9%; after faster/slower/equal rounds: 6/1/0.

Narrow-lower/four-plane correction is already applied. Current native geometry metrics reflect that actual descriptor; no further production geometry correction is proposed by this parser.
For a later requested performance investigation, measure per-family cache and actual raycast queries alongside this proxy. Preserve all raw timings; do not infer FPS or compare independent server runs as a controlled within-process experiment.

The current user review accepts wooden window panels and the volumetric RP tree. The flat composite tree measured here remains under correction; native test PASS is not whole-set acceptance.

Raw actual data: `reports/COMPOSITE_V8_GEOMETRY_METRICS.json`; log `reports/FIRST_SET_GAMETEST_ATTEMPT_19.log`.
