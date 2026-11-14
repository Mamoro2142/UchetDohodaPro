#!/usr/bin/env bash
set -euo pipefail
package_name=com.example.uchetdohodapro
apk_path=app/build/outputs/apk/debug/app-debug.apk
mkdir -p regression-evidence
collect_evidence() {
  adb logcat -d > regression-evidence/logcat.txt || true
  adb pull "/sdcard/Android/data/$package_name/files/regression" regression-evidence/ || true
}
trap collect_evidence EXIT
adb shell wm size 480x800
adb shell wm density 240
adb shell svc wifi disable
adb shell svc data disable
adb install -r baseline/app-debug.apk
python3 scripts/seed_legacy_database.py regression-evidence/legacy-v3.db
adb push regression-evidence/legacy-v3.db /data/local/tmp/legacy-v3.db
adb shell run-as "$package_name" mkdir -p databases
adb shell run-as "$package_name" cp /data/local/tmp/legacy-v3.db databases/uchet_dohoda.db
# No uninstall/clear: this must be an install over the original signed 1.0.9.
adb install -r "$apk_path"
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.example.uchetdohodapro.UpgradeRegressionTest "$package_name.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee regression-evidence/upgrade-instrumentation.txt
grep -q 'OK (1 test)' regression-evidence/upgrade-instrumentation.txt
collect_evidence
# Only the synthetic emulator app is removed. The public SAF backup must survive.
adb uninstall "$package_name"
adb install "$apk_path"
adb shell am instrument -w -r -e class com.example.uchetdohodapro.FreshRestoreRegressionTest "$package_name.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r' | tee regression-evidence/reinstall-instrumentation.txt
grep -q 'OK (1 test)' regression-evidence/reinstall-instrumentation.txt
