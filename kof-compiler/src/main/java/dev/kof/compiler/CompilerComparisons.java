package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers de comparação (mapComparison/invert), shortcuts numéricos e
 * detecção de retorno (hasReturnValue).
 */
public final class CompilerComparisons {

    private CompilerComparisons() {}

    /** Unknown OU Nullable(Unknown): o valor pode ser null (get sem pin). */
    private static boolean isMaybeNullType(Type t) {
        return t instanceof Type.UnknownType
                || (t instanceof Type.NullableType nt && nt.inner() instanceof Type.UnknownType);
    }

    static boolean isComparisonShortcut(CompilerDriver driver, BinaryExpr bin, List<IRLocalVariable> locals) {
        if (!TypeMetrics.isComparisonOp(bin.operator())) return false;
        if ("==".equals(bin.operator()) || "!=".equals(bin.operator())) {
            Type left = ExpressionTyper.inferExprType(driver, bin.left(), locals);
            Type right = ExpressionTyper.inferExprType(driver, bin.right(), locals);
            if (Type.isString(left) || Type.isString(right)) return false;
            // bug 188: record == record (ou qualquer record em ==) compara CONTEÚDO via .equals()
            // desativar shortcut para não emitir if_acmpeq direto
            if (CompilerTypes.isRecordType(left, driver.currentUnit, driver.semanticAnalyzer)
                    || CompilerTypes.isRecordType(right, driver.currentUnit, driver.semanticAnalyzer)) {
                return false;
            }
            // enum == enum: D-ENUM207 — as constantes são INSTÂNCIAS (singletons
            // de <clinit>), então a igualdade é por IDENTIDADE (if_acmp), não por
            // conteúdo String. Deixa o caminho de referência assumir.
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
        // D-NULL-INTENT/N1: `==`/`!=` entre Nullable(primitivo) GENUÍNO (não
        // map-miss — ex. `a == b` com `a`/`b` vindos de retorno `T?`) é
        // comparação de REFERÊNCIA — o valor já chega boxed de verdade, não
        // pode desembrulhar p/ numérico (o `if (a==b)` shortcut emitiria
        // if_icmpeq sobre uma referência → VerifyError).
        boolean eqNe = "==".equals(bin.operator()) || "!=".equals(bin.operator());
        boolean leftGenuine = isGenuineNullablePrimitive(driver, bin.left(), left, locals);
        boolean rightGenuine = isGenuineNullablePrimitive(driver, bin.right(), right, locals);
        if (eqNe && (leftGenuine || rightGenuine)) {
            return leftGenuine ? left : right;
        }
        // T? desembrulha: Nullable(primitivo) é numérico (unbox com guard
        // do kof_map_get), Nullable(referência) é referência (SG-008/bug 87)
        if (left instanceof Type.NullableType nl) left = nl.inner();
        if (right instanceof Type.NullableType nr) right = nr.inner();
        if (TypeMetrics.isNumeric(left) && TypeMetrics.isNumeric(right)) {
            return TypeMetrics.commonNumericType(left, right);
        }
        // Unknown/Nullable(Unknown) vs primitivo (get de mapOf() sem pin
        // vs literal int): o lado nullable só pode ser null (miss) →
        // comparação de referência com o primitivo BOXADO (espelha o
        // interpretador, Objects.equals). Sem isso o default INT emitia
        // if_icmp* sobre null → VerifyError (SG-008/bug 87).
        if ((isMaybeNullType(left) && TypeMetrics.isPrimitiveType(right))
                || (isMaybeNullType(right) && TypeMetrics.isPrimitiveType(left))) {
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        // comparação contra literal null é sempre referência (if_acmp*);
        // quando o outro lado é Unknown (get de Map, etc.) marca como Object
        if (isNullLiteral(bin.left()) || isNullLiteral(bin.right())) {
            Type other = isNullLiteral(bin.left()) ? right : left;
            if (other instanceof Type.ClassType || other instanceof Type.ArrayType
                    || other instanceof Type.TypeVariable) {
                return other;
            }
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        // referências conhecidas (String vs String, record vs record):
        // preserva o tipo para o backend emitir if_acmp*
        if (left instanceof Type.ClassType || left instanceof Type.ArrayType
                || left instanceof Type.TypeVariable) {
            return left;
        }
        if (right instanceof Type.ClassType || right instanceof Type.ArrayType
                || right instanceof Type.TypeVariable) {
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
     * §125 (decisão da mantenedora 12/09, opção A): `return null` em função
     * Nullable(primitivo) vale o DEFAULT do primitivo — mesmo precedente do
     * map-miss (SG-008/bug-87). `StatementLowerer` cai no `else` do ReturnStmt
     * (defaultValueOp, que já desempacota Nullable(primitivo)) em vez de
     * emitir aconst_null + unbox-de-Unknown → Object.intValue (VerifyError
     * no JVM, NoSuchMethodError Integer.valueOf/1 no interpretador).
     */
    static boolean isNullablePrimNullReturn(ReturnStmt ret, Type returnType) {
        return ret.value() != null && isNullLiteral(ret.value())
                && returnType instanceof Type.NullableType nt
                && nt.inner() instanceof Type.PrimitiveType;
    }

    /**
     * §125 (decisão A) extensão: `Int? f() = if (c) x else null`,
     * `Int? f() = switch { case 1 -> 1 default -> null }` e
     * `Int? v = if (c) x else null` — o `null` NÃO é literal no topo do
     * ReturnStmt/VarDecl (é um RAMO do if/switch), então o fold
     * `isNullablePrimNullReturn` não dispara e o ramo null cai no join
     * heterogêneo (branchTypeOrNullAsRef → Object) → ramo primitivo é
     * boxeado → `ireturn`/`istore` sobre referência (VerifyError JVM,
     * `Integer.valueOf/1` no interpretador) enquanto Native/JS imprimem o
     * default. Aqui: se o TIPO DE DESTINO é Nullable(primitivo), reescreve
     * CADA ramo `null` (profundo, só if/switch) p/ o default do primitivo —
     * o MESMO valor que `defaultValueOp`/map-miss já produzem — de modo que
     * o join deixe de ser heterogêneo e os 4 targets convirjam no contrato
     * congelado (opção A: null de primitivo = default). Não dispara p/
     * destino não-Nullable(prim) (ex.: `println(if (c) 1 else null)` —
     * semântica de expressão standalone, intocada: zero regressão).
     */
    static ExpressionNode foldNullablePrimBranches(ExpressionNode e, Type destType) {
        // D-NULL-INTENT/N1 (mantenedora 15/09, e04f10ff): §125 opção A
        // REVOGADA — Nullable(primitivo) agora é boxed e carrega null de
        // verdade, então um ramo `null` de if/switch NÃO colapsa mais para o
        // default quando o destino é Nullable(primitivo) (o join heterogêneo
        // já é tratado por ExpressionTyper.branchTypeOrNullAsRef/boxesOwnBranches
        // — cada ramo primitivo é boxeado in-branch, o ramo null já é
        // referência). Destino NÃO-nullable (`Int f() = if(c) x else null`)
        // continua colapsando — gap pré-existente fora do escopo desta unidade.
        if (e == null) return e;
        if (!(destType instanceof Type.PrimitiveType p) || Type.isVoid(p)) return e;
        return foldNullBranches(e, p);
    }

    /**
     * D-NULL-INTENT/N1: distingue um `Nullable(primitivo)` GENUÍNO (retorno/
     * local/param — boxed, null real) de um `Nullable(primitivo)` vindo de
     * `Map.get()` (SG-008/bug-87 — valor SEMPRE cru, default-on-miss, jamais
     * boxed, congelado/fora de escopo). Só `get()` produz esse shape
     * (CollectionMethodTyper: `put`/`remove` devolvem o valueType NU, não
     * Nullable) — dispatch por FORMA da chamada, não por tipo, para não
     * confundir os dois em nenhum guard de boxing/comparação.
     */
    static boolean isCollectionMissSource(CompilerDriver driver, ExpressionNode e, List<IRLocalVariable> locals) {
        if (!(e instanceof MethodCallExpr mc) || mc.receiver() == null || !"get".equals(mc.methodName())) {
            return false;
        }
        Type recvType = ExpressionTyper.inferExprType(driver, mc.receiver(), locals);
        return BuiltinTypes.isMap(recvType);
    }

    /**
     * D-NULL-INTENT/N1: `t` é um `Nullable(primitivo)` já BOXED de verdade
     * (retorno de função, local, param — nunca precisa de box de novo) — a
     * negação exata do gap que `isCollectionMissSource` cobre. Usar em todo
     * guard `TypeMetrics.isPrimitiveType(t)`/`isMaybeNullType(t)` que decide
     * "preciso boxar isto" — nunca em guards de largura/opcode (esses já
     * tratam Nullable(primitivo) como referência incondicionalmente, §0).
     */
    static boolean isGenuineNullablePrimitive(CompilerDriver driver, ExpressionNode e, Type t,
            List<IRLocalVariable> locals) {
        return t instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType
                && !isCollectionMissSource(driver, e, locals);
    }

    private static ExpressionNode foldNullBranches(ExpressionNode e, Type.PrimitiveType prim) {
        if (isNullLiteral(e)) return defaultLiteral(prim, ((LiteralExpr) e).position());
        if (e instanceof IfExpr ie) {
            return new IfExpr(ie.position(), ie.condition(),
                    foldNullBranches(ie.thenExpr(), prim), foldNullBranches(ie.elseExpr(), prim));
        }
        if (e instanceof SwitchExpr se) {
            List<SwitchExprCase> cs = new ArrayList<>();
            for (SwitchExprCase c : se.cases()) {
                cs.add(new SwitchExprCase(c.position(), c.value(),
                        foldNullBranches(c.body(), prim)));
            }
            return new SwitchExpr(se.position(), se.expression(), cs,
                    foldNullBranches(se.defaultValue(), prim));
        }
        return e;
    }

    static LiteralExpr defaultLiteral(Type.PrimitiveType prim, SourcePosition pos) {
        return switch (Type.canonicalPrimitiveName(prim.name())) {
            case "long" -> new LiteralExpr(pos, ConcreteLiteralKind.LONG, "0");
            case "float" -> new LiteralExpr(pos, ConcreteLiteralKind.FLOAT, "0.0f");
            case "double" -> new LiteralExpr(pos, ConcreteLiteralKind.DOUBLE, "0.0");
            case "bool", "boolean" -> new LiteralExpr(pos, ConcreteLiteralKind.BOOLEAN, "false");
            default -> new LiteralExpr(pos, ConcreteLiteralKind.INT, "0");
        };
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
        Type leftT = ExpressionTyper.inferExprType(driver, bin.left(), locals);
        Type rightT = ExpressionTyper.inferExprType(driver, bin.right(), locals);
        // lado "Unknown-ou-Nullable(Unknown)" pode conter null (get de
        // mapOf() sem pin) — o primitivo oposto é boxado (comparação vira
        // referência; espelha o interpretador, Objects.equals)
        boolean leftMaybeNull = isMaybeNullType(leftT);
        boolean rightMaybeNull = isMaybeNullType(rightT);
        localIdx = ExpressionLowerer.emitExpression(driver, bin.left(), ops, owner, localIdx, locals);
        // rightMaybeNull: o left (na pilha) é primitivo → boxa ele AGORA
        // (antes do emit do right, que empilha por cima)
        if (rightMaybeNull && TypeMetrics.isPrimitiveType(leftT)) {
            TypeEmitter.boxPrimitive(ops, leftT);
        }
        driver.emitWideningIfNeeded(ops, leftT, common);
        localIdx = ExpressionLowerer.emitExpression(driver, bin.right(), ops, owner, localIdx, locals);
        // leftMaybeNull: o right (acabou de emitir, topo da pilha) é primitivo
        // → boxa ele DEPOIS do emit
        if (leftMaybeNull && TypeMetrics.isPrimitiveType(rightT)) {
            TypeEmitter.boxPrimitive(ops, rightT);
        }
        driver.emitWideningIfNeeded(ops, rightT, common);
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