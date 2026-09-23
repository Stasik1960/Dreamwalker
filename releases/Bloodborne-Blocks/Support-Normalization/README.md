# Contract V2 support-normalization checkpoint

- Runtime: `bloodborne-blocks-2.1.0-alpha.1-support-normalization-mc1.20.1.jar`.
- Fresh world ZIP: `support-plane-gallery-20260924.zip`.
- Integrity: `SHA256.json` contains byte sizes and SHA-256 digests.

Minecraft Java 1.20.1 / Fabric; pinned dependencies are unchanged. Replace the
previous Bloodborne Blocks JAR, do not install both copies. Unzip the gallery
into the actual instance's `saves`: `support-plane-gallery-20260924/level.dat`
must be directly inside its world folder. Previous worlds/JARs are preserved.

The gallery shows all 47 Contract V2 families / 740 states plus six BASE/ALT
proof specimens, including labelled hidden compatibility versions. Use its
`gallery-positions.json` for coordinates. The existing ALT starter resource pack
from `../Nightmare-QA-ALT/` remains compatible if testing alternate visuals.

This is a support/collision checkpoint, not new semantic review or city
conversion. Four wall-mount ambiguities remain unchanged. Server tests and
official world-summary loading passed; client visual acceptance is pending.

See [implementation and audit](../../../Bloodborne-Blocks/docs/SUPPORT-NORMALIZATION.md)
and [completed verification](../../../Bloodborne-Blocks/docs/support-checks/README.md).
