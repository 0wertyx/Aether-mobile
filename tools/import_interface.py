"""One-time import of the exact previously generated AETHER Android source archive."""
from pathlib import Path, PurePosixPath
from hashlib import sha256
import sys
import zipfile

EXPECTED = "8d9292eec659a5e8e74d8a5c306a2c87ad4e6a240c843b93298295da6bdf23d2"
ROOT = Path(__file__).resolve().parents[1]
PREFIX = "AETHER-Android/"
WEB = "app/src/main/assets/web/"
archive = Path(sys.argv[1])
if sha256(archive.read_bytes()).hexdigest() != EXPECTED:
    raise SystemExit("Archive checksum does not match the original source ZIP")
with zipfile.ZipFile(archive) as z:
    for entry in z.infolist():
        if entry.is_dir() or not entry.filename.startswith(PREFIX):
            continue
        rel = entry.filename[len(PREFIX):]
        if not (rel.startswith(WEB) or rel in ("tests/host.mjs", "tests/smoke.py")):
            continue
        parts = PurePosixPath(rel).parts
        if not parts or ".." in parts or rel.startswith("/") or "\\" in rel:
            raise SystemExit("Invalid archive member")
        target = ROOT.joinpath(*parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(z.read(entry))
main = ROOT / WEB / "dist/main.js"
src = main.read_text(encoding="utf-8")
if "installNativeUpdates()" not in src:
    src = "import { installNativeUpdates } from './native-updates.js';\n" + src
    marker = "    installMobile();"
    if marker not in src:
        raise SystemExit("Missing boot entry")
    src = src.replace(marker, marker + "\n    installNativeUpdates();", 1)
    main.write_text(src, encoding="utf-8")
readme = ROOT / "README.md"
text = readme.read_text(encoding="utf-8")
lines = text.splitlines()
lines = ["**Interface imported.** Build and device testing are tracked in GitHub Actions." if line.startswith("**Import in progress:**") else line for line in lines]
text = "\n".join(lines) + "\n"
text = text.replace("The original web assets are still pending, and no signed release has been published.", "The original web assets have been imported. Signed release publication requires signing credentials.")
readme.write_text(text, encoding="utf-8")
print("Original interface imported and native updater control connected.")
