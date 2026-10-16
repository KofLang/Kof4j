package dev.kof.compiler;

import java.util.List;

/**
 * Emissão de conversões de primitivos (widen/narrow/box/unbox) e helpers.
 */
public final class CompilerEmissionHelpers {

    private CompilerEmissionHelpers() {}

    static boolean needsErasureBoxing(CompilerDriver driver) {
        // §284: Native tambem precisa do box — o runtime nativo ganhou
        // kof_box_*/kof_unbox_* reais; sem eles o primitivo cru caia na
        // pilha onde o consumer esperava ponteiro (SIGSEGV 139).
        return switch (driver.target) {
            case JVM, NATIVE, NATIVE_RISCV64, NATIVE_AARCH64 -> true;
            default -> false;
        };
    }

    static boolean isJvmTarget(CompilerDriver driver) {
        return driver.target == Target.JVM;
    }

    static void emitWideningIfNeeded(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        // slot declarado primitivo + valor de tipo APAGADO (Unknown/Object/
        // TypeVariable): o valor real chega boxed (ex.: `await` sobre um
        // handle que perdeu o Handle<Int> ao passar por um parâmetro Object —
        // kof_await devolve Object). Sem o unbox aqui, o return emitia
        // ireturn sobre referência → VerifyError (GitHub #31).
        if (!tn.isEmpty() && !TypeMetrics.isPrimitiveType(from)
                && (from instanceof Type.UnknownType
                    || from instanceof Type.TypeVariable
                    || (from instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                        && "Object".equals(ct.name())))) {
            // D-NULL-INTENT (#278): destino Nullable(primitivo) — o "Object"
            // aqui é só a imprecisão do join heterogêneo (#57/§70 do
            // if/switch, boxesOwnBranches), o valor JÁ chega fisicamente
            // boxed (Integer/Boolean/... ou aconst_null). `emitErasureUnbox`
            // faz um kof_unbox completo (Object->primitivo cru) — sem tipo
            // real p/ despachar, isso é `Object.intValue()Integer;`
            // (NoSuchMethodError, achado em `Int? sw(x) = switch{...default
            // -> null}`). O correto é só um CHECKCAST p/ o wrapper real —
            // upcast/downcast de referência, nunca unbox.
            if (to instanceof Type.NullableType nt2 && nt2.inner() instanceof Type.PrimitiveType) {
                if (needsErasureBoxing(driver)) {
                    ops.add(new KofCheckCast(TypeMetrics.boxedTypeFor(nt2.inner())));
                }
                return;
            }
            emitErasureUnbox(driver, ops, to);
            return;
        }
        KofUnaryOp conv = switch (tn) {
            case "long", "Long" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2L;
                default -> null;
            };
            case "float", "Float" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2F;
                case "long", "Long" -> KofUnaryOp.L2F;
                case "double", "Double" -> KofUnaryOp.D2F;
                default -> null;
            };
            case "double", "Double" -> switch (fn) {
                case "int", "Int", "char", "Char", "short", "Short", "byte", "Byte" -> KofUnaryOp.I2D;
                case "long", "Long" -> KofUnaryOp.L2D;
                case "float", "Float" -> KofUnaryOp.F2D;
                default -> null;
            };
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    /**
     * §121/§126 (B1, coleção): conversão ANTES do store de VALOR numa
     * coleção pinada. Só age quando AMBOS os lados são primitivos e o
     * widening é genuíno (emitWideningIfNeeded SÓ promove I2L/I2F/I2D/
     * L2F/L2D/D2F — nunca trunca); senão é NO-OP (byte-idêntico ao atual,
     * zero regressão). Rejeição/narrowing ficam com o guard SEM056 do
     * §126 (não é daqui). Nullable é desempacotado (storage é o inner).
     * from = tipo do ARG empilhado, to = tipo PINADO do slot.
     *
     * §383/#561 (opção (a), decisão da mantenedora 20/09 — slot ganha, o
     * valor é RESCRITO no store): o miss abençoado bool→long chegava ao
     * box pelo tipo PINADO (`Long.valueOf(J)` sobre `ICONST_1` width-1) →
     * COMP002 frame crash no JVM (medido 20/09; Script/Native já gravavam
     * 1). A conversão I2L casa os 3 que já funcionavam e o JS (BigInt
     * da lowering do I2L). Só nos SITES DE LIST (`widenBoolToLong`): os
     * slots de Map/Set são heterogeneidade TOLERADA pelo consenso 3/4
     * (#561, faces medidas S2/M1 20/09) — alargar mudaria faces vivas.
     */
    static boolean coerceStoreWiden(CompilerDriver driver, List<KofOperation> ops,
                                    Type from, Type to, boolean widenBoolToLong) {
        if (from == null || to == null) return false;
        Type f = from instanceof Type.NullableType nt ? nt.inner() : from;
        Type t = to instanceof Type.NullableType nt2 ? nt2.inner() : to;
        if (!(f instanceof Type.PrimitiveType fp) || !(t instanceof Type.PrimitiveType tp)) return false;
        if (fp.equals(tp)) return false;
        // §383: bool em slot Long — pila de int (bool é 0/1) → I2L antes do
        // box pelo slot (mesmo valor 1/0 que Script/Native já gravam).
        String bn = TypeMetrics.primitiveName(fp), bt = TypeMetrics.primitiveName(tp);
        if (widenBoolToLong && ("bool".equals(bn) || "Bool".equals(bn))
                && ("long".equals(bt) || "Long".equals(bt))) {
            ops.add(new KofUnary(KofUnaryOp.I2L, fp));
            return true;
        }
        // narrowing (Long→Int) NÃO é abençoado (§126: só widening numérico
        // passa) — deixa o arg cru, nunca truncar silenciosamente (R6).
        if (TypeMetrics.primWidth(fp) > TypeMetrics.primWidth(tp)) return false;
        int before = ops.size();
        emitWideningIfNeeded(driver, ops, fp, tp);
        return ops.size() > before;
    }

    /**
     * §121/§126 (B1): empilha os args de um add/set/put de coleção e aplica
     * a conversão no arg de VALOR (valIdx). Ao converter, ajusta
     * `argTypes` p/ o tipo PINADO — o box JVM é guiado pelos paramTypes.
     * §383: `listSite` liga o bool→long só nas escritas de List (Map/Set
     * guardam heterogeneidade tolerada — faces medidas S2/M1).
     */
    static int emitArgsCoercingValue(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals,
            List<Type> argTypes, Type slotType, int valIdx) {
        return emitArgsCoercingValue(driver, mc, ops, owner, localIdx, locals,
                argTypes, slotType, valIdx, false);
    }

    static int emitArgsCoercingValue(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals,
            List<Type> argTypes, Type slotType, int valIdx, boolean listSite) {
        return emitArgsCoercingValue(driver, mc, ops, owner, localIdx, locals,
                argTypes, slotType, "", valIdx, listSite);
    }

    /**
     * #845: num método de List ({@code listFn}), um {@code Int?} estreitado abre a
     * caixa antes da chamada e o argType acompanha o que ficou na pilha — nos
     * ÍNDICES Int ({@link CollectionMethodGates#indexArgCount}) e no VALOR de
     * elemento primitivo ({@link CollectionMethodGates#elementValueArg}): o slot
     * nativo de List é cru (§253, o ponteiro da caixa nunca casa no cmpq) e o box
     * JVM segue o argType (valueOf(I) sobre a referência era VerifyError).
     */
    static int emitArgsCoercingValue(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals,
            List<Type> argTypes, Type slotType, String listFn, int valIdx, boolean listSite) {
        int indexArgs = CollectionMethodGates.indexArgCount(listFn);
        int elementArg = CollectionMethodGates.elementValueArg(listFn);
        for (int ai = 0; ai < mc.arguments().size(); ai++) {
            ExpressionNode arg = mc.arguments().get(ai);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            if (ai < argTypes.size() && (ai < indexArgs || ai == elementArg)) {
                argTypes.set(ai, emitNarrowedNullableUnbox(driver, ops, argTypes.get(ai),
                        ai < indexArgs ? Type.PrimitiveType.INT : slotType));
            }
            if (ai == valIdx && coerceStoreWiden(driver, ops, argTypes.get(ai), slotType, listSite)) {
                argTypes.set(valIdx, slotType instanceof Type.NullableType nt ? nt.inner() : slotType);
            }
            // §284 follow-up (18/09): escrita de primitivo em slot de MAP
            // — o slot físico do Map é sempre objeto (contrato JVM, medido:
            // JvmOpCollections faz emitUnboxIfPrimitive no leitor e o HashMap
            // guarda Integer). Com o box real do §284 o par write-raw ×
            // read-unbox (`mapOf(); m.put("a",1); m.get("a") == 1`) SIGSEGVava
            // no rdi=1 do unbox. O box é aplicado pelo CALLER do helper (só
            // Map; List/Set nativos permanecem raw — contrato próprio §253).
        }
        return localIdx;
    }

    static void emitPrimNarrow(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        KofUnaryOp conv = switch (tn) {
            case "int", "Int" -> switch (fn) {
                case "long", "Long" -> KofUnaryOp.L2I;
                case "float", "Float" -> KofUnaryOp.F2I;
                case "double", "Double" -> KofUnaryOp.D2I;
                default -> null;
            };
            case "long", "Long" -> switch (fn) {
                case "float", "Float" -> KofUnaryOp.F2L;
                case "double", "Double" -> KofUnaryOp.D2L;
                default -> null;
            };
            // #471: as Byte / as Short must emit i2b / i2s before Byte/Short.valueOf
            // so the value is truncated to the correct range before boxing
            case "byte", "Byte" -> KofUnaryOp.I2B;
            case "short", "Short" -> KofUnaryOp.I2S;
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
    }

    /**
     * known-bugs §696: um STORE num slot Byte/Short estreita como
     * `(v as Byte)` — a MESMA semântica que o §695 deu ao cast. Sem isto o
     * valor CRU fica no slot e os quatro alvos divergem: o JVM estoura o
     * cache do `Byte.valueOf` (ArrayIndexOutOfBoundsException), o Script
     * estreita, e native/JS mantêm o cru. Chamado nos três stores (declaração
     * de local, atribuição simples, return) logo APÓS o widening — que é
     * NO-OP para destinos Byte/Short (só promove, nunca trunca).
     */
    static void emitStoreNarrow(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (!TypeMetrics.isPrimitiveType(from) || !TypeMetrics.isPrimitiveType(to)) return;
        String tn = TypeMetrics.primitiveName(to);
        if (!"byte".equals(tn) && !"Byte".equals(tn)
                && !"short".equals(tn) && !"Short".equals(tn)) {
            return;
        }
        if (from.equals(to)) return;
        // uma fonte mais LARGA (Long/Float/Double) converte primeiro para Int,
        // espelhando o caminho do cast (que parte de um Int na pilha).
        switch (TypeMetrics.primitiveName(from)) {
            case "long", "Long" -> ops.add(new KofUnary(KofUnaryOp.L2I, from));
            case "float", "Float" -> ops.add(new KofUnary(KofUnaryOp.F2I, from));
            case "double", "Double" -> ops.add(new KofUnary(KofUnaryOp.D2I, from));
            default -> { }
        }
        driver.emitPrimNarrow(ops, Type.PrimitiveType.INT, to);
    }

    static boolean isZeroLiteral(LiteralExpr lit) {
        if (lit.value() == null) return false;
        String v = lit.value().trim();
        boolean zero = "0".equals(v) || "-0".equals(v)
                || "0.0".equals(v) || "-0.0".equals(v) || "0.00".equals(v);
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT, ConcreteLiteralKind.LONG -> zero;
            case ConcreteLiteralKind.FLOAT, ConcreteLiteralKind.DOUBLE -> zero;
            default -> false;
        };
    }

    static void emitErasureBox(CompilerDriver driver, List<KofOperation> ops, Type primitive) {
        if (!driver.needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        Type boxParam = primitive instanceof Type.PrimitiveType pt
                && ("char".equals(pt.name()) || "Char".equals(pt.name())) ? Type.PrimitiveType.INT : primitive;
        ops.add(new KofCall(boxed, "kof_box", List.of(boxParam), boxed, KofCallKind.FUNCTION));
    }

    static void emitErasureUnbox(CompilerDriver driver, List<KofOperation> ops, Type primitive) {
        if (!driver.needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        ops.add(new KofCall(primitive, "kof_unbox", List.of(boxed), primitive, KofCallKind.FUNCTION));
    }

    /**
     * §284-map (18/09): unbox para CONSUMIDORES de {@code Int?} no native —
     * variantes do contrato de slot de Map (fisicamente boxed) cruzado com o
     * caminho de funcao local (cru). Soft = caixa abre, cru passa, null da o
     * mesmo diagnostico do estrito. Em JVM/Script o helper e identico ao
     * estrito (la o Nullable(primitivo) e sempre a referencia do wrapper).
     */
    static void emitErasureUnboxSoft(CompilerDriver driver, List<KofOperation> ops, Type primitive) {
        if (!driver.needsErasureBoxing()) return;
        Type boxed = TypeMetrics.boxedTypeFor(primitive);
        if (driver.target.isNative()) {
            ops.add(new KofCall(primitive, "kof_unbox_soft", List.of(boxed), primitive, KofCallKind.FUNCTION));
            return;
        }
        ops.add(new KofCall(primitive, "kof_unbox", List.of(boxed), primitive, KofCallKind.FUNCTION));
    }

    /**
     * #845: consumidor PRIMITIVO (store num local, return, formal, índice de
     * List) recebendo um {@code T?} primitivo que o frontend estreitou
     * ({@code if (i != null) { Int j = i }}). O estreitamento é semântico: o
     * slot continua Nullable — boxed desde o D-NULL-INTENT — e o valor chega
     * como a REFERÊNCIA do wrapper (JVM: istore/ireturn sobre Integer =
     * VerifyError; Native: o ponteiro da caixa lido como número). Abre a caixa
     * pelo inner da ORIGEM (soft no native, §284-map) e devolve o tipo agora
     * na pilha; o widening para o destino vem DEPOIS, no chamador
     * ({@code Int? → Long} = intValue + I2L — abrir pelo destino era o
     * {@code checkcast Long} → ClassCastException). Destino Nullable/referência
     * ou origem já crua: no-op, devolve {@code from}. A ACEITAÇÃO (só com
     * narrowing) é da semântica — aqui só se conserta a representação.
     */
    static Type emitNarrowedNullableUnbox(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (!TypeMetrics.isNullablePrimitive(from)
                || !(to instanceof Type.PrimitiveType tp) || Type.isVoid(tp)) {
            return from;
        }
        Type inner = ((Type.NullableType) from).inner();
        // Char? é boxed como Integer (boxPrimitive/kof_box: param INT) — abre como
        // Int (Integer.intValue()I; `intValue()C` não existe); na pilha char já é int
        emitErasureUnboxSoft(driver, ops, TypeMetrics.isCharType(inner) ? Type.PrimitiveType.INT : inner);
        return inner;
    }

    /**
     * §168: literal `1` no TIPO do operando — usado por `++`/`--`. Antes o
     * incremento empurrava sempre `INT 1` e o binário era emitido com o tipo
     * do alvo: `long x; x++` virava `LADD` sobre (long, int) → VerifyError no
     * JVM; `double`/`float` idem. O literal tem de casar com o tipo do alvo.
     */
    static void emitIncrementOne(List<KofOperation> ops, Type type) {
        String name = TypeMetrics.primitiveName(type);
        switch (name) {
            case "long", "Long" -> ops.add(new KofLoadLiteral(Type.PrimitiveType.LONG, 1L));
            case "float", "Float" -> ops.add(new KofLoadLiteral(Type.PrimitiveType.FLOAT, 1.0f));
            case "double", "Double" -> ops.add(new KofLoadLiteral(Type.PrimitiveType.DOUBLE, 1.0));
            default -> ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        }
    }
}
