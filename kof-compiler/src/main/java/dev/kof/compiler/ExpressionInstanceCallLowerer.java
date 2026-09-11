package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do dispatch de instância do MethodCallExpr (receiver != null):
 * chamadas super, recepção em Types Ui/Web/Media/Io/FunctionType/List/coleções
 * e o fallback para método resolvido/interface/runtime.
 */
public final class ExpressionInstanceCallLowerer {

    /** Métodos de String cujo parâmetro é String/CharSequence (SEM051 — bug 100). */
    private static final java.util.Set<String> STRING_ARG_METHODS = java.util.Set.of(
            "indexOf", "lastIndexOf", "contains", "startsWith", "endsWith",
            "split", "concat", "equalsIgnoreCase", "compareTo", "compareToIgnoreCase");

    /**
     * Funções da stdlib {@code strings.*} que NÃO são métodos de instância em
     * Kof (SEM052 — bug 96). Chamá-las como método era ACEITO e quebrava de
     * um jeito em cada target (JVM NoSuchMethodError, Native link-fail, JS
     * roda o nativo do JS, Script roda/quebra por reflexão) — paridade
     * absoluta JVM=JS=X86=ARM=RISC. O idiom do corpus é só a função
     * (training/idioms/stdlib.md, learn/39-stdlib.md).
     */
    private static final java.util.Set<String> NOT_INSTANCE_METHODS = java.util.Set.of(
            "repeat", "truncate", "padLeft", "padRight", "padStart", "padEnd",
            "capitalize", "uncapitalize", "reverse", "count",
            "isAlpha", "isNumeric", "isAlphaNumeric", "isAscii",
            "isUpperCase", "isLowerCase", "toCamelCase", "toPascalCase",
            "toSnakeCase", "toKebabCase", "slugify", "escapeHtml",
            "unescapeHtml", "escapeJson", "removeWhitespace", "normalizeWhitespace");

    private ExpressionInstanceCallLowerer() {}

    /** Nome canônico da função `strings.*` p/ o nome de método errado (SEM052). */
    private static String padHint(String methodName) {
        return switch (methodName) {
            case "padStart" -> "padLeft";
            case "padEnd" -> "padRight";
            default -> methodName;
        };
    }

    /** SEM051: Char/numérico/array/classe NÃO-Kof-String num formal String. */
    private static boolean notStringForFormal(Type t) {
        if (t instanceof Type.NullableType nt) t = nt.inner();
        if (t instanceof Type.UnknownType) return false;
        if (BuiltinTypes.isString(t)) return false;
        return TypeMetrics.isPrimitiveType(t) || t instanceof Type.ClassType
                || t instanceof Type.ArrayType;
    }

    private static String typeNameFor(Type t) {
        if (t instanceof Type.PrimitiveType p) return switch (p.name()) {
            case "char" -> "Char";
            case "int" -> "Int";
            case "long" -> "Long";
            case "double" -> "Double";
            case "float" -> "Float";
            case "bool" -> "Bool";
            case "byte" -> "Byte";
            case "short" -> "Short";
            default -> p.name();
        };
        if (t instanceof Type.ArrayType a) return typeNameFor(a.componentType()) + "[]";
        if (t instanceof Type.ClassType c) return c.name();
        return String.valueOf(t);
    }

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals) {
    if (recvType0(driver, mc, locals) instanceof Type.ArrayType arr) {
        // array não é coleção Kof: `.get(i)` → array index (AALOAD),
        // `.size`/`.length`/`.count` → arraylength. Sem isto o fallback
        // emitia KofCall com owner ArrayType → JvmTypeMapper produzia
        // internalName "" → Methodref "" no constant pool →
        // ClassFormatError: Illegal class name "" (GitHub #30).
        localIdx = ExpressionLowerer.emitExpression(driver, mc.receiver(), ops, owner, localIdx, locals);
        if ("get".equals(mc.methodName()) && mc.arguments().size() == 1) {
            localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
            ops.add(new KofArrayLoad(arr.componentType()));
        } else if (("size".equals(mc.methodName()) || "length".equals(mc.methodName())
                || "count".equals(mc.methodName())) && mc.arguments().isEmpty()) {
            ops.add(new KofArrayLength());
        } else if (mc.arguments().isEmpty()) {
            // .isEmpty()/.clear() não fazem sentido p/ array: diagnóstico
            // em vez de bytecode inválido (R6)
            if (driver.currentDiagnostics != null) {
                SourcePosition p = mc.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        "array does not have method '" + mc.methodName() + "' (use .length for size)",
                        "SEM025");
            }
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        } else {
            if (driver.currentDiagnostics != null) {
                SourcePosition p = mc.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        "array does not have method '" + mc.methodName() + "'",
                        "SEM025");
            }
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
                ops.add(new KofPop());
            }
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
        }
        return localIdx;
    }
    if (mc.receiver() instanceof IdentifierExpr sid && "super".equals(sid.name())
            && !owner.isEmpty()) {
        // super.method(args): non-virtual call to the
        // superclass implementation — lowered to
        // INVOKESPECIAL on the direct superclass (JVM).
        if (driver.target.isNative() && driver.currentDiagnostics != null) {
            SourcePosition p = mc.position();
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "super." + mc.methodName()
                            + "() is not supported on the native driver.target yet (SUP001)",
                    "SUP001");
            return localIdx;
        }
        // super.metodo() só faz sentido no corpo de um método
        // de classe; dentro de lambda sintética usa o driver
        // externo capturado ($outer) — sem ele, gap honesto
        String effectiveOwner = owner;
        String ownerSimple0 = owner.substring(owner.lastIndexOf('/') + 1);
        if (driver.semanticAnalyzer == null || driver.semanticAnalyzer.getClass(ownerSimple0) == null) {
            String enc = driver.lambdaEnclosingOwner.get(owner);
            if (enc == null) {
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = mc.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "super." + mc.methodName()
                                    + "() is only valid inside class methods (SUP002)",
                            "SUP002");
                }
                return localIdx;
            }
            effectiveOwner = enc;
        }
        String superInternal = HierarchyResolver.findSuperClass(effectiveOwner, driver.semanticAnalyzer);
        if (superInternal == null) superInternal = "java/lang/Object";
        // nomes declarados com pontos (android.view.View)
        // viram nome interno JVM para resolução e emissão
        superInternal = superInternal.replace('.', '/');
        Type superType = CompilerTypes.ownerTypeFromInternal(superInternal, driver.semanticAnalyzer);
        SymbolTable.MethodSymbol superMethod = null;
        if (driver.semanticAnalyzer != null) {
            String superSimple = superInternal.substring(superInternal.lastIndexOf('/') + 1);
            SymbolTable.Symbol s = driver.semanticAnalyzer.resolveInHierarchy(superSimple, mc.methodName());
            if (s instanceof SymbolTable.MethodSymbol ms) superMethod = ms;
        }
        List<Type> paramTypes;
        Type returnType;
        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
        ExternalClasspath.MethodSignature extSig = null;
        if (superMethod == null && osig == null && driver.externalClasspath != null) {
            extSig = driver.externalClasspath.resolveMethod(superInternal, mc.methodName(),
                    mc.arguments().size());
        }
        if (superMethod == null && osig == null && extSig == null
                && HierarchyResolver.hierarchyFullyKnown(superInternal, driver.semanticAnalyzer) && driver.currentDiagnostics != null) {
            // hierarquia inteiramente conhecida e o método não
            // existe — erro em compile-time, não NoSuchMethodError
            SourcePosition p = mc.position();
            driver.currentDiagnostics.error(p != null ? p.file() : "",
                    p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                    "method '" + mc.methodName() + "' does not exist in superclass '"
                            + HierarchyResolver.superSimpleName(superInternal) + "'",
                    "SEM016");
            return localIdx;
        }
        if (superMethod != null
                && superMethod.parameterTypes().size() == mc.arguments().size()) {
            paramTypes = superMethod.parameterTypes();
            returnType = superMethod.returnType();
        } else if (osig != null) {
            paramTypes = osig.parameterTypes();
            returnType = osig.returnType();
        } else if (extSig != null) {
            // assinatura real lida do classpath externo — o
            // descritor emitido casa com a classe externa
            List<Type> formal = new ArrayList<>();
            for (String d : extSig.parameterDescriptors()) {
                formal.add(ExternalClasspath.typeFromDescriptor(d));
            }
            paramTypes = formal;
            returnType = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
        } else {
            paramTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) paramTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            returnType = ExpressionTyper.inferExprType(driver, mc, locals);
        }
        // receiver: dentro de lambda sintética é o $outer e a
        // chamada vira uma PONTE kof_super$metodo na classe dona
        IRLocalVariable outerVar = driver.findLocalVar("$outer", locals);
        if (outerVar != null) {
            ops.add(new KofLoadLocal(outerVar.type(), outerVar.index()));
            String bridgeName = driver.ensureSuperBridge(effectiveOwner, superInternal,
                    mc.methodName(), paramTypes, returnType);
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), paramTypes,
                    ops, effectiveOwner, localIdx, locals);
            ops.add(new KofCall(CompilerTypes.ownerTypeFromInternal(effectiveOwner, driver.semanticAnalyzer), bridgeName,
                    paramTypes, returnType, KofCallKind.INSTANCE));
            return localIdx;
        } else {
            ops.add(new KofLoadLocal(CompilerTypes.ownerTypeFromInternal(effectiveOwner, driver.semanticAnalyzer), 0));
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), paramTypes, ops, owner, localIdx, locals);
            ops.add(new KofCall(superType, mc.methodName(), paramTypes, returnType, KofCallKind.SUPER));
            return localIdx;
        }
    }
    localIdx = ExpressionLowerer.emitExpression(driver, mc.receiver(), ops, owner, localIdx, locals);
    Type recvType = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    // narrowing de null-safety (`if (x != null) { x.substring(...) }`):
    // dispatch pelo inner — antes emitia `"".substring` (owner "" inválido)
    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
    if (KofUi.isUiType(recvType)) {
        localIdx = driver.emitUiInstance(recvType, mc, ops, owner, localIdx, locals);
        return localIdx;
    }
    if (CompilerTypes.isEnumType(recvType, driver.currentUnit) && "name".equals(mc.methodName()) && mc.arguments().isEmpty()) {
        // o valor do enum JÁ é o nome (String em runtime): identidade
        return localIdx;
    }
    if (KofWeb.isAppType(recvType)) {
        List<Type> webArgTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
        if (webCall != null) {
            boolean nativeWebT1 = (driver.target == Target.NATIVE
                    || driver.target == Target.NATIVE_RISCV64
                    || driver.target == Target.NATIVE_AARCH64)
                    && (webCall.function().equals("kof_web_listen")
                        || webCall.function().equals("kof_web_route"));
            if (driver.target != Target.JVM && driver.target != Target.ANDROID && !nativeWebT1) {
                String webCode = KofWeb.gapCode(webCall.function());
                String webMsg = switch (webCode) {
                    case "WEB002" -> "web TLS: not available on the " + driver.target
                            + " driver.target yet (WEB002)";
                    case "WEB003" -> "web SSE: not available on the " + driver.target
                            + " driver.target yet (WEB003)";
                    case "WEB004" -> "web WebSocket: not available on the " + driver.target
                            + " driver.target yet (WEB004)";
                    default -> "web: not available on the " + driver.target
                            + " driver.target yet (WEB001)";
                };
                if (driver.currentDiagnostics != null) {
                    driver.currentDiagnostics.error(mc.position() != null ? mc.position().file() : "",
                            mc.position() != null ? mc.position().line() : 0,
                            mc.position() != null ? mc.position().column() : 0,
                            0, webMsg, webCode);
                }
                return localIdx;
            }
            List<Type> webParams = new ArrayList<>();
            webParams.add(BuiltinTypes.STRING);
            if (KofWeb.isRouteMethod(mc.methodName()) && !"ws".equals(mc.methodName())) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, mc.methodName().toUpperCase()));
                webParams.add(BuiltinTypes.STRING);
            }
            for (ExpressionNode arg : mc.arguments()) {
                webParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(KofWeb.APP, webCall.function(), webParams,
                    webCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }
    if (KofMedia.isHandleType(recvType)) {
        KofMedia.MediaCall mediaCall =
                KofMedia.handleMethod(recvType, mc.methodName(), mc.arguments().size());
        if (mediaCall != null) {
            List<Type> mediaParams = new ArrayList<>();
            mediaParams.add(Type.PrimitiveType.INT);      // handle (receiver)
            for (ExpressionNode arg : mc.arguments()) {
                mediaParams.add(ExpressionTyper.inferExprType(driver, arg, locals));
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            ops.add(new KofCall(new Type.ClassType("dev.kof.runtime", "KofRuntime", List.of()),
                    mediaCall.function(), mediaParams,
                    mediaCall.returnType(), KofCallKind.FUNCTION));
        }
        return localIdx;
    }
    if (KofIo.isIoType(recvType)) {
        if (KofIo.isIdentityMethod(mc.methodName())) {
            return localIdx;
        }
        KofIo.IoCall ioCall = KofIo.instanceMethod(recvType, mc.methodName(), mc.arguments().size());
        if (ioCall != null) {
            // receiver File/Path/Directory é apagado pra String
            // path em runtime (empilhado acima); os METHOD args
            // alinham com ioCall.parameterTypes() — a conversão
            // formal (int literal → long slot no readRange)
            // evita o frame bug I/J no visitMaxs
            localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), ioCall.parameterTypes(),
                    ops, owner, localIdx, locals);
            List<Type> ioParams = new ArrayList<>();
            ioParams.add(BuiltinTypes.STRING);
            ioParams.addAll(ioCall.parameterTypes());
            ops.add(new KofCall(new Type.ClassType("kof.io", "Io", List.of()),
                    ioCall.function(), ioParams, ioCall.returnType(), KofCallKind.FUNCTION));
            return localIdx;
        }
    }
    if (recvType instanceof Type.FunctionType ft) {
        if (ft.className() == null) {
            // bug 8: valor de TIPO DE FUNÇÃO DECLARADO (param
            // (s: (Int) -> Int), sem classe sintética). Todas as
            // lambdas da assinatura implementam a interface
            // sintética — invoca via INVOKEINTERFACE.
            List<Type> argTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            for (ExpressionNode arg : mc.arguments()) {
                localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            }
            Type iface = driver.lambdaInterfaceType(ft);
            ops.add(new KofCall(iface, "invoke", argTypes, ft.returnType(), KofCallKind.INTERFACE));
            return localIdx;
        }
        List<Type> argTypes = new ArrayList<>();
        for (ExpressionNode arg : mc.arguments()) argTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        }
        // f.invoke(): o owner precisa ser a classe sintética
        // da lambda — FunctionType não tem nome JVM
        Type invokeOwner = new Type.ClassType("", ft.className(), List.of());
        ops.add(new KofCall(invokeOwner, "invoke", argTypes, ft.returnType(), KofCallKind.INSTANCE));
        return localIdx;
    }
    if (BuiltinTypes.isList(recvType) || BuiltinTypes.isChannel(recvType)
            || BuiltinTypes.isMap(recvType) || BuiltinTypes.isSet(recvType)) {
        int handled = CollectionCallLowerer.lower(driver, recvType, mc, ops, owner, localIdx, locals);
        if (handled >= 0) return handled;
    }
    Type methodReturnType = Type.UnknownType.UNKNOWN;
    List<Type> methodParamTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        methodParamTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    }
    SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer.getResolvedMethod(mc);
    if (resolvedMethod != null) {
        recvType = CompilerTypes.ownerTypeFromInternal(resolvedMethod.ownerClass(), driver.semanticAnalyzer);
        methodReturnType = resolvedMethod.returnType();
        methodParamTypes = new ArrayList<>(resolvedMethod.parameterTypes());
    } else if (BuiltinTypes.isString(recvType)) {
        // paridade absoluta (JVM=JS=X86=ARM=RISC): String.equals(não-String) é
        // `false` em TODO target (uma String nunca é igual a Int/Char/record/
        // lista). O Native, porém, CRASHAVA (kof_string_equals lia o Int-boxado
        // como ponteiro-String → SIGSEGV/saída vazia) enquanto JVM/Script
        // davam `false` — paridade quebrada em silêncio (R6). Constant-fold:
        // roda o efeito do arg, descarta os dois, empilha `false` (sem tocar o
        // contrato/semântica de `equals` de String-vs-String, que segue pro
        // runtime). Não-fold: arg pode ser String em runtime (Unknown/Nullable).
        if ("equals".equals(mc.methodName()) && mc.arguments().size() == 1) {
            Type at = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
            if (at instanceof Type.NullableType nnt) at = nnt.inner();
            // fold seguro SÓ p/ primitivos (Char/Int/Long/Double/Bool/...): o
            // `kof_string_equals` do Native deref o ponteiro → SIGSEGV com Int;
            // JVM/Script/JS dariam `false`. Classes/arrays (equals(Object) é
            // válido e raramente String) seguem pro runtime: lá o KofPop do
            // fold quebra a pilha JS (stack machine sem o descarte implícito
            // p/ chamada void — underflow no builder do listOf) e o valor de
            // uma classe user-defined vs String é `false` correto no runtime
            // (Objects.equals/===). Unknown/Nullable também não-fold (pode ser
            // String em runtime).
            boolean provablyNotString = TypeMetrics.isPrimitiveType(at) && !BuiltinTypes.isString(at);
            if (provablyNotString) {
                localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
                ops.add(new KofPop());   // arg
                ops.add(new KofPop());   // receiver (empilhado no topo do lower)
                ops.add(new KofLoadLiteral(Type.PrimitiveType.BOOL, 0));
                return localIdx;
            }
        }
        // bug 96 (paridade absoluta): as funções da stdlib `strings.*` NÃO são
        // métodos de instância em Kof — chamá-las como método era ACEITO pelo
        // typer e quebrava de um jeito em CADA target (JVM `NoSuchMethodError`
        // por descritor Object, Native `undefined reference` no link, JS roda o
        // nativo `.repeat`/`.padStart` do próprio JS, Script roda/quebra por
        // reflexão). Opção B (decisão da mantenedora): REJEITAR em compile-time
        // com SEM052 apontando p/ o idiom real do corpus — o MESMO erro nos 5
        // backends (este lowering é o frontend único). NÃO confunda com
        // `toUpperCase`/`toLowerCase`/`trim`/`split`/`replace`/`substring`,
        // que SÃO métodos de String na registry (e em Kof).
        if (NOT_INSTANCE_METHODS.contains(mc.methodName()) && driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "Kof não tem método \"" + mc.methodName() + "\" de String; use a função "
                            + "da stdlib: strings." + padHint(mc.methodName()) + "(...",
                    "SEM052");
        }
        // bug 100 (R6, paridade absoluta JVM=JS=X86=ARM=RISC): argumento
        // NÃO-String num parâmetro String/CharSequence (Char, Int, Long, lista…)
        // era ACEITO e quebrava de um jeito em CADA target (JVM
        // VerifyError/NoSuchMethodError/ExceptionInInitializer, Native SIGSEGV/
        // saída vazia, Script `false`/vazio). Opção B (decisão da mantenedora):
        // REJEITAR em tempo de compilação (SEM051), o MESMO erro nos 5 backends
        // (este lowering é o frontend único). Checagem POR POSIÇÃO pela formal
        // da registry (indexOf("a", 2) tem formal String,Int → só a posição 0
        // é stringy; indexOf('c') pega na 0). compareTo/compareToIgnoreCase não
        // estão na registry (sig=null) → tratadas TODAS as posições como stringy.
        // NÃO flaguemos `replace` (formal CHAR quando args são Char — widening
        // legal) nem `charAt`/`substring` (formal numérico, fora da lista).
        if (STRING_ARG_METHODS.contains(mc.methodName()) && driver.currentDiagnostics != null) {
            StringMethodRegistry.Sig sigG = StringMethodRegistry.stringMethodSignature(
                    mc.methodName(), mc.arguments().size(), methodParamTypes);
            List<Type> formals = sigG != null ? sigG.parameterTypes() : null;
            for (int ai = 0; ai < mc.arguments().size(); ai++) {
                boolean formalIsStringy = formals == null
                        || (ai < formals.size() && (BuiltinTypes.isString(formals.get(ai))
                            || (formals.get(ai) instanceof Type.ClassType fc
                                && "CharSequence".equals(fc.name()))));
                if (!formalIsStringy) continue;
                Type at = ExpressionTyper.inferExprType(driver, mc.arguments().get(ai), locals);
                if (notStringForFormal(at)) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "String." + mc.methodName() + " não aceita " + typeNameFor(at)
                                    + " como argumento " + (ai + 1) + " (o parâmetro é String); "
                                    + "use um literal String, ex.: " + mc.methodName() + "(\"c\")",
                            "SEM051");
                    break;
                }
            }
        }
        StringMethodRegistry.Sig sig = StringMethodRegistry.stringMethodSignature(mc.methodName(), mc.arguments().size(),
                methodParamTypes);
        if (sig != null) {
            methodReturnType = sig.returnType();
            methodParamTypes = new ArrayList<>(sig.parameterTypes());
        }
    } else if (TypeMetrics.isPrimitiveType(recvType) && "toString".equals(mc.methodName())
            && mc.arguments().isEmpty()) {
        // primitivo.toString(): o primitivo não tem classe —
        // boxar e converter (String.valueOf) em vez de gerar
        // um owner vazio no bytecode (ClassFormatError)
        TypeEmitter.boxPrimitive(ops, recvType);
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        return localIdx;
    } else {
        StringMethodRegistry.Sig osig = StringMethodRegistry.objectMethodSignature(mc.methodName(), mc.arguments().size());
        if (osig != null) {
            methodReturnType = osig.returnType();
            methodParamTypes = osig.parameterTypes();
        }
    }
    if (methodReturnType instanceof Type.UnknownType) {
        // fall back to the lowering's own inference (list-get
        // chains, user classes resolved through hierarchy)
        Type inferred = ExpressionTyper.inferExprType(driver, mc, locals);
        if (!(inferred instanceof Type.UnknownType)) {
            methodReturnType = inferred;
        }
    }
    localIdx = driver.emitArgumentsWithFormalTypes(mc.arguments(), methodParamTypes, ops, owner, localIdx, locals);
    KofCallKind callKind = KofCallKind.INSTANCE;
    if (recvType instanceof Type.ClassType rt && driver.semanticAnalyzer != null) {
        if (driver.semanticAnalyzer.isInterfaceType(rt.name())) {
            callKind = KofCallKind.INTERFACE;
        }
    }
    if (callKind == KofCallKind.INSTANCE && resolvedMethod != null && driver.semanticAnalyzer != null) {
        String ownerName = resolvedMethod.ownerClass();
        if (ownerName.contains("/")) ownerName = ownerName.substring(ownerName.lastIndexOf('/') + 1);
        if (driver.semanticAnalyzer.isInterfaceType(ownerName)) {
            callKind = KofCallKind.INTERFACE;
        }
    }
    String runtimeMethod = BuiltinTypes.isString(recvType)
            ? StringMethodRegistry.stringRuntimeMethod(mc.methodName()) : null;
    // receiver de classe EXTERNA sem símbolo resolvido: última
    // linha de defesa — assinatura vem do classpath, senão o
    // descritor sairia errado (owner vazio / retorno Object)
    if (resolvedMethod == null && runtimeMethod == null
            && mc.receiver() != null && driver.currentDiagnostics != null) {
        Type rt2 = driver.semanticAnalyzer != null
                ? driver.semanticAnalyzer.getExpressionType(mc.receiver())
                : Type.UnknownType.UNKNOWN;
        if (!(rt2 instanceof Type.ClassType)) {
            rt2 = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
        }
        if (rt2 instanceof Type.ClassType ct2 && !ct2.packageName().isEmpty()
                && driver.externalClasspath != null
                && driver.externalClasspath.knows(ct2.internalName())) {
            ExternalClasspath.MethodSignature sig = driver.externalClasspath.resolveMethod(
                    ct2.internalName(), mc.methodName(), mc.arguments().size());
            if (sig != null) {
                List<Type> formal = new ArrayList<>();
                for (String d : sig.parameterDescriptors()) {
                    formal.add(ExternalClasspath.typeFromDescriptor(d));
                }
                recvType = ct2;
                methodParamTypes = formal;
                methodReturnType = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
            }
        }
    }
    // String.valueOf/Integer.valueOf/…: receiver é o NOME de
    // um tipo builtin (não uma variável) — o identificador não
    // empilha valor; mapeia para o owner JDK estático (sem
    // isso o emit saía com owner "" → ClassFormatError)
    if (recvType instanceof Type.UnknownType
            && mc.receiver() instanceof IdentifierExpr brid
            && driver.findLocalVar(brid.name(), locals) == null
            && !brid.name().isEmpty()
            && Character.isUpperCase(brid.name().charAt(0))) {
        Type jdkOwner = switch (brid.name()) {
            case "String" -> BuiltinTypes.STRING;
            case "Int", "Integer" -> new Type.ClassType("java.lang", "Integer", List.of());
            case "Long" -> new Type.ClassType("java.lang", "Long", List.of());
            case "Float" -> new Type.ClassType("java.lang", "Float", List.of());
            case "Double" -> new Type.ClassType("java.lang", "Double", List.of());
            case "Bool", "Boolean" -> new Type.ClassType("java.lang", "Boolean", List.of());
            default -> Type.UnknownType.UNKNOWN;
        };
        if (!(jdkOwner instanceof Type.UnknownType)) {
            recvType = jdkOwner;
            callKind = KofCallKind.STATIC;
            if (methodParamTypes.size() == 1
                    && methodParamTypes.get(0) instanceof Type.PrimitiveType) {
                // valueOf(I) direto do JDK — sem boxing duplo
                methodReturnType = BuiltinTypes.STRING;
            }
        }
    }
    ops.add(new KofCall(recvType,
            runtimeMethod != null ? runtimeMethod : mc.methodName(),
            methodParamTypes, methodReturnType, callKind));
    if (methodReturnType instanceof Type.TypeVariable) {
        Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
        if (TypeMetrics.isPrimitiveType(effective)) {
            driver.emitErasureUnbox(ops, effective);
        }
    }
        return localIdx;
    }

        private static Type recvType0(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (mc.receiver() == null) return Type.UnknownType.UNKNOWN;
        return ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    }
}