"""Verify the complete prepared Gradle resource tree without starting Minecraft.

Useful for a verification/build retry with -x processResources after the copy
task completed in an earlier run. Never use that flag unless this check passes
and resource inputs stay unchanged through packaging.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def inventory(root: Path) -> dict[str, Path]:
    if not root.is_dir():
        raise AssertionError(f"Missing resource directory: {root}")
    return {path.relative_to(root).as_posix(): path for path in root.rglob("*") if path.is_file()}


def prune_stale_logical_models(resource_root: Path, staged_root: Path) -> list[str]:
    """Remove only obsolete hash-named meshes from an explicit build tree."""
    for directory in (staged_root, *staged_root.parents):
        if directory.is_symlink() or (hasattr(directory, 'is_junction') and directory.is_junction()):
            raise AssertionError('Staging path may not traverse links')
    source, staged = inventory(resource_root), inventory(staged_root)
    extras = sorted(set(staged) - set(source))
    prefix = 'assets/bloodborne_blocks/models/block/logical/'
    folder = (staged_root / prefix).resolve()
    if resource_root.resolve() == staged_root.resolve() or resource_root.resolve() in staged_root.resolve().parents:
        raise AssertionError('Staging must be separate from source resources')
    targets = []
    for name in extras:
        path = staged[name]
        if (not re.fullmatch(re.escape(prefix) + r'o_[0-9a-f]{20}\.json', name)
                or not path.is_file() or path.is_symlink() or path.resolve().parent != folder
                or folder != (staged_root.resolve() / prefix)):
            raise AssertionError('Unexpected staged file; no files pruned: ' + name)
        targets.append(path)
    # Validate the entire set before removing anything. All these files are
    # derived output absent from source; never touch source assets or worlds.
    for path in targets:
        path.unlink()
    return extras


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repair-staging", action="store_true",
                        help="Copy missing/different inputs only into build/resources/main, then verify everything")
    parser.add_argument("--prune-stale-logical-models", action="store_true",
                        help="Remove obsolete hash-named logical meshes only from build/resources/main, then verify")
    args = parser.parse_args()
    if args.prune_stale_logical_models:
        removed = prune_stale_logical_models(ROOT / 'src/main/resources', ROOT / 'build/resources/main')
        print(json.dumps({'staleGeneratedModelsRemoved': removed}), flush=True)
    source = inventory(ROOT / "src/main/resources")
    staged = inventory(ROOT / "build/resources/main")
    extras = set(staged) - set(source)
    if args.repair_staging:
        if extras:
            raise AssertionError(f"Unexpected staged files; refusing automatic deletion: {sorted(extras)[:8]}")
        destination = ROOT / "build/resources/main"

        def prepare(name):
            data = source[name].read_bytes()
            target = destination / name
            if target.is_file() and target.read_bytes() == data:
                return 0
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            return 1

        # Build output only: no generator or source-world mutation is involved.
        with ThreadPoolExecutor(max_workers=8) as executor:
            copied = sum(executor.map(prepare, source))
        print(json.dumps({"stagedCopies": copied}), flush=True)
        staged = inventory(destination)
    if set(source) != set(staged):
        raise AssertionError(f"Resource file set differs: missing={sorted(set(source) - set(staged))[:8]}, extra={sorted(set(staged) - set(source))[:8]}")

    def verify(name):
        left, right = source[name].read_bytes(), staged[name].read_bytes()
        if left != right:
            raise AssertionError(f"Staged resource differs: {name}")
        return name, hashlib.sha256(left).hexdigest(), len(left)

    with ThreadPoolExecutor(max_workers=8) as executor:
        entries = sorted(executor.map(verify, source))
    manifest = hashlib.sha256("".join(f"{name}\0{digest}\n" for name, digest, _ in entries).encode()).hexdigest()
    print(json.dumps({"ok": True, "files": len(entries), "bytes": sum(size for _, _, size in entries),
                      "manifestSha256": manifest}))


if __name__ == "__main__":
    main()
