package dev.kof.compiler;

/**
 * A field of the class being lowered, read through the implicit {@code this},
 * is a value name exactly like a local: it shadows a stdlib namespace of the
 * same name ({@code cache}, {@code buffer}, {@code config}...). The lowering
 * only looked at the IR locals, so {@code cache.get(k)} on a field
 * {@code TtlCache cache} was lowered as the stdlib {@code cache.get} (a wrong
 * value, a VerifyError or "no lowering") while the analyzer had typed the field.
 */
final class OwnFieldNames {

    private OwnFieldNames() {}

    static boolean isOwnField(CompilerDriver driver, String name) {
        String owner = driver.currentLoweringOwner;
        if (driver.semanticAnalyzer == null || owner == null || name == null) return false;
        String simple = owner.substring(owner.lastIndexOf('/') + 1);
        return MemberResolver.resolveFieldInHierarchy(driver.semanticAnalyzer, simple, name)
                instanceof SymbolTable.FieldSymbol;
    }
}
