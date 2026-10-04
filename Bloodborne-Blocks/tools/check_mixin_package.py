"""Check the packaged mixin namespace without starting Minecraft."""
import json
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as jar:
    assert jar.testzip() is None
    metadata = json.loads(jar.read('fabric.mod.json'))
    names = set(jar.namelist())
    declared_by_package = {}
    configs = []
    for config_name in metadata.get('mixins', []):
        config = json.loads(jar.read(config_name))
        prefix = config['package'].replace('.', '/') + '/'
        declared = {prefix + name.replace('.', '/') + '.class'
                    for key in ('mixins', 'client', 'server') for name in config.get(key, [])}
        declared_by_package.setdefault(prefix, set()).update(declared)
        configs.append(config)
        for entries in metadata['entrypoints'].values():
            for entry in entries:
                assert not entry.replace('.', '/').startswith(prefix), entry
        if 'refmap' in config:
            refmap = json.loads(jar.read(config['refmap']))
            assert refmap['mappings'], config['refmap']
    for prefix, declared in declared_by_package.items():
        actual = {name for name in names if name.startswith(prefix) and name.endswith('.class')
                  and '$' not in name.rsplit('/', 1)[-1]}
        assert actual == declared, (actual - declared, declared - actual)
        nested = {name for name in names if name.startswith(prefix) and name.endswith('.class')
                  and '$' in name.rsplit('/', 1)[-1]}
        assert all(name.split('$', 1)[0] + '.class' in declared for name in nested), nested
    print('PACKAGED MIXIN CHECK PASSED:', metadata['version'])
