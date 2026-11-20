#!/usr/bin/env bash
set -euo pipefail
wmm_fixture_dir="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
wmm_fixture_tools="$ANDROID_HOME/build-tools/36.0.0"
wmm_fixture_sdk="$ANDROID_HOME/platforms/android-37.0/android.jar"
if [[ ! -f "$wmm_fixture_sdk" ]]; then wmm_fixture_sdk="$ANDROID_HOME/platforms/android-37/android.jar"; fi
wmm_fixture_source="${1:-wechat}"
case "$wmm_fixture_source" in
  wechat) wmm_fixture_package="com.tencent.mm" ;;
  alipay) wmm_fixture_package="com.eg.android.AlipayGphone" ;;
  *) echo '仅支持 wechat 或 alipay 测试包' >&2; exit 1 ;;
esac
wmm_fixture_work="$(mktemp -d /tmp/wmm-payment-fixture.XXXXXX)"
trap 'rm -rf -- "$wmm_fixture_work"' EXIT
mkdir -p "$wmm_fixture_work/classes" "$wmm_fixture_work/dex"
"$wmm_fixture_tools/aapt2" link -I "$wmm_fixture_sdk" --manifest "$wmm_fixture_dir/AndroidManifest.xml" \
  --rename-manifest-package "$wmm_fixture_package" --version-code 1 --version-name 0.0-test -o "$wmm_fixture_work/base.apk"
"$JAVA_HOME/bin/javac" --release 17 -classpath "$wmm_fixture_sdk" -d "$wmm_fixture_work/classes" "$wmm_fixture_dir/PaymentFixtureActivity.java"
"$wmm_fixture_tools/d8" --lib "$wmm_fixture_sdk" --min-api 26 --output "$wmm_fixture_work/dex" "$wmm_fixture_work"/classes/com/wmm/capturefixture/*.class
"$JAVA_HOME/bin/jar" uf "$wmm_fixture_work/base.apk" -C "$wmm_fixture_work/dex" classes.dex
"$wmm_fixture_tools/zipalign" -f 4 "$wmm_fixture_work/base.apk" "$wmm_fixture_work/aligned.apk"
wmm_fixture_output="/tmp/wmm-payment-fixture-$wmm_fixture_source.apk"
"$wmm_fixture_tools/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --out "$wmm_fixture_output" "$wmm_fixture_work/aligned.apk"
echo "$wmm_fixture_output"
if [[ "${2:-}" == "--install" ]]; then
  wmm_fixture_device="${3:-emulator-5554}"
  [[ "$("$ANDROID_HOME/platform-tools/adb" -s "$wmm_fixture_device" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo '拒绝安装到非模拟器设备' >&2; exit 1; }
  wmm_fixture_existing="$("$ANDROID_HOME/platform-tools/adb" -s "$wmm_fixture_device" shell pm path "$wmm_fixture_package" 2>/dev/null || true)"
  if [[ -n "$wmm_fixture_existing" ]]; then
    "$ANDROID_HOME/platform-tools/adb" -s "$wmm_fixture_device" shell dumpsys package "$wmm_fixture_package" | rg -q 'versionName=0.0-test' || { echo '已有真实支付应用，拒绝替换' >&2; exit 1; }
  fi
  "$ANDROID_HOME/platform-tools/adb" -s "$wmm_fixture_device" install -r "$wmm_fixture_output"
fi
