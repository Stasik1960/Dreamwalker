import json
import tempfile
import unittest
from pathlib import Path

from assemble_launch_package import begin_output


class PackageInputsTests(unittest.TestCase):
    def inputs(self, base):
        gallery = base / 'gallery'
        gallery.mkdir()
        manifest = {'navigation': {'spacing': 3, 'columns': 7, 'firstPod': '/tp @s 10 66 20'},
                    'specimens': [{'tp': '/tp @s 999 66 999', 'pad_bounds': [0, 63, 0, 6, 63, 6]}]}
        (gallery / 'gallery-manifest.json').write_text(json.dumps(manifest), encoding='utf8')
        presentation = base / 'reviewed.pptx'
        presentation.write_bytes(b'reviewed presentation bytes')
        return gallery, presentation, manifest

    def test_fresh_package_keeps_presentation_and_explicit_navigation(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            gallery, presentation, _ = self.inputs(base)
            output = base / 'new-package'
            navigation = begin_output(output, gallery, presentation)
            self.assertEqual('/tp @s 10 66 20', navigation['firstPod'])
            self.assertEqual(7, navigation['columns'])
            self.assertEqual(presentation.read_bytes(), (output / 'Autumn-Variants.pptx').read_bytes())

    def test_stale_output_is_rejected_and_untouched(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            gallery, presentation, _ = self.inputs(base)
            output = base / 'stale'
            output.mkdir()
            stale = output / 'private-old-file.txt'
            stale.write_bytes(b'keep local original')
            with self.assertRaisesRegex(ValueError, 'must be new'):
                begin_output(output, gallery, presentation)
            self.assertEqual(b'keep local original', stale.read_bytes())
            self.assertFalse((output / 'Autumn-Variants.pptx').exists())

    def test_output_inside_source_is_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            gallery, presentation, _ = self.inputs(base)
            output = gallery / 'new-package'
            with self.assertRaisesRegex(ValueError, 'outside inputs'):
                begin_output(output, gallery, presentation)
            self.assertFalse(output.exists())

    def test_invalid_navigation_is_rejected_before_writing(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            gallery, presentation, manifest = self.inputs(base)
            for key, value in [('spacing', 128), ('columns', True), ('firstPod', '/op somebody')]:
                broken = {**manifest, 'navigation': {**manifest['navigation'], key: value}}
                (gallery / 'gallery-manifest.json').write_text(json.dumps(broken), encoding='utf8')
                output = base / ('invalid-' + key)
                with self.subTest(key=key), self.assertRaises(ValueError):
                    begin_output(output, gallery, presentation)
                self.assertFalse(output.exists())

    def test_stale_gallery_with_misdeclared_three_block_gap_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            gallery, presentation, manifest = self.inputs(base)
            manifest['navigation']['columns'] = 1
            manifest['specimens'].append({'pad_bounds': [0, 63, 11, 6, 63, 17]})
            (gallery / 'gallery-manifest.json').write_text(json.dumps(manifest), encoding='utf8')
            output = base / 'stale-gap'
            with self.assertRaisesRegex(ValueError, 'three-block gaps'):
                begin_output(output, gallery, presentation)
            self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
