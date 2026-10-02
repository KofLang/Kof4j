#!/bin/sh
set -eu

KOF_REPO="KofLang/Kof4j"
KFVM_REPO="${KFVM_REPO:-KofLang/Kof4j}"
KFVM_REF="${KFVM_REF:-lab}"
KFVM_PATH="${KFVM_PATH:-tooling/kfvm}"
KFVM_HOME="${KFVM_HOME:-$HOME/.local/share/kof}"
KFVM_DATA="${KFVM_DATA:-$HOME/.local/share/kfvm}"
BIN_DIR="${KFVM_BIN_DIR:-$HOME/.local/bin}"
KOF_MIN="${KOF_MIN:-0.5.0}"

say() { printf 'kfvm: %s\n' "$*"; }
die() {
    printf '[ERR] kfvm: install error: %s\n' "$*" >&2
    exit 1
}

require() { command -v "$1" > /dev/null 2>&1 || die "'$1' is required"; }

detect_os() {
    case "$(uname -s)" in
        Linux*) os=linux ;;
        Darwin*) os=macos ;;
        *) die "unsupported OS: $(uname -s)" ;;
    esac
    case "$(uname -m)" in
        x86_64 | amd64) arch=x86_64 ;;
        aarch64 | arm64) arch=arm64 ;;
        *) die "unsupported architecture: $(uname -m)" ;;
    esac
    PLATFORM="$os-$arch"
}

fetch() {
    if [ -n "${GITHUB_TOKEN:-}" ] && [ "${1#https://api.github.com/}" != "$1" ]; then
        curl -fsSL --retry 3 -H "Authorization: Bearer $GITHUB_TOKEN" "$1"
    else
        curl -fsSL --retry 3 "$1"
    fi
}

version_to_int() {
    printf '%s\n' "$1" | sed 's/^kof[- ]//; s/[-+ ].*//' | awk -F. '{ printf "1%06d%06d%06d\n", $1, $2, $3 }'
}

resolve_kof_tag() {
    fetch "https://api.github.com/repos/$KOF_REPO/releases?per_page=100" | awk -v suffix="-$PLATFORM" -v want="$1" -v want_stable="$2" '
        /"tag_name":/ { gsub(/.*"tag_name": *"|".*/, ""); tag = $0 }
        /"prerelease":/ {
            if (found || substr(tag, length(tag) - length(suffix) + 1) != suffix) next
            v = tag; sub(/^kof-/, "", v); sub(/[-+].*/, "", v); split(v, n, ".")
            if (sprintf("1%06d%06d%06d", n[1], n[2], n[3]) + 0 < want + 0) next
            if (want_stable && $0 ~ /true/) next
            found = tag
        }
        END { if (found != "") print found }'
}

sha256_check() {
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum -c - > /dev/null
    else
        shasum -a 256 -c - > /dev/null
    fi
}

kof_meets_min() {
    [ -x "$1" ] || return 1
    v="$("$1" version < /dev/null 2> /dev/null | head -n 1)" || return 1
    [ -n "$v" ] || return 1
    [ "$(version_to_int "$v")" -ge "$REQUIRED" ]
}

locate_kof_bin() {
    for k in "$(command -v kof 2> /dev/null || true)" "$KFVM_HOME/current/bin/kof" "$KFVM_HOME"/kof-*/bin/kof; do
        if [ -n "$k" ] && kof_meets_min "$k"; then
            KOF="$k"
            return 0
        fi
    done
    return 1
}

install_kof() {
    say "no Kof >= $KOF_MIN found, installing one"
    tag="$(resolve_kof_tag "$REQUIRED" 1)"
    [ -n "$tag" ] || tag="$(resolve_kof_tag "$REQUIRED" 0)"
    [ -n "$tag" ] || die "no Kof release >= $KOF_MIN found for $PLATFORM"
    encoded="$(printf '%s' "$tag" | sed 's/+/%2B/g')"
    base="https://github.com/$KOF_REPO/releases/download/$encoded"
    say "downloading $tag.tar.gz"
    fetch "$base/$encoded.tar.gz" > "$TMP/$tag.tar.gz"
    fetch "$base/SHA256SUMS" > "$TMP/SHA256SUMS"
    line="$(grep " \*\{0,1\}$tag.tar.gz\$" "$TMP/SHA256SUMS" || true)"
    [ -n "$line" ] || die "checksum for $tag.tar.gz not found"
    (cd "$TMP" && printf '%s\n' "$line" | sha256_check) || die "checksum mismatch for $tag.tar.gz"
    mkdir -p "$TMP/kof" "$KFVM_HOME"
    tar -xzf "$TMP/$tag.tar.gz" -C "$TMP/kof"
    root="$TMP/kof"
    if [ "$(ls -A "$root" | wc -l | tr -d ' ')" = 1 ] && [ -d "$root/$(ls -A "$root")" ]; then
        root="$root/$(ls -A "$root")"
    fi
    [ -x "$root/bin/kof" ] || die "archive does not contain bin/kof"
    rm -rf "${KFVM_HOME:?}/$tag"
    mv "$root" "$KFVM_HOME/$tag"
    [ -e "$KFVM_HOME/current" ] || ln -sfn "$KFVM_HOME/$tag" "$KFVM_HOME/current"
    KOF="$KFVM_HOME/$tag/bin/kof"
}

get_kfvm_source() {
    if [ -n "${KFVM_SOURCE:-}" ]; then
        [ -d "$KFVM_SOURCE/src" ] || die "KFVM_SOURCE has no src directory: $KFVM_SOURCE"
        SRC="$KFVM_SOURCE"
        return
    fi
    bundled="$(dirname "$(dirname "$KOF")")/$KFVM_PATH"
    if [ -d "$bundled/src" ]; then
        say "using kfvm source from $bundled"
        SRC="$bundled"
        return
    fi
    say "downloading kfvm source ($KFVM_REPO@$KFVM_REF:$KFVM_PATH)"
    if command -v git > /dev/null 2>&1 \
        && git clone --quiet --depth 1 --filter=blob:none --sparse --branch "$KFVM_REF" "https://github.com/$KFVM_REPO.git" "$TMP/repo" > /dev/null 2>&1 \
        && git -C "$TMP/repo" sparse-checkout set "$KFVM_PATH" > /dev/null 2>&1; then
        SRC="$TMP/repo/$KFVM_PATH"
    else
        fetch "https://github.com/$KFVM_REPO/archive/$KFVM_REF.tar.gz" > "$TMP/kfvm.tar.gz"
        mkdir -p "$TMP/src"
        tar -xzf "$TMP/kfvm.tar.gz" -C "$TMP/src"
        SRC="$(find "$TMP/src" -mindepth 1 -maxdepth 1 -type d | head -n 1)/$KFVM_PATH"
    fi
    [ -d "$SRC/src" ] || die "kfvm source not found in $KFVM_REPO@$KFVM_REF:$KFVM_PATH"
}

find_kof_native_bin() {
    for name in kfvm main; do
        f="$(find "$TMP/native" -type f -name "$name" -perm -u+x 2> /dev/null | head -n 1)"
        [ -n "$f" ] && {
            printf '%s\n' "$f"
            return
        }
    done
    find "$TMP/native" -type f -perm -u+x ! -name '*.jar' ! -name '*.class' 2> /dev/null | head -n 1
}

build_native_kof_bin() {
    "$KOF" build "$SRC/src" --target native --release --output "$TMP/native" < /dev/null > /dev/null 2>&1 || return 1
    [ -d "$TMP/native" ] || return 1
    f="$(find_kof_native_bin)"
    [ -n "$f" ] || return 1
    "$f" -v < /dev/null > /dev/null 2>&1 || return 1
    mkdir -p "$BIN_DIR"
    cp "$f" "$BIN_DIR/kfvm.tmp"
    chmod 755 "$BIN_DIR/kfvm.tmp"
    mv "$BIN_DIR/kfvm.tmp" "$BIN_DIR/kfvm"
    rm -f "$KFVM_DATA/kfvm.jar"
}

build_jar() {
    "$KOF" build "$SRC/src" --release --fat --output "$TMP/jvm" < /dev/null > "$TMP/build.log" 2>&1 || {
        cat "$TMP/build.log" >&2
        die "build failed"
    }
    [ -f "$TMP/jvm/kof-app.jar" ] || die "build did not produce kof-app.jar"
    mkdir -p "$KFVM_DATA" "$BIN_DIR"
    cp "$TMP/jvm/kof-app.jar" "$KFVM_DATA/kfvm.jar"
    chmod 644 "$KFVM_DATA/kfvm.jar"
    write_wrapper > "$BIN_DIR/kfvm.tmp"
    chmod 755 "$BIN_DIR/kfvm.tmp"
    mv "$BIN_DIR/kfvm.tmp" "$BIN_DIR/kfvm"
}

write_wrapper() {
    cat << EOF
#!/bin/sh
KFVM_HOME="\${KFVM_HOME:-\$HOME/.local/share/kof}"
JAR="$KFVM_DATA/kfvm.jar"
for d in "\$KFVM_HOME/current" "\$KFVM_HOME"/kof-*; do
    for j in "\$d/jdk/bin/java" "\$d/jdk/Contents/Home/bin/java"; do
        [ -x "\$j" ] && exec "\$j" -jar "\$JAR" "\$@"
    done
done
if [ -n "\${JAVA_HOME:-}" ] && [ -x "\$JAVA_HOME/bin/java" ]; then
    exec "\$JAVA_HOME/bin/java" -jar "\$JAR" "\$@"
fi
command -v java >/dev/null 2>&1 && exec java -jar "\$JAR" "\$@"
echo "[ERR]: No Java was found (install a Kof version or set JAVA_HOME)" >&2
exit 1
EOF
}

setup_path() {
    case ":$PATH:" in
        *":$BIN_DIR:"*) return ;;
    esac
    case "${SHELL:-}" in
        */zsh) rc="$HOME/.zshrc" ;;
        */bash) rc="$HOME/.bashrc" ;;
        *) rc="$HOME/.profile" ;;
    esac
    case "$BIN_DIR" in
        "$HOME"/*) dir="\$HOME${BIN_DIR#"$HOME"}" ;;
        *) dir="$BIN_DIR" ;;
    esac
    line="export PATH=\"$dir:\$PATH\""
    if [ -f "$rc" ] && grep -qF "$line" "$rc"; then
        return
    fi
    printf '\n%s\n' "$line" >> "$rc"
    say "added $BIN_DIR to PATH in $rc (open a new terminal or run: . $rc)"
}

install_kof_launcher() {
    command -v kof > /dev/null 2>&1 && return
    if [ ! -e "$KFVM_HOME/current" ]; then
        ln -sfn "$(dirname "$(dirname "$KOF")")" "$KFVM_HOME/current"
    fi
    mkdir -p "$BIN_DIR"
    cat > "$BIN_DIR/kof.tmp" << EOF
#!/bin/sh
exec "\${KFVM_HOME:-$KFVM_HOME}/current/bin/kof" "\$@"
EOF
    chmod 755 "$BIN_DIR/kof.tmp"
    mv "$BIN_DIR/kof.tmp" "$BIN_DIR/kof"
    say "installed kof launcher to $BIN_DIR/kof"
}

main() {
    require curl
    require tar
    require uname
    detect_os
    TMP="$(mktemp -d "${TMPDIR:-/tmp}/kfvm-install.XXXXXX")"
    trap 'rm -rf "$TMP"' EXIT INT TERM
    REQUIRED="$(version_to_int "$KOF_MIN")"
    locate_kof_bin || install_kof
    get_kfvm_source
    install_kof_launcher
    say "using $KOF ($("$KOF" version < /dev/null | head -n 1))"
    say "building kfvm"
    if build_native_kof_bin; then
        say "installed native binary to $BIN_DIR/kfvm"
    else
        build_jar
        say "installed $KFVM_DATA/kfvm.jar and launcher $BIN_DIR/kfvm"
    fi
    setup_path
    "$BIN_DIR/kfvm" -v < /dev/null || true
}

main "$@"
