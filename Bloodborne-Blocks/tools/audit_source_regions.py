#!/usr/bin/env python3
"""Read-only integrity audit for every Anvil file in the supplied source ZIP."""

from __future__ import annotations

import hashlib
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path

from world_io import RegionFile


EXPECTED_SHA256 = "4353737d536677469d3b895e3515496ab64fab7b224e43428eb96e8c09724a51"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def category(name: str) -> str:
    parts = name.split("/")
    parent = parts[-2] if len(parts) >= 2 else ""
    return parent if parent in {"region", "entities", "poi"} else "other"


def audit(path: Path) -> dict:
    before = sha256(path)
    if before != EXPECTED_SHA256:
        raise ValueError(f"unexpected source SHA-256: {before}")
    files = Counter()
    chunks = Counter()
    with zipfile.ZipFile(path) as archive:
        entries = sorted((info for info in archive.infolist()
                          if info.filename.endswith(".mca") and "/" in info.filename),
                         key=lambda info: info.filename)
        for info in entries:
            kind = category(info.filename)
            data = archive.read(info)
            region = RegionFile(data)
            files[kind] += 1
            for chunk in region.chunks():
                chunk.nbt()  # decompression and full NBT decoding are intentional.
                chunks[kind] += 1
    after = sha256(path)
    if after != before:
        raise RuntimeError("source archive changed during read-only audit")
    return {
        "format": "bloodborne-source-region-audit-v2",
        "source": {"path": str(path), "sha256Before": before, "sha256After": after},
        "regionFiles": {"total": sum(files.values()), "byKind": dict(sorted(files.items()))},
        "decodedChunks": {"total": sum(chunks.values()), "byKind": dict(sorted(chunks.items()))},
        "result": "ok",
    }


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "reference-inputs" / "source-world.zip"
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else root / "docs" / "source-region-audit-v2.json"
    result = audit(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
