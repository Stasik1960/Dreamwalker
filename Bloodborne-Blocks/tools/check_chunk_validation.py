"""Bytecode regression guard for the recorded spawn-promotion deadlock.

No Minecraft instance is created. Check the compiled callback's access boundary
and the pinned mapped Minecraft method that supplies nonblocking chunk reads.
"""
import argparse
import re
import subprocess
from pathlib import Path


def disassemble(javap, classpath, name):
    return subprocess.run([str(javap), '-c', '-p', '-classpath', str(classpath), name],
                          check=True, capture_output=True, text=True).stdout


def methods(text):
    result = {}
    current = None
    for line in text.splitlines():
        if re.match(r'^  \S.*\);$', line):
            current = line.strip()
            result[current] = []
        elif current:
            result[current].append(line)
    return {name:'\n'.join(lines) for name,lines in result.items()}


def check(javap, classes, minecraft):
    code = disassemble(javap, classes, 'dev.dreamwalker.bloodborneblocks.ArchitecturePartBlockEntity')
    bodies = methods(code)
    # isChunkLoaded() did NOT make a World#getBlockState() safe in CHUNK_LOAD:
    # the captured call entered Lithium getChunkBlocking before promotion finished.
    forbidden = re.compile(r'// (?:Interface)?Method net/minecraft/(?:world/World|world/BlockView|server/world/ServerWorld)\.(?:getBlockState|getBlockEntity|getChunk|isChunkLoaded):')
    assert not forbidden.search(code), 'Validation contains a potentially blocking world lookup'
    assert 'net/minecraft/server/world/ServerChunkManager.getWorldChunk:(II)' in code, 'No ready-only cross-chunk lookup'
    assert 'net/minecraft/world/chunk/WorldChunk.getBlockState:' in code, 'Root validation must still inspect real chunk data'
    removers = [name for name,body in bodies.items() if '.removeBlock:' in body]
    assert len(removers) == 1 and 'drainRemovals(' in removers[0], ('Mutation outside deferred tick cleanup', removers)
    for name, body in bodies.items():
        if 'drainRemovals:' in body:
            assert 'lambda$registerValidation$' in name, ('Unexpected synchronous cleanup entry point', name)
    assert 'ServerTickEvents.END_WORLD_TICK:' in code, 'Cleanup must run after chunk promotion'
    assert 'sipush        256' in bodies[removers[0]], 'Cleanup must retain its per-tick budget'

    manager = methods(disassemble(javap, minecraft, 'net.minecraft.server.world.ServerChunkManager'))
    ready = [body for name,body in manager.items() if name.endswith('getWorldChunk(int, int);')]
    assert len(ready) == 1, 'Pinned ready-chunk API not found'
    assert 'CompletableFuture.getNow:' in ready[0], 'Chunk API no longer polls its future without waiting'
    assert not re.search(r'CompletableFuture\.(?:join|get):|\.runTasks:|\.getChunkBlocking:', ready[0]), 'Chunk API waits for promotion'
    print('CHUNK VALIDATION CHECK PASSED: no blocking world reads, tick-only bounded cleanup, getNow lookup')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--javap', type=Path, required=True)
    p.add_argument('--classes', type=Path, default=Path('build/classes/java/main'))
    p.add_argument('--minecraft', type=Path, required=True)
    a = p.parse_args();check(a.javap, a.classes, a.minecraft)
