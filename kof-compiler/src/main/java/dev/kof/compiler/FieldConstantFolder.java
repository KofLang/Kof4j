package dev.kof.compiler;

/**
 * Dobra inicializadores de campo em tempo de compilação. Devolve o valor
 * (Integer/Long/Float/Double/String) ou {@code null} se a expressão não for
 * constante (chamada, {@code new}, referência). Cobre literais diretos e
 * unários/binários sobre eles — inclusive {@code -1}, {@code 2 + 3} e
 * {@code "a" + "b"}.
 *
 * <p>Motivo (UIW052): os backends compilados NÃO sintetizam {@code <clinit>}
 * para inicializadores não-constantes (o {@code NativeMethodEmitter} até ignora
 * {@code <clinit>} explicitamente) e o front-end só levava o {@code LiteralExpr}
 * direto para {@code initialValue}. Dobrar o que é constante faz o valor viajar
 * como {@code ConstantValue}/dado estático nos 4 targets; o que sobra (runtime)
 * continua documentado como gap.
 */
final class FieldConstantFolder {

    private FieldConstantFolder() {}

    static Object foldConstantExpr(CompilerDriver driver, ExpressionNode expr) {
        if (expr == null) return null;
        if (expr instanceof LiteralExpr lit) {
            return switch (lit.kind()) {
                case ConcreteLiteralKind.INT -> CompilerTypeSupport.parseIntLiteral(lit.value());
                case ConcreteLiteralKind.LONG -> CompilerTypeSupport.parseLongLiteral(lit.value());
                case ConcreteLiteralKind.FLOAT -> Float.parseFloat(CompilerTypeSupport.stripSuffix(lit.value()));
                case ConcreteLiteralKind.DOUBLE -> Double.parseDouble(CompilerTypeSupport.stripSuffix(lit.value()));
                case ConcreteLiteralKind.STRING -> lit.value();
                case ConcreteLiteralKind.BOOLEAN -> Boolean.parseBoolean(lit.value()) ? 1 : 0;
                default -> null;
            };
        }
        if (expr instanceof UnaryExpr ue && ue.prefix()) {
            Object v = foldConstantExpr(driver, ue.operand());
            return v == null ? null : foldUnary(ue.operator(), v);
        }
        if (expr instanceof BinaryExpr be) {
            Object l = foldConstantExpr(driver, be.left());
            Object r = foldConstantExpr(driver, be.right());
            if (l == null || r == null) return null;
            return foldBinary(be.operator(), l, r);
        }
        return null;
    }

    private static Object foldUnary(String op, Object v) {
        return switch (op) {
            case "-" -> v instanceof Double d ? (Object) (-d)
                    : v instanceof Float f ? (Object) (-f)
                    : v instanceof Long l ? (Object) (-l)
                    : v instanceof Integer i ? (Object) (-i) : null;
            case "+" -> v;
            case "~" -> v instanceof Long l ? (Object) (~l)
                    : v instanceof Integer i ? (Object) (~i) : null;
            case "!" -> v instanceof Integer i ? (Object) (i == 0 ? 1 : 0) : null;
            default -> null;
        };
    }

    private static Object foldBinary(String op, Object l, Object r) {
        if ("+".equals(op) && l instanceof String ls && r instanceof String rs) return ls + rs;
        if (!(l instanceof Number ln) || !(r instanceof Number rn)) return null;
        if (ln instanceof Double || rn instanceof Double) {
            double a = ln.doubleValue(), b = rn.doubleValue();
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                case "%" -> a % b;
                default -> null;
            };
        }
        if (ln instanceof Float || rn instanceof Float) {
            float a = ln.floatValue(), b = rn.floatValue();
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                case "%" -> a % b;
                default -> null;
            };
        }
        if (ln instanceof Long || rn instanceof Long) {
            long a = ln.longValue(), b = rn.longValue();
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> b == 0 ? null : a / b;
                case "%" -> b == 0 ? null : a % b;
                case "&" -> a & b;
                case "|" -> a | b;
                case "^" -> a ^ b;
                case "<<" -> a << (b & 63);
                case ">>" -> a >> (b & 63);
                case ">>>" -> a >>> (b & 63);
                default -> null;
            };
        }
        int a = ln.intValue(), b = rn.intValue();
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b == 0 ? null : a / b;
            case "%" -> b == 0 ? null : a % b;
            case "&" -> a & b;
            case "|" -> a | b;
            case "^" -> a ^ b;
            case "<<" -> a << (b & 31);
            case ">>" -> a >> (b & 31);
            case ">>>" -> a >>> (b & 31);
            case "==" -> a == b ? 1 : 0;
            case "!=" -> a != b ? 1 : 0;
            case "<" -> a < b ? 1 : 0;
            case "<=" -> a <= b ? 1 : 0;
            case ">" -> a > b ? 1 : 0;
            case ">=" -> a >= b ? 1 : 0;
            default -> null;
        };
    }

    /** Coage o valor dobrado ao tipo do campo (o {@code ConstantValue} exige o tipo). */
    static Object coerceFieldConstant(Object v, Type fieldType) {
        if (v == null) return null;
        if (fieldType instanceof Type.PrimitiveType pt) {
            if (!(v instanceof Number n)) return null;
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "long" -> n.longValue();
                case "double" -> n.doubleValue();
                case "float" -> n.floatValue();
                case "int", "short", "byte", "char", "bool" -> n.intValue();
                default -> null;
            };
        }
        if (v instanceof String s) return Type.isString(fieldType) ? s : null;
        return null;
    }
}
