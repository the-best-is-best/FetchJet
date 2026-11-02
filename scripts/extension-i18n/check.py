import json, os, re, sys

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'extension', 'i18n')
ph = lambda v: sorted(re.findall(r'\{\w+\}', v))


def load(code):
    text = open(os.path.join(DIR, code + '.js'), encoding='utf-8').read().strip()
    m = re.fullmatch(r'fjAddLanguage\("(\w+)", (\{.*\})\);', text, re.S)
    if not m or m.group(1) != code:
        sys.exit(f'{code}.js: expected fjAddLanguage("{code}", {{...}});')
    return json.loads(m.group(2))


en = load('en')
bad = 0
for f in sorted(os.listdir(DIR)):
    code = f[:-3]
    if not f.endswith('.js') or code in ('i18n', 'en'):
        continue
    t = load(code)
    for k in en.keys() - t.keys():
        print(f'{code}: missing {k}'); bad += 1
    for k in t.keys() - en.keys():
        print(f'{code}: unknown {k}'); bad += 1
    for k in en.keys() & t.keys():
        if ph(t[k]) != ph(en[k]):
            print(f'{code}: {k} placeholders {ph(t[k])} != {ph(en[k])}'); bad += 1
print('ok' if not bad else f'{bad} problem(s)')
sys.exit(1 if bad else 0)
