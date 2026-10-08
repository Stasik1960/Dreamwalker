"""Stage the supplied shader bytes in an isolated client profile; do not launch it."""
from __future__ import annotations
import argparse
import hashlib
import json
import locale
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def decode_javap_output(data: bytes) -> tuple[str, str]:
    """Decode without dropping bytes; Java17 on Windows can use its native ACP."""
    candidates = ['utf8']
    if __import__('os').name == 'nt':
        import ctypes
        candidates.append('cp' + str(ctypes.windll.kernel32.GetACP()))
    candidates.append(locale.getpreferredencoding(False))
    for encoding in dict.fromkeys(candidates):
        try:
            value = data.decode(encoding, errors='strict')
            if value.encode(encoding, errors='strict') == data:
                return value, encoding
        except (UnicodeError, LookupError):
            continue
    raise ValueError('javap output did not round-trip through UTF8 or the native Windows code page')

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def owned_write(path: Path, data: bytes, run: Path) -> None:
    path.resolve().relative_to(run)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    if path.read_bytes() != data:
        raise IOError('Shader profile write did not preserve bytes: ' + str(path))

def properties_value(value: str) -> str:
    result = []
    for char in value:
        if char in '\\ :=#!':
            result.append('\\' + char)
        elif ord(char) < 32 or ord(char) > 126:
            for offset in range(0, len(char.encode('utf-16-be')), 2):
                code = int.from_bytes(char.encode('utf-16-be')[offset:offset + 2], 'big')
                result.append('\\u' + format(code, '04x'))
        else:
            result.append(char)
    return ''.join(result)

def iris_bytecode_audit(run: Path) -> dict:
    manifest_path = ROOT / 'reports/MODSET_PROFILE.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf8'))
    entry = next(row for row in manifest['selected'] if row.get('id') == 'iris')
    if entry['path'] not in manifest['profiles']['full_client']:
        raise ValueError('Selected Iris is absent from the declared full-client profile')
    jar = run / 'mods' / Path(entry['path']).name
    if not jar.is_file() or digest(jar.read_bytes()) != entry['sha256']:
        raise ValueError('Shader profile requires the exact selected Iris JAR already copied into its own mods folder')
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
        if metadata['id'] != 'iris' or metadata['version'] != entry['version']:
            raise ValueError('Selected Iris metadata mismatch')
        classes = {name: digest(archive.read(name)) for name in (
            'net/irisshaders/iris/config/IrisConfig.class', 'net/irisshaders/iris/Iris.class')}
    javap = Path('C:/Users/vakir/AppData/Roaming/.minecraft/runtime/java-runtime-gamma/windows/java-runtime-gamma/bin/javap.exe')
    if not javap.is_file():
        raise ValueError('The verified Java17 javap is required for this local Iris config audit')
    outputs = {}
    for class_name in ['net.irisshaders.iris.config.IrisConfig', 'net.irisshaders.iris.Iris']:
        result = subprocess.run([str(javap), '-J-Dfile.encoding=UTF-8', '-p', '-c', '-v', '-cp', str(jar), class_name],
                                capture_output=True, timeout=60)
        stdout, stdout_encoding = decode_javap_output(result.stdout)
        stderr, stderr_encoding = decode_javap_output(result.stderr)
        if result.returncode:
            raise RuntimeError('Iris bytecode disassembly failed: ' + stderr)
        path = run / 'shader-audit' / (class_name.rsplit('.', 1)[1] + '.verbose.javap.txt')
        owned_write(path, stdout.encode('utf8'), run)
        outputs[class_name] = {'path': str(path), 'sha256': digest(path.read_bytes()),
                               'raw_stdout_sha256': digest(result.stdout), 'stdout_decoded_as': stdout_encoding,
                               'raw_stderr_sha256': digest(result.stderr), 'stderr_decoded_as': stderr_encoding,
                               'decoding_round_trip_verified': True, 'requested_java_file_encoding': 'UTF-8'}
    config = Path(outputs['net.irisshaders.iris.config.IrisConfig']['path']).read_text(encoding='utf8')
    iris = Path(outputs['net.irisshaders.iris.Iris']['path']).read_text(encoding='utf8')
    # Check the exact property reads/writes, profile config directory and sidecar
    # concat proven in this supplied JAR, instead of guessing generic Iris keys.
    for key in ['shaderPack', 'enableShaders']:
        if '// String ' + key not in config or 'java/util/Properties.getProperty' not in config or 'java/util/Properties.setProperty' not in config:
            raise ValueError('Selected Iris bytecode does not contain the audited config contract')
    if ('// String iris.properties' not in iris or 'FabricLoader.getConfigDir' not in iris
            or '\\u0001.txt' not in iris or 'getShaderpacksDirectory' not in iris
            or 'loadExternalShaderpack(java.lang.String)' not in iris):
        raise ValueError('Selected Iris bytecode does not contain the audited shader sidecar contract')
    return {'status': 'PASS_EXACT_SELECTED_IRIS_BYTECODE', 'jar': str(jar), 'jar_sha256': entry['sha256'],
            'version': metadata['version'], 'source_profile_manifest_sha256': digest(manifest_path.read_bytes()),
            'class_sha256': classes, 'evidence': outputs,
            'config_relative_path': 'config/iris.properties',
            'supported_written_keys': ['shaderPack', 'enableShaders'],
            'settings_relative_path_rule': 'shaderpacks/<selected ZIP filename>.txt'}

def settings_static_audit(pack_data: bytes, settings_data: bytes) -> dict:
    declarations = {}
    with zipfile.ZipFile(__import__('io').BytesIO(pack_data)) as archive:
        for name in archive.namelist():
            if not name.endswith(('.glsl', '.vsh', '.fsh', '.gsh', '.csh')):
                continue
            source = archive.read(name).decode('utf8', errors='replace').replace('\r', '')
            for line_number, line in enumerate(source.splitlines(), 1):
                match = re.match(r'\s*(?://\s*)?#define\s+([A-Za-z_][A-Za-z0-9_]*)\b(.*)', line)
                if match is None:
                    match = re.match(r'\s*const\s+(?:float|int|bool)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)', line)
                if match is None:
                    continue
                key, tail = match.groups()
                options = re.search(r'//\s*\[([^\]]+)\]', tail)
                tokens = options.group(1).split() if options else None
                is_boolean = not tail.split('//', 1)[0].strip() or tail.strip().startswith(('true;', 'false;'))
                declarations.setdefault(key, []).append({'source': name, 'line': line_number,
                                                         'allowed_tokens': tokens, 'is_boolean': is_boolean})
    checks = []
    unparsed = []
    for line in settings_data.decode('latin1').splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        match = re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(\S+)', line)
        if match is None:
            unparsed.append(line)
            continue
        key, value = match.groups()
        matches = declarations.get(key, [])
        known = bool(matches)
        supported_value = any(value in row['allowed_tokens'] if row['allowed_tokens'] is not None
                              else row['is_boolean'] and value in ('true', 'false') for row in matches)
        checks.append({'key': key, 'value': value, 'declaration_found': known,
                       'matches_declared_value': supported_value, 'declarations': matches})
    rejected = [row['key'] for row in checks if not row['declaration_found'] or not row['matches_declared_value']]
    return {'status': 'STATIC_DECLARATIONS_MATCH_RUNTIME_UNVERIFIED' if not rejected and not unparsed
            else 'PROPOSED_SETTINGS_STATIC_MISMATCH_RUNTIME_UNVERIFIED',
            'provided_setting_count': len(checks), 'unknown_or_unsupported_settings': rejected,
            'unparsed_settings': unparsed, 'checks': checks,
            'limitations': 'Source declarations/ranges only; Iris option registration, shader compile and actual rendering require the later client run. Bytes are never rewritten.'}

def prepare_shader_profile(run_dir, shaderPackPath, settingsPath=None):
    """Return provenance after writing only this project's build/runtime-client-*.

    The launcher must first copy its selected full-client mods (including Iris).
    The settings filename changes to match the pack; both input byte streams are
    preserved exactly. Preparation does not launch a game or claim compatibility.
    """
    run = Path(run_dir).resolve()
    build = (ROOT / 'build').resolve()
    if run.parent != build or not re.fullmatch(r'runtime-client-[A-Za-z0-9_.-]+', run.name) or not run.is_dir():
        raise ValueError('Shader writes require an existing isolated build/runtime-client-* directory')
    source = Path(shaderPackPath).resolve()
    data = source.read_bytes()
    if source.suffix.lower() != '.zip':
        raise ValueError('Shader pack must be a ZIP')
    with zipfile.ZipFile(__import__('io').BytesIO(data)) as archive:
        if not any(name.endswith('shaders/shaders.properties') for name in archive.namelist()):
            raise ValueError('Supplied ZIP has no shaderpack shaders.properties')
    audit = iris_bytecode_audit(run)
    target = run / 'shaderpacks' / source.name
    owned_write(target, data, run)
    settings = None
    if settingsPath is not None:
        settings_source = Path(settingsPath).resolve()
        settings_data = settings_source.read_bytes()
        settings_target = target.with_name(target.name + '.txt')
        owned_write(settings_target, settings_data, run)
        settings = {'source': str(settings_source), 'source_sha256': digest(settings_data),
                    'source_filename': settings_source.name, 'copy': str(settings_target),
                    'copy_sha256': digest(settings_target.read_bytes()),
                    'filename_changed_for_iris_sidecar_rule': settings_source.name != settings_target.name,
                    'bytes_identical': True, 'static_option_audit': settings_static_audit(data, settings_data)}
        if settings_source.read_bytes() != settings_data:
            raise ValueError('Original shader settings changed during preparation')
    config = run / 'config/iris.properties'
    config_data = ('# Isolated Dreamwalker QA shader selection\nshaderPack=' + properties_value(source.name)
                   + '\nenableShaders=true\n').encode('ascii')
    before = digest(config.read_bytes()) if config.is_file() else None
    owned_write(config, config_data, run)
    if source.read_bytes() != data:
        raise ValueError('Original shader pack changed during preparation')
    provenance = {'schema': 'dreamwalker-isolated-shader-profile-v1', 'status': 'PASS_PROFILE_PREPARED',
                  'run_directory': str(run), 'shader_source': str(source), 'shader_sha256': digest(data),
                  'shader_copy': str(target), 'shader_copy_sha256': digest(target.read_bytes()),
                  'shader_bytes_identical': True, 'iris': audit, 'settings': settings,
                  'iris_config': str(config), 'iris_config_previous_sha256': before,
                  'iris_config_sha256': digest(config_data),
                  'iris_config_values': {'shaderPack': source.name, 'enableShaders': 'true'},
                  'original_inputs_modified': False, 'existing_user_installation_modified': False,
                  'shader_compile': 'NOT_RUN', 'actual_rendering': 'NOT_RUN',
                  'compatibility': 'PENDING_ACTUAL_CLIENT_LOGS_AND_RENDERING'}
    owned_write(run / 'shader-profile.json', (json.dumps(provenance, ensure_ascii=False, indent=2) + '\n').encode('utf8'), run)
    return provenance

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--shader-pack', type=Path, required=True)
    parser.add_argument('--shader-settings', type=Path)
    args = parser.parse_args()
    result = prepare_shader_profile(args.run_dir, args.shader_pack, args.shader_settings)
    print(json.dumps({'status': result['status'], 'shader_sha256': result['shader_sha256'],
                      'shader_copy': result['shader_copy'], 'settings_status': result['settings']['static_option_audit']['status'] if result['settings'] else None,
                      'compile': result['shader_compile']}))

if __name__ == '__main__':
    main()
