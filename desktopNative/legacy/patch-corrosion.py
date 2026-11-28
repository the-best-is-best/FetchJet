import sys
from pathlib import Path

path = Path(sys.argv[1]) / "cmake" / "FindRust.cmake"
text = path.read_text(encoding="utf-8")
if '"win7"' not in text:
    anchor = '        "uwp" # aarch64-uwp-windows-msvc\n'
    if anchor not in text:
        sys.exit("patch-corrosion: vendor list not found in " + str(path))
    text = text.replace(anchor, anchor + '        "win7"\n', 1)
    path.write_text(text, encoding="utf-8", newline="\n")
print("patch-corrosion: ok")
