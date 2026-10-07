package dev.kof.compiler.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * ARTEFATO DO GERADOR scripts/gen_portukof_methods.py -- nao editar a mao.
 *
 * D-PORTUKOF unidade 3 (07/10) -- paridade COMPLETA de metodos/campos de
 * receivers tipados. A fonte dos canonicos e o dispatcher real do
 * compilador (evidencia, nao invencao): cada categoria tem bijecao
 * canonico<->alias e o splicer resolve PELO TIPO DO RECEIVER, entao
 * `tamanho` pode ser `length` (String) e `size` (List) sem ambiguidade.
 * Alias != implementacao: o nome canonico e unico; nada de runtime paralelo.
 * Classes do usuario NAO estao aqui (retornam categoria null) -- um metodo
 * proprio `contem` do usuario permanece `contem` (PARTE 5).
 */
public final class PortuKofMethodAliases {

    private PortuKofMethodAliases() {}

    /** categoria -> {alias_pt -> canonico} (funcao injetiva). */
    private static final Map<String, Map<String, String>> ALIAS = build();
    /** categoria -> canonicos cobertos (ordem dos dispatchers). */
    private static final Map<String, java.util.List<String>> CANON = buildCanon();

    public static String canonicalize(String category, String alias) {
        Map<String, String> m = ALIAS.get(category);
        return m == null ? alias : m.getOrDefault(alias, alias);
    }

    public static Map<String, Map<String, String>> aliasByCategory() {
        return ALIAS;
    }

    /** categoria -> lista ordenada de canonicos (cobertura/paridade). */
    public static Map<String, java.util.List<String>> canonicalsByCategory() {
        return CANON;
    }

    private static Map<String, Map<String, String>> build() {
        Map<String, Map<String, String>> r = new HashMap<>();
        r.put("STRING", pair(new String[][]{{"length", "comprimento"}, {"length", "tamanho"}, {"isEmpty", "estaVazio"}, {"charAt", "caracterEm"}, {"substring", "subcadena"}, {"contains", "contem"}, {"startsWith", "comecaCom"}, {"endsWith", "terminaCom"}, {"equals", "igualA"}, {"equalsIgnoreCase", "igualAIgnorarCaixa"}, {"indexOf", "indiceDe"}, {"lastIndexOf", "ultimoIndiceDe"}, {"concat", "concatenar"}, {"trim", "aparar"}, {"toInt", "paraInt"}, {"toLong", "paraLong"}, {"toDouble", "paraDouble"}, {"toFloat", "paraFloat"}, {"toUpperCase", "paraMaiusculas"}, {"replace", "substituir"}, {"replaceAll", "substituirTodos"}, {"matches", "corresponde"}, {"toCharArray", "paraCaracteres"}, {"getBytes", "paraBytes"}, {"compareTo", "compararA"}, {"compareToIgnoreCase", "compararAIgnorarCaixa"}, {"split", "dividir"}, {"name", "nome"}, {"path", "caminho"}}));
        r.put("LIST", pair(new String[][]{{"map", "mapear"}, {"filter", "filtrar"}, {"reduce", "reduzir"}, {"any", "algum"}, {"all", "todos"}, {"none", "nenhum"}, {"find", "achar"}, {"forEach", "paraCada"}, {"flatMap", "achatarMap"}, {"groupBy", "agruparPor"}, {"get", "obter"}, {"remove", "remover"}, {"size", "tamanho"}, {"contains", "contem"}, {"isEmpty", "estaVazio"}, {"indexOf", "indiceDe"}, {"lastIndexOf", "ultimoIndiceDe"}, {"addAll", "adicionarTodos"}, {"subList", "subLista"}, {"take", "pegar"}, {"drop", "descartar"}, {"slice", "fatiar"}, {"distinct", "distintos"}, {"sorted", "ordenado"}, {"zip", "zip"}, {"add", "adicionar"}, {"push", "empilhar"}, {"append", "anexar"}, {"set", "definir"}, {"clear", "limpar"}, {"sort", "ordenar"}}));
        r.put("MAP", pair(new String[][]{{"get", "obter"}, {"remove", "remover"}, {"put", "colocar"}, {"getOrDefault", "obterOuPadrao"}, {"containsValue", "contemValor"}, {"putIfAbsent", "colocarSeAusente"}, {"size", "tamanho"}, {"containsKey", "contemChave"}, {"contains", "contem"}, {"isEmpty", "estaVazio"}, {"clear", "limpar"}, {"keys", "chaves"}, {"values", "valores"}}));
        r.put("SET", pair(new String[][]{{"size", "tamanho"}, {"contains", "contem"}, {"isEmpty", "estaVazio"}, {"add", "adicionar"}, {"remove", "remover"}, {"clear", "limpar"}}));
        r.put("ARRAY", pair(new String[][]{{"get", "obter"}, {"length", "comprimento"}, {"length", "tamanho"}}));
        r.put("ENUM", pair(new String[][]{{"name", "nome"}, {"toString", "paraTexto"}, {"ordinal", "ordem"}, {"compareTo", "compararA"}}));
        r.put("PRIMITIVE", pair(new String[][]{{"toString", "paraTexto"}, {"toInt", "paraInt"}, {"toLong", "paraLong"}, {"toFloat", "paraFloat"}, {"toDouble", "paraDouble"}, {"toHexString", "toHexString"}, {"toBinaryString", "toBinaryString"}}));
        r.put("IO", pair(new String[][]{{"exists", "existe"}, {"isFile", "ehArquivo"}, {"isDirectory", "ehDiretorio"}, {"readText", "lerTexto"}, {"writeText", "escreverTexto"}, {"appendText", "anexarTexto"}, {"readBytes", "lerBytes"}, {"readRange", "readRange"}, {"writeBytes", "escreverBytes"}, {"appendBytes", "anexarBytes"}, {"delete", "excluir"}, {"size", "tamanho"}, {"name", "nome"}, {"copyTo", "copiarPara"}, {"moveTo", "moverPara"}, {"modifiedTime", "tempoModificado"}, {"isSymlink", "ehLinkSimbolico"}, {"resolve", "resolver"}, {"parent", "pai"}, {"fileName", "nomeArquivo"}, {"extension", "extensao"}, {"normalize", "normalize"}, {"isAbsolute", "isAbsolute"}, {"toAbsolute", "toAbsolute"}, {"realPath", "realPath"}, {"create", "create"}, {"createDirectories", "createDirectories"}, {"mkdir", "mkdir"}, {"mkdirs", "mkdirs"}, {"list", "listar"}}));
        r.put("BUFFER", pair(new String[][]{{"bytes", "bytes"}}));
        r.put("UI", pair(new String[][]{{"setId", "definirId"}, {"setClass", "definirClasse"}, {"setDisabled", "definirDesabilitado"}, {"on", "ao"}, {"setBorder", "definirBorda"}, {"setShadow", "definirSombra"}, {"setGradient", "definirGradiente"}, {"setFlexBasis", "definirBaseFlex"}, {"setMaxWidth", "definirLarguraMax"}, {"setStyle", "definirEstilo"}, {"font", "fonte"}, {"setFont", "definirFonte"}, {"title", "titulo"}, {"bind", "vincular"}, {"show", "mostrar"}, {"close", "fechar"}, {"size", "tamanho"}, {"text", "texto"}, {"setText", "definirTexto"}, {"fontSize", "tamanhoFonte"}, {"setFontSize", "setFontSize"}, {"bold", "bold"}, {"setBold", "setBold"}, {"color", "color"}, {"setColor", "setColor"}, {"remove", "remover"}, {"red", "red"}, {"green", "green"}, {"blue", "blue"}, {"alpha", "alpha"}, {"toCss", "toCss"}, {"withAlpha", "withAlpha"}, {"isOpaque", "isOpaque"}, {"setPlaceholder", "setPlaceholder"}, {"setType", "setType"}, {"setChecked", "setChecked"}, {"checked", "checked"}, {"setName", "setName"}, {"setReadonly", "setReadonly"}, {"selected", "selected"}, {"setSelected", "setSelected"}, {"setOptions", "setOptions"}, {"setItems", "setItems"}, {"setRows", "setRows"}, {"background", "background"}, {"surface", "surface"}, {"primary", "primary"}, {"secondary", "secondary"}, {"error", "error"}, {"isDark", "isDark"}, {"url", "url"}, {"setUrl", "setUrl"}, {"src", "src"}, {"setSrc", "setSrc"}, {"setAlt", "setAlt"}, {"setWidth", "setWidth"}, {"setHeight", "setHeight"}, {"name", "nome"}, {"setSize", "setSize"}, {"onSubmit", "onSubmit"}, {"submit", "submit"}, {"setControls", "setControls"}, {"play", "play"}, {"pause", "pause"}, {"state", "estado"}, {"stateSet", "stateSet"}, {"view", "view"}, {"onMount", "onMount"}, {"onDispose", "onDispose"}, {"effect", "effect"}, {"mount", "mount"}, {"type", "type"}, {"stopPropagation", "stopPropagation"}, {"key", "key"}, {"value", "value"}, {"x", "x"}, {"y", "y"}, {"target", "target"}, {"relatedTarget", "relatedTarget"}, {"get", "obter"}, {"set", "definir"}, {"subscribe", "subscribe"}, {"unsubscribe", "unsubscribe"}, {"beginPath", "beginPath"}, {"closePath", "closePath"}, {"moveTo", "moverPara"}, {"lineTo", "lineTo"}, {"arc", "arc"}, {"fill", "fill"}, {"stroke", "stroke"}, {"setFill", "setFill"}, {"setStroke", "setStroke"}, {"setLineWidth", "setLineWidth"}, {"clearRect", "clearRect"}, {"save", "salvar"}, {"restore", "restore"}, {"setGlobalAlpha", "setGlobalAlpha"}, {"fillText", "fillText"}, {"measureText", "measureText"}, {"transform", "transform"}, {"drawImage", "drawImage"}}));
        r.put("WEB_APP", pair(new String[][]{{"use", "usar"}, {"security", "seguranca"}, {"policy", "politica"}, {"listen", "escutar"}, {"serveDir", "servirDiretorio"}, {"health", "saude"}, {"listenSecure", "escutarSeguro"}, {"port", "porta"}, {"close", "fechar"}, {"configure", "configurar"}, {"sse", "sse"}, {"ws", "ws"}}));
        r.put("PROCESS_HANDLE", pair(new String[][]{{"write", "escrever"}, {"readLine", "lerLinha"}, {"exitCode", "codigoSaida"}, {"kill", "matar"}, {"alive", "vivo"}}));
        r.put("PROCESS_RESULT", pair(new String[][]{{"stdout", "saidaPadrao"}, {"exitCode", "codigoSaida"}}));
        r.put("MEDIA", pair(new String[][]{{"width", "largura"}, {"height", "altura"}, {"format", "formato"}, {"save", "salvar"}, {"saveAs", "salvarComo"}, {"dataUri", "dataUri"}, {"bytes", "bytes"}, {"bytesAs", "bytesComo"}, {"close", "fechar"}, {"sampleRate", "taxaAmostragem"}, {"durationMs", "duracaoMs"}, {"saveWav", "salvarWav"}, {"pcmBytes", "bytesPcm"}, {"path", "caminho"}, {"size", "tamanho"}}));
        r.put("SECRET", pair(new String[][]{{"reveal", "revelar"}, {"redacted", "redigido"}}));
        r.put("KEY_HANDLE", pair(new String[][]{{"rotate", "girar"}}));
        r.put("INTEROP_ERROR", pair(new String[][]{{"message", "mensagem"}, {"code", "codigo"}}));
        return java.util.Collections.unmodifiableMap(r);
    }

    private static Map<String, String> pair(String[][] rows) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String[] row : rows) m.put(row[1], row[0]);
        return m;
    }

    private static Map<String, java.util.List<String>> buildCanon() {
        Map<String, java.util.List<String>> r = new java.util.LinkedHashMap<>();
        r.put("STRING", java.util.List.of("length", "isEmpty", "charAt", "substring", "contains", "startsWith", "endsWith", "equals", "equalsIgnoreCase", "indexOf", "lastIndexOf", "concat", "trim", "toInt", "toLong", "toDouble", "toFloat", "toUpperCase", "replace", "replaceAll", "matches", "toCharArray", "getBytes", "compareTo", "compareToIgnoreCase", "split", "name", "path"));
        r.put("LIST", java.util.List.of("map", "filter", "reduce", "any", "all", "none", "find", "forEach", "flatMap", "groupBy", "get", "remove", "size", "contains", "isEmpty", "indexOf", "lastIndexOf", "addAll", "subList", "take", "drop", "slice", "distinct", "sorted", "zip", "add", "push", "append", "set", "clear", "sort"));
        r.put("MAP", java.util.List.of("get", "remove", "put", "getOrDefault", "containsValue", "putIfAbsent", "size", "containsKey", "contains", "isEmpty", "clear", "keys", "values"));
        r.put("SET", java.util.List.of("size", "contains", "isEmpty", "add", "remove", "clear"));
        r.put("ARRAY", java.util.List.of("get", "length"));
        r.put("ENUM", java.util.List.of("name", "toString", "ordinal", "compareTo"));
        r.put("PRIMITIVE", java.util.List.of("toString", "toInt", "toLong", "toFloat", "toDouble", "toHexString", "toBinaryString"));
        r.put("IO", java.util.List.of("exists", "isFile", "isDirectory", "readText", "writeText", "appendText", "readBytes", "readRange", "writeBytes", "appendBytes", "delete", "size", "name", "copyTo", "moveTo", "modifiedTime", "isSymlink", "resolve", "parent", "fileName", "extension", "normalize", "isAbsolute", "toAbsolute", "realPath", "create", "createDirectories", "mkdir", "mkdirs", "list"));
        r.put("BUFFER", java.util.List.of("bytes"));
        r.put("UI", java.util.List.of("setId", "setClass", "setDisabled", "on", "setBorder", "setShadow", "setGradient", "setFlexBasis", "setMaxWidth", "setStyle", "font", "setFont", "title", "bind", "show", "close", "size", "text", "setText", "fontSize", "setFontSize", "bold", "setBold", "color", "setColor", "remove", "red", "green", "blue", "alpha", "toCss", "withAlpha", "isOpaque", "setPlaceholder", "setType", "setChecked", "checked", "setName", "setReadonly", "selected", "setSelected", "setOptions", "setItems", "setRows", "background", "surface", "primary", "secondary", "error", "isDark", "url", "setUrl", "src", "setSrc", "setAlt", "setWidth", "setHeight", "name", "setSize", "onSubmit", "submit", "setControls", "play", "pause", "state", "stateSet", "view", "onMount", "onDispose", "effect", "mount", "type", "stopPropagation", "key", "value", "x", "y", "target", "relatedTarget", "get", "set", "subscribe", "unsubscribe", "beginPath", "closePath", "moveTo", "lineTo", "arc", "fill", "stroke", "setFill", "setStroke", "setLineWidth", "clearRect", "save", "restore", "setGlobalAlpha", "fillText", "measureText", "transform", "drawImage"));
        r.put("WEB_APP", java.util.List.of("use", "security", "policy", "listen", "serveDir", "health", "listenSecure", "port", "close", "configure", "sse", "ws"));
        r.put("PROCESS_HANDLE", java.util.List.of("write", "readLine", "exitCode", "kill", "alive"));
        r.put("PROCESS_RESULT", java.util.List.of("stdout", "exitCode"));
        r.put("MEDIA", java.util.List.of("width", "height", "format", "save", "saveAs", "dataUri", "bytes", "bytesAs", "close", "sampleRate", "durationMs", "saveWav", "pcmBytes", "path", "size"));
        r.put("SECRET", java.util.List.of("reveal", "redacted"));
        r.put("KEY_HANDLE", java.util.List.of("rotate"));
        r.put("INTEROP_ERROR", java.util.List.of("message", "code"));
        return java.util.Collections.unmodifiableMap(r);
    }
}

