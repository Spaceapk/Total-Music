#!/usr/bin/env bash
# Build and run the small playback harness on two source revisions in one
# Android 13 emulator.  This is deliberately an emulator diagnostic, not a
# physical-device benchmark or an audible-output test.
set -euo pipefail

ROOT=$(git rev-parse --show-toplevel)
BASE_REF=${BASE_REF:-a6019d21492718a0f68f22af846974ea71c1e19c}
CANDIDATE_REF=${CANDIDATE_REF:-$(git rev-parse HEAD)}
RUNNER_TEMP=${RUNNER_TEMP:-"$ROOT/.tmp_performance"}
WORK="$RUNNER_TEMP/total-music-performance-comparison"
RESULTS="$ROOT/device-results"
mkdir -p "$RESULTS" "$WORK"
printf '1\n' > "$RESULTS/overall-status.txt"
printf 'base=%s\ncandidate=%s\n' "$BASE_REF" "$CANDIDATE_REF" > "$RESULTS/revisions.txt"

cleanup() {
  adb emu kill >/dev/null 2>&1 || true
  git worktree remove --force "$WORK/baseline" >/dev/null 2>&1 || true
  git worktree remove --force "$WORK/candidate" >/dev/null 2>&1 || true
}
trap cleanup EXIT

rm -rf "$WORK/baseline" "$WORK/candidate" "$WORK/apks"
mkdir -p "$WORK/apks"
git worktree add --detach "$WORK/baseline" "$BASE_REF"
git worktree add --detach "$WORK/candidate" "$CANDIDATE_REF"

HARNESS_REL=app/src/androidTest/java/com/music/bitchord/LocalPlaybackPerformanceTest.kt
for variant in baseline candidate; do
  cp "$ROOT/$HARNESS_REL" "$WORK/$variant/$HARNESS_REL"
done

build_variant() {
  local variant=$1
  local dir="$WORK/$variant"
  echo "Building $variant at $(git -C "$dir" rev-parse HEAD)"
  (
    cd "$dir"
    chmod +x gradlew
    ./gradlew '-Dorg.gradle.jvmargs=-Xmx6144m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true' \
      --max-workers=2 :app:assembleProdDebug :app:assembleProdDebugAndroidTest :app:assembleProdRelease \
      -PspaceSpatialEdition=true --build-cache --stacktrace
  )
  mkdir -p "$WORK/apks/$variant"
  mapfile -t app_apks < <(find "$dir/app/build/outputs/apk/prod/debug" -name '*universal*debug.apk')
  mapfile -t test_apks < <(find "$dir/app/build/outputs/apk/androidTest/prod/debug" -name '*.apk')
  mapfile -t release_apks < <(find "$dir/app/build/outputs/apk/prod/release" -name '*universal*release-unsigned.apk')
  [ "${#app_apks[@]}" -eq 1 ]
  [ "${#test_apks[@]}" -eq 1 ]
  [ "${#release_apks[@]}" -eq 1 ]
  cp "${app_apks[0]}" "$WORK/apks/$variant/app-debug.apk"
  cp "${test_apks[0]}" "$WORK/apks/$variant/test-debug.apk"
  cp "${release_apks[0]}" "$WORK/apks/$variant/release-unsigned.apk"
  git -C "$dir" rev-parse HEAD > "$RESULTS/$variant-source.txt"
}

build_variant baseline
build_variant candidate

# The candidate's JVM reports are also used by package_ci.py.  The focused
# device comparison below remains separate from the full Android regression
# suite, whose unrelated classes previously disconnected the emulator.
(
  cd "$WORK/candidate"
  ./gradlew '-Dorg.gradle.jvmargs=-Xmx6144m -Dfile.encoding=UTF-8 -Djava.net.preferIPv4Stack=true' \
    --max-workers=2 :shared:jvmTest :sharedUi:jvmTest :app:testProdDebugUnitTest \
    -PspaceSpatialEdition=true --build-cache --stacktrace
  python3 scripts/release/package_ci.py
)
rm -rf "$ROOT/release-artifacts"
cp -a "$WORK/candidate/release-artifacts" "$ROOT/release-artifacts"

mkdir -p "$RESULTS/apks"
for variant in baseline candidate; do
  cp -a "$WORK/apks/$variant" "$RESULTS/apks/$variant"
done
(
  cd "$RESULTS/apks"
  find . -type f -name '*.apk' -print0 | sort -z | xargs -0 sha256sum
) > "$RESULTS/apk-sha256.txt"

cat > "$RESULTS/environment.txt" <<EOF
kind=automated-emulator-diagnostic
android=13 (API 33), x86_64 Google APIs image
audio=emulator started with -no-audio; no audible-output claim
baseline=$BASE_REF
candidate=$CANDIDATE_REF
same_emulator=true
same_runner=true
physical_infinix=false
EOF

sdkmanager 'emulator' 'system-images;android-33;google_apis;x86_64'
export ANDROID_AVD_HOME="$RUNNER_TEMP/total-music-performance-avd"
mkdir -p "$ANDROID_AVD_HOME"
sudo chmod a+rw /dev/kvm
echo no | avdmanager create avd -n performance -k 'system-images;android-33;google_apis;x86_64' --force
"$ANDROID_HOME/emulator/emulator" -avd performance -no-window -no-audio -no-boot-anim \
  -no-snapshot -no-metrics -gpu swiftshader -feature -Vulkan -cores 2 -memory 3072 \
  > "$RESULTS/emulator.log" 2>&1 &
timeout 120 adb wait-for-device
timeout 300 bash -c 'until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = 1 ]; do sleep 2; done'
adb shell input keyevent 82
adb shell svc wifi disable
adb shell svc data disable

overall=0
run_variant() {
  local variant=$1
  local app="$WORK/apks/$variant/app-debug.apk"
  local test="$WORK/apks/$variant/test-debug.apk"
  local log="$RESULTS/$variant-instrumentation.txt"
  local logcat="$RESULTS/$variant-logcat.txt"
  echo "Running $variant"
  adb install -r "$app" || { overall=1; return; }
  adb install -r "$test" || { overall=1; return; }
  # This is a disposable emulator.  Reset only app data so the synthetic
  # fixture and queue state start equivalently; no user music is involved.
  adb shell pm clear org.nexlibre.totalmusic >/dev/null || { overall=1; return; }
  adb shell am force-stop org.nexlibre.totalmusic || true
  adb logcat -c || true
  adb logcat -v threadtime > "$logcat" 2>&1 &
  local logcat_pid=$!
  timeout 240 adb shell am instrument -w -r \
    -e class com.music.bitchord.LocalPlaybackPerformanceTest \
    org.nexlibre.totalmusic.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1
  local instrumentation_rc=$?
  kill "$logcat_pid" >/dev/null 2>&1 || true
  cat "$log"
  if [ "$instrumentation_rc" -ne 0 ] || ! grep -Eq '^OK \([0-9]+ tests?\)' "$log"; then
    overall=1
  fi
  adb pull /sdcard/Android/data/org.nexlibre.totalmusic/files/local-playback-performance.csv \
    "$RESULTS/$variant-local-playback-performance.csv" || overall=1
  adb shell dumpsys package org.nexlibre.totalmusic > "$RESULTS/$variant-package.txt" 2>&1 || true
  if ! adb get-state >/dev/null 2>&1; then
    echo 'Emulator disconnected; measurement is incomplete'
    overall=1
  fi
}

# Do not abort between variants: a partial CSV is useful evidence and the
# publishing step must still expose it.
set +e
run_variant baseline
run_variant candidate
set -e
printf '%s\n' "$overall" > "$RESULTS/overall-status.txt"
exit "$overall"
