# Changelog

## 2.1.0-rc.1 — verification candidate (not released)

- Select runtime baseline `b086e88929a971a2abd184629b3e7a59225304e5` and give
  the rebuilt artifact its own version; equivalence to the published beta.3
  binary is not asserted. No repair runtime changes are included.
- CI fetches the history required by grid checks and installs pinned NumPy and
  Pillow dependencies before check/build/GameTests/version/package checks.
- Production release remains blocked pending compatibility and world/runtime
  gates; see [release status](docs/RELEASE-STATUS.md).

## 2.1.0-beta.3 — audit baseline

- `VERSION` is aligned with the existing Gradle version: `2.1.0-beta.3`.
- Documentation now identifies the selected audit/source baseline and separates
  it from unmerged repair and catalog work.
- This entry does **not** announce a release. Current status is RELEASE
  CANDIDATE BLOCKED / RELEASE_READY FAIL; no certified stable commit is known.

Earlier changelog text is retained verbatim in
[docs/history/main-b086e8892/CHANGELOG.pre-audit.md](docs/history/main-b086e8892/CHANGELOG.pre-audit.md).
