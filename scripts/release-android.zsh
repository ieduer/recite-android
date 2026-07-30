#!/bin/zsh
set -euo pipefail

ROOT="${0:A:h:h}"
REPO="ieduer/recite-android"
R2_BUCKET="blog-images"
R2_PREFIX="apps/recite-android"
VERSION="${1:-}"
VERSION_CODE="${2:-}"

[[ "$VERSION" =~ '^[0-9]+\.[0-9]+\.[0-9]+$' ]] || {
  print -u2 "Usage: scripts/release-android.zsh <x.y.z> <positive-version-code>"
  exit 2
}
[[ "$VERSION_CODE" =~ '^[1-9][0-9]*$' ]] || {
  print -u2 "versionCode must be a positive integer"
  exit 2
}

cd "$ROOT"
[[ -z "$(git status --porcelain)" ]] || {
  print -u2 "Release requires a clean Git tree"
  exit 2
}

: ${RECITE_ANDROID_KEYSTORE_PATH:=${BDFZ_ANDROID_KEYSTORE_PATH:-}}
: ${RECITE_ANDROID_KEYSTORE_PASSWORD:=${BDFZ_ANDROID_KEYSTORE_PASSWORD:-}}
: ${RECITE_ANDROID_KEY_ALIAS:=${BDFZ_ANDROID_KEY_ALIAS:-}}
: ${RECITE_ANDROID_KEY_PASSWORD:=${BDFZ_ANDROID_KEY_PASSWORD:-}}
export RECITE_ANDROID_KEYSTORE_PATH RECITE_ANDROID_KEYSTORE_PASSWORD RECITE_ANDROID_KEY_ALIAS RECITE_ANDROID_KEY_PASSWORD

for name in RECITE_ANDROID_KEYSTORE_PATH RECITE_ANDROID_KEYSTORE_PASSWORD RECITE_ANDROID_KEY_ALIAS RECITE_ANDROID_KEY_PASSWORD; do
  [[ -n "${(P)name:-}" ]] || {
    print -u2 "Missing signing variable: $name"
    exit 2
  }
done

RECITE_GRADLE_USER_HOME="${GRADLE_USER_HOME:-/private/tmp/recite-gradle-home}"
RECITE_ANDROID_USER_HOME="${ANDROID_USER_HOME:-/private/tmp/recite-android-home}"

ACTUAL_VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
ACTUAL_CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
[[ "$VERSION" == "$ACTUAL_VERSION" && "$VERSION_CODE" == "$ACTUAL_CODE" ]] || {
  print -u2 "build.gradle.kts is version $ACTUAL_VERSION ($ACTUAL_CODE), not $VERSION ($VERSION_CODE)"
  exit 2
}

env \
  GRADLE_USER_HOME="$RECITE_GRADLE_USER_HOME" \
  ANDROID_USER_HOME="$RECITE_ANDROID_USER_HOME" \
  ./gradlew :app:lintDirectRelease :app:testDirectDebugUnitTest \
    :app:assembleDirectRelease :app:assemblePlayRelease :app:bundlePlayRelease

APK="app/build/outputs/apk/direct/release/app-direct-release.apk"
PLAY_APK="app/build/outputs/apk/play/release/app-play-release.apk"
AAB="app/build/outputs/bundle/playRelease/app-play-release.aab"
[[ -f "$APK" && -f "$PLAY_APK" && -f "$AAB" ]] || {
  print -u2 "Release artifacts are missing"
  exit 2
}

ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}}"
AAPT="$ANDROID_SDK/build-tools/37.0.0/aapt"
APKSIGNER="$ANDROID_SDK/build-tools/37.0.0/apksigner"
[[ -x "$AAPT" && -x "$APKSIGNER" ]] || {
  print -u2 "Android build-tools 37.0.0 are required"
  exit 2
}

EXPECTED_APP_ID="net.bdfz.recite.direct"
DIRECT_APP_ID="$("$AAPT" dump badging "$APK" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p")"
PLAY_APP_ID="$("$AAPT" dump badging "$PLAY_APK" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p")"
[[ "$DIRECT_APP_ID" == "$EXPECTED_APP_ID" && "$PLAY_APP_ID" == "$EXPECTED_APP_ID" ]] || {
  print -u2 "Direct and Play must both use $EXPECTED_APP_ID"
  exit 1
}
"$AAPT" dump permissions "$APK" | grep -q 'android.permission.REQUEST_INSTALL_PACKAGES' || {
  print -u2 "Direct APK is missing REQUEST_INSTALL_PACKAGES"
  exit 1
}
if "$AAPT" dump permissions "$PLAY_APK" | grep -q 'android.permission.REQUEST_INSTALL_PACKAGES'; then
  print -u2 "Play APK must not request REQUEST_INSTALL_PACKAGES"
  exit 1
fi

DIRECT_CERT="$("$APKSIGNER" verify --print-certs "$APK" |
  sed -n 's/^.*certificate SHA-256 digest: //p' | head -n 1)"
PLAY_CERT="$("$APKSIGNER" verify --print-certs "$PLAY_APK" |
  sed -n 's/^.*certificate SHA-256 digest: //p' | head -n 1)"
[[ -n "$DIRECT_CERT" && "$DIRECT_CERT" == "$PLAY_CERT" ]] || {
  print -u2 "Direct and Play signer certificates differ"
  exit 1
}

APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
APK_SIZE="$(stat -f '%z' "$APK")"
SHA12="${APK_SHA[1,12]}"
PUBLISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
RELEASE_DIR="release/v${VERSION}-${VERSION_CODE}"
mkdir -p "$RELEASE_DIR"
STAGED_APK="$RELEASE_DIR/langlang-${VERSION}.apk"
STAGED_AAB="$RELEASE_DIR/langlang-play-${VERSION}.aab"
cp "$APK" "$STAGED_APK"
cp "$AAB" "$STAGED_AAB"

APK_KEY="$R2_PREFIX/releases/v${VERSION}/${SHA12}/langlang-${VERSION}.apk"
RELEASE_KEY="$R2_PREFIX/releases/v${VERSION}/${SHA12}/release.json"
DOWNLOAD_URL="https://img.bdfz.net/${APK_KEY}"

jq -n \
  --arg schema "bdfz-android-update-v1" \
  --arg appId "$EXPECTED_APP_ID" \
  --arg version "$VERSION" \
  --argjson versionCode "$VERSION_CODE" \
  --argjson minAndroidApi 23 \
  --arg apkUrl "$DOWNLOAD_URL" \
  --arg sha256 "$APK_SHA" \
  --argjson size "$APK_SIZE" \
  --arg publishedAt "$PUBLISHED_AT" \
  --argjson mandatory false \
  '{
    schema: $schema,
    appId: $appId,
    version: $version,
    versionCode: $versionCode,
    minAndroidApi: $minAndroidApi,
    apkUrl: $apkUrl,
    sha256: $sha256,
    size: $size,
    publishedAt: $publishedAt,
    releaseNotes: [
      "Direct 與 Play 統一為同一個 App，不再產生重複安裝",
      "更新前核對套件名、版本、大小、SHA-256 與目前 App 簽章",
      "更新清單升級為 bdfz-android-update-v1"
    ],
    mandatory: $mandatory,
    minimumSupportedVersionCode: 1,
    downloadUrl: $apkUrl,
    notes: [
      "Direct 與 Play 統一為同一個 App，不再產生重複安裝",
      "更新前核對套件名、版本、大小、SHA-256 與目前 App 簽章",
      "更新清單升級為 bdfz-android-update-v1"
    ]
  }' > "$RELEASE_DIR/latest.json"
cp "$RELEASE_DIR/latest.json" "$RELEASE_DIR/release.json"

if wrangler r2 object get "${R2_BUCKET}/${APK_KEY}" --pipe --remote >/dev/null 2>&1; then
  print -u2 "Immutable APK key already exists: $APK_KEY"
  exit 1
fi
if wrangler r2 object get "${R2_BUCKET}/${RELEASE_KEY}" --pipe --remote >/dev/null 2>&1; then
  print -u2 "Immutable release key already exists: $RELEASE_KEY"
  exit 1
fi

wrangler r2 object put "${R2_BUCKET}/${APK_KEY}" \
  --file "$STAGED_APK" \
  --content-type application/vnd.android.package-archive \
  --remote
wrangler r2 object put "${R2_BUCKET}/${RELEASE_KEY}" \
  --file "$RELEASE_DIR/release.json" \
  --content-type application/json \
  --remote

PUBLIC_SHA="$(curl -sS "$DOWNLOAD_URL" | sha256sum | awk '{print $1}')"
[[ "$PUBLIC_SHA" == "$APK_SHA" ]] || {
  print -u2 "Public R2 hash mismatch: expected $APK_SHA, got $PUBLIC_SHA"
  exit 1
}

gh release create "v${VERSION}" "$STAGED_APK" "$STAGED_AAB" "$RELEASE_DIR/release.json" \
  --repo "$REPO" \
  --title "琅琅 Android ${VERSION}" \
  --notes "Native Android release ${VERSION} (${VERSION_CODE}). APK SHA-256: ${APK_SHA}"

# latest.json is the only mutable object and moves last.
wrangler r2 object put "${R2_BUCKET}/${R2_PREFIX}/latest.json" \
  --file "$RELEASE_DIR/latest.json" \
  --content-type application/json \
  --remote

curl -sS "https://img.bdfz.net/${R2_PREFIX}/latest.json" | jq -e \
  --arg schema "bdfz-android-update-v1" \
  --arg appId "$EXPECTED_APP_ID" \
  --arg version "$VERSION" \
  --arg sha256 "$APK_SHA" \
  --argjson size "$APK_SIZE" \
  '.schema == $schema and .appId == $appId and .version == $version and
    .sha256 == $sha256 and .size == $size and .apkUrl == .downloadUrl and
    .releaseNotes == .notes' >/dev/null

print "Released v${VERSION} (${VERSION_CODE})"
print "APK: $DOWNLOAD_URL"
print "SHA-256: $APK_SHA"
