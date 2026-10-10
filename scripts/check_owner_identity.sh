#!/usr/bin/env bash
# check_owner_identity.sh — gate da regra absoluta de identidade (`D-AGENT-IDENTITY-IPPORT`, 01/10).
#
# Regra (AGENTS.md §Authority / §Multi-agent state / §Operating loop):
#   todo claim em DOING.md / DOING.pt_BR.md datado >= 01/10 deve levar
#   `owner = <ipv4-local>:<porta-opencode>` (EN) / `dona = <ipv4-local>:<porta-opencode>` (PT).
#   IPv4 puro sem :porta, ou "this session"/"esta sessão", é INVÁLIDO.
#
# Estratégia:
#   - parseia linhas de claim (começam com "> **" em DOING*.md)
#   - extrai a data DD/MM do primeiro campo após o emoji de estado
#   - se a data >= 01/10 (ano corrente, 2026), exige o padrão IP:PORTA
#   - claims anteriores a 01/10 ficam isentos (a regra foi declarada em 01/10)
#   - rc=1 lista cada violação (arquivo:linha + resumo).
#
# Uso:
#   scripts/check_owner_identity.sh              # scan nos dois arquivos
#   scripts/check_owner_identity.sh --selftest   # teste com fixtures (não exige repo)
#
# Saída:
#   rc=0 — todos os claims >= 01/10 estão conformes
#   rc=1 — pelo menos um claim violou a regra (mensagem lista cada violação)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOING_FILES=("$REPO_ROOT/DOING.md" "$REPO_ROOT/DOING.pt_BR.md")

CUTOFF_D=1
CUTOFF_M=10

# ---- selftest ----
selftest() {
    local tmp
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' EXIT
    cat > "$tmp/ok.md" <<'OK'
> **✅ DONE 01/10 (lane x — owner = 192.168.1.1:9092): good.** body
> **🔄 IN PROGRESS 02/10 (lane y — dona = 10.0.0.5:1234): also good.** body
> **⏹ TRIAGE/STOP 30/09 (lane z — owner = 192.168.1.1): exempt (before 01/10).** body
OK
    cat > "$tmp/bad.md" <<'BAD'
> **✅ DONE 01/10 (lane x — owner = 192.168.1.1): BAD, no port.** body
> **🔄 IN PROGRESS 03/10 (lane y — owner = this session): BAD, bare session.** body
BAD
    if ! DOING_MD="$tmp/ok.md" bash "$0" --file >/dev/null 2>&1; then
        echo "selftest FAIL: ok.md rejected" >&2
        exit 2
    fi
    if DOING_MD="$tmp/bad.md" bash "$0" --file >/dev/null 2>&1; then
        echo "selftest FAIL: bad.md accepted" >&2
        exit 2
    fi
    echo "selftest OK"
    exit 0
}

if [[ "${1:-}" == "--selftest" ]]; then
    selftest
fi

# scan_one <file> -> prints violations to stdout (empty if clean)
scan_one() {
    local file="$1"
    local n=0
    while IFS= read -r line; do
        n=$((n+1))
        # Claim lines start with "> **"
        [[ "$line" == "> **"* ]] || continue
        # Extract the date "DD/MM" from the leading status token (DONE/FEITO/IN PROGRESS/
        # EM PROGRESSO/TRIAGE/STOP/FIXED/CORRIGIDO/...). The date is the first
        # DD/MM-looking pair in the first ~80 chars.
        local head="${line:0:120}"
        local d m
        if [[ "$head" =~ ([0-9]{2})/([0-9]{2}) ]]; then
            d="${BASH_REMATCH[1]}"; m="${BASH_REMATCH[2]}"
        else
            continue  # no date = not a claim line we police
        fi
        # enforce cutoff: date >= 01/10 (m>10 OR m==10 AND d>=1)
        local mm=$((10#$m)) dd=$((10#$d))
        if (( mm > CUTOFF_M )) || { (( mm == CUTOFF_M )) && (( dd >= CUTOFF_D )); }; then
            # Look for owner=/dona= and require <ipv4>:<port> immediately after.
            # If the value after owner=/dona= is "this session"/"esta sessão", reject.
            # If the value is a bare IPv4 with no :port, reject.
            local val
            local re='(owner|dona)[[:space:]]*=[[:space:]]*[^)]+'
            if [[ "$line" =~ $re ]]; then
                val="${BASH_REMATCH[0]}"
                val="${val#*=}"
                val="${val#"${val%%[![:space:]]*}"}"
                val="${val%"${val##*[![:space:]]}"}"
                if [[ "$val" == "this session"* || "$val" == "esta sessão"* ]]; then
                    echo "$file:$n: bare session claim — needs owner=<ipv4>:<port> (D-AGENT-IDENTITY-IPPORT)"
                    continue
                fi
                # must contain <ipv4>:<port> somewhere in val
                local re2='[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+'
                if ! [[ "$val" =~ $re2 ]]; then
                    echo "$file:$n: owner value '$val' lacks :<port> (D-AGENT-IDENTITY-IPPORT)"
                fi
            fi
        fi
    done < "$file"
}

VIOL=0
if [[ "${1:-}" == "--file" ]]; then
    # selftest mode: single file from DOING_MD env
    out=$(scan_one "${DOING_MD:?}")
    if [[ -n "$out" ]]; then printf '%s\n' "$out"; exit 1; fi
    exit 0
fi

for f in "${DOING_FILES[@]}"; do
    [[ -f "$f" ]] || continue
    v=$(scan_one "$f" || true)
    if [[ -n "$v" ]]; then
        printf '%s\n' "$v"
        VIOL=1
    fi
done

if (( VIOL )); then
    echo "check_owner_identity: REGRA ABSOLUTA VIOLADA — todo claim >= 01/10 em DOING.md precisa de owner=<ipv4>:<porta> (gate, D-AGENT-IDENTITY-IPPORT)." >&2
    exit 1
fi
echo "check_owner_identity: OK (todos os claims >= 01/10 levam IP:PORTA)"
exit 0
