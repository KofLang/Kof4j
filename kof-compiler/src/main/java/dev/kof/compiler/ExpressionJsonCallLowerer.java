package dev.kof.compiler;

import dev.kof.compiler.jvm.JvmTypeMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering do dispatch json.encode/json.decode (receiver identificador "json").
 */
public final class ExpressionJsonCallLowerer {

    private ExpressionJsonCallLowerer() {}

    static int lower(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
                    String owner, int localIdx, List<IRLocalVariable> locals) {
    if ("encode".equals(mc.methodName()) && mc.arguments().size() == 1) {
        SecretRevealLint.warnIfRevealed(driver, mc.arguments(), "json.encode");
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
        } else if (BuiltinTypes.isMap(argType)) {
            // §106 (decisão 2b): Map -> objeto JSON com chaves sorted; o runtime
            // recebe a tag do VALOR (mesma tabela do elem de List) p/ escolher
            // o encoder tipado (v1 flat: int/string/bool).
            Type mv2 = BuiltinTypes.mapValue(argType);
            int tag = JsonDispatch.listTag(mv2);
            // §284-map (18/09): no NATIVO o slot da familia Int/Long vive em
            // caixa MAGIC — o walker do kof_json_encode_map precisa da tag 7
            // (desembale via kof_box_to_string, mesma tabela do println) para
            // nao JSONificar o PONTEIRO da caixa. List/Set seguem crus.
            if (driver.target.isNative()
                    && CollectionLoweringSupport.mapBoxablePrim(
                        mv2 instanceof Type.NullableType nt ? nt.inner() : mv2)) {
                tag = 7;
            }
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
        // §538: um type-argument que é um PARÂMETRO DE TIPO ABERTO do escopo
        // (`json.decode<T>` dentro de `f<T>`) não tem token de tipo em runtime.
        // Antes o lowerer escrevia o NOME do type-var no símbolo e no cast
        // (`kof_json_decode_T` + `checkcast T`) → bytecode inválido. Recusa
        // explícita em compile-time (R6), nunca emitir símbolo inexistente.
        Type openCheck = CompilerTypes.resolveWithTypeParams(mc.typeArguments().get(0),
                driver.currentTypeParams, driver.currentUnit, driver.semanticAnalyzer);
        if (hasOpenTypeParam(openCheck)) {
            if (driver.currentDiagnostics != null) {
                SourcePosition p = mc.position();
                driver.currentDiagnostics.error(p != null ? p.file() : "",
                        p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                        "json.decode: an open type parameter has no runtime type token"
                                + " — use a concrete type (the decoder needs a Class to build the value)",
                        "JSN005");
            }
            return localIdx;
        }
        Type targetType = CompilerTypes.toType(mc.typeArguments().get(0), driver.currentUnit);
        if (!driver.jsonSupported(targetType, true)) {
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        Type listElementType = driver.listElementType(targetType);
        String decodeFn = JsonDispatch.decodeFunction(targetType, listElementType);
        List<Type> decodeParams = List.of(BuiltinTypes.STRING);
        if (BuiltinTypes.isList(targetType)
                && driver.listElementType(targetType) instanceof Type.ClassType ect
                && !BuiltinTypes.isString(ect)) {
            if (BuiltinTypes.isList(ect) || BuiltinTypes.isMap(ect) || BuiltinTypes.isSet(ect)) {
                // decode<List<List<T>>>: o className do elemento seria o nome
                // do builtin ("kof.List") → Class.forName crasha em runtime
                // (CNFE silencioso, violation R6). Gap honesto até o decoder
                // recursivo existir (JSN004).
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = mc.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "json.decode: nested collections (List<List<T>>) are not supported yet (JSN004)",
                            "JSN004");
                }
                return localIdx;
            }
            if (driver.target.isNative()) {
                // decode<List<T>> (T = classe/record de usuário) no Native:
                // o runtime nativo não tem kof_json_decode_object_list nem
                // decoder real de lista de records (kof_json_decode_record_list
                // é stub). Gap honesto (R6): diagnosticar em vez de emitir
                // função inexistente (link fail) ou stub que retorna lixo.
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = mc.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "json.decode: List<Record>/List<Class> not supported on the Native target yet (JSN004); use JVM/JS/interpreted",
                            "JSN004");
                }
                return localIdx;
            }
            // decode<List<T>> where T is a user class: bind
            // each element to T (the element type survives the
            // generic erasure through the type system).
            decodeFn = "kof_json_decode_object_list";
            decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
            String className = ect.packageName().isEmpty()
                    ? ect.name() : ect.packageName() + "." + ect.name();
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, className));
        } else if (BuiltinTypes.isMap(targetType)) {
            // §103.1 (#103): decode<Map<String,T>>. Antes caía no default
            // JsonDispatch → "kof_json_decode_" + sanitize("Map") =
            // kof_json_decode_Map, método que NUNCA existiu no runtime
            // (NoSuchMethodError em runtime, passa no check). Roteia pelo
            // tipo do VALOR: classe de usuário → object_map (binda cada
            // valor); escalável/string → map (HashMap cru do parser).
            Type vt = BuiltinTypes.mapValue(targetType);
            boolean valueIsClass = vt instanceof Type.ClassType vct
                    && !BuiltinTypes.isString(vct)
                    && !BuiltinTypes.isList(vct) && !BuiltinTypes.isMap(vct);
            if (driver.target.isNative()) {
                // Gap honesto (R6): o runtime nativo não tem decoder de mapa
                // (nem escalável nem de classes). Sem este ramo, Map<String,T>
                // caía no default JsonDispatch → kof_json_decode_Map e dava
                // link-fail / decodificava errado. Espelha List<Record>.
                if (driver.currentDiagnostics != null) {
                    SourcePosition p = mc.position();
                    driver.currentDiagnostics.error(p != null ? p.file() : "",
                            p != null ? p.line() : 0, p != null ? p.column() : 0, 0,
                            "json.decode: Map<String,T> not supported on the Native target yet (JSN004); use JVM/JS/interpreted",
                            "JSN004");
                }
                return localIdx;
            }
            if (valueIsClass) {
                decodeFn = "kof_json_decode_object_map";
                decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
                Type.ClassType vct = (Type.ClassType) vt;
                String vcn = vct.packageName().isEmpty()
                        ? vct.name() : vct.packageName() + "." + vct.name();
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, vcn));
            } else if (driver.target == Target.JVM
                    && vt instanceof Type.ClassType vct2
                    && (BuiltinTypes.isMap(vct2) || BuiltinTypes.isList(vct2))) {
                // #633: o VALOR do Map é ele mesmo uma coleção — o decoder
                // compile-time não conhece a forma aninhada; passa a ASSINATURA
                // genérica e o binder recursivo resolve (Map<K, Map<K,V>>,
                // List<T>, ...). No interpretador (target JVM + `interpreting`)
                // o runtime intercepta `kof_json_decode_typed` e refaz o bind
                // sobre as classes Kof (KofObj), via a mesma assinatura.
                decodeFn = "kof_json_decode_typed";
                decodeParams = List.of(BuiltinTypes.STRING, BuiltinTypes.STRING);
                String sig = JvmTypeMapper.toGenericSignature(targetType);
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, sig));
            } else {
                decodeFn = "kof_json_decode_map";
                decodeParams = List.of(BuiltinTypes.STRING);
            }
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
                    default -> null; // §516: String ja vem pronta do
                };               // find_value (sem aspas, desescapado);
                if (dec != null) // decode_string so serve p/ literal
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

    /** §538: o tipo (recursivo, inclui args de `ClassType`) carrega um
     *  type-param aberto ({@link Type.TypeVariable}/{@link Type.WildcardType})? */
    private static boolean hasOpenTypeParam(Type t) {
        if (t == null) return false;
        if (t instanceof Type.NullableType n) return hasOpenTypeParam(n.inner());
        if (t instanceof Type.TypeVariable || t instanceof Type.WildcardType) return true;
        if (t instanceof Type.ArrayType a) return hasOpenTypeParam(a.componentType());
        if (t instanceof Type.ClassType ct) {
            for (Type arg : ct.typeArguments()) {
                if (hasOpenTypeParam(arg)) return true;
            }
        }
        return false;
    }
}