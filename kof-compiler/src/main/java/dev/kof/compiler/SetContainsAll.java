package dev.kof.compiler;

import java.util.List;

/**
 * §830 — {@code s.containsAll(other)} (stdlib batch, maintainer decision
 * 05/11): {@code other} is a Kof Set or List, compared by the same content
 * equality as {@code contains}. Another argument is SEM025; a native target
 * is {@link CollectionNativeGap} (NAT009).
 */
final class SetContainsAll {

    private SetContainsAll() {}

    static boolean refuse(CompilerDriver driver, MethodCallExpr mc, List<Type> argTypes) {
        if (driver.currentDiagnostics == null) return false;
        Type arg = argTypes.size() == 1 ? argTypes.get(0) : null;
        if (arg == null || (!Type.isUnknown(arg) && !BuiltinTypes.isSet(arg) && !BuiltinTypes.isList(arg))) {
            SourcePosition pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                    pos != null ? pos.column() : 0, 0,
                    "Set.containsAll takes one Set or List argument", "SEM025");
            return true;
        }
        return CollectionNativeGap.refuse(driver, mc, "Set.containsAll");
    }
}
