package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers de comparação (mapComparison/invert), shortcuts numéricos e
 * detecção de retorno (hasReturnValue).
 */
final class CompilerComparisons {

    private CompilerComparisons() {}

    static boolean isComparisonShortcut(CompilerDriver driver, BinaryExpr bin, List<IRLocalVariable> locals) {
        if (!TypeMetrics.isComparisonOp(bin.operator())) return false;
        if ("==".equals(bin.operator()) || "!=".equals(bin.operator())) {
            Type left = ExpressionTyper.inferExprType(driver, bin.left(), locals);
            Type right = ExpressionTyper.inferExprType(driver, bin.right(), locals);
            if (Type.isString(left) || Type.isString(right)) return false;
            // enum == enum compara conteúdo (string) — nunca identidade
            if (CompilerTypes.isEnumType(left, driver.currentUnit) || CompilerTypes.isEnumType(right, driver.currentUnit)) return false;
            // primitivo vs null → constante (caminho da cadeia binária)
            boolean leftNull = bin.left() instanceof LiteralExpr ll2 && ll2.kind() == ConcreteLiteralKind.NULL;
            boolean rightNull = bin.right() instanceof LiteralExpr rl2 && rl2.kind() == ConcreteLiteralKind.NULL;
            if ((leftNull && TypeMetrics.isPrimitiveType(right)) || (rightNull && TypeMetrics.isPrimitiveType(left))) return false;
        }
        return true;
    }

    /**
     * Operand type of a comparison shortcut: the common numeric type of the
     * two operands (int, long, float or double). The IR carries it so the
     * JVM backend can emit the correct compare instruction.
     */
    static Type comparisonOperandType(CompilerDriver driver, BinaryExpr bin, List<IRLocalVariable> locals) {
        Type left = ExpressionTyper.inferExprType(driver, bin.left(), locals);
        Type right = ExpressionTyper.inferExprType(driver, bin.right(), locals);
        if (TypeMetrics.isNumeric(left) && TypeMetrics.isNumeric(right)) {
            return TypeMetrics.commonNumericType(left, right);
        }
        // comparação contra literal null é sempre referência (if_acmp*);
        // quando o outro lado é Unknown (get de Map, etc.) marca como Object
        if (isNullLiteral(bin.left()) || isNullLiteral(bin.right())) {
            Type other = isNullLiteral(bin.left()) ? right : left;
            if (other instanceof Type.ClassType || other instanceof Type.ArrayType
                    || other instanceof Type.TypeVariable || other instanceof Type.NullableType) {
                return other;
            }
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        // referências conhecidas (String vs String, record vs record):
        // preserva o tipo para o backend emitir if_acmp*
        if (left instanceof Type.ClassType || left instanceof Type.ArrayType
                || left instanceof Type.TypeVariable || left instanceof Type.NullableType) {
            return left;
        }
        if (right instanceof Type.ClassType || right instanceof Type.ArrayType
                || right instanceof Type.TypeVariable || right instanceof Type.NullableType) {
            return right;
        }
        // ambos UnknownType (ex.: `if (a == b)` com `var a = null`): referência
        // (if_acmp*) — espelha ExpressionBinaryLowerer (bug 36). Unknown nunca
        // surge de int inferido (dá INT), então acmp é seguro.
        if (left instanceof Type.UnknownType && right instanceof Type.UnknownType) {
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        return Type.PrimitiveType.INT;
    }

    static boolean isNullLiteral(ExpressionNode e) {
        return e instanceof LiteralExpr le && le.kind() == ConcreteLiteralKind.NULL;
    }

    /**
     * Emits both operands of a comparison-shortcut condition, widening each
     * to the common numeric type (e.g. `longExpr < 2000` must widen the
     * literal before the compare).
     */
    static int emitComparisonShortcut(CompilerDriver driver, BinaryExpr bin, List<KofOperation> ops,
                                         String owner, int localIdx, List<IRLocalVariable> locals) {
        Type common = CompilerComparisons.comparisonOperandType(driver, bin, locals);
        if (!driver.fpSupportedOnNative(common, bin.position())) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
        driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, bin.left(), locals), common);
        localIdx = ExpressionLowerer.emitExpression(driver, bin.right(), ops, owner, localIdx, locals);
        driver.emitWideningIfNeeded(ops, ExpressionTyper.inferExprType(driver, bin.right(), locals), common);
        return localIdx;
    }

    static KofComparison mapComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.GT;
            case "<" -> KofComparison.LT;
            case ">=" -> KofComparison.GE;
            case "<=" -> KofComparison.LE;
            case "==" -> KofComparison.EQ;
            case "!=" -> KofComparison.NE;
            default -> KofComparison.NE;
        };
    }

    static KofComparison invertComparison(String op) {
        return switch (op) {
            case ">" -> KofComparison.LE;
            case "<" -> KofComparison.GE;
            case ">=" -> KofComparison.LT;
            case "<=" -> KofComparison.GT;
            case "==" -> KofComparison.NE;
            case "!=" -> KofComparison.EQ;
            default -> KofComparison.NE;
        };
    }

    // Int → Long[] slot (I2L) ou Long → Int[] slot (L2I): sem isso o emit
    // do array store usa o opcode do slot com um valor do outro tipo e o
    // verifier rejeita (frame crash / VerifyError "JavaFX").
    static void emitPrimWidenNarrow(CompilerDriver driver, List<KofOperation> ops, ExpressionNode value,
                                     Type elemType, List<IRLocalVariable> locals) {
        Type vt = ExpressionTyper.inferExprType(driver, value, locals);
        if (elemType instanceof Type.PrimitiveType et && vt instanceof Type.PrimitiveType st) {
            if ("long".equals(et.name()) && "int".equals(st.name())) {
                ops.add(new KofUnary(KofUnaryOp.I2L, Type.PrimitiveType.INT));
            } else if ("int".equals(et.name()) && "long".equals(st.name())) {
                ops.add(new KofUnary(KofUnaryOp.L2I, Type.PrimitiveType.LONG));
            }
        }
    }

    static boolean hasReturnValue(CompilerDriver driver, ExpressionNode expr, List<IRLocalVariable> locals) {
        return CompilerComparisons.hasReturnValueInner(driver, expr, locals);
    }

    static boolean hasReturnValueInner(CompilerDriver driver, ExpressionNode expr,
                                              List<IRLocalVariable> locals) {
        if (expr instanceof AssignmentExpr) return false;
        if (expr instanceof MethodCallExpr mc) {
            if ("print".equals(mc.methodName()) || "println".equals(mc.methodName())) return false;
            // cache.* primeiro: cache.delete é void e o nome colide com o
            // File.delete do Io (que o check genérico abaixo não sabe tipar
            // com receiver Unknown) — sem isto o Pop extra diverge o frame
            // idem emit: `cache` pode ser VARIÁVEL LOCAL List (kof_list_add) —
            // só é namespace builtin se não for local/param (frame COMP002:
            // pop duplo em cache.add(...) com local chamado "cache")
            if (mc.receiver() instanceof IdentifierExpr rid && !driver.isLocalVarName(rid.name(), locals)
                    && KofCache.isCacheNamespace(rid.name())) {
                List<Type> cacheArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) cacheArgTypes.add(ExpressionTyper.inferExprType(driver, arg, locals));
                KofCache.CacheCall cc = KofCache.staticCall(mc.methodName(), cacheArgTypes);
                if (cc == null) return true;
                return !(cc.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
            }
            // gpu.*: todas as funções retornam valor (bool/str/int)
            if (mc.receiver() instanceof IdentifierExpr rid && KofGpu.isGpuNamespace(rid.name())) {
                return true;
            }
            if (mc.receiver() != null && KofIo.instanceMethod(Type.UnknownType.UNKNOWN,
                    mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            // List methods that leave a value on the stack (get, remove,
            // size, contains, isEmpty) must be popped at statement level;
            // add/set/clear are already popped by the JVM backend.
            if (mc.receiver() != null && BuiltinTypes.isList(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "size", "length", "count",
                            "contains", "isEmpty" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isMap(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "get", "remove", "put", "size", "length", "count",
                            "contains", "containsKey", "isEmpty", "keys", "values" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() != null && BuiltinTypes.isSet(ExpressionTyper.inferExprType(driver, mc.receiver(), locals))) {
                return switch (mc.methodName()) {
                    case "contains", "isEmpty", "size", "length", "count",
                            "add", "remove" -> true;
                    default -> false;
                };
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofOrm.isOrmNamespace(rid.name())) {
                // todos os orm.* retornam valor (Bool/Object/List/Long) — antes
                // dos checks genéricos (o "delete" também é rota do web)
                return true;
            }
            if (mc.receiver() != null) {
                List<Type> webArgTypes = new ArrayList<>();
                for (ExpressionNode arg : mc.arguments()) webArgTypes.add(ExpressionTyper.inferExprType(driver, arg, List.of()));
                KofWeb.WebCall webCall = KofWeb.instanceMethod(mc.methodName(), webArgTypes);
                if (webCall != null) {
                    return !(webCall.returnType() instanceof Type.PrimitiveType pt && "void".equals(pt.name()));
                }
            }
            if (mc.receiver() instanceof IdentifierExpr rid && KofIo.isConstructor(rid.name())
                    && KofIo.staticMethod(rid.name(), mc.methodName(), mc.arguments().size()) != null) {
                return true;
            }
            if (driver.semanticAnalyzer != null) {
                SymbolTable.MethodSymbol resolved = driver.semanticAnalyzer.getResolvedMethod(mc);
                if (resolved != null) {
                    // add/set/clear de coleção builtin: o JVM backend já
                    // descarta o valor no emit (POP) — um KofPop extra aqui
                    // vira stack underflow no merge de frames (COMP002)
                    String oc = resolved.ownerClass();
                    if (("List".equals(oc) || "ArrayList".equals(oc) || "java/util/List".equals(oc)
                            || "Map".equals(oc) || "HashMap".equals(oc)
                            || "Set".equals(oc) || "HashSet".equals(oc))
                            && ("add".equals(mc.methodName()) || "push".equals(mc.methodName())
                                || "append".equals(mc.methodName()) || "set".equals(mc.methodName())
                                || "clear".equals(mc.methodName()) || "put".equals(mc.methodName()))) {
                        return false;
                    }
                    Type resolvedType = resolved.returnType();
                    if (Type.isVoid(resolvedType)) return false;
                    return !(resolvedType instanceof Type.UnknownType);
                }
            }
            Type t = ExpressionTyper.inferExprType(driver, mc, locals);
            if (t instanceof Type.UnknownType || Type.isVoid(t)) return false;
            // add/push/append/set/clear/put de coleção: o emit do backend
            // já descarta o valor (POP no kof_list_add/kof_map_put) — sem
            // KofPop aqui (underflow no merge de frames, COMP002).
            if (mc.receiver() instanceof IdentifierExpr) {
                String mn = mc.methodName();
                if ("add".equals(mn) || "push".equals(mn) || "append".equals(mn)
                        || "set".equals(mn) || "clear".equals(mn) || "put".equals(mn)) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

}