#!/usr/bin/env bash
#
# bump-version.sh — keeps every version placeholder in sync from the single
# source of truth: the VERSION file at the repository root.
#
# Usage:
#   scripts/bump-version.sh [new-version]
#
# With no argument, it reads VERSION and rewrites the pom.xml <revision>
# property, kof-compiler's version.properties and README.pt_BR/README.md current development section.
# With an argument, it first writes that value to VERSION and then syncs the derived files.
#
# The pipeline (release.yml) calls this with the next version before packaging.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_FILE="$ROOT/VERSION"
POM="$ROOT/pom.xml"
PROPS="$ROOT/kof-compiler/src/main/resources/dev/kof/version.properties"
README="$ROOT/README.md"
README_PTBR="$ROOT/README.pt_BR.md"

if [ $# -ge 1 ]; then
    printf '%s\n' "$1" > "$VERSION_FILE"
fi

VERSION="$(cat "$VERSION_FILE")"

case "$VERSION" in
    *[!0-9a-zA-Z.-]*|""|"."|"-") echo "error: invalid version format in VERSION: '$VERSION'" >&2; exit 1 ;;
esac

COMPONENT_VERSION="$VERSION"
RELEASE_PHASE="${VERSION##*-}"
if [ "$RELEASE_PHASE" != "$VERSION" ]; then
    COMPONENT_VERSION="${VERSION%-*}"
fi

echo "kof version: $VERSION"

# 1. pom.xml <revision> property
awk -v v="$VERSION" '
    /<revision>/ { sub(/<revision>.*<\/revision>/, "<revision>" v "</revision>") }
    { print }
' "$POM" > "$POM.tmp" && mv "$POM.tmp" "$POM"

# 2. packaged version.properties (compiler, runtime, stdlib, tooling API)
cat > "$PROPS" <<EOF
kof.version=$VERSION
kof.compiler.version=$COMPONENT_VERSION
kof.runtime.version=$COMPONENT_VERSION
kof.stdlib.version=$COMPONENT_VERSION
kof.tooling.api=21
EOF

# 3. README.md and README.pt_BR.md
for file in README.md README.pt_BR.md; do
    sed -i.bak -E "s|<!-- version -->**.***<!-- end_version -->|<!-- version -->**${VERSION}**<!-- end_version -->|" "$file"
    rm -f "${file}.bak"
done

echo "synced pom.xml and $PROPS"
echo "next release: $VERSION"