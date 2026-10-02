#!/usr/bin/env bash
#
# agent-evidence.sh — manifesto de evidência de uma entrega do worker.
# "Testes verdes" deixa de ser uma frase no commit e vira dado verificável:
# qual SHA, quais comandos, que exit code, qual log, o que foi NOT_RUN.
#
# Persistência (fora do repo e do /tmp):
#   ~/.local/state/kof-agent/verifier/<run-id>/{evidence.json,commands.jsonl,logs/}
#
# Uso:
#   agent-evidence.sh init --issue N --classification "BUG REAL" [--risk auto|low|medium|high]
#                          [--base SHA | --commits SHA[,SHA…]] [--session S] [--text "..."] [--repo DIR]   # imprime o run-id
#   --commits: janela por-commit (changed_files = união dos commits listados; o último DEVE ser HEAD).
#              Evita absorver produção de lanes irmãs no meio de um range base..head (#659/#664).
#   agent-evidence.sh run  --run-id ID --label L [--kind test|gate|smoke|cross|adversarial] [--cwd DIR] -- CMD...
#   agent-evidence.sh mark --run-id ID --name N --status FAIL|NOT_RUN [--reason R] [--section cross_target|structural_gates]
#   agent-evidence.sh verdict --run-id ID --worker pass|fail
#   agent-evidence.sh show     --run-id ID
#   agent-evidence.sh validate --run-id ID [--repo DIR]     # exit 0 ok, 3 inválido/stale
#
# Regras: `run` grava o exit code REAL do comando e o SHA testado; skip/toolchain
# ausente é `mark ... NOT_RUN` — nunca PASS. `mark PASS` é PROIBIDO: o worker não
# escreve "PASS" à mão (kind gate/smoke/cross/adversarial/test vêm de `run`).
set -uo pipefail
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
# shellcheck source=agent-common.sh
. "$HERE/agent-common.sh"
RISK="$HERE/agent-risk.sh"

sub="${1:-}"; shift || true
run_id=""; issue=""; classification=""; risk="auto"; base=""; session=""; text=""; repo="."; commits=""
label=""; kind="test"; name=""; status=""; reason=""; section="cross_target"; worker=""; cwd=""
while [ $# -gt 0 ]; do
    case "$1" in
        --run-id) run_id="${2:-}"; shift 2;;
        --issue) issue="${2:-}"; shift 2;;
        --classification) classification="${2:-}"; shift 2;;
        --risk) risk="${2:-auto}"; shift 2;;
        --base) base="${2:-}"; shift 2;;
        --session) session="${2:-}"; shift 2;;
        --text) text="${2:-}"; shift 2;;
        --repo) repo="${2:-.}"; shift 2;;
        --label) label="${2:-}"; shift 2;;
        --kind) kind="${2:-test}"; shift 2;;
        --name) name="${2:-}"; shift 2;;
        --status) status="${2:-}"; shift 2;;
        --reason) reason="${2:-}"; shift 2;;
        --section) section="${2:-cross_target}"; shift 2;;
        --worker) worker="${2:-}"; shift 2;;
        --cwd) cwd="${2:-}"; shift 2;;
        --commits) commits="${2:-}"; shift 2;;
        --) shift; break;;
        *) echo "argumento desconhecido: $1" >&2; exit 2;;
    esac
done

dir_of() { echo "$AGENT_STATE_ROOT/verifier/$1"; }
need_run() {
    [ -n "$run_id" ] && [ -f "$(dir_of "$run_id")/evidence.json" ] || { echo "run-id inexistente: '$run_id'" >&2; exit 2; }
}

# --- todas as mutações do JSON passam por UM programa python ---------------------
pyjson() { python3 - "$@" <<'PY'
import json, sys, os, re
action, path = sys.argv[1], sys.argv[2]
args = sys.argv[3:]
def load():
    with open(path, encoding="utf-8") as f: return json.load(f)
def save(d):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f: json.dump(d, f, indent=2, ensure_ascii=False); f.write("\n")
    os.replace(tmp, path)
if action == "init":
    (run_id, branch, base, head, dirty, issue, classification, risk, session, created, files, reasons) = args
    d = {"schema": 1, "run_id": run_id, "branch": branch, "base_sha": base, "head_sha": head,
         "dirty": dirty == "true", "issue": int(issue) if issue.isdigit() else (issue or None),
         "classification": classification, "risk": risk,
         "risk_reasons": [r for r in reasons.split("\n") if r],
         "changed_files": [f for f in files.split("\n") if f],
         "tests": [], "package_smoke": None, "cross_target": {}, "structural_gates": {},
         "worker_session": session or None, "worker_verdict": None,
         "independent_verifier": None, "created_at": created}
    save(d)
elif action == "append":
    kindname, entry = args[0], json.loads(args[1])
    d = load()
    if kindname == "smoke": d["package_smoke"] = entry
    elif kindname == "gate": d["structural_gates"][entry["label"]] = entry
    elif kindname == "cross": d["cross_target"][entry["label"]] = entry
    else: d["tests"].append(entry)
    save(d)
elif action == "mark":
    section, name, status, reason = args
    d = load()
    d.setdefault(section, {})[name] = {"status": status, "reason": reason or None}
    save(d)
elif action == "verdict":
    d = load(); d["worker_verdict"] = args[0]; save(d)
elif action == "validate":
    head_now, dirty_now = args
    d = load(); problems = []
    if d.get("head_sha") != head_now: problems.append(f"STALE: manifesto testou {str(d.get('head_sha'))[:12]} mas HEAD e {head_now[:12]}")
    if d.get("dirty"): problems.append("DIRTY: arvore suja quando a evidencia foi aberta")
    if not d.get("tests"): problems.append("SEM_TESTES: nenhum comando de teste registrado")
    for t in d.get("tests", []):
        if t.get("status") != "PASS": problems.append(f"TESTE_NAO_PASS: {t.get('label')} = {t.get('status')}")
        if t.get("sha_tested") != d.get("head_sha"): problems.append(f"SHA_DIFERENTE: {t.get('label')} rodou em {str(t.get('sha_tested'))[:12]}")
    for sec in ("cross_target", "structural_gates"):
        for n, v in d.get(sec, {}).items():
            st = v.get("status")
            if st == "NOT_RUN": problems.append(f"NOT_RUN: {sec}.{n} ({v.get('reason')})")
            elif st == "FAIL": problems.append(f"FAIL: {sec}.{n}")
    if d.get("worker_verdict") not in ("pass", "fail", None): problems.append("worker_verdict invalido")
    if problems:
        print("\n".join(problems)); sys.exit(3)
    print("OK: evidencia valida para", d.get("head_sha", "")[:12])
PY
}

case "$sub" in
init)
    [ -n "$issue" ] && [ -n "$classification" ] || { echo "init exige --issue e --classification" >&2; exit 2; }
    branch="$(git -C "$repo" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    head="$(git -C "$repo" rev-parse HEAD 2>/dev/null || echo unknown)"
    [ -n "$base" ] || base="$(git -C "$repo" merge-base HEAD "origin/$branch" 2>/dev/null || git -C "$repo" rev-parse HEAD~1 2>/dev/null || echo "$head")"
    dirty=false; [ -n "$(git -C "$repo" status --porcelain 2>/dev/null)" ] && dirty=true
    files="$( { git -C "$repo" diff --name-only "$base..$head" 2>/dev/null; git -C "$repo" status --porcelain 2>/dev/null | sed 's/^...//'; } | sort -u )"
    # --commits: janela EXATA por commit (lição #659: range base..head absorve
    # produção de lanes irmãs no meio). changed_files = união dos listados;
    # o último listado DEVE ser o HEAD (senão o validate ficaria mentindo).
    if [ -n "$commits" ]; then
        IFS=',' read -r -a CL <<< "$commits"
        last_c="${CL[$(( ${#CL[@]} - 1 ))]}"
        last_full="$(git -C "$repo" rev-parse "$last_c" 2>/dev/null)" \
            || { echo "--commits: commit inexistente: $last_c" >&2; exit 2; }
        [ "$last_full" = "$head" ] || { echo "--commits: o último listado ($last_full) deve ser HEAD ($head)" >&2; exit 2; }
        files=""
        for c in "${CL[@]}"; do
            git -C "$repo" cat-file -e "$c^{commit}" 2>/dev/null \
                || { echo "--commits: commit inexistente: $c" >&2; exit 2; }
            files="$(printf '%s\n%s\n' "$files" "$(git -C "$repo" show --name-only --format= "$c" 2>/dev/null)")"
        done
        files="$(printf '%s\n' "$files" | grep -v '^$' | sort -u)"
        first_full="$(git -C "$repo" rev-parse "${CL[0]}")"
        base="$(git -C "$repo" rev-parse "$first_full^" 2>/dev/null || echo "$first_full")"
    fi
    declared=""; case "$risk" in low|medium|high) declared="--declared $risk";; esac
    # shellcheck disable=SC2086
    rk="$(printf '%s\n' "$files" | bash "$RISK" --stdin --text "$text $classification" $declared)"
    level="$(printf '%s\n' "$rk" | sed -n 's/^risk=//p')"
    reasons="$(printf '%s\n' "$rk" | grep '^reason:' || true)"
    id="$(date +%Y%m%dT%H%M%S)-${head:0:8}-$RANDOM"
    d="$(dir_of "$id")"; mkdir -p "$d/logs"; : > "$d/commands.jsonl"
    pyjson init "$d/evidence.json" "$id" "$branch" "$base" "$head" "$dirty" "$issue" "$classification" "$level" "$session" "$(date -Is)" "$files" "$reasons"
    echo "$id"
    ;;
run)
    need_run
    [ $# -gt 0 ] && [ -n "$label" ] || { echo "run exige --label e -- <comando>" >&2; exit 2; }
    d="$(dir_of "$run_id")"; n=$(( $(wc -l < "$d/commands.jsonl") + 1 ))
    logf="$d/logs/$(printf '%02d' "$n")-$(printf '%s' "$label" | tr -c 'A-Za-z0-9_.-' '_').log"
    start="$(date -Is)"; sha_tested="$(git -C "$repo" rev-parse HEAD 2>/dev/null || echo unknown)"
    dirty_run=false; [ -n "$(git -C "$repo" status --porcelain 2>/dev/null)" ] && dirty_run=true
    run_dir="${cwd:-$repo}"; run_dir="$(cd "$run_dir" && pwd)"
    ( cd "$run_dir" && "$@" ) > "$logf" 2>&1; rc=$?
    end="$(date -Is)"
    # resumo do Maven quando confiável: ÚLTIMA linha "Tests run: N, Failures: F, Errors: E, Skipped: S" sem " -- in "
    summ="$(grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+' "$logf" | grep -v ' -- in ' | tail -n1 || true)"
    entry="$(python3 - "$label" "$kind" "$rc" "$start" "$end" "$sha_tested" "$dirty_run" "$logf" "$summ" "$*" "$run_dir" <<'PY'
import json, sys, re
label, kind, rc, start, end, sha, dirty, log, summ, cmd, cwd = sys.argv[1:12]
e = {"label": label, "kind": kind, "command": cmd, "start": start, "end": end,
     "exit_code": int(rc), "status": "PASS" if int(rc) == 0 else "FAIL",
     "sha_tested": sha, "dirty_at_run": dirty == "true", "cwd": cwd, "log_path": log,
     "executed_tests": None, "failures": None, "errors": None, "skips": None}
m = re.search(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)", summ or "")
if m:
    e["executed_tests"], e["failures"], e["errors"], e["skips"] = map(int, m.groups())
    if e["failures"] or e["errors"]: e["status"] = "FAIL"      # exit 0 com F/E (failure.ignore) NAO e PASS
print(json.dumps(e, ensure_ascii=False))
PY
)"
    printf '%s\n' "$entry" >> "$d/commands.jsonl"
    pyjson append "$d/evidence.json" "$kind" "$entry"
    exit "$rc"
    ;;
mark)
    need_run
    # PASS só nasce de um comando EXECUTADO (`run`); `mark` é para o que NÃO rodou/falhou.
    case "$status" in
        NOT_RUN|FAIL) ;;
        PASS) echo "mark PASS proibido: PASS so vem de 'run' (comando executado, exit code real)" >&2; exit 2;;
        *) echo "--status: NOT_RUN|FAIL" >&2; exit 2;;
    esac
    [ -n "$name" ] || { echo "mark exige --name" >&2; exit 2; }
    [ "$status" = "NOT_RUN" ] && [ -z "$reason" ] && { echo "NOT_RUN exige --reason (toolchain ausente etc.)" >&2; exit 2; }
    pyjson mark "$(dir_of "$run_id")/evidence.json" "$section" "$name" "$status" "$reason"
    ;;
verdict)
    need_run
    case "$worker" in pass|fail) ;; *) echo "--worker: pass|fail" >&2; exit 2;; esac
    pyjson verdict "$(dir_of "$run_id")/evidence.json" "$worker"
    ;;
show)
    need_run; cat "$(dir_of "$run_id")/evidence.json"
    ;;
validate)
    need_run
    head_now="$(git -C "$repo" rev-parse HEAD 2>/dev/null || echo unknown)"
    pyjson validate "$(dir_of "$run_id")/evidence.json" "$head_now" ""
    ;;
*)
    echo "uso: $0 {init|run|mark|verdict|show|validate} ..." >&2; exit 2;;
esac
