package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do dispatch json.encode/json.decode (receiver identificador "json").
 */
final class ExpressionJsonCallLowerer {

    private ExpressionJsonCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals) {
    if ("encode".equals(mc.methodName()) && mc.arguments().size() == 1) {
        Type argType = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        if (!driver.jsonSupported(argType, false)) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        List<Type> paramTypes = List.of(argType);
        if (BuiltinTypes.isList(argType)) {
            int tag = JsonDispatch.listTag(driver.listElementType(argType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, tag));
            paramTypes = List.of(argType, Type.PrimitiveType.INT);
        } else if (driver.target.isNative()
                && argType instanceof Type.ClassType ect
                && !BuiltinTypes.isString(argType)
                // List/Map têm caminho builtin próprio
                && !BuiltinTypes.isList(argType) && !BuiltinTypes.isMap(argType)) {
            // JSN002: compoe o JSON em compile-time a partir
            // dos campos conhecidos (sem reflection, sem
            // walker generico) — so primitivas testadas.
            String cn2 = ect.packageName().isEmpty()
                    ? ect.name() : ect.packageName() + "." + ect.name();
            java.util.List<String[]> flds = driver.classFieldsOrdered(cn2);
            // guarda o objeto em local temporario
            ops.add(new KofStoreLocal(argType, localIdx));
            locals.add(new IRLocalVariable(localIdx, "#jsonobj", argType));
            int objTmp = localIdx;
            localIdx += TypeMetrics.isDoubleWidth(argType) ? 2 : 1;
            // acc = "{"
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "{"));
            for (int fi = 0; fi < flds.size(); fi++) {
                String fname = flds.get(fi)[0];
                Type ftype = CompilerTypes.toType(flds.get(fi)[1], driver.currentUnit);
                if (fi > 0) {
                    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ","));
                    ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                            List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                            BuiltinTypes.STRING, KofCallKind.FUNCTION));
                }
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                        "\"" + fname + "\":"));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                // valor do campo
                ops.add(new KofLoadLocal(argType, objTmp));
                ops.add(new KofLoadField(argType, fname, ftype));
                switch (ftype instanceof Type.PrimitiveType fp
                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                    case "long":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_long_to_string",
                                List.of(Type.PrimitiveType.LONG), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "bool":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_bool_to_string",
                                List.of(Type.PrimitiveType.BOOL), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "int":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_int_to_string",
                                List.of(Type.PrimitiveType.INT), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "double":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_double_to_string",
                                List.of(Type.PrimitiveType.DOUBLE), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    case "float":
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_float_to_string",
                                List.of(Type.PrimitiveType.FLOAT), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                        break;
                    default: // string
                        ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_quote",
                                List.of(BuiltinTypes.STRING), BuiltinTypes.STRING,
                                KofCallKind.FUNCTION));
                }
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "}"));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            return localIdx;
        }
        ops.add(new KofCall(argType, JsonDispatch.encodeFunction(argType), paramTypes,
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
    } else if ("decode".equals(mc.methodName()) && mc.arguments().size() == 1
            && !mc.typeArguments().isEmpty()) {
        Type targetType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        if (!driver.jsonSupported(targetType, true)) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        String decodeFn = JsonDispatch.decodeFunction(targetType, driver.listElementType(targetType));
        List<Type> decodeParams = List.of(BuiltinTypes.STRING);
        if (BuiltinTypes.isList(targetType)
                && driver.listElementType(targetType) instanceof Type.ClassType ect
                && !BuiltinTypes.isString(ect)) {
            // decode<List<T>> where T is a user class: bind
            // each element to T (the element type survives the
            // generic erasure through the type system).
            decodeFn = "kof_json_decode_object_list";
            decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
            String className = ect.packageName().isEmpty()
                    ? ect.name() : ect.packageName() + "." + ect.name();
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, className));
        } else if (driver.target.isNative()
                && targetType instanceof Type.ClassType dct
                && !BuiltinTypes.isString(targetType)
                // List/Map têm caminho builtin próprio
                && !BuiltinTypes.isList(targetType) && !BuiltinTypes.isMap(targetType)) {
            // JSN002: decode composto — find_value por campo +
            // decoders escalares + construtor canonico
            String cn3 = dct.packageName().isEmpty()
                    ? dct.name() : dct.packageName() + "." + dct.name();
            java.util.List<String[]> flds = driver.classFieldsOrdered(cn3);
            // json em local temporario
            ops.add(new KofStoreLocal(BuiltinTypes.STRING, localIdx));
            locals.add(new IRLocalVariable(localIdx, "#jsonsrc", BuiltinTypes.STRING));
            int jTmp = localIdx;
            localIdx += 1;
            List<Type> ctorTypes = new ArrayList<>();
            ops.add(new KofNewObject(targetType,
                    flds.stream().map(f -> CompilerTypes.toType(f[1], driver.currentUnit)).toList()));
            ops.add(new KofDup());
            for (String[] f : flds) {
                Type ft = CompilerTypes.toType(f[1], driver.currentUnit);
                ctorTypes.add(ft);
                ops.add(new KofLoadLocal(BuiltinTypes.STRING, jTmp));
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f[0]));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_json_find_value",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
                String dec = switch (ft instanceof Type.PrimitiveType fp
                        ? Type.canonicalPrimitiveName(fp.name()) : "") {
                    case "int", "char", "byte", "short" -> "kof_json_decode_int";
                    case "long" -> "kof_json_decode_long";
                    case "bool" -> "kof_json_decode_bool";
                    default -> "kof_json_decode_string";
                };
                ops.add(new KofCall(targetType, dec,
                        List.of(BuiltinTypes.STRING), ft, KofCallKind.FUNCTION));
            }
            ops.add(new KofCall(targetType, "<init>", ctorTypes,
                    Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            return localIdx;
        }
        ops.add(new KofCall(targetType, decodeFn, decodeParams,
                targetType, KofCallKind.FUNCTION));
    }
    return localIdx;
    }
}