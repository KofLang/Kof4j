#!/usr/bin/env python3
"""
gen_portukof_aliases.py — GERADOR da tabela de aliases de MEMBROS da stdlib
PortuKof (`D-PORTUKOF`, regra absoluta da mantenedora 07/10: paridade
COMPLETA do frontend). Fonte da verdade: as listas `functions()`/`List.of`
REAIS dos dispatchers (`StdCatalog.java` + `Kof*.java` + `KofSecurity.java`)
— a mesma transcrição protegida-por-teste do `StdCatalogTest`, só que ao
contrário: aqui a tabela é derivada, e o `--check` recusa deriva ≠ fonte.

Regras:
  - cada membro canônico ganha exatamente UM alias pt-BR (bijetivo por
    namespace); se a tradução colidir com outro alias do MESMO namespace, o
    membro mantém o nome canônico (identidade) — nunca um segundo significado.
  - palavras sem tradução técnica decente ficam como estão (componente do
    camelCase preservado; ex.: `httpGet` -> `httpObter`).
  - nomes de usuário/identificadores de código NUNCA são tocados (PARTE 5):
    a tabela só alcança a POSIÇÃO de membro de namespace canônico.

Uso:
  scripts/gen_portukof_aliases.py           # escreve o arquivo gerado
  scripts/gen_portukof_aliases.py --check   # regenera e compara (rc=1 se deriva)
  scripts/gen_portukof_aliases.py --print   # tabela no stdout (auditoria)
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COMP = os.path.join(ROOT, "kof-compiler/src/main/java/dev/kof/compiler")
OUT = os.path.join(COMP, "lang/PortuKofStdlibMembers.java")

# léxico de palavras (minúsculas; camelCase montado componente a componente)
LEX = {
    "abs": "abs", "add": "adicionar", "age": "idade", "alpha": "alfa",
    "alphabetic": "alfabetico", "alphanumeric": "alfaNumerico", "append": "acrescentar",
    "apply": "aplicar", "array": "vetor", "ascii": "ascii", "at": "em",
    "auth": "autenticar", "authenticated": "autenticado", "available": "disponivel",
    "math": "matematica", "strings": "textos", "encoding": "codificacao", "net": "rede",
    "validation": "validacao", "observability": "observabilidade", "image": "imagem",
    "audio": "audio", "video": "video", "mic": "microfone", "file": "arquivo",
    "media": "midia", "db": "banco", "mq": "fila",
    "average": "media", "b64": "b64", "base": "base", "between": "entre",
    "binary": "binario", "bind": "vincular", "boot": "iniciar", "bytes": "bytes",
    "cache": "cache", "cancel": "cancelar", "canvas": "tela", "capitalize": "capitalizar",
    "case": "caixa", "ceiling": "teto", "chars": "caracteres", "clamp": "limitar",
    "clear": "limpar", "close": "fechar", "collect": "coletar", "color": "cor",
    "column": "coluna", "combine": "combinar", "compare": "comparar", "compile": "compilar",
    "component": "componente", "connect": "conectar", "contains": "contem",
    "constant": "constante", "convert": "converter", "cookie": "cookie",
    "copy": "copiar", "count": "contar", "create": "criar", "crop": "recortar",
    "csp": "csp", "csv": "csv", "current": "atual", "data": "dados",
    "day": "dia", "days": "dias", "decode": "decodificar", "decrement": "decrementar",
    "decrypt": "decriptar", "deep": "profundo", "defer": "adiar", "delete": "excluir",
    "depth": "profundidade", "desc": "desc", "destroy": "destruir", "detach": "destacar",
    "digest": "digest", "diff": "diferenca", "dir": "diretorio", "domain": "dominio",
    "download": "baixar", "draw": "desenhar", "drop": "descartar", "each": "cada",
    "element": "elemento", "empty": "vazio", "encode": "codificar", "end": "fim",
    "enqueue": "enfileirar", "entries": "entradas", "equals": "igual", "escape": "escapar",
    "exec": "executar", "execute": "executar", "exit": "sair", "expand": "expandir",
    "export": "exportar", "filter": "filtrar", "first": "primeiro", "fit": "ajustar",
    "flatten": "achatar", "flip": "inverter", "floor": "piso", "format": "formatar",
    "frames": "quadros", "free": "livre", "from": "de", "generate": "gerar",
    "get": "obter", "group": "agrupar", "hash": "hash", "head": "cabeca",
    "height": "altura", "hex": "hex", "history": "historia", "hour": "hora",
    "hours": "horas", "hue": "matiz", "index": "indice", "info": "info",
    "insert": "inserir", "inspect": "inspecionar", "install": "instalar",
    "interval": "intervalo", "invert": "inverter", "invoke": "chamar", "is": "eh",
    "item": "item", "join": "juntar", "jwt": "jwt", "key": "chave", "keys": "chaves",
    "label": "rotulo", "last": "ultimo", "latest": "maisRecente", "launch": "lancar",
    "leap": "bissexto", "level": "nivel", "limit": "limite", "listen": "escutar",
    "load": "carregar", "locale": "localidade", "log": "registrar", "lookup": "buscar",
    "lower": "minusculo", "map": "mapear", "mark": "marcar", "max": "maximo",
    "media": "media", "member": "membro", "menu": "menu", "merge": "mesclar",
    "message": "mensagem", "metadata": "metadados", "microphone": "microfone",
    "milli": "mili", "min": "minimo", "minute": "minuto", "minutes": "minutos",
    "month": "mes", "months": "meses", "move": "mover", "name": "nome",
    "needs": "precisa", "new": "novo", "next": "proximo", "now": "agora",
    "offset": "deslocamento", "of": "de", "on": "sobre", "open": "abrir",
    "option": "opcao", "ord": "ordem", "order": "ordem", "parse": "analisar",
    "password": "senha", "passwords": "senhas", "pause": "pausar", "peak": "pico",
    "percent": "porcento", "pick": "escolher", "play": "tocar", "port": "porta",
    "private": "privada", "process": "processo", "properties": "propriedades",
    "proximity": "proximidade", "public": "publica", "publish": "publicar",
    "query": "consultar", "random": "aleatorio", "rate": "taxa", "read": "ler",
    "ready": "pronto", "real": "real", "record": "gravar", "reduce": "reduzir",
    "refresh": "atualizar", "register": "registrar", "reject": "recusar",
    "release": "soltar", "remove": "remover", "render": "renderizar", "repeat": "repetir",
    "replace": "substituir", "report": "relatorio", "request": "requisicao", "resolve": "resolver",
    "resume": "retomar", "retry": "tentarNovamente", "reveal": "revelar", "reverse": "reverter",
    "role": "papel", "rotate": "girar", "round": "arredondar", "rows": "linhas",
    "run": "executar", "sample": "amostra", "save": "salvar", "schedule": "agendar",
    "second": "segundo", "seconds": "segundos", "seek": "buscar", "seed": "semente",
    "segment": "segmento", "select": "selecionar", "send": "enviar", "set": "definir",
    "setup": "preparar", "shake": "tremer", "shift": "deslocar", "sign": "assinar",
    "signal": "sinal", "size": "tamanho", "sleep": "dormir", "slice": "fatiar",
    "sort": "ordenar", "split": "dividir", "sqrt": "raizQuadrada", "stage": "estagio",
    "stamp": "selo", "start": "iniciar", "startOf": "inicioDe", "state": "estado",
    "status": "status", "stop": "parar", "store": "armazenar", "stream": "fluxo",
    "subscribe": "assinarCanal", "subtract": "subtrair", "sum": "soma", "swap": "trocar",
    "text": "texto", "tick": "tique", "tile": "mosaico", "time": "tempo",
    "to": "para", "today": "hoje", "toggle": "alternar", "top": "topo",
    "total": "total", "trim": "aparar", "tz": "fuso", "unlock": "desbloquear",
    "until": "ate", "update": "atualizarRegistro", "upload": "enviarArquivo",
    "usage": "uso", "uuid": "uuid", "validate": "validar", "value": "valor",
    "values": "valores", "variance": "variância", "verify": "verificar", "version": "versao",
    "wait": "esperar", "watch": "observar", "week": "semana", "weekend": "fimDeSemana",
    "where": "onde", "width": "largura", "year": "ano", "years": "anos",
    "zip": "combinarListas",
}


def words_of(name):
    # camelCase -> palavras (maiúsculas rebaixadas; siglas 2+ letras preservadas)
    parts = re.findall(r"[A-Z]+(?![a-z])|[A-Z][a-z0-9]*|[a-z0-9]+", name)
    return [p[0].lower() + p[1:] if p[0].isupper() and len(p) > 1 and p[1].islower() else p
            for p in parts]


def translate(name):
    ws = words_of(name)
    out = []
    for i, w in enumerate(ws):
        t = LEX.get(w.lower())
        if t is None:
            t = w
        out.append(t if i == 0 else (t[0].upper() + t[1:] if len(t) > 1 else t.upper()))
    alias = "".join(out)
    return alias if alias != name else name


def dump_lines():
    """Dump autoritativo: roda `PortuKofCatalogDump` contra o catálogo REAL."""
    import subprocess
    cp = os.path.join(ROOT, "kof-compiler/target/classes")
    if not os.path.isdir(cp):
        sys.exit("gen_portukof_aliases: rode `mvn -o -pl kof-compiler -am compile` antes (sem target/classes)")
    java = "java"
    for cand in (os.environ.get("JAVA_HOME"), "/home/mel/tools/jdk-25"):
        if cand and os.path.exists(os.path.join(cand, "bin/java")):
            java = os.path.join(cand, "bin/java")
            break
    r = subprocess.run([java, "-cp", cp, "dev.kof.compiler.lang.PortuKofCatalogDump"],
                       capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("gen_portukof_aliases: dump falhou:\n" + r.stderr)
    return r.stdout.splitlines()


def extract():
    """{namespace: [membros canônicos]} do dump do StdCatalog em execução."""
    ns = {}
    for line in dump_lines():
        if not line.strip() or "\t" not in line:
            continue
        n, m = line.split("\t", 1)
        ns.setdefault(n, [])
        if m not in ns[n]:
            ns[n].append(m)
    return ns


def dedup(xs):
    out = []
    for x in xs:
        if x not in out:
            out.append(x)
    return out


def build_table(ns):
    """{ns: [(canon, alias)]} com bijetividade: colisão -> identidade."""
    table = {}
    for n, members in ns.items():
        used = set()
        rows = []
        for m in members:
            a = translate(m)
            if a != m and (a in used or a in members):
                a = m  # colide: mantém canônico (identidade) — nunca 2 significados
            used.add(a)
            rows.append((m, a))
        table[n] = rows
    return table


def build_ns_table(ns):
    """canônico -> alias pt (bijetivo por colisão→identidade), ordem estável."""
    used = {}
    rows = []
    for n in sorted(ns):
        a = translate(n)
        key = a if a not in used and a not in ns else n
        used[key] = n
        rows.append((n, key))
    return rows


def render(table, ns_rows):
    order = sorted(table)
    lines = []
    lines.append("package dev.kof.compiler.lang;")
    lines.append("")
    lines.append("import java.util.LinkedHashMap;")
    lines.append("import java.util.Map;")
    lines.append("")
    lines.append("/**")
    lines.append(" * ARTEFATO DO GERADOR scripts/gen_portukof_aliases.py — nao editar a mao.")
    lines.append(" *")
    lines.append(" * D-PORTUKOF (07/10) - paridade COMPLETA de membros da stdlib: cada membro")
    lines.append(" * canonico do catalogo real (`StdCatalog` + dispatchers `Kof*`) tem exatamente")
    lines.append(" * um alias pt-BR por namespace (identidade quando a traducao colidiria).")
    lines.append(" * Alias != implementacao: o simbolo canonico e unico; `arquivo`/`tempo`/... so")
    lines.append(" * resolvidos para a mesma funcao. A fonte e a lista real; `--check` recusa")
    lines.append(" * deriva; o gate `scripts/check_portukof_parity.sh` cobra cobertura total.")
    lines.append(" */")
    lines.append("public final class PortuKofStdlibMembers {")
    lines.append("")
    lines.append("    private PortuKofStdlibMembers() {}")
    lines.append("")
    lines.append("    /** namespace canônico -> alias pt-BR (bijetivo; colisão mantém o canônico). */")
    lines.append("    public static Map<String, String> namespaces() {")
    lines.append("        var m = new LinkedHashMap<String, String>();")
    for c, a in ns_rows:
        lines.append('        m.put("%s", "%s");' % (c, a))
    lines.append("        return java.util.Collections.unmodifiableMap(m);")
    lines.append("    }")
    lines.append("")
    lines.append("    /** namespace canônico -> [(membro canônico, alias pt-BR)] (ordem do catálogo). */")
    lines.append("    public static Map<String, String[][]> table() {")
    lines.append("        var m = new LinkedHashMap<String, String[][]>();")
    for n in order:
        rows = ", ".join('{\"%s\", \"%s\"}' % (c, a) for c, a in table[n])
        lines.append('        m.put("%s", new String[][]{%s});' % (n, rows))
    lines.append("        return java.util.Collections.unmodifiableMap(m);")
    lines.append("    }")
    lines.append("}")
    return "\n".join(lines) + "\n"


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "gen"
    if not os.path.exists(OUT):
        open(OUT, "w").write(render({}, []))  # bootstrap: stub para o primeiro compile
    ns = extract()
    table = build_table(ns)
    text = render(table, build_ns_table(ns))
    if mode == "--print":
        for n in sorted(table):
            for c, a in table[n]:
                print(f"{n}.{c} -> {a}")
        return 0
    if mode == "--check":
        cur = open(OUT).read() if os.path.exists(OUT) else ""
        if cur == text:
            print(f"gen_portukof_aliases: OK ({sum(len(v) for v in ns.values())} membros, {len(ns)} namespaces)")
            return 0
        print("gen_portukof_aliases: DERIVA — a tabela commits ≠ a fonte real; rode o gerador", file=sys.stderr)
        return 1
    open(OUT, "w").write(text)
    print(f"gen_portukof_aliases: gerado {OUT} ({sum(len(v) for v in ns.values())} membros, {len(ns)} namespaces)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
