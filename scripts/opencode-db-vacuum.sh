#!/usr/bin/env bash
# opencode-db-vacuum.sh — encolhe o opencode.db inflado (causa das mortes de sessao por OOM/IO).
# Uso: rode de um terminal NORMAL, com TODOS os servidores opencode PARADOS (fora do opencode).
#
# Por que existe: o opencode grava cada atualizacao de mensagem no WAL/event-stream; quedas
# repetidas de sessao deixam dezenas de GB em paginas orfas no .db. Um `VACUUM` com o servidor
# vivo NAO libera (lock exclusivo = outra morte). Este script so roda parado, com backup verificado.
#
# Medido 06/10 no host do maintainer: antes=69.3GiB (db=74.4GiB em paginas, wal=68GiB) ->
# depois=2.94GiB, 1 VACUUM, sessoes intactas. quick_check ok antes e depois.
set -euo pipefail

DB="${OPENCODE_DB:-$HOME/.local/share/opencode/opencode.db}"
BAK="${OPENCODE_BAK:-$HOME/.local/share/opencode/backups}"

echo "== 1) servidores opencode precisam estar PARADOS =="
if pgrep -x opencode >/dev/null 2>&1; then
  echo "ainda ha processo 'opencode' vivo (pids: $(pgrep -x opencode | tr '\n' ' '))."
  echo "FECHA o opencode (todas as janelas/servidores) e rode de novo."
  exit 1
fi

echo "== 2) espaco em disco =="
FREE_GB=$(df -BG --output=avail "$HOME" | tail -1 | tr -dc '0-9')
[ "$FREE_GB" -ge 80 ] || { echo "livre=${FREE_GB}G < 80G (preciso p/ backup+VACUUM)"; exit 1; }
echo "livre=${FREE_GB}G ok"

echo "== 3) backup com verificacao de integridade =="
mkdir -p "$BAK"
TS=$(date +%Y%m%d-%H%M%S)
cp -f "$DB" "$BAK/opencode-$TS.db"
python3 - "$BAK/opencode-$TS.db" <<'PY'
import sqlite3, sys
con = sqlite3.connect(f"file:{sys.argv[1]}?mode=ro", uri=True)
r = con.execute("PRAGMA quick_check").fetchone()[0]
assert r == "ok", f"quick_check no backup FALHOU: {r} (nao rode o VACUUM; investigue)"
print("backup verificado (quick_check ok):", sys.argv[1])
PY

echo "== 4) truncate do WAL + VACUUM =="
python3 - "$DB" <<'PY'
import sqlite3, sys, os
p = sys.argv[1]
con = sqlite3.connect(p)
before = os.path.getsize(p) / 2**30
con.execute("PRAGMA journal_mode=WAL")
con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
con.execute("VACUUM")
con.commit()
r = con.execute("PRAGMA quick_check").fetchone()[0]
print(f"VACUUM: {before:.1f} -> {os.path.getsize(p)/2**30:.2f} GiB | quick_check={r}")
assert r == "ok", "quick_check pos-VACUUM falhou"
PY

echo "== CONCLUIDO. Backup em: $BAK/opencode-$TS.db"
echo "== (opcional) apagar o backup quando confiar: rm '$BAK/opencode-$TS.db' =="
