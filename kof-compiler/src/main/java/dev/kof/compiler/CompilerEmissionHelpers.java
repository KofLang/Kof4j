package dev.kof.compiler;

import java.util.List;

/**
 * Emissão de conversões de primitivos (widen/narrow/box/unbox) e helpers.
 */
public final class CompilerEmissionHelpers {

    private CompilerEmissionHelpers() {}

    static boolean needsErasureBoxing(CompilerDriver driver) {
        return driver.target == Target.JVM;
    }

    static boolean isJvmTarget(CompilerDriver driver) {
        return driver.target == Target.JVM;
    }

    static void emitWideningIfNeeded(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (from.equals(to)) return;
        String fn = TypeMetrics.primitiveName(from);
        String tn = TypeMetrics.primitiveName(to);
        boolean fromIsErasedRef = !TypeMetrics.isPrimitiveType(from)
                && (from instanceof Type.UnknownType
                    || from instanceof Type.TypeVariable
                    || (from instanceof Type.ClassType ct && "java.lang".equals(ct.packageName())
                        && "Object".equals(ct.name())));
        // D-NULL-INTENT/N1: `to` Nullable(primitivo) é BOXED — um valor
        // apagado (Object, ex.: join heterogêneo de if/switch com ramo null)
        // já É a referência certa (Integer/null); precisa só de CHECKCAST,
        // NUNCA desembrulhar (unbox tentaria `Object.intValue()` — método
        // inexistente, NoSuchMethodError). `tn` desempacota Nullable, então
        // este caso tem que ser checado ANTES do ramo unbox abaixo.
        if (fromIsErasedRef && to instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType) {
            ops.add(new KofCheckCast(nt.inner()));
            return;
        }
        // slot declarado primitivo + valor de tipo APAGADO (Unknown/Object/
        // TypeVariable): o valor real chega boxed (ex.: `await` sobre um
        // handle que perdeu o Handle<Int> ao passar por um parâmetro Object —
        // kof_await devolve Object). Sem o unbox aqui, o return emitia
        // ireturn sobre referência → VerifyError (GitHub #31).
        if (!tn.isEmpty() && fromIsErasedRef) {
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
     */
    static boolean coerceStoreWiden(CompilerDriver driver, List<KofOperation> ops, Type from, Type to) {
        if (from == null || to == null) return false;
        Type f = from instanceof Type.NullableType nt ? nt.inner() : from;
        Type t = to instanceof Type.NullableType nt2 ? nt2.inner() : to;
        if (!(f instanceof Type.PrimitiveType fp) || !(t instanceof Type.PrimitiveType tp)) return false;
        if (fp.equals(tp)) return false;
        // narrowing (Long→Int) NÃO é abençoado (§126: só widening numérico
        // passa) — deixa o arg cru, nunca truncar silenciosamente (R6).
        if (TypeMetrics.primWidth(fp) > TypeMetrics.primWidth(tp)) return false;
        int before = ops.size();
        emitWideningIfNeeded(driver, ops, fp, tp);
        return ops.size() > before;
    }

    /**
     * §121/§126 (B1): empilha os args de um add/set/put de coleção e aplica
     * a coerção de widening no arg de VALOR (valIdx). Ao converter, ajusta
     * `argTypes` p/ o tipo PINADO — o box JVM é guiado pelos paramTypes.
     */
    static int emitArgsCoercingValue(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals,
            List<Type> argTypes, Type slotType, int valIdx) {
        for (int ai = 0; ai < mc.arguments().size(); ai++) {
            ExpressionNode arg = mc.arguments().get(ai);
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            if (ai == valIdx && coerceStoreWiden(driver, ops, argTypes.get(ai), slotType)) {
                argTypes.set(valIdx, slotType instanceof Type.NullableType nt ? nt.inner() : slotType);
            }
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
            default -> null;
        };
        if (conv != null) {
            ops.add(new KofUnary(conv, from));
        }
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
