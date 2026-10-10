package dev.kof.compiler;

import java.util.List;

/**
 * Substituição/inferência de type-variables (extraído de {@link CompilerTypes}
 * no split do check_500): reescrita recursiva de leaves de type-variable
 * (por receiver ou por mapa de bind), detecção de type-var estrutural e
 * unificação formal×actual para inferir type-args de chamadas genéricas.
 * Puro — sem estado.
 */
final class TypeSubstitution {

    private TypeSubstitution() {}

    static Type substituteTypeVariable(String tvName, Type recvType, CompilationUnitNode currentUnit) {
        if (!(recvType instanceof Type.ClassType ct) || ct.typeArguments().isEmpty()) return null;
        if (currentUnit != null) {
            for (AstNode d : currentUnit.declarations()) {
                // §355/#385: qualquer declaração com type-params é fonte de
                // substituição — classe, INTERFACE genérica e record. Antes só
                // ClassDeclarationNode era varrida, e `Wrapper<String>.get()`
                // (interface) não substituia T → o efetivo saía TypeVariable.
                List<String> tps = switch (d) {
                    case ClassDeclarationNode cls when cls.name().equals(ct.name()) -> cls.typeParameters();
                    case InterfaceDeclarationNode it when it.name().equals(ct.name()) -> it.typeParameters();
                    case RecordDeclarationNode rc when rc.name().equals(ct.name()) -> rc.typeParameters();
                    default -> null;
                };
                if (tps == null) continue;
                for (int i = 0; i < tps.size(); i++) {
                    // §355: a entrada pode carregar bound ("T: Animal") —
                    // compara pelo NOME limpo, nunca pela crua.
                    if (i < ct.typeArguments().size()
                            && TypeParams.name(tps.get(i)).equals(tvName)) {
                        return ct.typeArguments().get(i);
                    }
                }
            }
        }
        return null;
    }

    /**
     * §245/#268: tipo de um campo/método cujo tipo declarado é um parâmetro
     * genérico (`T wrapped`) — substitui o type-variable pelo argumento real do
     * RECEIVER (`Wrapper<Point>.wrapped` → `Point`). Sem isto o tipo ficava
     * `TypeVariable(T)`/erased e o próximo acesso (`.x`) emitia owner `?`/`""`
     * (`NoClassDefFoundError`/`ClassFormatError`). Não muda nada quando o tipo
     * não é um type-variable ou o receiver não traz argumentos.
     */
    static Type substituteTypeVariableIn(Type memberType, Type recvType, CompilationUnitNode currentUnit) {
        if (memberType == null) return null;
        if (!(recvType instanceof Type.ClassType ct) || ct.typeArguments().isEmpty()) return memberType;
        return substituteLeaves(memberType, name -> substituteTypeVariable(name, recvType, currentUnit));
    }

    private static Type substituteLeaves(Type t, java.util.function.Function<String, Type> leaf) {
        if (t == null) return null;
        if (t instanceof Type.TypeVariable tv) {
            Type r = leaf.apply(tv.name());
            return r != null ? r : t;
        }
        if (t instanceof Type.ClassType ct) {
            if (ct.packageName().isEmpty() && ct.typeArguments().isEmpty()) {
                Type r = leaf.apply(ct.name());
                if (r != null) return r;
                return t;
            }
            if (ct.typeArguments().isEmpty()) return t;
            java.util.List<Type> args = new java.util.ArrayList<>(ct.typeArguments().size());
            boolean changed = false;
            for (Type a : ct.typeArguments()) {
                Type r = substituteLeaves(a, leaf);
                if (r != a) changed = true;
                args.add(r);
            }
            return changed ? new Type.ClassType(ct.packageName(), ct.name(), args) : t;
        }
        if (t instanceof Type.ArrayType at) {
            Type c = substituteLeaves(at.componentType(), leaf);
            return c == at.componentType() ? t : new Type.ArrayType(c);
        }
        if (t instanceof Type.NullableType nt) {
            Type c = substituteLeaves(nt.inner(), leaf);
            return c == nt.inner() ? t : new Type.NullableType(c);
        }
        if (t instanceof Type.FunctionType ft) {
            java.util.List<Type> ps = new java.util.ArrayList<>(ft.parameterTypes().size());
            boolean changed = false;
            for (Type p : ft.parameterTypes()) {
                Type rp = substituteLeaves(p, leaf);
                if (rp != p) changed = true;
                ps.add(rp);
            }
            Type rr = substituteLeaves(ft.returnType(), leaf);
            if (changed || rr != ft.returnType()) return new Type.FunctionType(ps, rr, ft.className());
            return t;
        }
        if (t instanceof Type.WildcardType wt) {
            Type rb = substituteLeaves(wt.bound(), leaf);
            return rb == wt.bound() ? t : new Type.WildcardType(rb, wt.upper());
        }
        return t;
    }

    /** Reescrita generica dos leaves de type-variable (ver {@link #substituteLeaves}). */
    static Type substituteTypeVars(Type t, java.util.function.Function<String, Type> leaf) {
        return substituteLeaves(t, leaf);
    }

    /** True se o tipo contem (em qualquer profundidade) um leaf de type-variable. */
    static boolean containsTypeVariable(Type t) {
        if (t instanceof Type.TypeVariable) return true;
        if (t instanceof Type.ClassType ct) {
            for (Type a : ct.typeArguments()) if (containsTypeVariable(a)) return true;
            return false;
        }
        if (t instanceof Type.ArrayType at) return containsTypeVariable(at.componentType());
        if (t instanceof Type.NullableType nt) return containsTypeVariable(nt.inner());
        return false;
    }

    /**
     * Unifica o tipo FORMAL de um parametro (com type-params do escopo) contra o
     * tipo REAL do argumento, coletando `tv -> tipo` em {@code out}. Suficiente
     * para inferir `T` de chamadas top-level (`window(l: List<Int>, ...)` ->
     * `T = Int`), incluindo type-vars aninhados (`List<T>` x `List<Int>`).
     * Casamento estrutural; incompativel/desconhecido = nao liga (nunca chuta).
     */
    static void bindTypeVars(Type formal, Type actual, java.util.List<String> typeParams,
                             java.util.Map<String, Type> out) {
        if (formal == null || actual == null) return;
        if (formal instanceof Type.TypeVariable tv) {
            for (String tp : typeParams) {
                if (TypeParams.name(tp).equals(tv.name())) { out.putIfAbsent(tv.name(), actual); return; }
            }
            return;
        }
        if (formal instanceof Type.ClassType fct) {
            if (fct.packageName().isEmpty() && fct.typeArguments().isEmpty()) {
                for (String tp : typeParams) {
                    if (TypeParams.name(tp).equals(fct.name())) { out.putIfAbsent(fct.name(), actual); return; }
                }
                return;
            }
            if (actual instanceof Type.ClassType act && fct.name().equals(act.name())
                    && fct.typeArguments().size() == act.typeArguments().size()) {
                for (int i = 0; i < fct.typeArguments().size(); i++) {
                    bindTypeVars(fct.typeArguments().get(i), act.typeArguments().get(i), typeParams, out);
                }
            }
            return;
        }
        if (formal instanceof Type.ArrayType fat && actual instanceof Type.ArrayType aat) {
            bindTypeVars(fat.componentType(), aat.componentType(), typeParams, out);
            return;
        }
        if (formal instanceof Type.NullableType fnt && actual instanceof Type.NullableType ant) {
            bindTypeVars(fnt.inner(), ant.inner(), typeParams, out);
        }
    }
}
