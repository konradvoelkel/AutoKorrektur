#!/usr/bin/env bash
# Assembles the static tree for autokorrektur.org into OUT (default site/dist, gitignored):
#   /              site/index.html (de), en.html, impressum.html, site.css, robots.txt, icons/, img/
#   /privacy       privacy.html rendered by pandoc from PRIVACY_POLICY.md (German, the URL the Play
#                  listing points at) -- so the hosted policy cannot drift from the file
#   /privacy-en    privacy-en.html from PRIVACY_POLICY.en.md (minus its italic maintainers' note)
#   /download/     the signed `core` release APK (arm64-v8a) named with the versionName, plus
#                  SHA256SUMS -- the sideload route while the Play listing is not live
# Then precompresses text files (brotli if installed, zstd, gzip) for Caddy's `precompressed`.
# Same shape as Laberampel's site/build.sh, without the browser app. Needs: pandoc, zstd, git.
# Deploying is site/deploy.sh. Nothing here touches the network.
#
# The APK is whatever `./gradlew :app:assembleCoreRelease` last produced -- `core`, because that is
# "the app" (docs/PRODUCT_TIERS.md), signed with the release keystore (keystore.properties, local
# only: without it Gradle falls back to the debug key and the check below refuses to publish it).
# Never pass -PscreenshotAbi when building it, or the download is an x86_64 APK no phone can run.
#
# DOWNLOAD_VERSION overrides the version the pages *claim* to serve, for a pages-only change (an
# Impressum fix) that must not force a rebuild the source has moved past; pair it with deploy.sh
# KEEP_DOWNLOAD=1 so /download/ on the server is left alone, and with APK_BUILD_DATE if the local
# APK is not the published one. Never republish different bytes under a name people have
# checksummed: R8 output is not reproducible.
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$repo/site/dist}"
case "$out" in /*) ;; *) out="$PWD/$out" ;; esac

for tool in pandoc zstd git; do
  command -v "$tool" >/dev/null || { echo "build.sh: $tool not installed" >&2; exit 1; }
done
# versionName the way app/build.gradle.kts computes it (git describe --tags --always).
source_version=$(git -C "$repo" describe --tags --always 2>/dev/null || echo unknown)
version="${DOWNLOAD_VERSION:-$source_version}"
# app/build.gradle.kts strips the leading "v" from the tag for the versionName users see; the APK
# filename and the metadata check below use that form, the pages print it too.
apk_version="${version#v}"
built=$(git -C "$repo" log -1 --format=%cd --date=short 2>/dev/null || date -u +%Y-%m-%d)

# --- the APK the download page will serve --------------------------------------------------------
apkdir="$repo/app/build/outputs/apk/core/release"
apk_src="$apkdir/app-core-release.apk"
[ -f "$apk_src" ] || { echo "build.sh: $apk_src missing; run ./gradlew :app:assembleCoreRelease" >&2; exit 1; }
# Refuse a stale APK: the build's own metadata must carry the version this site claims to serve.
# With DOWNLOAD_VERSION that is the published one, so the check still bites -- it just compares
# against the release instead of against HEAD.
grep -q "\"versionName\": \"$apk_version\"" "$apkdir/output-metadata.json" ||
  { echo "build.sh: $apkdir/output-metadata.json is not version $apk_version; rebuild${DOWNLOAD_VERSION:+ or correct DOWNLOAD_VERSION}" >&2; exit 1; }
# Refuse an arm64-less APK: -PscreenshotAbi=x86_64 is routine for emulator work and would otherwise
# leak an APK no phone can run into the download.
# Command substitution, not `unzip -l | grep -q`: -q closes the pipe, unzip dies of SIGPIPE and
# `set -o pipefail` turns a *passing* check into a failed build (the same trap as rsync | grep).
if [ -z "$(unzip -Z1 "$apk_src" 'lib/arm64-v8a/*' 2>/dev/null)" ]; then
  echo "build.sh: $apk_src has no lib/arm64-v8a/; it was built with -PscreenshotAbi" >&2; exit 1
fi
# Refuse a debug-signed APK. minSdk 29 means AGP signs with v2/v3 only, so there is no
# META-INF/CERT.RSA to inspect -- but the signer certificate sits in the APK signing block as raw
# bytes, so the release CN is greppable and the debug key's "CN=Android Debug" is not there. A
# positive assertion, not a blocklist: no keystore.properties means Gradle silently falls back to
# the debug key, and that APK must never be published (users could not take a Play update, and
# anyone can sign an "update" for it). Needs no Android SDK.
if ! grep -qa 'Konrad Voelkel' "$apk_src"; then
  echo "build.sh: $apk_src is not signed with the release key (keystore.properties missing?)" >&2; exit 1
fi

rm -rf "$out"; mkdir -p "$out/icons" "$out/img" "$out/download"
cp -p "$repo"/site/{index.html,en.html,impressum.html,site.css,robots.txt} "$out/"
cp -p "$repo"/site/icons/* "$out/icons/"
cp -p "$repo/media/image_1_with_car_640x640.png" "$out/img/before.png"
cp -p "$repo/media/image_1_without_car_640x640.png" "$out/img/after.png"

# --- /privacy, /privacy-en from the two policy files ---------------------------------------------
# Drops an italic preamble (a paragraph in _..._ right after the title: repo notes, not policy text).
render_policy() { # <source .md> <output .html> <lang> <title> <home href> <nav html> <trailing note html>
  local src=$1 dst=$2 lang=$3 title=$4 home=$5 nav=$6 note=$7
  python3 - "$src" > "$dst.body.md" <<'PY'
import re, sys
text = open(sys.argv[1], encoding="utf-8").read()
# The first paragraph that is entirely _italic_ (within the head of the file) is the repo note.
text = re.sub(r"(?ms)\A(.{0,400}?\n\n)_[^\n]*_\n\n", r"\1", text, count=1)
sys.stdout.write(text)
PY
  {
    cat <<HTML
<!DOCTYPE html>
<html lang="$lang">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title>
<!-- Generated by site/build.sh from $(basename "$src"); edit that file, never this one. -->
<link rel="stylesheet" href="site.css">
<link rel="icon" href="icons/icon.svg" type="image/svg+xml">
</head>
<body>
<header class="site">
  <img src="icons/icon.svg" alt="" width="44" height="44">
  <a class="name" href="$home">AutoKorrektur</a>
  <nav>$nav</nav>
</header>
<main class="doc">
HTML
    pandoc -f gfm -t html5 "$dst.body.md"
    cat <<HTML
<p class="note">$note</p>
</main>
<footer class="site">$nav</footer>
</body>
</html>
HTML
  } > "$dst"
  rm "$dst.body.md"
}
render_policy "$repo/PRIVACY_POLICY.md" "$out/privacy.html" de "AutoKorrektur – Datenschutzerklärung" "./" \
  '<a href="./">Startseite</a><a href="impressum">Impressum</a><a href="privacy-en" lang="en" hreflang="en">English</a>' \
  "Gilt für die Android-App. Hosting dieser Website: <a href=\"impressum\">Impressum</a>. Text zum Stand von App-Version $version."
render_policy "$repo/PRIVACY_POLICY.en.md" "$out/privacy-en.html" en "AutoKorrektur – Privacy Policy" "en.html" \
  '<a href="en.html">Home</a><a href="impressum">Impressum</a><a href="privacy" lang="de" hreflang="de">Deutsch</a>' \
  "Applies to the Android app; the German <a href=\"privacy\" lang=\"de\">Datenschutzerklärung</a> is the binding text. Hosting of this website: <a href=\"impressum\">Impressum</a> (German). Text as of app version $version."

# --- /download/ -----------------------------------------------------------------------------------
apk_name="autokorrektur-$apk_version-arm64-v8a.apk"
cp -p "$apk_src" "$out/download/$apk_name"
(cd "$out/download" && sha256sum "$apk_name" > SHA256SUMS)
apk_mb=$(( ($(stat -c%s "$out/download/$apk_name") + 524288) / 1048576 ))
# The page's "built" date describes the APK that is *published*, which is not always the local
# rebuild: a pages-only deploy (DOWNLOAD_VERSION + deploy.sh KEEP_DOWNLOAD=1) leaves /download/ as
# it is on the server, so the date must be overridable.
apk_date=${APK_BUILD_DATE:-$(date -u -r "$apk_src" +%Y-%m-%d)}

# --- placeholders ---------------------------------------------------------------------------------
for page in index.html en.html; do
  sed -i -e "s|__APK_FILE__|$apk_name|g" -e "s|__APK_MB__|$apk_mb|g" \
    -e "s|__APK_VERSION__|$apk_version|g" -e "s|__APK_DATE__|$apk_date|g" \
    -e "s|__VERSION__|$version|g" -e "s|__DATE__|$built|g" "$out/$page"
  if grep -q '__[A-Z0-9_]*__' "$out/$page"; then echo "build.sh: unfilled placeholder in $page" >&2; exit 1; fi
done

# --- precompress (Caddy: file_server precompressed br zstd gzip) ---------------------------------
find "$out" -type f \( -name '*.html' -o -name '*.css' -o -name '*.svg' -o -name '*.txt' \) -print0 |
  xargs -0 -r -P "$(nproc)" -I{} sh -c '
    command -v brotli >/dev/null && brotli -kf "$1"
    zstd -q -kf -19 "$1"
    gzip -kf -9 "$1"' _ {}

echo "build.sh: $out -- version $version ($built), $(find "$out" -type f | wc -l) files, $(du -sh "$out" | cut -f1)"
echo "build.sh: /download/$apk_name -- $apk_mb MB, built $apk_date, sha256 $(cut -d' ' -f1 "$out/download/SHA256SUMS")"
