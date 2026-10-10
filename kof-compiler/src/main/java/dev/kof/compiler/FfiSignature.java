package dev.kof.compiler;

import java.util.List;

/**
 * FFI (R3): descrição compacta da assinatura `extern` e mapeamento tipo→layout
 * FFM. chars: i=Int j=Long f=Float d=Double b=Boolean S=String(char*) v=void(retorno).
 * Mantido fora de {@code CompilerPipeline} para a regra de ≤500 linhas/classe.
 */
public final class FfiSignature {

    private FfiSignature() {}

    static Character paramChar(String t) {
        if (CompilerPipeline.isIntType(t)) return 'i';
        if (CompilerPipeline.isStringType(t)) return 'S';
        if (CompilerPipeline.isDoubleType(t)) return 'd';
        if (isLongFFI(t)) return 'j';
        if (isFloatFFI(t)) return 'f';
        if (isBoolFFI(t)) return 'b';
        return null;
    }

    static Character returnChar(String t) {
        if (isVoidFFI(t)) return 'v';
        return paramChar(t);
    }

    static boolean isVoidFFI(String t) {
        return t == null || t.isEmpty() || "void".equals(t) || "Void".equals(t);
    }

    static String signature(CompilerDriver driver, ExternalFunctionNode ext) {
        StringBuilder sb = new StringBuilder();
        // 3.8b fatia 2: retorno struct (record) vira o token `@`; o nome binário
        // do record vai como sufixo `:` (após os chars dos params) para o runtime
        // reconstruir a instância a partir do struct devolvido por valor.
        String structRet = structReturnName(ext.returnType(), driver);
        if (structRet != null) {
            // JVM: `@` basta (o runtime reflete o record pelo sufixo `:Nome`).
            // JS: o host não reflete — o retorno carrega os chars do layout com
            // prefixo de TAMANHO (`@2ij`), como no param, e o guest reconstrói.
            String retFields = structFieldChars(ext.returnType(), driver);
            if (driver.target == Target.JS) {
                sb.append('@').append(retFields.length()).append(retFields);
            } else {
                sb.append('@');
            }
        } else {
            Character rc = returnChar(ext.returnType());
            sb.append(rc != null ? rc.charValue() : '?');
        }
        for (var p : ext.parameters()) {
            Character c = paramChar(p.type());
            if (c != null) {
                sb.append(c.charValue());
            } else if (callbackDescriptor(p.type()) != null) {
                // callback (R3, 3.4): token aninhado "(<retchar><paramchars>)"
                sb.append('(').append(callbackDescriptor(p.type())).append(')');
            } else if (structFieldChars(p.type(), driver) != null) {
                // D6-1 (A) / 3.8b: um `record` de campos escalares atravessa por
                // valor como struct C. No JVM o runtime deriva o layout e os
                // valores da própria classe (reflexão em RecordComponent) — o
                // token `@` basta. No JS o host não reflete um objeto GraalJS:
                // o token carrega os chars do layout com prefixo de TAMANHO
                // (`@2ij`) — sem ambiguidade com o escalar seguinte — e o
                // `__kof_ffi_fields` do record entrega os valores no fio.
                String fields = structFieldChars(p.type(), driver);
                if (driver.target == Target.JS) {
                    sb.append('@').append(fields.length()).append(fields);
                } else {
                    sb.append('@');
                }
            } else if (arrayElemChar(p.type()) != null) {
                // D6-2 / 3.8b fatia 3: `T[]` primitivo vira `ptr` C — token `p` +
                // o char do ELEMENTO (o runtime faz copy-in por chamada).
                sb.append('p').append(arrayElemChar(p.type()).charValue());
            } else if (isStringArray(p.type())) {
                // D-MEM-FFI-CROSS-FULL face 2: `String[]` vira `char**` (token
                // `pS`; o runtime empacota o payload de cada String).
                sb.append("pS");
            } else if (isBufferParam(p.type())) {
                // D6-3 / D-R3-BUFFER: `Buffer(U8)` (INOUT) — token `B`; o runtime
                // faz copy-in (arena da chamada), chama e copia de volta.
                sb.append('B');
            } else {
                // inalcançável: isExternBound filtra antes; nunca silencioso (R6).
                sb.append('?');
            }
        }
        if (structRet != null) sb.append(':').append(structRet);
        return sb.toString();
    }

    /** Nome simples (sem pacote) do tipo escrito no `extern`. */
    static String simpleName(String typeName) {
        String simple = typeName;
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) simple = simple.substring(dot + 1);
        int slash = simple.lastIndexOf('/');
        if (slash >= 0) simple = simple.substring(slash + 1);
        return simple;
    }

    /** 3.8b fatia 2 (JVM): nome binário (dots) do `record` de RETORNO bindável
     *  (campos escalares), ou null se o retorno não for um struct bindável. O sig
     *  carrega esse nome para o runtime reconstruir o record devolvido por valor. */
    static String structReturnName(String typeName, CompilerDriver driver) {
        if (structFieldChars(typeName, driver) == null) return null;
        String simple = simpleName(typeName);
        if (driver.semanticAnalyzer != null) {
            SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(simple);
            if (cs != null) return cs.internalName().replace('/', '.');
        }
        return simple;   // pacote default: o próprio nome
    }

    /** Type do retorno quando é um struct bindável (record), senão null. */
    static Type structReturnType(String typeName, CompilerDriver driver) {
        String bin = structReturnName(typeName, driver);
        if (bin == null) return null;
        int dot = bin.lastIndexOf('.');
        if (dot < 0) return new Type.ClassType("", bin, List.of());
        return new Type.ClassType(bin.substring(0, dot), bin.substring(dot + 1), List.of());
    }

    /** Retorno bindável no JVM e no JS (Native fica FFI001) — gate do
     *  `isExternBound` (agora em `CompilerFfiBinding`, fora do pipeline p/
     *  manter a classe ≤500). No JS
     *  o host devolve os campos e o guest reconstrói (`__kof_ffi_from`). */
    static boolean structReturnBindable(CompilerDriver driver, ExternalFunctionNode ext) {
        return (driver.target == Target.JVM || driver.target == Target.JS)
                && structReturnType(ext.returnType(), driver) != null;
    }

    /** Type do `KofCall` de retorno do `kof_ffi`: o ClassType do record se for
     *  struct bindável, senão o escalar de sempre (lowerer ≤500). */
    static Type callReturnType(CompilerDriver driver, ExternalFunctionNode ext) {
        Type st = structReturnType(ext.returnType(), driver);
        return st != null ? st : returnType(ext.returnType());
    }

    /** D6-1/3.8b (JVM): se {@code typeName} for um `record` do unit corrente cujos
     *  campos são TODOS escalares não-ponteiro (i/j/f/d/b — `String`/`S` fica de
     *  fora no v1: campo `char*` é ponteiro, outra fatia), devolve a string de
     *  chars dos campos (ex. "ii"); senão null (o gate mantém FFI001/FFI002). */
    static String structFieldChars(String typeName, CompilerDriver driver) {
        if (typeName == null || driver == null || driver.currentUnit == null) return null;
        String simple = simpleName(typeName);
        RecordDeclarationNode rec = null;
        for (AstNode d : driver.currentUnit.declarations()) {
            if (d instanceof RecordDeclarationNode r && r.name().equals(simple)) { rec = r; break; }
        }
        if (rec == null || rec.components().isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (RecordComponentNode comp : rec.components()) {
            Character ch = structFieldChar(comp.type());
            if (ch == null) return null;   // campo não-escalar/ponteiro → não-bindável
            sb.append(ch.charValue());
        }
        return sb.toString();
    }

    /** char do layout de um campo de struct: só numérico/bool (sem `S`/ponteiro). */
    static Character structFieldChar(String t) {
        if (isLongFFI(t)) return 'j';
        if (isFloatFFI(t)) return 'f';
        if (isBoolFFI(t)) return 'b';
        if (CompilerPipeline.isDoubleType(t)) return 'd';
        if (CompilerPipeline.isIntType(t)) return 'i';
        return null;
    }

    /** D6-2 (3.8b fatia 3): `T[]` de elemento escalar numérico/bool vira um `ptr`
     *  C (copy-in por chamada). Devolve o char do ELEMENTO (i/j/f/d/b) ou null
     *  se não for array de escalar (ex. `String[]` = array de ponteiros, fora do
     *  v1; aninhado também). */
    static Character arrayElemChar(String typeName) {
        if (typeName == null || !typeName.endsWith("[]")) return null;
        String base = typeName.substring(0, typeName.length() - 2);
        Character c = paramChar(base);
        return (c == null || c.charValue() == 'S') ? null : c;
    }

    /** D-MEM-FFI-CROSS-FULL face 2 (30/09): `String[]` é um array de `char*`
     *  (ponteiros), distinto do copy-in escalar de `arrayElemChar`. O runtime
     *  empacota um `char**` com o payload de cada `String` (offset 24), NULL→0.
     *  Bindável no JVM (FFM) e nos nativos (x86-64 + cross); JS/estruturais
     *  ficam fora do conjunto (R6). */
    static boolean isStringArray(String typeName) {
        return typeName != null && typeName.endsWith("[]")
                && CompilerPipeline.isStringType(typeName.substring(0, typeName.length() - 2));
    }

    /** D6-3 / D-R3-BUFFER: `Buffer(U8)` como parâmetro `extern` (INOUT). Aceita
     *  `Buffer`, `Buffer(U8)` e `Buffer<Byte>` (o parser normaliza `Buffer(U8)`→
     *  `Buffer`); o runtime faz copy-in / chamada / copy-back. */
    static boolean isBufferParam(String typeName) {
        if (typeName == null) return false;
        String s = simpleName(typeName);
        if (!s.startsWith("Buffer")) return false;
        String rest = s.substring("Buffer".length());
        return rest.isEmpty() || rest.startsWith("(") || rest.startsWith("<");
    }

    static Type returnType(String r) {
        if (isVoidFFI(r)) return Type.PrimitiveType.VOID;
        if (CompilerPipeline.isDoubleType(r)) return Type.PrimitiveType.DOUBLE;
        if (isLongFFI(r)) return Type.PrimitiveType.LONG;
        if (isFloatFFI(r)) return Type.PrimitiveType.FLOAT;
        if (isBoolFFI(r)) return Type.PrimitiveType.BOOL;
        if (CompilerPipeline.isStringType(r)) return BuiltinTypes.STRING;
        return Type.PrimitiveType.INT;
    }

    /** #431 (Native): char do layout FFI a partir do Type ja baixado na IR
     *  (o KofCall nativo carrega os tipos declarados do `extern`). null = fora
     *  do conjunto escalar (callback/array/struct — nunca alcançável no call
     *  site nativo, o gate FFI001 filtra antes). */
    public static Character charOfType(Type t) {
        if (t == null) return null;
        if (Type.isVoid(t)) return 'v';
        if (t instanceof Type.PrimitiveType pt) {
            switch (pt.name()) {
                case "int": return 'i';
                case "long": return 'j';
                case "float": return 'f';
                case "double": return 'd';
                case "bool": case "boolean": return 'b';
                default: return null;
            }
        }
        if (t instanceof Type.NullableType nt) return charOfType(nt.inner());
        if (BuiltinTypes.isString(t)) return 'S';
        return null;
    }

    /** Type do parâmetro `extern` (o caminho nativo empurra o valor cru na
     *  pilha de operandos com este tipo; espelha returnType). */
    public static Type paramType(String t) {
        if (CompilerPipeline.isIntType(t)) return Type.PrimitiveType.INT;
        if (isLongFFI(t)) return Type.PrimitiveType.LONG;
        if (isFloatFFI(t)) return Type.PrimitiveType.FLOAT;
        if (isBoolFFI(t)) return Type.PrimitiveType.BOOL;
        if (CompilerPipeline.isStringType(t)) return BuiltinTypes.STRING;
        if (CompilerPipeline.isDoubleType(t)) return Type.PrimitiveType.DOUBLE;
        return null;
    }

    static boolean isLongFFI(String t) { return "long".equals(t) || "Long".equals(t); }
    static boolean isFloatFFI(String t) { return "float".equals(t) || "Float".equals(t); }
    static boolean isBoolFFI(String t) {
        return "bool".equals(t) || "boolean".equals(t) || "Boolean".equals(t) || "Bool".equals(t);
    }

    // ---- callbacks / upcalls (R3, fatia 3.4): token C(<ret><params>) ----------------
    // Parâmetros bindáveis: os escalares {Int, Long, Float, Double, Boolean} E String
    // (um `char*` que entra no callback: o runtime faz o bridge ADDRESS->String na
    // fronteira do upcall, espelhando o downcall `getString`; 3.4-C3.4). O RETORNO do
    // callback continua primitivo-ou-void: devolver `String` exigiria entregar ao C um
    // `char*` cujo dono da memória não é observável no contrato síncrono → fica fora do
    // conjunto bindável e o gate mantém FFI001/FFI002 honestos (R6). void/pointer/struct/
    // função-aninhada como parâmetro continuam null (não-bindável).
    // Ex.: "(String, Int) -> Int" -> descritor "iSi"; "(Int) -> String" -> null (retorno S).

    static Character cbParamChar(String t) {
        // 'S' é bindável como ARGUMENTO (char*->String no upcall); só o não-escalar
        // (função/struct/pointer) — paramChar==null — cai fora do conjunto.
        return paramChar(t);
    }

    static Character cbReturnChar(String t) {
        if (isVoidFFI(t)) return 'v';
        Character c = paramChar(t);
        if (c == null || c.charValue() == 'S') return null;   // String RETURN: não-bindável
        return c;
    }

    static boolean isFunctionType(String t) {
        return t != null && t.startsWith("(") && t.contains(" -> ");
    }

    /** Descritor de callback ("r" + chars dos params) ou null se não for bindável. */
    static String callbackDescriptor(String t) {
        if (!isFunctionType(t)) return null;
        int close = matchParen(t, 0);
        if (close < 0) return null;
        String rest = t.substring(close + 1).trim();
        if (!rest.startsWith("->")) return null;
        Character rc = cbReturnChar(rest.substring(2).trim());
        if (rc == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(rc.charValue());
        String paramsStr = t.substring(1, close).trim();
        if (!paramsStr.isEmpty()) {
            for (String p : splitTopLevel(paramsStr)) {
                Character pc = cbParamChar(p.trim());
                if (pc == null) return null;
                sb.append(pc.charValue());
            }
        }
        return sb.toString();
    }

    private static int matchParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static java.util.List<String> splitTopLevel(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '<') depth++;
            else if (c == ')' || c == '>') depth--;
            else if (c == ',' && depth == 0) { out.add(s.substring(start, i)); start = i + 1; }
        }
        out.add(s.substring(start));
        return out;
    }
}
