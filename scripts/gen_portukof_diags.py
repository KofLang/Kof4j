#!/usr/bin/env python3
"""D-PORTUKOF F6 — gerador do catálogo de diagnósticos localizados.

Fonte da verdade = os PONTOS DE EMISSÃO reais do compilador (evidência, não
invenção — mesmo princípio de U2/U3). Para cada CÓDIGO canônico (LEX/PARSE/SEM)
extraio o(s) TEMPLATE(S) EN decodificados (o valor de runtime da concatenação
Java: literais + {i} para cada argumento) e a aridade.

Mantém `scripts/portukof_diag_pt.json` = { code: [ {en, pt}, ... ] }:
  - `en` vem do scan (nunca editável à mão — deriva);
  - `pt` é o que o humano autora (alinhado por `en`; novo `en` => pt="").

Emite o bloco do catálogo entre `@@CATALOG_BEGIN@@/@@END@@` em
`lang/PortuKofDiagnostics.java` a partir do JSON.

Modos:
  (default)     re-gera o JSON (merge preservando pt) + o Java
  --check       recusa deriva: Java commitado != Java gerado, JSON fora de
                sincronia com o scan, ou catálogo com pt vazio/placeholders
                divergentes => rc!=0 (usado pelo gate)
"""
import sys, re, pathlib, json

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "kof-compiler" / "src" / "main" / "java" / "dev" / "kf" / "compiler"
# caminho real:
SRC = ROOT / "kof-compiler" / "src" / "main" / "java" / "dev" / "kof" / "compiler"
JSON = ROOT / "scripts" / "portukof_diag_pt.json"
JAVA = SRC / "lang" / "PortuKofDiagnostics.java"
OPENER = re.compile(r'(?:\.(error|warning|note|info|expect|expectId|reportError|reportWarn)'
                    r'|\b(report|Issue))\s*\(')
CODEARG = re.compile(r'^"((?:LEX|PARSE|SEM)\d+)"$')


def match_call(text, open_i):
    depth = 0
    i = open_i
    n = len(text)
    instr = esc = False
    while i < n:
        ch = text[i]
        if instr:
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                instr = False
            i += 1
            continue
        if ch == '"':
            instr = True
        elif ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def split_args(s):
    out, buf = [], []
    depth, i, n = 0, 0, len(s)
    instr = esc = False
    while i < n:
        ch = s[i]
        if instr:
            buf.append(ch)
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                instr = False
            i += 1
            continue
        if ch == '"':
            instr = True
            buf.append(ch)
        elif ch in '([{':
            depth += 1
            buf.append(ch)
        elif ch in ')]}':
            depth -= 1
            buf.append(ch)
        elif ch == ',' and depth == 0:
            out.append("".join(buf)); buf = []
        else:
            buf.append(ch)
        i += 1
    if buf:
        out.append("".join(buf))
    return [a.strip() for a in out]


def decode(lit):
    """valor de runtime de um literal de string Java (sem aspas externas)."""
    out = []
    i = 0
    n = len(lit)
    while i < n:
        c = lit[i]
        if c == '\\' and i + 1 < n:
            nx = lit[i + 1]
            mp = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', '\\': '\\',
                  '\'': '\'', 'b': '\b', 'f': '\f'}
            if nx in mp:
                out.append(mp[nx]); i += 2; continue
            if nx == 'u':
                try:
                    out.append(chr(int(lit[i + 2:i + 6], 16))); i += 6; continue
                except Exception:
                    pass
        out.append(c); i += 1
    return "".join(out)


def decompose(msg):
    """(en_template, [arg_exprs]) a partir de uma concatenação de literais+exprs.
    None se a mensagem não é modelável (sem literal)."""
    parts, buf = [], []
    depth, i, n = 0, 0, len(msg)
    instr = esc = False
    while i < n:
        ch = msg[i]
        if instr:
            buf.append(ch)
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                instr = False
            i += 1
            continue
        if ch == '"':
            instr = True
            buf.append(ch)
        elif ch in '([{':
            depth += 1
            buf.append(ch)
        elif ch in ')]}':
            depth -= 1
            buf.append(ch)
        elif ch == '+' and depth == 0:
            parts.append("".join(buf)); buf = []
        else:
            buf.append(ch)
        i += 1
    if buf:
        parts.append("".join(buf))
    parts = [p.strip() for p in parts if p.strip() != ""]

    def is_lit(p):
        return len(p) >= 2 and p[0] == '"' and p[-1] == '"'
    if not any(is_lit(p) for p in parts):
        return None
    sk = []
    exprs = []
    for p in parts:
        if is_lit(p):
            sk.append(decode(p[1:-1]))
        else:
            sk.append("{%d}" % len(exprs)); exprs.append(p)
    return ("".join(sk), exprs)


def grab_until_semi(text, i):
    """expressão Java a partir de i até o ';' em profundidade 0 (fora de string)."""
    depth, buf = 0, []
    instr = esc = False
    while i < len(text):
        ch = text[i]
        if instr:
            buf.append(ch)
            if esc: esc = False
            elif ch == '\\': esc = True
            elif ch == '"': instr = False
            i += 1; continue
        if ch == '"': instr = True; buf.append(ch)
        elif ch in '([{': depth += 1; buf.append(ch)
        elif ch in ')]}': depth -= 1; buf.append(ch)
        elif ch == ';' and depth == 0: break
        else: buf.append(ch)
        i += 1
    return "".join(buf).strip()


def ternary_branches(expr):
    """'cond ? A : B' -> [A, B]; sem ternário no topo -> [expr]."""
    depth, i, instr, esc = 0, 0, False, None
    qpos = -1
    while i < len(expr):
        ch = expr[i]
        if instr:
            if esc: esc = None
            elif ch == '\\': esc = True
            elif ch == '"': instr = False
            i += 1; continue
        if ch == '"': instr = True
        elif ch in '([{': depth += 1
        elif ch in ')]}': depth -= 1
        elif ch == '?' and depth == 0:
            qpos = i; break
        i += 1
    if qpos < 0:
        return [expr]
    a = expr[qpos + 1:]
    depth, j, instr, esc = 0, 0, False, None
    while j < len(a):
        ch = a[j]
        if instr:
            if esc: esc = None
            elif ch == '\\': esc = True
            elif ch == '"': instr = False
            j += 1; continue
        if ch == '"': instr = True
        elif ch in '([{': depth += 1
        elif ch in ')]}': depth -= 1
        elif ch == '?' and depth == 0:  # ternário aninhado -> desvia
            depth += 1
        elif ch == ':' and depth == 0:
            return [a[:j].strip(), a[j + 1:].strip()]
        j += 1
    return [a.strip()]


def method_registry(texts):
    """name -> expressão do PRIMEIRO `return` de um `static String name(...) { ... }`."""
    reg = {}
    decl = re.compile(r'\bstatic\s+String\s+(\w+)\s*\([^)]*\)\s*\{')
    for text in texts:
        for m in decl.finditer(text):
            brace = m.end() - 1
            depth, i, instr, esc = 0, brace, False, None
            body_start = None
            while i < len(text):
                ch = text[i]
                if instr:
                    if esc: esc = None
                    elif ch == '\\': esc = True
                    elif ch == '"': instr = False
                    i += 1; continue
                if ch == '"': instr = True
                elif ch == '{':
                    depth += 1
                    if depth == 1: body_start = i + 1
                elif ch == '}':
                    depth -= 1
                    if depth == 0:
                        break
                i += 1
            body = text[body_start:i] if body_start else ""
            rm = re.search(r'\breturn\b', body)
            if not rm:
                continue
            reg[m.group(1)] = grab_until_semi(body, rm.end())
    return reg


def resolve_msg(msg, file_text, reg, call_pos):
    """Mensagens construídas em runtime cujo TEXTO é literal no código:
    identificador (`String msg = …`, inclusive ternário) ou chamada de função
    `Class.method(…)`/`method(…)` que retorna um `return` de literais. Devolve
    uma lista de (en, exprs); vazia se não for modelável — NUNCA inventa."""
    exprs = []
    if re.fullmatch(r'[A-Za-z_]\w*', msg):
        # pega a atribuição `String <msg> = ...` MAIS PRÓXIMA ANTES da chamada
        # (escopo do método), nunca a primeira do arquivo.
        am = None
        for cand in re.finditer(r'String\s+' + re.escape(msg) + r'\s*=', file_text):
            if cand.start() < call_pos:
                am = cand
            else:
                break
        if am:
            exprs.append(grab_until_semi(file_text, am.end()))
    else:
        cm = re.fullmatch(r'(?:[A-Za-z_]\w*\.)?([A-Za-z_]\w*)\s*\(.*\)', msg, re.S)
        if cm and cm.group(1) in reg:
            exprs.append(reg[cm.group(1)])
    out = []
    for e in exprs:
        for br in ternary_branches(e):
            d = decompose(br)
            if d:
                out.append(d)
    return out


def register(variants, code, dec):
    en, _ = dec
    idxs = [int(i) for i in re.findall(r'\{(\d+)\}', en)]
    arity = (max(idxs) + 1) if idxs else 0
    variants.setdefault(code, {}).setdefault(en, arity)


def scan():
    """code -> { en -> arity }, preservando a ordem de primeira aparição."""
    files = [f for f in sorted(SRC.rglob("*.java")) if f.name != "PortuKofDiagnostics.java"]
    texts = {f: f.read_text() for f in files}
    reg = method_registry(texts.values())
    # primeira passada: coletores (code, msg, owner_file)
    calls = []
    for f in files:
        text = texts[f]
        for m in OPENER.finditer(text):
            open_i = m.end() - 1
            close = match_call(text, open_i)
            if close < 0:
                continue
            args = split_args(text[open_i + 1:close])
            if len(args) < 2:
                continue
            ci = None
            for ai in range(len(args) - 1, -1, -1):
                if CODEARG.match(args[ai]):
                    ci = ai; break
            if ci is None or ci < 1:
                continue
            calls.append((CODEARG.match(args[ci]).group(1), args[ci - 1], text, open_i))
    variants = {}
    for code, msg, text, pos in calls:
        dec = decompose(msg)
        if dec is not None:
            register(variants, code, dec)
            continue
        for d in resolve_msg(msg, text, reg, pos):
            register(variants, code, d)
    return variants



def load_json():
    if JSON.exists():
        return json.loads(JSON.read_text())
    return {}


def build_json(variants, existing):
    out = {}
    for code in sorted(variants):
        ens = sorted(variants[code].keys(), key=lambda e: (variants[code][e], e))
        prev = {v["en"]: v.get("pt", "") for v in existing.get(code, [])}
        rows = []
        for en in ens:
            rows.append({"en": en, "pt": prev.get(en, ""), "arity": variants[code][en]})
        out[code] = rows
    return out


def placeholders(s):
    return sorted(int(i) for i in re.findall(r'\{(\d+)\}', s))


def java_str(s):
    esc = (s.replace("\\", "\\\\").replace('"', "\\\"")
            .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r"))
    return '"' + esc + '"'


def emit_java(data):
    lines = []
    for code, rows in data.items():
        vs = ", ".join("new Variant(%s, %s)" % (java_str(r["en"]), java_str(r.get("pt", "")))
                       for r in rows)
        lines.append('        m.put("%s", List.of(%s));' % (code, vs))
    return lines


def main():
    check = "--check" in sys.argv
    variants = scan()
    existing = load_json()
    data = build_json(variants, existing)

    if check:
        errs = []
        # 1) JSON deve bater com o scan (mesmos codes/en/arity)
        if json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) != \
           json.dumps(existing, ensure_ascii=False, indent=2, sort_keys=True):
            errs.append("portukof-diag: JSON fora de sincronia com os pontos de emissão — rode "
                        "python3 scripts/gen_portukof_diags.py")
        # 2) placeholder parity + pt preenchido (cobertura total do domínio)
        missing_pt = []
        for code, rows in data.items():
            for r in rows:
                if placeholders(r["en"]) != placeholders(r.get("pt", "")):
                    errs.append(f"portukof-diag: placeholder divergente em {code}: "
                                f"EN={r['en']!r} PT={r.get('pt','')!r}")
                if not r.get("pt"):
                    missing_pt.append(code)
        if missing_pt:
            errs.append("portukof-diag: PT ausente em %d/%d códigos: %s"
                        % (len(missing_pt), len(data),
                           ",".join(sorted(set(missing_pt))[:20])))
        # 3) Java commitado == Java gerado
        if JAVA.exists():
            cur = JAVA.read_text()
            bi = cur.index("@@CATALOG_BEGIN@@")
            ei = cur.index("@@CATALOG_END@@")
            b = cur.index("\n", bi) + 1        # após a linha do BEGIN
            e = cur.rfind("\n", 0, ei) + 1     # início da linha do END
            body_cur = cur[b:e]
            body_new = "\n".join(emit_java(data)) + "\n"
            if body_cur != body_new:
                errs.append("portukof-diag: PortuKofDiagnostics.java diverge do catálogo — "
                            "re-gerar")
        if errs:
            for x in errs:
                print(x, file=sys.stderr)
            sys.exit(1)
        tot = sum(len(r) for r in data.values())
        print(f"portukof-diag: OK — {len(data)} códigos, {tot} variantes, "
              "paridade EN↔PT travada")
        return

    JSON.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    # injeta o bloco no Java (preserva as linhas-comentário dos marcadores)
    if JAVA.exists():
        cur = JAVA.read_text()
        bi = cur.index("@@CATALOG_BEGIN@@")
        ei = cur.index("@@CATALOG_END@@")
        b = cur.index("\n", bi) + 1        # após a linha do BEGIN
        e = cur.rfind("\n", 0, ei) + 1     # início da linha do END
        body = "\n".join(emit_java(data)) + "\n"
        new = cur[:b] + body + cur[e:]
        JAVA.write_text(new)
    tot = sum(len(r) for r in data.values())
    done = sum(1 for r in data.values() for v in r if v.get("pt"))
    print(f"gerado: {len(data)} códigos / {tot} variantes / {done} com PT")


if __name__ == "__main__":
    main()
