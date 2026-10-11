"""Read-only proof of the fresh marker-gated V12.2 native capture helper."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-report', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    assert not args.report.exists(), 'Fresh evidence required'
    build = json.loads(args.build_report.read_text(encoding='utf8'))
    assert build['status'] == 'PASS' and build['exitCode'] == 0 and build['productionJarUnchanged']
    production = Path(build['productionJar'])
    assert sha(production) == build['productionJarSha256']
    addon = Path(build['addon'])
    assert sha(addon) == build['addonSha256']
    generated = args.build_report.parent/'generated-java/dev/dreamwalker/bloodbornedw/gallery'
    capture = generated/'FlatGalleryV122ClientCapture.java'
    mixin = generated/'capture/mixin/FlatGalleryV122CameraMixin.java'
    legacy = ROOT/'src/gallery/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryClientCapture.java'
    old = legacy.read_text(encoding='utf8')
    current = capture.read_text(encoding='utf8')
    # These original checks remain byte-for-byte the same in the generated class.
    for name, following in [('checkRoot', 'checkEntity'), ('checkEntity', 'atCamera'), ('atCamera', 'write')]:
        begin = old.index('    private static '+('boolean ' if name == 'atCamera' else 'JsonObject ')+name+'(')
        end = old.index('    private '+('void ' if following == 'write' else 'static '+('boolean ' if following == 'atCamera' else 'JsonObject '))+following+'(', begin)
        assert old[begin:end] in current, 'Original critical check changed: '+name
    assert 'if(quitAfterCapture&&++quitAge==20)client.scheduleStop();' in current
    assert 'report.add("failureDiagnostics",cameraDiagnostics' in current
    assert 'if(capture==null||capture.stopped||!capture.sent' in current
    prefix = 'dev/dreamwalker/bloodbornedw/gallery/'
    with zipfile.ZipFile(addon) as archive:
        assert archive.testzip() is None
        metadata = json.loads(archive.read('fabric.mod.json'))
        config = json.loads(archive.read('dreamwalker-gallery-v122-capture.mixins.json'))
        assert metadata['version'] == '1.2.4'
        assert metadata['mixins'] == [{'config':'dreamwalker-gallery-v122-capture.mixins.json', 'environment':'client'}]
        assert config['required'] and config['client'] == ['FlatGalleryV122CameraMixin']
        assert config['package'] == 'dev.dreamwalker.bloodbornedw.gallery.capture.mixin'
        assert not any(entry.startswith(config['package']+'.') for names in metadata['entrypoints'].values() for entry in names), 'Mixin package owns an entrypoint'
        assert config['injectors']['defaultRequire'] == 1
        assert not any(name.startswith('assets/') for name in archive.namelist())
        classes = [name for name in archive.namelist() if name.endswith('.class')]
        mixin_class = prefix+'capture/mixin/FlatGalleryV122CameraMixin.class'
        assert all(name.startswith(prefix+'FlatGalleryV122Author')
                   or name == prefix+'FlatGalleryV122ClientCapture.class'
                   or name == mixin_class for name in classes)
        assert [name for name in classes if name.startswith(prefix+'capture/mixin/')] == [mixin_class]
        bytecode = archive.read(mixin_class)
        assert b'net.minecraft.class_757' in bytecode and b'method_3188' in bytecode
        assert b'dwGalleryHoldPose' in bytecode and b'holdPlannedPose' in bytecode
    mappings = Path('C:/Users/vakir/.gradle/caches/fabric-loom/1.20.1/net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2/mappings.tiny')
    mapped = mappings.read_text(encoding='utf8')
    assert '\tnet/minecraft/class_757\tnet/minecraft/client/render/GameRenderer\n' in mapped
    assert '\tmethod_3188\trenderWorld\n' in mapped
    report = {'schema':'dreamwalker-v122-native-capture-helper-static-proof-v1', 'status':'PASS',
        'productionJarSha256':sha(production), 'addonSha256':sha(addon),
        'buildReportSha256':sha(args.build_report), 'legacyCaptureSourceSha256':sha(legacy),
        'generatedCaptureSourceSha256':sha(capture), 'generatedMixinSourceSha256':sha(mixin),
        'mappingsSha256':sha(mappings), 'metadata':metadata, 'mixinConfig':config,
        'criticalChecksUnchanged':['checkRoot','checkEntity','atCamera'],
        'helperClasses':classes, 'normalStopOnFailure':True,
        'runtimeCaptureStatus':'NOT_RUN_BY_THIS_STATIC_PROOF'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf8')


if __name__ == '__main__':
    main()
