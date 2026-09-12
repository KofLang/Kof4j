#!/usr/bin/env bash
# gh-as-agent.sh — identidade de AGENTE para issues/PRs (AGENTS.md §fonte-da-verdade
# regra 7). Organização NÃO emite PAT; a identidade de agente é um GitHub App
# instalado na org (mesmo padrão dos agentes publicando via TEMM-CODE): o app
# comenta como <app>[bot], atribuído à org — nunca ao perfil pessoal da
# mantenedora.
#
# Setup (uma vez, humano):
#   1. github.com/settings/apps/new → cria a app (p.ex. "kof-agent-worker"),
#      Permissions: Issues/PR/Contents = Read&Write.
#   2. Generate a private key (PEM) — baixa .pem.
#   3. Install the app na org Kof-agent-worker (ou direto no repo KofLang/Kof4j).
#   4. Exporta no ambiente do agente (ou em ~/.config/kof/agent-app.env):
#        GH_APP_ID=...                      (numeric, na URL da app)
#        GH_APP_INSTALLATION_ID=...         (opcional; senão descobre por /installation)
#        GH_APP_PRIVATE_KEY_FILE=/caminho/kof-agent-worker.   (ou GH_APP_PRIVATE_KEY com o PEM inline)
#
# Uso:
#   scripts/gh-as-agent.sh token            # imprima "export GH_TOKEN=ghs_..."
#   eval "$(scripts/gh-as-agent.sh token)"   # aplica no shell corrente
#   scripts/gh-as-agent.sh check             # identidade atual + validade
#   scripts/gh-as-agent.sh whoami            # gh api user --jq .login
set -euo pipefail

ENV_FILE="${KOF_AGENT_APP_ENV:-$HOME/.config/kof/agent-app.env}"
if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    . "$ENV_FILE"
fi

b64url() { openssl base64 -A -e -nopad | tr '+/' '-_' | tr -d '='; }

mint_jwt() {
    local app_id="$1" key_file="$2" now iat exp header payload sig
    now=$(date +%s); iat=$((now - 60)); exp=$((now + 540))
    header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
    payload=$(printf '{"iat":%d,"exp":%d,"iss":"%s"}' "$iat" "$exp" "$app_id" | b64url)
    sig=$(printf '%s.%s' "$header" "$payload" \
        | openssl dgst -sha256 -sign "$key_file" -binary | b64url)
    printf '%s.%s.%s' "$header" "$payload" "$sig"
}

cmd_token() {
    [ -n "${GH_APP_ID:-}" ] || { echo "GH_APP_ID ausente (configure em $ENV_FILE)" >&2; exit 1; }
    local key_file="${GH_APP_PRIVATE_KEY_FILE:-}"
    if [ -z "$key_file" ] && [ -n "${GH_APP_PRIVATE_KEY:-}" ]; then
        key_file=$(mktemp); printf '%s\n' "$GH_APP_PRIVATE_KEY" > "$key_file"
    fi
    [ -n "$key_file" ] && [ -f "$key_file" ] || { echo "chave privada ausente (GH_APP_PRIVATE_KEY_FILE / GH_APP_PRIVATE_KEY / $ENV_FILE)" >&2; exit 1; }
    local jwt inst
    jwt=$(mint_jwt "$GH_APP_ID" "$key_file")
    inst="${GH_APP_INSTALLATION_ID:-}"
    if [ -z "$inst" ]; then
        inst=$(curl -s -H "Authorization: Bearer $jwt" -H "Accept: application/vnd.github+json" \
            "https://api.github.com/app/installations" \
            | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["id"])' 2>/dev/null || true)
    fi
    [ -n "$inst" ] || { echo "nenhuma installation visível para a app (instalou na org?)" >&2; exit 1; }
    curl -s -X POST -H "Authorization: Bearer $jwt" -H "Accept: application/vnd.github+json" \
        "https://api.github.com/app/installations/$inst/access_tokens" \
        | python3 -c 'import json,sys; d=json.load(sys.stdin); print("export GH_TOKEN=" + d["token"])'
}

cmd_whoami() { gh api user --jq .login; }

cmd_check() {
    if [ -z "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ]; then
        echo "sem GH_TOKEN de app no ambiente — agente NÃO deve comentar como melmonfre" >&2
        echo "(provisione o app: ver cabeçalho deste script; eval \"\$(scripts/gh-as-agent.sh token)\")" >&2
        exit 1
    fi
    local login
    login=$(cmd_whoami)
    case "$login" in
        *"[bot]"|*"-bot"|Kof-agent-worker*) echo "OK: identidade de agente = $login";;
        melmonfre) echo "ERRO: identidade = melmonfre (perfil pessoal) — NÃO postar issues/PRs assim" >&2; exit 1;;
        *) echo "ATENÇÃO: identidade = $login (não é app do worker) — confirme antes de postar";;
    esac
}

case "${1:-check}" in
    token) cmd_token;;
    whoami) cmd_whoami;;
    check) cmd_check;;
    *) echo "uso: $0 {token|whoami|check}" >&2; exit 1;;
esac
