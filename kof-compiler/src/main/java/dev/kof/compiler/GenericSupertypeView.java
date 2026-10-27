package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GenericSupertypeView {

    private GenericSupertypeView() {}

    static Type as(CompilerDriver driver, Type actual, Type formal) {
        if (!(formal instanceof Type.ClassType fct) || fct.typeArguments().isEmpty()) return actual;
        if (!(actual instanceof Type.ClassType act) || act.name().equals(fct.name())
                || driver.semanticAnalyzer == null) {
            return actual;
        }
        Type.ClassType found = find(driver, act, fct.name(), new HashSet<>());
        if (found == null || TypeSubstitution.containsTypeVariable(found)) return actual;
        for (Type a : found.typeArguments()) {
            if (!concrete(driver, a)) return actual;
        }
        return found;
    }

    private static Type.ClassType find(CompilerDriver driver, Type.ClassType cur, String target, Set<String> seen) {
        if (!seen.add(cur.name())) return null;
        SymbolTable.ClassSymbol cs = driver.semanticAnalyzer.getClass(cur.name());
        if (cs == null) return null;
        List<String> supers = new ArrayList<>(cs.interfaces());
        if (cs.superClass() != null) supers.add(cs.superClass());
        for (String s : supers) {
            if (s == null) continue;
            Type raw = CompilerTypes.toType(s, driver.currentUnit);
            if (TypeSubstitution.substituteTypeVariableIn(raw, cur, driver.currentUnit) instanceof Type.ClassType st) {
                if (st.name().equals(target)) return st;
                Type.ClassType deeper = find(driver, st, target, seen);
                if (deeper != null) return deeper;
            }
        }
        return null;
    }

    private static boolean concrete(CompilerDriver driver, Type t) {
        if (t instanceof Type.ClassType ct) {
            if (ct.packageName().isEmpty() && ct.typeArguments().isEmpty()
                    && driver.semanticAnalyzer.getClass(ct.name()) == null) {
                return false;
            }
            for (Type a : ct.typeArguments()) {
                if (!concrete(driver, a)) return false;
            }
        }
        if (t instanceof Type.NullableType nt) return concrete(driver, nt.inner());
        if (t instanceof Type.ArrayType at) return concrete(driver, at.componentType());
        return true;
    }
}
