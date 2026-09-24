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
            case "kof_list_sort" -> 0;
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
                        ? " — sort() uses the natural order (Kof has no Comparator yet)" : "");
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
        return "List.sort needs elements with a natural order (Int/Long/Double/Float/Bool/Char/String);"
                + " '" + CollectionWrites.typeNameFor(inner) + "' has none"
                + " (Kof has no Comparator/Comparable — sort the projected key list instead)";
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
        boolean vBox = CollectionCallLowerer.mapBoxablePrim(vt);
        boolean aBox = at != null && CollectionCallLowerer.mapBoxablePrim(at);
        if (vBox) return aBox ? 2 : 3;
        if (aStr || aBox) return 3;
        if (CollectionWrites.isKofObject(vt)) return 7;
        return 0;
    }
}
