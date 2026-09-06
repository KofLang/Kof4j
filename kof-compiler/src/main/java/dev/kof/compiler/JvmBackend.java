package dev.kof.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;


class JvmBackend implements Backend {

    /** Classpath externo para computar ancestrais comuns de frames (android.*). */
    private ExternalClasspath externalTypes;

    void setExternalTypes(ExternalClasspath externalTypes) {
        this.externalTypes = externalTypes;
    }

    private final Map<LabelId, Label> labelMap = new HashMap<>();

    private record TryRegion(LabelId start, LabelId end) {
    }

    private record TryCatchEntry(LabelId start, LabelId end, LabelId handler, String type) {
    }

    private final java.util.Deque<TryRegion> tryStack = new java.util.ArrayDeque<>();
    private final List<TryCatchEntry> tryCatches = new java.util.ArrayList<>();

    Label resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> new Label());
    }

    void pushTryRegion(LabelId start, LabelId end) {
        tryStack.push(new TryRegion(start, end));
    }

    void popTryRegion() {
        tryStack.pop();
    }

    void registerTryCatch(KofCatchStart kcs) {
        TryRegion region = tryStack.peek();
        if (region != null) {
            tryCatches.add(new TryCatchEntry(region.start(), region.end(),
                    kcs.handlerLabel(), kcs.exceptionType()));
        }
    }

    private Type classTypeFromInternal(String internalName) {
        int slashIdx = internalName.lastIndexOf('/');
        if (slashIdx >= 0) {
            return new Type.ClassType(internalName.substring(0, slashIdx).replace('/', '.'), internalName.substring(slashIdx + 1), List.of());
        }
        return new Type.ClassType("", internalName, List.of());
    }

    @Override
    public void emit(IRModule module, Path outputDir) throws IOException {
        emit(module, outputDir, true);
    }

    private IRModule currentModule;
    private IRClass currentClass;

    @Override
    public void emit(IRModule module, Path outputDir, boolean debugInfo) throws IOException {
        this.currentModule = module;
        this.sourceName = module.sourceName();
        this.debugInfoEnabled = debugInfo;
        for (IRClass clazz : module.classes()) {
            emitClass(clazz, outputDir);
        }
        if (usesJson || usesVk || usesExtern) {
            JvmRuntime.ensureCompiled(outputDir, module.classes(), usesVk, usesExtern);
        }
    }

    private boolean debugInfoEnabled = true;

    boolean usesJson = false;
    boolean usesVk = false;

    private String sourceName;

    private void emitClass(IRClass clazz, Path outputDir) throws IOException {
        this.currentClass = clazz;
        Path classFile = outputDir.resolve(clazz.name() + ".class");
        Files.createDirectories(classFile.getParent());

        // getCommonSuperClass: ASM consulta Class.forName; classes externas
        // (android.* etc.) não estão no classpath da compilação. Caminhamos
        // nas DUAS hierarquias (classpath externo + JDK) e devolvemos o
        // ancestral comum real — nunca um palpite que corrompa os frames.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (type1.equals(type2)) return type1;
                java.util.Set<String> ancestors2 = ancestorSet(type2);
                String current = type1;
                int hops = 0;
                while (current != null && hops++ < 64) {
                    if (ancestors2.contains(current)) return current;
                    if ("java/lang/Object".equals(current)) break;
                    current = superClassOfInternal(current);
                }
                // sem ancestral computável: Object é o único seguro
                return "java/lang/Object";
            }

            private java.util.Set<String> ancestorSet(String type) {
                java.util.Set<String> set = new java.util.HashSet<>();
                String current = type;
                int hops = 0;
                while (current != null && hops++ < 64) {
                    set.add(current);
                    if ("java/lang/Object".equals(current)) break;
                    current = superClassOfInternal(current);
                }
                return set;
            }

            private String superClassOfInternal(String internalName) {
                if (externalTypes != null && externalTypes.knows(internalName)) {
                    String sup = externalTypes.superClassOf(internalName);
                    return sup != null ? sup : "java/lang/Object";
                }
                try {
                    Class<?> c = Class.forName(internalName.replace('/', '.'), false,
                            JvmBackend.class.getClassLoader());
                    Class<?> sup = c.getSuperclass();
                    return sup != null ? sup.getName().replace('.', '/') : "java/lang/Object";
                } catch (Throwable e) {
                    return "java/lang/Object";
                }
            }
        };
        String superName = clazz.superName() != null ? clazz.superName() : "java/lang/Object";
        cw.visit(V21, clazz.accessFlags(), clazz.name(), clazz.signature(),
                superName, clazz.interfaces().toArray(new String[0]));
        if (sourceName != null && debugInfoEnabled) {
            cw.visitSource(sourceName, null);
        }
        JvmAnnotations.emitAnnotations(cw::visitAnnotation, clazz.annotations());

        for (IRField field : clazz.fields()) {
            String desc = JvmTypeMapper.toDescriptor(field.type());
            var fv = cw.visitField(field.accessFlags(), field.name(), desc, null, field.initialValue());
            JvmAnnotations.emitAnnotations(fv::visitAnnotation, field.annotations());
            fv.visitEnd();
        }

        if ("java/lang/Record".equals(superName)) {
            for (IRField field : clazz.fields()) {
                cw.visitRecordComponent(field.name(), JvmTypeMapper.toDescriptor(field.type()), null).visitEnd();
            }
        }

        for (IRMethod method : clazz.methods()) {
            try {
                emitMethod(cw, clazz.name(), method, clazz.superName());
            } catch (RuntimeException e) {
                throw new RuntimeException("frame crash em " + clazz.name() + "."
                        + method.name() + " (super=" + superName + "): " + e.getMessage(), e);
            }
        }

        if ("java/lang/Record".equals(superName)) {
            JvmRecordEmitter.emitRecordMethods(cw, clazz);
        }

        cw.visitEnd();
        Files.write(classFile, cw.toByteArray());
    }

    private void emitMethod(ClassWriter cw, String className, IRMethod method, String classSuperName) {
        String desc = JvmTypeMapper.toMethodDescriptor(method.returnType(), method.parameterTypes());
        MethodVisitor mv = cw.visitMethod(method.accessFlags(), method.name(), desc,
                null, method.thrownExceptions().toArray(new String[0]));
        JvmAnnotations.emitAnnotations(mv::visitAnnotation, method.annotations());
        if (!method.parameterAnnotations().isEmpty()) {
            for (int i = 0; i < method.parameterAnnotations().size(); i++) {
                int paramIndex = i;
                JvmAnnotations.emitAnnotations((v, visible) -> mv.visitParameterAnnotation(paramIndex, v, visible),
                        method.parameterAnnotations().get(i));
            }
        }
        if ((method.accessFlags() & ACC_ABSTRACT) != 0 || method.basicBlocks().isEmpty()) {
            mv.visitEnd();
            return;
        }
        mv.visitCode();

        List<KofOperation> ops = method.basicBlocks().stream()
                .flatMap(b -> b.operations().stream())
                .collect(Collectors.toList());
        if ("<init>".equals(method.name())) {
            String superName = classSuperName != null ? classSuperName : "java/lang/Object";
            // Only a super(...) or this(...) as the constructor's own super
            // invocation suppresses the implicit super(); a CONSTRUCTOR call
            // to any other class (e.g. FixedClock(0) in a field assignment)
            // must NOT count — otherwise Object.<init> is never called and
            // the verifier rejects the class.
            boolean hasSuperOrThisCall = ops.stream().anyMatch(op -> {
                if (!(op instanceof KofCall kc) || kc.kind() != KofCallKind.CONSTRUCTOR) return false;
                if (kc.ownerType() instanceof Type.ClassType ct) {
                    String internal = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
                    return internal.equals(superName) || internal.equals(className);
                }
                return false;
            });
            if (!hasSuperOrThisCall) {

                Type thisType = classTypeFromInternal(className);
                ops.add(0, new KofCall(classTypeFromInternal(superName),
                        "<init>", List.of(), Type.PrimitiveType.VOID, KofCallKind.CONSTRUCTOR));
                ops.add(0, new KofLoadLocal(thisType, 0));
            }
        }

        int maxStack = 0;
        int maxLocals = 0;
        for (IRBasicBlock block : method.basicBlocks()) {
            maxLocals = Math.max(maxLocals, JvmLiteralEmitter.computeLocals(block.operations()));
            maxStack = Math.max(maxStack, JvmLiteralEmitter.computeStack(block.operations()));
        }
        java.util.Map<KofOperation, SourcePosition> debugPositions =
                method.debugInfo() != null ? method.debugInfo().positions() : java.util.Map.of();
        Label debugStart = null;
        if (debugInfoEnabled) {
            debugStart = new Label();
            mv.visitLabel(debugStart);
        }
        int lastLine = -1;
        for (KofOperation op : ops) {
            SourcePosition pos = debugPositions.get(op);
            if (pos != null && pos.line() != lastLine && debugInfoEnabled) {
                Label lineLabel = new Label();
                mv.visitLabel(lineLabel);
                mv.visitLineNumber(pos.line(), lineLabel);
                lastLine = pos.line();
            }
            emitOperation(mv, className, op);
        }
        if (debugInfoEnabled && debugStart != null) {
            Label debugEnd = new Label();
            mv.visitLabel(debugEnd);
            for (IRLocalVariable local : method.localVariables()) {
                mv.visitLocalVariable(local.name(), JvmTypeMapper.toDescriptor(local.type()), null,
                        debugStart, debugEnd, local.index());
            }
        }

        for (TryCatchEntry entry : tryCatches) {
            mv.visitTryCatchBlock(resolveLabel(entry.start()), resolveLabel(entry.end()),
                    resolveLabel(entry.handler()), exceptionJvmType(entry.type()));
        }
        tryCatches.clear();
        tryStack.clear();

        try {
            mv.visitMaxs(maxStack, maxLocals);
        } catch (RuntimeException e) {
            // re-emit num ClassWriter COMPUTE_MAXS + TraceClassVisitor: mostra
            // o bytecode exato que quebrou o COMPUTE_FRAMES
            if (Boolean.getBoolean("kof.trace.asm")) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    org.objectweb.asm.util.Printer pr = new org.objectweb.asm.util.Textifier();
                    org.objectweb.asm.MethodVisitor dump = new org.objectweb.asm.util.TraceMethodVisitor(pr);
                    for (KofOperation op : ops) emitOperation(dump, className, op);
                    pr.print(new java.io.PrintWriter(sw, true));
                    System.err.println("=== bytecode de " + className + "." + method.name() + " ===");
                    System.err.println(sw);
                } catch (Throwable t2) {
                    System.err.println("trace.asm falhou: " + t2);
                }
            }
            if (Boolean.getBoolean("kof.trace.ir")) {
                System.err.println("=== IR ops de " + className + "." + method.name() + " ===");
                for (IRBasicBlock block : method.basicBlocks()) {
                    for (KofOperation op : block.operations()) {
                        System.err.println("  " + op);
                    }
                }
            }
            throw e;
        }
        mv.visitEnd();
    }

    private String exceptionJvmType(String kofType) {
        if ("String".equals(kofType)) return "java/lang/RuntimeException";
        return "java/lang/" + kofType;
    }

    private void emitOperation(MethodVisitor mv, String className, KofOperation op) {

        JvmOpEmitter.emit(this, new JvmOpEmitter.OpContext(mv, className, currentModule, currentClass), op);

    }
}
