package dev.kof.compiler;
import java.util.ArrayList;
import java.util.List;

/**
 * Lowering da UNIDADE de compilação → {@link IRModule}: a tabela `extern` do
 * módulo inteiro (#934 — independente da ordem de mesclagem dos arquivos), o
 * despacho por declaração (classes/interfaces/records/enums/entities/funções
 * de topo/externs), o gate FFI de binding (FFI001/FFI002 honestos) e a
 * montagem da classe Main das funções de topo. Extraído do CompilerPipeline
 * (split check_500; o comportamento é o mesmo, byte a byte).
 */
final class CompilerUnitLowering {

    private CompilerUnitLowering() {}

    static IRModule lowerToIR(CompilerDriver driver, CompilationUnitNode unit, DiagnosticCollector diagnostics) {
        List<String> imports = new ArrayList<>(unit.imports());
        // D-NET-JS-V1 (maintainer 02/10): kof.net não é superfície JS no v1 —
        // o import recusa no compile com código nomeado, em vez do shim de
        // runtime (NETN001; nenhum sub-alvo browser/Node é introduzido).
        if (driver.target == Target.JS && !driver.interpreting && diagnostics != null) {
            for (String imp : unit.imports()) {
                String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
                if (base.equals("kof.net") || base.equals("net") || base.startsWith("kof.net.")) {
                    SourcePosition pos = unit.position();
                    diagnostics.error(pos != null ? pos.file() : driver.currentSourceName,
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            "The kof.net front is not a JS v1 surface — refusing 'import " + imp
                                    + "' (NETN001: D-NET-JS-V1; the network verbs have no JS target in v1)",
                            "NETN001");
                }
            }
        }
        List<IRClass> classes = new ArrayList<>();
        List<IRMethod> topLevelFunctions = new ArrayList<>();
        String moduleName = unit.packageName().isEmpty() ? "Default" : unit.packageName().replace('.', '/');
        int nextTypeId = 10;
        // #934: a tabela de assinaturas `extern` é propriedade do MÓDULO INTEIRO —
        // um call-site num arquivo que mescla ANTES da declaração `extern` tem de
        // baixar pelo MESMO caminho FFI (marshal `String`→`char*`) do que mescla
        // depois. Preenchida DURANTE o laço (abaixo), a visibilidade virava ordem
        // de compilação: o call-site antigo perdia o gate do dispatcher
        // (`externSignatures.containsKey`), caía no bare-call e emitia um
        // `invokestatic Default/Main.access` fantasma no JVM (VerifyError no load)
        // ou o handle de String CRU no rdi do `call access` no Native (ENOENT
        // silencioso). Coletar antes de baixar QUALQUER corpo torna a tabela
        // independente da ordem (D-KOF-FIRST R6: nunca silencioso).
        for (AstNode d : unit.declarations()) {
            if (d instanceof ExternalFunctionNode ext) {
                driver.externSignatures
                        .computeIfAbsent(ext.name(), n -> new java.util.ArrayList<>())
                        .add(ext);
            }
        }
        for (AstNode decl : unit.declarations()) {
            String declPkg = driver.declPackage(decl, unit.packageName());
            if (decl instanceof ClassDeclarationNode cls) classes.add(CompilerClassLowering.lowerClass(driver, cls, declPkg, nextTypeId++));
            else if (decl instanceof InterfaceDeclarationNode iface) classes.add(CompilerIfaceRecordLowering.lowerInterface(driver, iface, declPkg, nextTypeId++));
            else if (decl instanceof RecordDeclarationNode rec) classes.add(CompilerIfaceRecordLowering.lowerRecord(driver, rec, declPkg, nextTypeId++));
            else if (decl instanceof EnumDeclarationNode en) classes.add(CompilerEnumLowering.lowerEnum(driver, en, declPkg, nextTypeId++)); // #445
            else switch (decl) {
                case EntityDeclarationNode ent -> {
                    driver.entitySchemas.put(ent.name(), ent.fields());
                    if (ent.modifiers().contains("audited")) driver.auditedEntities.add(ent.name()); // entity-history 2b
                    List<RecordComponentNode> components = new java.util.ArrayList<>();
                    for (EntityFieldNode f : ent.fields()) {
                        components.add(new RecordComponentNode(f.position(), List.of(), f.type(), f.name(), null));
                    }
                    classes.add(CompilerIfaceRecordLowering.lowerRecord(driver, new RecordDeclarationNode(ent.position(), ent.name(),
                            ent.modifiers(), null, List.of(), components, List.of()),
                            declPkg, nextTypeId++));
                }
                case FunctionDeclarationNode func -> {
                    topLevelFunctions.add(CompilerFunctionLowering.lowerFunction(driver, func));
                    topLevelFunctions.addAll(CompilerFunctionLowering.lowerFunctionDefaults(driver, func));
                }
                case ExternalFunctionNode ext -> {
                    // #763 (`D-MAINT-BATCH-0610`/B): mesmo nome ≠ colisão — vira
                    // lista de candidatos; a seleção é por assinatura no typer.
                    // #934: a POPULAÇÃO da tabela mudou para o pré-lasso acima —
                    // aqui fica só o diagnóstico de binding (a lista já contém
                    // TODAS as decls do módulo, em qualquer ordem de arquivo).
                    // FFI (TIER 2.1.3/2.1.7): binding suportado (JVM Int→Int,
                    // String→Int, Double→Double; Native Int→Int, String→Int) não é
                    // gap; o resto é gap honesto por target — FFI002 no JS (web/edge
                    // sem FFI nativo), FFI001 nos demais. Nunca stub silencioso (R6).
                    if (diagnostics != null && !CompilerFfiBinding.isExternBound(driver, ext)) {
                        SourcePosition sp = ext.position();
                        String lib = ext.library() != null ? " in " + ext.library() : "";
                        // #667: a interpretação (Script) roda com target=JVM, então
                        // o rótulo/mensagem precisam nomear o alvo REAL (script).
                        boolean jsTarget = driver.target == Target.JS && !driver.interpreting;
                        String code = jsTarget ? "FFI002" : "FFI001";
                        String targetName = driver.interpreting ? "script" : String.valueOf(driver.target);
                        String msg = jsTarget
                                ? "extern '" + ext.name() + "'" + lib + ": FFI signature not bound on the JS target yet (FFI002)"
                                : "extern '" + ext.name() + "'" + lib + ": FFI binding not implemented on the "
                                        + targetName + " target yet (FFI001)";
                        diagnostics.error(sp != null ? sp.file() : "", sp != null ? sp.line() : 0,
                                sp != null ? sp.column() : 0, 0, msg, code);
                    }
                }
                case null, default -> { }  // no-op p/ null ou tipo nao-casado (paridade com o if-else original)
            }
        }
        if (!topLevelFunctions.isEmpty()) {
            String mainClassName = moduleName.isEmpty() ? "Main" : moduleName + "/Main";
            classes.add(0, new IRClass(mainClassName, "java/lang/Object", List.of(),
                    AccessFlags.PUBLIC | AccessFlags.SUPER, List.of(), topLevelFunctions, List.of(), null, 0));
        }
        classes.addAll(driver.syntheticClasses);
        return new IRModule(moduleName, classes, imports, driver.currentSourceName,
                driver.currentSourceContent);
    }
}
