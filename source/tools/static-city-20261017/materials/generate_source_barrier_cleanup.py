#!/usr/bin/env python3
"""Extract only proven original minecraft:barrier coordinates from the completed source plan."""
import argparse, gzip, hashlib, json, sqlite3, struct, zlib
from pathlib import Path

DECISION_ID = 223
EXPECTED = {
    "action": "KEEP",
    "previousPreparationReason": "USER_REQUEST_SYSTEM_OR_INVISIBLE_CARRIER",
    "reason": "PRESERVE_ORIGINAL_INVISIBLE_NATIVE_BARRIER_OR_LIGHT",
    "sourceState": "minecraft:barrier",
}

def sha(path):
    digest=hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda:stream.read(1024*1024),b""): digest.update(block)
    return digest.hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--source-plan",type=Path,required=True)
    parser.add_argument("--output-dir",type=Path,required=True)
    args=parser.parse_args()
    source=args.source_plan.resolve(strict=True); out=args.output_dir.resolve()
    if out.exists() and not out.is_dir(): raise ValueError("output-dir is not a directory")
    out.mkdir(parents=True,exist_ok=True)
    uri=f"file:{source.as_posix()}?mode=ro&immutable=1"
    db=sqlite3.connect(uri,uri=True)
    decision=json.loads(db.execute("SELECT data FROM decisions WHERE id=?",(DECISION_ID,)).fetchone()[0])
    if decision != EXPECTED: raise ValueError(f"decision {DECISION_ID} changed: {decision!r}")
    rows=[]
    for dim,cx,cz,sy,blob in db.execute("SELECT dimension,cx,cz,sy,decision_indices FROM sections ORDER BY dimension,cx,cz,sy"):
        raw=zlib.decompress(blob)
        if len(raw)!=8192: raise ValueError("invalid decision section")
        for index,(value,) in enumerate(struct.iter_unpack("<H",raw)):
            if value==DECISION_ID:
                rows.append({"dimension":dim,"position":[cx*16+(index&15),sy*16+(index>>8),cz*16+((index>>4)&15)]})
    db.close()
    if len(rows)!=14855: raise ValueError(f"expected 14855 source barriers, found {len(rows)}")
    phase=out/"source-barriers.jsonl.gz"
    with phase.open("wb") as raw:
        with gzip.GzipFile(filename="source-barriers.jsonl",mode="wb",fileobj=raw,mtime=0) as stream:
            for row in rows: stream.write((json.dumps(row,separators=(",",":"),sort_keys=True)+"\n").encode())
    manifest={"schemaVersion":1,"status":"PASS_PROVEN_SOURCE_BARRIERS","decisionId":DECISION_ID,
              "decision":EXPECTED,"count":len(rows),"sourcePlanPath":str(source),"sourcePlanSha256":sha(source),
              "phaseFile":phase.name,"phaseSha256":sha(phase),
              "coordinateOrder":"dimension,cx,cz,sy,index; index=y*256+z*16+x"}
    (out/"source-barrier-cleanup-manifest.json").write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(json.dumps(manifest,sort_keys=True))

if __name__=="__main__": main()
