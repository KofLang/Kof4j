#!/usr/bin/env bash
# auto-loop.sh — heartbeat de cron para o modo autônomo do opencode (AGENTS.md).
#
# "Re-dispacho é do humano ou de cron": enquanto o loop autônomo está ativo,
# um cronjob manda o PROMPT de re-disparo para a sessão do opencode a cada
# N minutos (padrão 30), o que re-dispara o agente sem intervenção humana.
#
# Uso:
#   scripts/auto-loop.sh start [sessionID] [intervalo-min]  # ativa (padrão: última sessão, 30 min)
#   scripts/auto-loop.sh stop                                # desativa (remove o cron)
#   scripts/auto-loop.sh status                              # estado atual
#   scripts/auto-loop.sh tick [--dry-run]                    # chamado pelo cron
set -euo pipefail

MARKER="kof-auto-loop"
SCRIPT=$(readlink -f "$0")
REPO=$(cd "$(dirname "$SCRIPT")/.." && pwd)
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/$MARKER"
STATE="$STATE_DIR/state"
LOG="$STATE_DIR/loop.log"
LOCK="$STATE_DIR/lock"

OPENCODE="${OPENCODE_BIN:-}"
if [ -z "$OPENCODE" ]; then
    OPENCODE=$(command -v opencode || true)
    [ -n "$OPENCODE" ] || OPENCODE="$HOME/.opencode/bin/opencode"
fi

DEFAULT_PROMPT="analize os documentos, verifique os gaps, identifique o que falta em nossos planos, trace um todo de implementação e continue o desenvolvimento"

last_session() {
    "$OPENCODE" session list -n 1 --format json \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])'
}

install_cron() {
    local n="$1" line
    line="*/$n * * * * $SCRIPT tick # $MARKER"
    ( { crontab -l 2>/dev/null | grep -vF "$MARKER" || true; }; echo "$line" ) | crontab -
}

remove_cron() {
    if crontab -l 2>/dev/null | grep -qF "$MARKER"; then
        { crontab -l 2>/dev/null | grep -vF "$MARKER" || true; } | crontab -
    fi
}

cmd_start() {
    local session="${1:-}" interval="${2:-30}"
    [ -n "$session" ] || session=$(last_session)
    case "$interval" in *[!0-9]*|'') echo "intervalo deve ser inteiro (minutos)" >&2; exit 1;; esac
    mkdir -p "$STATE_DIR"
    local prompt_q
    prompt_q=$(printf '%s' "${AUTOLOOP_PROMPT:-$DEFAULT_PROMPT}" | sed "s/'/'\\\\''/g")
    {
        echo "session=$session"
        echo "interval=$interval"
        echo "repo=$REPO"
        echo "prompt='$prompt_q'"
        echo "started=$(date -Is)"
    } > "$STATE"
    install_cron "$interval"
    echo "auto-loop ATIVO: sessão $session a cada ${interval}min (log: $LOG)"
    echo "prompt: ${prompt_q:0:60}..."
    echo "parar: $SCRIPT stop"
}

cmd_stop() {
    remove_cron
    rm -f "$STATE"
    echo "auto-loop PARADO (cron removido; estado em $STATE_DIR)"
}

cmd_status() {
    if [ -f "$STATE" ]; then
        echo "ATIVO:"; sed 's/^/  /' "$STATE"
    else
        echo "INATIVO (sem state em $STATE)"
    fi
    echo "cron:"
    crontab -l 2>/dev/null | grep -F "$MARKER" | sed 's/^/  /' || echo "  (nenhuma linha $MARKER)"
    if [ -f "$LOG" ]; then echo "últimos ticks:"; tail -n 5 "$LOG" | sed 's/^/  /'; fi
}

cmd_tick() {
    [ -f "$STATE" ] || exit 0
    # shellcheck disable=SC1090
    . "$STATE"
    local args=(run --session "$session" --dir "$repo" --auto "${prompt:-$DEFAULT_PROMPT}")
    if [ "${1:-}" = "--dry-run" ]; then
        echo "[dry-run] $OPENCODE ${args[*]}"
        return 0
    fi
    mkdir -p "$STATE_DIR"
    exec 9>"$LOCK"
    if ! flock -n 9; then
        echo "$(date -Is) tick pulado: run anterior ainda ativo" >> "$LOG"
        return 0
    fi
    echo "$(date -Is) tick -> $session" >> "$LOG"
    "$OPENCODE" "${args[@]}" >> "$LOG" 2>&1 || echo "$(date -Is) tick FALHOU (rc=$?)" >> "$LOG"
}

case "${1:-}" in
    start)  shift; cmd_start "${1:-}" "${2:-30}";;
    stop)   cmd_stop;;
    status) cmd_status;;
    tick)   shift; cmd_tick "${1:-}";;
    *)      echo "uso: $0 {start [sessionID] [min]|stop|status|tick [--dry-run]}" >&2; exit 1;;
esac
