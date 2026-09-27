# Bloodborne Blocks

Minecraft 1.20.1 / Fabric architectural mod. The declared build version is
`2.1.0-rc.1`; this is a verification candidate, **not** a release recommendation.

Current release evidence and gates are maintained in
[RELEASE-STATUS.md](docs/RELEASE-STATUS.md). The current conclusion is
**RELEASE CANDIDATE BLOCKED** and **RELEASE_READY FAIL**. There is no known
certified stable commit and no approved current-world package.

## Scope

The baseline runtime contains 49 logical families and 2,914 compatibility
blocks. Whole-owner repair changes belong to the separate repair line. It must be installed on
both server and clients. Do not treat archived beta artifacts as a compatible
world upgrade path.

## Development baseline

The selected runtime baseline is `origin/main` at
`b086e88929a971a2abd184629b3e7a59225304e5`, also named
`origin/archive/beta3-grid-fragmentation-broken`. It is an origin point for the
candidate verification only. The candidate has a new version and JAR identity;
it is not presented as the previously published beta.3 binary.

Repair checkpoint `3d07` and local catalog work in progress are separate and
unmerged. They do not change the baseline or establish release readiness.

## Where to look

- [Release status](docs/RELEASE-STATUS.md)
- [Known issues](docs/known-issues/README.md)
- [Current handoff](HANDOFF.md)
- [Historical documentation](docs/history/README.md)

Build and test commands remain in the Gradle build file. Do not publish a JAR,
world or resource pack until the release status records passing required gates.
