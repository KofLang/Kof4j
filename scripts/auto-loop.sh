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
#   scripts/auto-loop.sh start [sessionID] [intervalo-min] [porta]  # ativa (padrão: última sessão, 30 min)
#   scripts/auto-loop.sh stop                                # desativa (remove o cron)
#   scripts/auto-loop.sh status                              # estado atual
#   scripts/auto-loop.sh tick [--dry-run]                    # chamado pelo cron
#
# AUTOLOOP_NAME (env): nomeia o loop (padrão: kof-auto-loop). Cada nome tem
# estado e linha de cron PRÓPRIOS — use `AUTOLOOP_NAME=loop-b` nos start/stop/
# status/tick para rodar dois heartbeats em sessões diferentes ao mesmo tempo
# sem um apagar o outro (o cron gerado carrega o env p/ o próprio tick).
#
# Porta (3º arg): PINA o servidor do heartbeat em http://127.0.0.1:<porta>
# (ex.: start ses_xxx 2 9093). Sem o arg, resolve dinamicamente. Necessário
# porque os servidores TUI compartilham storage e CONHECEM todas as sessões —
# a resolução dinâmica pode escolher a porta da lane errada (regra dos crons:
# nunca cruzar sessões; AGENTS.md "Two sessions, two crons").
set -euo pipefail

# Nome do loop: permite DOIS heartbeats simultâneos (sessões/portas diferentes,
# regra "never cross them"). O padrão mantém o comportamento legado byte a byte;
# AUTOLOOP_NAME muda MARKER e portanto STATE_DIR/LOG/LOCK e a linha do cron.
MARKER="${AUTOLOOP_NAME:-kof-auto-loop}"
case "$MARKER" in *[!A-Za-z0-9_-]*) echo "AUTOLOOP_NAME invalido (use [A-Za-z0-9_-])" >&2; exit 1;; esac
SCRIPT=$(readlink -f "$0")
REPO=$(cd "$(dirname "$SCRIPT")/.." && pwd)
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/$MARKER"
STATE="$STATE_DIR/state"
LOG="$STATE_DIR/loop.log"
LOCK="$STATE_DIR/lock"
# Gate de despacho (Onda 1): decide SEM modelo se o agente deve ser chamado.
GATE="$(dirname "$SCRIPT")/agent-dispatch-gate.sh"

# Servidor TUI vivo da sessão aberta. A porta NÃO é fixa: cada sessão do TUI
# escolhe a sua (9092 pode ser outra sessão — o heartbeat dela não é o nosso).
# OPENCODE_SERVER_URL sobrescreve; senão o tick resolve dinamicamente a porta
# que realmente hospeda $session (probe /session/<id> em cada servidor vivo).
SERVER="${OPENCODE_SERVER_URL:-}"

resolve_server() {
    local sid="$1" port body
    for port in 9091 9092 9093 9094 9095; do
        body=$(curl -s -m 3 "http://127.0.0.1:$port/session/$sid" 2>/dev/null) || continue
        case "$body" in *'"id":"'"$sid"'"'*) echo "http://127.0.0.1:$port"; return 0;; esac
    done
    return 1
}

OPENCODE="${OPENCODE_BIN:-}"
if [ -z "$OPENCODE" ]; then
    OPENCODE=$(command -v opencode || true)
    [ -n "$OPENCODE" ] || OPENCODE="$HOME/.opencode/bin/opencode"
fi

DEFAULT_PROMPT="analize os documentos, verifique os gaps, identifique o que falta em nossos planos em docs/development, trace um todo de implementação e continue o desenvolvimento. REGRA DE EVOLUCAO VERDADEIRA (ordem da mantenedora 07/11): cada ciclo PRECISA produzir mudanca real e medida — codigo, teste, doc normativo ou correcao — NUNCA apenas um tick TRIAGE repetindo 'verificado-sem-mudanca'. Antes de encerrar, rode scripts/check_cycle_evolution.sh: se ele disser NO-EVOLUTION, o ciclo NAO pode ser fechado com um tick — escolha uma frente real (uma linha do plano que o host sustenta, um gap medivel, um bug de outra lane que voce possa reportar com prova) ou registre honestamente que esta BLOQUEADO por decisao da mantenedora, nomeando a decisao. Tick vazio e mentira operacional. REGRA DE EVOLUCAO DA LINGUAGEM (ordem da mantenedora 07/11, mais forte que a anterior): o ciclo NAO pode terminar sem uma mudanca que faca a LINGUAGEM Kof avancar — um primitivo novo (ex.: math.log/math.exp), uma face de stdlib, uma correcao de compilador/emissor, um alvo que passa a funcionar. Uma biblioteca em Kof puro sobre primitivos que JA existem e bem-vinda, mas NAO substitui a evolucao da linguagem: se o ciclo so adicionou uma lib, o proximo ciclo DEVE atacar o primitivo ausente que ela expos. 'Nao ha nada para fazer' e PROIBIDO como resposta: se uma frente parece bloqueada, MEDA o compilador (grep no dispatch, no emissor, no runtime de cada alvo) antes de declarar bloqueio — o bloqueio real quase sempre e um primitivo que falta e que ESTE ciclo pode adicionar. Antes de encerrar rode scripts/check_cycle_evolution.sh; se ele disser NO-EVOLUTION, o ciclo NAO fecha. AUTORIDADE DE CODEOWNER (mantenedora 07/11): esta lane tem autonomia TOTAL para decidir e executar, desde que a linguagem fique COMPLETA e PORTAVEL. As QUATRO GUARDRAILS INVIOLAVEIS: (1) TODA evolucao e INCREMENTAL — um passo coeso por ciclo, nunca um big-bang; (2) NAO quebramos o FRONTEND da linguagem — sintaxe/gramatica/parse existente continua valendo; (3) NAO quebramos CODIGO QUE FUNCIONA — zero regressao, additive-compatibility, a suite inteira verde; (4) EVOLUA A LINGUAGEM DE VERDADE — primitivo/face/alvo novo que funciona, nunca um tick vazio. Se uma decisao de arquitetura parecer necessaria, ela e SUA para tomar dentro dessas quatro guardrails; so pare diante de semantica congelada que as guardrails nao cobrem, colisao de lane ou regressao externa inexplicada. ORDEM 07/11 (segunda, mais ampla): o FUTURE-FREEZE CAIU — docs/development/future/ pode ser promovido e implementado, e a meta e DESENVOLVER TUDO: nao deixar gaps, nao deixar stubs, nao deixar falhas, nao deixar erros, CORRIGIR TODAS AS ISSUES abertas. Revise tudo que esta dado como garantido (docs com state: implemented, matrizes de paridade, 'DONE' em planos) contra o codigo real — um 'garantido' que nao se prova no codigo e um gap. Trabalhe em LOTES coesos: cada ciclo fecha um conjunto de issues relacionadas com prova executada, nunca um tick vazio. A ordem de prioridade: (1) bugs de correcao (o codigo faz a coisa errada), (2) gaps de paridade entre alvos, (3) features de stdlib/linguagem, (4) planos de future/ promovidos um a um."

last_session() {
    "$OPENCODE" session list -n 1 --format json \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])'
}

install_cron() {
    local n="$1" line
    if [ "$MARKER" = "kof-auto-loop" ]; then
        line="*/$n * * * * $SCRIPT tick # $MARKER"
    else
        line="*/$n * * * * env AUTOLOOP_NAME=$MARKER $SCRIPT tick # $MARKER"
    fi
    ( { crontab -l 2>/dev/null | grep -vE "# ${MARKER}\$" || true; }; echo "$line" ) | crontab -
}

remove_cron() {
    if crontab -l 2>/dev/null | grep -qE "# ${MARKER}\$"; then
        { crontab -l 2>/dev/null | grep -vE "# ${MARKER}\$" || true; } | crontab -
    fi
}

cmd_start() {
    local session="${1:-}" interval="${2:-30}" port="${3:-}"
    [ -n "$session" ] || session=$(last_session)
    case "$interval" in *[!0-9]*|'') echo "intervalo deve ser inteiro (minutos)" >&2; exit 1;; esac
    case "$port" in '') ;; *[!0-9]*) echo "porta deve ser inteira (ou vazia p/ resolucao dinamica)" >&2; exit 1;; esac
    mkdir -p "$STATE_DIR"
    local prompt_q
    prompt_q=$(printf '%s' "${AUTOLOOP_PROMPT:-$DEFAULT_PROMPT}" | sed "s/'/'\\\\''/g")
    local server_q=""
    [ -z "$server_q" ] && [ -n "$port" ] && server_q="http://127.0.0.1:$port"
    if [ -z "$server_q" ]; then
        server_q=$(resolve_server "$session" || true)
    fi
    {
        echo "session=$session"
        echo "interval=$interval"
        echo "repo=$REPO"
        echo "prompt='$prompt_q'"
        echo "gate_mode=${AGENT_GATE_MODE:-active}"
        [ -n "$server_q" ] && echo "server=$server_q"
        echo "started=$(date -Is)"
    } > "$STATE"
    install_cron "$interval"
    echo "auto-loop ATIVO: sessão $session a cada ${interval}min (log: $LOG)"
    [ -n "$server_q" ] && echo "server fixado: $server_q" || echo "AVISO: nenhum servidor vivo resolveu a sessão — o tick vai tentar a cada rodada"
    echo "prompt: ${prompt_q:0:60}..."
    if [ "$MARKER" = "kof-auto-loop" ]; then
        echo "parar: $SCRIPT stop"
    else
        echo "parar: AUTOLOOP_NAME=$MARKER $SCRIPT stop"
    fi
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
    crontab -l 2>/dev/null | grep -E "# ${MARKER}\$" | sed 's/^/  /' || echo "  (nenhuma linha $MARKER)"
    if [ -f "$LOG" ]; then echo "últimos ticks:"; tail -n 5 "$LOG" | sed 's/^/  /'; fi
}

cmd_tick() {
    [ -f "$STATE" ] || exit 0
    # shellcheck disable=SC1090
    . "$STATE"
    # INJETAR na sessão aberta via servidor TUI vivo (--attach) — nunca
    # spawnar agente headless concorrente (isso criava "outra sessão").
    # OPENCODE_SERVER_URL força uma porta; senão usa a gravada no state
    # (start resolveu na origem); senão resolve dinamicamente.
    SERVER="${OPENCODE_SERVER_URL:-${server:-}}"
    if [ -z "$SERVER" ]; then
        SERVER=$(resolve_server "$session") || SERVER="http://127.0.0.1:9093"
    fi
    local args=(run --session "$session" --dir "$repo" --attach "$SERVER" --auto "${prompt:-$DEFAULT_PROMPT}")
    # Onda 1: estado legado (sem gate_mode) roda em SHADOW = comportamento antigo
    # (chama todo tick) + registro do que o gate faria; `active` pula sem novidade.
    local gmode="${gate_mode:-shadow}" gflag=()
    [ "$gmode" = "shadow" ] && gflag=(--shadow)
    if [ "${1:-}" = "--dry-run" ]; then
        echo "[dry-run] $OPENCODE ${args[*]}"
        echo "[dry-run] gate_mode=$gmode $("$GATE" decide auto-loop --session "$session" --repo "$repo" --dry-run 2>&1 | head -n1)"
        return 0
    fi
    # servidor TUI fora do ar → não dispara (sessão aberta não existe).
    if ! curl -s -o /dev/null -m 5 "$SERVER/global/health"; then
        echo "$(date -Is) tick pulado: servidor $SERVER fora do ar" >> "$LOG"
        "$GATE" note auto-loop --session "$session" --decision skip --reason server_down || true
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
            "$GATE" note auto-loop --session "$session" --decision skip --reason agent_busy || true
            return 0
        fi
    fi
    date +%s > "$LOCK.held"
    # Gate de despacho: decide com o lock já adquirido (sem corrida entre ticks).
    local gout grc=0 t0 run_rc=0
    gout=$("$GATE" decide auto-loop --session "$session" --repo "$repo" "${gflag[@]}") || grc=$?
    if [ "$gmode" != "shadow" ] && [ "$grc" -ne 0 ]; then
        echo "$(date -Is) tick pulado pelo gate (rc=$grc): $gout" >> "$LOG"
        rm -f "$LOCK.held"
        return 0
    fi
    echo "$(date -Is) tick -> $session (attach $SERVER) [gate=$gmode: $gout]" >> "$LOG"
    t0=$(date +%s)
    "$OPENCODE" "${args[@]}" >> "$LOG" 2>&1 || run_rc=$?
    [ "$run_rc" -eq 0 ] || echo "$(date -Is) tick FALHOU (rc=$run_rc)" >> "$LOG"
    "$GATE" record auto-loop --session "$session" --rc "$run_rc" --duration $(( $(date +%s) - t0 )) || true
    rm -f "$LOCK.held"
}

# Onda 1: liga/desliga o gate sem reiniciar o cron (rollout shadow -> active).
cmd_set_mode() {
    local m="${1:-}"
    case "$m" in active|shadow) ;; *) echo "uso: $0 set-mode {active|shadow}" >&2; exit 1;; esac
    [ -f "$STATE" ] || { echo "sem state em $STATE (rode start)" >&2; exit 1; }
    if grep -q '^gate_mode=' "$STATE"; then sed -i "s/^gate_mode=.*/gate_mode=$m/" "$STATE"
    else echo "gate_mode=$m" >> "$STATE"; fi
    echo "gate_mode=$m"
}

case "${1:-}" in
    start)  shift; cmd_start "${1:-}" "${2:-30}" "${3:-}";;
    stop)   cmd_stop;;
    status) cmd_status;;
    tick)   shift; cmd_tick "${1:-}";;
    set-mode) shift; cmd_set_mode "${1:-}";;
    stats)  shift; exec "$GATE" stats "$@";;
    *)      echo "uso: $0 {start [sessionID] [min]|stop|status|tick [--dry-run]|set-mode {active|shadow}|stats [--since 24h]}" >&2; exit 1;;
esac
