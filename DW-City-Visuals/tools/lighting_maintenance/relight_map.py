"""Bake selected Bloodborne lighting on a scratch server and import caches only."""
from argparse import ArgumentParser
from collections import defaultdict
import json
import os
from pathlib import Path
import re
import shutil
import socket
import stat
import struct
import subprocess
import sys
import time
from zipfile import ZipFile

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS / "map_io"))
from world_io import RegionFile, Tag, compound, invalidate_chunk_lighting, lighting_cache_errors, _dimension_for_region

KNOWN_LEVELS = {"minecraft:full", "full"}


def chunk_index(world):
    paths = defaultdict(dict)
    for file in sorted(world.glob("**/region/r.*.*.mca")):
        dimension = _dimension_for_region(world, file)
        rx, rz = map(int, file.stem.split(".")[1:])
        region = RegionFile.open(file)
        for c in region.chunks():
            root = compound(c.nbt().root)
            if root["Status"].value in KNOWN_LEVELS:
                paths[dimension][(rx*32+c.x, rz*32+c.z)] = file
    return paths


def plan(world, audit, probe, edited, all_full):
    if probe:
        # Smoke test the arch, an orphan cell, a luminous lantern and an old-world seam.
        index = {key: {tuple(pos): None for pos in value["full_chunk_positions"]}
                 for key, value in audit.items()}
        targets = {
            "minecraft:overworld": {(6,-49),(6,-48),(2,-70),(2,-69),(3,-69),(5,-70),(-8,-48),(-7,-48),(10,-61)},
            "bloodborne_dw:bloodborne_old": {(-16,-18),(-15,-18),(1,8),(2,8)},
        }
        return index, targets

    index = chunk_index(world)
    if all_full:
        return index, {key: set(columns) for key, columns in index.items()}
    targets = {}
    for dimension, columns in index.items():
        observed = audit[dimension]
        problem = {tuple(c) for row in observed["air_skylight_jumps"] for c in row["chunks"]}
        problem |= {tuple(c) for c in observed["invalidated_light_chunks"]}
        problem |= {(x//16,z//16) for (x,_,z), *_ in observed["unsupported_block_light_maxima"]}
        problem |= set(edited.get(dimension, ()))
        for x,z in ((6,-49),(6,-48),(10,-61),(1,-69)):
            if dimension == "minecraft:overworld":
                problem.add((x,z))
        # One halo lets the changed cache meet a valid light gradient at its edge.
        expanded = {(x+dx,z+dz) for x,z in problem for dx in (-1,0,1) for dz in (-1,0,1)}
        targets[dimension] = set(columns) & expanded
    return index, targets


def read_plan(path):
    targets = defaultdict(set)
    for row in path.read_text(encoding="utf-8").splitlines():
        action, dim, x, z = row.split("\t")
        if action == "target":
            targets[dim].add((int(x),int(z)))
    return targets


def checked_copy(source, target):
    for part, dirs, names in os.walk(source, followlinks=False):
        for name in dirs + names:
            if (Path(part) / name).lstat().st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
                raise ValueError("Unexpected reparse point "+str(Path(part)/name))
    shutil.copytree(source, target, ignore=shutil.ignore_patterns("session.lock"))


def stage_server(world, run, targets, index, opts):
    run.mkdir()
    checked_copy(world, run/"bloodborne")
    (run/"mods").mkdir()
    (run/"lighting-disposable.marker").touch()
    (run/"options.txt").write_text("soundCategory_master:0.0\n",encoding="ascii")
    (run/"eula.txt").write_text("eula=true\n",encoding="ascii")
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    (run/"server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nlevel-name=bloodborne\nonline-mode=false\n"
        "view-distance=3\nsimulation-distance=3\nmax-tick-time=-1\nenable-status=false\n",
        encoding="ascii")
    with (run / "lighting-plan.tsv").open("x",encoding="utf-8",newline="\n") as output:
        for dim, columns in sorted(index.items()):
            for x,z in sorted(columns):
                output.write(f"existing\t{dim}\t{x}\t{z}\n")
        for dim, columns in sorted(targets.items()):
            for x,z in sorted(columns):
                output.write(f"target\t{dim}\t{x}\t{z}\n")
    for jar in (opts.dreamwalker, opts.tool):
        shutil.copy2(jar, run/"mods")
    mods = {
        "architectury", "cloth-config", "customnpcs", "cpm", "cnpc_cpm", "dawnoftimebuilder", "decocraft",
        "decorative_blocks", "fabric-api", "farmersdelight", "furnish", "geckolib", "handcrafted",
        "immersive_melodies", "modernfix", "poolbilliards", "resourcefullib", "yuushya",
    }
    for jar in opts.mods.glob("*.jar"):
        with ZipFile(jar) as archive:
            if "fabric.mod.json" not in archive.namelist():
                continue
            metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] in mods:
            shutil.copy2(jar, run/"mods")
    required = ["lighting.done", "registry.tsv"]
    if opts.fabric_classpath:
        cp = [str(path.resolve()) for path in opts.fabric_classpath]
    else:
        args = opts.fabric_client_args.read_text(encoding="utf-8-sig").splitlines()
        source = args[args.index('"-cp"')+1].strip('"').split(";")
        cp = [p for p in source if re.search(r"/0\.19\.3\.jar$|/libraries/(net/fabricmc|org/ow2/asm)/", p)]
    if not cp or not all(Path(p).is_file() for p in cp):
        raise ValueError("Incomplete Fabric server classpath")
    args = [opts.java.resolve(), "-Xms256m", f"-Xmx{opts.heap}m", "-XX:ActiveProcessorCount=4",
            f"-Dfabric.gameJarPath={opts.vanilla.resolve()}", "-cp", os.pathsep.join(cp),
            "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui"]
    if opts.after_pid is not None:
        print(f"Waiting for client process {opts.after_pid} before using Java memory",flush=True)
        if os.name == "nt":
            import ctypes
            kernel = ctypes.windll.kernel32
            kernel.OpenProcess.restype = ctypes.c_void_p
            handle = kernel.OpenProcess(0x00100000, False, opts.after_pid)
            if handle:
                try:
                    wait = kernel.WaitForSingleObject(ctypes.c_void_p(handle), 900000)
                    if wait != 0:
                        raise TimeoutError("Client process was not released")
                finally:
                    kernel.CloseHandle(ctypes.c_void_p(handle))
        else:
            deadline = time.monotonic() + 900
            while Path(f"/proc/{opts.after_pid}").exists() and time.monotonic() < deadline:
                time.sleep(1)
            if Path(f"/proc/{opts.after_pid}").exists():
                raise TimeoutError("Client process was not released")
    print(f"Launching scratch dedicated server {run}: {sum(map(len,targets.values()))} targets",flush=True)
    with (run/"server-console.log").open("wb") as output:
        server = subprocess.Popen(args,cwd=run,stdout=output,stderr=subprocess.STDOUT,
                                  creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
        try:
            code = server.wait(timeout=2400)
        except BaseException:
            if server.poll() is None:
                server.terminate()
                server.wait(timeout=15)
            raise
    if any(not (run/f).is_file() for f in required):
        raise RuntimeError("Lighting job did not complete. See "+str(run/"server-console.log"))
    if code:
        print(f"Scratch server shutdown returned {code}; validate every independent light blob before use.",
              flush=True)
    print((run/"lighting.done").read_text().strip(),flush=True)


def read_baked(path):
    with path.open("rb") as reader:
        bottom, top = struct.unpack(">ii",reader.read(8))
        if (bottom,top) != (-5,21):
            raise ValueError(f"Unexpected section range {bottom}..{top} at {path}")
        layers = {}
        for y in range(bottom, top):
            section = {}
            for key in ("SkyLight","BlockLight"):
                present = reader.read(1)
                if present not in (b"\x00",b"\x01"):
                    raise ValueError(f"Invalid section flag at {path} Y={y}")
                if present == b"\x01":
                    data = reader.read(2048)
                    if len(data) != 2048:
                        raise ValueError(f"Truncated light cache {path} Y={y}")
                    section[key] = Tag(7,data)
            if section:
                layers[y] = section
        if reader.read(1):
            raise ValueError("Trailing bytes in "+str(path))
    return layers


def preserve_fields(root):
    clean = compound(root).copy()
    clean.pop("isLightOn",None)
    clean.pop("starlight.light_version",None)
    sections = []
    light_keys = {"SkyLight","BlockLight","starlight.blocklight_state","starlight.skylight_state"}
    for part in clean["sections"].value:
        values = {k: v for k, v in compound(part).items() if k not in light_keys}
        if values.keys() != {"Y"}:
            sections.append(Tag(10,values))
    clean["sections"] = Tag(9,sections,10)
    return clean


def transplant(world, run, output):
    targets = read_plan(run/"lighting-plan.tsv")
    expected = sum(map(len,targets.values()))
    if (run/"lighting.done").read_text(encoding="utf-8").strip() != f"{expected} chunks relit":
        raise ValueError("Baker completion marker does not match plan")
    output.mkdir()
    existing = chunk_index(world)
    report = defaultdict(dict)
    by_file = defaultdict(list)
    for dim, coords in targets.items():
        for x,z in coords:
            by_file[existing[dim][x,z]].append((dim,x,z))
    actual = 0
    for file, rows in sorted(by_file.items()):
        original = RegionFile.open(file)
        region = RegionFile(original.to_bytes())
        modified = {}
        for dim,x,z in rows:
            old = original.get_chunk(x%32,z%32)
            nbt = old.nbt()
            root = compound(nbt.root)
            original_fields = preserve_fields(nbt.root)
            invalidate_chunk_lighting(root)
            cache = read_baked(run/"relit"/dim.replace(":","_")/f"{x}.{z}.bin")
            root["isLightOn"] = Tag(1,1)
            sections = root["sections"].value
            active = {compound(section)["Y"].value: section for section in sections}
            for y, values in cache.items():
                if y not in active:
                    active[y] = Tag(10,{"Y":Tag(1,y)})
                    sections.append(active[y])
                compound(active[y]).update(values)
            if original_fields != preserve_fields(nbt.root) or lighting_cache_errors(root):
                raise ValueError(f"Non-light NBT or invalid arrays at {dim} {(x,z)}")
            region.set_chunk(x%32,z%32,nbt, compression=old.compression, timestamp=old.timestamp)
            modified[x%32,z%32] = nbt
            actual += 1
        file_out = output / file.relative_to(world)
        file_out.parent.mkdir(parents=True,exist_ok=True)
        region.save(file_out)
        verified = RegionFile.open(file_out)
        for old in original.chunks():
            candidate = verified.get_chunk(old.x,old.z)
            updated = modified.get((old.x,old.z))
            if updated is None:
                if candidate != old:
                    raise ValueError("Unrequested change to chunk "+str((old.x,old.z)))
            elif candidate.nbt() != updated or candidate.timestamp != old.timestamp:
                raise ValueError("Light cache round-trip mismatch at "+str((old.x,old.z)))
        report[dim][str(file_out.relative_to(output))] = len(rows)
    if expected != actual:
        raise ValueError("Missing relit chunks")
    (output/"relight-report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(f"Verified {actual} chunks in {len(by_file)} staged region replacements, only light NBT changed.",flush=True)


def main():
    cli = ArgumentParser(description=__doc__)
    cli.add_argument("world", type=Path)
    cli.add_argument("run", type=Path)
    cli.add_argument("--audit", type=Path)
    cli.add_argument("--probe", action="store_true")
    cli.add_argument("--all-full", action="store_true", help="Bake every saved FULL chunk")
    cli.add_argument("--edited-chunks", type=Path)
    cli.add_argument("--transplant",type=Path)
    for name in ("mods", "dreamwalker", "tool", "java", "vanilla", "fabric-client-args"):
        cli.add_argument("--"+name, type=Path)
    cli.add_argument("--fabric-classpath", type=Path, nargs="+")
    cli.add_argument("--heap", type=int, default=3750)
    cli.add_argument("--after-pid", type=int)
    opt = cli.parse_args()
    if opt.transplant:
        transplant(opt.world.resolve(),opt.run.resolve(),opt.transplant.resolve())
        return
    if opt.audit is None:
        cli.error("--audit is required for a bake")
    for name in ("mods", "dreamwalker", "tool", "java", "vanilla"):
        if getattr(opt,name,None) is None:
            cli.error("--"+name.replace("_","-")+" is required for a bake")
    if opt.fabric_classpath is None and opt.fabric_client_args is None:
        cli.error("--fabric-classpath or --fabric-client-args is required for a bake")
    edited = {}
    if opt.edited_chunks is not None:
        for dim, columns in json.loads(opt.edited_chunks.read_text(encoding="utf-8")).items():
            edited[dim] = [tuple(p) for p in columns]
    idx, targets = plan(opt.world, json.loads(opt.audit.read_text(encoding="utf-8")),
                       opt.probe, edited, opt.all_full)
    stage_server(opt.world.resolve(), opt.run.resolve(), targets, idx, opt)


if __name__ == "__main__":
    main()
