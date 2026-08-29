#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-avatar-lab-proof}"
fail() { echo "9R.1 preflight ERROR: $*" >&2; exit 1; }

test -s "$OUT/9r1-summary.txt" || fail "missing technical summary"
test -s "$OUT/9r1-runtime-log.txt" || fail "missing combined runtime log"
test -s "$OUT/9r1-camera-return-zoom.txt" || fail "missing HOME return comparison"
grep -Fq "PASS 9R.1 locomotion technical gate" "$OUT/9r1-summary.txt" \
  || fail "technical PASS marker missing"
grep -Fq "TELEPORT=false" "$OUT/9r1-summary.txt" || fail "no-teleport marker missing"
grep -Fq "CAMERA=physically_fixed_no_chase" "$OUT/9r1-summary.txt" \
  || fail "fixed camera marker missing"
grep -Fq "floorCalibrated=true" "$OUT/9r1-runtime-log.txt" \
  || fail "9R floor calibration marker missing"

for spec in \
  "16:9r1-bed" \
  "21:9r1-chair" \
  "26:9r1-window" \
  "31:9r1-camera-return"; do
  prefix="${spec%%:*}"
  label="${spec#*:}"
  for phase in turn walk-a walk-b stop idle; do
    image="$OUT/$prefix-$label-$phase.png"
    test -s "$image" || fail "missing evidence image: $image"
  done
  turn_sha="$(sha256sum "$OUT/$prefix-$label-turn.png" | awk '{print $1}')"
  walk_a_sha="$(sha256sum "$OUT/$prefix-$label-walk-a.png" | awk '{print $1}')"
  walk_b_sha="$(sha256sum "$OUT/$prefix-$label-walk-b.png" | awk '{print $1}')"
  stop_sha="$(sha256sum "$OUT/$prefix-$label-stop.png" | awk '{print $1}')"
  [ "$turn_sha" != "$walk_a_sha" ] || fail "$label turn/walk evidence is stale-identical"
  [ "$walk_a_sha" != "$walk_b_sha" ] || fail "$label walking evidence is stale-identical"
  [ "$walk_b_sha" != "$stop_sha" ] || fail "$label walk/stop evidence is stale-identical"
done

echo "PASS: 9R.1 structural/stale-image guards; manual floor/contact review remains required."
