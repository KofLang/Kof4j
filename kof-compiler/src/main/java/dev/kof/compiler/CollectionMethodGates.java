package dev.kof.compiler;

/**
 * #382/#386 — gates compartilhados dos métodos novos de coleção (aridade e
 * domínio do sort). Vivem fora do {@code CollectionCallLowerer} (gate 500):
 * um gate na semântica compartilhada, os 4 alvos reportam igual
 * (precedente SEM072/#336, SEM073/#361 — nunca 4 crashes diferentes).
 */
public final class CollectionMethodGates {

    private CollectionMethodGates() {}

    /** Aridade esperada dos métodos novos; -1 = sem checagem (legados). */
    static int expectedArgs(String opFn) {
        return switch (opFn) {
            case "kof_list_index_of", "kof_list_last_index_of", "kof_list_add_all" -> 1;
            case "kof_list_sub_list" -> 2;
            case "kof_list_take", "kof_list_drop" -> 1;
            case "kof_list_slice" -> 2;
            // D-MULTIPARADIGMA-PHASE1A — quantifiers take exactly one lambda.
            case "kof_list_any", "kof_list_all", "kof_list_none" -> 1;
            // D-MULTIPARADIGMA-PHASE1A slice 1b — find takes one lambda;
            // count with a lambda counts matches (bare count keeps size).
            // Slice 1c — forEach takes one lambda. Slice 1d — flatMap too.
            // Slice 1e — distinct takes no arguments.
            case "kof_list_find", "kof_list_count_pred", "kof_list_foreach",
                    "kof_list_flatmap" -> 1;
            // Slice 1e — distinct takes no arguments.
            case "kof_list_distinct" -> 0;
            case "kof_list_sort" -> 0;
            // D-MULTIPARADIGMA-PHASE1A slice 1g — sorted: natural takes no
            // arguments; with comparator it takes exactly one lambda
            // (D-MULTIPARADIGMA-SORTED).
            case "kof_list_sorted" -> 0;
            case "kof_list_sorted_cmp" -> 1;
            // D-MULTIPARADIGMA-PHASE1A slice 1h — groupBy takes exactly one
            // lambda (D-MULTIPARADIGMA-GROUPBY).
            case "kof_list_groupby" -> 1;
            case "kof_map_contains_value" -> 1;
            case "kof_map_put_if_absent" -> 2;
            default -> -1;
        };
    }

    /** SEM025 de aridade para os métodos novos; null = aridade correta. */
    static String arityError(String opFn, String kind, String mn, int got) {
        int want = expectedArgs(opFn);
        if (want < 0 || got == want) return null;
        return kind + "." + mn + " takes exactly " + want + " argument(s)"
                + ("kof_list_sort".equals(opFn)
                        ? " — sort() uses the natural order (for a custom order use sorted((a, b) -> Int))" : "");
    }

    /**
     * #382 — domínio do sort (SEM097, todos os alvos): Kof não tem
     * Comparable/Comparator, então só ordens naturais existem. Aceitar
     * record/class viraria 3 comportamentos divergentes no runtime
     * (ClassCastException no JVM, ponteiro no Native, no-op estável no JS)
     * — rejeição universal, mesmo gate de SEM056.
     */
    static boolean naturalOrderType(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (inner instanceof Type.UnknownType) return true; // lista vazia / pré-pin
        if (BuiltinTypes.isString(inner)) return true;
        if (inner instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int", "long", "double", "float", "boolean", "bool", "char" -> true;
                default -> false;
            };
        }
        return false;
    }

    static String sortDomainError(Type elemType) {
        Type inner = elemType instanceof Type.NullableType nt ? nt.inner() : elemType;
        return "List.sort/sorted needs elements with a natural order (Int/Long/Double/Float/Bool/Char/String);"
                + " '" + CollectionWrites.typeNameFor(inner) + "' has none"
                + " — use sorted((a, b) -> Int) with an explicit comparator instead";
    }

    /** Tag de comparação do sort: 0=raw signed qword, 1=String, 2=Double,
     *  3=Float (§352/NAT001 fechado 21/09: o slot guarda os 32 bits crus e o
     *  runtime alarga para Double — `cvtss2sd` no x86, `fcvt.d.s` no cross —
     *  reusando a semântica medida do Double.compare). */
    static int sortTag(Type elemType) {
        Type inner = elemType instanceof Type.NullableType nt ? nt.inner() : elemType;
        if (BuiltinTypes.isString(inner)) return 1;
        if (inner instanceof Type.PrimitiveType pt) {
            String n = Type.canonicalPrimitiveName(pt.name());
            if ("double".equals(n)) return 2;
            if ("float".equals(n)) return 3;
        }
        return 0;
    }

    private static Type unwrap(Type t) {
        return t instanceof Type.NullableType nt ? nt.inner() : t;
    }

    /**
     * D-MULTIPARADIGMA-PHASE1A slice 1b — box tag for {@code find} on Native:
     * Native list slots hold RAW primitives but {@code T?} consumers expect
     * boxed values (like {@code Map.get}, whose slots are boxed) — the hit
     * path must box. Numbering mirrors {@code NativeBoxTags} collection tags
     * (0=int/char/short/byte, 1=String/passthrough, 2=long, 3=bool, 4=double,
     * 5=float, 6=unknown/record/passthrough); the asm maps each to its
     * {@code kof_box_*} (or passthrough). Unknown passes through — homogeneous
     * lists (SEM056) never hide a raw primitive behind Unknown in practice.
     */
    static int findBoxTag(Type elemType) {
        Type e = unwrap(elemType);
        if (e instanceof Type.PrimitiveType pt) {
            switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int", "char", "short", "byte": return 0;
                case "long": return 2;
                case "bool", "boolean": return 3;
                case "double": return 4;
                case "float": return 5;
                default: return 6;
            }
        }
        return 6;
    }

    /** §122: tipos que NUNCA são um índice/count válido (Int é o contrato). */
    static boolean isReferenceIndexType(Type t) {
        if (t == null || Type.UnknownType.UNKNOWN.equals(t)) return false;
        if (t instanceof Type.NullableType nt) return isReferenceIndexType(nt.inner());
        if (TypeMetrics.isPrimitiveType(t)) return false;
        return t instanceof Type.ClassType || t instanceof Type.ArrayType
                || t instanceof Type.TypeVariable;
    }

    /** pagination P1 — take/drop/slice exigem Int count; null = ok. */
    static String countDomainError(String opFn, String mn, java.util.List<Type> argTypes) {
        if (!("kof_list_take".equals(opFn) || "kof_list_drop".equals(opFn)
                || "kof_list_slice".equals(opFn))) return null;
        for (int i = 0; i < argTypes.size() && i < 2; i++) {
            if (isReferenceIndexType(argTypes.get(i))) {
                return "List." + mn + " takes an Int count; "
                        + CollectionWrites.typeNameFor(argTypes.get(i)) + " is not a count";
            }
        }
        return null;
    }

    /**
     * #386 — tag do scan de VALOR no containsValue nativo: 0 = cmpq raw
     * (Double/Bool/Char-largo/classes — slot e arg crús; Double.equals é
     * bit-a-bit, logo o mesmo cmpq; classe sem equals é identidade, como o
     * {@code l.contains(rec)}) — 1 = kof_string_equals (String×String) —
     * 2 = kof_box_equals (família Int/Long dos dois lados: slot fisicamente
     * boxed, arg boxed pelo lowering; Int×Long já casa com o java.util — o
     * tag interno da caixa distingue e o resultado é false, como equals) —
     * 3 = false garantido (§126 safe miss: famílias ≠ — Int-arg num mapa de
     * Double nunca é equals no JVM, e no native NÃO se derefença bits crus;
     * Unknown-value = mapa vazio, false sempre) — 6 = mapa de valor Object
     * (§352 NAT002 fechado 21/09): o compile não sabe o que a expressão
     * carrega (caixa/String/ponteiro/bit cru), então o runtime classifica
     * arg e entradas com kof_value_kind e compara no caminho do kind) —
     * 7 = record/classe Kof (§104b-ii nunca portado pro VALOR do map,
     * só pra CHAVE — {@link CollectionWrites#mapKeyTag}; {@code
     * kof_obj_equals}, o mesmo runtime content-equality de hoje).
     */
    static int valueCmpTag(Type valueType, Type argType) {
        Type vt = unwrap(valueType);
        Type at = unwrap(argType);
        boolean vStr = vt != null && BuiltinTypes.isString(vt);
        boolean aStr = at != null && BuiltinTypes.isString(at);
        if (vt == null || vt instanceof Type.UnknownType) return 3;
        if (BuiltinTypes.isObject(vt)) return 6;
        if (vStr) return aStr ? 1 : 3;
        boolean vBox = CollectionLoweringSupport.mapBoxablePrim(vt);
        boolean aBox = at != null && CollectionLoweringSupport.mapBoxablePrim(at);
        if (vBox) return aBox ? 2 : 3;
        if (aStr || aBox) return 3;
        if (CollectionWrites.isKofObject(vt)) return 7;
        return 0;
    }
}
