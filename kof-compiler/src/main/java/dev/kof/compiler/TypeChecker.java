package dev.kof.compiler;

import java.util.List;

/**
 * Checagem de tipos extraída do SemanticAnalyzer (REFACTOR-500 fase 6):
 * assignabilidade, conferência de argumentos, tipos de resultado de
 * operações binárias e literais. Sem estado — diagnostics por parâmetro.
 */
final class TypeChecker {

    private TypeChecker() {}

    static Type inferLiteralType(LiteralExpr lit) {
        return switch (lit.kind()) {
            case ConcreteLiteralKind.INT -> Type.PrimitiveType.INT;
            case ConcreteLiteralKind.LONG -> Type.PrimitiveType.LONG;
            case ConcreteLiteralKind.FLOAT -> Type.PrimitiveType.FLOAT;
            case ConcreteLiteralKind.DOUBLE -> Type.PrimitiveType.DOUBLE;
            case ConcreteLiteralKind.STRING -> BuiltinTypes.STRING;
            case ConcreteLiteralKind.BOOLEAN -> Type.PrimitiveType.BOOL;
            case ConcreteLiteralKind.CHAR -> Type.PrimitiveType.CHAR;
            case ConcreteLiteralKind.NULL -> Type.UnknownType.UNKNOWN;
        };
    }

    static Type inferBinaryResultType(DiagnosticCollector diagnostics, String operator, Type left, Type right) {
        if ("==".equals(operator) || "!=".equals(operator) || "<".equals(operator) ||
                ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }

        if ("&&".equals(operator) || "||".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("instanceof".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("as".equals(operator)) {
            return right;
        }
        if ("!".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if (Type.isString(left) || Type.isString(right)) {
            if ("+".equals(operator)) {
                return BuiltinTypes.STRING;
            }
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to String and " + right, "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.PrimitiveType lp && right instanceof Type.PrimitiveType rp) {
            if ("int".equals(lp.name())) {
                if ("long".equals(rp.name()) || "Long".equals(rp.name())) return Type.PrimitiveType.LONG;
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.INT;
            }
            if ("long".equals(lp.name()) || "Long".equals(lp.name())) {
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.LONG;
            }
            if ("float".equals(lp.name()) || "Float".equals(lp.name())) {
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.FLOAT;
            }
            if ("double".equals(lp.name()) || "Double".equals(lp.name())) {
                return Type.PrimitiveType.DOUBLE;
            }
                if ("bool".equals(lp.name()) || "bool".equals(rp.name())) {
                    if ("+".equals(operator) || "-".equals(operator) || "*".equals(operator) ||
                            "/".equals(operator) || "%".equals(operator)) {
                        if (diagnostics != null) {
                            diagnostics.error("", 0, 0, 0,
                                    "Cannot apply '" + operator + "' to boolean types. Use == or != for comparison.", "SEM002");
                        }
                        return Type.UnknownType.UNKNOWN;
                    }
                }
            return left;
        }
        if (left instanceof Type.ArrayType || right instanceof Type.ArrayType) {
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.UnknownType || right instanceof Type.UnknownType) {
            return Type.UnknownType.UNKNOWN;
        }
        // Aritmética sobre tipo referência (ex.: param de lambda sem anotação
        // → Object) não tem opcode: o emit cairia em IADD sobre referência e a
        // JVM rejeitaria o bytecode (VerifyError). Diagnóstico explícito, nunca
        // fallback silencioso (R6). String + já foi tratado acima.
        if (isArithmeticOp(operator) && (isReferenceType(left) || isReferenceType(right))) {
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to non-numeric type "
                                + (isReferenceType(left) ? left : right)
                                + " (declare o tipo do parâmetro, ex.: (x: Int) -> ...)",
                        "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        return left;
    }

    static boolean isConcurrentHandle(Type t) {
        return t instanceof Type.ClassType ct
                && "kof.concurrent".equals(ct.packageName())
                && "Handle".equals(ct.name());
    }

    static boolean isArithmeticOp(String op) {
        return "+".equals(op) || "-".equals(op) || "*".equals(op)
                || "/".equals(op) || "%".equals(op);
    }

    static boolean isReferenceType(Type t) {
        return t instanceof Type.ClassType || t instanceof Type.FunctionType;
    }

    static void checkArgTypes(DiagnosticCollector diagnostics, String methodName,
                              List<Type> argTypes, List<Type> paramTypes) {
        if (diagnostics == null || paramTypes.isEmpty() && !argTypes.isEmpty()) return;
        if (argTypes.size() != paramTypes.size()) {
            diagnostics.error("", 0, 0, 0,
                    "Wrong number of arguments for '" + methodName + "': expected "
                            + paramTypes.size() + " but got " + argTypes.size(), "SEM013");
            return;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            if (!Type.isUnknown(argTypes.get(i)) && !Type.isUnknown(paramTypes.get(i))
                    && !isAssignable(argTypes.get(i), paramTypes.get(i))) {
                diagnostics.error("", 0, 0, 0,
                        "Argument " + (i + 1) + " of '" + methodName + "': expected "
                                + paramTypes.get(i) + " but got " + argTypes.get(i), "SEM014");
                return;
            }
        }
    }

    static boolean isAssignable(Type from, Type to) {
        if (from == null || to == null) return true;
        if (Type.isUnknown(from) || Type.isUnknown(to)) return true;
        if (from instanceof Type.TypeVariable || to instanceof Type.TypeVariable) return true;
        if (from instanceof Type.NullableType fn) {
            if (to instanceof Type.NullableType tn) return isAssignable(fn.inner(), tn.inner());
            return false;
        }
        if (to instanceof Type.NullableType tn) {
            if (from instanceof Type.NullableType) return isAssignable(((Type.NullableType)from).inner(), tn.inner());
            return isAssignable(from, tn.inner());
        }
        if (from.equals(to)) return true;
        if (from instanceof Type.PrimitiveType fp && to instanceof Type.PrimitiveType tp) {
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            if ("double".equals(fp.name()) && "float".equals(tp.name())) return true;
            int fw = primitiveWidth(fp);
            int tw = primitiveWidth(tp);
            return fw <= tw;
        }
        if (from instanceof Type.FunctionType && to instanceof Type.ClassType) {
            // lambda → interface funcional externa (SAM conversion): a
            // compatibilidade real (aridade/tipos) é validada na emissão
            return true;
        }
        if (from instanceof Type.PrimitiveType && to instanceof Type.ClassType ct
                && "Object".equals(ct.name()) && "java.lang".equals(ct.packageName())) {
            // bug 15: primitivo → Object (auto-boxing no emit) — `Object n = 42`
            return true;
        }
        if (from instanceof Type.PrimitiveType fp
                && "double".equals(fp.name())
                && to instanceof Type.PrimitiveType tp
                && "float".equals(tp.name())) {
            // double → float: o lowering emite D2F; sem isso literais
            // decimais (1000.0) não atribuem a campos Float
            return true;
        }
        if (to instanceof Type.ClassType) {
            return from instanceof Type.ClassType;
        }
        return false;
    }

    static int primitiveWidth(Type.PrimitiveType pt) {
        return switch (pt.name()) {
            case "bool", "Bool" -> 0;
            case "char", "Char" -> 1;
            case "int", "Int", "byte", "short" -> 2;
            case "long", "Long" -> 3;
            case "float", "Float" -> 4;
            case "double", "Double" -> 5;
            default -> 2;
        };
    }
}
