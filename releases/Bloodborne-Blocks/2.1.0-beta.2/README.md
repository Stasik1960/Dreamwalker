> Superseded world copies: use the [recovered city delivery](../2.1.0-beta.2-city-recovery/README.md). The beta.2 JAR is unchanged.

# Bloodborne Blocks 2.1.0-beta.2 — city compatibility preview

**The city is still UNRESOLVED. Do not replace the live world with these
diagnostic copies.** The conservative copy retains 33 cells with missing
transient models; the aggressive copy retains 13. Their original IDs are
preserved, not replaced with air or invented geometry.

- `bloodborne-blocks-2.1.0-beta.2-mc1.20.1.jar`: Fabric 1.20.1 / Java 17,
  existing 49 production families plus compact historical city compatibility.
  Client and server must use the same version.
- `Bloodborne-City-Beta2-Conservative-UNRESOLVED.zip`: no forced logical
  replacements; exact cell-preserving compatibility mapping afterward.
- `Bloodborne-City-Beta2-Aggressive-UNRESOLVED.zip`: only the converter's
  previously proven forced replacements; no arbitrary foreign decor deletion.
- `Bloodborne-City-Beta2-Reports.zip`: full ledgers, independent checks,
  preservation, second passes, reference comparisons and missing-model evidence.
- `proof.json`: input/artifact checksums and verification results.

Both worlds use only the frozen latest MODDED backup. The vanilla world was
reference evidence only. Their 27.5 million compatibility palette changes do
not move blocks. Every chunk and the 170 nonterrain files passed preservation
checks; both second passes make zero changes and helper audits report zero
orphans. Production conversion ledgers equal beta.1, so their recorded
reference comparisons remain applicable.

Build/static/offline checks passed. Client and server gameplay testing was
not run, as requested. This is not a performance or graphical acceptance claim.

The supplied 1.2.1 JAR has no missing composite palette. Finishing the city
requires the lost generated models or an explicitly reviewed replacement
decision for the documented cells. See the source handoff at
`Bloodborne-Blocks/docs/city-compat/README.md`.
