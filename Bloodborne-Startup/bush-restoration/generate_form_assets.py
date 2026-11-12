"""Generate registry metadata and model resources for fixed Bloodborne forms."""

import json


ASSETS = "assets/bloodborne_dw/"
DATA = "bloodborne_dw/"


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")


def read(archive, path):
    return json.loads(archive.read(path).decode("utf-8-sig"))


def path(identifier):
    return identifier.removeprefix("bloodborne_dw:")


def fixed_blockstate(document, locked):
    def matches(conditions):
        return all(key not in locked or locked[key] in value.split("|") for key, value in conditions.items())

    if "variants" in document:
        variants = {}
        for key, model in document["variants"].items():
            conditions = dict(token.split("=", 1) for token in key.split(",")) if key else {}
            if matches(conditions):
                remaining = ",".join(f"{prop}={value}" for prop, value in conditions.items() if prop not in locked)
                if remaining in variants:
                    raise ValueError(f"Overlapping model conditions after fixing {locked}")
                variants[remaining] = model
        if variants:
            return {"variants": variants}
    elif "multipart" in document:
        def simplify(conditions):
            if not matches({key: value for key, value in conditions.items() if key not in {"OR", "AND"}}):
                return None
            result = {key: value for key, value in conditions.items() if key not in locked and key not in {"OR", "AND"}}
            for op in ("OR", "AND"):
                if op not in conditions:
                    continue
                terms = [simplify(term) for term in conditions[op]]
                if op == "OR" and {} in terms:
                    continue
                if op == "AND" and None in terms:
                    return None
                terms = [term for term in terms if term is not None and term != {}]
                if terms:
                    result[op] = terms
                elif op == "OR":
                    return None
            return result

        parts = []
        for part in document["multipart"]:
            conditions = simplify(part.get("when", {}))
            if conditions is not None:
                parts.append({"when": conditions, "apply": part["apply"]} if conditions else {"apply": part["apply"]})
        if parts:
            return {"multipart": parts}
    raise ValueError(f"No block model for fixed properties {locked}")


def form_resources(release):
    def data(name):
        return read(release, DATA + name + ".json")

    catalogue = data("debug_catalogue")
    original_entries = {path(row["registryId"]): row for row in catalogue["entries"]}
    used_numbers = {row["temporaryId"] for row in catalogue["entries"] + catalogue.get("retiredEntries", [])}
    next_number = 94000
    resources = {}
    families = {}
    slabs = []
    translations = {lang: read(release, ASSETS + f"lang/{lang}.json") for lang in ("ru_ru", "en_us")}

    def add_item(source, alias, document, locked, name):
        nonlocal next_number
        blockstate = ASSETS + f"blockstates/{alias}.json"
        fixed = fixed_blockstate(document, locked)
        resources[blockstate] = encoded(fixed)
        model = ASSETS + f"models/item/{source}.json"
        if "type" in locked:
            # The inventory icon should depict the selected half or full model.
            target = fixed["variants"]["facing=north"]["model"]
            resources[ASSETS + f"models/item/{alias}.json"] = encoded({"parent": target})
        else:
            resources[ASSETS + f"models/item/{alias}.json"] = release.read(model)
        identifier = "bloodborne_dw:" + alias
        while str(next_number) in used_numbers:
            next_number += 1
        if next_number > 99999:
            raise ValueError("No free TEMP number for " + identifier)
        catalogue["entries"].append({"temporaryId": str(next_number), "finalId": None,
            "kind": "architecture", "name": name, "registryId": identifier, "aliases": [identifier]})
        if "type" in locked:
            for language in translations.values():
                language[f"block.bloodborne_dw.{alias}"] = f"{name} [{next_number}]"
        next_number += 1

    stairs = data("imported-stair-types")
    dynamic = {row["path"] for key in ("stairTypes", "ladderTypes") for row in stairs[key]}
    dynamic.update({"source_standard_lantern", "source_street_lamp_post", "source_shutter_window_surround"})
    assembly = data("imported-assembly-v126")
    assembly_poses = {}
    for row in assembly["recipes"]:
        assembly_poses.setdefault(path(row["primaryRegistryId"]), set()).add(row["installationPose"])
    dynamic.update(map(path, assembly["hiddenPartRedirects"]))
    dynamic.update(path(row["registryId"]) for row in data("imported-style-retirements")["retirements"])
    connections = {}
    for identifier, family in data("stone-fence-connections")["families"].items():
        poses = connections.setdefault(path(identifier), set())
        for group in family["groups"]:
            poses.update(group["poses"] + group.get("compatible", []) + [group.get("straight", -1)])
            poses.update(row["pose"] for row in group.get("wallStates", []))
    parts = {(path(row["registryId"]), row["variant"]) for row in data("imported-source-parts-v127")["parts"]}

    for row in sorted(data("remaining-catalogue")["objects"], key=lambda row: row["path"]):
        source = row["path"]
        original = original_entries.get(source)
        if source in dynamic or row.get("conversionOnly") or not original or "canonicalRegistryId" in original:
            continue
        if len(row["variants"]) < 2:
            continue
        # Generated wall/pane masks describe automatic connections. Mixed families also contain independent models.
        if source in connections and source.startswith("source_assembly_"):
            continue
        document = read(release, ASSETS + f"blockstates/{source}.json")
        for form in row["variants"]:
            pose = form["variant"]
            if ((source, pose) in parts or pose in connections.get(source, ())
                    or (source in assembly_poses and pose not in assembly_poses[source])):
                continue
            locked = {"variant": str(pose % 16), "art_bank": str(pose // 16)}
            add_item(source, f"{source}_form_{pose}", document, locked, f"{form['name']} · форма {pose}")
            families.setdefault(source, []).append(pose)

    for row in sorted(data("restored-material-slabs")["materials"], key=lambda row: row["id"]):
        source = path(row["id"])
        name = row.get("name", source).rsplit(" [", 1)[0]
        document = read(release, ASSETS + f"blockstates/{source}.json")
        for shape, suffix, label in (("bottom", "half", "нижняя плита"), ("top", "upper", "верхняя плита"),
                                     ("double", "full", "полный блок")):
            locked = {"type": shape}
            if shape == "double":
                locked["waterlogged"] = "false"
            add_item(source, f"{source}_{suffix}", document, locked, f"{name} · {label}")
        slabs.append(source)

    resources[DATA + "distinct-forms.json"] = encoded(families)
    resources[DATA + "distinct-slabs.json"] = encoded(slabs)
    print(f"Fixed forms: {sum(map(len, families.values()))} authored models + {len(slabs) * 3} slab forms")
    replacements = {DATA + "debug_catalogue.json": catalogue}
    replacements.update({ASSETS + f"lang/{lang}.json": entries for lang, entries in translations.items()})
    return resources, replacements
