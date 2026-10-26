#!/usr/bin/env bash
# Draft demo footage for the 1st submission (RENTCARE-19). Raw material, not the final edit:
#   demo-out/<n>-<scene>.mp4   car screen clips (adb screenrecord, 1920x1080)
#   demo-out/shots/*.png       phone and vendor pages (headless Chrome)
#   demo-out/demo-draft.mp4    everything in order (screenshots shown 4 s each), no narration
# Needs: emulator, debug app (gasoline scene uses the labelled simulated fuel value), local server + Funnel
# like scripts/e2e.sh, ffmpeg. Wipes the car app's data on the emulator (demo rental only).
#   scripts/demo-record.sh [scene ...]     scenes: ev gas report attach phone (default: all)
[ -n "${BASH_VERSION:-}" ] || exec bash "$0" "$@"
set -uo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
UI=scripts/ui.sh; VC=scripts/vconsole.sh
B=$(grep -E "^LOCAL_BASE_URL=" web/.env | cut -d= -f2-)
tok=$(grep ^DEMO_VENDOR_TOKEN web/.env | cut -d= -f2)
OUT=demo-out; mkdir -p "$OUT/shots"
SCENES=("${@:-ev gas report attach phone}"); SCENES=(${SCENES[*]})
[[ "$("$ADB" get-state 2>/dev/null)" == device ]] || { echo "No emulator." >&2; exit 2; }
command -v ffmpeg >/dev/null || { echo "ffmpeg missing." >&2; exit 2; }

rec_start() { "$ADB" shell rm -f /sdcard/demo.mp4; "$ADB" shell screenrecord --size 1920x1080 --bit-rate 8000000 --time-limit 180 /sdcard/demo.mp4 & REC=$!; sleep 1; }
rec_stop() { sleep 1; "$ADB" shell pkill -INT screenrecord; wait "$REC" 2>/dev/null; sleep 1; "$ADB" pull /sdcard/demo.mp4 "$OUT/$1.mp4" >/dev/null && echo "  clip $OUT/$1.mp4"; }
restart_app() { "$ADB" shell am force-stop --user current com.rentcare.car; "$ADB" shell am start -n com.rentcare.car/.ui.MainActivity >/dev/null; sleep 4; }
# Close the on-screen keyboard only when it is open (BACK would otherwise leave the app).
hide_ime() { "$ADB" shell dumpsys input_method | grep -q "mInputShown=true" && "$ADB" shell input keyevent 4; sleep 1; }
type_field() { $UI tap "$1"; sleep 1; "$ADB" shell input text "$2"; sleep 1; hide_ime; }
top() { for _ in 1 2 3 4; do "$ADB" shell input swipe 1800 400 1800 1200 300; done; sleep 1; }
pause() { sleep "${1:-3}"; }   # hold a screen long enough to read in the video
rental() { # $1: EV|GAS, $2: optional rule chip
  $UI tap "대여 정보"; pause 1; $UI tap "데모 $([[ $1 == EV ]] && echo EV || echo 휘발유) 계약 불러오기"; pause 1
  [[ -n "${2:-}" ]] && { $UI tap "$2"; pause 1; }
  $UI tap "저장"; pause 4
}
vendor_jar() { local j; j=$(mktemp); curl -s -c "$j" -o /dev/null -X POST "$B/vendor/login" -H "Origin: $B" --data-urlencode "token=$tok" --data-urlencode "name=데모 담당자"; echo "$j"; }
media() { # $1: file, $2: images|video, $3: mime
  local CU MS row; CU=$("$ADB" shell am get-current-user | tr -d '\r'); MS=content://media/external_primary/$2/media
  "$ADB" push "$1" /data/local/tmp/demo_media >/dev/null
  "$ADB" shell content insert --user "$CU" --uri $MS --bind _display_name:s:demo_$RANDOM.${1##*.} --bind mime_type:s:"$3" --bind relative_path:s:"$([[ $2 == video ]] && echo Movies || echo Pictures)/" >/dev/null
  row=$("$ADB" shell content query --user "$CU" --uri $MS --projection _id | grep -oE '_id=[0-9]+' | cut -d= -f2 | sort -n | tail -1)
  "$ADB" shell "content write --user $CU --uri $MS/$row < /data/local/tmp/demo_media"
  "$ADB" shell content update --user "$CU" --uri $MS/$row --bind is_pending:i:0 >/dev/null
}

$VC gear Park >/dev/null; $VC soc 60 >/dev/null; $VC telltale TPMS Off >/dev/null
"$ADB" shell pm clear --user current com.rentcare.car >/dev/null; restart_app

for scene in "${SCENES[@]}"; do hide_ime; restart_app; case $scene in
ev)
  echo "== 1 EV: contract same-SOC 80 %, now 60 % → charge; SOC up → OK; charge waived"
  rec_start
  rental EV; pause 4                                   # phone report code + QR on the rental screen
  $UI tap "반납 준비"; pause 6
  $VC soc 100 >/dev/null; pause 3; $UI tap "새로고침"; pause 6   # live SOC; at 100 % the navi arrival (99 %) is usable
  rental EV "추가 충전 면제"; $UI tap "반납 준비"; pause 6
  $UI tap "경유지·일정"; pause 5
  rec_stop 1-ev; $VC soc 60 >/dev/null ;;
gas)
  echo "== 2 Gasoline (simulated fuel value, labelled): 60 % → refuel; 95 % with fuel model → OK"
  rec_start
  rental GAS; $UI tap "반납 준비"; pause 3
  $UI tap "모의 60%"; pause 2; top; pause 5
  type_field "연료탱크 (L)" 50; type_field "연비 (km/L)" 12.5
  $UI tap "적용"; pause 1; $UI tap "모의 95%"; pause 2; top; pause 5
  rec_stop 2-gas ;;
report)
  echo "== 3 Report: TPMS lamp → tire report → staff seen → tow with ETA → done"
  rental EV; $VC telltale TPMS On >/dev/null; restart_app
  rec_start
  $UI tap "고장·사고 접수"; pause 5
  $UI tap "제주 데모렌터카에 보내기에 동의"; pause 1; $UI tap "접수 보내기"; pause 6
  jar=$(vendor_jar); id=$(curl -s -b "$jar" "$B/vendor" | grep -oE "/vendor/incidents/[0-9a-f-]{36}" | head -1)
  node scripts/browser-login-check.mjs "$B" --shot "$OUT/shots/3a-vendor-list.png" --width 1400 --path /vendor >/dev/null 2>&1
  curl -s -b "$jar" -o /dev/null -X POST "$B$id?/acknowledge" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''
  $UI tap "새로고침"; pause 5
  curl -s -b "$jar" -o /dev/null -X POST "$B$id?/addAction" -H "Origin: $B" --data "kind=ACTION&actionType=TOW&expectedIn=40&body=%EA%B2%AC%EC%9D%B8%EC%B0%A8+%EC%B6%9C%EB%B0%9C"
  $UI tap "새로고침"; pause 6
  node scripts/browser-login-check.mjs "$B" --shot "$OUT/shots/3b-vendor-detail.png" --width 1400 --path "$id" >/dev/null 2>&1
  curl -s -b "$jar" -o /dev/null -X POST "$B$id?/resolve" -H "Origin: $B" -H 'Content-Type: application/x-www-form-urlencoded' -d ''; rm -f "$jar"
  $UI tap "새로고침"; pause 6
  rec_stop 3-report; $VC telltale TPMS Off >/dev/null ;;
attach)
  echo "== 4 Attach: car photo picker (photo + video), phone attach code page"
  d=$(mktemp -d)
  ffmpeg -loglevel error -f lavfi -i "testsrc2=s=1280x720:d=1" -frames:v 1 "$d/p.jpg"
  ffmpeg -loglevel error -f lavfi -i "testsrc2=s=640x360:d=3" -pix_fmt yuv420p "$d/v.mp4"
  media "$d/p.jpg" images image/jpeg; media "$d/v.mp4" video video/mp4; rm -rf "$d"
  $UI tap "접수 상태"; pause 2
  rec_start
  $UI tap "차량에서 사진·영상 고르기"; pause 5
  $UI tapdescre "촬영한 사진|Photo taken"; pause 1; $UI tapdescre "동영상|Video"; pause 1
  $UI tap "추가(2)" || $UI tap "추가(1)" || "$ADB" shell input keyevent 4
  for _ in $(seq 1 30); do $UI has "전송 완료" && break; sleep 1; done; pause 4
  rec_stop 4-attach
  code=$($UI texts | grep -oE '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}$' | head -1)
  [[ -n "$code" ]] && node scripts/browser-login-check.mjs "$B" --code-check "$code" --shot "$OUT/shots/4-phone-attach.png" >/dev/null 2>&1 ;;
phone)
  echo "== 5 Phone-only basic report with the rental code (car screen unusable)"
  rental EV; $UI tap "대여 정보"; pause 2; top
  pcode=$($UI descs | grep -oE 'phone-code [A-Z0-9]{8}' | head -1 | cut -d' ' -f2)
  [[ -n "$pcode" ]] && node scripts/browser-login-check.mjs "$B" --rental-check "$pcode" --width 400 --shot "$OUT/shots/5-phone-report.png" >/dev/null 2>&1
  node scripts/browser-login-check.mjs "$B" --shot "$OUT/shots/5-vendor-list.png" --width 1400 --path /vendor >/dev/null 2>&1 ;;
*) echo "unknown scene: $scene" >&2 ;;
esac; done

echo "== draft video"
list=$(mktemp); n=0
add() { # $1 file: clips as is, screenshots as 4 s 1920x1080 stills
  local f=$1 seg="$OUT/.seg$n.mp4"; n=$((n+1))
  if [[ $f == *.png ]]; then
    ffmpeg -loglevel error -y -loop 1 -t 4 -i "$f" -vf "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=white,format=yuv420p" -r 30 "$seg"
  else
    ffmpeg -loglevel error -y -i "$f" -vf "scale=1920:1080,format=yuv420p" -r 30 -an "$seg"
  fi
  echo "file '$PWD/$seg'" >> "$list"
}
for f in "$OUT"/1-ev.mp4 "$OUT"/2-gas.mp4 "$OUT"/3-report.mp4 "$OUT"/shots/3a-*.png "$OUT"/shots/3b-*.png \
         "$OUT"/4-attach.mp4 "$OUT"/shots/4-*.png "$OUT"/shots/5-phone-report-[123]-*.png "$OUT"/shots/5-vendor-list.png; do
  [[ -f "$f" ]] && add "$f"
done
ffmpeg -loglevel error -y -f concat -safe 0 -i "$list" -c:v libx264 -crf 22 "$OUT/demo-draft.mp4" && rm -f "$OUT"/.seg*.mp4 "$list"
echo "  $OUT/demo-draft.mp4 ($(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/demo-draft.mp4" | cut -d. -f1) s)"
