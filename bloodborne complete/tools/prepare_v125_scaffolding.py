"""Create fresh V12.5 QA/gallery tooling from the accepted V12.4 implementation.

No production model, old source archive, world or release is modified.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAIRS = {
    'tools/build_gallery_v124_addon.py': 'tools/build_gallery_v125_addon.py',
    'tools/prepare_gallery_v124_input.py': 'tools/prepare_gallery_v125_input.py',
    'tools/gallery-v124/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV124Author.java':
        'tools/gallery-v125/java/dev/dreamwalker/bloodbornedw/gallery/FlatGalleryV125Author.java',
}


def main():
    for source, target in PAIRS.items():
        destination = ROOT / target
        assert not destination.exists(), 'Fresh task source required: ' + target
        value = (ROOT / source).read_text(encoding='utf-8-sig')
        value = value.replace('V12.4', 'V12.5').replace('V124', 'V125').replace('v124', 'v125')
        value = value.replace('1.2.6', '1.2.7')
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(value.encode('utf8'))
        print(target)


if __name__ == '__main__':
    main()
