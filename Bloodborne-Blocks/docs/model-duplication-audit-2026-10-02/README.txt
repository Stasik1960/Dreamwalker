Bloodborne-Blocks: model duplication audit, 2026-10-02

Audited revision: 00948c35665f76bae1ff42006f1db93a98b0f91b.
Source fingerprints in the archived JSON reports also identify the audited working-tree inputs.

Open REPORT.html locally in a browser for the Russian report, comparison images,
and searchable list of 799 exact appearance groups. GitHub displays HTML source.
exact-duplicates.csv lists the 1,602 entries in those groups (837 registry IDs).
There are 803 repeated visual entries beyond one representative per group.
This does not imply that 803 registry IDs can be deleted.

The ZIP contains the full evidence, current audit scripts, and illustrations.
It deliberately excludes invalid preliminary reports. To reproduce using this
repository revision, extract its model-duplication-audit directory under
Bloodborne-Blocks/build/, retaining the original directory layout expected by
the scripts. Python dependencies include NumPy and Pillow; the native model
audit also needs the Minecraft 1.20.1 client JAR in the Fabric Loom cache.
Inspect each script's local runtime/cache paths before running on another host.
The archive is an evidence bundle, not a Minecraft mod or a world upgrade.

Verification: successful main, technical and native resource audits;
2,340 source fingerprints checked; catalogue totals and group members checked;
archive CRC checked. No new Gradle build or in-game smoke test was needed for
this documentation-only publication. Model equality does not prove identical
block behavior or collision. Near-geometry matches remain review candidates.
