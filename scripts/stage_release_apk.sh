#!/usr/bin/env bash
# Stages the signed `core` release APK for publication: vets it, copies it under its public name and
# writes SHA256SUMS next to it. The one place that decides what an AutoKorrektur download looks like,
# used by site/build.sh (autokorrektur.org/download/) and scripts/publish_release.sh (the GitHub
# release of a tag), so both routes serve byte-identical files under the same name.
#
#   scripts/stage_release_apk.sh <version> <outdir>
#     <version>  the versionName the APK must carry, without the tag's "v": 2.0.1, 2.0.0-37-g8f6c882
#     <outdir>   receives autokorrektur-<version>-arm64-v8a.apk and SHA256SUMS (created if missing)
#   Prints the staged APK's path on stdout; everything else goes to stderr.
#
# The APK is whatever `./gradlew :app:assembleCoreRelease` last produced -- `core`, because that is
# "the app" (docs/PRODUCT_TIERS.md), signed with the release keystore (keystore.properties, local
# only). Three refusals, each for a build that has slipped through somewhere before:
#   stale      the APK's own metadata must name <version>; an untagged build is 2.0.0-N-gsha
#   x86_64     -PscreenshotAbi=x86_64 is routine for emulator work and yields an APK no phone runs
#   debug key  without keystore.properties Gradle silently falls back to the debug key
# Never republish different bytes under a name people have checksummed: R8 output is not
# reproducible, so a rebuild of the same version is a different file. Needs: unzip, sha256sum.
set -euo pipefail
me=$(basename "$0")
repo="$(cd "$(dirname "$0")/.." && pwd)"
version=${1:?usage: $me <version> <outdir>}
out=${2:?usage: $me <version> <outdir>}
case "$out" in /*) ;; *) out="$PWD/$out" ;; esac
for tool in unzip sha256sum; do
  command -v "$tool" >/dev/null || { echo "$me: $tool not installed" >&2; exit 1; }
done

apkdir="$repo/app/build/outputs/apk/core/release"
apk_src="$apkdir/app-core-release.apk"
[ -f "$apk_src" ] || { echo "$me: $apk_src missing; run ./gradlew :app:assembleCoreRelease" >&2; exit 1; }
# Refuse a stale APK: the build's own metadata must carry the version this release claims to be.
if ! grep -q "\"versionName\": \"$version\"" "$apkdir/output-metadata.json"; then
  echo "$me: $apkdir/output-metadata.json is not version $version but $(grep -o '"versionName": "[^"]*"' "$apkdir/output-metadata.json" | cut -d'"' -f4); tag first, then rebuild" >&2
  exit 1
fi
# Refuse an arm64-less APK. Command substitution, not `unzip -l | grep -q`: -q closes the pipe,
# unzip dies of SIGPIPE and `set -o pipefail` turns a *passing* check into a failure.
if [ -z "$(unzip -Z1 "$apk_src" 'lib/arm64-v8a/*' 2>/dev/null)" ]; then
  echo "$me: $apk_src has no lib/arm64-v8a/; it was built with -PscreenshotAbi" >&2; exit 1
fi
# Refuse a debug-signed APK. minSdk 29 means AGP signs with v2/v3 only, so there is no
# META-INF/CERT.RSA to inspect -- but the signer certificate sits in the APK signing block as raw
# bytes, so the release CN is greppable and the debug key's "CN=Android Debug" is not there. A
# positive assertion, not a blocklist, and it needs no Android SDK. A debug-signed APK must never
# be published: users could not take a Play update, and anyone can sign an "update" for it.
if ! grep -qa 'Konrad Voelkel' "$apk_src"; then
  echo "$me: $apk_src is not signed with the release key (keystore.properties missing?)" >&2; exit 1
fi

apk_name="autokorrektur-$version-arm64-v8a.apk"
mkdir -p "$out"
cp -p "$apk_src" "$out/$apk_name"
(cd "$out" && sha256sum "$apk_name" > SHA256SUMS)
echo "$me: $out/$apk_name -- $(( ($(stat -c%s "$out/$apk_name") + 524288) / 1048576 )) MB, sha256 $(cut -d' ' -f1 "$out/SHA256SUMS")" >&2
echo "$out/$apk_name"
