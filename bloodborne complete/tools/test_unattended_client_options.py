"""Synthetic options-only regression; does not stage libraries, launch Java or a game."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from run_final_client import prepare_unattended_focus_pause, unattended_options_bytes


class IsolatedOptionsTests(unittest.TestCase):
    def test_missing_reserved_key_preserves_unicode_graphics_and_resources(self):
        raw = 'renderDistance:8\nresourcePacks:["file/Проверка.zip"]\ngamma:1.0\n'.encode('utf-8')
        after, delta = unattended_options_bytes(raw)
        self.assertEqual(after, raw + b'pauseOnLostFocus:false\n')
        self.assertIsNone(delta['originalExplicitValue'])
        self.assertTrue(delta['unrelatedLineContentsUnchanged'])

    def test_true_replaced_with_exact_crlf_and_no_other_change(self):
        raw = b'renderDistance:8\r\npauseOnLostFocus:true\r\ngamma:1.0\r\n'
        after, delta = unattended_options_bytes(raw)
        self.assertEqual(after, raw.replace(b'pauseOnLostFocus:true', b'pauseOnLostFocus:false'))
        self.assertEqual(delta['originalExplicitValue'], 'true')

    def test_false_is_byte_idempotent(self):
        raw = b'pauseOnLostFocus:false\nrenderDistance:8\n'
        self.assertEqual(unattended_options_bytes(raw)[0], raw)
        self.assertEqual(unattended_options_bytes(raw)[1]['operation'], 'ALREADY_FALSE')

    def test_duplicate_reserved_values_refused(self):
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            unattended_options_bytes(b'pauseOnLostFocus:true\npauseOnLostFocus:false\n')

    def test_noncanonical_reserved_key_refused(self):
        with self.assertRaisesRegex(ValueError, 'Malformed'):
            unattended_options_bytes(b' pauseOnLostFocus:true\n')

    def test_invalid_reserved_value_refused(self):
        with self.assertRaisesRegex(ValueError, 'Malformed'):
            unattended_options_bytes(b'pauseOnLostFocus:true:unexpected\n')

    def test_absent_final_newline_changes_only_required_line_separator(self):
        raw = b'renderDistance:8'
        after, delta = unattended_options_bytes(raw)
        self.assertEqual(after, b'renderDistance:8\npauseOnLostFocus:false\n')
        self.assertEqual(delta['appendedLineSeparator'], '\n')

    def test_invalid_utf8_refused(self):
        with self.assertRaises(UnicodeError): unattended_options_bytes(b'graphics:\xff\n')

    def test_isolated_snapshot_and_shader_bytes_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            run = base / 'runtime-client-synthetic'
            run.mkdir()
            raw = b'renderDistance:8\nsimulationDistance:5\ngamma:1.0\n'
            (run / 'options.txt').write_bytes(raw)
            user = base / 'original-user-options.txt'
            user.write_bytes(b'pauseOnLostFocus:true\nrenderDistance:22\n')
            shader = run / 'fake-shader-source.zip'
            settings = run / 'fake-shader-source.zip.txt'
            shader.write_bytes(b'SYNTHETIC_SHADER_BYTES_NOT_A_RUNTIME_PROFILE')
            settings.write_bytes(b''.join(('OPTION_%d=value\n' % i).encode('ascii') for i in range(42)))
            protected = {path: path.read_bytes() for path in (shader, settings, user)}
            record = prepare_unattended_focus_pause(run, base)
            snapshot = Path(record['sourceOriginalOptions'])
            self.assertEqual(snapshot.read_bytes(), raw)
            self.assertEqual(record['sourceOriginalOptionsSha256'], hashlib.sha256(raw).hexdigest())
            self.assertEqual((run / 'options.txt').read_bytes(), raw + b'pauseOnLostFocus:false\n')
            self.assertEqual(record['graphicsValueChanges'], [])
            for path, before in protected.items(): self.assertEqual(path.read_bytes(), before)
            with self.assertRaises(ValueError): prepare_unattended_focus_pause(run, base)

    def test_outside_isolated_directory_refused_before_write(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            invalid = base / 'existing-user-install'
            invalid.mkdir()
            raw = b'pauseOnLostFocus:true\n'
            (invalid / 'options.txt').write_bytes(raw)
            with self.assertRaises(ValueError): prepare_unattended_focus_pause(invalid, base)
            self.assertEqual((invalid / 'options.txt').read_bytes(), raw)
            self.assertFalse((invalid / 'qa-inputs').exists())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(IsolatedOptionsTests)
    result = unittest.TextTestRunner(verbosity=1).run(suite)
    report = {'schema': 'dw-unattended-isolated-option-synthetic-check-v1', 'status': 'PASS_SYNTHETIC_OPTIONS_ONLY' if result.wasSuccessful() else 'FAIL_SYNTHETIC_OPTIONS_ONLY',
              'tests': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors), 'gameOrJavaLaunched': False,
              'scope': 'Pure UTF8 single-key rewrite plus isolated synthetic file snapshots; actual focus/camera/full-client proof NOT_RUN'}
    if args.report:
        if args.report.exists(): raise ValueError('Refusing prior synthetic evidence overwrite')
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))
    raise SystemExit(0 if result.wasSuccessful() else 1)


if __name__ == '__main__': main()
