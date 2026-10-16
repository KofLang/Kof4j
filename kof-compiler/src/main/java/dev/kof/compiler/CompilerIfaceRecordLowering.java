package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * §355 (rio da erasure) — lowering de INTERFACES e RECORDS para IR, extraído
 * de {@code CompilerClassLowering} (gate de 500: a classe-mãe estava em 599
 * e não podia cruzar os 600 com o fix do #385). Responsabilidade própria: o
 * IR de {@code interface} (type-params → descritores apagados, §356/#385) e
 * o IR de {@code record} (accessors + bridges, §131/#213).
 */
public final class CompilerIfaceRecordLowering {

    private CompilerIfaceRecordLowering() {}

    static IRClass lowerInterface(CompilerDriver driver, InterfaceDeclarationNode iface,
                            String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, iface.name());
        List<String> ifaces = iface.interfaces().stream().map(n -> CompilerAnnotations.externalOrLocalInternalName(driver, CompilerClassLowering.eraseTypeArgs(n))).toList();
        int access = driver.computeAccess(iface.modifiers()) | AccessFlags.ABSTRACT | AccessFlags.INTERFACE;
        List<IRMethod> methods = new ArrayList<>();
        List<IRField> fields = new ArrayList<>();
        // §385 (família §356): type-params da interface GENÉRICA eram jogados
        // fora (List.of() no lowerMethod/lowerField) — `get(): T` resolvia para
        // o ClassType fantasma ("","T") e o METHOD DESCRITOR saia `()LT;`
        // literal (classe válida, descritor chamativo): a chamada em
        // `Wrapper<String>` era `invokeinterface Wrapper.get()Ljava/lang/Object;`
        // e a JVM não achava o método → NoSuchMethodError. O symbol SEMÂNTICO
        // já apagava certo (#160); só o IR baixava cru.
        List<String> typeParams = iface.typeParameters() == null ? List.of() : iface.typeParameters();
        for (AstNode member : iface.members()) {
            if (member instanceof MethodDeclarationNode method) {
                methods.add(CompilerClassLowering.lowerMethod(driver, method, internalName, true, typeParams));
            } else if (member instanceof FieldDeclarationNode field) {
                IRField irF = CompilerClassLowering.lowerField(driver, field, typeParams);
                int fAccess = irF.accessFlags() | AccessFlags.PUBLIC | AccessFlags.STATIC | AccessFlags.FINAL;
                fields.add(new IRField(irF.name(), irF.type(), fAccess, irF.initialValue(), irF.annotations()));
            }
        }
        return new IRClass(internalName, "java/lang/Object", ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, iface.annotations()));
    }

    static IRClass lowerRecord(CompilerDriver driver, RecordDeclarationNode rec,
                       String packageName, int typeId) {
        String internalName = driver.toInternalName(packageName, rec.name());
        String superName = rec.superClass() != null ? driver.toInternalName("", CompilerClassLowering.eraseTypeArgs(rec.superClass())) : "java/lang/Record";
        List<String> ifaces = rec.interfaces().stream().map(n -> CompilerAnnotations.externalOrLocalInternalName(driver, CompilerClassLowering.eraseTypeArgs(n))).toList();
        int access = driver.computeAccess(rec.modifiers()) | AccessFlags.FINAL | AccessFlags.PUBLIC;
        List<IRField> fields = new ArrayList<>();
        List<IRMethod> methods = new ArrayList<>();
        List<String> typeParams = rec.typeParameters() == null ? List.of() : rec.typeParameters();
        for (RecordComponentNode comp : rec.components()) {
            fields.add(new IRField(comp.name(), CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer),
                    AccessFlags.PRIVATE | AccessFlags.FINAL,
                    null, CompilerAnnotations.lowerAnnotations(driver, comp.annotations())));
        }
        for (AstNode member : rec.members()) {
            if (member instanceof FieldDeclarationNode field) {
                IRField irField = CompilerClassLowering.lowerField(driver, field, typeParams);
                fields.add(irField);
            }
        }
        // bug #53: se o record declara um construtor explícito com a MESMA
        // aridade do canônico (número de componentes), NÃO gerar o automático —
        // senão dois <init> no JVM → ClassFormatError. O canônico explícito é
        // lowered em lowerRecord (membros) e substitui o gerado.
        boolean hasCanonicalCtor = rec.members().stream()
                .filter(m -> m instanceof ConstructorDeclarationNode)
                .anyMatch(c -> ((ConstructorDeclarationNode) c).parameters().size()
                        == rec.components().size());
        if (!hasCanonicalCtor) {
            methods.add(0, CompilerRecordSupport.generateRecordConstructor(driver, rec, internalName));
        }
        methods.addAll(CompilerRecordSupport.generateRecordDefaultOverloads(driver, rec, internalName));
        Type ownerType = CompilerTypes.ownerTypeFromInternal(internalName, driver.semanticAnalyzer);
        // §714: um acessor EXPLÍCITO declarado pelo usuário (método de mesmo
        // nome e 0 parâmetros) SUBSTITUI o acessor sintético do componente —
        // como no record do Java, onde `record R(int x) { int x() {...} }` é
        // legal e o acesso explícito vence. Sem isto o IR ganha DOIS métodos
        // `x()` idênticos: o JVM emite uma classe inválida (ClassFormatError
        // no load), o native `as: symbol 'R_x' is already defined`, e o Script
        // diverge (o sintético vencia). O método do usuário é lowered abaixo.
        java.util.Set<String> explicitZeroArgMethods = new java.util.HashSet<>();
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode m && m.parameters().isEmpty()) {
                explicitZeroArgMethods.add(m.name());
            }
        }
        for (RecordComponentNode comp : rec.components()) {
            if (explicitZeroArgMethods.contains(comp.name())) continue;
            Type compType = CompilerTypes.resolveWithTypeParams(comp.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
            List<KofOperation> body = new ArrayList<>();
            body.add(new KofLoadLocal(ownerType, 0));
            body.add(new KofLoadField(ownerType, comp.name(), compType));
            body.add(new KofReturn(compType));
            methods.add(new IRMethod(comp.name(), compType, List.of(), AccessFlags.PUBLIC, List.of(),
                    List.of(new IRBasicBlock(0, body)),
                    List.of(new IRLocalVariable(0, "this", ownerType))));
        }
        for (AstNode member : rec.members()) {
            if (member instanceof FieldDeclarationNode field && !field.modifiers().contains("static")) {
                if (explicitZeroArgMethods.contains(field.name())) continue;
                Type fieldType = CompilerTypes.resolveWithTypeParams(field.type(), typeParams, driver.currentUnit, driver.semanticAnalyzer);
                List<KofOperation> body = new ArrayList<>();
                body.add(new KofLoadLocal(ownerType, 0));
                body.add(new KofLoadField(ownerType, field.name(), fieldType));
                body.add(new KofReturn(fieldType));
                methods.add(new IRMethod(field.name(), fieldType, List.of(), AccessFlags.PUBLIC, List.of(),
                        List.of(new IRBasicBlock(0, body)),
                        List.of(new IRLocalVariable(0, "this", ownerType))));
            }
        }
        for (AstNode member : rec.members()) {
            if (member instanceof MethodDeclarationNode method) {
                methods.add(CompilerClassLowering.lowerMethod(driver,method, internalName, false, typeParams));
            } else if (member instanceof ConstructorDeclarationNode ctor) {
                methods.add(CompilerClassLowering.lowerConstructor(driver,ctor, internalName, "java/lang/Record",
                        typeParams, fields, java.util.Map.of()));
            }
        }
        // Native: records não geram toString/equals nos backends (JVM/JS geram
        // nos seus emitters). Sintetiza no IR para paridade — bug 11 (native `==`
        // dava undefined reference) e toString imprimia o handle.
        // #818 (face JVM): um record que ESTENDE uma classe do usuário perde a
        // superclasse `java/lang/Record`; a correção JVM é o `JvmBackend` passar a
        // reconhecer `clazz.isRecord()` e rodar o `JvmRecordEmitter` (que já trata
        // os componentes category-2 — Double/Long — corretamente). NÃO se sintetiza
        // mais no IR para o JVM: aqueles builders são orientados ao native e
        // tratavam o slot de 64 bits como int (regressão #823: Long → VerifyError
        // `long_2nd`; Double → ClassFormatError pelo owner primitivo no call de hash).
        boolean nativeTarget = driver.target == Target.NATIVE || driver.target == Target.NATIVE_RISCV64
                || driver.target == Target.NATIVE_AARCH64;
        if (nativeTarget) {
            methods.add(CompilerRecordSupport.buildRecordToStringMethod(driver, internalName, rec, fields, typeParams));
            methods.add(CompilerRecordSupport.buildRecordEqualsMethod(driver, internalName, fields, typeParams));
            methods.add(CompilerRecordSupport.buildRecordHashCodeMethod(driver, internalName, fields, typeParams));
        }
        // #613 (face Native do #608): um record que implementa interface
        // genérica precisa do bridge de erasure, igual a `lowerClass` (§356/§483)
        // — `lowerRecord` nunca chamava `generateCovariantReturnBridges`; o
        // invokeinterface/despacho da interface usa o descritor APAGADO
        // (`Object get()`), e sem bridge o slot caía no concreto cru (`Int get()`
        // lido como referência → SIGSEGV no Native). Bridges PRIMEIRO: o slot da
        // vtable é semeado pela interface (§248) e o bridge precisa ocupá-lo
        // (§483) antes de o concreto sobrescrever por nome. O mangling dos dois
        // é desempatado pelo sufixo de retorno do bridge (#613,
        // NativeSymbolMangling).
        if (driver.target.isNative()) {
            List<IRMethod> bridges = CompilerRecordSupport.generateCovariantReturnBridges(
                    driver, internalName, superName, ifaces, methods);
            methods.addAll(0, bridges);
        }
        // #608 (face JVM — PR #609/#614, autoria Publio Santos, conteúdo d5455124):
        // mesma lacuna do gate Native acima, no JVM — o invokeinterface do call
        // site usa o descritor APAGADO da interface (`Object get()`); sem o
        // bridge, o slot fica sem implementação → AbstractMethodError no
        // load/1ª chamada. Os gates são complementares (JVM × Native).
        if (driver.target == Target.JVM) {
            List<IRMethod> bridges = CompilerRecordSupport.generateCovariantReturnBridges(
                    driver, internalName, superName, ifaces, methods);
            methods.addAll(bridges);
        }
        // TIER 2.7 (D-VALUE-RECORD, 2.7.3/2.7.4): a flag `value` viaja para a
        // IR — o emissor JS congela o objeto (ABI JS) e o Native consulta-a p/
        // a decisão de ABI registrada no plano (o heap atual já entrega a
        // semântica de valor: imutável + igualdade estrutural).
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, rec.annotations()), true,
                rec.modifiers().contains("value"));
    }
}
