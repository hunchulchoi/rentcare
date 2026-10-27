#!/usr/bin/env bash
# Car app translations (RENTCARE-18/31): every string exists in en, ko, ja, zh with the same placeholders,
# and English messages fit the Pleos length rule (RENTCARE-44).
# Web strings are checked by vitest (web/src/lib/i18n.test.ts).
set -uo pipefail
cd "$(dirname "$0")/.."
python3 -I - car/app/src/main/res/values/strings.xml car/app/src/main/res/values-{ko,ja,zh}/strings.xml <<'PY'
import re, sys
def load(p):
    return dict(re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(p, encoding='utf-8').read(), re.S))
en = load(sys.argv[1])
ph = lambda s: sorted(re.findall(r'%\d+\$[sd.0-9f]*[sdf]', s))
errs = []
for path in sys.argv[2:]:
    lang = path.split('values-')[1].split('/')[0]
    t = load(path)
    errs += [f"missing in {lang}: {k}" for k in en if k not in t] + [f"extra in {lang}: {k}" for k in t if k not in en]
    errs += [f"placeholders differ in {lang}: {k}" for k in en if k in t and ph(en[k]) != ph(t[k])]
    errs += [f"empty in {lang}: {k}" for k, v in t.items() if not v.strip()]
# Pleos design guide: one English message at most 120 characters or 24 words.
for k, v in en.items():
    text = v.replace('\\n', ' ').replace('\\', '')
    if len(text) > 120 or len(text.split()) > 24:
        errs.append(f"too long in en ({len(text)} chars, {len(text.split())} words): {k}")
for e in errs: print("  FAIL", e)
print(f"== {len(en)} strings x {len(sys.argv) - 1} languages, {len(errs)} problems")
sys.exit(1 if errs else 0)
PY
