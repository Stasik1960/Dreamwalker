"""Set Creative and cheats in a NEW saved V10 fixture before its first client entry."""
import argparse, hashlib, json
from pathlib import Path
from world_io import read_nbt, write_nbt, compound, Tag, TAG_INT, TAG_BYTE

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--world', type=Path, required=True)
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    world = a.world.resolve()
    assert world.is_relative_to((ROOT/'build').resolve())
    assert 'v10' in world.as_posix().lower(), 'Never patch an original or V9 world'
    assert not a.report.exists(), 'Earlier proof must not be replaced'
    files = [world/'level.dat', *sorted((world/'playerdata').glob('*.dat'))]
    rows = []
    for f in files:
        document = read_nbt(f)
        root = compound(document.root)
        data = compound(root['Data']) if f.name == 'level.dat' else root
        before = {'sha256': hashlib.sha256(f.read_bytes()).hexdigest()}
        changes = {}
        if f.name == 'level.dat':
            assert str(data['LevelName'].value) == 'isolated-smoke-world'
            for key, value in [('GameType', Tag(TAG_INT,1)), ('allowCommands', Tag(TAG_BYTE,1))]:
                changes[key] = {'before': int(data.get(key, Tag(TAG_INT,0)).value), 'after': int(value.value)}
                data[key] = value
            player = compound(data['Player']) if 'Player' in data else None
            if player is not None:
                changes['Player.playerGameType'] = {'before': int(player.get('playerGameType', Tag(TAG_INT,0)).value), 'after': 1}
                player['playerGameType'] = Tag(TAG_INT,1)
        else:
            changes['playerGameType'] = {'before': int(data.get('playerGameType', Tag(TAG_INT,0)).value), 'after': 1}
            data['playerGameType'] = Tag(TAG_INT,1)
        write_nbt(f, document)
        reread = compound(read_nbt(f).root)
        actual = compound(reread['Data']) if f.name == 'level.dat' else reread
        if f.name == 'level.dat':
            assert int(actual['GameType'].value) == 1 and int(actual['allowCommands'].value) == 1
            if 'Player' in actual: assert int(compound(actual['Player'])['playerGameType'].value) == 1
        else: assert int(actual['playerGameType'].value) == 1
        rows.append({'path': str(f), **before, 'changes': changes,
                     'afterSha256': hashlib.sha256(f.read_bytes()).hexdigest()})
    result = {'schema': 'dreamwalker-v10-before-first-entry-world-settings-v1',
              'status': 'PASS_SAVED_WORLD_CREATIVE_CHEATS_ACTUAL_FIRST_CLIENT_ENTRY_PENDING',
              'scope': 'Explicit new isolated V10 world only; original/user/V9 worlds untouched',
              'world': str(world), 'files': rows, 'actualFirstClientEntry': 'PENDING'}
    a.report.parent.mkdir(parents=True, exist_ok=True)
    a.report.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(json.dumps({'status': result['status'], 'world': str(world), 'files': len(files)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
