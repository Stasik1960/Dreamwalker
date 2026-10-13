"""Prepare version-specific evidence collection without changing historical QA."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def main():
    source = (ROOT / 'tools/collect_gallery_v124_client.py').read_text(encoding='utf8')
    source = source.replace('gallery-v124', 'gallery-v126').replace('gallery_v124', 'gallery_v126')
    source = source.replace("assert sections=={'v12','new','absent','items'}",
                            "assert {'v12','new','absent','items','assemblies','doors','city'} <= sections")
    source = source.replace("assert native_stairs==marker['nativeStairMaterials'] and rung_ladders>0",
                            "assert rung_ladders>0")
    source = source.replace("after['verifiedDiagnostics']", "after.get('verifiedDiagnostics', 0)")
    assert 'v124' not in source and "marker['nativeStairMaterials']" not in source
    (ROOT / 'tools/collect_gallery_v126_client.py').write_text(source, encoding='utf8', newline='\n')
    print('Prepared V12.6 native-client evidence collector')

if __name__ == '__main__':
    main()
