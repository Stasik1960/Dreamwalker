"""Give zero-volume legacy block model elements a 0.5-pixel thickness."""
import argparse
import copy
import json
import subprocess
from pathlib import Path

NAMESPACE = "bloodborne_blocks"
AXIS_FACES = ((0, "west", "east"), (1, "down", "up"), (2, "north", "south"))


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def model_path(root, ref):
    if not isinstance(ref, str):
        return None
    namespace, sep, name = ref.partition(":")
    if not sep:
        namespace, name = NAMESPACE, ref
    if namespace != NAMESPACE or not name.startswith("block/"):
        return None
    path = root / "models" / (name + ".json")
    return path if path.is_file() else None


def refs(value):
    if isinstance(value, dict):
        if "model" in value:
            yield value["model"]
        for item in value.values():
            yield from refs(item)
    elif isinstance(value, list):
        for item in value:
            yield from refs(item)


def collect_models(root, ids):
    found, pending = set(), []
    for block_id in ids:
        state = root / "blockstates" / (block_id + ".json")
        if state.is_file():
            pending.extend(refs(read_json(state)))
    while pending:
        ref = pending.pop()
        path = model_path(root, ref)
        if path is None or path in found:
            continue
        found.add(path)
        data = read_json(path)
        parent = data.get("parent")
        if parent:
            pending.append(parent)
    return found


def head_zero_indices(path, git_root):
    rel = path.relative_to(git_root).as_posix()
    raw = subprocess.check_output(["git", "show", "HEAD:" + rel], cwd=git_root)
    original = json.loads(raw)
    return {i for i, e in enumerate(original.get("elements", []))
            if e.get("from", [None] * 3) != e.get("to", [None] * 3)
            and any(e["from"][a] == e["to"][a] for a in range(3))}


def extrude(data, zero_indices):
    changed = 0
    for index, element in enumerate(data.get("elements", [])):
        if index not in zero_indices:
            continue
        coords = element.get("from"), element.get("to")
        if not all(isinstance(c, list) and len(c) == 3 for c in coords):
            continue
        for axis, low_face, high_face in AXIS_FACES:
            value = element["from"][axis]
            if element["from"][axis] == element["to"][axis]:
                low = max(-16, min(value - .25, 31.5))
                element["from"][axis], element["to"][axis] = low, low + .5
            faces = element.setdefault("faces", {})
            source = next((f for f in faces.values() if isinstance(f, dict) and "uv" in f), None)
            if isinstance(source, dict):
                for face_name in (low_face, high_face):
                    if face_name not in faces:
                        new_face = copy.deepcopy(source)
                        new_face.pop("cullface", None)
                        faces[face_name] = new_face
            changed += 1
    return changed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("src/main/resources/assets/bloodborne_blocks"))
    parser.add_argument("--ids", type=Path, default=Path("src/main/resources/bloodborne_blocks/v2/legacy-creative.json"))
    args = parser.parse_args()
    root, ids_path = args.root.resolve(), args.ids.resolve()
    git_root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], cwd=root, text=True).strip())
    ids = read_json(ids_path)
    paths = collect_models(root, ids)
    files, elements = 0, 0
    for path in sorted(paths):
        data = read_json(path)
        before = copy.deepcopy(data)
        count = extrude(data, head_zero_indices(path, git_root))
        if data != before:
            path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
            files += 1
            elements += count
    print(f"models={len(paths)} changed_files={files} extruded_elements={elements}")


if __name__ == "__main__":
    main()
