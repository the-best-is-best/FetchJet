import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(HERE, "..", "sharedLogic", "src", "commonMain", "kotlin", "com", "michelle", "raouf", "fetch_jet", "core", "i18n",
                   "data_provider")
LANGS = ["English", "Arabic", "Chinese", "French", "German", "Hindi", "Indonesian", "Italian", "Japanese", "Korean", "Persian",
         "Portuguese", "Russian", "Spanish", "Turkish"]
CODES = {"English": "en", "Arabic": "ar", "Chinese": "zh", "French": "fr", "German": "de", "Hindi": "hi", "Indonesian": "id",
         "Italian": "it", "Japanese": "ja", "Korean": "ko", "Persian": "fa", "Portuguese": "pt", "Russian": "ru", "Spanish": "es",
         "Turkish": "tr"}


def load(path):
    raw = io.open(path, "rb").read().decode("utf-8")
    return raw, ("\r\n" if "\r\n" in raw else "\n")


def save(path, raw):
    io.open(path, "wb").write(raw.encode("utf-8"))


def kt(text):
    return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")


def insert_after_member(raw, nl, anchor, lines):
    at = raw.index(anchor)
    end = raw.index(nl, at)
    return raw[:end + len(nl)] + nl.join(lines) + nl + raw[end + len(nl):]


def main():
    spec = json.load(io.open(sys.argv[1], encoding="utf-8"))
    anchor = spec["after"]
    keys = spec["keys"]
    path = os.path.join(DIR, "AppLocalization.kt")
    raw, nl = load(path)
    missing = [k for k in keys if "val %s:" % k in raw]
    if missing:
        raise SystemExit("already there: %s" % missing)
    raw = insert_after_member(raw, nl, "val %s:" % anchor, ["    val %s: String" % k for k in keys])
    save(path, raw)
    for lang in LANGS:
        code = CODES[lang]
        path = os.path.join(DIR, lang + "Provider.kt")
        raw, nl = load(path)
        lines = []
        for k in keys:
            text = keys[k].get(code)
            if text is None:
                raise SystemExit("missing %s for %s" % (code, k))
            lines.append("    override val %s = \"%s\"" % (k, kt(text)))
        raw = insert_after_member(raw, nl, "override val %s" % anchor, lines)
        save(path, raw)
    print("added %d keys to 16 files" % len(keys))


if __name__ == "__main__":
    main()
