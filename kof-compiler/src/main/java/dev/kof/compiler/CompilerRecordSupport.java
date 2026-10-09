package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Geração de métodos sintéticos para records: toString, equals, construtor.
 */
public final class CompilerRecordSupport {

    private CompilerRecordSupport() {}

    static IRMethod buildRecordToStringMethod(CompilerDriver driver, String internalName,
                                RecordDeclarationNode rec,
                                               List<IRField> fields, List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        String simpleName = internalName.contains("/")
                ? internalName.substring(internalName.lastIndexOf('/') + 1) : internalName;
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        // #773: no native o `valueOf` de um literal String é no-op (String já
        // é o valor) e o de um campo despacha pelo tipo REAL — passar Unknown
        // fazia o emissor nativo chamar `kof_box_to_string`, o que puxava a
        // fatia §284 inteira (box/unbox) para o runtime de QUALQUER programa
        // (o record `Pair` do zip é injetado flat em todos): o hello podado
        // voltava de <110 p/ 126 syms. JVM/JS/Script mantêm o Unknown (o JVM
        // precisa do Object do `String.valueOf(Object)`).
        boolean nativeT = driver.target.isNative();
        Type literalValueOfArg = nativeT ? BuiltinTypes.STRING : Type.UnknownType.UNKNOWN;
        // "Nome[x=valor, y=valor]" — concat: literal, campo, separador...
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, simpleName + "["));
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(literalValueOfArg), BuiltinTypes.STRING, KofCallKind.STATIC));
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f.name() + "="));
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(literalValueOfArg), BuiltinTypes.STRING, KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            if (nativeT) {
                // Campo genérico/apagado (T/Unknown): o valor é uma referência
                // crua; o `valueOf` de um String é no-op e o concat a trata
                // como String (comportamento pré-§612, sem puxar a fatia de
                // box). Campo concreto despacha pelo tipo REAL (Int→int_to_string,
                // record→vtable).
                // Record injetado FLAT e sem uso (o `Pair` do zip em todo
                // programa, #773): campo `T` apagado passa como String (no-op)
                // — não puxa `kof_box_to_string`/fatia §284. O `Pair<Int,Int>`
                // já é recusado no native (NAT004), então nada imprimível
                // regride; o ganho é o hello voltar a <110 syms (S-3).
                Type fv = f.type();
                boolean flatInjected = driver.flatInjectedRecordTypes.contains(simpleName);
                Type fieldValueOfArg = (fv instanceof Type.TypeVariable
                        || fv instanceof Type.UnknownType || Type.isString(fv))
                        ? (flatInjected ? BuiltinTypes.STRING : fv)
                        : fv;
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(fieldValueOfArg), BuiltinTypes.STRING, KofCallKind.STATIC));
            } else {
                if (!Type.isString(f.type())) TypeEmitter.boxPrimitive(ops, f.type());
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
            }
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            if (i + 1 < fields.size()) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ", "));
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(literalValueOfArg), BuiltinTypes.STRING, KofCallKind.STATIC));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
        }
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "]"));
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(literalValueOfArg), BuiltinTypes.STRING, KofCallKind.STATIC));
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
        ops.add(new KofReturn(BuiltinTypes.STRING));
        return new IRMethod("toString", BuiltinTypes.STRING, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * equals() nativo de record: compara todos os componentes (bug 11 native).
     * §114: campo String → CONTEÚDO via kof_string_equals (null-safe, o mesmo
     * helper que o top-level `s == t` usa — FUNCTION, roteado nos 3 backends
     * nativos); campo de classe/record aninhado continua EQ de ponteiro até a
     * armadura genérica de equals-vtable-do-campo do §104b-ii (unidade própria).
     */
    static IRMethod buildRecordEqualsMethod(CompilerDriver driver, String internalName,
                            List<IRField> fields,
                                             List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        locals.add(new IRLocalVariable(1, "other", ownerType));
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            ops.add(new KofLoadLocal(ownerType, 1));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            if (isStringField(f.type())) {
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
            } else if (isKofObjectField(f.type())) {
                // §114 face aninhada: campo record/classe compara por CONTEÚDO
                // (kof_obj_equals: null-safe, String por conteúdo, senão
                // despacho na kof_equals_table — identidade p/ classe). Antes o
                // EQ de ponteiro fazia Outer(Inner(1),"z") != Outer(Inner(1),"z").
                Type objectType = new Type.ClassType("java.lang", "Object", List.of());
                ops.add(new KofCall(objectType, "kof_obj_equals",
                        List.of(objectType, objectType),
                        Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
            } else {
                ops.add(new KofBinary(KofBinaryOp.EQ, f.type()));
            }
            // AND acumula a partir do 2º campo: [bool0] → (bool0 AND bool1)
            // O AND só após a 2ª comparação ter empilhado o 2º bool.
            if (i > 0) {
                ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.BOOL));
            }
        }
        if (fields.isEmpty()) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        }
        ops.add(new KofReturn(Type.PrimitiveType.BOOL));
        return new IRMethod("equals", Type.PrimitiveType.BOOL, List.of(ownerType), AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * §114: campo de record que é REFERÊNCIA Kof (classe/record) compara por
     * conteúdo via `kof_obj_equals`. Ficam de fora String (já usa
     * `kof_string_equals`), primitivos, arrays e type-vars (identidade — o
     * mesmo contrato de `Objects.equals` no JVM, que não desce em arrays nem
     * em `Object` cru) e `Object` (pode ser box de primitivo, sem `type_id` no
     * offset 0).
     */
    private static boolean isStringField(Type type) {
        Type t = type instanceof Type.NullableType n ? n.inner() : type;
        return Type.isString(t);
    }

    private static boolean isDoubleField(Type type) {
        Type t = type instanceof Type.NullableType n ? n.inner() : type;
        return t instanceof Type.PrimitiveType pt && "double".equals(pt.name());
    }

    private static boolean isKofObjectField(Type type) {
        Type t = type instanceof Type.NullableType n ? n.inner() : type;
        if (!(t instanceof Type.ClassType)) return false;
        if (Type.isString(t)) return false;
        return !BuiltinTypes.isObject(t);
    }

    /**
     * equals() de classe NÃO-record no Native: identidade de referência
     * (this == other), o MESMO contrato do Object.equals herdado no JVM
     * (Thing(5).equals(Thing(5)) = false — oracle 11/09). Sem este método o
     * backend emitia `call Thing_equals` sem símbolo (LINK_FAIL) em código
     * válido (bug 104b-i). A comparação EQ de ponteiro no Native é a mesma
     * que `t1 == t2` (já provada correta). hashCode/toString de classe
     * não-record ficam em §104b-ii.
     */
    static IRMethod buildClassIdentityEqualsMethod(CompilerDriver driver, String internalName) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        locals.add(new IRLocalVariable(1, "other", ownerType));
        ops.add(new KofLoadLocal(ownerType, 0));
        ops.add(new KofLoadLocal(ownerType, 1));
        ops.add(new KofBinary(KofBinaryOp.EQ, ownerType));
        ops.add(new KofReturn(Type.PrimitiveType.BOOL));
        return new IRMethod("equals", Type.PrimitiveType.BOOL, List.of(ownerType), AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * hashCode() nativo de record: 31 * h + campo (bug 42 native). §114 (face
     * hash): campo de REFERENCIA não pode somar o PONTEIRO — o contrato é o
     * JVM (`31*h + o.hashCode()`, `o==null → 0`). String por CONTEÚDO via
     * `kof_string_hash_code`; record/classe via `kof_obj_hash` (despacho
     * `kof_hashcode_table`); Double via `kof_double_hash` (`(int)(bits^(bits>>>32))`).
     * Int/Long/Bool/Char casam crus; Float casa cru (o slot de 32 bits é
     * `floatToIntBits`). Medido: todas as faces fechadas (§114).
     */
    static IRMethod buildRecordHashCodeMethod(CompilerDriver driver, String internalName,
                                              List<IRField> fields,
                                              List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        for (IRField f : fields) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 31));
            ops.add(new KofBinary(KofBinaryOp.MUL, Type.PrimitiveType.INT));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            if (isStringField(f.type())) {
                ops.add(new KofCall(BuiltinTypes.STRING, "hashCode", List.of(),
                        Type.PrimitiveType.INT, KofCallKind.INSTANCE));
            } else if (isDoubleField(f.type())) {
                // §114 face hash: Double.hashCode = (int)(bits ^ (bits>>>32)).
                // O ADD cru somava 0 (o lowering int não lê o slot de 64 bits).
                ops.add(new KofCall(Type.PrimitiveType.DOUBLE, "kof_double_hash",
                        List.of(Type.PrimitiveType.DOUBLE),
                        Type.PrimitiveType.INT, KofCallKind.FUNCTION));
            } else if (isKofObjectField(f.type())) {
                // §114 face hash aninhada: campo record/classe soma o hashCode
                // de CONTEUDO (kof_obj_hash: null-safe, String por conteúdo,
                // senão kof_hashcode_table[type_id]); antes somava o PONTEIRO.
                Type objectType = new Type.ClassType("java.lang", "Object", List.of());
                ops.add(new KofCall(objectType, "kof_obj_hash",
                        List.of(objectType),
                        Type.PrimitiveType.INT, KofCallKind.FUNCTION));
            }
            ops.add(new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
        }
        ops.add(new KofReturn(Type.PrimitiveType.INT));
        return new IRMethod("hashCode", Type.PrimitiveType.INT, List.of(), AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, ops)), locals);
    }

    static IRMethod generateRecordConstructor(CompilerDriver driver, RecordDeclarationNode rec,
                                  String owner) {
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        List<Type> compTypes = rec.components().stream().map(c -> CompilerTypes.resolveWithTypeParams(c.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer)).toList();
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        Type superType = rec.superClass() != null
                ? CompilerTypes.ownerTypeFromInternal(driver.toInternalName("", rec.superClass()), driver.semanticAnalyzer)
                : new Type.ClassType("java.lang", "Record", List.of());
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (driver.isJvmTarget()) {
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        int localIdx = 1;
        for (RecordComponentNode comp : rec.components()) {
            Type compType = CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
            locals.add(new IRLocalVariable(localIdx, comp.name(), compType));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadLocal(compType, localIdx));
            ops.add(new KofStoreField(ownerType, comp.name(), compType));
            localIdx += TypeMetrics.isDoubleWidth(compType) ? 2 : 1;
        }
        for (AstNode member : rec.members()) {
            if (member instanceof FieldDeclarationNode field && field.initializer() != null
                    && !field.modifiers().contains("static")) {
                Type fieldType = CompilerTypes.resolveWithTypeParams(field.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
                ops.add(new KofLoadLocal(ownerType, 0));
                localIdx = ExpressionLowerer.emitExpression(driver, field.initializer(), ops, owner, localIdx, locals);
                ops.add(new KofStoreField(ownerType, field.name(), fieldType));
            }
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, compTypes, AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    static List<IRMethod> generateRecordDefaultOverloads(CompilerDriver driver,
                                            RecordDeclarationNode rec,
                                            String owner) {
        List<IRMethod> overloads = new ArrayList<>();
        int n = rec.components().size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (rec.components().get(i).initializer() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return overloads;
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        List<Type> canonicalTypes = rec.components().stream().map(c -> CompilerTypes.toType(c.type(), driver.currentUnit)).toList();
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = new ArrayList<>();
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                Type t = CompilerTypes.toType(rec.components().get(i).type(), driver.currentUnit);
                paramTypes.add(t);
                locals.add(new IRLocalVariable(localIdx, rec.components().get(i).name(), t));
                ops.add(new KofLoadLocal(t, localIdx));
                localIdx += TypeMetrics.isDoubleWidth(t) ? 2 : 1;
            }
            for (int i = paramCount; i < n; i++) {
                ExpressionNode init = rec.components().get(i).initializer();
                if (init != null) {
                    localIdx = ExpressionLowerer.emitExpression(driver, init, ops, owner, localIdx, locals);
                }
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            IRMethod m = new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, AccessFlags.PUBLIC,
                    List.of(), List.of(new IRBasicBlock(0, ops)), locals);
            overloads.add(m);
        }
        return overloads;
    }

    /**
     * Issue #248: Gera métodos bridge sintéticos para métodos sobrescritos com
     * tipo de retorno covariante (subtipo do retorno da superclasse).
     * O bridge possui o descritor da superclasse e delega ao método covariante.
     *
     * <p>§356 (rio da erasure — #385/#366/#365): os pais agora incluem as
     * INTERFACES implementadas (uma classe que implementa `Wrapper<String>`
     * precisa do bridge `get()Ljava/lang/Object;` da interface APAGADA, senão
     * o invokeinterface não acha o método → NoSuchMethodError), os argumentos
     * casam por ERASURE (não por igualdade crua — `set(item: T)` apagado vs
     * `set(String)` concreto é o mesmo slot erased), e o corpo atravessa a
     * fronteira apagada: checkcast/unbox nos argumentos, box no retorno
     * primitivo (#365: bridge `Object getValue()` que fazia `areturn` sobre
     * `int` cru → VerifyError).
     */
    static List<IRMethod> generateCovariantReturnBridges(CompilerDriver driver, String internalName,
                                                         String superName, List<String> ifaces,
                                                         List<IRMethod> methods) {
        if (driver.semanticAnalyzer == null) {
            return List.of();
        }
        List<String> parents = new ArrayList<>();
        if (superName != null && !superName.isEmpty() && !"java/lang/Object".equals(superName)) {
            parents.add(superName);
        }
        if (ifaces != null) parents.addAll(ifaces);
        if (parents.isEmpty()) return List.of();

        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        List<IRMethod> bridges = new ArrayList<>();

        for (String parent : parents) {
        String superSimple = parent.contains("/")
                ? parent.substring(parent.lastIndexOf('/') + 1) : parent;
        SymbolTable.ClassSymbol superSym = driver.semanticAnalyzer.getClass(superSimple);
        if (superSym == null) continue;

        for (IRMethod m : methods) {
            if ("<init>".equals(m.name()) || "<clinit>".equals(m.name())
                    || (m.accessFlags() & AccessFlags.STATIC) != 0
                    || (m.accessFlags() & AccessFlags.PRIVATE) != 0) {
                continue;
            }
            SymbolTable.Symbol superMember = MemberResolver.resolveInHierarchy(driver.semanticAnalyzer,
                    superSimple, m.name());
            List<SymbolTable.MethodSymbol> candidates = new ArrayList<>();
            if (superMember instanceof SymbolTable.MethodSymbol ms) {
                candidates.add(ms);
            } else if (superMember instanceof SymbolTable.MethodSet set) {
                candidates.addAll(set.methods());
            }

            for (SymbolTable.MethodSymbol parentMethod : candidates) {
                if ((parentMethod.accessFlags() & AccessFlags.STATIC) != 0
                        || (parentMethod.accessFlags() & AccessFlags.PRIVATE) != 0) {
                    continue;
                }
                if (parentMethod.parameterTypes().size() != m.parameterTypes().size()) {
                    continue;
                }
                // §356: casa por ERASURE (T→Object/bound), não por igualdade
                // crua — o bridge JVM existe para o mesmo SLOT apagado.
                // Quando o parâmetro do PAI é um type-variable (ou aninha um,
                // ex. `T[]`) e o filho tem o tipo concreto, o slot apagado é o
                // MESMO na interface/abstrato — a fronteira faz unbox (primitivo)
                // ou checkcast (referência) abaixo. Sem isto, `set(v: T)` vs
                // `set(v: Int)` ficava sem bridge → AbstractMethodError (slot
                // (Ljava/lang/Object;)V nunca implementado).
                boolean paramsErasureMatch = true;
                boolean paramsDiffer = false;
                for (int i = 0; i < m.parameterTypes().size(); i++) {
                    if (!erasureKey(m.parameterTypes().get(i)).equals(
                            erasureKey(parentMethod.parameterTypes().get(i)))) {
                        if (!containsTypeVar(parentMethod.parameterTypes().get(i))) {
                            paramsErasureMatch = false;
                            break;
                        }
                        paramsDiffer = true;
                    } else if (!m.parameterTypes().get(i).equals(parentMethod.parameterTypes().get(i))) {
                        paramsDiffer = true;
                    }
                }
                if (!paramsErasureMatch) continue;

                Type parentRet = parentMethod.returnType();
                Type childRet = m.returnType();
                boolean retDiffer = !childRet.equals(parentRet);

                // §485 — no Native, um bridge de retorno covariante cujos
                // PARÂMETROS já são idênticos ao slot apagado do pai (sem
                // box/unbox/checkcast de argumento) e cujo retorno é uma
                // REFERÊNCIA nas duas pontas é um pass-through de registrador:
                // a vtable pode apontar direto para o método concreto. Gerar o
                // bridge aqui só produzia dois símbolos asm idênticos
                // (`Classe_nome`), porque `NativeSymbolMangling.sigTag` codifica
                // apenas os TIPOS DE PARÂMETRO — o `as` falhava com "symbol
                // already defined" (JVM/script/JS verdes; divergência rule 5).
                // Retornos primitivos NÃO entram (o bridge faz o box e é
                // REALMENTE necessário) — face catalogada no §485.
                boolean childPrim = childRet instanceof Type.PrimitiveType;
                boolean parentPrim = parentRet instanceof Type.PrimitiveType;
                if (driver.target.isNative() && !paramsDiffer && !childPrim && !parentPrim) {
                    continue;
                }

                if ((retDiffer || paramsDiffer)
                        && TypeChecker.isAssignable(driver.semanticAnalyzer, childRet, parentRet)) {
                    // Já existe um método na classe com a MESMA assinatura
                    // apagada do pai? (chave por erasure — §356: um segundo
                    // bridge para o mesmo slot JVM = ClassFormatError)
                    boolean alreadyExists = methods.stream().anyMatch(existing ->
                            existing != m
                            && existing.name().equals(m.name())
                            && erasureKey(existing.returnType()).equals(erasureKey(parentRet))
                            && existing.parameterTypes().size() == parentMethod.parameterTypes().size()
                            && java.util.stream.IntStream.range(0, existing.parameterTypes().size())
                                    .allMatch(i -> erasureKey(existing.parameterTypes().get(i))
                                            .equals(erasureKey(parentMethod.parameterTypes().get(i)))));
                    if (alreadyExists) continue;

                    List<KofOperation> ops = new ArrayList<>();
                    List<IRLocalVariable> locals = new ArrayList<>();
                    locals.add(new IRLocalVariable(0, "this", ownerType));
                    ops.add(new KofLoadLocal(ownerType, 0));

                    int localIdx = 1;
                    for (int i = 0; i < m.parameterTypes().size(); i++) {
                        Type bridgePt = parentMethod.parameterTypes().get(i);
                        Type childPt = m.parameterTypes().get(i);
                        locals.add(new IRLocalVariable(localIdx, "arg" + i, bridgePt));
                        ops.add(new KofLoadLocal(bridgePt, localIdx));
                        // fronteira apagada: o slot do bridge tem a erasure
                        // (Object); o método concreto quer o tipo real.
                        if (!childPt.equals(bridgePt)) {
                            if (TypeMetrics.isPrimitiveType(childPt)) {
                                driver.emitErasureUnbox(ops, childPt);
                            } else if (childPt instanceof Type.ClassType
                                    || childPt instanceof Type.ArrayType) {
                                ops.add(new KofCheckCast(childPt));
                            }
                        }
                        localIdx += TypeMetrics.isDoubleWidth(bridgePt) ? 2 : 1;
                    }

                    ops.add(new KofCall(ownerType, m.name(), m.parameterTypes(), childRet, KofCallKind.INSTANCE));
                    if (Type.isVoid(parentRet)) {
                        ops.add(new KofReturnVoid());
                    } else {
                        if (TypeMetrics.isPrimitiveType(childRet)
                                && !TypeMetrics.isPrimitiveType(parentRet)) {
                            // #365: retorno primitivo atravessando o bridge
                            // apagado (Object) — box na fronteira, senão
                            // `areturn` sobre `int` → VerifyError.
                            driver.emitErasureBox(ops, childRet);
                        }
                        ops.add(new KofReturn(parentRet));
                    }

                    int bridgeFlags = AccessFlags.PUBLIC | AccessFlags.BRIDGE | AccessFlags.SYNTHETIC;
                    bridges.add(new IRMethod(m.name(), parentRet, parentMethod.parameterTypes(),
                            bridgeFlags, List.of(), List.of(new IRBasicBlock(0, ops)), locals));
                }
            }
        }
        }
        return bridges;
    }

    /**
     * §356 — chave de comparacao de descritor APAGADO para o par pai×filho do
     * bridge: primitivo fica primitivo, TypeVariable cai para o bound/Object,
     * args de classe caem para o nome qualificado (type-arguments irrelevantes
     — `List<Int>` × `List<String>` ocupam o mesmo slot java/util/List).
     */
    static String erasureKey(Type t) {
        if (t == null) return "?";
        if (t instanceof Type.NullableType n) t = n.inner();
        if (t instanceof Type.PrimitiveType p) return "P:" + Type.canonicalPrimitiveName(p.name());
        if (t instanceof Type.TypeVariable tv) {
            return tv.bound() != null ? erasureKey(tv.bound()) : "Ljava/lang/Object;";
        }
        if (t instanceof Type.WildcardType) return "Ljava/lang/Object;";
        if (t instanceof Type.ArrayType a) return "[" + erasureKey(a.componentType());
        if (t instanceof Type.ClassType c) return "C:" + c.packageName() + "." + c.name();
        if (t instanceof Type.FunctionType f) return f.className() != null ? "C:" + f.className() : "Ljava/lang/Object;";
        return "O:" + t;
    }

    /** §356: o tipo contém um type-variable (topo, componente de array ou nullable)? */
    static boolean containsTypeVar(Type t) {
        if (t == null) return false;
        if (t instanceof Type.NullableType n) return containsTypeVar(n.inner());
        if (t instanceof Type.TypeVariable || t instanceof Type.WildcardType) return true;
        if (t instanceof Type.ArrayType a) return containsTypeVar(a.componentType());
        return false;
    }


}