package dev.kof.compiler;

final class LambdaParameterTypes {

    private LambdaParameterTypes() {
    }

    static Type resolve(CompilerDriver driver, String typeName) {
        Type t = CompilerTypes.toType(typeName, driver.currentUnit);
        if (t instanceof Type.ClassType ct && ct.packageName().isEmpty() && ct.typeArguments().isEmpty()
                && !CompilerTypes.userDeclaresType(driver.currentUnit, driver.semanticAnalyzer, ct.name())) {
            Type builtin = CompilerTypes.builtinDeclaredType(ct.name());
            if (builtin != null) return builtin;
        }
        return t;
    }
}
