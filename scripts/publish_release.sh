#!/usr/bin/env bash
# Publishes an AutoKorrektur release on GitHub: the locally built, release-signed `core` APK and its
# SHA256SUMS become the assets of a release for an existing annotated tag, whose message becomes
# the release notes. Every tagged release gets its APK this way -- from the laptop, never from CI,
# because signing in CI would put the keystore into GitHub secrets (PLAYBOOK §3.9).
#
#   scripts/publish_release.sh v2.0.1
#   DRY_RUN=1 scripts/publish_release.sh v2.0.1    every check, the notes it would post; no push,
#                                                  no release
#
# The order matters, because the versionName comes from `git describe`:
#   git tag -a v2.0.1 -m "AutoKorrektur 2.0.1" -m "<what changed>"   # tag first ...
#   ./gradlew :app:assembleCoreRelease                                 # ... so the APK says 2.0.1
#   adb install -r app/build/outputs/apk/core/release/app-core-release.apk   # and smoke-test it
#   scripts/publish_release.sh v2.0.1                                  # push the tag, release
#   site/deploy.sh                                                     # same APK to autokorrektur.org
#
# Refuses: a tag that is not annotated, not on origin/main, or different on origin; an APK that is
# not that version, not arm64 or not release-signed (scripts/stage_release_apk.sh); an existing
# release whose SHA256SUMS differs from the local APK -- R8 is not reproducible, and a name people
# have checksummed never gets new bytes. Re-running after a partial failure is safe: an existing
# release with the same checksum is a no-op, one without assets gets them uploaded.
# Needs: gh (logged in, scope repo), git, plus what stage_release_apk.sh needs.
set -euo pipefail
me=$(basename "$0")
repo="$(cd "$(dirname "$0")/.." && pwd)"
tag=${1:?usage: $me <tag>, e.g. $me v2.0.1}
dry=${DRY_RUN:+1}
say() { echo "$me: $*" >&2; }
die() { say "$*"; exit 1; }
run() { if [ -n "$dry" ]; then say "DRY_RUN, skipping: $*"; else "$@"; fi; }

case "$tag" in v[0-9]*) ;; *) die "the tag must look like v2.0.1 (the versionName drops the v)" ;; esac
version=${tag#v}
case "$version" in *-*) prerelease=1 ;; *) prerelease= ;; esac   # 2.1.0-beta1, 2.0.0-37-g8f6c882
for tool in gh git; do command -v "$tool" >/dev/null || die "$tool not installed"; done
gh auth status >/dev/null 2>&1 || die "gh is not logged in (gh auth login)"

# --- the tag --------------------------------------------------------------------------------------
cd "$repo"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null ||
  die "no local tag $tag; git tag -a $tag -m '...' first, then build the APK"
[ "$(git cat-file -t "refs/tags/$tag")" = tag ] ||
  die "$tag is a lightweight tag; make it annotated (git tag -a), its message is the release note"
commit=$(git rev-parse "$tag^{commit}")
git fetch -q origin main
git merge-base --is-ancestor "$commit" origin/main ||
  die "$tag points at ${commit:0:7}, which is not on origin/main; push main first"
remote=$(git ls-remote --tags origin "refs/tags/$tag" | cut -f1)
local_tag=$(git rev-parse "refs/tags/$tag")
[ -z "$remote" ] || [ "$remote" = "$local_tag" ] ||
  die "$tag on origin is ${remote:0:7}, locally ${local_tag:0:7}; a tag never moves -- make a new one"

# --- the APK --------------------------------------------------------------------------------------
stage=$(mktemp -d); trap 'rm -rf "$stage"' EXIT
apk=$("$repo/scripts/stage_release_apk.sh" "$version" "$stage")
apk_name=$(basename "$apk")
sums="$stage/SHA256SUMS"
sha=$(cut -d' ' -f1 "$sums")

# --- the notes: the tag message, then the file, its checksum and how it is signed ----------------
. "$repo/site/site.env"
title=$(git tag -l --format='%(contents:subject)' "$tag")
notes="$stage/notes.md"
{
  git tag -l --format='%(contents:body)' "$tag"
  cat <<MD
### Download

| File | SHA-256 |
|---|---|
| \`$apk_name\` | \`$sha\` |

Signed with the project's release certificate, like every AutoKorrektur release. Install steps and
the certificate's fingerprint: https://$SITE_DOMAIN/en.html#android, which serves the same file.
MD
} > "$notes"

# --- an existing release for this tag? ----------------------------------------------------------
if existing=$(gh release view "$tag" --json assets,url --jq '.url, .assets[].name' 2>"$stage/err"); then
  url=$(head -1 <<<"$existing")
  if grep -qx SHA256SUMS <<<"$existing"; then
    published=$(gh release download "$tag" --pattern SHA256SUMS --output - | cut -d' ' -f1)
    [ "$published" = "$sha" ] ||
      die "$url already serves sha256 $published, the local APK is $sha; a published name never gets new bytes -- tag a new version"
    say "already published with these bytes, nothing to do: $url"
    exit 0
  fi
  grep -qx "$apk_name" <<<"$existing" && die "$url has $apk_name but no SHA256SUMS; look at it by hand"
  say "$url exists without assets (an earlier run failed half-way); uploading them"
  run gh release upload "$tag" "$apk" "$sums"
  exit 0
fi
grep -q 'release not found' "$stage/err" || die "gh release view failed: $(cat "$stage/err")"

# --- push the tag, create the release ------------------------------------------------------------
[ -n "$remote" ] || run git push origin "refs/tags/$tag"
flags=(--verify-tag --title "$title" --notes-file "$notes")
if [ -n "$prerelease" ]; then flags+=(--prerelease --latest=false); else flags+=(--latest); fi
say "release $tag${prerelease:+ (pre-release)} \"$title\": $apk_name, sha256 $sha"
[ -z "$dry" ] || { say "notes:"; sed 's/^/    | /' "$notes" >&2; }
run gh release create "$tag" "${flags[@]}" "$apk" "$sums"
[ -n "$dry" ] || say "published: $(gh release view "$tag" --json url --jq .url)"
