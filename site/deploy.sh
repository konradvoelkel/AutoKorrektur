#!/usr/bin/env bash
# Builds the site (site/build.sh) into a temporary directory and rsyncs it to the server's
# docroot, which the infrastructure repo (~/files/work/server, role autokorrektur) owns: Caddy,
# the domain, the headers and the certificate live there, this script only delivers files.
# Same shape as Laberampel's site/deploy.sh.
#
# Environment (defaults from site/site.env, then the gitignored site/deploy.local.env):
#   VPS_HOST   user@<server>   (key via ~/.ssh/config) -- deploy.local.env only, the repo is public
#   VPS_WWW    /srv/autokorrektur/www
#   DRY_RUN=1  show what rsync would do, upload nothing
#   KEEP_DOWNLOAD=1  leave /download/ on the server untouched (pages-only deploy). `--delete` does
#                    not remove excluded paths, so the published APK and its SHA256SUMS survive --
#                    which is the point: never republish different bytes under a name people have
#                    checksummed. Pair it with build.sh DOWNLOAD_VERSION and APK_BUILD_DATE so the
#                    pages keep describing the release that is actually up there.
set -euo pipefail
cd "$(dirname "$0")"
. ./site.env
[ -f ./deploy.local.env ] && . ./deploy.local.env
VPS_HOST=${VPS_HOST:?set VPS_HOST=user@host in site/deploy.local.env (gitignored)}
VPS_WWW=${VPS_WWW:-/srv/autokorrektur/www}

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
./build.sh "$out"
echo "deploy.sh: https://$SITE_DOMAIN <- $VPS_HOST:$VPS_WWW"
# --chmod: ohne das traegt `rsync -a` die Rechte des lokalen Bauverzeichnisses hinueber
# (umask 002 -> 0775), und /srv/autokorrektur/www schwankt zwischen jedem Deploy (0775) und
# jedem `make site` im Infra-Repo (0755). Verzeichnisse 755, Dateien 644 -- Caddy liest nur.
# Kein --no-perms dazu: das schaltet -p ab, und --chmod wirkt dann laut rsync(1) auf
# bestehende Dateien gar nicht -- mit beiden zusammen blieb www am 2026-09-25 auf 0664/0775.
# --itemize-changes statt `| grep` auf --info=stats1: rsync 3.2.7 druckt die "Number of ..."-Zeilen
# nicht, grep endet mit 1, und `set -o pipefail` meldet einen geglueckten Upload als Fehler.
rsync -a --chmod=D755,F644 --delete --itemize-changes ${DRY_RUN:+--dry-run} \
  ${KEEP_DOWNLOAD:+--exclude=/download/} "$out/" "$VPS_HOST:$VPS_WWW/"
