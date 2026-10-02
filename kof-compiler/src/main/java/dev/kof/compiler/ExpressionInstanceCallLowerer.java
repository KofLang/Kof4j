package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do dispatch de instância do MethodCallExpr (receiver != null):
 * chamadas super, recepção em Types Ui/Web/Media/Io/FunctionType/List/coleções
 * e o fallback para método resolvido/interface/runtime.
 */
public final class ExpressionInstanceCallLowerer {

    private ExpressionInstanceCallLowerer() {}

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
        if (superMethod == null && osig == null) {
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
    int beforeRecvOps = ops.size();
    localIdx = ExpressionLowerer.emitExpression(driver, mc.receiver(), ops, owner, localIdx, locals);
    Type recvType = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    // narrowing de null-safety (`if (x != null) { x.substring(...) }`):
    // dispatch pelo inner — antes emitia `"".substring` (owner "" inválido)
    if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
    // #375/§355 (rio da erasure): method call em type-variable COM bound
    // (`item.shout()` com `item: T: Animal`) — dispatch pelo BOUND, como
    // javac pós-erasure; sem bound o dono sai por erasure no emit (§355,
    // JvmOpEmitter → Object). Sem isto o owner saía `""` (ClassFormatError).
    if (recvType instanceof Type.TypeVariable tvb && tvb.bound() != null) {
        recvType = tvb.bound();
    }
    // §358 (21/09): receiver type-parameter SEM bound em alvo nativo não tem
    // dispatch de instância no runtime cross → recusa honesta NAT004 (o gate
    // extraído substitui o `call <método>` nu que dava link-fail críptico).
    if (NativeGenericDispatchGate.refuse(driver, mc, recvType)) {
        return localIdx;
    }
    if (KofUi.isUiType(recvType)) {
        localIdx = driver.emitUiInstance(recvType, mc, ops, owner, localIdx, locals);
        return localIdx;
    }
    int enumHandled = ExpressionBuiltinInstanceCalls.lowerEnum(driver, mc, ops, owner, localIdx, locals, recvType);
    if (enumHandled >= 0) {
        return enumHandled;
    }
    if (KofNet.isNetHandleType(recvType)) {
        return ExpressionBuiltinInstanceCalls.lowerNet(driver, mc, ops, owner, localIdx, locals, recvType);
    }
    if (KofWeb.isAppType(recvType)) {
        return ExpressionBuiltinInstanceCalls.lowerWeb(driver, mc, ops, owner, localIdx, locals, recvType);
    }
    if (KofMedia.isHandleType(recvType)) {
        return ExpressionBuiltinInstanceCalls.lowerMedia(driver, mc, ops, owner, localIdx, locals, recvType);
    }
    if (KofBuffer.isBufferType(recvType)) {
        return ExpressionBuiltinInstanceCalls.lowerBuffer(driver, mc, ops, owner, localIdx, locals, recvType);
    }
    if (KofSecurity.isSecretType(recvType) || KofSecurity.isKeyHandleType(recvType)) {
        return ExpressionBuiltinInstanceCalls.lowerSecret(driver, mc, ops, owner, localIdx, locals, recvType);
    }
    if (KofIo.isIoType(recvType)) {
        return ExpressionBuiltinInstanceCalls.lowerIo(driver, mc, ops, owner, localIdx, locals, recvType);
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
            || BuiltinTypes.isMap(recvType) || BuiltinTypes.isSet(recvType)
            || KofProcess.isHandle(recvType)) {
        // KofProcess.isHandle: F10 handle ops (write/readLine/exitCode/kill/
        // alive) live in CollectionCallLowerer's isHandle branch — without this
        // routing they leaked to a raw invokevirtual java.lang.Long.readLine
        // (NoSuchMethodError at run; never executed until ProcessSpawnE2ETest).
        int handled = CollectionCallLowerer.lower(driver, recvType, mc, ops, owner, localIdx, locals);
        if (handled >= 0) return handled;
    }
    Type methodReturnType = Type.UnknownType.UNKNOWN;
    List<Type> methodParamTypes = new ArrayList<>();
    for (ExpressionNode arg : mc.arguments()) {
        methodParamTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
    }
    SymbolTable.MethodSymbol resolvedMethod = driver.semanticAnalyzer != null
            ? driver.semanticAnalyzer.getResolvedMethod(mc) : null;
    if (resolvedMethod != null) {
        // #639 face 2 (D-DECISION-BATCH-2709B): o owner do método é o NOME
        // SIMPLES; com duas packages de mesmo nome, re-derivar o pacote aqui
        // caía no last-write (nome simples) e emitia invokevirtual do owner
        // ERRADO (VerifyError). Quando o receiver JÁ carrega o pacote e o
        // owner é um nome simples de mesmo nome, preserva o pacote do receiver
        // (sem colisão o resultado é idêntico).
        String oc = resolvedMethod.ownerClass();
        boolean simpleOwner = oc != null && !oc.contains("/");
        boolean recvPackagedSame = recvType instanceof Type.ClassType rct
                && rct.packageName() != null && !rct.packageName().isEmpty()
                && rct.name().equals(oc);
        if (!(simpleOwner && recvPackagedSame)) {
            recvType = CompilerTypes.ownerTypeFromInternal(oc, driver.semanticAnalyzer);
        }
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
        // §424: five String methods have no JS/Native lowering — refuse with an
        // honest STR003 instead of a JS TypeError / Native ld link-fail (R6).
        if (StringTargetGaps.refuse(driver, mc)) {
            return localIdx;
        }
        // Guardas de diagnóstico em receptor String (SEM052/SEM066/SEM051) —
        // extraídos p/ StringReceiverGuards (gate ≤600 REFACTOR-500; são puros:
        // só emitem diagnostics, não mexem na pilha/ops).
        StringReceiverGuards.check(driver, mc, locals, methodParamTypes);
        StringMethodRegistry.Sig sig = StringMethodRegistry.stringMethodSignature(mc.methodName(), mc.arguments().size(),
                methodParamTypes);
        if (sig != null) {
            methodReturnType = sig.returnType();
            methodParamTypes = new ArrayList<>(sig.parameterTypes());
        }
    } else if ((TypeMetrics.isPrimitiveType(recvType) || recvType instanceof Type.UnknownType)
            && mc.arguments().isEmpty()
            && ("toInt".equals(mc.methodName()) || "toLong".equals(mc.methodName())
                || "toFloat".equals(mc.methodName()) || "toDouble".equals(mc.methodName()))) {
        // §89 (decisão 3a, 13/09): conversão numérica em receiver PRIMITIVO =
        // alias do `as` (mesma superfície do corpus: learn/04, cast.kf exit 0
        // nos 3 nativos). Antes emitia KofCall owner vazio → ClassFormatError
        // no JVM compilado / undefined reference nos nativos (só o
        // interpretador implementava). WARNING (não erro) quando a conversão
        // pode truncar (Double/Float -> Int/Long) apontando o `as`.
        Type target = switch (mc.methodName()) {
            case "toInt" -> Type.PrimitiveType.INT;
            case "toLong" -> Type.PrimitiveType.LONG;
            case "toFloat" -> Type.PrimitiveType.FLOAT;
            default -> Type.PrimitiveType.DOUBLE;
        };
        String fn0 = TypeMetrics.primitiveName(recvType);
        boolean mayTruncate = ("toInt".equals(mc.methodName()) || "toLong".equals(mc.methodName()))
                && (fn0.isEmpty() || "double".equals(fn0) || "Double".equals(fn0)
                    || "float".equals(fn0) || "Float".equals(fn0));
        if (mayTruncate && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.warning("", 0, 0, 0,
                    "'" + mc.methodName() + "()' may truncate (fractional part discarded; "
                        + "overflow throws) — explicit form: value as "
                        + TypeMetrics.primitiveName(target), "SEM090");
        }
        driver.emitWideningIfNeeded(ops, recvType, target);
        if (target instanceof Type.PrimitiveType tp
                && ("char".equals(tp.name()) || "Char".equals(tp.name()))) {
            ops.add(new KofUnary(KofUnaryOp.I2C, recvType));
        }
        driver.emitPrimNarrow(ops, recvType, target);
        return localIdx;
    } else if (TypeMetrics.isPrimitiveType(recvType) && "toString".equals(mc.methodName())
            && mc.arguments().isEmpty()) {
        // primitivo.toString(): o primitivo não tem classe —
        // boxar e converter (String.valueOf) em vez de gerar
        // um owner vazio no bytecode (ClassFormatError)
        // §216/#153 (face 1 — triagem da mantenedora 14/09: "`Char.toString()`
        // NÃO é documentado numérico; é bug de paridade"): o `char` vai pelo
        // overload `String.valueOf(char)` (descritor `(C)`) com o receiver já
        // empilhado — `Character.toString`/`valueOf(I)` dariam o code point
        // ("65"). As OUTRAS stringificações de char (println/concat) continuam
        // numéricas — contrato CONGELADO (training/language/strings.md:25
        // "println(s.charAt(0)) // 72"); e o box de char em COLEÇÃO continua
        // Integer (JvmOpCollections) — não tocar daqui.
        if ("char".equals(Type.canonicalPrimitiveName(((Type.PrimitiveType) recvType).name()))) {
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.PrimitiveType.CHAR), BuiltinTypes.STRING, KofCallKind.STATIC));
            return localIdx;
        }
        if (driver.target == Target.JS && TypeMetrics.isFloatingPoint(recvType)) {
            // §264 (JS): Double/Float crus (Number no JS) — valueOf recebe o
            // tipo REAL p/ o emissor formatar no contrato do JDK ("4.0").
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(recvType), BuiltinTypes.STRING, KofCallKind.STATIC));
            return localIdx;
        }
        TypeEmitter.boxPrimitive(ops, recvType);
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        return localIdx;
    } else if (TypeMetrics.isPrimitiveType(recvType)
            && PrimitiveNumericFormatters.isFormatter(mc.methodName(), mc.arguments().size())) {
        // §218/#148: formatadores numéricos de primitivo → estático JDK real
        // (ver PrimitiveNumericFormatters). Int/Long emitem a chamada; os
        // demais primitivos recebem SEM052 e encerram o caminho aqui.
        PrimitiveNumericFormatters.emit(driver, mc, recvType, ops);
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
    if (callKind == KofCallKind.INSTANCE && resolvedMethod != null && driver.semanticAnalyzer != null) {
        if ((resolvedMethod.accessFlags() & AccessFlags.STATIC) != 0) {
            callKind = KofCallKind.STATIC;
        } else if (resolvedMethod.dispatchKind() == SymbolTable.DispatchKind.INTERFACE) {
            // §557: o typer já carimbou INTERFACE (interface externa/JDK).
            callKind = KofCallKind.INTERFACE;
        } else {
            String ownerName = resolvedMethod.ownerClass();
            if (ownerName.contains("/")) ownerName = ownerName.substring(ownerName.lastIndexOf('/') + 1);
            if (driver.semanticAnalyzer.isInterfaceType(ownerName)) {
                callKind = KofCallKind.INTERFACE;
            } else if (driver.externalClasspath != null
                    && driver.externalClasspath.isInterface(resolvedMethod.ownerClass())) {
                // §557: interface EXTERNA/JDK — interfaceNames é só Kof-local.
                callKind = KofCallKind.INTERFACE;
            }
        }
    }
    if (callKind == KofCallKind.INSTANCE && recvType instanceof Type.ClassType rt) {
        if (driver.semanticAnalyzer != null && driver.semanticAnalyzer.isInterfaceType(rt.name())) {
            callKind = KofCallKind.INTERFACE;
        } else if (driver.externalClasspath != null
                && driver.externalClasspath.isInterface(rt.internalName())) {
            // §557: o RECEPTOR é uma interface externa/JDK (o sintoma medido:
            // `PublicKey pub = ...; pub.getEncoded()`).
            callKind = KofCallKind.INTERFACE;
        }
    }
    if (callKind == KofCallKind.STATIC) {
        // Para método estático chamado em receiver (u.square(4)), o valor do
        // receiver avaliado na pilha antes dos argumentos precisa ser descartado
        // (POP) antes da chamada INVOKESTATIC, para manter o stack balance.
        // A inserção do POP ocorre antes de empilhar os argumentos.
        ops.add(beforeRecvOps + (ops.size() - beforeRecvOps - mc.arguments().size()), new KofPop());
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
                && driver.externalClasspath.knows(ct2.internalName())) {
            List<Type> actualArgTypes = new ArrayList<>();
            for (ExpressionNode arg : mc.arguments()) {
                actualArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
            }
            ExternalClasspath.MethodSignature sig = driver.externalClasspath.resolveMethodWithArgs(
                    ct2.internalName(), mc.methodName(), mc.arguments().size(), actualArgTypes);
            if (sig != null) {
                List<Type> formal = new ArrayList<>();
                for (String d : sig.parameterDescriptors()) {
                    formal.add(ExternalClasspath.typeFromDescriptor(d));
                }
                recvType = ct2;
                methodParamTypes = formal;
                methodReturnType = ExternalClasspath.typeFromDescriptor(sig.returnDescriptor());
                if (sig.ownerIsInterface()) {
                    // §557: mesma flag do ramo com símbolo resolvido — sem
                    // isto o conserto seria meio-aplicado.
                    callKind = KofCallKind.INTERFACE;
                }
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
            if (jdkOwner instanceof Type.ClassType jct
                    && driver.externalClasspath.knows(jct.internalName())) {
                ExternalClasspath.MethodSignature extSig = driver.externalClasspath.resolveMethod(
                        jct.internalName(), mc.methodName(), mc.arguments().size());
                if (extSig != null) {
                    methodReturnType = ExternalClasspath.typeFromDescriptor(extSig.returnDescriptor());
                    List<Type> formal = new ArrayList<>();
                    for (String d : extSig.parameterDescriptors()) {
                        formal.add(ExternalClasspath.typeFromDescriptor(d));
                    }
                    methodParamTypes = formal;
                } else if ("valueOf".equals(mc.methodName()) && methodParamTypes.size() == 1
                        && methodParamTypes.get(0) instanceof Type.PrimitiveType) {
                    // valueOf(I) direto do JDK — sem boxing duplo
                    methodReturnType = BuiltinTypes.STRING;
                } else if (methodParamTypes.size() == 1
                        && switch (mc.methodName()) {
                            case "isNaN", "isInfinite", "isFinite" -> true;
                            default -> false;
                        }
                        && jdkOwner instanceof Type.ClassType fpOwner
                        && ("Double".equals(fpOwner.name()) || "Float".equals(fpOwner.name()))) {
                    // #233: Double.isNaN(d)/isInfinite/isFinite (e Float) —
                    // estáticos JDK reais, retorno BOOL (não String/Unknown)
                    methodReturnType = Type.PrimitiveType.BOOL;
                }
            } else if (mc.arguments().size() == 1
                    && ("isNaN".equals(mc.methodName()) || "isInfinite".equals(mc.methodName()) || "isFinite".equals(mc.methodName()))
                    && ("Double".equals(brid.name()) || "Float".equals(brid.name()))) {
                methodReturnType = Type.PrimitiveType.BOOL;
                methodParamTypes = List.of("Double".equals(brid.name()) ? Type.PrimitiveType.DOUBLE : Type.PrimitiveType.FLOAT);
            } else if ("valueOf".equals(mc.methodName()) && methodParamTypes.size() == 1
                    && methodParamTypes.get(0) instanceof Type.PrimitiveType) {
                // valueOf(I) direto do JDK — sem boxing duplo
                methodReturnType = BuiltinTypes.STRING;
            }
        }
    }
    ops.add(new KofCall(recvType,
            runtimeMethod != null ? runtimeMethod : mc.methodName(),
            methodParamTypes, methodReturnType, callKind));
    GenericReturnAdapter.emit(driver, mc, ops, locals, methodReturnType);
        return localIdx;
    }

        private static Type recvType0(CompilerDriver driver, MethodCallExpr mc, List<IRLocalVariable> locals) {
        if (mc.receiver() == null) return Type.UnknownType.UNKNOWN;
        return ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
    }
}