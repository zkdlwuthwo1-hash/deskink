"""Render the offline guide; image bytes are embedded without alteration."""
import base64
from pathlib import Path

root = Path(__file__).resolve().parents[1]
html = (root / 'docs/guide-template.html').read_text(encoding='utf-8')
for name, file in [('WORKSPACE', 'ipad-workspace.png'), ('TOOLS', 'ipad-tools-preview.png'), ('ANDROID', 'android-streaming.png')]:
    encoded = base64.b64encode((root / 'docs/images' / file).read_bytes()).decode('ascii')
    html = html.replace('{{' + name + '}}', 'data:image/png;base64,' + encoded)
if '{{' in html:
    raise SystemExit('An HTML placeholder was not replaced')
(root / 'README.html').write_text(html, encoding='utf-8')
print('README.html: self-contained images, CSS and JavaScript')
