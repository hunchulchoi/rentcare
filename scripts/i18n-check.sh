#!/usr/bin/env bash
# Car app translations (RENTCARE-18): every string exists in English and Korean with the same placeholders.
# Web strings are checked by vitest (web/src/lib/i18n.test.ts).
set -uo pipefail
cd "$(dirname "$0")/.."
python3 -I - car/app/src/main/res/values/strings.xml car/app/src/main/res/values-ko/strings.xml <<'PY'
import re, sys
def load(p):
    return dict(re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', open(p, encoding='utf-8').read(), re.S))
en, ko = load(sys.argv[1]), load(sys.argv[2])
errs = [f"missing in ko: {k}" for k in en if k not in ko] + [f"missing in en: {k}" for k in ko if k not in en]
ph = lambda s: sorted(re.findall(r'%\d+\$[sd]', s))
errs += [f"placeholders differ: {k}" for k in en if k in ko and ph(en[k]) != ph(ko[k])]
errs += [f"empty: {k}" for d in (en, ko) for k, v in d.items() if not v.strip()]
for e in errs: print("  FAIL", e)
print(f"== {len(en)} strings, {len(errs)} problems")
sys.exit(1 if errs else 0)
PY
