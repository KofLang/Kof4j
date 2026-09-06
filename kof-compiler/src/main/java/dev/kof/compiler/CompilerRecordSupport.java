package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Geração de métodos sintéticos para records: toString, equals, construtor.
 */
final class CompilerRecordSupport {

    private CompilerRecordSupport() {}

    static IRMethod buildRecordToStringMethod(CompilerDriver driver, String internalName,
                                RecordDeclarationNode rec,
                                               List<IRField> fields, List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        String simpleName = internalName.contains("/")
                ? internalName.substring(internalName.lastIndexOf('/') + 1) : internalName;
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        // "Nome[x=valor, y=valor]" — concat: literal, campo, separador...
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, simpleName + "["));
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, f.name() + "="));
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            if (!Type.isString(f.type())) TypeEmitter.boxPrimitive(ops, f.type());
            ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                    List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
            ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                    List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                    BuiltinTypes.STRING, KofCallKind.FUNCTION));
            if (i + 1 < fields.size()) {
                ops.add(new KofLoadLiteral(BuiltinTypes.STRING, ", "));
                ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                        List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        BuiltinTypes.STRING, KofCallKind.FUNCTION));
            }
        }
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, "]"));
        ops.add(new KofCall(BuiltinTypes.STRING, "valueOf",
                List.of(Type.UnknownType.UNKNOWN), BuiltinTypes.STRING, KofCallKind.STATIC));
        ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_concat",
                List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                BuiltinTypes.STRING, KofCallKind.FUNCTION));
        ops.add(new KofReturn(BuiltinTypes.STRING));
        return new IRMethod("toString", BuiltinTypes.STRING, List.of(), AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    /**
     * equals() nativo de record: compara todos os componentes (bug 11 native).
     */
    static IRMethod buildRecordEqualsMethod(CompilerDriver driver, String internalName,
                            List<IRField> fields,
                                             List<String> typeParams) {
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        locals.add(new IRLocalVariable(0, "this", ownerType));
        locals.add(new IRLocalVariable(1, "other", ownerType));
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            ops.add(new KofLoadLocal(ownerType, 1));
            ops.add(new KofLoadField(ownerType, f.name(), f.type()));
            ops.add(new KofBinary(KofBinaryOp.EQ, f.type()));
            // AND acumula a partir do 2º campo: [bool0] → (bool0 AND bool1)
            // O AND só após a 2ª comparação ter empilhado o 2º bool.
            if (i > 0) {
                ops.add(new KofBinary(KofBinaryOp.AND, Type.PrimitiveType.BOOL));
            }
        }
        if (fields.isEmpty()) {
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 1));
        }
        ops.add(new KofReturn(Type.PrimitiveType.BOOL));
        return new IRMethod("equals", Type.PrimitiveType.BOOL, List.of(ownerType), AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, ops)), locals);
    }

    static IRMethod generateRecordConstructor(CompilerDriver driver, RecordDeclarationNode rec,
                                  String owner) {
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        List<Type> compTypes = rec.components().stream().map(c -> CompilerTypes.resolveWithTypeParams(c.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer)).toList();
        List<KofOperation> ops = new ArrayList<>();
        List<IRLocalVariable> locals = new ArrayList<>();
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        Type superType = new Type.ClassType("java.lang", "Record", List.of());
        locals.add(new IRLocalVariable(0, "this", ownerType));
        if (driver.isJvmTarget()) {


            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofCall(superType, "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
        }
        int localIdx = 1;
        for (RecordComponentNode comp : rec.components()) {
            Type compType = CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
            locals.add(new IRLocalVariable(localIdx, comp.name(), compType));
            ops.add(new KofLoadLocal(ownerType, 0));
            ops.add(new KofLoadLocal(compType, localIdx));
            ops.add(new KofStoreField(ownerType, comp.name(), compType));
            localIdx += TypeMetrics.isDoubleWidth(compType) ? 2 : 1;
        }
        ops.add(new KofReturnVoid());
        return new IRMethod("<init>", Type.PrimitiveType.VOID, compTypes, AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ops)), locals);
    }

    static List<IRMethod> generateRecordDefaultOverloads(CompilerDriver driver,
                                            RecordDeclarationNode rec,
                                            String owner) {
        List<IRMethod> overloads = new ArrayList<>();
        int n = rec.components().size();
        int firstDefault = n;
        for (int i = 0; i < n; i++) {
            if (rec.components().get(i).initializer() != null) {
                firstDefault = i;
                break;
            }
        }
        if (firstDefault == n) return overloads;
        Type ownerType = CompilerTypes.ownerTypeFromInternal(owner, driver.semanticAnalyzer);
        List<Type> canonicalTypes = rec.components().stream().map(c -> CompilerTypes.toType(c.type(), driver.currentUnit)).toList();
        for (int drop = 1; drop <= n - firstDefault; drop++) {
            int paramCount = n - drop;
            List<Type> paramTypes = new ArrayList<>();
            List<IRLocalVariable> locals = new ArrayList<>();
            List<KofOperation> ops = new ArrayList<>();
            locals.add(new IRLocalVariable(0, "this", ownerType));
            ops.add(new KofLoadLocal(ownerType, 0));
            int localIdx = 1;
            for (int i = 0; i < paramCount; i++) {
                Type t = CompilerTypes.toType(rec.components().get(i).type(), driver.currentUnit);
                paramTypes.add(t);
                locals.add(new IRLocalVariable(localIdx, rec.components().get(i).name(), t));
                ops.add(new KofLoadLocal(t, localIdx));
                localIdx += TypeMetrics.isDoubleWidth(t) ? 2 : 1;
            }
            for (int i = paramCount; i < n; i++) {
                ExpressionNode init = rec.components().get(i).initializer();
                if (init != null) {
                    localIdx = ExpressionLowerer.emitExpression(driver, init, ops, owner, localIdx, locals);
                }
            }
            ops.add(new KofCall(ownerType, "<init>", canonicalTypes, Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
            ops.add(new KofReturnVoid());
            IRMethod m = new IRMethod("<init>", Type.PrimitiveType.VOID, paramTypes, AccessFlags.PUBLIC,
                    List.of(), List.of(new IRBasicBlock(0, ops)), locals);
            overloads.add(m);
        }
        return overloads;
    }

}