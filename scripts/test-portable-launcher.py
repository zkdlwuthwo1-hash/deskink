"""Exercise portable startup with isolated process-boundary fixtures on Windows.

The shipped script runs unchanged. A PowerShell wrapper scopes its OS process
lookup to the fixture, so an already-running presenter is never affected.
Video files represent a verified cache; no network, Desktop or real USB changes
are made. Host and Android setup are child-process fixtures, not source mocks.
"""
import ctypes
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


SCRIPT = Path(__file__).resolve().parent / 'portable/start-portable.ps1'
POWERSHELL = Path(os.environ.get('SystemRoot', r'C:\Windows')) / 'System32/WindowsPowerShell/v1.0/powershell.exe'
RUNTIME_HASH = '0f376f96fb38554ccefb1b2ae9c7c6a7b351f0e60a372b38262c320e8392c5d0'
DLLS = ('avcodec-61.dll', 'avdevice-61.dll', 'avfilter-10.dll',
        'avformat-61.dll', 'avutil-59.dll', 'swresample-5.dll', 'swscale-8.dll')

HOST_SOURCE = r'''
using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
class FixtureHost {
    static int Main(string[] args) {
        string events = Environment.GetEnvironmentVariable("DESKINK_FIXTURE_EVENTS");
        File.WriteAllText(Path.Combine(events, "host.pid"), Process.GetCurrentProcess().Id.ToString());
        File.WriteAllText(Path.Combine(events, "host-arguments.txt"), String.Join(" ", args));
        if (Array.IndexOf(args, "--list-monitors") >= 0) return 0;
        string failure = Environment.GetEnvironmentVariable("DESKINK_FIXTURE_HOST_EXIT");
        if (!String.IsNullOrEmpty(failure)) return Int32.Parse(failure);
        DateTime deadline = DateTime.UtcNow.AddSeconds(60);
        while (!File.Exists(Path.Combine(events, "release")) && DateTime.UtcNow < deadline)
            Thread.Sleep(50);
        return 0;
    }
}
'''

LOOKUP_WRAPPER = r'''
param([string]$Launcher, [string]$Receipt, [switch]$CheckOnly, [int]$RunningPid = 0, [switch]$FailHelperSpawn)
$global:fixtureRunningPid = $RunningPid
function Get-Process {
    [CmdletBinding()]
    param([string]$Name)
    if ($Name -ne 'deskink') { throw "Unexpected process lookup: $Name" }
    if ($global:fixtureRunningPid) {
        Microsoft.PowerShell.Management\Get-Process -Id $global:fixtureRunningPid
    }
}
if ($FailHelperSpawn) {
    function Start-Process {
        [CmdletBinding()]
        param([string]$FilePath, [string[]]$ArgumentList, [string]$WorkingDirectory,
              [System.Diagnostics.ProcessWindowStyle]$WindowStyle, [switch]$PassThru,
              [switch]$Wait, [string]$RedirectStandardOutput, [string]$RedirectStandardError)
        if ([IO.Path]::GetFileName($FilePath) -eq 'powershell.exe') {
            throw 'Fixture OS denied starting the optional helper'
        }
        Microsoft.PowerShell.Management\Start-Process @PSBoundParameters
    }
}
$launch = @{Monitor=1}
if ($Receipt) { $launch.UpdateReceipt = $Receipt }
if ($CheckOnly) { $launch.CheckOnly = $true }
& $Launcher @launch
exit $LASTEXITCODE
'''

SLOW_HELPER = r'''
param([switch]$PrepareOnly)
$events = $env:DESKINK_FIXTURE_EVENTS
Set-Content -LiteralPath (Join-Path $events 'helper.pid') -Value $PID
@{prepare_only=[bool]$PrepareOnly; receipt_exists=(Test-Path -LiteralPath (Join-Path $events 'receipt.json'))} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $events 'helper-started.json')
[Console]::Out.WriteLine('Android fixture started')
$deadline = [DateTime]::UtcNow.AddSeconds(60)
while (-not (Test-Path -LiteralPath (Join-Path $events 'release')) -and [DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Milliseconds 50
}
Set-Content -LiteralPath (Join-Path $events 'helper-finished') -Value 'done'
'''


def alive(pid):
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.OpenProcess.argtypes = [ctypes.c_uint32, ctypes.c_bool, ctypes.c_uint32]
    kernel.OpenProcess.restype = ctypes.c_void_p
    kernel.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    handle = kernel.OpenProcess(0x100000, False, pid)
    if not handle:
        return False
    try:
        return kernel.WaitForSingleObject(handle, 0) == 258
    finally:
        kernel.CloseHandle(handle)


@unittest.skipUnless(os.name == 'nt', 'Windows portable launcher')
class PortableLauncher(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compiler_temp = tempfile.TemporaryDirectory(prefix='deskink launcher compiler ')
        cls.compiler_root = Path(cls.compiler_temp.name).resolve()
        cls.fixture_host = cls.compiler_root / 'fixture-host.exe'
        source = cls.compiler_root / 'fixture-host.cs'
        source.write_text(HOST_SOURCE, encoding='utf-8')
        compiler = Path(os.environ['SystemRoot']) / 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
        subprocess.run([str(compiler), '/nologo', '/target:exe',
                        '/out:' + str(cls.fixture_host), str(source)], check=True,
                       capture_output=True, timeout=30)

    @classmethod
    def tearDownClass(cls):
        assert cls.compiler_root.parent == Path(tempfile.gettempdir()).resolve()
        cls.compiler_temp.cleanup()

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='deskink portable startup ')
        self.base = Path(self.temporary.name).resolve()
        self.root = self.base / 'DeskInk with spaces'
        self.events = self.base / 'events'
        self.events.mkdir()
        for directory in ('bin', 'scripts', 'runtime'):
            (self.root / directory).mkdir(parents=True)
        self.launcher = self.root / 'scripts/start-portable.ps1'
        shutil.copyfile(SCRIPT, self.launcher)
        shutil.copyfile(self.fixture_host, self.root / 'bin/deskink.exe')
        (self.root / 'runtime/verified.sha256').write_text(RUNTIME_HASH)
        (self.root / 'runtime/desktop-shortcuts-created').write_text('1')
        for dll in DLLS:
            (self.root / 'runtime' / dll).write_bytes(b'fixture cached runtime')
        self.helper = self.root / 'scripts/start-android.ps1'
        self.helper.write_text(SLOW_HELPER, encoding='utf-8')
        self.wrapper = self.base / 'isolated-process-lookup.ps1'
        self.wrapper.write_text(LOOKUP_WRAPPER, encoding='utf-8')
        self.receipt = self.events / 'receipt.json'
        self.environment = dict(os.environ, LOCALAPPDATA=str(self.base / 'AppData'),
                                DESKINK_FIXTURE_EVENTS=str(self.events))
        self.logs = self.base / 'AppData/DeskInk/logs'

    def tearDown(self):
        (self.events / 'release').touch()
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            pids = [int(path.read_text().strip()) for path in self.events.glob('*.pid')]
            if not any(alive(pid) for pid in pids):
                break
            time.sleep(.05)
        else:
            self.fail('Fixture processes did not stop after release; no presenter process was terminated.')
        assert self.base.parent == Path(tempfile.gettempdir()).resolve()
        self.temporary.cleanup()

    def launch(self, *extra, receipt=True):
        command = [str(POWERSHELL), '-NoProfile', '-NonInteractive',
                   '-ExecutionPolicy', 'Bypass', '-File', str(self.wrapper),
                   '-Launcher', str(self.launcher)]
        if receipt:
            command += ['-Receipt', str(self.receipt)]
        started = time.monotonic()
        # Windows children may inherit a console pipe handle even with their
        # output redirected. Files let us measure launcher exit, not pipe EOF.
        out_path, err_path = self.base / 'launcher.out', self.base / 'launcher.err'
        with out_path.open('wb') as output, err_path.open('wb') as error:
            result = subprocess.run(command + list(extra), stdout=output, stderr=error,
                                    env=self.environment, timeout=12)
        result.stdout = out_path.read_text(encoding='utf-8', errors='replace')
        result.stderr = err_path.read_text(encoding='utf-8', errors='replace')
        return result, time.monotonic() - started

    def wait_for_file(self, path):
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if path.exists() and path.stat().st_size:
                return
            time.sleep(.05)
        self.fail(f'Expected child-process output was not created: {path.name}')

    def test_slow_android_preparation_does_not_delay_host_or_update_receipt(self):
        result, elapsed = self.launch()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        receipt = json.loads(self.receipt.read_text(encoding='utf-8-sig'))
        self.assertEqual(Path(receipt['path']), self.root / 'bin/deskink.exe')
        self.assertTrue(alive(receipt['process_id']))
        self.assertLess(elapsed, 8, 'Optional USB preparation must not hold up startup.')
        started = self.events / 'helper-started.json'
        self.wait_for_file(started)
        helper = json.loads(started.read_text(encoding='utf-8-sig'))
        self.assertTrue(helper['prepare_only'])
        self.assertTrue(helper['receipt_exists'], 'The host must be acknowledged before optional setup.')
        self.assertFalse((self.events / 'helper-finished').exists())
        self.assertEqual((self.events / 'host-arguments.txt').read_text(), '--monitor 1')

    def test_failed_android_preparation_keeps_host_and_successful_update_receipt(self):
        self.helper.write_text("param([switch]$PrepareOnly)\n"
                               "[Console]::Out.WriteLine('Preparing Android fixture')\n"
                               "[Console]::Error.WriteLine('Android fixture download unavailable')\n"
                               "exit 17\n", encoding='utf-8')
        result, elapsed = self.launch()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertLess(elapsed, 8)
        receipt = json.loads(self.receipt.read_text(encoding='utf-8-sig'))
        self.assertTrue(alive(receipt['process_id']))
        logs = list(self.logs.glob('*-android-stderr.log'))
        self.assertEqual(len(logs), 1)
        self.wait_for_file(logs[0])
        self.assertIn('Android fixture download unavailable', logs[0].read_text())
        output = list(self.logs.glob('*-android-stdout.log'))
        self.assertEqual(len(output), 1)
        self.assertIn('Preparing Android fixture', output[0].read_text())

    def test_optional_helper_process_failure_is_logged_without_rolling_back_startup(self):
        # Simulate an OS launch denial at the child-process boundary while
        # retaining real host startup and the unmodified product script.
        result, _ = self.launch('-FailHelperSpawn')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        receipt = json.loads(self.receipt.read_text(encoding='utf-8-sig'))
        self.assertTrue(alive(receipt['process_id']))
        errors = '\n'.join(path.read_text() for path in self.logs.glob('*-stderr.log'))
        self.assertIn('Could not start optional Android USB setup', errors)

    def test_failed_host_does_not_acknowledge_update_or_start_optional_setup(self):
        self.environment['DESKINK_FIXTURE_HOST_EXIT'] = '19'
        result, _ = self.launch()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('DeskInk could not start', result.stderr)
        self.assertFalse(self.receipt.exists())
        self.assertFalse(list(self.logs.glob('*-android-*.log')))
        self.assertFalse((self.events / 'helper-started.json').exists())

    def test_diagnostic_check_does_not_prepare_android_or_create_update_receipt(self):
        result, _ = self.launch('-CheckOnly')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('DeskInk runtime check passed.', result.stdout)
        self.assertEqual((self.events / 'host-arguments.txt').read_text(), '--list-monitors')
        self.assertFalse(self.receipt.exists())
        self.assertFalse(list(self.logs.glob('*-android-*.log')))
        self.assertFalse((self.events / 'helper-started.json').exists())

    def test_switching_an_existing_session_does_not_start_android_setup(self):
        # Only the OS lookup is substituted; the real process boundary still
        # receives switch-only arguments. No real DeskInk process is contacted.
        result, _ = self.launch('-RunningPid', str(os.getpid()), receipt=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.events / 'host-arguments.txt').read_text(), '--monitor 1 --switch-only')
        self.assertFalse(self.receipt.exists())
        self.assertFalse(list(self.logs.glob('*-android-*.log')))
        self.assertFalse((self.events / 'helper-started.json').exists())


if __name__ == '__main__':
    unittest.main(verbosity=2)
