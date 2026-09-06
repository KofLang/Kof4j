package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompilerDriver extends CompilerDriverState {
    int emitSamAdapter(LambdaExpr le, Type.ClassType iface, ExternalClasspath.Sam sam,
                    List<KofOperation> ops, String owner, int localIdx, List<IRLocalVariable> locals) {
        return CompilerUiEmitter.emitSamAdapter(this, le, iface, sam, ops, owner, localIdx, locals);
    }

    int emitFieldIncrement(Type ownerType, String fieldName, Type fieldType,
                           boolean prefix, KofBinaryOp op, List<KofOperation> ops,
                           int localIdx, List<IRLocalVariable> locals) {
        return CompilerUiEmitter.emitFieldIncrement(this, ownerType, fieldName, fieldType, prefix, op, ops, localIdx, locals);
    }

    int emitPackedColor(List<ExpressionNode> args, List<KofOperation> ops,
                       String owner, int localIdx, List<IRLocalVariable> locals) {
        return CompilerUiEmitter.emitPackedColor(this, args, ops, owner, localIdx, locals);
    }

    int emitUiInstance(Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                      String owner, int localIdx, List<IRLocalVariable> locals) {
        return CompilerUiEmitter.emitUiInstance(this, recvType, mc, ops, owner, localIdx, locals);
    }

    /** Uma chave de config descoberta em compile-time (kof config gen). */
    public record ConfigKeyInfo(String method, String key, String defaultLiteral,
                                String file, int line) {
        /** Tipo declarado do valor, para o template gerado. */
        public String typeHint() {
            return switch (method) {
                case "int" -> "Int";
                case "long" -> "Long";
                case "bool" -> "Bool";
                case "str", "required", "get" -> "String";
                default -> "String";
            };
        }

        public boolean hasDefault() {
            return defaultLiteral != null;
        }

        public ConfigKeyInfo {
            // "..." no source vira conteúdo sem aspas aqui (vem da AST);
            // null = sem default (required/get)
            defaultLiteral = normalizeDefault(defaultLiteral);
        }
        private static String normalizeDefault(String d) {
            if (d == null) return null;
            return d.replaceFirst("^\"", "").replaceFirst("\"$", "");
        }
    }
    String ensureSuperBridge(String ownerInternal, String superInternal,
                           String methodName, List<Type> paramTypes, Type returnType) {
        return CompilerEmission2.ensureSuperBridge(this, ownerInternal, superInternal, methodName, paramTypes, returnType);
    }

    int emitArgumentsWithFormalTypes(List<ExpressionNode> args, List<Type> formalTypes,
                                    List<KofOperation> ops, String owner, int localIdx,
                                    List<IRLocalVariable> locals) {
        return CompilerEmission2.emitArgumentsWithFormalTypes(this, args, formalTypes, ops, owner, localIdx, locals);
    }

    int emitIncrement(UnaryExpr ue, Type operandType, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        return CompilerEmission2.emitIncrement(this, ue, operandType, ops, owner, localIdx, locals);
    }




    public CompilationResult compileForTestsSources(java.util.List<Path> sources, Path outputDir,
                                                    Target target, Path moduleRoot) {
        return CompilerPipeline.compileForTestsSources(this, sources, outputDir, target, moduleRoot);
    }



    public CompilationResult compileSources(java.util.List<Path> sources, Path outputDir, Target target,
                                            Path moduleRoot) {
        return CompilerPipeline.compileSources(this, sources, outputDir, target, moduleRoot);
    }

    /**
     * INTERPRETA um módulo Kof sem emitir bytecode nem fork de JVM — o
     * target KofScript. Roda o mesmo frontend do compileSources e executa a
     * IR otimizada no KofInterpreter (paridade por construção). Falhas do
     * frontend viram {@link KofInterpretException} com os diagnósticos.
     */
    public KofInterpreter.Result interpret(java.util.List<Path> sources, Path moduleRoot,
                                           String[] args) {
        return CompilerPipeline.interpret(this, sources, moduleRoot, args);
    }
Target target = Target.JVM;


    /** Um caso `test "nome" { }` descoberto em compile-time. */
    public record TestInfo(String name, String functionName) {
    }

    /** Testes descobertos na última compilação (ordem de declaração). */

    /**
     * Compila em modo harness de testes: cada `test "nome" { }` vira uma
     * função void (`kof_test_N`) e o main do programa é substituído por um
     * runner sintetizado que executa os testes isolados por try/catch,
     * imprime PASS/FAIL por nome e sai com código != 0 quando há falha.
     * O main original é ignorado (como cargo test).
     */

    /** Variante multi-arquivo do harness de testes (um diretório = um módulo). */


    /** Emite warnings acumulados do classpath externo quando houver coletor. */


    /**
     * Compilação MULTI-ARQUIVO: todos os .kf do diretório formam UM módulo
     * (convenção Go-like: diretório = pacote). Classes/funções de um arquivo
     * são visíveis aos demais sem import — o import fica para classes
     * EXTERNAS (JVM/Android via ExternalClasspath).
     */

    /**
     * Deriva o moduleRoot do menor ancestral comum dos diretórios-pai de todas
     * as fontes — resolução unificada de `import a.b.C` para projetos
     * multi-diretório (P1-4). Fontes no mesmo diretório mantêm o diretório
     * como raiz (comportamento anterior, convenção Go-like).
     */



    /**
     * Imports de PACOTES KOF (código Kof em outras pastas):
     *   import vendas.models            → módulo inteiro do diretório
     *   import vendas.models.Cliente    → arquivo Cliente.kf daquele pacote
     *
     * Resolução: relativa à RAIZ do módulo (diretório passado ao build),
     * TRANSITIVA (imports dos imports), sem ciclos. Tipos ficam visíveis
     * pelo nome simples — a IR é única e global ao build.
     */























    /** Cache de interfaces sintéticas de função (uma por assinatura). */
    final java.util.Map<String, Type.ClassType> functionInterfaces = new java.util.HashMap<>();
    final java.util.IdentityHashMap<LambdaExpr, String> lambdaClassNames = new java.util.IdentityHashMap<>();

    /**
     * Garante um método-ponte na classe DONA da lambda:
     *   kof_super$metodo(...) { super.metodo(...); }
     * A lambda chama a ponte (invokevirtual no $outer) — o verificador JVM
     * rejeita INVOKESPECIAL direto quando a classe corrente não é subclasse.
     */


    void validateOrmField(MethodCallExpr mc, String entityName,
                          List<EntityFieldNode> fields) {
        CompilerOrmSupport.validateOrmField(this, mc, entityName, fields);
    }

    int lowerQueryDsl(QueryDslExpr q, List<KofOperation> ops, String owner,
                      int localIdx, List<IRLocalVariable> locals) {
        return CompilerOrmSupport.lowerQueryDsl(this, q, ops, owner, localIdx, locals);
    }



    String lambdaClass(LambdaExpr le, Type.FunctionType ft, List<IRLocalVariable> captures,
                       boolean isTask) {
        return CompilerLambdaClass.lambdaClass(this, le, ft, captures, isTask);
    }



    /** Chaves de config descobertas na última compilação (ordem de uso). */

    /**
     * Registra `config.method("chave"[, default])` em compile-time (P3 —
     * kof config gen). Só aceita chave como literal de string; chave
     * computada não aparece no template (nada é inferido em runtime).
     */

    /**
     * Gera um template `kof.config` a partir das chaves descobertas na
     * última compilação — para deploy (docs/stdlib-config.md §8.2 P3).
     * Chaves com default viram comentário (o programa já tem valor);
     * required/get sem default viram linha ativa.
     */

    /**
     * Synthetic lambda class. Captured outer locals become private final
     * fields set by a capturing <init>; invoke() copies them into locals at
     * entry, so the body lowers unchanged (captures are read-only snapshots).
     */




    /** Constantes por enum declarado na unidade atual (nome → [A, B, ...]). */


    /**
     * O tipo é um RECORD (dados imutáveis com equals/hashCode gerados)? Usado
     * no lowering de `==`/`!=` (bug 11) para despachar para equals (conteúdo)
     * em vez de igualdade de referência.
     */

    /**
     * O tipo (ou seus type arguments) contém uma FunctionType com className
     * null? Isso indica um tipo de lambda vindo da análise semântica (que roda
     * antes da síntese) — obsoleto para o emit do invoke (bug 20).
     */

    /** Nome da constante de enum representada por um rótulo de case. */




    int emitStatement(StatementNode stmt, List<KofOperation> ops, String owner, int localIdx,
                              List<IRLocalVariable> locals, Type returnType) {
        int before = ops.size();
        int result = StatementLowerer.emitStatementInner(this, stmt, ops, owner, localIdx, locals, returnType);
        if (stmt.position() != null) {
            for (int i = before; i < ops.size(); i++) {
                currentDebugPositions.put(ops.get(i), stmt.position());
            }
        }
        return result;
    }







    /** Compatibilidade largura para fallback de resolução de construtor:
     *  primitivos por largura, tipos de referência por hierarquia, Unknown aceita tudo. */








    void emitPrimWidenNarrow(List<KofOperation> ops, ExpressionNode value,
                             Type elemType, List<IRLocalVariable> locals) {
        CompilerComparisons.emitPrimWidenNarrow(this, ops, value, elemType, locals);
    }



    final java.util.IdentityHashMap<LambdaExpr, String> samAdapterNames =
            new java.util.IdentityHashMap<>();

    /**
     * Gera (uma vez por lambda) a classe sintética que IMPLEMENTA a
     * interface externa: o método SAM contém o corpo da lambda e as
     * capturas viram campos finais + construtor — o mesmo modelo das
     * lambdas nativas. Emite NEW+DUP+capturas+&lt;init&gt; na pilha.
     */


    /**
     * Corpo do adapter: mesmo esqueleto de lambdaClass, mas implementa a
     * interface externa e o método tem o nome/assinatura do SAM. Os params
     * da lambda são ligados POSICIONALMENTE aos params do SAM.
     */
    void buildSyntheticAdapter(String className, String ifaceInternal, String samName,
                                       List<Type> samParamTypes, Type samReturnType,
                                       LambdaExpr le, List<IRLocalVariable> captures) {
        Type ownerType = new Type.ClassType("", className, List.of());
        List<FormalParameterNode> params = le.parameters();

        List<IRField> fields = new ArrayList<>();
        List<Type> captureTypes = new ArrayList<>();
        for (IRLocalVariable cap : captures) {
            fields.add(new IRField(cap.name(), cap.type(),
                    AccessFlags.PRIVATE | AccessFlags.FINAL, null));
            captureTypes.add(cap.type());
        }

        // invoke(): copia capturas pra locais e chama o método SAM real,
        // que contém o corpo da lambda
        List<KofOperation> ctorOps = new ArrayList<>();
        List<IRLocalVariable> ctorLocals = new ArrayList<>();
        ctorLocals.add(new IRLocalVariable(0, "this", ownerType));
        int cidx = 1;
        for (IRLocalVariable cap : captures) {
            ctorOps.add(new KofLoadLocal(ownerType, 0));
            ctorOps.add(new KofLoadLocal(cap.type(), cidx));
            ctorOps.add(new KofStoreField(ownerType, cap.name(), cap.type()));
            ctorLocals.add(new IRLocalVariable(cidx, cap.name(), cap.type()));
            cidx += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        ctorOps.add(new KofReturnVoid());
        IRMethod ctor = new IRMethod("<init>", Type.PrimitiveType.VOID, captureTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, ctorOps)), ctorLocals);

        // método SAM: this + capturas nos primeiros slots + params do SAM
        List<KofOperation> bodyOps = new ArrayList<>();
        List<IRLocalVariable> bodyLocals = new ArrayList<>();
        bodyLocals.add(new IRLocalVariable(0, "this", ownerType));
        int bidx = 1;
        for (IRLocalVariable cap : captures) {
            bodyOps.add(new KofLoadLocal(ownerType, 0));
            bodyOps.add(new KofLoadField(ownerType, cap.name(), cap.type()));
            bodyOps.add(new KofStoreLocal(cap.type(), bidx));
            bodyLocals.add(new IRLocalVariable(bidx, cap.name(), cap.type()));
            bidx += TypeMetrics.isDoubleWidth(cap.type()) ? 2 : 1;
        }
        for (int i = 0; i < params.size() && i < samParamTypes.size(); i++) {
            bodyLocals.add(new IRLocalVariable(bidx, params.get(i).name(), samParamTypes.get(i)));
            bidx += TypeMetrics.isDoubleWidth(samParamTypes.get(i)) ? 2 : 1;
        }
        int localEnd = bidx;
        for (StatementNode stmt : le.body()) {
            localEnd = emitStatement(stmt, bodyOps, className, localEnd, bodyLocals, samReturnType);
        }
        KofOperation last = bodyOps.isEmpty() ? null : bodyOps.get(bodyOps.size() - 1);
        if (last == null || !(last instanceof KofReturn || last instanceof KofReturnVoid)) {
            if (Type.isVoid(samReturnType)) bodyOps.add(new KofReturnVoid());
            else bodyOps.add(new KofReturn(samReturnType));
        }
        IRMethod samMethod = new IRMethod(samName, samReturnType, samParamTypes,
                AccessFlags.PUBLIC, List.of(),
                List.of(new IRBasicBlock(0, bodyOps)), bodyLocals);

        IRClass cls = new IRClass(className, "java/lang/Object",
                List.of(ifaceInternal),
                AccessFlags.PUBLIC | AccessFlags.SUPER | AccessFlags.FINAL,
                fields, List.of(samMethod, ctor), List.of(), null, 300 + lambdaCounter);
        syntheticClasses.add(cls);
    }





    /**
     * Emits ++/-- on assignable targets (locals, fields, array elements) with
     * correct prefix/postfix semantics: the result value stays on the stack
     * and the target is stored back.
     */

    /**
     * Field increment: the receiver must survive the field read for the store.
     * Postfix needs a temp for the previous value (JVM putfield consumes the
     * top two slots as value+receiver).
     */




    /**
     * FLT001: no Native, float/double ainda não têm aritmética SSE nem
     * formatação real (os bits vivem na pilha como inteiros). Operações de
     * ponto flutuante viram diagnóstico em compile-time — nunca resultado
     * silenciosamente errado. JSON já tem o próprio código (JSN001).
     */




    /**
     * JSN002: valida recursivamente que toda instancia tem layout
     * conhecido em compile-time e campos suportados pelo walker nativo.
     * Qualquer campo fora do conjunto (List, Map, float/double) diagnostica
     * explicitamente — nunca resultado silenciosamente errado.
     */
    /**
     * Campos ordenados (nome, tipo) de uma classe/record/entity declarada
     * na unidade corrente — usados pela composicao JSON no Native.
     */
    java.util.List<String[]> classFieldsOrdered(String className) {
        java.util.List<String[]> out = new ArrayList<>();
        for (AstNode d : currentUnit.declarations()) {
            if (d instanceof RecordDeclarationNode r && r.name().equals(className)) {
                for (RecordComponentNode f : r.components()) {
                    out.add(new String[]{f.name(), f.type()});
                }
            } else if (d instanceof EntityDeclarationNode e && e.name().equals(className)) {
                for (EntityFieldNode f : e.fields()) {
                    out.add(new String[]{f.name(), f.type()});
                }
            } else if (d instanceof ClassDeclarationNode c && c.name().equals(className)) {
                for (AstNode m : c.members()) {
                    if (m instanceof FieldDeclarationNode f) {
                        out.add(new String[]{f.name(), f.type()});
                    }
                }
            }
        }
        return out;
    }

    boolean nativeObjJsonFieldsOk(String className, java.util.Set<String> visiting,
                                          String ownerForDiag) {
        if (visiting.contains(className)) return true; // ciclo: aceita no nivel externo
        visiting.add(className);
        boolean ok = true;
        for (String[] f : classFieldsOrdered(className)) {
            if (!CompilerTypeSupport.fieldOk(this, f[1], className, visiting)) ok = false;
        }
        return ok;
    }

    // v1 flat: objetos aninhados ainda nao sao suportados pelo walker










    int emitComparisonShortcut(BinaryExpr bin, List<KofOperation> ops, String owner,
                               int localIdx, List<IRLocalVariable> locals) {
        return CompilerComparisons.emitComparisonShortcut(this, bin, ops, owner, localIdx, locals);
    }



    /**
     * toString() nativo de record: "Nome[campo=valor, ...]" — sintetizado no
     * IR (padrão de concat: valueOf + kof_string_concat).
     */



    /**
     * Parses an integer literal, including hexadecimal (0xFF...). ARGB color
     * values may exceed Integer.MAX_VALUE; they wrap to the signed 32-bit
     * representation, which the Kof color semantics use (shifts + mask).
     */


}
