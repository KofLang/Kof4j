#!/usr/bin/env bash
# check_skip_inventory.sh — o §10 da OPERAÇÃO (ordem da mantenedora): "Investigue
# os skips individualmente. Cada skip deve ter: Identificação, Justificativa,
# Ambiente necessário, Target afetado, Condição de execução, Responsável ou
# plano de resolução. Não permita skips silenciosos."
#
# O que mede (mecânico, sobre os FONTES de teste — a face estática do inventário;
# a face de EXECUÇÃO é o TOTAL: skipped=N do log carimbado da suíte):
#   1. Todo sítio de skip em */src/test/java (assumeTrue/assumeFalse/abort,
#      @Disabled) tem que carregar uma JUSTIFICATIVA NOMEADA (o literal string
#      do mensaje / o value do @Disabled). Skip sem justificativa = RED
#      (o skip silencioso é proibido — fail-closed, nunca falso-verde).
#   2. O inventário sai AGRUPADO por mensagem (as famílias) com a identificação
#      (file:line) e a contagem — o insumo da tabela §10 do AUDIT-STABILITY.
#
# Uso: scripts/check_skip_inventory.sh [--selftest]   (rc=0 ok / rc=1 skip silencioso)
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

SELFTEST=false
[ "${1:-}" = "--selftest" ] && SELFTEST=true

run_scan() {  # SCAN_ROOTS (separados por espaço) -> stdout ; rc 0 ok / 1 silencioso
    python3 - $@ <<'PYEOF'
import re, sys, pathlib

sites = []   # (file, line, kind, message or None)
pat = re.compile(r'\b(org\.junit\.jupiter\.api\.)?(Assumptions\.assumeTrue|Assumptions\.assumeFalse|Assumptions\.abort)\s*\(')

def scan_tokens(text, start, end):
    # tokeniza respeitando strings (o corpus tem vírgulas/parênteses DENTRO
    # de literais de mensaje — o parser cego a string quebrava o split)
    i = start
    while i < end:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < end:
                if text[j] == '\\': j += 2; continue
                if text[j] == '"': break
                j += 1
            yield ('str', text[i:j+1])
            i = j + 1
        else:
            j = i
            while j < end and text[j] != '"':
                j += 1
            yield ('code', text[i:j])
            i = j

def top_level_args(inner):
    # os argumentos de 1º nível (vírgula FORA de parênteses E de strings) —
    # o mensaje é o ÚLTIMO arg; strings dentro de has("as","ld") nunca são
    args, depth, cur = [], 0, []
    for kind, chunk in scan_tokens(inner, 0, len(inner)):
        if kind == 'str':
            cur.append(chunk)
            continue
        for ch in chunk:
            if ch == '(': depth += 1; cur.append(ch)
            elif ch == ')': depth -= 1; cur.append(ch)
            elif ch == ',' and depth == 0:
                args.append(''.join(cur)); cur = []
            else: cur.append(ch)
    args.append(''.join(cur))
    return [a.strip() for a in args if a.strip()]

def message_of(inner, file_text):
    # o mensaje é o ÚLTIMO arg de 1º nível. NOMEADO em quatro formas (o
    # corpus usa todas): o literal cru; a concatenação/ternário que CONTÉM
    # um literal (o último literal é a chave da família); a CONSTANTE
    # (static final String NOME = "...", resolvida no MESMO arquivo); a
    # CHAMADA de helper que construye el mensaje (skipMessage(...) — o
    # motivo existe em runtime; listado na família dinâmica, NUNCA
    # silencioso). Um arg sem literal/constante/chamada = SILENCIOSO.
    args = top_level_args(inner)
    if not args: return None
    last = args[-1]
    m = re.compile(r'^"((?:[^"\\]|\\.)*)"$', re.S).match(last)
    if m: return m.group(1)
    lits = re.findall(r'"((?:[^"\\]|\\.)*)"', last)
    if lits: return lits[-1]
    consts = re.findall(r'\b([A-Z][A-Z0-9_]{2,})\b', last)
    for c in consts:
        cm = re.search(r'String\s+' + c + r'\s*=\s*"((?:[^"\\]|\\.)*)"', file_text)
        if cm: return cm.group(1)
    if re.match(r'^[A-Za-z_][A-Za-z0-9_.]*\(', last): return "[dynamic] " + last[:60]
    if consts: return "[const-unresolved] " + consts[0]
    return None

for arg in sys.argv[1:]:
    root = pathlib.Path(arg)
    if not root.is_dir(): continue
    for f in sorted(root.rglob("*.java")):
        text = f.read_text(errors="replace")
        for m in pat.finditer(text):
            line = text.count("\n", 0, m.start()) + 1
            depth, j, in_str, esc = 1, m.end(), False, False
            while j < len(text):
                ch = text[j]
                if in_str:
                    if esc: esc = False
                    elif ch == '\\': esc = True
                    elif ch == '"': in_str = False
                else:
                    if ch == '"': in_str = True
                    elif ch == '(': depth += 1
                    elif ch == ')':
                        depth -= 1
                        if depth == 0:
                            j += 1
                            break
                j += 1
            inner = text[m.end():j-1] if depth == 0 else text[m.end():j]
            msg = message_of(inner, text)
            sites.append((str(f), line, m.group(2).split('.')[-1], msg))
        for m in re.finditer(r'@Disabled(?!\w)', text):
            line = text.count("\n", 0, m.start()) + 1
            rest = text[m.end():m.end()+160].lstrip()
            mm = re.compile(r'"((?:[^"\\]|\\.)*)"').search(rest)
            msg = mm.group(1) if rest.startswith('(') and mm else None
            sites.append((str(f), line, "Disabled", msg))

silent = [s for s in sites if s[3] is None]
named  = [s for s in sites if s[3] is not None]

# as famílias: agrupa pela mensagem normalizada (a 1ª linha, cortada a 90)
fams = {}
for f, line, kind, msg in named:
    key = msg.split("\n")[0][:90].strip()
    fams.setdefault(key, []).append(f"{f}:{line}")

print(f"SITES: {len(sites)} (named={len(named)} silent={len(silent)})")
for key in sorted(fams, key=lambda k: -len(fams[k])):
    files = fams[key]
    print(f"FAMILY ({len(files)}): {key}")
    for x in files[:3]:
        print(f"    {x}")
    if len(files) > 3:
        print(f"    ... +{len(files)-3} mais")
if silent:
    print(f"SILENT SKIPS ({len(silent)}) — proibidos (§10):")
    for f, line, kind, _ in silent:
        print(f"    {f}:{line} {kind}")
    sys.exit(1)
print("SKIP-INVENTORY: OK — nenhum skip silencioso")
PYEOF
}

if [ "$SELFTEST" = true ]; then
    ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
    mkdir -p "$ST/pkg"
    cat > "$ST/pkg/Good.java" <<'EOF'
import org.junit.jupiter.api.Assumptions;
class Good {
    void t() {
        Assumptions.assumeTrue(has("as"), "native toolchain absent");
        Assumptions.assumeTrue(
                other(),
                "webkit does not launch on this host (system libs)");
    }
}
EOF
    cat > "$ST/pkg/Bad.java" <<'EOF'
import org.junit.jupiter.api.Assumptions;
class Bad {
    void t() {
        Assumptions.assumeTrue(has("as"));
    }
}
EOF
    cat > "$ST/pkg/BadDis.java" <<'EOF'
import org.junit.jupiter.api.Disabled;
@Disabled
class BadDis {
}
EOF
    if OUT="$(run_scan "$ST")"; then
        echo "SELFTEST FAIL: skip silencioso aceito (falso verde): $OUT"; exit 2
    fi
    grep -q "Bad.java:4" <<<"$OUT" || { echo "SELFTEST FAIL: o sítio silencioso não identificado: $OUT"; exit 2; }
    grep -q "BadDis.java:2" <<<"$OUT" || { echo "SELFTEST FAIL: @Disabled nu não pego: $OUT"; exit 2; }
    rm "$ST/pkg/Bad.java" "$ST/pkg/BadDis.java"
    OUT="$(run_scan "$ST")" || { echo "SELFTEST FAIL: sítio NOMEADO reprovado (falso vermelho): $OUT"; exit 2; }
    grep -q "native toolchain absent" <<<"$OUT" || { echo "SELFTEST FAIL: família não listada: $OUT"; exit 2; }
    echo "SELFTEST: ok — silencioso (assumeTrue 1-arg e @Disabled nu) RED com file:line; nomeado passa e sai no inventário"
    exit 0
fi

OUT="$(run_scan "kof-compiler/src/test/java
kof-runtime/src/test/java
kof-cli/src/test/java
kof-script/src/test/java
kof-c-compiler/src/test/java")" ; rc=$?
echo "$OUT"
if [ $rc -ne 0 ]; then
    echo "check_skip_inventory: rc=1 — skips silenciosos proibidos (§10 da OPERAÇÃO); cada skip precisa de justificativa nomeada"
    exit 1
fi
echo "check_skip_inventory: rc=0 — o inventário cobre os 5 módulos de teste (a face estática; a contagem de EXECUÇÃO é o skipped=N do log carimbado)"
exit 0
