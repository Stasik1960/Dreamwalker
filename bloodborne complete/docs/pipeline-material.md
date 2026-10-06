# Bloodborne material pipeline

`tools/complete_material_pipeline.py` reads the v16 map audit and writes a fresh
`assets/bloodborne_dw` tree. IDs are assigned once by descending observed block
frequency, then lexical source name; air is excluded. The catalog stores input
SHA-256 values, bilingual names, base/alt visual paths, and a conservative
render-layer flag. ALT models inherit BASE, so a resource pack can replace either
textures or the complete model.

The generated blockstates preserve source variants/multipart, with BASE/ALT
selectors and per-ID alternative leaf wrappers. The
output `gallery-manifest.json` records stable platform IDs, bounds, default state,
gap and the manual editor protocol. World conversion is intentionally a separate
stage: it preserves block properties, biomes, block-entity NBT and unrelated
data, and fails on unknown Bloodborne entities. Complete implementation and
actual validation are described in PROCESS-RU.md and ../VALIDATION.md. Legacy
RP-only build_catalog.py/generate_public_resources.py are not steps in this
complete release build: the final committed resources are already complete.
