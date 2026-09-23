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
        for (RecordComponentNode comp : rec.components()) {
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
        // Native: records não geram toString/equals nos backends (JVM/JS
        // geram nos seus emitters). Sintetiza no IR para paridade — bug 11
        // (native `==` dava undefined reference) e toString imprimia o handle.
        if (driver.target == Target.NATIVE || driver.target == Target.NATIVE_RISCV64
                || driver.target == Target.NATIVE_AARCH64) {
            methods.add(CompilerRecordSupport.buildRecordToStringMethod(driver, internalName, rec, fields, typeParams));
            methods.add(CompilerRecordSupport.buildRecordEqualsMethod(driver, internalName, fields, typeParams));
            methods.add(CompilerRecordSupport.buildRecordHashCodeMethod(driver, internalName, fields, typeParams));
        }
        if (driver.target == Target.JVM) {
            // #603 (mesma face do §356, nunca portada pra record): um record
            // que `implements` uma interface genérica (ou qualquer interface
            // cujo método apagado difira do concreto) nunca ganhava o bridge
            // de erasure — só `CompilerClassLowering.lowerClass` chamava
            // `generateCovariantReturnBridges`. O invokeinterface do call site
            // usa o descritor APAGADO da interface; sem o bridge, o slot fica
            // sem implementação → AbstractMethodError no load/1ª chamada.
            //
            // Escopo: JVM apenas. O Native já tinha um comportamento diferente
            // e pré-existente aqui (saída silenciosamente vazia, não medi a
            // causa) — estender `generateCovariantReturnBridges` pra records
            // no Native colide o símbolo mangled do bridge com o método
            // concreto (`IntBox_get` duplicado: o backend nativo não separa
            // por descritor como o JVM faz). Esse mangling é um problema
            // separado do backend nativo, não desta lacuna de lowering —
            // fica para uma issue própria.
            List<IRMethod> bridges = CompilerRecordSupport.generateCovariantReturnBridges(
                    driver, internalName, superName, ifaces, methods);
            methods.addAll(bridges);
        }
        return new IRClass(internalName, superName, ifaces, access, fields, methods, List.of(), null,
                typeId, CompilerAnnotations.lowerAnnotations(driver, rec.annotations()));
    }
}
