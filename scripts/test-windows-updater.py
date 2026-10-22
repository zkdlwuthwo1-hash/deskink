"""Exercise the shipped installer CLI using disposable portable folders only."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import ctypes
import sys
import time
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parent / 'portable/update-portable.ps1'
OLD_SOURCE = 'a' * 40
NEW_SOURCE = 'b' * 40


def digest(data):
    return hashlib.sha256(data).hexdigest()


class PortableUpdater(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='deskink updater ')
        self.base = Path(self.temporary.name)
        self.root = self.base / 'DeskInk with spaces'
        self.root.mkdir()
        self.old = {'DeskInk.exe': b'old launcher', 'bin/deskink.exe': b'old host',
                    'DeskInk-Android.exe': b'old Android launcher',
                    'DeskInk-Shortcuts.exe': b'old shortcut launcher',
                    'obsolete.txt': b'old managed file'}
        for name, data in self.old.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        self.write_manifest(self.root, 'v0.5.0', OLD_SOURCE, self.old)
        (self.root / 'runtime').mkdir()
        (self.root / 'runtime/verified.sha256').write_text('keep runtime')
        (self.root / 'my-notes.txt').write_text('keep my file')
        self.archive = self.base / 'update.zip'
        self.descriptor = self.base / 'update.json'
        self.package()

    def tearDown(self):
        self.temporary.cleanup()

    def write_manifest(self, root, version, source, files):
        (root / 'build-info.json').write_text(json.dumps({
            'version': version, 'source_commit': source,
            'files': {name: digest(data) for name, data in files.items()}}))

    def package(self, extra=None, corrupt_manifest=False):
        files = {'DeskInk.exe': b'new launcher', 'bin/deskink.exe': b'new host',
                 'scripts/start-portable.ps1': b'# new launcher script',
                 'scripts/update-portable.ps1': b'# new updater'}
        manifest = {'version': 'v0.5.1', 'source_commit': NEW_SOURCE,
                    'files': {name: digest(data) for name, data in files.items()}}
        if corrupt_manifest:
            manifest['files']['bin/deskink.exe'] = '0' * 64
        files['build-info.json'] = json.dumps(manifest).encode()
        with zipfile.ZipFile(self.archive, 'w', zipfile.ZIP_DEFLATED) as bundle:
            for name, data in files.items():
                bundle.writestr('DeskInk/' + name, data)
            for name, data in (extra or {}).items():
                bundle.writestr(name, data)
        self.descriptor.write_text(json.dumps({
            'version': 'v0.5.1', 'source_commit': NEW_SOURCE,
            'url': 'https://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Windows-x64.zip',
            'sha256': digest(self.archive.read_bytes()), 'size': self.archive.stat().st_size}))

    def cli(self, *arguments, ok=True, cwd=None):
        result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive',
                                 '-ExecutionPolicy', 'Bypass', '-File', str(SCRIPT),
                                 *map(str, arguments)], capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=45, cwd=cwd)
        if ok:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout)
        return result

    def stage(self, ok=True):
        result = self.cli('-Mode', 'Stage', '-PackageRoot', self.root,
                          '-DescriptorPath', self.descriptor, '-ArchivePath', self.archive, ok=ok)
        return json.loads(result.stdout) if ok else result

    def test_update_waits_for_apply_and_keeps_folder_runtime_and_unmanaged_files(self):
        job = self.stage()
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')
        self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch')
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'new host')
        self.assertEqual((self.root / 'runtime/verified.sha256').read_text(), 'keep runtime')
        self.assertEqual((self.root / 'my-notes.txt').read_text(), 'keep my file')
        self.assertFalse((self.root / 'obsolete.txt').exists())
        self.assertFalse((self.root / 'DeskInk-Android.exe').exists())
        self.assertFalse((self.root / 'DeskInk-Shortcuts.exe').exists())
        self.assertEqual(json.loads((self.root / 'build-info.json').read_text())['version'], 'v0.5.1')

    def test_installer_releases_inherited_installation_working_directory(self):
        job = self.stage()
        self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch', cwd=self.root)
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'new host')

    def test_new_files_created_while_update_downloads_survive_restart(self):
        job = self.stage()
        (self.root / 'my-notes.txt').write_text('newer contents after staging')
        (self.root / 'new-note.txt').write_text('created while downloading')
        self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch')
        self.assertEqual((self.root / 'my-notes.txt').read_text(), 'newer contents after staging')
        self.assertEqual((self.root / 'new-note.txt').read_text(), 'created while downloading')

    def test_bad_checksum_never_changes_installed_app(self):
        self.archive.write_bytes(self.archive.read_bytes() + b'corrupted')
        self.stage(ok=False)
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')

    def test_bad_file_manifest_never_changes_installed_app(self):
        self.package(corrupt_manifest=True)
        self.stage(ok=False)
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')

    def test_zip_paths_cannot_escape_or_hide_unlisted_files(self):
        for path in ['DeskInk/../../escaped.txt', 'DeskInk/bin/../escaped.txt',
                     'DeskInk/bin/deskink.exe:stream', 'DeskInk/bin/DESKINK.exe',
                     'Elsewhere/escaped.txt', 'DeskInk/unlisted.exe']:
            with self.subTest(path=path):
                self.package(extra={path: b'untrusted'})
                self.stage(ok=False)
                self.assertFalse((self.base / 'escaped.txt').exists())
                self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')

    def test_unsafe_download_origin_and_downgrade_are_rejected(self):
        for change in [{'url': 'https://evil.invalid/update.zip'},
                       {'url': 'http://github.com/zkdlwuthwo1-hash/deskink/releases/download/v0.5.1/DeskInk-Windows-x64.zip'},
                       {'version': 'v0.4.9'},
                       {'version': 'v0.5.0', 'source_commit': OLD_SOURCE}]:
            with self.subTest(change=change):
                self.package()
                descriptor = json.loads(self.descriptor.read_text())
                descriptor.update(change)
                self.descriptor.write_text(json.dumps(descriptor))
                self.stage(ok=False)
                self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')

    def test_changed_staging_file_is_rejected_before_installation(self):
        job = self.stage()
        staged = Path(job['job_path']).parent / 'package/bin/deskink.exe'
        staged.write_bytes(b'tampered after verification')
        self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch', ok=False)
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')

    def test_locked_installation_is_retained_and_can_retry_after_unlock(self):
        job = self.stage()
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.CreateFileW.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32,
                                      ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p]
        kernel.CreateFileW.restype = ctypes.c_void_p
        kernel.CloseHandle.argtypes = [ctypes.c_void_p]
        handle = kernel.CreateFileW(str(self.root), 0x80000000, 1 | 2, None, 3, 0x02000000, None)
        self.assertNotEqual(handle, ctypes.c_void_p(-1).value)
        try:
            self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch', ok=False)
            self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')
        finally:
            kernel.CloseHandle(handle)
        (self.root / 'my-notes.txt').write_text('edited after failed attempt')
        self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch')
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'new host')
        self.assertEqual((self.root / 'my-notes.txt').read_text(), 'edited after failed attempt')

    def test_failed_new_host_start_rolls_back_the_previous_package(self):
        job = self.stage()
        result = self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-Quiet', ok=False)
        self.assertIn('did not report a successful start', result.stderr)
        self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')
        self.assertEqual((self.root / 'my-notes.txt').read_text(), 'keep my file')

    def test_waiting_for_presenter_never_forces_process_exit(self):
        job = self.stage()
        sleeper = subprocess.Popen(['powershell.exe', '-NoProfile', '-NonInteractive',
                                    '-Command', 'Start-Sleep -Seconds 15'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            result = self.cli('-Mode', 'Apply', '-JobPath', job['job_path'], '-NoLaunch',
                              '-WaitProcessId', sleeper.pid, '-WaitSeconds', '1', ok=False)
            self.assertIn('did not close in time', result.stderr)
            self.assertIsNone(sleeper.poll())
            self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')
        finally:
            sleeper.terminate()  # Only this test's disposable sleeper, never DeskInk.
            sleeper.wait(timeout=5)

    def test_helper_verifies_before_requesting_app_exit(self):
        job = self.stage()
        presenter = subprocess.Popen([sys.executable, '-c', 'import sys; sys.stdin.buffer.read(1)'], stdin=subprocess.PIPE)
        installer = subprocess.Popen(['powershell.exe', '-NoProfile', '-NonInteractive',
                                      '-ExecutionPolicy', 'Bypass', '-File', str(SCRIPT),
                                      '-Mode', 'Apply', '-JobPath', job['job_path'],
                                      '-WaitProcessId', str(presenter.pid), '-SignalReady', '-NoLaunch'],
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        ready = Path(job['job_path']).parent / 'ready'
        try:
            deadline = time.monotonic() + 15
            while not ready.exists() and installer.poll() is None and time.monotonic() < deadline:
                time.sleep(0.05)
            self.assertTrue(ready.exists(), installer.stderr.read().decode(errors='replace') if installer.poll() is not None else 'No readiness signal')
            self.assertIsNone(presenter.poll())
            self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'old host')
            presenter.stdin.write(b'x')
            presenter.stdin.flush()
            presenter.wait(timeout=5)
            output, error = installer.communicate(timeout=30)
            self.assertEqual(installer.returncode, 0, output + error)
            self.assertEqual((self.root / 'bin/deskink.exe').read_bytes(), b'new host')
        finally:
            for process in (presenter, installer):
                if process.poll() is None:
                    process.terminate()
                    process.wait(timeout=5)
                for stream in (process.stdin, process.stdout, process.stderr):
                    if stream:
                        stream.close()


if __name__ == '__main__':
    unittest.main()
