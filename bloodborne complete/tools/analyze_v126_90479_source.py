"""Readonly focused source adjacency for the explicit90479 ->90001 request."""
from __future__ import annotations
import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    cache = ROOT / "build/v125-original-map.sqlite"
    con = sqlite3.connect(cache.as_uri() + "?mode=ro", uri=True)
    apps = [(i, json.loads(v)) for i, v in con.execute("SELECT id,apps FROM applications WHERE apps LIKE '%dark_oak_trapdoor_top%' OR apps LIKE '%aca_door_%'")]
    source = []
    totals = {}
    for app_id, applications in apps:
        totals[str(app_id)] = {"applications": applications, "count": con.execute("SELECT count(*) FROM cells WHERE applications=?", (app_id,)).fetchone()[0]}
        if not any(a["model"] == "minecraft:block/dark_oak_trapdoor_top" for a in applications):
            continue
        for dimension, x, y, z, state_id in con.execute("SELECT dimension,x,y,z,state FROM cells WHERE applications=?", (app_id,)):
            neighbors = []
            for nx, ny, nz, app_text, state_text in con.execute("SELECT c.x,c.y,c.z,a.apps,s.state FROM cells c JOIN applications a ON a.id=c.applications JOIN states s ON s.id=c.state WHERE c.dimension=? AND c.x BETWEEN ? AND ? AND c.y BETWEEN ? AND ? AND c.z BETWEEN ? AND ?", (dimension, x-2, x+2, y-6, y+6, z-2, z+2)):
                selected = json.loads(app_text)
                if any("aca_door_" in a["model"] or "dark_oak_trapdoor_" in a["model"] for a in selected):
                    neighbors.append({"position": [nx, ny, nz], "relative": [nx-x, ny-y, nz-z], "applications": selected, "state": state_text})
            source.append({"dimension": dimension, "position": [x, y, z], "applications": applications, "state": con.execute("SELECT state FROM states WHERE id=?", (state_id,)).fetchone()[0], "nearbyDoorSourceCells": neighbors})
    con.close()
    result = {"status": "READ_ONLY_SOURCE_ADJACENCY_NOT_A_MIGRATION_RULE", "sourceArchiveNotModified": True, "cache": str(cache), "applicationTotals": totals, "90479SourceCells": source}
    out = ROOT / "docs/v12.6/assemblies/90479-source-adjacency.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "cells90479": len(source), "cellsWithNearbyAcaDoor": sum(any(any("aca_door_" in a["model"] for a in n["applications"]) for n in c["nearbyDoorSourceCells"]) for c in source), "output": str(out)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
