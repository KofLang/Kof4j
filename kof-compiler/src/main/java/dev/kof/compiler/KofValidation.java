package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.validation} (G4).
 *
 * Intention-first: {@code validation.required(name)}, {@code validation.email(email)} etc.
 * Maps to {@code kof_validation_*} runtime functions on each backend.
 * All validation predicates are available on JVM / Native / JS — no target gap.
 */
public final class KofValidation {

    private KofValidation() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type BOOL = Type.PrimitiveType.BOOL;
    private static final Type INT = Type.PrimitiveType.INT;

    static final List<String> NAMESPACES = List.of("validation");

    static boolean isValidationNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record ValidationCall(String function, Type returnType, List<Type> parameterTypes) {}

    static ValidationCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"validation".equals(namespace)) return null;
        int argc = argTypes.size();
        return switch (name) {
            case "required" -> argc == 1
                    ? new ValidationCall("kof_validation_required", BOOL, List.of(STR)) : null;
            case "notBlank" -> argc == 1
                    ? new ValidationCall("kof_validation_notBlank", BOOL, List.of(STR)) : null;
            case "minLength" -> argc == 2
                    ? new ValidationCall("kof_validation_minLength", BOOL, List.of(STR, INT)) : null;
            case "maxLength" -> argc == 2
                    ? new ValidationCall("kof_validation_maxLength", BOOL, List.of(STR, INT)) : null;
            case "lengthBetween" -> argc == 3
                    ? new ValidationCall("kof_validation_lengthBetween", BOOL, List.of(STR, INT, INT)) : null;
            case "isEmail" -> argc == 1
                    ? new ValidationCall("kof_validation_isEmail", BOOL, List.of(STR)) : null;
            case "isUrl" -> argc == 1
                    ? new ValidationCall("kof_validation_isUrl", BOOL, List.of(STR)) : null;
            case "matches" -> argc == 2
                    ? new ValidationCall("kof_validation_matches", BOOL, List.of(STR, STR)) : null;
            case "isInt" -> argc == 1
                    ? new ValidationCall("kof_validation_isInt", BOOL, List.of(STR)) : null;
            case "isLong" -> argc == 1
                    ? new ValidationCall("kof_validation_isLong", BOOL, List.of(STR)) : null;
            case "inRange" -> argc == 3
                    ? new ValidationCall("kof_validation_inRange", BOOL, List.of(INT, INT, INT)) : null;
            case "min" -> argc == 2
                    ? new ValidationCall("kof_validation_min", BOOL, List.of(INT, INT)) : null;
            case "max" -> argc == 2
                    ? new ValidationCall("kof_validation_max", BOOL, List.of(INT, INT)) : null;
            // S5 (STDLIB): documentos BR — dígitos extraídos (não-dígitos
            // ignorados), algoritmos de dígito verificador módulo 11.
            // S12 (STDLIB): pontuação BR — 11 dígitos => DDD.DDD.DDD-DD (CPF),
            // 8 => DDDDD-DDDD (CEP); null/fora-de-11/8 => original (no-op,
            // nunca lança — face leniente da lane, idêntico a isCpf/isCep).
            case "formatCpf", "formatCep" -> argc == 1
                    ? new ValidationCall("kof_validation_" + name, STR, List.of(STR)) : null;
            case "isCpf", "isCnpj", "isCep", "isPis" -> argc == 1
                    ? new ValidationCall("kof_validation_" + name, BOOL, List.of(STR)) : null;
            // S6a (STDLIB): predicados de rede — dotted-quad / MAC (6 hex com
            // separador : ou -) / porta 1..65535. Sem ambiguidade de design.
            case "isIpv4", "isMac" -> argc == 1
                    ? new ValidationCall("kof_validation_" + name, BOOL, List.of(STR)) : null;
            case "isPort" -> argc == 1
                    ? new ValidationCall("kof_validation_isPort", BOOL, List.of(INT)) : null;
            // S6b (STDLIB): Luhn (dígitos extraídos, 12..19, soma*alternada%10).
            case "isCreditCard" -> argc == 1
                    ? new ValidationCall("kof_validation_isCreditCard", BOOL, List.of(STR)) : null;
            case "isIpv6" -> argc == 1
                    ? new ValidationCall("kof_validation_isIpv6", BOOL, List.of(STR)) : null;
            // S6c (STDLIB): domínio — subconjunto RFC 1123 declarado (escopo
            // v1, idem isIpv6): labels [A-Za-z0-9-] 1..63 sem hyphen em
            // ponta; >=2 labels; TLD >=2 só letras; total<=253; sem ponto
            // final, sem underscore, sem IDN (punycode xn-- passa: é ASCII).
            case "isDomain" -> argc == 1
                    ? new ValidationCall("kof_validation_isDomain", BOOL, List.of(STR)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(String function, Target target) {
        return true; // all validation predicates on JVM/Native/JS
    }

    static String gapCode(String function) {
        return "VAL001";
    }

    private static boolean isString(Type t) {
        return t == STR || "String".equals(t.toString()) || t.toString().contains("String");
    }

    private static boolean isInt(Type t) {
        return t == INT || t == Type.PrimitiveType.INT || "int".equals(t.toString()) || "Int".equals(t.toString());
    }
}
