package dev.kof.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymbolTable {

    private final SymbolTable parent;
    private final Map<String, Symbol> symbols = new HashMap<>();
    private final Map<String, FieldSymbol> fields = new HashMap<>();

    SymbolTable() {
        this(null);
    }

    SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    void define(Symbol symbol) {
        // sobrecarga de CONSTRUTORES: mesmo nome <init>, assinaturas várias
        if (symbol instanceof ConstructorSymbol cs) {
            Symbol existing = symbols.get("<init>");
            java.util.LinkedHashMap<List<Type>, ConstructorSymbol> merged = new java.util.LinkedHashMap<>();
            if (existing instanceof ConstructorSymbol one) {
                merged.put(one.parameterTypes(), one);
            } else if (existing instanceof ConstructorSet set) {
                for (ConstructorSymbol c : set.constructors()) merged.put(c.parameterTypes(), c);
            }
            merged.put(cs.parameterTypes(), cs);
            if (merged.size() == 1 && !(existing instanceof ConstructorSet)) {
                symbols.put("<init>", cs);
            } else {
                symbols.put("<init>", new ConstructorSet(new ArrayList<>(merged.values())));
            }
            return;
        }
        // §131 (10a): sobrecarga de MÉTODO por assinatura — mesmo nome,
        // aridades/tipos vários coexistem (MethodSet), espelho do <init>.
        if (symbol instanceof MethodSymbol ms) {
            Symbol existing = symbols.get(ms.name());
            java.util.LinkedHashMap<List<Type>, MethodSymbol> merged = new java.util.LinkedHashMap<>();
            if (existing instanceof MethodSymbol one) {
                merged.put(one.parameterTypes(), one);
            } else if (existing instanceof MethodSet set) {
                for (MethodSymbol m : set.methods()) merged.put(m.parameterTypes(), m);
            }
            merged.put(ms.parameterTypes(), ms);
            if (merged.size() == 1 && !(existing instanceof MethodSet)) {
                symbols.put(ms.name(), ms);
            } else {
                symbols.put(ms.name(), new MethodSet(new ArrayList<>(merged.values())));
            }
            return;
        }
        if (symbol instanceof FieldSymbol fs) {
            fields.put(fs.name(), fs);
        }
        symbols.put(symbol.name(), symbol);
    }

    /** Construtor com exatamente {@param argumentCount} parâmetros, ou null. */
    static ConstructorSymbol constructorFor(SymbolTable members, int argumentCount) {
        return constructorFor(members, argumentCount, null);
    }

    /**
     * Construtor aplicável a {@param argumentCount} argumentos, respeitando
     * parâmetros default (#766). Primeiro procura a assinatura exata; depois o
     * wrapper de prefixo válido. Sem {@param argTypes}, a ambiguidade por tipos
     * é decidida pela ordem de declaração.
     */
    static ConstructorSymbol constructorFor(SymbolTable members, int argumentCount, List<Type> argTypes) {
        Symbol s = members != null ? members.resolve("<init>") : null;
        List<ConstructorSymbol> ctors = new ArrayList<>();
        if (s instanceof ConstructorSymbol c) {
            ctors.add(c);
        } else if (s instanceof ConstructorSet set) {
            ctors.addAll(set.constructors());
        }
        ConstructorSymbol exactFallback = null;
        for (ConstructorSymbol c : ctors) {
            if (c.parameterTypes().size() != argumentCount) {
                continue;
            }
            if (exactFallback == null) {
                exactFallback = c;
            }
            if (argTypes == null || TypeChecker.ctorAccepts(c, argTypes)) {
                return c;
            }
        }
        if (exactFallback != null) {
            return exactFallback;
        }
        ConstructorSymbol compatibleDefault = null;
        ConstructorSymbol arityDefault = null;
        for (ConstructorSymbol c : ctors) {
            if (!c.acceptsArgumentCount(argumentCount)) {
                continue;
            }
            if (arityDefault == null) arityDefault = c;
            List<Type> effective = c.effectiveParameterTypes(argumentCount);
            if (argTypes == null || TypeChecker.ctorAccepts(new ConstructorSymbol(
                    c.ownerClass(), effective, c.accessFlags()), argTypes)) {
                compatibleDefault = c;
                break;
            }
        }
        return compatibleDefault != null ? compatibleDefault : arityDefault;
    }

    static String describeExpectedConstructorArity(ConstructorSymbol ctor) {
        int full = ctor.parameterTypes().size();
        int required = ctor.requiredArity();
        if (required == full) return String.valueOf(full);
        return required + ".." + full;
    }

    Symbol resolve(String name) {
        Symbol s = symbols.get(name);
        if (s != null) return s;
        if (parent != null) return parent.resolve(name);
        return null;
    }

    FieldSymbol resolveField(String name) {
        FieldSymbol fs = fields.get(name);
        if (fs != null) return fs;
        if (parent != null) return parent.resolveField(name);
        return null;
    }

    boolean hasLocal(String name) {
        return symbols.containsKey(name);
    }

    SymbolTable enterScope() {
        return new SymbolTable(this);
    }

    /**
     * Atualiza o tipo de um local já definido NESTE escopo (pinning de
     * coleções: o primeiro put() em mapOf() vazio tipa o Map — SG-008).
     * Retorna true se atualizou.
     */
    boolean updateLocalType(String name, Type newType) {
        Symbol s = symbols.get(name);
        if (s instanceof LocalVariableSymbol lv && !lv.isVal()
                && !newType.equals(lv.type())) {
            symbols.put(name, new LocalVariableSymbol(lv.name(), newType, lv.index(), lv.isVal()));
            return true;
        }
        return false;
    }

    SymbolTable parent() {
        return parent;
    }

    Map<String, Symbol> localSymbols() {
        return Collections.unmodifiableMap(symbols);
    }

    interface Symbol {
        String name();
        Type type();
    }

    record ParameterSymbol(String name, Type type, int index) implements Symbol {
    }

    // §355 (rio da erasure): o symbol guarda o BOUND declarado (`T: Animal`),
    // entregue pelo parser na entrada "T: Animal". type() devolve o
    // TypeVariable COM bound — é o que a erasure JVM central usa para apagar
    // para o bound (e o member-access resolve em Animal, não em "?").
    record TypeParameterSymbol(String name, Type bound) implements Symbol {
        TypeParameterSymbol(String name) {
            this(name, null);
        }

        @Override
        public Type type() {
            return new Type.TypeVariable(name, bound);
        }
    }

    record LocalVariableSymbol(String name, Type type, int index, boolean isVal) implements Symbol {
        // construtor compacto: mantém os call sites de 3 args (params de catch,
        // loop vars, pattern vars — nunca val) sem quebrar.
        LocalVariableSymbol(String name, Type type, int index) {
            this(name, type, index, false);
        }
    }

    record FieldSymbol(String name, Type type, int accessFlags, String ownerClass) implements Symbol {
    }

    static final class MethodSymbol implements Symbol {
        private final String name;
        private final String ownerClass;
        private Type returnType;
        private final List<Type> parameterTypes;
        private final int accessFlags;
        private final DispatchKind dispatchKind;

        MethodSymbol(String name, String ownerClass, Type returnType,
                     List<Type> parameterTypes, int accessFlags,
                     DispatchKind dispatchKind) {
            this.name = name;
            this.ownerClass = ownerClass;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.accessFlags = accessFlags;
            this.dispatchKind = dispatchKind;
        }

        void setReturnType(Type returnType) {
            this.returnType = returnType;
        }

        @Override
        public Type type() {
            return returnType;
        }

        @Override
        public String name() {
            return name;
        }

        public String ownerClass() {
            return ownerClass;
        }

        public Type returnType() {
            return returnType;
        }

        public List<Type> parameterTypes() {
            return parameterTypes == null ? List.of() : java.util.Collections.unmodifiableList(parameterTypes);
        }

        public int accessFlags() {
            return accessFlags;
        }

        public DispatchKind dispatchKind() {
            return dispatchKind;
        }
    }

    /** Conjunto de MÉTODOS sobrecarregados (§131, 10a) — mesmo nome,
     *  assinaturas várias; type() = último (comportamento pré-§131 p/ quem
     *  não faz seleção por aridade). */
    record MethodSet(List<MethodSymbol> methods) implements Symbol {
        @Override
        public String name() {
            return methods.get(0).name();
        }

        @Override
        public Type type() {
            return methods.get(methods.size() - 1).type();
        }

        /** §131: seleciona o overload por aridade (e compatibilidade de args
         *  quando conhecida); null = nenhum casa. Entre aplicáveis, escolhe
         *  o mais específico por igualdade exata de tipo (ex.: Boolean não
         *  cair em Int no primeiro que casar por aridade). */
        public MethodSymbol select(int argCount, List<Type> argTypes) {
            MethodSymbol byArity = null;
            List<MethodSymbol> applicable = new ArrayList<>();
            for (MethodSymbol m : methods) {
                if (m.parameterTypes().size() == argCount) {
                    if (byArity == null) byArity = m;
                    if (argTypes == null) continue;
                    boolean compatible = true;
                    for (int i = 0; i < argCount; i++) {
                        if (!Type.isUnknown(argTypes.get(i))
                                && !dev.kof.compiler.TypeChecker.isAssignable(argTypes.get(i), m.parameterTypes().get(i))) {
                            compatible = false;
                            break;
                        }
                    }
                    if (compatible) applicable.add(m);
                }
            }
            if (argTypes == null || applicable.isEmpty()) return byArity;
            if (applicable.size() == 1) return applicable.get(0);

            // Mais de um aplicável: pontua por igualdade exata de tipo
            MethodSymbol best = null;
            int bestScore = -1;
            for (MethodSymbol m : applicable) {
                int score = 0;
                for (int i = 0; i < argCount; i++) {
                    Type arg = argTypes.get(i);
                    Type par = m.parameterTypes().get(i);
                    if (arg != null && arg.equals(par)) score++;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            }
            return best != null ? best : applicable.get(0);
        }
    }

    /** Conjunto de construtores sobrecarregados (mesmo nome <init>). */
    record ConstructorSet(List<ConstructorSymbol> constructors) implements Symbol {
        @Override
        public String name() {
            return "<init>";
        }

        @Override
        public Type type() {
            return constructors.get(constructors.size() - 1).type();
        }
    }

    record ConstructorSymbol(String ownerClass, List<Type> parameterTypes, int accessFlags,
                             int requiredArity) implements Symbol {
        ConstructorSymbol(String ownerClass, List<Type> parameterTypes, int accessFlags) {
            this(ownerClass, parameterTypes, accessFlags, parameterTypes.size());
        }

        @Override
        public String name() {
            return "<init>";
        }

        @Override
        public Type type() {
            return new Type.ClassType("", ownerClass, List.of());
        }

        List<Type> effectiveParameterTypes(int argumentCount) {
            if (argumentCount < requiredArity || argumentCount > parameterTypes.size()) {
                return List.of();
            }
            return List.copyOf(parameterTypes.subList(0, argumentCount));
        }

        boolean acceptsArgumentCount(int argumentCount) {
            return argumentCount >= requiredArity && argumentCount <= parameterTypes.size();
        }
    }

    record ClassSymbol(String name, String packageName, String superClass,
                       List<String> interfaces, SymbolTable members) implements Symbol {
        @Override
        public Type type() {
            return new Type.ClassType(packageName, name, List.of());
        }

        String internalName() {
            if (packageName.isEmpty()) return name;
            return packageName.replace('.', '/') + "/" + name;
        }
    }

    record FunctionSymbol(String name, Type returnType, List<Type> parameterTypes,
                          int accessFlags) implements Symbol {
        @Override
        public Type type() {
            return returnType;
        }
    }

    enum DispatchKind {
        INSTANCE,
        STATIC,
        INTERFACE
    }
}
