package dev.kof.compiler;

/**
 * REFACTOR-500 (fila do gate, 19/09): os predicados de tipo do lowering
 * binário, puros e compartilhados — extraídos verbatim de
 * {@code ExpressionBinaryLowerer} (601 ≥ 600 cruzava o gate CI). Rule 7:
 * nome por responsabilidade. Comportamento idêntico, zero chamada nova.
 */
final class ExpressionBinaryPredicates {

    private ExpressionBinaryPredicates() {}

    static boolean isMaybeNullType(Type t) {
        return t instanceof Type.UnknownType
                || (t instanceof Type.NullableType nt && nt.inner() instanceof Type.UnknownType);
    }

        /**
     * §519/#632 (UIW050): handle de kof.ui/midia APAGA para int no JVM. Nas
     * bordas de referencia ele e boxed como Integer — exatamente a familia
     * "Nullable(primitivo)" do D-NULL-INTENT. Estes predicados casam SO o
     * handle (nunca primitivo real) e so entram em clausulas gated p/ JVM.
     */
    static Type boxedHandleType(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (inner instanceof Type.ClassType ct
                && (KofUi.isUiType(ct) || KofMedia.isHandleType(ct))) {
            return TypeMetrics.boxedTypeFor(inner);
        }
        return null;
    }

    /** `Nullable(View)`/`Nullable(Color)` — o slot boxed (igual `Nullable(Int)`). */
    static boolean isNullableHandleLike(Type t) {
        return t instanceof Type.NullableType nt && boxedHandleType(nt) != null;
    }

    /** `View` cru (nao-nullable) — int fisico, precisa boxar p/ borda Object. */
    static boolean isBareHandleErasedToInt(Type t) {
        return !(t instanceof Type.NullableType) && boxedHandleType(t) != null;
    }

    /** Lado comparavel na caixa de slot boxed: primitivo (nullable ou cru) OU handle (nullable ou cru). */
    static boolean isNullablePrimOrBarePrimOrHandle(Type t) {
        return isNullablePrimOrBarePrim(t) || isNullableHandleLike(t) || isBareHandleErasedToInt(t);
    }

/** Int (ou Nullable(Int)) — alvo de cast que handle de UI/mídia satisfaz. */
    static boolean isIntPrimitive(Type t) {
        if (t instanceof Type.NullableType nt) return isIntPrimitive(nt.inner());
        return t instanceof Type.PrimitiveType pt
                && ("int".equals(pt.name()) || "Int".equals(pt.name()));
    }

    /**
     * §553 / D-EQ-UNBOUNDED-T (mantenedora 02/10, regra 6): type variable SEM
     * bound (apaga p/ {@code Object}) — o {@code ==} sobre ela é igualdade de
     * CONTEÚDO (Objects.equals), nunca identidade de referência. Um type var
     * COM bound ({@code T: Animal}) mantém o contrato do bound (referência),
     * pois o bound dá a semântica de comparação.
     */
    static boolean isUnboundedTypeVar(Type t) {
        Type u = t instanceof Type.NullableType nt ? nt.inner() : t;
        return u instanceof Type.TypeVariable tv && tv.bound() == null;
    }

    /** §262(b): `Point?` (Nullable(record)) É record p/ o caminho de conteúdo. */
    static boolean isRecordLike(Type t, CompilerDriver driver) {
        Type u = t instanceof Type.NullableType nt ? nt.inner() : t;
        return CompilerTypes.isRecordType(u, driver.currentUnit, driver.semanticAnalyzer);
    }

    /**
     * D-NULL-INTENT (I6): {@code Nullable(primitivo)} GENUÍNO — física de
     * referência boxed agora, então precisa do MESMO caminho null-safe de
     * conteúdo que record usa (I6: igualdade lifted, nunca identidade do
     * wrapper — cache do {@code Integer} faria dois {@code 10000} distintos
     * darem {@code false} num {@code if_acmp} cru).
     */
    static boolean isNullablePrimLike(Type t) {
        return t instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType pt
                && !Type.isVoid(pt);
    }

    /**
     * D-NULL-INTENT (I6, caso misto): {@code Nullable(primitivo)} genuíno OU
     * primitivo CRU (bare, não-nullable) — cobre {@code m.get("a") == 1}
     * (esquerda boxed, direita literal cru). O lado bare nunca é null, então
     * o dance null-safe do {@link RecordEqualityLowerer} continua correto
     * (equivale a comparar por valor sem nunca dar falso-negativo/NPE).
     */
    static boolean isNullablePrimOrBarePrim(Type t) {
        return isNullablePrimLike(t) || (t instanceof Type.PrimitiveType pt && !Type.isVoid(pt));
    }

    /** §284-map: familia com caixa fisica no slot de Map/local (unboxFn do backend). */
    static boolean isBoxedPrimConsumer(Type t) {
        return t instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType pt
                && switch (pt.name()) {
                    case "int", "char", "short", "byte", "long", "bool", "boolean", "double", "float" -> true;
                    default -> false;
                };
    }

    /** §167: bitwise inteiro `& | ^` (o `&&`/`||` lógico já saiu antes). */
    static boolean isBitwiseOp(String op) {
        return "&".equals(op) || "|".equals(op) || "^".equals(op);
    }

    /** §167: shift inteiro `<< >> >>>`. */
    static boolean isShiftOp(String op) {
        return "<<".equals(op) || ">>".equals(op) || ">>>".equals(op);
    }

    /** Relacionais de ordem: `<`, `<=`, `>`, `>=`. */
    static boolean isRelationalOp(String op) {
        return "<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op);
    }

    /** #779: dynamic/reference lado vs Double/Float, sem caminho primitivo seguro. */
    static boolean isUnorderedAgainstFloating(Type left, Type right) {
        Type l = left instanceof Type.NullableType nt ? nt.inner() : left;
        Type r = right instanceof Type.NullableType nt ? nt.inner() : right;
        if (!(TypeMetrics.isFloatingPoint(l) || TypeMetrics.isFloatingPoint(r))) {
            return false;
        }
        Type other = TypeMetrics.isFloatingPoint(l) ? r : l;
        return !TypeMetrics.isNumeric(other)
                && (Type.isUnknown(other)
                    || other instanceof Type.TypeVariable
                    || other instanceof Type.ClassType
                    || other instanceof Type.ArrayType);
    }

}
