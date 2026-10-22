"""Exercise the Android setup CLI in disposable portable folders only."""
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import time
import unittest


SCRIPT = Path(__file__).resolve().parent / 'portable/start-android.ps1'
ARCHIVE_HASH = '45f4d63113e895ebde0c90f194099a4676b6ac653bd28d54314a9e022bbc1a99'
FILES = ('adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll', 'libwinpthread-1.dll',
         'NOTICE.txt', 'source.properties')


class AndroidSetup(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='deskink Android setup ')
        self.base = Path(self.temporary.name)
        self.scripts = self.base / 'DeskInk/scripts'
        self.scripts.mkdir(parents=True)
        shutil.copyfile(SCRIPT, self.scripts / SCRIPT.name)
        self.host_started = self.base / 'host-started.txt'
        (self.scripts / 'start-portable.ps1').write_text(
            "param([int]$Monitor)\nSet-Content -LiteralPath $env:DESKINK_TEST_HOST -Value $Monitor\n",
            encoding='utf-8')
        self.local = self.base / 'Local App Data'
        self.tools = self.local / 'DeskInk/android/platform-tools'
        self.environment = dict(os.environ, LOCALAPPDATA=str(self.local),
                                DESKINK_TEST_HOST=str(self.host_started))

    def tearDown(self):
        self.temporary.cleanup()

    def cache(self):
        self.tools.mkdir(parents=True)
        for name in FILES:
            (self.tools / name).write_bytes(b'cached component')
        (self.tools / 'verified.sha256').write_text(ARCHIVE_HASH, encoding='ascii')

    def cli(self, *arguments):
        return subprocess.run(
            ['powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
             'Bypass', '-File', str(self.scripts / SCRIPT.name), *map(str, arguments)],
            env=self.environment, capture_output=True, text=True,
            encoding='utf-8', errors='replace', timeout=20)

    def disconnected_cli(self, stall=False):
        requests = []

        class OfflineProxy(BaseHTTPRequestHandler):
            def do_CONNECT(self):
                requests.append(self.path)
                if stall:
                    time.sleep(3)
                    return
                self.send_error(503, 'Offline test proxy')

            def log_message(self, *args):
                pass

        with ThreadingHTTPServer(('127.0.0.1', 0), OfflineProxy) as proxy:
            worker = threading.Thread(target=proxy.serve_forever, daemon=True)
            worker.start()
            try:
                environment = dict(self.environment,
                                   DESKINK_TEST_SCRIPT=str(self.scripts / SCRIPT.name),
                                   DESKINK_TEST_PROXY=f'http://127.0.0.1:{proxy.server_port}')
                result = subprocess.run(
                    ['powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                     'Bypass', '-Command',
                     '[Net.WebRequest]::DefaultWebProxy = [Net.WebProxy]::new($env:DESKINK_TEST_PROXY); '
                     '& $env:DESKINK_TEST_SCRIPT -PrepareOnly -DownloadTimeoutSeconds 1'],
                    env=environment, capture_output=True, text=True,
                    encoding='utf-8', errors='replace', timeout=20)
            finally:
                proxy.shutdown()
                worker.join(timeout=5)
        self.assertTrue(requests, 'The fixture must intercept the download request')
        return result

    def test_cached_background_setup_does_not_launch_or_download(self):
        self.cache()
        before = {p.name: (p.read_bytes(), p.stat().st_mtime_ns) for p in self.tools.iterdir()}
        result = self.cli('-PrepareOnly')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(self.host_started.exists(), 'Preparation must not launch another host')
        self.assertEqual(before, {p.name: (p.read_bytes(), p.stat().st_mtime_ns)
                                  for p in self.tools.iterdir()})

    def test_unavailable_download_exits_without_prompt_or_partial_runtime(self):
        result = self.disconnected_cli()
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(result.stderr.strip(), 'Background setup reports failure to its log stream')
        self.assertFalse(self.host_started.exists())
        self.assertFalse(self.tools.exists(), 'Failed setup must not publish a partial runtime directory')
        self.assertEqual(list((self.local / 'DeskInk/android').iterdir()), [])

    def test_stalled_download_is_bounded_and_reports_retry(self):
        started = time.monotonic()
        result = self.disconnected_cli(stall=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('timed out', result.stderr)
        self.assertLess(time.monotonic() - started, 8)
        self.assertFalse(self.tools.exists())
        self.assertFalse(self.host_started.exists())

    def test_failed_refresh_retains_existing_runtime(self):
        self.cache()
        (self.tools / 'verified.sha256').write_text('old version', encoding='ascii')
        before = {p.name: p.read_bytes() for p in self.tools.iterdir()}
        result = self.disconnected_cli()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.tools.iterdir()})
        self.assertFalse(self.host_started.exists())

    def test_legacy_cached_setup_still_launches_requested_monitor(self):
        self.cache()
        result = self.cli('-Monitor', 1)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.host_started.read_text().strip(), '1')

    @unittest.skipUnless(os.environ.get('DESKINK_SETUP_NETWORK_TEST') == '1',
                         'Opt in to downloading the fixed Google archive')
    def test_pinned_official_download_publishes_complete_runtime_and_checkonly_works(self):
        process = subprocess.Popen(
            ['powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
             'Bypass', '-File', str(self.scripts / SCRIPT.name), '-PrepareOnly'],
            env=self.environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        observed_partial = False
        deadline = time.monotonic() + 75
        try:
            while process.poll() is None and time.monotonic() < deadline:
                if self.tools.exists():
                    observed_partial |= not all((self.tools / name).is_file()
                                                for name in (*FILES, 'verified.sha256'))
                time.sleep(0.001)
            output, error = process.communicate(timeout=5)
            self.assertEqual(process.returncode, 0, output + error)
            self.assertFalse(observed_partial, 'A running host must only discover the complete runtime')
            self.assertEqual((self.tools / 'verified.sha256').read_text().strip(), ARCHIVE_HASH)
            self.assertEqual({p.name for p in self.tools.iterdir()}, set(FILES) | {'verified.sha256'})
            self.assertFalse(self.host_started.exists())
            result = self.cli('-CheckOnly')
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('runtime check passed', result.stdout)
            self.assertIn('Android Debug Bridge', (self.tools.parent / 'version.txt').read_text())
            self.assertFalse(self.host_started.exists())
        finally:
            if process.poll() is None:
                process.terminate()  # Only this test-owned setup child.
                process.wait(timeout=5)
            for stream in (process.stdout, process.stderr):
                stream.close()


if __name__ == '__main__':
    unittest.main()
