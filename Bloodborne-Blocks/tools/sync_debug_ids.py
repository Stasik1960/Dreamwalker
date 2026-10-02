"""Append stable, display-only numeric IDs for registered Bloodborne blocks."""
import json
from pathlib import Path
import re


ROOT=Path(__file__).resolve().parents[1]
RESOURCE=ROOT/"src/main/resources/bloodborne_blocks/debug-ids.json"


def definitions(path):
    return [entry["id"] for entry in json.loads(path.read_text(encoding="utf-8"))["blocks"]]


def main():
    current=json.loads(RESOURCE.read_text(encoding="utf-8")) if RESOURCE.exists() else {"schemaVersion":1,"ids":{},"retiredIds":[]}
    if current.get("schemaVersion")!=1 or not isinstance(current.get("ids"),dict) or not isinstance(current.get("retiredIds"),list):
        raise SystemExit("debug-ids.json must use schemaVersion 1 with ids and retiredIds")
    ids=current["ids"]
    retired=set(current["retiredIds"])
    if len(retired)!=len(current["retiredIds"]):
        raise SystemExit("retired numeric debug IDs must be unique")
    values=list(ids.values())+list(retired)
    if len(values)!=len(set(values)) or any(not isinstance(value,str) or not re.fullmatch(r"[0-9]{5}",value) or value=="00000" for value in values):
        raise SystemExit("numeric debug IDs must be unique five-digit values from 00001")
    logical=definitions(ROOT/"src/main/resources/bloodborne_blocks/logical/definitions.json")
    city=definitions(ROOT/"src/main/resources/bloodborne_blocks/city/definitions.json")
    expected={"architecture_part",*logical,*city}
    if len(expected)!=len(logical)+len(city)+1:
        raise SystemExit("registered block definition IDs must be unique")
    for key in set(ids)-expected:
        retired.add(ids.pop(key))
    used=set(ids.values())|retired
    for key in sorted(expected-set(ids)):
        for number in range(1,100000):
            candidate=f"{number:05d}"
            if candidate not in used:
                ids[key]=candidate
                used.add(candidate)
                break
        else:
            raise SystemExit("numeric debug ID capacity exhausted")
    RESOURCE.write_text(json.dumps({"schemaVersion":1,"ids":dict(sorted(ids.items())),"retiredIds":sorted(retired)},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")


if __name__=="__main__":
    main()
