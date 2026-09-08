#!/usr/bin/env bash
# kof-heartbeat.sh — heartbeat de 10min para o modo autônomo (sem crontab).
#
# Injeta o prompt de re-disparo na SESSÃO ABERTA via auto-loop.sh tick
# (--attach, nunca spawna headless concorrente). Usa o flock do auto-loop.sh
# para evitar sobreposição de ticks.
#
# Uso:
#   scripts/kof-heartbeat.sh start   # inicia o loop em background (nohup)
#   scripts/kof-heartbeat.sh stop    # mata o loop
#   scripts/kof-heartbeat.sh status  # mostra PID / se está ativo
set -euo pipefail

INTERVAL="${KOFO_HEARTBEAT_INTERVAL:-600}"   # segundos (padrão 10min)
LOG="/home/mel/.local/state/kof-auto-loop/loop.log"
SCRIPT_NAME="kof-heartbeat.sh"
PIDFILE="/tmp/$SCRIPT_NAME.pid"

start() {
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
        echo "já ativo (PID $(cat "$PIDFILE"))"
        return 0
    fi
    nohup bash "$0" loop > /dev/null 2>&1 &
    echo "$!" > "$PIDFILE"
    echo "heartbeat $((INTERVAL / 60))min iniciado (PID $!)"
}

stop() {
    if [ -f "$PIDFILE" ]; then
        kill "$(cat "$PIDFILE")" 2>/dev/null || true
        rm -f "$PIDFILE"
        echo "heartbeat parado"
    else
        echo "não ativo"
    fi
}

status() {
    if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
        echo "ATIVO (PID $(cat "$PIDFILE"), intervalo ${INTERVAL}s)"
    else
        echo "INATIVO"
    fi
}

loop() {
    while true; do
        cd "$(dirname "$0")/.."   # repo root
        scripts/auto-loop.sh tick >> "$LOG" 2>&1 || echo "$(date -Is) tick erro" >> "$LOG"
        sleep "$INTERVAL"
    done
}

case "${1:-start}" in
    start) start ;;
    stop) stop ;;
    status) status ;;
    loop) loop ;;
    *) echo "uso: $0 [start|stop|status]"; exit 1 ;;
esac