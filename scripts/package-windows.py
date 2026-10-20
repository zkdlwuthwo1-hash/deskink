"""Build a portable ZIP from a release build, with an explicit file allowlist.

Usage: python scripts/package-windows.py --version v0.3.0-lab.1 --crt PATH
The video runtime is downloaded and verified on first launch, not bundled.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def package(version, crt, release):
    output = ROOT / 'artifacts/local/releases' / version
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='deskink-package-') as temporary:
        stage = Path(temporary) / 'DeskInk'

        def copy(source, destination):
            target = stage / destination
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)

        copy(release / 'deskink.exe', 'bin/deskink.exe')
        copy(release / 'deskink-launcher.exe', 'DeskInk.exe')
        copy(release / 'deskink-launcher.exe', 'DeskInk-Shortcuts.exe')
        copy(release / 'deskink-launcher.exe', 'DeskInk-Android.exe')
        copy(crt, 'vcruntime140.dll')
        copy(crt, 'bin/vcruntime140.dll')
        for name in ('start-portable.ps1', 'start-android.ps1', 'create-shortcuts.ps1'):
            copy(ROOT / 'scripts/portable' / name, 'scripts/' + name)
        copy(ROOT / 'README.html', 'README.html')
        for name in ('README.md', 'README.ko.md'):
            text = (ROOT / name).read_text(encoding='utf-8')
            text = re.sub(r'\]\((?!https?://|#)([^)]+)\)',
                          r'](https://github.com/zkdlwuthwo1-hash/deskink/blob/main/\1)', text)
            (stage / name).write_text(text, encoding='utf-8')
        for source, destination in (
            ('third-party/EternalMonitor-LICENSE', 'EternalMonitor-MIT.txt'),
            ('host/assets/fonts/OFL.txt', 'Geist-OFL.txt'),
            ('third-party/windows-runtime-notices.txt', 'Windows-runtime-notices.txt'),
        ):
            copy(ROOT / source, 'licenses/' + destination)

        # Include dependency license texts directly from the locked packages.
        # All target dependencies are covered; extra build-tool notices are harmless.
        metadata = json.loads(subprocess.check_output(
            ['cargo', 'metadata', '--locked', '--filter-platform', 'x86_64-pc-windows-msvc', '--format-version', '1'], cwd=ROOT))
        index = []
        for dep in sorted(metadata['packages'], key=lambda p: (p['name'], p['version'])):
            if not dep.get('source'):
                continue
            base = Path(dep['manifest_path']).parent
            label = dep['name'] + '-' + dep['version']
            notices = [p for p in base.rglob('*') if p.is_file() and (
                any(word in p.name.upper() for word in ('LICENSE', 'LICENCE', 'COPYING', 'NOTICE'))
                or (p.suffix.lower() == '.txt' and 'fonts' in p.relative_to(base).parts)
                or (dep.get('license_file') and p == base / dep['license_file']))]
            supplemental = ROOT / 'third-party/crate-notices' / label
            for notice in supplemental.rglob('*'):
                if notice.is_file():
                    copy(notice, Path('licenses/rust') / label / 'upstream' / notice.relative_to(supplemental))
            if not notices and not any(p.is_file() and p.name != 'sources.json' for p in supplemental.rglob('*')):
                raise RuntimeError('No license text found for ' + label)
            for notice in notices:
                copy(notice, Path('licenses/rust') / label / notice.relative_to(base))
            index.append({'package': label, 'license': dep.get('license'),
                          'source': 'https://crates.io/crates/' + dep['name'] + '/' + dep['version']})
        (stage / 'licenses/rust/index.json').write_text(
            json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
        dirty = bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT).strip())
        manifest = {'version': version, 'source_commit': commit, 'working_tree_dirty': dirty,
                    'files': {p.relative_to(stage).as_posix(): sha256(p)
                              for p in sorted(stage.rglob('*')) if p.is_file()}}
        (stage / 'build-info.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
        archive = output / 'DeskInk-Windows-x64.zip'
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
            for file in sorted(stage.rglob('*')):
                if file.is_file():
                    bundle.write(file, file.relative_to(stage.parent).as_posix())
    print(archive)
    print('SHA256 ' + sha256(archive))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--crt', type=Path, required=True,
                        help='Unmodified x64 vcruntime140.dll from Visual Studio VC/Redist')
    parser.add_argument('--build-dir', type=Path,
                        default=ROOT / 'target/monitor-switch/release',
                        help='Release binary directory; use a separate target when DeskInk is running')
    args = parser.parse_args()
    if not args.version or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-' for c in args.version):
        parser.error('Version must be a simple tag, without path separators.')
    if args.crt.name.lower() != 'vcruntime140.dll' or not args.crt.is_file():
        parser.error('--crt must be an existing vcruntime140.dll')
    package(args.version, args.crt.resolve(), args.build_dir.resolve())
