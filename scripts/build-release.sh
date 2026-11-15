#!/usr/bin/env bash
set -euo pipefail
umask 077

cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
wmm_tools="$ANDROID_HOME/build-tools/${WMM_BUILD_TOOLS:-36.0.0}"
wmm_keystore_dir="$HOME/Library/Application Support/WMM/signing"
wmm_keystore="$wmm_keystore_dir/wmm-release.p12"
wmm_service="WMM Android Release Signing"
wmm_account="com.wmm.ledger"
wmm_temp="$(mktemp -d "${TMPDIR:-/tmp}/wmm-release.XXXXXX")"
trap 'rm -rf -- "$wmm_temp"' EXIT

./gradlew :app:assembleRelease --console=plain

if [ -f "$wmm_keystore" ]; then
    WMM_SIGNING_PASS="$(security find-generic-password -s "$wmm_service" -a "$wmm_account" -w)"
else
    if security find-generic-password -s "$wmm_service" -a "$wmm_account" >/dev/null 2>&1; then
        printf '%s\n' '钥匙串已有签名密码，但密钥库文件不存在。请找回原密钥库。' >&2
        exit 1
    fi
    mkdir -p "$wmm_keystore_dir"
    WMM_SIGNING_PASS="$(openssl rand -hex 32)"
    export WMM_SIGNING_PASS
    "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$wmm_temp/wmm-release.p12" -storetype PKCS12 \
        -storepass:env WMM_SIGNING_PASS -keypass:env WMM_SIGNING_PASS -alias wmm-release \
        -keyalg RSA -keysize 3072 -validity 12000 -dname 'CN=WMM Android Release'
    # 用 stdin 将随机密码交给钥匙串，避免出现在工具的命令行参数里。
    printf 'add-generic-password -s "%s" -a "%s" -w "%s"\n' \
        "$wmm_service" "$wmm_account" "$WMM_SIGNING_PASS" | security -i >/dev/null
    [ "$(security find-generic-password -s "$wmm_service" -a "$wmm_account" -w)" = "$WMM_SIGNING_PASS" ]
    mv "$wmm_temp/wmm-release.p12" "$wmm_keystore"
fi
export WMM_SIGNING_PASS

wmm_unsigned="app/build/outputs/apk/release/app-release-unsigned.apk"
wmm_version="$("$wmm_tools/aapt2" dump badging "$wmm_unsigned" | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p")"
[[ "$wmm_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
"$wmm_tools/zipalign" -P 16 -f 4 "$wmm_unsigned" "$wmm_temp/aligned.apk"
"$wmm_tools/apksigner" sign --ks "$wmm_keystore" --ks-key-alias wmm-release \
    --ks-pass env:WMM_SIGNING_PASS --ks-type PKCS12 --v4-signing-enabled false \
    --out "$wmm_temp/signed.apk" "$wmm_temp/aligned.apk"
unset WMM_SIGNING_PASS
"$wmm_tools/apksigner" verify --verbose --print-certs "$wmm_temp/signed.apk"
"$wmm_tools/zipalign" -c -P 16 4 "$wmm_temp/signed.apk"
mkdir -p release
wmm_output="release/WMM-$wmm_version-release.apk"
mv "$wmm_temp/signed.apk" "$wmm_output"
shasum -a 256 "$wmm_output"
