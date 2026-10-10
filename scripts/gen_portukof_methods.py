#!/usr/bin/env python3
"""D-PORTUKOF unidade 3 (07/10) — extrai os nomes CANONICOS de metodo/campo por
categoria de receiver dos REGIOES REAIS de dispatch do compilador (evidencia,
nao in vencao) e gera `lang/PortuKofMethodAliases.java`: a tabela bijetiva
(categoria, alias_pt) -> canonico usada pelo splicer receiver-aware.

Fonte da verdade = o proprio dispatcher:
  STRING        -> StringMethodRegistry.stringMethodSignature + CollectionMethodTyper(isString)
  LIST/MAP/SET  -> CollectionMethodTyper por predicate
  ARRAY         -> MethodCallTyper receiver ArrayType + SemFieldAccessTyper length
  ENUM          -> MethodCallTyper isEnumType branch
  PRIMITIVE     -> MethodCallTyper toString / numeric conversion
  IO            -> KofIo.instanceMethod
  BUFFER        -> KofBuffer.instanceMethod
  UI            -> KofUi.instanceMethod
  WEB_APP       -> KofWeb.instanceMethod
  PROCESS_HANDLE-> KofProcess.handleMethod
  PROCESS_RESULT-> KofProcess fields
  MEDIA         -> KofMedia imageData/audio/video methods
  SECRET/KEY    -> KofSecurity.instanceMethod
  INTEROP_ERROR -> KofInteropError.instanceMethod

`--check` regenera e falha (rc!=0) se o arquivo gerado divergir (drift gate).
"""
import sys
import re
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "kof-compiler" / "src" / "main" / "java" / "dev" / "kof" / "compiler"
OUT = SRC / "lang" / "PortuKofMethodAliases.java"

CASE = re.compile(r'case\s+"([A-Za-z][A-Za-z0-9]*)"')
EQ = re.compile(r'"([A-Za-z][A-Za-z0-9]*)"\s*\.\s*equals\(\s*'
                r'(?:mn|name|mc\.methodName\(\)|function|fa\.fieldName\(\))\s*\)')


def body(text, start_marker):
    """Retorna o trecho a partir de um marcador ate o proximo `static` (ou EOF)."""
    return body_upto(text, start_marker, "static ")


def body_upto(text, start_marker, end_marker):
    i = text.find(start_marker)
    if i < 0:
        return ""
    j = text.find(end_marker, i + len(start_marker))
    return text[i:] if j < 0 else text[i:j]


def names_from(text, patterns=(CASE, EQ)):
    out = []
    seen = set()
    for p in patterns:
        for m in p.finditer(text):
            n = m.group(1)
            if n not in seen:
                seen.add(n)
                out.append(n)
    return out


def read(f):
    return (SRC / f).read_text()


# --- canonical por categoria (evidencia dos dispatchers reais) ---
smr = read("StringMethodRegistry.java")
cmt = read("CollectionMethodTyper.java")
mct = read("MethodCallTyper.java")
io = read("KofIo.java")
buf = read("KofBuffer.java")
ui = read("KofUi.java")
web = read("KofWeb.java")
proc = read("KofProcess.java")
media = read("KofMedia.java")
sec = read("KofSecurity.java")
ioe = read("KofInteropError.java")

def names_from_set(text):
    return re.findall(r'"([A-Za-z][A-Za-z0-9]*)"', text)


STRING = names_from(body_upto(smr,
    "static Sig stringMethodSignature(String name, int argCount, List<Type> argTypes)",
    "private static Sig replaceSignature"))
LIST = names_from(body_upto(cmt, "if (BuiltinTypes.isList(recvType))",
                             "if (BuiltinTypes.isMap"))
MAP = names_from(body_upto(cmt, "if (BuiltinTypes.isMap(recvType))", "if (BuiltinTypes.isSet"))
SET = names_from(body_upto(cmt, "if (BuiltinTypes.isSet(recvType))", "if (Type.isString"))
ARRAY = names_from(body_upto(mct, "instanceof Type.ArrayType at", "super.metodo"))
ENUM = names_from(body_upto(mct, "if (CompilerTypes.isEnumType(recvType",
                            "BuiltinTypes.isList(recvType) || BuiltinTypes.isMap"))
PRIMITIVE = names_from_set(body_upto(read("SemMethodCallTyper.java"),
                                     'Set.of("toString", "toInt"', "static Type infer"))

# So o que o EMIT possui existe como canonico (licao #101/§145: "no typer mas
# nao no registro" = JVM descritor fallback / Native link-fail / JS TypeError).
# O typer colecoes aceita length/count em List/Map/Set, mas o emit canonico e
# size/add/... -- alias de nome sem emit quebraria paridade cross-target.
EXCLUDES = {
    "LIST": {"length", "count"},
    "MAP": {"length", "count"},
    "SET": {"length", "count"},
    "ARRAY": {"size", "count"},
}
for cat, ex in EXCLUDES.items():
    globals()[cat] = [n for n in globals()[cat] if n not in ex]
ROUTE = names_from(body(web, "ROUTE_METHODS ="))
IOM = names_from(body(io, "static IoCall instanceMethod"))
BUFFER = names_from(body(buf, "static BufferCall instanceMethod"))
UIM = names_from(body(ui, "static UiCall instanceMethod"))
WEBM = names_from(body(web, "static WebCall instanceMethod")) + ROUTE
PROC_H = names_from(body(proc, "static ProcessCall handleMethod"))
PROC_R = names_from(body(proc, "static Type fieldType"))
MEDIA_M = names_from(body(media, "static MediaCall imageDataMethod")) + \
    names_from(body(media, "static MediaCall audioMethod")) + \
    names_from(body(media, "static MediaCall videoMethod"))
SECRETM = names_from(body_upto(sec, "if (isSecretType(receiver))",
                               "if (isKeyHandleType(receiver))"))
KEYM = names_from(body_upto(sec, "if (isKeyHandleType(receiver))",
                            "return null;"))
IOEM = names_from(body(ioe, "static InteropCall instanceMethod"))

# --- seed de traducao pt-BR (regra absoluta: todo canonico tem UM alias; ---
# --- identidade quando a traducao colidiria ou nao existe termo limpo)  ---
SEED = {
    "length": "comprimento", "size": "tamanho", "count": "contagem",
    "isEmpty": "estaVazio", "isBlank": "estaEmBranco",
    "charAt": "caracterEm", "substring": "subcadena", "contains": "contem",
    "startsWith": "comecaCom", "endsWith": "terminaCom",     "equals": "igualA",
    "equalsIgnoreCase": "igualAIgnorarCaixa",
    "indexOf": "indiceDe", "lastIndexOf": "ultimoIndiceDe", "concat": "concatenar",
    "trim": "aparar", "toUpperCase": "paraMaiusculas", "toLowerCase": "paraMinusculas",
    "replace": "substituir", "replaceAll": "substituirTodos",
    "replaceFirst": "substituirPrimeiro", "matches": "corresponde",
    "toCharArray": "paraCaracteres", "split": "dividir", "repeat": "repetir",
    "compareTo": "compararA", "compareToIgnoreCase": "compararAIgnorarCaixa",
    "toInt": "paraInt", "toLong": "paraLong", "toDouble": "paraDouble",
    "toFloat": "paraFloat", "toString": "paraTexto", "getBytes": "paraBytes",
    "add": "adicionar", "remove": "remover", "clear": "limpar", "set": "definir",
    "get": "obter", "put": "colocar", "keys": "chaves", "values": "valores",
    "containsKey": "contemChave", "containsValue": "contemValor",
    "getOrDefault": "obterOuPadrao", "putIfAbsent": "colocarSeAusente",
    "addAll": "adicionarTodos", "subList": "subLista", "sort": "ordenar",
    "sorted": "ordenado", "distinct": "distintos", "reverse": "reverter",
    "map": "mapear", "filter": "filtrar", "reduce": "reduzir", "any": "algum",
    "all": "todos", "none": "nenhum", "find": "achar", "forEach": "paraCada",
    "flatMap": "achatarMap", "groupBy": "agruparPor", "zip": "zip",
    "take": "pegar", "drop": "descartar", "slice": "fatiar", "push": "empilhar",
    "append": "anexar", "exists": "existe", "isFile": "ehArquivo",
    "isDirectory": "ehDiretorio", "isSymlink": "ehLinkSimbolico",
    "readText": "lerTexto", "writeText": "escreverTexto",
    "appendText": "anexarTexto", "readBytes": "lerBytes", "writeBytes": "escreverBytes",
    "appendBytes": "anexarBytes", "delete": "excluir", "name": "nome",
    "copyTo": "copiarPara", "moveTo": "moverPara", "modifiedTime": "tempoModificado",
    "resolve": "resolver", "parent": "pai", "fileName": "nomeArquivo",
    "extension": "extensao", "path": "caminho", "bytes": "bytes",
    "setId": "definirId", "setClass": "definirClasse", "setDisabled": "definirDesabilitado",
    "on": "ao", "setBorder": "definirBorda", "setShadow": "definirSombra",
    "setGradient": "definirGradiente", "setFlexBasis": "definirBaseFlex",
    "setMaxWidth": "definirLarguraMax", "setStyle": "definirEstilo",
    "font": "fonte", "setFont": "definirFonte", "title": "titulo", "bind": "vincular",
    "show": "mostrar", "close": "fechar", "text": "texto", "setText": "definirTexto",
    "fontSize": "tamanhoFonte", "listen": "escutar", "listenSecure": "escutarSeguro",
    "serveDir": "servirDiretorio", "health": "saude", "port": "porta",
    "configure": "configurar", "policy": "politica", "security": "seguranca",
    "use": "usar", "write": "escrever", "readLine": "lerLinha",
    "exitCode": "codigoSaida", "kill": "matar", "alive": "vivo",
    "stdout": "saidaPadrao", "stderr": "erroPadrao",
    "open": "abrir", "openWav": "abrirWav", "record": "gravar", "list": "listar",
    "width": "largura", "height": "altura", "format": "formato", "save": "salvar",
    "saveAs": "salvarComo", "saveWav": "salvarWav", "dataUri": "dataUri",
    "bytesAs": "bytesComo", "pcmBytes": "bytesPcm", "sampleRate": "taxaAmostragem",
    "durationMs": "duracaoMs", "reveal": "revelar", "rotate": "girar",
    "message": "mensagem", "code": "codigo", "ordinal": "ordem",
    "redacted": "redigido", "state": "estado",
}


# Alias/campos extras definidos abaixo (FIELD/EXTRA_ALIASES).


# Campos de superficie medidos em SemFieldAccessTyper (face FIELD):
FIELD = {"STRING": ["length", "name", "path"], "ARRAY": ["length"]}
# Alias naturais que compartilham o mesmo canônico (bijetividade preservada:
# alias->canonico continua funcao; canonico->1+ alias e a direcao coberta).
EXTRA_ALIASES = {
    ("STRING", "length"): ["tamanho"],
    ("ARRAY", "length"): ["tamanho"],
}


def rows(canon_list):
    return rows_for(None, canon_list)


def rows_for(cat, canon_list, field_list=()):
    out = []
    names = list(dict.fromkeys(canon_list))
    for f in dict.fromkeys(field_list):
        if f not in names:
            names.append(f)
    aliases_seen = {}
    for c in names:
        for a in [SEED.get(c, c)] + EXTRA_ALIASES.get((cat, c), []):
            if a in aliases_seen and aliases_seen[a] != c:
                raise SystemExit(f"colisao de alias {cat}:{a} ({aliases_seen[a]} vs {c})")
            aliases_seen[a] = c
            out.append((c, a))
    return out


CATS = {
    "STRING": rows_for("STRING", STRING, FIELD["STRING"]),
    "LIST": rows(LIST), "MAP": rows(MAP), "SET": rows(SET),
    "ARRAY": rows_for("ARRAY", ARRAY, FIELD["ARRAY"]),
    "ENUM": rows(ENUM), "PRIMITIVE": rows(PRIMITIVE),
    "IO": rows(IOM), "BUFFER": rows(BUFFER), "UI": rows(UIM), "WEB_APP": rows(WEBM),
    "PROCESS_HANDLE": rows(PROC_H), "PROCESS_RESULT": rows(PROC_R),
    "MEDIA": rows(MEDIA_M), "SECRET": rows(SECRETM), "KEY_HANDLE": rows(KEYM),
    "INTEROP_ERROR": rows(IOEM),
}

# --- validacoes estruturais (falham o gerador, nunca silencioso) ---
def validate():
    errs = []
    for cat, rs in CATS.items():
        if not rs:
            errs.append("categoria vazia: " + cat)
        ali = [a for _, a in rs]
        if len(ali) != len(set(ali)):
            errs.append("colisao de alias em " + cat + ": " +
                        str([a for a in ali if ali.count(a) > 1]))
        for c, a in rs:
            if not re.fullmatch(r"[a-z][A-Za-z0-9]*", a):
                errs.append(f"alias invalido {cat}:{a} (canon {c})")
    if errs:
        for e in errs:
            print("ERRO:", e, file=sys.stderr)
        sys.exit(1)


def emit():
    L = []
    L.append("package dev.kof.compiler.lang;")
    L.append("")
    L.append("import java.util.HashMap;")
    L.append("import java.util.Map;")
    L.append("")
    L.append("/**")
    L.append(" * ARTEFATO DO GERADOR scripts/gen_portukof_methods.py -- nao editar a mao.")
    L.append(" *")
    L.append(" * D-PORTUKOF unidade 3 (07/10) -- paridade COMPLETA de metodos/campos de")
    L.append(" * receivers tipados. A fonte dos canonicos e o dispatcher real do")
    L.append(" * compilador (evidencia, nao invencao): cada categoria tem bijecao")
    L.append(" * canonico<->alias e o splicer resolve PELO TIPO DO RECEIVER, entao")
    L.append(" * `tamanho` pode ser `length` (String) e `size` (List) sem ambiguidade.")
    L.append(" * Alias != implementacao: o nome canonico e unico; nada de runtime paralelo.")
    L.append(" * Classes do usuario NAO estao aqui (retornam categoria null) -- um metodo")
    L.append(" * proprio `contem` do usuario permanece `contem` (PARTE 5).")
    L.append(" */")
    L.append("public final class PortuKofMethodAliases {")
    L.append("")
    L.append("    private PortuKofMethodAliases() {}")
    L.append("")
    L.append("    /** categoria -> {alias_pt -> canonico} (funcao injetiva). */")
    L.append("    private static final Map<String, Map<String, String>> ALIAS = build();")
    L.append("    /** categoria -> canonicos cobertos (ordem dos dispatchers). */")
    L.append("    private static final Map<String, java.util.List<String>> CANON = buildCanon();")
    L.append("")
    L.append("    public static String canonicalize(String category, String alias) {")
    L.append("        Map<String, String> m = ALIAS.get(category);")
    L.append("        return m == null ? alias : m.getOrDefault(alias, alias);")
    L.append("    }")
    L.append("")
    L.append("    public static Map<String, Map<String, String>> aliasByCategory() {")
    L.append("        return ALIAS;")
    L.append("    }")
    L.append("")
    L.append("    /** categoria -> lista ordenada de canonicos (cobertura/paridade). */")
    L.append("    public static Map<String, java.util.List<String>> canonicalsByCategory() {")
    L.append("        return CANON;")
    L.append("    }")
    L.append("")
    L.append("    private static Map<String, Map<String, String>> build() {")
    L.append("        Map<String, Map<String, String>> r = new HashMap<>();")
    for cat, rs in CATS.items():
        L.append(f"        r.put(\"{cat}\", pair(new String[][]{{" +
                 ", ".join(f'{{"{c}", "{a}"}}' for c, a in rs) + "}));")
    L.append("        return java.util.Collections.unmodifiableMap(r);")
    L.append("    }")
    L.append("")
    L.append("    private static Map<String, String> pair(String[][] rows) {")
    L.append("        Map<String, String> m = new java.util.LinkedHashMap<>();")
    L.append("        for (String[] row : rows) m.put(row[1], row[0]);")
    L.append("        return m;")
    L.append("    }")
    L.append("")
    L.append("    private static Map<String, java.util.List<String>> buildCanon() {")
    L.append("        Map<String, java.util.List<String>> r = new java.util.LinkedHashMap<>();")
    for cat, rs in CATS.items():
        L.append(f"        r.put(\"{cat}\", java.util.List.of(" +
                 ", ".join(f'\"{c}\"' for c in dict.fromkeys(c for c, _ in rs)) + "));")
    L.append("        return java.util.Collections.unmodifiableMap(r);")
    L.append("    }")
    L.append("}")
    L.append("")
    return "\n".join(L) + "\n"


def main():
    validate()
    content = emit()
    if "--check" in sys.argv:
        cur = OUT.read_text() if OUT.exists() else ""
        if cur != content:
            print("DRIFT: PortuKofMethodAliases.java nao bate com os dispatchers.",
                  file=sys.stderr)
            print("re-rodar: python3 scripts/gen_portukof_methods.py", file=sys.stderr)
            sys.exit(1)
        print("PortuKofMethodAliases.java em sincronia com os dispatchers.")
        return
    OUT.write_text(content)
    print(f"gerado {OUT.name}: "
          + ", ".join(f"{k}={len(v)}" for k, v in CATS.items()))


if __name__ == "__main__":
    main()
