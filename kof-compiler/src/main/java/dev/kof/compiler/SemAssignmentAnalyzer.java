package dev.kof.compiler;

import java.util.List;

/**
 * Análise de assignment como statement, extraída do StatementAnalyzer
 * (REFACTOR-500 fase 6 / split §446): infere alvo/valor e valida
 * assignability (SEM012) SEM emitir o SEM027 (reservado para assignment
 * como VALOR — bug 12); guards SEM048/SEM037/SEM049/SEM038/SEM021/SEM054.
 */
public final class SemAssignmentAnalyzer {

    private SemAssignmentAnalyzer() {}

    /**
     * Assignment como STATEMENT (`a = b`, `i = i + 1` no update do for):
     * infere alvo/valor e valida assignability (SEM012) SEM emitir o SEM027
     * (que é reservado para assignment usado como VALOR — bug 12).
     */
    static Type analyzeAssignmentStatement(SemanticAnalyzer sa, AssignmentExpr ae, SymbolTable scope) {
        Type valueType = SemExpressionTyper.inferType(sa, ae.value(), scope);
        // SG-005/008 (SEM048): `x = null` é erro — null nunca é atribuível
        if (CompilerComparisons.isNullLiteral(ae.value()) && sa.diagnostics() != null) {
            sa.diagnostics().error(ae,
                    "null cannot be assigned: null safety works by narrowing"
                            + " (if (x != null)), never by direct null literals",
                    "SEM048");
        }
        Type targetType = Type.UnknownType.UNKNOWN;
        if (ae.target() instanceof IdentifierExpr ie) {
            SymbolTable.Symbol sym = scope.resolve(ie.name());
            if (sym != null) {
                // D-NARROW-WHILE (#159): alvo NARROWADO — a assignabilidade e
                // o `val` vêm da DECLARAÇÃO; o narrowing é de fluxo, não muda
                // o tipo do slot (senão `s = nextVal(i)` num corpo narrowado
                // dava SEM012 falso-positivo).
                SymbolTable.Symbol effective = Narrowing.assignTarget(scope, ie.name(), sym);
                targetType = effective.type();
                // bug 62: `val` é imutável — escrever em val é erro de
                // mutabilidade (SEM037), alinhado à semântica congelada.
                if (effective instanceof SymbolTable.LocalVariableSymbol lv && lv.isVal()
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(ie,
                            "cannot assign to immutable 'val' variable '" + ie.name() + "'",
                            "SEM037");
                }
                // String += <any> is always valid: the lowerer converts via valueOf+kof_string_concat
                boolean stringConcat = "+=".equals(ae.operator()) && BuiltinTypes.isString(targetType);
                if (sa.diagnostics() != null && !Type.isUnknown(targetType)
                        && !Type.isUnknown(valueType)
                        && !stringConcat
                        && !TypeChecker.isAssignable(sa, valueType, targetType)) {
                    sa.diagnostics().error(ae,
                            "Type mismatch: cannot assign " + Type.display(valueType) + " to " + Type.display(targetType),
                            "SEM012");
                }
            } else {
                targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
            }
        } else if (ae.target() instanceof FieldAccessExpr fa) {
            // #42 (DD-02): escrita em componente de record é SEM038 — o corpus
            // (learn/07) define record como imutável; hoje só o JVM/JS falham
            // em runtime (IllegalAccessError/TypeError) e o interpretador
            // muta em silêncio. O guard no analyzer alinha os 4 caminhos.
            Type recvType = SemExpressionTyper.inferType(sa, fa.receiver(), scope);
            targetType = recvType;
            // §491 face (b) — WRITE: pseudo-tipo builtin (File/Path/Directory/
            // Buffer/Secret/KeyHandle) NÃO tem campo. `SemExpressionTyper` já
            // rejeita o READ (§491 face a); aqui o alvo é escrito direto e o
            // analyzer só checava `resolveFieldInHierarchy` — que devolve null
            // (o pseudo-tipo não é classe do unit) e deixava passar. O lowering
            // então emitia `putfield kof/io/File.bogus` / `kof/Buffer.bogus` /
            // `kof/Secret.bogus` contra classe AUSENTE do runtime →
            // NoClassDefFoundError no load, compilado limpo. `++`/`--` já caíam
            // no guard do READ (o incremento lê antes); `=` e `+=` caem aqui.
            if ((KofIo.isIoType(recvType) || KofBuffer.isBufferType(recvType)
                    || KofSecurity.isSecretType(recvType) || KofSecurity.isKeyHandleType(recvType))
                    && sa.diagnostics() != null) {
                String builtinName = KofIo.isDirectory(recvType) ? "Directory"
                        : KofIo.isPath(recvType) ? "Path"
                        : KofIo.isFile(recvType) ? "File"
                        : KofBuffer.isBufferType(recvType) ? "Buffer"
                        : (KofSecurity.isSecretType(recvType) ? "Secret" : "KeyHandle");
                sa.diagnostics().error(fa,
                        "'" + builtinName + "' has no field '" + fa.fieldName()
                                + "' (this builtin exposes methods, not properties)",
                        "SEM102");
            }
            // §246/#269: escrita em campo por receiver NULLABLE tem o mesmo
            // contrato do READ (SEM049 em SemExpressionTyper): o acesso direto
            // seria NPE em runtime. Sem este guard o analyzer aceitava em
            // silêncio e o lowering emitia `putfield` com descritor errado
            // (`Field "?".num:Ljava/lang/Object;` → VerifyError no load).
            if (recvType instanceof Type.NullableType && sa.diagnostics() != null) {
                SourcePosition faPos = fa.position();
                sa.diagnostics().error(faPos != null ? faPos.file() : "",
                        faPos != null ? faPos.line() : 0, faPos != null ? faPos.column() : 0, 0,
                        "receiver is nullable (T?); narrow first: if (x != null) { x.field = v }",
                        "SEM049");
            }
            // DD-02/#42: escrita em componente de record é SEM038. Para o
            // receiver explícito, o tipo resolve normalmente; para `this`,
            // inferType não tipa o identificador — usa-se currentClassName.
            // `this.x =` só é legal no construtor (init do campo final,
            // JVMS 4.4); em método de record → sintoma (c) do #42.
            boolean onThis = fa.receiver() instanceof IdentifierExpr rid && "this".equals(rid.name());
            boolean recvIsRecord = onThis
                    ? (sa.currentClassName() != null && CompilerTypes.isRecordType(
                            new Type.ClassType("", sa.currentClassName(), List.of()), sa.unit(), sa))
                    : (recvType != null && CompilerTypes.isRecordType(recvType, sa.unit(), sa));
            if (sa.diagnostics() != null && recvIsRecord && !(onThis && sa.inConstructor)) {
                sa.diagnostics().error(fa,
                        "cannot assign to '" + fa.fieldName() + "': record is immutable",
                        "SEM038");
            }
            // #331/#327 — escrita em campo: MESMO contrato do READ (que passa
            // por SemExpressionTyper), mas aqui o inferType e so do RECEIVER,
            // entao os cheques de acesso/`final` precisam ser feitos a mao.
            // Owner: receiver ClassType explicito, ou currentClassName p/
            // `this.x`. final so e escrito legalmente no <init> da declarante
            // (o construtor ja cai no caminho legal de checkFinalFieldWrite).
            String ownerName = onThis ? sa.currentClassName()
                    : (recvType instanceof Type.ClassType rct ? rct.name() : null);
            if (ownerName != null) {
                SymbolTable.Symbol wf = MemberResolver.resolveFieldInHierarchy(sa, ownerName, fa.fieldName());
                if (wf instanceof SymbolTable.FieldSymbol wfs) {
                    MemberAccessChecks.checkFieldAccess(sa, wfs);
                    MemberAccessChecks.checkFinalFieldWrite(sa, wfs);
                    // §379 (era §376; originally §363/§371): escrita de lambda em campo de tipo-funcao tem o MESMO
                    // contrato da declaracao SC2 (`var f: (T) -> U = ...`): o lambda
                    // e emitido com a interface da assinatura INFERIDA do corpo e o
                    // call site despacha pela DECLARADA do campo — divergencia
                    // (ex.: corpo `-> Map?` em campo `-> Map`) ICE em runtime.
                    // Rejeita no compile-time com SEM021. Este gate e o dueno de
                    // FunctionType; o gate generico §368 abaixo cobre o resto.
                    Type fieldType = wfs.type();
                    if (sa.diagnostics() != null && fieldType instanceof Type.FunctionType
                            && !TypeChecker.functionTypesConform(valueType, fieldType)) {
                        sa.diagnostics().error(fa,
                                "type mismatch: cannot assign " + Type.display(valueType)
                                        + " to field '" + fa.fieldName() + ": " + Type.display(fieldType) + "'",
                                "SEM021");
                    }
                    // §368: a store de campo deve passar pelo MESMO gate de
                    // atributibilidade do local (:52-58) e do var-decl (:245).
                    // Sem isto, `x.c = "x"` (Char) e `x.n = 2.5` (Int) compilam
                    // "clean" e morrem em VerifyError na carga (JVM/Native) ou
                    // viram phantom-store no Script/JS — R6 + regra 5. A caixa
                    // de widening numerico (42 → Int?, 'x' → Char?) ja existe no
                    // writer (§361); o gate so rejeita o que NENHUM backend
                    // executa hoje — nenhum golden funcional muda (Probe4).
                    boolean strConcatAssign = "+=".equals(ae.operator()) && BuiltinTypes.isString(wfs.type());
                    if (sa.diagnostics() != null && !Type.isUnknown(fieldType)
                            && !Type.isUnknown(valueType)
                            && !strConcatAssign
                            && !(fieldType instanceof Type.FunctionType)
                            && !TypeChecker.isAssignable(sa, valueType, fieldType)) {
                        sa.diagnostics().error(fa,
                                "Type mismatch: cannot assign " + Type.display(valueType) + " to " + Type.display(fieldType),
                                "SEM012");
                    }
                }
            }
        } else if (ae.target() instanceof ArrayAccessExpr aa) {
            // #149/#152: `l[i]` READ on a List is supported; the WRITE face
            // (`l[i] = v`) was never lowered — it emitted a raw array store
            // (JVM VerifyError "not assignable to Object" at aastore, Native
            // SIGSEGV exit 139, JS silent). R6: reject at compile-time pointing
            // to `.set(i, v)` instead of emitting broken bytecode. Only List
            // needs the check here: String/Map/Set writes already hit the
            // read guard in SemExpressionTyper (avoiding a duplicate SEM054).
            Type recvType = SemExpressionTyper.inferType(sa, aa.receiver(), scope);
            Type unwrapped = recvType instanceof Type.NullableType nt ? nt.inner() : recvType;
            if (sa.diagnostics() != null && BuiltinTypes.isList(unwrapped)) {
                SourcePosition pos = aa.position();
                sa.diagnostics().error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        "`[]` assignment only works on arrays in Kof; for a List use l.set(i, v)",
                        "SEM054");
            }
            // ARRAY-1 (27/10, #776): o §368 para ELEMENTO de array real — a
            // store SIMPLES `a[i] = v` passa pelo MESMO gate de
            // atributibilidade do local e do campo. Sem isto, `a[0] =
            // 3000000000L` (Long em Int[]) compila "clean" e morre em
            // VerifyError na carga da classe (o launcher JVM disfarça de
            // "JavaFX runtime missing" — o diagnóstico da mensagem nunca é
            // benigno). MESMO código do gate da variável (o N2 mediu SEM021).
            // #976 (28/10, a suíte estampada 7560/2F): a store COMPOSTA
            // (`+=`, `*=`) NÃO passa aqui — ela é leitura+op+loja com o
            // PRÓPRIO caminho de conversão (emitCompoundRhsConv): o operando
            // combina pela ARITMÉTICA/concat (o golden `ab9`: `names[0] += 9`
            // → concat; `chars[i] += 1` → Char trunc), não pela loja direta —
            // gatear o operando cru contra o tipo do ELEMENTO rejeitava o que
            // TODOS os backends executam. O gate da variável/local já sabe
            // disso (o `stringConcat`); aqui o gate INTEIRO fica na store
            // simples.
            Type arrT = unwrapped instanceof Type.ArrayType aat ? aat.componentType() : null;
            if (sa.diagnostics() != null && arrT != null && "=".equals(ae.operator())) {
                Type vT = SemExpressionTyper.inferType(sa, ae.value(), scope);
                // #976 face 2 (a suíte estampada 7560/2F): o §184/§187 da
                // matriz de conformance PINA a store de estreitamento
                // i32→i32 (`c[0] = 70000` em Char[], `b[0] = 130` em Byte[])
                // como LEGAL — BASTORE/SASTORE/CASTORE truncam em TODOS os
                // alvos (o expected `4464`). A única classe que NENHUM
                // backend executa = o valor de 64 bits NO slot i32 (o
                // lastore pede i64 e o lowering não emite l2i — o
                // VerifyError que fundou o gate). O gate recusa SÓ ela.
                boolean elemTruncNarrow = (Type.PrimitiveType.BYTE.equals(arrT)
                        || Type.PrimitiveType.SHORT.equals(arrT)
                        || Type.PrimitiveType.CHAR.equals(arrT))
                        && (Type.PrimitiveType.INT.equals(vT)
                        || Type.PrimitiveType.BYTE.equals(vT)
                        || Type.PrimitiveType.SHORT.equals(vT)
                        || Type.PrimitiveType.CHAR.equals(vT));
                if (!Type.isUnknown(vT) && !Type.isUnknown(arrT)
                        && !elemTruncNarrow
                        && !TypeChecker.isAssignable(sa, vT, arrT)) {
                    SourcePosition apos = aa.position();
                    sa.diagnostics().error(apos != null ? apos.file() : "",
                            apos != null ? apos.line() : 0, apos != null ? apos.column() : 0, 0,
                            "type mismatch: cannot assign " + Type.display(vT)
                                    + " to array element of " + Type.display(arrT),
                            "SEM021");
                }
            }
            targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
        } else if (ae.target() != null) {
            targetType = SemExpressionTyper.inferType(sa, ae.target(), scope);
        }
        return targetType;
    }
}
