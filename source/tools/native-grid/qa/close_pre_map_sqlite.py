import json, sqlite3
from pathlib import Path
root = Path(r"C:\Users\vakir\.codex\worktrees\3f07\DW-native-grid-city-20261022\research")
relative = ["catalog-state.sqlite", "fragments.sqlite", "native-grid-plan/plan.sqlite", "compiler-checks/fragments.sqlite", "compiler-checks-v2/fragments.sqlite", "compiler-checks-v4/fragments.sqlite", "compiler-checks-v5/fragments.sqlite"]
rows = []
for name in relative:
    path = root / name
    before = path.stat()
    wal = path.with_name(path.name + "-wal")
    assert wal.is_file() and wal.stat().st_size == 0, str(wal)
    assert not path.with_name(path.name + "-journal").exists()
    db = sqlite3.connect(path.as_uri() + "?mode=rw", uri=True, timeout=10)
    try:
        result = list(db.execute("PRAGMA wal_checkpoint(TRUNCATE)"))
        assert result == [(0, 0, 0)], result
    finally:
        db.close()
    after = path.stat()
    assert (before.st_size, before.st_mtime_ns) == (after.st_size, after.st_mtime_ns), str(path)
    assert not wal.exists() and not path.with_name(path.name + "-shm").exists(), str(path)
    rows.append({"path":str(path), "emptyWalCheckpoint":result, "mainBytesAndMtimeUnchanged":True})
receipt={"status":"PASS_CLOSED_EMPTY_WAL_SQLITE_NO_MAIN_FILE_CHANGES","databases":rows}
out=Path(r"C:\Users\vakir\.codex\worktrees\3f07\DW-native-grid-pre-map-freeze-20261024") / "sqlite-quiescence.json"
out.write_text(json.dumps(receipt,indent=2)+"\n",encoding="utf-8")
print(json.dumps(receipt))
