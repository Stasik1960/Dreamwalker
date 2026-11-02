Independent matched performance recorder

This test-only mod has no imports or calls to DreamWalker production classes.
Use the same remapped QA JAR for the pinned pre-ALT baseline and both new modes.
It must not be included in the delivered production mod.

JVM arguments:
  -Ddw.perf.plan=<absolute tools/perf/route.json>
  -Ddw.perf.output=<new absolute run output folder>
  -Ddw.perf.expectedSha256=<production JAR SHA256>
  -Ddw.perf.variant=baseline|original|alt

The recorder waits for an actual focused, visible integrated world. A report
status WAITING_FOCUS requests activation of that benchmark window. Do not spoof
Minecraft focus fields. Focus loss during a measurement invalidates the run.
It never changes visual mode or reloads resources; the runner must seed the
selected state before launch, use an independent identical world copy, and
verify that the intended built-in profile is active. No original world edits.

Six populated city cameras, two complete visits, same exact sequence. Loading
records from teleport request until every in-view chunk is FULL, native terrain
is complete, the target has rendered and a nonair scene probe is present, plus
two fixed stable seconds with native mesh, terrain-update and shape-publication
queues idle. Public core metrics are read through cached reflection, without
forcing work or importing newer visual-mode classes. Missing required counters
fail the test. Native idle state is checked and recorded again at steady end.
Each steady window lasts at least 15 actual seconds.
Readiness timeout is 90 seconds; whole recorder timeout 840 seconds. Timeouts,
focus loss, missing terrain and missing frame records fail instead of passing.
Every phase also requires a successful asynchronous screenshot callback and a
new nonempty PNG before it is added to the completed phases. Screenshot I/O has
its own 30-second deadline; failed writes never become completed measurements.

Frame timing is System.nanoTime at RenderSystem.flipFrame HEAD, with a fixed
preallocated array and no per-frame file writes. It measures CPU-side native
presentation intervals, including pacing, rather than GPU execution time or
physical display scanout. Raw loading and steady intervals are saved separately.
One-second heap/GC/direct-buffer samples are Java accounting, never called RSS.

Start sample_process.py alongside the owned benchmark PID:
  python sample_process.py --pid PID --output process-memory.jsonl --seconds 900
This read-only Windows sampler records Working Set and Private Bytes separately.
It exits when that PID exits and never terminates a process.

Use one up-front lock manifest with SHA256s of all nonproduction client mods,
config files, unchanged source world, JDK/launcher, QA JAR, route and exact
options/resolution/heap. Keep shader and external resource-pack choices identical.
Recommended order: baseline A0, new Original, new ALT, baseline A1. Repeated
baseline shows observed ordering/system drift. Never import old-PC measurements.

compare.py input manifest example (absolute paths also accepted):
  {"schema":1,"lock":{"profileLockSha256":"...","worldSha256":"..."},
   "runs":[{"name":"base-A0","variant":"baseline","report":"A0/performance.json","memory":"A0/process-memory.jsonl"},
           {"name":"new-original","variant":"original","report":"original/performance.json","memory":"original/process-memory.jsonl"},
           {"name":"new-alt","variant":"alt","report":"alt/performance.json","memory":"alt/process-memory.jsonl"}]}

  python compare.py --manifest cohort.json --output comparison.json

The runner also writes pid.json, lock.json, variant-binding.json,
mode-after-run.json and launch-result.json next to each performance.json.
cohort.py seals cohort-lock.json before the first Popen and refuses changed
mod/config/world/QA/options or pinned production identities on later runs.
The analyzer independently rehashes these files and the canonical cohort lock,
checks loaded PID/JAR/QA/route hashes, intended client/world mode seeds and the
saved mode afterward. A nonzero JVM exit remains incomplete even if a prior
save happened; successful timing output alone does not turn it into a pass.

The analyzer rejects incomplete/unfocused/mixed settings, wrong baseline SHA,
different recorder/route hashes and frame statistics inconsistent with raw
intervals. It compares each visit and pools raw steady/loading intervals only
within their separate categories. Process memory is aligned to recorded epoch
windows; sampled peaks and startup lifetime peaks stay distinct. A short loading
window can have no one-second memory sample: this is reported rather than filled.
The output describes measured local results, not universal proof that all map
locations or future machines have no stutters.

Synthetic arithmetic checks, not game evidence:
  python test_compare.py
