package dev.kof.compiler;

import dev.kof.compiler.SymbolTable.LocalVariableSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * Inferência de tipos de MethodCallExpr, extraída do SemanticAnalyzer
 * (REFACTOR-500 fase 6). Mantém a ordem exata dos branches do switch
 * original (diagnósticos SEM0xx na mesma sequência).
 */
public final class SemMethodCallTyper {

    private SemMethodCallTyper() {}

    static final String SSE_CONNECTION_TYPE =
            "dev.kof.runtime.KofRuntime$SseConnection";

    private static final java.util.Set<String> PRIMITIVE_METHODS =
            java.util.Set.of("toString", "toInt", "toLong", "toFloat", "toDouble",
                    "toHexString", "toBinaryString");

    static Type infer(SemanticAnalyzer sa, MethodCallExpr mc, SymbolTable scope) {
        // F10: métodos de instância do handle de process.spawn
        Type recv = null;
        if (mc.receiver() != null) {
            recv = SemExpressionTyper.inferType(sa, mc.receiver(), scope);
            // SG-005: deref de T? sem narrowing é erro — null safety é por
            // narrowing (`if (x != null)` re-tipa o símbolo no escopo filho,
            // StatementAnalyzer). Se o receiver AINDA é NullableType aqui, o
            // acesso é direto e seria NPE em runtime. Antes o lowering
            // desembrulhava silenciosamente (ExpressionTyper) — advisory.
            if (recv instanceof Type.NullableType && sa.diagnostics() != null) {
                SourcePosition mcPos = mc.position();
                sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                        mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                        "receiver is nullable (T?); narrow first: if (x != null) { x.method() }",
                        "SEM049");
            }
            // bug 17: array não tem método get()/set() — a API é o
            // operador arr[i]. Antes o compilador aceitava e emitia
            // bytecode inválido (ClassFormatError no JVM, undefined
            // reference no Native).
            if (recv instanceof Type.ArrayType && sa.diagnostics() != null) {
                // #512: o diagnóstico saía em :0:0 sem arquivo — impossível de
                // localizar no editor/CLI. Segue o padrão SEM049 acima
                // (posição do call-site). O hint arr[i] só faz sentido para
                // get/set; em `arr.toString()` ele empurrava o usuário para a
                // sintaxe errada.
                SourcePosition mcPos = mc.position();
                boolean accessMethod = "get".equals(mc.methodName()) || "set".equals(mc.methodName());
                sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                        mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                        "array has no method '" + mc.methodName() + "()'"
                                + (accessMethod ? "; use the operator arr[i] / arr[i] = v" : ""),
                        "SEM028");
            }
            // #362 (R6, nunca silencioso): método de instância em primitivo.
            // O emit só conhece a whitelist de primitivos (toString + as
            // conversões §89 + formatadores §218); fora dela caía no ramo
            // genérico e emitia KofCall com owner "" → Methodref "" no
            // constant pool → ClassFormatError no load (JVM) / undefined
            // reference (Native) — compilava e o `check` dizia no-errors, só
            // crashava em execução. `Int`/`Bool`/`Char` etc. não são classes
            // em Kof (idiom: comparação é `a == b`; matemática é função
            // top-level, ex. math.abs(x); precedentes SEM050 de campo e
            // bug 99; mesmo gate compartilhado SEM028/SEM072/SEM073).
            if (recv instanceof Type.PrimitiveType pt && !"void".equals(Type.canonicalPrimitiveName(pt.name()))
                    && !PRIMITIVE_METHODS.contains(mc.methodName())
                    && sa.diagnostics() != null) {
                SourcePosition mcPos = mc.position();
                sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                        mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                        "'" + Type.canonicalPrimitiveName(pt.name()) + "' is a primitive — it has no method '"
                                + mc.methodName() + "()' (primitives have toString() and the conversions "
                                + "toInt()/toLong()/toFloat()/toDouble(); comparison is `a == b`, math is "
                                + "top-level functions, e.g. math.abs(x))",
                        "SEM074");
            }
            if (KofProcess.isHandle(recv)) {
                List<Type> argTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) argTypes.add(SemExpressionTyper.inferType(sa, arg, scope));
                KofProcess.ProcessCall hm = KofProcess.handleMethod(mc.methodName(), argTypes);
                if (hm != null) return hm.returnType();
            }
            // §502 (SEM025): Handle<T> do `spawn` não tem método de instância —
            // o idioma é `await h` (ou `awaitTimeout(h, ms)`). Sem este gate,
            // `h.bogus()` compilava limpo e o emit caía no CompletableFuture
            // com `invokevirtual ...bogus` → NoSuchMethodError.
            if (TypeChecker.isConcurrentHandle(recv) && sa.diagnostics() != null) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                SourcePosition mcPos = mc.position();
                sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                        mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                        "Handle<T> has no method '" + mc.methodName()
                                + "()'; use `await h` to get the value",
                        "SEM025");
                return Type.UnknownType.UNKNOWN;
            }
            // Canais tipados: c.send(v) / c.receive() -> T
            if (BuiltinTypes.isChannel(recv)) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                if ("send".equals(mc.methodName())) return Type.PrimitiveType.VOID;
                if ("receive".equals(mc.methodName())) return BuiltinTypes.channelElement(recv);
            }
            // Map<K,V>: get() devolve V? para valores de referência (ausência = null,
            // narrowing via if (x != null)); primitivos/UI não representam ausência
            if (BuiltinTypes.isMap(recv)) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                Type valueType = BuiltinTypes.mapValue(recv);
                // mapOf() nasce Map<Unknown,Unknown>: o primeiro put() pina os
                // tipos no SÍMBOLO do local (espelha o pin de IR no emit,
                // CollectionCallLowerer) — sem isso get() permanecia
                // Nullable(Unknown) no cache semântico enquanto o emit já
                // via o tipo concreto (divergência typer/emit, VerifyError)
                if ("put".equals(mc.methodName()) && valueType instanceof Type.UnknownType
                        && mc.arguments().size() == 2
                        && mc.receiver() instanceof IdentifierExpr rid) {
                    Type putKey = sa.expressionTypes().get(mc.arguments().get(0));
                    Type putValue = sa.expressionTypes().get(mc.arguments().get(1));
                    if (putKey != null && putValue != null
                            && !(putKey instanceof Type.UnknownType)
                            && !(putValue instanceof Type.UnknownType)
                            && sa.currentScope() != null) {
                        SymbolTable scopeTbl = sa.currentScope();
                        // resolve o símbolo em qualquer escopo ancestral, mas
                        // atualiza no escopo que O DEFINE (updateLocalType só
                        // mexe no escopo dono — retorna false caso contrário)
                        SymbolTable owner = null;
                        for (SymbolTable s = scopeTbl; s != null; s = s.parent()) {
                            if (s.hasLocal(rid.name())) { owner = s; break; }
                        }
                        if (owner != null && owner.updateLocalType(rid.name(),
                                new Type.ClassType("kof", "Map", List.of(putKey, putValue)))) {
                            valueType = putValue;
                        }
                    }
                }
                if ("get".equals(mc.methodName())) {
                    // SG-008 (bug 87): get() devolve V? para TODO valor —
                    // ausência é null comparável, nunca NPE por unbox
                    return new Type.NullableType(valueType);
                }
                // D-NULL-INTENT/I7: mesma razão do `get` acima — Java Map
                // contract (valor anterior/removido OU null quando ausente).
                // Este é o typer consultado no EMIT (ExpressionTyper.
                // inferExprType) — divergir do CollectionCallLowerer (que já
                // declara o KofCall como V?) reproduzia o mesmo bug do #278:
                // a chamada devolve boxed de verdade mas o consumidor (ex.
                // `==`) achava que era primitivo cru → VerifyError.
                if ("put".equals(mc.methodName()) || "remove".equals(mc.methodName()))
                    return new Type.NullableType(valueType);
                if ("getOrDefault".equals(mc.methodName())) return valueType;
                // #386 — containsValue→Bool; putIfAbsent→V? (contrato Java:
                // anterior OU null; D-NULL-INTENT/I7, mesmo par put/remove).
                if ("containsValue".equals(mc.methodName())) return Type.PrimitiveType.BOOL;
                if ("putIfAbsent".equals(mc.methodName())) return new Type.NullableType(valueType);
                if ("size".equals(mc.methodName()) || "length".equals(mc.methodName())
                        || "count".equals(mc.methodName())) return Type.PrimitiveType.INT;
                if ("contains".equals(mc.methodName()) || "containsKey".equals(mc.methodName())
                        || "isEmpty".equals(mc.methodName())) return Type.PrimitiveType.BOOL;
                if ("clear".equals(mc.methodName())) return Type.PrimitiveType.VOID;
                if ("keys".equals(mc.methodName())) return new Type.ClassType("kof", "List",
                        List.of(BuiltinTypes.mapKey(recv)));
                if ("values".equals(mc.methodName())) return new Type.ClassType("kof", "List",
                        List.of(valueType));
            }
        }
        // §353: o SEM typer nao conhecia as faces de kof.io — o corpo de lambda
        // `() -> File("x").exists()` inferia UNKNOWN e o call-site rejeitava com
        // SEM014 ("expected 'function' but got 'function'"). Espelha o typer do
        // emit (MethodCallTyper, ramo KofIo.isIoType) — mesma tabela, nenhum
        // contrato novo.
        if (KofIo.isIoType(recv)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            KofIo.IoCall ioCall = KofIo.instanceMethod(recv, mc.methodName(), mc.arguments().size());
            if (ioCall != null) return ioCall.returnType();
            if (KofIo.isIdentityMethod(mc.methodName())) return recv;
        }
        // D-R3-BUFFER: espelha o ramo do emit (MethodCallTyper) para Buffer(U8).
        if (KofBuffer.isBufferType(recv)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            KofBuffer.BufferCall bufferCall =
                    KofBuffer.instanceMethod(recv, mc.methodName(), mc.arguments().size());
            if (bufferCall != null) return bufferCall.returnType();
        }
        // D-SECRETS face 1: espelha o ramo do emit para o tipo Secret.
        if (KofSecurity.isSecretType(recv) || KofSecurity.isKeyHandleType(recv)) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            KofSecurity.SecCall secretCall =
                    KofSecurity.instanceMethod(recv, mc.methodName(), mc.arguments().size());
            if (secretCall != null) return secretCall.returnType();
        }
        // #490 (SEM102): método desconhecido em tipo builtin de kof.buffer /
        // kof.security (Buffer/Secret/KeyHandle). Os ramos acima só devolvem os
        // métodos da TABELA ao vivo; sem este guard o fall-through não tinha
        // contrato — o emit devolvia o PRÓPRIO receptor (no-op silencioso:
        // `secrets.of("x").bogus()` imprimia o Secret) ou vazava um UNKNOWN cujo
        // nome de classe vazio abortava o load (`ClassFormatError: Illegal class
        // name ""`: `s.bogus(1,2)`). É o mesmo mecanismo do #617 (kof.io), uma
        // família ao lado. A mensagem nomeia o tipo e o método.
        if ((KofBuffer.isBufferType(recv) || KofSecurity.isSecretType(recv)
                || KofSecurity.isKeyHandleType(recv)) && sa.diagnostics() != null) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            String builtinName = KofBuffer.isBufferType(recv) ? "Buffer"
                    : (KofSecurity.isSecretType(recv) ? "Secret" : "KeyHandle");
            SourcePosition mcPos = mc.position();
            sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                    mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                    "'" + builtinName + "' has no method '" + mc.methodName() + "()'",
                    "SEM102");
            return Type.UnknownType.UNKNOWN;
        }
        // #617 (SEM102): método desconhecido em tipo builtin de kof.io
        // (File/Path/Directory) — mesmo guard da família SEM028 (array):
        // aceitar em silêncio deixava o emit sem contrato (retorno UNKNOWN)
        // e o programa "rodava" como no-op; `.toString()` no valor chegava a
        // dar ClassFormatError "Illegal class name \"\"" no JVM. O hint
        // aponta o idioma vivo: para criar diretório é
        // Directory(path).createDirectories().
        if (KofIo.isIoType(recv) && sa.diagnostics() != null) {
            for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
            SourcePosition mcPos = mc.position();
            boolean dir = KofIo.isDirectory(recv);
            String mkdirHint = "mkdir".equals(mc.methodName())
                    ? "; to create a directory use Directory(path).createDirectories()" : "";
            sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                    mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                    "'" + (dir ? "Directory" : "File") + "' has no method '" + mc.methodName()
                            + "()'" + mkdirHint,
                    "SEM102");
            return Type.UnknownType.UNKNOWN;
        }
        // §499 (SEM074): método estático desconhecido em nome de tipo builtin
        // (`String.bogus()`, `Int.bogus()`, ...). O Kof expõe os estáticos REAIS
        // do JDK nesses nomes (String.valueOf/join/format, Long.parseLong,
        // Double.isNaN, Bool.parseBoolean, ...), resolvidos por reflexão no
        // lowerer (`ExpressionInstanceCallLowerer` + `JdkReflectionResolver`).
        // Quando o método não existe, sem este gate o typer deixava UNKNOWN e o
        // lowerer emitia `invokestatic <Owner>.bogus` → NoSuchMethodError
        // (compilava limpo). Só rejeita quando o owner JDK é conhecido E o
        // método é ausente — sem falso-positivo em interop indisponível.
        if (mc.receiver() instanceof IdentifierExpr typeRecv
                && !typeRecv.name().isEmpty()
                && Character.isUpperCase(typeRecv.name().charAt(0))
                && sa.diagnostics() != null) {
            String jdkOwner = jdkStaticOwner(typeRecv.name());
            if (jdkOwner != null
                    && JdkReflectionResolver.isJdkClass(jdkOwner)
                    && !JdkReflectionResolver.hasJdkMethod(jdkOwner, mc.methodName(),
                            mc.arguments().size())) {
                for (ExpressionNode arg : mc.arguments()) SemExpressionTyper.inferType(sa, arg, scope);
                SourcePosition mcPos = mc.position();
                sa.diagnostics().error(mcPos != null ? mcPos.file() : "",
                        mcPos != null ? mcPos.line() : 0, mcPos != null ? mcPos.column() : 0, 0,
                        "'" + typeRecv.name() + "' has no static method '" + mc.methodName() + "()'",
                        "SEM074");
                return Type.UnknownType.UNKNOWN;
            }
        }
        Type builtin = BuiltinCallTyper.infer(sa, mc, scope);
        if (builtin != null) return builtin;
        if (mc.receiver() != null) {
            Type member = MemberCallTyper.infer(sa, mc, scope);
            if (member != null) return member;
        }
        return BuiltinCallTyper.inferTail(sa, mc, scope);
    }

    /**
     * §499: nome interno JDK dos tipos builtin usados como receiver estático.
     * Reproduz o mapa do emit em {@code ExpressionInstanceCallLowerer} para os
     * que ele mapeia e estende Char/Byte/Short/Object — todos os nomes de tipo
     * builtin, para que o gate valide o membro estático em qualquer um deles.
     */
    static String jdkStaticOwner(String name) {
        return switch (name) {
            case "String", "string" -> "java/lang/String";
            case "Int", "int", "Integer" -> "java/lang/Integer";
            case "Long", "long" -> "java/lang/Long";
            case "Float", "float" -> "java/lang/Float";
            case "Double", "double" -> "java/lang/Double";
            case "Bool", "bool", "boolean", "Boolean" -> "java/lang/Boolean";
            case "Char", "char" -> "java/lang/Character";
            case "Byte", "byte" -> "java/lang/Byte";
            case "Short", "short" -> "java/lang/Short";
            case "Object" -> "java/lang/Object";
            case "Troolean", "troolean" -> "java/lang/Boolean";
            default -> null;
        };
    }
}
