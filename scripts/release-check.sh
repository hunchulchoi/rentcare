#!/usr/bin/env bash
# Audit the APK we submit (RENTCARE-37, Pleos app security guide). Run on the exact file you send.
#   scripts/release-check.sh car/app/build/outputs/apk/release/team_RentCare_release.apk
# Checks: v2/v3 signature verifies for minSdk 28, not the Android debug certificate, not debuggable,
# no PRIVATE_* permission, allowBackup off, release HTTPS server. Prints the certificate SHA-256.
set -uo pipefail
APK="${1:?usage: scripts/release-check.sh <apk>}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null)}"; export JAVA_HOME
pass=0; fail=0
ok()  { echo "  PASS $1"; pass=$((pass + 1)); }
bad() { echo "  FAIL $1"; fail=$((fail + 1)); }

[ -f "$APK" ] || { echo "no such file: $APK"; exit 2; }
case "$APK" in *-unsigned.apk) bad "file name says unsigned (not a submission file)";; esac

sig="$("$BT/apksigner" verify --verbose --print-certs --min-sdk-version 28 "$APK" 2>&1)"
if echo "$sig" | grep -q '^Verifies$'; then ok "signature verifies (minSdk 28)"; else bad "signature does not verify: $(echo "$sig" | grep -m1 -E 'ERROR|DOES NOT')"; fi
echo "$sig" | grep -qE 'Verified using v(2|3) scheme \(APK Signature Scheme v(2|3)\): true' && ok "v2/v3 signature" || bad "no v2/v3 signature"
dn="$(echo "$sig" | grep -m1 'certificate DN:' | sed 's/.*certificate DN: //')"
sha="$(echo "$sig" | grep -m1 'certificate SHA-256 digest:' | sed 's/.*digest: //')"
if [ -z "$dn" ]; then bad "no signer certificate"
elif echo "$dn" | grep -q 'CN=Android Debug'; then bad "signed with the Android debug key ($dn)"
else ok "release certificate: $dn"; echo "       SHA-256 $sha"; fi

badging="$("$BT/aapt" dump badging "$APK" 2>/dev/null)"
xml="$("$BT/aapt" dump xmltree "$APK" AndroidManifest.xml 2>/dev/null)"
echo "$badging" | grep -q "application-debuggable" && bad "debuggable" || ok "not debuggable"
echo "$xml" | grep -qE 'android:allowBackup\(0x[0-9a-f]+\)=\(type 0x12\)0x0' && ok "allowBackup=false" || bad "allowBackup not false"
priv="$("$BT/aapt" dump permissions "$APK" | grep -o "pleos.car.permission.PRIVATE_[A-Z_]*" | sort -u | tr '\n' ' ')"
[ -z "$priv" ] && ok "no PRIVATE_* permission" || bad "PRIVATE permission in release: $priv"

dex="$(mktemp -d)"; unzip -q -o "$APK" 'classes*.dex' -d "$dex"
url="$(LC_ALL=C strings "$dex"/classes*.dex | grep -m1 -E '^https?://[a-z0-9.-]+\.[a-z]{2,}/?$' || true)"
rm -rf "$dex"
case "$url" in https://*) ok "server $url";; *) bad "server address not https ($url)";; esac

echo "== $pass passed, $fail failed"
[ "$fail" -eq 0 ]
