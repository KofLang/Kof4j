#!/usr/bin/env bash
# auto-loop.sh — heartbeat de cron para o modo autônomo do opencode (AGENTS.md).
#
# "Re-dispacho é do humano ou de cron": enquanto o loop autônomo está ativo,
# um cronjob manda o PROMPT de re-disparo para a SESSÃO ABERTA (o servidor
# TUI vivo) a cada N minutos (padrão 30), o que re-dispara o agente sem
# intervenção humana. Usa `opencode run --attach` — INJETAR na sessão viva,
# nunca spawnar um agente headless concorrente (isso criava "outra sessão").
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

# Servidor TUI vivo da sessão aberta (porta fixa do modo autônomo).
SERVER="${OPENCODE_SERVER_URL:-http://127.0.0.1:9092}"

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
    # INJETAR na sessão aberta via servidor TUI vivo (--attach) — nunca
    # spawnar agente headless concorrente (isso criava "outra sessão").
    local args=(run --session "$session" --dir "$repo" --attach "$SERVER" --auto "${prompt:-$DEFAULT_PROMPT}")
    if [ "${1:-}" = "--dry-run" ]; then
        echo "[dry-run] $OPENCODE ${args[*]}"
        return 0
    fi
    # servidor TUI fora do ar → não dispara (sessão aberta não existe).
    if ! curl -s -o /dev/null -m 5 "$SERVER/global/health"; then
        echo "$(date -Is) tick pulado: servidor $SERVER fora do ar" >> "$LOG"
        return 0
    fi
    mkdir -p "$STATE_DIR"
    exec 9>"$LOCK"
    if ! flock -n 9; then
        # WATCHDOG: lock presa há mais de AUTOLOOP_MAX_MIN (padrão 240) = run
        # pendurado. O zumbi real de 07/09 viveu 4h sem produzir nada; um
        # turno ativo legítimo (suíte longa + vários commits) pode passar de
        # ~2h, então o teto é 4h — mata o zumbi sem matar trabalho de verdade.
        # Só conta a partir do lock.held (marcador escrito ao adquirir); run
        # sem marcador (antecede a feature) tem age=0 e nunca é tocado.
        local age_min max holder held_since
        age_min=0
        if [ -f "$LOCK.held" ]; then
            held_since=$(cat "$LOCK.held" 2>/dev/null || echo 0)
            case "$held_since" in (*[!0-9]*|'') held_since=0;; esac
            age_min=$(( ( $(date +%s) - held_since ) / 60 ))
        fi
        max="${AUTOLOOP_MAX_MIN:-240}"
        if [ "$age_min" -ge "$max" ]; then
            holder=$(fuser "$LOCK" 2>/dev/null | tr -s ' \t' '\n' | grep -E '^[0-9]+$' | grep -vx "$$" | tr '\n' ' ' || true)
            echo "$(date -Is) lock STALE (${age_min}min >= ${max}min) — matando holder(s): ${holder:-nenhum}" >> "$LOG"
            if [ -n "$holder" ]; then
                # shellcheck disable=SC2086
                kill $holder 2>/dev/null || true
                sleep 2
            fi
            exec 9>"$LOCK"
            if ! flock -n 9; then
                echo "$(date -Is) tick pulado: lock ainda ocupada após kill do holder stale" >> "$LOG"
                return 0
            fi
        else
            echo "$(date -Is) tick pulado: run anterior ainda ativo (${age_min}min < ${max}min)" >> "$LOG"
            return 0
        fi
    fi
    date +%s > "$LOCK.held"
    echo "$(date -Is) tick -> $session (attach $SERVER)" >> "$LOG"
    "$OPENCODE" "${args[@]}" >> "$LOG" 2>&1 || echo "$(date -Is) tick FALHOU (rc=$?)" >> "$LOG"
    rm -f "$LOCK.held"
}

case "${1:-}" in
    start)  shift; cmd_start "${1:-}" "${2:-30}";;
    stop)   cmd_stop;;
    status) cmd_status;;
    tick)   shift; cmd_tick "${1:-}";;
    *)      echo "uso: $0 {start [sessionID] [min]|stop|status|tick [--dry-run]}" >&2; exit 1;;
esac
