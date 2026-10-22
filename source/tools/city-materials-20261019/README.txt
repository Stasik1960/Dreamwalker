Dreamwalker BB materials.1, base streaming.5 / ece36054605f3fbd00b1ee90449b6749e0a977d7.

Read AGENTS.md, C:\Users\vakir\CodexControl\storage-mode.json and
C-return-result.txt first. Use Guard.ps1 -Mode Check and -Mode Operation for
controlled builds/clients. Local C: only; the old E: migration stays paused.
Do not run Gradle while a native Minecraft test is running.

audit.py performs ONE global exact-v15 PNG/model/used-UV pass and preserves
the original JAR/map manifests. provenance_audit.py is its helper. Inputs are
the explicitly selected streaming.5 deliverable and the original v15 ZIP in
the preserved DW repository. All research output is under
../research/city-materials-20261019. Do not repeat the global resource audit
after a code-only lighting edit; verify_artifact.py proves resources unchanged.

build_gallery.ps1 builds the separate QA add-on. Its immutable frozen copy
is shared by the old and new JARs, and is never shipped in the production JAR.
build.ps1 builds/remaps the production JAR and runs SourceFlatLightChecks.
Use Java17 and the existing cached offline dependencies / WorldEdit API JAR.

native_case.py reuses exactly ONE existing owned QA runtime/save, native
libraries and graphics. frames.json has targeted triangle probes, exact
FULL-chunk gates and decimal teleport coordinates. After a successful process,
it archives capture input/output, logs and native screenshots, retaining the
normally saved world in place. An interrupted run is not a passed check; retain
and archive its partial runtime evidence explicitly before retrying.

The user explicitly requested performance regression comparison on resume.
--measure enables the existing 20-second settled frame window and streaming
phase counters, plus the existing Windows memory sampler. No JFR or travel
route is introduced. reset_working_save.py --case <case> restores identical
active bytes from the immutable delivered map IN THE SAME WORKING SAVE before
each matched case. It replaces only changed ordinary files and removes only
extra QA files in that exact save; no reparse traversal or recursive deletion.
Both cases use the same QA pumpkin removal and input manifest. Original map
and existing independent backup are never modified or recopied elsewhere.

Comparison cases: compare-base (streaming.5), compare-final2 (final materials.1).
compare-candidate/reopen and rejected-preview retain a rejected broad prototype;
compare-final/reopen-final and narrow-preview retain the intermediate a991 JAR
which left basalt trim black. Neither preview is the released JAR.
diagnose-tower uses a separate explicitly selected QA
addon and full 128-block visible ray, independent of invisible collider bounds.
compare.py checks exact input/options/QA/command/camera/weather equality and
summarizes frame windows, RAM/commit, heap snapshots, phase/server timings and
log differences. One pair is observational, not a statistical confidence bound.
summarize.py --case <case> extracts actual visible triangle/texture/light proof.
The one-frame reopen-final2 case opens the final candidate's normally saved world
in a fresh process, with no reset. After an observed facade FPS drop in the
main comparison, --measure was enabled here for ONE targeted anomaly repeat,
combined with mandatory save/reopen. Both results are retained. This uses the
frozen enhanced surface probe and remaining-trim-frames.json (8 rays), after FPS.
snapshot_reopen_host.ps1 records one read-only host snapshot during that window;
summarize_repeat_performance.py reports the repeat without replacing the main pair.
final-ao-on is a visual branch control of the intermediate a991 build. It led
to diagnose-trim-flat2, which proved the residual basalt material/light case.
diagnose-trim-flat was rejected before world load for 10 points exceeding the
gallery limit of 8; its failed evidence was archived, never marked passed.
The native driver now validates that limit before starting Java.

The production correction changes only diagonal GENERAL faces of opaque source_*
carriers where the part's native source origin is its own opacity-15 root cell.
An exterior centroid sample is used. If the centroid remains inside its opaque
root, four slightly inset exterior corners can be averaged, only when all four
are outside the root and none is in another opacity-15 cell. Light channels
include zero; no brightest/floor fallback is used. Directional/cull-face lists,
cardinal planar faces, inset faces, shifted origins, nonopaque carriers and the
native AO branch keep their native queries. Raw-model general-face membership
and surface coordinates are resource-sized metadata, cleared on reload; per-root
occlusion wrappers are never keys of the resource cache.
The BER fallback tracks the sampled section for existing mesh invalidation.
No texture/UV/layer/geometry/ID/recipe/save/physics/door/stair edits, no emission,
gamma or global AO override, no conversion/migration/barrier cleanup/DH/LOD.
