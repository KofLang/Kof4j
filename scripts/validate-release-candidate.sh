#!/usr/bin/env bash
# validate-release-candidate.sh — mechanizes the release-candidate identity
# check for D-ARTIFACT-TRUST queue (b) / R1: the SHA that runs the release
# tests must be the exact SHA that gets packaged and published, VERSION must
# be a genuinely new version, and none of its three target tags may exist
# yet. release.yml calls this instead of embedding the logic inline so it is
# testable without GitHub Actions.
#
# Usage: scripts/validate-release-candidate.sh <expected-sha>
#
# Reads: VERSION (repo root), `git rev-parse HEAD`, local tags matching
#   kof-<version>-{linux-x86_64,windows-x86_64,macos-arm64}
#
# On success (exit 0), stdout is exactly:
#   version=<VERSION>
#   sha=<HEAD>
# so a caller can pipe it straight into $GITHUB_OUTPUT.
#
# Exit 0: candidate valid and not yet published.
# Exit 1: invalid candidate — bad/empty VERSION, VERSION not newer than the
#         last release, a target tag for this VERSION already exists, or
#         HEAD differs from the expected SHA. Diagnostics go to stderr as
#         `::error::...` (never silent — R6).
# Exit 2: usage/environment error (missing arg, not a git repo, no VERSION).
set -uo pipefail

usage() { echo "usage: $0 <expected-sha>" >&2; exit 2; }

[ $# -eq 1 ] || usage
EXPECTED_SHA="$1"

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
    echo "::error::not a git repository" >&2
    exit 2
}
cd "$REPO_ROOT"

[ -f VERSION ] || { echo "::error::VERSION file missing" >&2; exit 2; }

VERSION="$(cat VERSION)"

# Forma básica PRIMEIRO (vazio/caracteres inválidos) — um VERSION mal-formado
# é diagnosticado pelo seu próprio defeito, não pela checagem de consistência
# entre arquivos (que exigiria um pom.xml/version.properties só pra decidir
# que o VERSION em si já é inutilizável).
case "$VERSION" in
    "")
        echo "::error::VERSION is empty" >&2
        exit 1
        ;;
    *[!0-9A-Za-z.-]*)
        echo "::error::invalid VERSION: '$VERSION'" >&2
        exit 1
        ;;
esac

# (ordem da mantenedora 11/10, #814): a tag tem que corresponder à versão que o
# COMPILADOR em si informa — recusa o candidato se as fontes da versão
# (pom <revision> / version.properties) dessincronizaram da VERSION.
if ! bash "$REPO_ROOT/scripts/check_version_consistency.sh" >/dev/null 2>&1; then
    echo "::error::version sources out of sync with VERSION ($VERSION) — run scripts/bump-version.sh and re-commit" >&2
    bash "$REPO_ROOT/scripts/check_version_consistency.sh" >&2 || true
    exit 1
fi
SHA="$(git rev-parse HEAD)"

if [ "$SHA" != "$EXPECTED_SHA" ]; then
    echo "::error::checkout HEAD $SHA differs from release candidate $EXPECTED_SHA" >&2
    exit 1
fi

for TARGET in linux-x86_64 windows-x86_64 macos-arm64 macos-x86_64; do
    TAG="kof-${VERSION}-${TARGET}"
    if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
        echo "::error::release tag already exists: $TAG" >&2
        exit 1
    fi
done

# Pre-releases (qualquer branch != main) podem ter várias 0.5.0 com mesma VERSION
# base — o workflow cria tag com +DATE (kof-0.5.0-beta+2026.09.17-...) sem mudar
# VERSION, então não pode enroscar em tag nem em "is not newer". Final 0.5.0
# na main deve ser considerado mais novo que 0.5.0-beta+date (sort -V já faz).
LAST_TAG="$(git tag -l 'kof-*-linux-x86_64' --sort=-v:refname | head -1 || true)"
if [ "${GITHUB_REF_NAME:-main}" != "main" ]; then
    echo "validate: pre-release on ${GITHUB_REF_NAME:-unknown} — skip newer gate for $VERSION (last $LAST_TAG)" >&2
else
    # The linux tag family anchors "the last published version" (same anchor the
    # old inline bump logic used) — kept here deliberately; a single canonical
    # tag per version is a later slice (see R2/R3 in the exact-artifact doc), not
    # this one.
    if [ -n "$LAST_TAG" ]; then
        LAST="${LAST_TAG#kof-}"
        LAST="${LAST%-linux-x86_64}"
        # semver: 0.5.0 > 0.5.0-beta+date, mas sort -V acha o contrário — tratar -beta/+date
        LAST_NOPLUS="${LAST%%+*}"
        VER_NOPLUS="${VERSION%%+*}"
        LAST_HAS_BETA=0; [[ "$LAST" == *"-beta"* ]] && LAST_HAS_BETA=1
        VER_HAS_BETA=0; [[ "$VERSION" == *"-beta"* ]] && VER_HAS_BETA=1
        LAST_BASE="${LAST_NOPLUS%%-beta*}"
        VER_BASE="${VER_NOPLUS%%-beta*}"
        HIGHER_BASE="$(printf '%s\n%s\n' "$LAST_BASE" "$VER_BASE" | sort -V | tail -1)"
        if [ "$VER_BASE" != "$HIGHER_BASE" ]; then
            echo "::error::VERSION $VERSION is not newer than last release $LAST" >&2
            exit 1
        fi
        if [ "$VER_BASE" = "$LAST_BASE" ]; then
            if [ "$VER_HAS_BETA" = "1" ] && [ "$LAST_HAS_BETA" = "0" ]; then
                echo "::error::VERSION $VERSION is not newer than last release $LAST (beta < final)" >&2
                exit 1
            fi
            if [ "$VERSION" = "$LAST" ]; then
                echo "::error::VERSION $VERSION is not newer than last release $LAST" >&2
                exit 1
            fi
            # 0.5.0 > 0.5.0-beta+date é ok (final > beta da mesma base)
        fi
    fi
fi

echo "version=$VERSION"
echo "sha=$SHA"
exit 0
