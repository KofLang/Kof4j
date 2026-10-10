#!/usr/bin/env bash
#
# lib-agent-test.sh — fixture compartilhada dos testes da automação de agentes
# (auto-loop, issue-watcher, dispatch gate, evidência). Sem rede e SEM writes
# reais no GitHub: `gh`, `opencode` e `curl` são FAKES no PATH; o estado vai
# para um XDG_STATE_HOME temporário; o relógio é o do host (os testes não
# dependem de sleep — o cooldown é forçado por env).
#
# O fake `opencode` só CONTA chamadas (linha `CALL:` em opencode.calls) — é a
# métrica central da Onda 1: chamadas evitadas ao modelo.
#
# Uso (dentro de um teste):  . scripts/tests/lib-agent-test.sh ; mk_env ; ...
set -uo pipefail

FAILED=0
pass() { echo "  ok  — $1"; }
fail() { echo "  FAIL— $1"; FAILED=1; }

REPO_ROOT="$(git rev-parse --show-toplevel)"

# --- ambiente isolado -------------------------------------------------------
TMPS=()
trap 'rm -rf "${TMPS[@]}"' EXIT

# Caminho de temp que o BASH e o PYTHON NATIVO do Windows entendem (#655): num
# host MSYS/Cygwin `mktemp -d` devolve `/tmp/...`, que o python nativo nao abre
# (FileNotFoundError -> manifestos saem vazios -> falso PASS). `cygpath -m`
# converte para `C:/...`, valido nos dois. Fora do Windows, comportamento igual.
tmp_root() {
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -m "${TMPDIR:-${TEMP:-${TMP:-/tmp}}}"
    else
        printf '%s' "${TMPDIR:-/tmp}"
    fi
}

mk_env() {
    TMP="$(mktemp -d "$(tmp_root)/kof-agent-test.XXXXXX")"
    TMPS+=("$TMP")
    export XDG_STATE_HOME="$TMP/state"
    export HOME="$TMP/home"
    mkdir -p "$XDG_STATE_HOME" "$HOME" "$TMP/bin"
    export FAKE_GH_DIR="$TMP/fake"
    mkdir -p "$FAKE_GH_DIR"
    : > "$FAKE_GH_DIR/issues.tsv"
    : > "$FAKE_GH_DIR/gh.calls"
    : > "$FAKE_GH_DIR/opencode.calls"
    unset FAKE_GH_FAIL FAKE_SERVER_DOWN FAKE_OPENCODE_RC AGENT_GATE_MODE
    export OPENCODE_BIN="$TMP/bin/opencode"
    export AGENT_FP_CI=0
    write_fakes
    export PATH="$TMP/bin:$PATH"
}

write_fakes() {
    cat > "$TMP/bin/gh" <<'EOF'
#!/usr/bin/env bash
# FAKE gh — serve fixtures de $FAKE_GH_DIR; nunca escreve no GitHub.
D="${FAKE_GH_DIR:?}"
echo "gh $*" >> "$D/gh.calls"
[ -n "${FAKE_GH_FAIL:-}" ] && { echo "gh: falha simulada" >&2; exit 1; }
args="$*"
case "$args" in
  *"/comments"*)
      n=$(printf '%s' "$args" | sed -n 's#.*issues/\([0-9]*\)/comments.*#\1#p')
      f="$D/comments-$n.tsv"          # id<TAB>login por linha
      if printf '%s' "$args" | grep -q -- '\.\[-1\]\.id'; then   # forma legada
          if [ -s "$f" ]; then tail -n1 "$f" | cut -f1; else echo 0; fi
      else
          [ -f "$f" ] && cat "$f"
      fi
      exit 0;;
  *"issue list"*)   cut -f1 "$D/issues.tsv"; exit 0;;
  *"issues?"*)      cat "$D/issues.tsv"; exit 0;;       # number<TAB>title<TAB>body
  *"run list"*)     [ -f "$D/runs.txt" ] && cat "$D/runs.txt"; exit 0;;
  *"issue close"*)  echo "CLOSE: $args" >> "$D/closes.log"; exit 0;;
esac
exit 0
EOF
    cat > "$TMP/bin/opencode" <<'EOF'
#!/usr/bin/env bash
# FAKE opencode — conta chamadas; hook opcional muda o estado (run "produtivo").
D="${FAKE_GH_DIR:?}"
# `opencode stats`: fixture opencode-stats.txt (formato real, com caixas); NÃO conta como CALL
if [ "${1:-}" = "stats" ]; then
    printf 'STATS: %s\n' "$*" >> "$D/stats.calls"
    [ -n "${FAKE_STATS_FAIL:-}" ] && exit 1
    [ -f "$D/opencode-stats.txt" ] && cat "$D/opencode-stats.txt"
    exit 0
fi
printf 'CALL: %s\n' "$*" >> "$D/opencode.calls"
[ -x "$D/opencode.hook" ] && "$D/opencode.hook"
exit "${FAKE_OPENCODE_RC:-0}"
EOF
    cat > "$TMP/bin/curl" <<'EOF'
#!/usr/bin/env bash
# FAKE curl — só o health-check do servidor TUI.
case "$*" in *global/health*) [ -n "${FAKE_SERVER_DOWN:-}" ] && exit 22; exit 0;; esac
exit 0
EOF
    chmod +x "$TMP/bin/gh" "$TMP/bin/opencode" "$TMP/bin/curl"
}

# --- helpers de fixture -----------------------------------------------------
opencode_calls() { grep -c '^CALL:' "$FAKE_GH_DIR/opencode.calls" 2>/dev/null || true; }
last_opencode_call() { grep '^CALL:' "$FAKE_GH_DIR/opencode.calls" | tail -n1; }

# issue N "título" "corpo" — (re)escreve a linha da issue em issues.tsv
set_issue() {
    local n="$1" title="$2" body="${3:-}"
    grep -v -P "^$n\t" "$FAKE_GH_DIR/issues.tsv" > "$FAKE_GH_DIR/issues.tmp" || true
    printf '%s\t%s\t%s\n' "$n" "$title" "$body" >> "$FAKE_GH_DIR/issues.tmp"
    sort -n "$FAKE_GH_DIR/issues.tmp" > "$FAKE_GH_DIR/issues.tsv"; rm -f "$FAKE_GH_DIR/issues.tmp"
}
# add_comment N ID LOGIN
add_comment() { printf '%s\t%s\n' "$2" "$3" >> "$FAKE_GH_DIR/comments-$1.tsv"; }
close_issue_fixture() {
    grep -v -P "^$1\t" "$FAKE_GH_DIR/issues.tsv" > "$FAKE_GH_DIR/issues.tmp" || true
    mv "$FAKE_GH_DIR/issues.tmp" "$FAKE_GH_DIR/issues.tsv"
}

telemetry_file() { echo "$XDG_STATE_HOME/kof-agent/dispatch.jsonl"; }

assert_eq() { # esperado obtido mensagem
    if [ "$1" = "$2" ]; then pass "$3"; else fail "$3 (esperado '$1', obtido '$2')"; fi
}
assert_contains() { # texto agulha mensagem
    case "$1" in *"$2"*) pass "$3";; *) fail "$3 (sem '$2' em: ${1:0:200})";; esac
}
finish() {
    if [ "$FAILED" -eq 0 ]; then echo "TODOS OS CENÁRIOS PASSARAM"; exit 0; fi
    echo "HÁ CENÁRIOS FALHANDO"; exit 1
}

# repo git temporário com DOING/known-bugs/docs-development (auto-loop e fingerprint)
mk_repo() {
    REPO="$TMP/repo"
    mkdir -p "$REPO/docs/bugs-and-gaps" "$REPO/docs/development"
    ( cd "$REPO" && git init -q -b beta-0.4.0 . \
        && git config user.email t@t && git config user.name t \
        && echo "doing v0" > DOING.md \
        && echo "bugs v0" > docs/bugs-and-gaps/known-bugs.md \
        && echo "plan" > docs/development/plan.md \
        && git add -A && git commit -q -m init )
}
