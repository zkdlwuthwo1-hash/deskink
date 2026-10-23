"""Render the offline guide with embedded screenshot assets, fonts, CSS and JS."""
import base64
from pathlib import Path

root = Path(__file__).resolve().parents[1]
html = (root / 'docs/guide-template.html').read_text(encoding='utf-8')
for name, file in [('STYLES', 'guide.css'), ('SCRIPT', 'guide.js')]:
    html = html.replace('{{' + name + '}}', (root / 'docs' / file).read_text(encoding='utf-8'))
screenshots = [
    ('WORKSPACE', 'ipad-workspace.png'),
    ('TOOLS', 'ipad-tools-preview.png'),
    ('SHAPES', 'android-selection.png'),
    ('ERASER', 'ipad-eraser-sizes.png'),
    ('COLORS', 'ipad-colors.png'),
    ('ANDROID', 'android-streaming.png'),
    ('APPLE_DEVICES', 'apple-devices-annotated.png'),
    ('ILOADER_MAC', 'iloader-mac-server-guide.png'),
    ('ILOADER_LOGIN', 'iloader-apple-login-guide.png'),
    ('ILOADER_2FA', 'iloader-apple-2fa-guide.png'),
    ('ILOADER_IMPORT', 'iloader-import-ipa-guide.png'),
    ('WINDOWS_LAN', 'windows-lan-guide.png'),
    ('WINDOWS_QR', 'windows-qr-guide.png'),
    ('IPAD_IP', 'ipad-ip-guide.png'),
    ('ANDROID_IP', 'android-ip-guide.png'),
    ('ANDROID_PAIRING', 'android-pairing-guide.png'),
]
for name, file in screenshots:
    encoded = base64.b64encode((root / 'docs/images' / file).read_bytes()).decode('ascii')
    html = html.replace('{{' + name + '}}', 'data:image/png;base64,' + encoded)
for name, file in [('GEIST', 'Geist-Regular.ttf'), ('GEIST_BOLD', 'Geist-SemiBold.ttf'), ('GEIST_MONO', 'GeistMono-Regular.ttf')]:
    encoded = base64.b64encode((root / 'host/assets/fonts' / file).read_bytes()).decode('ascii')
    html = html.replace('{{' + name + '}}', 'data:font/ttf;base64,' + encoded)
license_text = (root / 'host/assets/fonts/OFL.txt').read_text(encoding='utf-8')
license_text = '\n'.join(line.rstrip() for line in license_text.splitlines())
html = html.replace('{{FONT_LICENSE}}', license_text.replace('--', '&#45;&#45;'))
if '{{' in html:
    raise SystemExit('An HTML placeholder was not replaced')
(root / 'README.html').write_text(html, encoding='utf-8', newline='\n')
print('README.html: self-contained screenshots, fonts, CSS and JavaScript')
