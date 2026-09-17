"""Check the packaged mixin namespace without starting Minecraft."""
import json
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as jar:
    assert jar.testzip() is None
    metadata = json.loads(jar.read('fabric.mod.json'))
    names = set(jar.namelist())
    for config_name in metadata.get('mixins', []):
        config = json.loads(jar.read(config_name))
        prefix = config['package'].replace('.', '/') + '/'
        declared = {prefix + name.replace('.', '/') + '.class'
                    for key in ('mixins', 'client', 'server') for name in config.get(key, [])}
        actual = {name for name in names if name.startswith(prefix) and name.endswith('.class')}
        assert actual == declared, (actual - declared, declared - actual)
        for entries in metadata['entrypoints'].values():
            for entry in entries:
                assert not entry.replace('.', '/').startswith(prefix), entry
        refmap = json.loads(jar.read(config['refmap']))
        assert any('DecorativeClimbMixin' in key for key in refmap['mappings'])
    print('PACKAGED MIXIN CHECK PASSED:', metadata['version'])