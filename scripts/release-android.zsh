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

for name in RECITE_ANDROID_KEYSTORE_PATH RECITE_ANDROID_KEYSTORE_PASSWORD RECITE_ANDROID_KEY_ALIAS RECITE_ANDROID_KEY_PASSWORD; do
  [[ -n "${(P)name:-}" ]] || {
    print -u2 "Missing signing variable: $name"
    exit 2
  }
done

ACTUAL_VERSION="$(sed -n 's/.*versionName = "\\([^"]*\\)".*/\\1/p' app/build.gradle.kts | head -n 1)"
ACTUAL_CODE="$(sed -n 's/.*versionCode = \\([0-9]*\\).*/\\1/p' app/build.gradle.kts | head -n 1)"
[[ "$VERSION" == "$ACTUAL_VERSION" && "$VERSION_CODE" == "$ACTUAL_CODE" ]] || {
  print -u2 "build.gradle.kts is version $ACTUAL_VERSION ($ACTUAL_CODE), not $VERSION ($VERSION_CODE)"
  exit 2
}

./gradlew :app:lintDirectRelease :app:testDirectDebugUnitTest :app:assembleDirectRelease :app:bundlePlayRelease

APK="app/build/outputs/apk/direct/release/app-direct-release.apk"
AAB="app/build/outputs/bundle/playRelease/app-play-release.aab"
[[ -f "$APK" && -f "$AAB" ]] || {
  print -u2 "Release artifacts are missing"
  exit 2
}

APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
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
  --arg version "$VERSION" \
  --argjson versionCode "$VERSION_CODE" \
  --arg sha256 "$APK_SHA" \
  --arg downloadUrl "$DOWNLOAD_URL" \
  --arg publishedAt "$PUBLISHED_AT" \
  '{
    version: $version,
    versionCode: $versionCode,
    minimumSupportedVersionCode: 1,
    sha256: $sha256,
    downloadUrl: $downloadUrl,
    publishedAt: $publishedAt,
    notes: ["首个原生 Android 预览版", "78 篇离线语料与五阶段练习", "手机和平板自适应布局"]
  }' > "$RELEASE_DIR/latest.json"
cp "$RELEASE_DIR/latest.json" "$RELEASE_DIR/release.json"

gh release create "v${VERSION}" "$STAGED_APK" "$STAGED_AAB" "$RELEASE_DIR/release.json" \
  --repo "$REPO" \
  --title "琅琅 Android ${VERSION}" \
  --notes "Native Android release ${VERSION} (${VERSION_CODE}). APK SHA-256: ${APK_SHA}"

wrangler r2 object put "${R2_BUCKET}/${APK_KEY}" \
  --file "$STAGED_APK" \
  --content-type application/vnd.android.package-archive \
  --remote
wrangler r2 object put "${R2_BUCKET}/${RELEASE_KEY}" \
  --file "$RELEASE_DIR/release.json" \
  --content-type application/json \
  --remote
wrangler r2 object put "${R2_BUCKET}/${R2_PREFIX}/latest.json" \
  --file "$RELEASE_DIR/latest.json" \
  --content-type application/json \
  --remote

PUBLIC_SHA="$(curl -sS "$DOWNLOAD_URL" | sha256sum | awk '{print $1}')"
[[ "$PUBLIC_SHA" == "$APK_SHA" ]] || {
  print -u2 "Public R2 hash mismatch: expected $APK_SHA, got $PUBLIC_SHA"
  exit 1
}
curl -sS "https://img.bdfz.net/${R2_PREFIX}/latest.json" | jq -e \
  --arg version "$VERSION" \
  --arg sha256 "$APK_SHA" \
  '.version == $version and .sha256 == $sha256' >/dev/null

print "Released v${VERSION} (${VERSION_CODE})"
print "APK: $DOWNLOAD_URL"
print "SHA-256: $APK_SHA"
