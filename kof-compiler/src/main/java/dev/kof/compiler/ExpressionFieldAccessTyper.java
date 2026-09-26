package dev.kof.compiler;

import java.util.List;


/**
 * Inferencia de tipo do acesso a campo (FieldAccessExpr) extraida VERBATIM
 * do ExpressionTyper (gate &lt;=500; rule 3 = estrutura, nao comportamento):
 * palette/UI, enums, estaticos externos da face §500 slice B e campos de
 * instancia/reflection.
 */
final class ExpressionFieldAccessTyper {

    private ExpressionFieldAccessTyper() {}

    static Type type(CompilerDriver driver, FieldAccessExpr fa, List<IRLocalVariable> locals) {
        Type recvType = ExpressionTyper.inferExprType(driver, fa.receiver(), locals);
        // narrowing de null-safety: `if (x != null) { x.length }` — inner type
        if (recvType instanceof Type.NullableType nt) recvType = nt.inner();
        if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
            return KofProcess.fieldType(fa.fieldName());
        }
        if (KofUi.isComponent(recvType) && "state".equals(fa.fieldName())) {
            return Type.PrimitiveType.INT;
        }
        if (KofUi.isWindow(recvType) && "title".equals(fa.fieldName())) {
            return BuiltinTypes.STRING;
        }
        if (KofUi.isLabel(recvType) && "text".equals(fa.fieldName())) {
            return BuiltinTypes.STRING;
        }
        if (KofUi.isLabel(recvType) && "fontSize".equals(fa.fieldName())) {
            return Type.PrimitiveType.INT;
        }
        if (KofUi.isLabel(recvType) && "bold".equals(fa.fieldName())) {
            return Type.PrimitiveType.BOOL;
        }
        if (KofUi.isLabel(recvType) && "color".equals(fa.fieldName())) {
            return KofUi.COLOR;
        }
        if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())
                && KofUi.paletteColor(fa.fieldName()) != null) {
            return KofUi.COLOR;
        }
        if (fa.receiver() instanceof IdentifierExpr tid && KofUiTokens.isTokenNamespace(tid.name())
                && KofUiTokens.tokenValue(tid.name(), fa.fieldName()) != null) {
            return Type.PrimitiveType.INT;
        }
        if (BuiltinTypes.isList(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
            return Type.PrimitiveType.INT;
        }
        if (BuiltinTypes.isMap(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
            return Type.PrimitiveType.INT;
        }
        if (BuiltinTypes.isSet(recvType) && ("size".equals(fa.fieldName()) || "length".equals(fa.fieldName()))) {
            return Type.PrimitiveType.INT;
        }
        if (recvType instanceof Type.ArrayType && ("length".equals(fa.fieldName())
                || "size".equals(fa.fieldName()) || "count".equals(fa.fieldName()))) {
            return Type.PrimitiveType.INT;
        }
        if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
            return Type.PrimitiveType.INT;
        }
        if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
            return BuiltinTypes.STRING;
        }
        // §500 slice B: tipo real do campo estático externo pelo NOME
        // da classe — sem isto o dispatch do `println` escolhia
        // `String.valueOf(Object)` e o int cru do getstatic entrava
        // sem box → VerifyError no load.
        Type s500e = StaticClassReceiver.emitType(driver, fa, locals);
        if (s500e != null) return s500e;
        if (recvType instanceof Type.ClassType ct
                && CompilerTypes.isEnumName(ct.name(), driver.currentUnit)) { // #445: pkg real
            if (!CompilerTypes.enumConstantsOf(ct.name(), driver.currentUnit).contains(fa.fieldName()) && driver.currentDiagnostics != null) {
                driver.currentDiagnostics.error(fa,
                        "enum '" + ct.name() + "' has no constant '" + fa.fieldName() + "'",
                        "SEM030");
            }
            return recvType;
        }
        if (recvType instanceof Type.ClassType ct && driver.semanticAnalyzer != null) {
            SymbolTable.Symbol s = HierarchyResolver.resolveFieldInHierarchy(ct.name(), fa.fieldName(), driver.semanticAnalyzer);
            if (s instanceof SymbolTable.FieldSymbol fs) {
                return CompilerTypes.substituteTypeVariableIn(fs.type(), recvType, driver.currentUnit);
            }
            if (s instanceof SymbolTable.MethodSymbol ms && ms.parameterTypes().isEmpty()) {
                return CompilerTypes.substituteTypeVariableIn(ms.returnType(), recvType, driver.currentUnit);
            }
        }
        return Type.UnknownType.UNKNOWN;
    
    }
}
