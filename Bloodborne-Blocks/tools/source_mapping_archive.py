"""Offline source evidence; never loaded by the Fabric runtime registry."""
import gzip
import json
from functools import lru_cache
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

@lru_cache(None)
def archive(root=ROOT):
    return json.loads(gzip.decompress((Path(root)/'docs/pre-production-source-mapping.json.gz').read_bytes()))

def mapping(name,root=ROOT):
    path=Path(root)/'src/main/resources/bloodborne_blocks'/name
    if path.exists():return json.loads(path.read_text(encoding='utf-8-sig'))
    return archive(Path(root))[name]
