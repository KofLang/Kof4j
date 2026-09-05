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

    private Label resolveLabel(LabelId id) {
        return labelMap.computeIfAbsent(id, k -> new Label());
    }

    private Type classTypeFromInternal(String internalName) {
        int slashIdx = internalName.lastIndexOf('/');
        if (slashIdx >= 0) {
            return new Type.ClassType(internalName.substring(0, slashIdx).replace('/', '.'), internalName.substring(slashIdx + 1), List.of());
        }
        return new Type.ClassType("", internalName, List.of());
    }

    /** Valor default do primitivo (0/false/0.0) na pilha, com width correto. */
    private void emitDefaultValue(MethodVisitor mv, Type type) {
        String n = type instanceof Type.PrimitiveType pt ? pt.name() : "";
        switch (Type.canonicalPrimitiveName(n)) {
            case "long" -> { mv.visitInsn(LCONST_0); }
            case "float" -> { mv.visitInsn(FCONST_0); }
            case "double" -> { mv.visitInsn(DCONST_0); }
            default -> mv.visitInsn(ICONST_0);
        }
    }

    private String unboxMethodName(Type boxed) {
        if (boxed instanceof Type.ClassType ct) {
            return switch (ct.name()) {
                case "Integer" -> "intValue";
                case "Long" -> "longValue";
                case "Boolean" -> "booleanValue";
                case "Float" -> "floatValue";
                case "Double" -> "doubleValue";
                case "Character" -> "charValue";
                case "Byte" -> "byteValue";
                case "Short" -> "shortValue";
                default -> "intValue";
            };
        }
        return "intValue";
    }

    private String boxedClassNameFor(Type primitive) {
        if (primitive instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "long", "Long" -> "java/lang/Long";
                case "float", "Float" -> "java/lang/Float";
                case "double", "Double" -> "java/lang/Double";
                case "boolean", "bool", "Bool" -> "java/lang/Boolean";
                case "byte", "Byte" -> "java/lang/Byte";
                case "short", "Short" -> "java/lang/Short";
                default -> "java/lang/Integer";
            };
        }
        // kof.ui handles (Color, Theme, Label, Button, Input, Column, Row,
        // View, Style, Window) are Int values on every target; on the JVM
        // they must be boxed when stored in Object slots (e.g. List<Label>).
        if (dev.kof.compiler.KofUi.isUiType(primitive) || KofMedia.isHandleType(primitive)) {
            return "java/lang/Integer";
        }
        return null;
    }

    private void emitBoxIfPrimitive(MethodVisitor mv, Type type) {
        String boxed = boxedClassNameFor(type);
        if (boxed != null) {
            String desc = JvmTypeMapper.toDescriptor(type);
            if ("char".equals(typeName(type)) || "Char".equals(typeName(type))) desc = "I";
            if (KofUi.isUiType(type) || KofMedia.isHandleType(type)) desc = "I";
            mv.visitMethodInsn(INVOKESTATIC, boxed, "valueOf", "(" + desc + ")L" + boxed + ";", false);
        }
    }

    private boolean isPrimitiveType(Type type) {
        if (type instanceof Type.NullableType nt) return isPrimitiveType(nt.inner());
        return type instanceof Type.PrimitiveType pt && !"void".equals(pt.name());
    }

    private void emitUnboxIfPrimitive(MethodVisitor mv, Type type) {
        String boxed = boxedClassNameFor(type);
        if (boxed != null) {
            mv.visitTypeInsn(CHECKCAST, boxed);
            String method = boxed.endsWith("Integer") ? "intValue"
                    : boxed.endsWith("Long") ? "longValue"
                    : boxed.endsWith("Boolean") ? "booleanValue"
                    : boxed.endsWith("Float") ? "floatValue"
                    : boxed.endsWith("Double") ? "doubleValue"
                    : boxed.endsWith("Byte") ? "byteValue"
                    : boxed.endsWith("Short") ? "shortValue" : "intValue";
            mv.visitMethodInsn(INVOKEVIRTUAL, boxed, method, "()" + JvmTypeMapper.toDescriptor(type), false);
        }
    }

    private boolean isPrimitiveOf(Type type, String name) {
        if (type instanceof Type.NullableType nt) return isPrimitiveOf(nt.inner(), name);
        return type instanceof Type.PrimitiveType pt && (pt.name().equals(name) || pt.name().equals(capitalize(name)));
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private int intCompareOpcode(KofBinaryOp op) {
        return switch (op) {
            case EQ -> IFEQ;
            case NE -> IFNE;
            case LT -> IFLT;
            case LE -> IFLE;
            case GT -> IFGT;
            case GE -> IFGE;
            default -> IFEQ;
        };
    }

    private String typeName(Type type) {
        return type instanceof Type.PrimitiveType pt ? pt.name() : "";
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
        if (usesJson || usesVk) {
            JvmRuntime.ensureCompiled(outputDir, module.classes(), usesVk);
        }
    }

    private boolean debugInfoEnabled = true;
    private boolean usesJson = false;
    private boolean usesVk = false;
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
        emitAnnotations(cw::visitAnnotation, clazz.annotations());

        for (IRField field : clazz.fields()) {
            String desc = JvmTypeMapper.toDescriptor(field.type());
            var fv = cw.visitField(field.accessFlags(), field.name(), desc, null, field.initialValue());
            emitAnnotations(fv::visitAnnotation, field.annotations());
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
            emitRecordMethods(cw, clazz);
        }

        cw.visitEnd();
        Files.write(classFile, cw.toByteArray());
    }

    private void emitRecordMethods(ClassWriter cw, IRClass clazz) {
        List<IRField> fields = clazz.fields();
        String cn = clazz.name();
        String simpleName = cn.contains("/") ? cn.substring(cn.lastIndexOf('/') + 1) : cn;


        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
        mv.visitLdcInsn(simpleName + "[");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        for (int i = 0; i < fields.size(); i++) {
            IRField f = fields.get(i);
            if (i > 0) {
                mv.visitLdcInsn(", ");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
            mv.visitLdcInsn(f.name() + "=");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, cn, f.name(), JvmTypeMapper.toDescriptor(f.type()));
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", appendDescriptor(f.type()), false);
        }
        mv.visitLdcInsn("]");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();


        mv = cw.visitMethod(ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        Label same = new Label();
        mv.visitJumpInsn(IF_ACMPEQ, same);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(INSTANCEOF, cn);
        Label notSame = new Label();
        mv.visitJumpInsn(IFEQ, notSame);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, cn);
        mv.visitVarInsn(ASTORE, 2);
        for (IRField f : fields) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, cn, f.name(), JvmTypeMapper.toDescriptor(f.type()));
            mv.visitVarInsn(ALOAD, 2);
            mv.visitFieldInsn(GETFIELD, cn, f.name(), JvmTypeMapper.toDescriptor(f.type()));
            emitEqualsComparison(mv, f.type(), cn);
        }
        mv.visitLabel(same);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IRETURN);
        mv.visitLabel(notSame);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();


        mv = cw.visitMethod(ACC_PUBLIC, "hashCode", "()I", null, null);
        mv.visitCode();
        mv.visitInsn(ICONST_1);
        mv.visitVarInsn(ISTORE, 1);
        for (IRField f : fields) {
            mv.visitVarInsn(ILOAD, 1);
            mv.visitLdcInsn(31);
            mv.visitInsn(IMUL);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, cn, f.name(), JvmTypeMapper.toDescriptor(f.type()));
            emitHashContribution(mv, f.type());
            mv.visitInsn(IADD);
            mv.visitVarInsn(ISTORE, 1);
        }
        mv.visitVarInsn(ILOAD, 1);
        mv.visitInsn(IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitEqualsComparison(MethodVisitor mv, Type type, String cn) {
        if (type instanceof Type.PrimitiveType pt) {
            switch (pt.name()) {
                case "int", "Int", "byte", "Byte", "short", "Short", "char", "Char", "bool", "Bool" -> {
                    Label ok = new Label();
                    mv.visitJumpInsn(IF_ICMPEQ, ok);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                    mv.visitLabel(ok);
                }
                case "long", "Long" -> {
                    mv.visitInsn(LCMP);
                    Label ok = new Label();
                    mv.visitJumpInsn(IFEQ, ok);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                    mv.visitLabel(ok);
                }
                case "float", "Float" -> {
                    mv.visitInsn(FCMPL);
                    Label ok = new Label();
                    mv.visitJumpInsn(IFEQ, ok);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                    mv.visitLabel(ok);
                }
                case "double", "Double" -> {
                    mv.visitInsn(DCMPL);
                    Label ok = new Label();
                    mv.visitJumpInsn(IFEQ, ok);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(IRETURN);
                    mv.visitLabel(ok);
                }
                default -> throw new IllegalStateException("unhandled primitive in record equals: " + pt.name());
            }
        } else {
            mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            Label ok = new Label();
            mv.visitJumpInsn(IFNE, ok);
            mv.visitInsn(ICONST_0);
            mv.visitInsn(IRETURN);
            mv.visitLabel(ok);
        }
    }

    private void emitHashContribution(MethodVisitor mv, Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            switch (pt.name()) {
                case "int", "Int", "byte", "Byte", "short", "Short", "char", "Char", "bool", "Bool" -> { }
                case "long", "Long" -> {
                    mv.visitInsn(DUP2);
                    mv.visitLdcInsn(32);
                    mv.visitInsn(LUSHR);
                    mv.visitInsn(LXOR);
                    mv.visitInsn(L2I);
                }
                case "float", "Float" -> mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "floatToIntBits", "(F)I", false);
                case "double", "Double" -> {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "doubleToLongBits", "(D)J", false);
                    mv.visitInsn(DUP2);
                    mv.visitLdcInsn(32);
                    mv.visitInsn(LUSHR);
                    mv.visitInsn(LXOR);
                    mv.visitInsn(L2I);
                }
                default -> throw new IllegalStateException("unhandled primitive in record hashCode: " + pt.name());
            }
        } else {
            mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "hashCode", "(Ljava/lang/Object;)I", false);
        }
    }

    private String appendDescriptor(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "long", "Long" -> "(J)Ljava/lang/StringBuilder;";
                case "float", "Float" -> "(F)Ljava/lang/StringBuilder;";
                case "double", "Double" -> "(D)Ljava/lang/StringBuilder;";
                case "bool", "Bool" -> "(Z)Ljava/lang/StringBuilder;";
                case "char", "Char" -> "(C)Ljava/lang/StringBuilder;";
                default -> "(I)Ljava/lang/StringBuilder;";
            };
        }
        if (Type.isString(type)) return "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
        return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
    }

    private void emitMethod(ClassWriter cw, String className, IRMethod method, String classSuperName) {
        String desc = JvmTypeMapper.toMethodDescriptor(method.returnType(), method.parameterTypes());
        MethodVisitor mv = cw.visitMethod(method.accessFlags(), method.name(), desc,
                null, method.thrownExceptions().toArray(new String[0]));
        emitAnnotations(mv::visitAnnotation, method.annotations());
        if (!method.parameterAnnotations().isEmpty()) {
            for (int i = 0; i < method.parameterAnnotations().size(); i++) {
                int paramIndex = i;
                emitAnnotations((v, visible) -> mv.visitParameterAnnotation(paramIndex, v, visible),
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
            maxLocals = Math.max(maxLocals, computeLocals(block.operations()));
            maxStack = Math.max(maxStack, computeStack(block.operations()));
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

    // ── Annotations ─────────────────────────────────────────────────

    /**
     * Pacotes com retenção CLASS/SOURCE (não visíveis em runtime).
     * Tudo que não estiver aqui é emitido como RuntimeVisible — a escolha
     * conservadora para interop (frameworks Android/JUnit leem em runtime).
     */
    private static final List<String> INVISIBLE_PREFIXES = List.of(
            "java/lang/Override",
            "java/lang/SuppressWarnings",
            "androidx/annotation/",
            "javax/annotation/",
            "org/jetbrains/annotations/",
            "edu/umd/cs/findbugs/annotations/"
    );

    private static boolean retentionIsVisible(String internalName) {
        if (internalName == null) return true;
        for (String prefix : INVISIBLE_PREFIXES) {
            if (internalName.equals(prefix) || internalName.startsWith(prefix)) return false;
        }
        return true;
    }

    private interface AnnotationVisitorFactory {
        org.objectweb.asm.AnnotationVisitor create(String descriptor, boolean visible);
    }

    private void emitAnnotations(AnnotationVisitorFactory factory, List<IRAnnotation> annotations) {
        if (annotations == null) return;
        for (IRAnnotation anno : annotations) {
            String desc = "L" + anno.name() + ";";
            var av = factory.create(desc, retentionIsVisible(anno.name()));
            if (av != null) {
                for (var e : anno.values().entrySet()) {
                    // forma curta @Name("x"): chave null → elemento "value"
                    String key = e.getKey() != null ? e.getKey() : "value";
                    emitAnnotationValues(av, key, e.getValue());
                }
                av.visitEnd();
            }
        }
    }

    /**
     * Emite um valor de annotation: constante simples ou array {v1, v2}.
     */
    private void emitAnnotationValues(org.objectweb.asm.AnnotationVisitor av,
                                      String key, Object value) {
        if (value instanceof List<?> items) {
            var arr = av.visitArray(key);
            for (Object item : items) {
                if (item instanceof IRClassConstant cc) {
                    arr.visit(null, org.objectweb.asm.Type.getType("L" + cc.internalName() + ";"));
                } else if (item instanceof IREnumConstant ec) {
                    arr.visitEnum(null, "L" + ec.internalName() + ";", ec.constant());
                } else {
                    arr.visit(null, asmValue(item));
                }
            }
            arr.visitEnd();
            return;
        }
        if (value instanceof IRClassConstant cc) {
            av.visit(key, org.objectweb.asm.Type.getType("L" + cc.internalName() + ";"));
            return;
        }
        if (value instanceof IREnumConstant ec) {
            av.visitEnum(key, "L" + ec.internalName() + ";", ec.constant());
            return;
        }
        av.visit(key, asmValue(value));
    }

    private Object asmValue(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double
                || value instanceof Character) return value;
        return String.valueOf(value);
    }

    private void emitOperation(MethodVisitor mv, String className, KofOperation op) {
        if (op instanceof KofLoadLiteral lit) {
            emitLoadLiteral(mv, lit);
        } else if (op instanceof KofLoadLocal ll) {
            mv.visitVarInsn(loadVarOpcode(ll.type()), ll.index());
        } else if (op instanceof KofStoreLocal sl) {
            mv.visitVarInsn(storeVarOpcode(sl.type()), sl.index());
        } else if (op instanceof KofLoadField lf) {
            if (BuiltinTypes.isString(lf.ownerType()) && "length".equals(lf.name())) {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
            } else {
                String owner = JvmTypeMapper.toInternalName(
                        lf.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                        lf.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
                if (KofProcess.isResult(lf.ownerType())) {
                    owner = "dev/kof/runtime/KofRuntime$ProcessResult";
                }
                boolean isRecord = false;
                boolean isSelfRecordAccess = false;
                if (lf.ownerType() instanceof Type.ClassType ct) {
                    String internal = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
                    if (currentModule != null) {
                        for (IRClass c : currentModule.classes()) {
                            if (c.name().equals(internal) && "java/lang/Record".equals(c.superName())) {
                                isRecord = true;
                                // If we are inside the record's own accessor, use GETFIELD directly to avoid recursion
                                if (currentClass != null && currentClass.name().equals(internal)) {
                                    isSelfRecordAccess = true;
                                }
                                break;
                            }
                        }
                    }
                }
                if (isRecord && !isSelfRecordAccess) {
                    mv.visitMethodInsn(INVOKEVIRTUAL, owner, lf.name(), "()" + JvmTypeMapper.toDescriptor(lf.fieldType()), false);
                } else {
                    mv.visitFieldInsn(GETFIELD, owner, lf.name(), JvmTypeMapper.toDescriptor(lf.fieldType()));
                }
            }
        } else if (op instanceof KofStoreField sf) {
            String owner = JvmTypeMapper.toInternalName(
                    sf.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    sf.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(PUTFIELD, owner, sf.name(), JvmTypeMapper.toDescriptor(sf.fieldType()));
        } else if (op instanceof KofGetStatic gs) {
            String owner = JvmTypeMapper.toInternalName(
                    gs.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    gs.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(GETSTATIC, owner, gs.name(), JvmTypeMapper.toDescriptor(gs.fieldType()));
        } else if (op instanceof KofPutStatic ps) {
            String owner = JvmTypeMapper.toInternalName(
                    ps.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    ps.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            mv.visitFieldInsn(PUTSTATIC, owner, ps.name(), JvmTypeMapper.toDescriptor(ps.fieldType()));
        } else if (op instanceof KofBinary kb) {
            switch (kb.op()) {
                case ADD -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IADD));
                case SUB -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), ISUB));
                case MUL -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IMUL));
                case DIV -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IDIV));
                case MOD -> mv.visitInsn(opcodeForArithmetic(kb.operandType(), IREM));
                case AND -> mv.visitInsn(opcodeForBitwise(kb.operandType(), IAND));
                case OR -> mv.visitInsn(opcodeForBitwise(kb.operandType(), IOR));
                case XOR -> mv.visitInsn(opcodeForBitwise(kb.operandType(), IXOR));
                case SHL -> mv.visitInsn(opcodeForBitwise(kb.operandType(), ISHL));
                case SHR -> mv.visitInsn(opcodeForBitwise(kb.operandType(), ISHR));
                case USHR -> mv.visitInsn(opcodeForBitwise(kb.operandType(), IUSHR));
                case EQ, NE, LT, LE, GT, GE -> {
                    boolean isLong = isPrimitiveOf(kb.operandType(), "long");
                    boolean isFloat = isPrimitiveOf(kb.operandType(), "float");
                    boolean isDouble = isPrimitiveOf(kb.operandType(), "double");
                    // Unknown NÃO é referência (int não-inferido também infere Unknown)
                    boolean isRef = kb.operandType() instanceof Type.ClassType
                            || kb.operandType() instanceof Type.ArrayType
                            || kb.operandType() instanceof Type.TypeVariable
                            || (kb.operandType() instanceof Type.NullableType nt
                                && !(nt.inner() instanceof Type.PrimitiveType));
                    int cmpOpcode;
                    if (isRef) {
                        cmpOpcode = switch (kb.op()) {
                            case EQ -> IF_ACMPEQ;
                            case NE -> IF_ACMPNE;
                            default -> IF_ACMPEQ;
                        };
                    } else if (isLong) {
                        mv.visitInsn(LCMP);
                        cmpOpcode = intCompareOpcode(kb.op());
                    } else if (isFloat) {
                        mv.visitInsn(FCMPL);
                        cmpOpcode = intCompareOpcode(kb.op());
                    } else if (isDouble) {
                        mv.visitInsn(DCMPL);
                        cmpOpcode = intCompareOpcode(kb.op());
                    } else {
                        cmpOpcode = switch (kb.op()) {
                            case EQ -> IF_ICMPEQ;
                            case NE -> IF_ICMPNE;
                            case LT -> IF_ICMPLT;
                            case LE -> IF_ICMPLE;
                            case GT -> IF_ICMPGT;
                            case GE -> IF_ICMPGE;
                            default -> IF_ICMPEQ;
                        };
                    }
                    Label trueLabel = new Label();
                    Label endLabel = new Label();
                    mv.visitJumpInsn(cmpOpcode, trueLabel);
                    mv.visitInsn(ICONST_0);
                    mv.visitJumpInsn(GOTO, endLabel);
                    mv.visitLabel(trueLabel);
                    mv.visitInsn(ICONST_1);
                    mv.visitLabel(endLabel);
                }
            }
        } else if (op instanceof KofUnary ku) {
            if (ku.op() == KofUnaryOp.NEG) {
                mv.visitInsn(opcodeForArithmetic(ku.operandType(), INEG));
            } else if (ku.op() == KofUnaryOp.NOT) {
                Label trueLabel = new Label();
                Label endLabel = new Label();
                mv.visitInsn(ICONST_0);
                mv.visitJumpInsn(IF_ICMPEQ, trueLabel);
                mv.visitInsn(ICONST_0);
                mv.visitJumpInsn(GOTO, endLabel);
                mv.visitLabel(trueLabel);
                mv.visitInsn(ICONST_1);
                mv.visitLabel(endLabel);
            } else if (ku.op() == KofUnaryOp.I2L) {
                mv.visitInsn(I2L);
            } else if (ku.op() == KofUnaryOp.I2F) {
                mv.visitInsn(I2F);
            } else if (ku.op() == KofUnaryOp.I2D) {
                mv.visitInsn(I2D);
            } else if (ku.op() == KofUnaryOp.I2C) {
                mv.visitInsn(I2C);
            } else if (ku.op() == KofUnaryOp.L2I) {
                mv.visitInsn(L2I);
            } else if (ku.op() == KofUnaryOp.L2F) {
                mv.visitInsn(L2F);
            } else if (ku.op() == KofUnaryOp.L2D) {
                mv.visitInsn(L2D);
            } else if (ku.op() == KofUnaryOp.F2D) {
                mv.visitInsn(F2D);
            } else if (ku.op() == KofUnaryOp.D2F) {
                mv.visitInsn(D2F);
            } else if (ku.op() == KofUnaryOp.D2I) {
                mv.visitInsn(D2I);
            } else if (ku.op() == KofUnaryOp.F2I) {
                mv.visitInsn(F2I);
            } else if (ku.op() == KofUnaryOp.D2L) {
                mv.visitInsn(D2L);
            } else if (ku.op() == KofUnaryOp.F2L) {
                mv.visitInsn(F2L);
            }
        } else if (op instanceof KofLabel kl) {
            mv.visitLabel(resolveLabel(kl.label()));
        } else if (op instanceof KofJump kj) {
            mv.visitJumpInsn(GOTO, resolveLabel(kj.target()));
        } else if (op instanceof KofConditionalJump kc) {
            boolean isLong = isPrimitiveOf(kc.operandType(), "long");
            boolean isFloat = isPrimitiveOf(kc.operandType(), "float");
            boolean isDouble = isPrimitiveOf(kc.operandType(), "double");
            // Unknown NÃO é referência: int não-inferido (r.exitCode != 0)
            // também infere Unknown — if_acmp sobre int = VerifyError
            boolean isRef = kc.operandType() instanceof Type.ClassType
                    || kc.operandType() instanceof Type.ArrayType
                    || kc.operandType() instanceof Type.TypeVariable
                    || (kc.operandType() instanceof Type.NullableType nt
                        && !(nt.inner() instanceof Type.PrimitiveType));
            if (isLong) {
                mv.visitInsn(LCMP);
            } else if (isFloat) {
                mv.visitInsn(FCMPL);
            } else if (isDouble) {
                mv.visitInsn(DCMPL);
            }
            int opcode;
            if (isRef) {
                // referências (incl. String? vs null): if_acmp*
                opcode = switch (kc.comparison()) {
                    case EQ -> IF_ACMPEQ;
                    case NE -> IF_ACMPNE;
                    default -> IF_ACMPEQ;
                };
            } else if (isLong || isFloat || isDouble) {
                // LCMP/FCMPL/DCMPL leave a single int; use 1-operand jumps.
                opcode = switch (kc.comparison()) {
                    case EQ -> IFEQ;
                    case NE -> IFNE;
                    case LT -> IFLT;
                    case LE -> IFLE;
                    case GT -> IFGT;
                    case GE -> IFGE;
                };
            } else {
                opcode = switch (kc.comparison()) {
                    case EQ -> IF_ICMPEQ;
                    case NE -> IF_ICMPNE;
                    case LT -> IF_ICMPLT;
                    case LE -> IF_ICMPLE;
                    case GT -> IF_ICMPGT;
                    case GE -> IF_ICMPGE;
                };
            }
            mv.visitJumpInsn(opcode, resolveLabel(kc.trueLabel()));
            mv.visitJumpInsn(GOTO, resolveLabel(kc.falseLabel()));
        } else if (op instanceof KofCall kc && ("kof_box".equals(kc.methodName()) || "kof_unbox".equals(kc.methodName()))) {
            if ("kof_box".equals(kc.methodName())) {
                Type boxed = kc.ownerType();
                String boxedName = JvmTypeMapper.toInternalName(
                        boxed instanceof Type.ClassType ct ? ct.packageName() : "java.lang",
                        boxed instanceof Type.ClassType ct ? ct.name() : "Object");
                mv.visitMethodInsn(INVOKESTATIC, boxedName, "valueOf",
                        "(" + JvmTypeMapper.toDescriptor(kc.parameterTypes().get(0)) + ")L" + boxedName + ";", false);
            } else {
                Type boxed = kc.parameterTypes().get(0);
                String boxedName = JvmTypeMapper.toInternalName(
                        boxed instanceof Type.ClassType ct ? ct.packageName() : "java.lang",
                        boxed instanceof Type.ClassType ct ? ct.name() : "Object");
                mv.visitTypeInsn(CHECKCAST, boxedName);
                mv.visitMethodInsn(INVOKEVIRTUAL, boxedName, unboxMethodName(boxed),
                        "()" + JvmTypeMapper.toDescriptor(kc.returnType()), false);
            }
        } else if (op instanceof KofCall kc && JvmRuntime.hasRuntimeFn(kc.methodName())) {
            usesJson = true;
            if (kc.methodName().startsWith("kof_vk_")
                    || kc.methodName().startsWith("kof_mv64_")) {
                usesVk = true;
            }
            mv.visitMethodInsn(INVOKESTATIC, "dev/kof/runtime/KofRuntime", kc.methodName(),
                    JvmRuntimeCallDescriptors.callDescriptor(kc.methodName()), false);
            if ("Ljava/lang/Object;".equals(JvmRuntimeCallDescriptors.callReturnDescriptor(kc.methodName()))) {
                if (kc.returnType() instanceof Type.ClassType ct && !BuiltinTypes.isString(kc.returnType())
                        // handle de spawn é opaco em runtime (CompletableFuture) — sem cast
                        && !"kof.concurrent".equals(ct.packageName())) {
                    mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
                } else if ("kof_poll".equals(kc.methodName()) && isPrimitiveType(kc.returnType())) {
                    // poll pode devolver null (não pronto): unbox com guard
                    String boxed = boxedClassNameFor(kc.returnType());
                    org.objectweb.asm.Label notNull = new org.objectweb.asm.Label();
                    org.objectweb.asm.Label end = new org.objectweb.asm.Label();
                    mv.visitInsn(DUP);
                    mv.visitJumpInsn(IFNONNULL, notNull);
                    mv.visitInsn(POP);
                    emitDefaultValue(mv, kc.returnType());
                    mv.visitJumpInsn(GOTO, end);
                    mv.visitLabel(notNull);
                    mv.visitTypeInsn(CHECKCAST, boxed);
                    mv.visitMethodInsn(INVOKEVIRTUAL, boxed, unboxMethodName(kc.returnType()),
                            "()" + JvmTypeMapper.toDescriptor(kc.returnType()), false);
                    mv.visitLabel(end);
                } else if (("kof_await".equals(kc.methodName())
                        || "kof_await_timeout".equals(kc.methodName())) && isPrimitiveType(kc.returnType())) {
                    // await/awaitTimeout com resultado primitivo: reflexão devolve boxed.
                    emitUnboxIfPrimitive(mv, kc.returnType());
                } else if ("kof_list_reduce".equals(kc.methodName()) && isPrimitiveType(kc.returnType())) {
                    emitUnboxIfPrimitive(mv, kc.returnType());
                }
            }
        } else if (op instanceof KofCall kc && BuiltinTypes.isString(kc.ownerType())
                && ("kof_string_concat".equals(kc.methodName()) || "kof_string_equals".equals(kc.methodName()))) {
            if ("kof_string_concat".equals(kc.methodName())) {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
            } else {
                // null-safe string equality (Objects.equals tolerates null)
                mv.visitMethodInsn(INVOKESTATIC, "java/util/Objects", "equals",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
            }
        } else if (op instanceof KofCall kc && BuiltinTypes.isList(kc.ownerType())) {
            Type elemType = listElementType(kc.ownerType());
            switch (kc.methodName()) {
                case "kof_list_new" -> {
                    mv.visitTypeInsn(NEW, "java/util/ArrayList");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                }
                case "kof_list_add" -> {
                    emitBoxIfPrimitive(mv, elemType);
                    // ArrayList.add empilha boolean; o emit descarta — o IR
                    // não deve adicionar KofPop para add/set/clear
                    // (hasReturnValue = false), senão underflow no frame.
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                    mv.visitInsn(POP);
                }
                case "kof_list_get" -> {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "get", "(I)Ljava/lang/Object;", false);
                    if (!isPrimitiveType(elemType) && !KofUi.isUiType(elemType) && !KofMedia.isHandleType(elemType)) {
                        if (elemType instanceof Type.ArrayType at) {
                            // elemento é array: cast pro tipo JVM real ([I etc)
                            // — callers esperam o componente, não Object
                            mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toDescriptor(at));
                        } else if (elemType instanceof Type.ClassType ct) {
                            mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
                        } else if (elemType instanceof Type.FunctionType ft && ft.className() != null) {
                            // elemento é lambda (bug 20): cast para a classe
                            // sintética, senão o invokevirtual seguinte falha no
                            // verifier (Object onde Lambda0 é esperado)
                            mv.visitTypeInsn(CHECKCAST, ft.className());
                        }
                        // Unknown/other: sem cast — a lista guarda Object
                    }
                    emitUnboxIfPrimitive(mv, elemType);
                }
                case "kof_list_set" -> {
                    emitBoxIfPrimitive(mv, elemType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "set", "(ILjava/lang/Object;)Ljava/lang/Object;", false);
                    mv.visitInsn(POP);
                }
                case "kof_list_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false);
                case "kof_list_contains" -> {
                    if (kc.parameterTypes().size() > 1) {
                        mv.visitInsn(POP);
                    }
                    emitBoxIfPrimitive(mv, elemType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "contains", "(Ljava/lang/Object;)Z", false);
                }
                case "kof_list_is_empty" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "isEmpty", "()Z", false);
                case "kof_list_remove" -> {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "remove", "(I)Ljava/lang/Object;", false);
                    emitUnboxIfPrimitive(mv, elemType);
                }
                case "kof_list_clear" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "clear", "()V", false);
                default -> {}
            }
        } else if (op instanceof KofCall kc && BuiltinTypes.isChannel(kc.ownerType())) {
            // Canais tipados: LinkedBlockingQueue (FIFO thread-safe; put/take
            // bloqueiam — com virtual threads o bloqueio é barato).
            Type elemType = BuiltinTypes.channelElement(kc.ownerType());
            switch (kc.methodName()) {
                case "kof_channel_new" -> {
                    mv.visitTypeInsn(NEW, "java/util/concurrent/LinkedBlockingQueue");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/concurrent/LinkedBlockingQueue",
                            "<init>", "()V", false);
                }
                case "kof_channel_send" -> {
                    emitBoxIfPrimitive(mv, elemType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/LinkedBlockingQueue",
                            "put", "(Ljava/lang/Object;)V", false);
                }
                case "kof_channel_receive" -> {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/LinkedBlockingQueue",
                            "take", "()Ljava/lang/Object;", false);
                    if (!isPrimitiveType(elemType) && !KofUi.isUiType(elemType) && !KofMedia.isHandleType(elemType)
                            && !(elemType instanceof Type.UnknownType)) {
                        if (elemType instanceof Type.ArrayType at) {
                            mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toDescriptor(at));
                        } else if (elemType instanceof Type.ClassType ct) {
                            mv.visitTypeInsn(CHECKCAST, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
                        }
                    }
                    emitUnboxIfPrimitive(mv, elemType);
                }
                default -> {}
            }
        } else if (op instanceof KofCall kc && BuiltinTypes.isMap(kc.ownerType())) {
            Type keyType = Type.UnknownType.UNKNOWN;
            Type valueType = Type.UnknownType.UNKNOWN;
            if (kc.ownerType() instanceof Type.ClassType ct && ct.typeArguments().size() == 2
                    && !(ct.typeArguments().get(0) instanceof Type.UnknownType)) {
                keyType = ct.typeArguments().get(0);
                valueType = ct.typeArguments().get(1);
            }
            // tipos reais dos argumentos no call-site (mapOf() nasce Unknown)
            if (!kc.parameterTypes().isEmpty()) {
                keyType = kc.parameterTypes().get(0);
                if (kc.parameterTypes().size() > 1 && !BuiltinTypes.isList(kc.parameterTypes().get(1))) {
                    valueType = kc.parameterTypes().get(1);
                }
            }
            switch (kc.methodName()) {
                case "kof_map_new" -> {
                    mv.visitTypeInsn(NEW, "java/util/HashMap");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
                }
                case "kof_map_put" -> {
                    // stack: map, key, value — box ambos antes do put(Object,Object)
                    emitBoxIfPrimitive(mv, valueType);          // [m,k,V]
                    if (isPrimitiveType(keyType)) {
                        mv.visitInsn(SWAP);                     // [m,V,k]
                        emitBoxIfPrimitive(mv, keyType);        // [m,V,K]
                        mv.visitInsn(SWAP);                     // [m,K,V]
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                    // VOID no call-site (ex.: pares do mapOf): o valor anterior é descartado
                    if (Type.isVoid(kc.returnType())) {
                        mv.visitInsn(POP);
                    }
                }
                case "kof_map_get" -> {
                    emitBoxIfPrimitive(mv, keyType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                    if (!isPrimitiveType(valueType) && !KofUi.isUiType(valueType) && !KofMedia.isHandleType(valueType) && !(valueType instanceof Type.UnknownType)) {
                        String internal = JvmTypeMapper.toInternalName(valueType instanceof Type.ClassType ct ? ct.packageName() : "", valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
                        mv.visitTypeInsn(CHECKCAST, internal);
                    }
                    emitUnboxIfPrimitive(mv, valueType);
                }
                case "kof_map_remove" -> {
                    emitBoxIfPrimitive(mv, keyType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                    if (!isPrimitiveType(valueType) && !(valueType instanceof Type.UnknownType)) {
                        String internal = JvmTypeMapper.toInternalName(valueType instanceof Type.ClassType ct ? ct.packageName() : "", valueType instanceof Type.ClassType ct ? ct.name() : "java/lang/Object");
                        mv.visitTypeInsn(CHECKCAST, internal);
                    }
                    emitUnboxIfPrimitive(mv, valueType);
                }
                case "kof_map_contains" -> {
                    emitBoxIfPrimitive(mv, keyType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z", false);
                }
                case "kof_map_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "size", "()I", false);
                case "kof_map_is_empty" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "isEmpty", "()Z", false);
                case "kof_map_clear" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "clear", "()V", false);
                case "kof_map_keys" -> {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "keySet", "()Ljava/util/Set;", false);
                    mv.visitTypeInsn(NEW, "java/util/ArrayList");
                    mv.visitInsn(DUP_X1);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
                }
                case "kof_map_values" -> {
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "values", "()Ljava/util/Collection;", false);
                    mv.visitTypeInsn(NEW, "java/util/ArrayList");
                    mv.visitInsn(DUP_X1);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "(Ljava/util/Collection;)V", false);
                }
                default -> {}
            }
        } else if (op instanceof KofCall kc && BuiltinTypes.isSet(kc.ownerType())) {
            Type elemType = Type.UnknownType.UNKNOWN;
            if (kc.ownerType() instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()
                    && !(ct.typeArguments().get(0) instanceof Type.UnknownType)) {
                elemType = ct.typeArguments().get(0);
            }
            // tipo real do argumento no call-site (setOf() nasce Unknown)
            if (!kc.parameterTypes().isEmpty()) {
                elemType = kc.parameterTypes().get(0);
            }
            switch (kc.methodName()) {
                case "kof_set_new" -> {
                    mv.visitTypeInsn(NEW, "java/util/HashSet");
                    mv.visitInsn(DUP);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false);
                }
                case "kof_set_add" -> {
                    emitBoxIfPrimitive(mv, elemType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "add", "(Ljava/lang/Object;)Z", false);
                    if (Type.isVoid(kc.returnType())) {
                        mv.visitInsn(POP);
                    }
                }
                case "kof_set_contains" -> {
                    emitBoxIfPrimitive(mv, elemType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "contains", "(Ljava/lang/Object;)Z", false);
                }
                case "kof_set_remove" -> {
                    emitBoxIfPrimitive(mv, elemType);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "remove", "(Ljava/lang/Object;)Z", false);
                }
                case "kof_set_size" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "size", "()I", false);
                case "kof_set_is_empty" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "isEmpty", "()Z", false);
                case "kof_set_clear" -> mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashSet", "clear", "()V", false);
                default -> {}
            }
        } else if (op instanceof KofCall kc) {
            String owner = "";
            if (kc.ownerType() instanceof Type.ClassType ct) {
                owner = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
            }
            String desc = JvmTypeMapper.toMethodDescriptor(kc.returnType(), kc.parameterTypes());
            switch (kc.kind()) {
                case INSTANCE -> mv.visitMethodInsn(INVOKEVIRTUAL, owner, kc.methodName(), desc, false);
                case STATIC -> mv.visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                case CONSTRUCTOR -> mv.visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                case FUNCTION -> mv.visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                case INTERFACE -> mv.visitMethodInsn(INVOKEINTERFACE, owner, kc.methodName(), desc, true);
                case SUPER -> mv.visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
            }
        } else if (op instanceof KofNewObject no) {
            String typeName = no.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            mv.visitTypeInsn(NEW, typeName);
        } else if (op instanceof KofDup) {
            mv.visitInsn(DUP);
        } else if (op instanceof KofDupX1) {
            mv.visitInsn(DUP_X1);
        } else if (op instanceof KofDupX2) {
            mv.visitInsn(DUP_X2);
        } else if (op instanceof KofPop) {
            mv.visitInsn(POP);
        } else if (op instanceof KofReturn kr) {
            mv.visitInsn(returnOpcode(kr.returnType()));
        } else if (op instanceof KofReturnVoid) {
            mv.visitInsn(RETURN);
        } else if (op instanceof KofThrow) {
            mv.visitInsn(ATHROW);
        } else if (op instanceof KofTryStart kts) {
            mv.visitLabel(resolveLabel(kts.startLabel()));
            tryStack.push(new TryRegion(kts.startLabel(), kts.endLabel()));
        } else if (op instanceof KofTryEnd) {
            tryStack.pop();
        } else if (op instanceof KofCatchStart kcs) {
            mv.visitLabel(resolveLabel(kcs.handlerLabel()));
            TryRegion region = tryStack.peek();
            if (region != null) {
                tryCatches.add(new TryCatchEntry(region.start(), region.end(),
                        kcs.handlerLabel(), kcs.exceptionType()));
            }
            if ("String".equals(kcs.exceptionType())) {
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/RuntimeException", "getMessage",
                        "()Ljava/lang/String;", false);
            }
            mv.visitVarInsn(ASTORE, kcs.localIndex());
        } else if (op instanceof KofCheckCast cc) {
            String type = cc.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            mv.visitTypeInsn(CHECKCAST, type);
        } else if (op instanceof KofInstanceOf io) {
            String type = io.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            mv.visitTypeInsn(INSTANCEOF, type);
        } else if (op instanceof KofNewArray na) {
            if (na.elementType() instanceof Type.ClassType ct) {
                // array de referência: ANEWARRAY (NEWARRAY é só primitivo)
                mv.visitTypeInsn(ANEWARRAY, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
            } else {
                mv.visitIntInsn(NEWARRAY, arrayTypeForType(na.elementType()));
            }
        } else if (op instanceof KofArrayLoad al) {
            mv.visitInsn(arrayLoadOpcode(al.elementType()));
        } else if (op instanceof KofArrayStore as) {
            mv.visitInsn(arrayStoreOpcode(as.elementType()));
        } else if (op instanceof KofArrayLength) {
            mv.visitInsn(ARRAYLENGTH);
        }
    }

    private void emitLoadLiteral(MethodVisitor mv, KofLoadLiteral lit) {
        if (lit.value() instanceof Integer i) {
            emitLoadInt(mv, i);
        } else if (lit.value() instanceof Long l) {
            emitLoadLong(mv, l);
        } else if (lit.value() instanceof Float f) {
            emitLoadFloat(mv, f);
        } else if (lit.value() instanceof Double d) {
            emitLoadDouble(mv, d);
        } else if (lit.value() instanceof String s) {
            mv.visitLdcInsn(s);
        } else if (lit.value() == null) {
            mv.visitInsn(ACONST_NULL);
        }
    }

    private void emitLoadInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) mv.visitInsn(ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) mv.visitIntInsn(BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) mv.visitIntInsn(SIPUSH, value);
        else mv.visitLdcInsn(value);
    }

    private void emitLoadLong(MethodVisitor mv, long value) {
        if (value == 0L) mv.visitInsn(LCONST_0);
        else if (value == 1L) mv.visitInsn(LCONST_1);
        else mv.visitLdcInsn(value);
    }

    private void emitLoadFloat(MethodVisitor mv, float value) {
        if (value == 0f) mv.visitInsn(FCONST_0);
        else if (value == 1f) mv.visitInsn(FCONST_1);
        else if (value == 2f) mv.visitInsn(FCONST_2);
        else mv.visitLdcInsn(value);
    }

    private void emitLoadDouble(MethodVisitor mv, double value) {
        if (value == 0.0) mv.visitInsn(DCONST_0);
        else if (value == 1.0) mv.visitInsn(DCONST_1);
        else mv.visitLdcInsn(value);
    }

    private int opcodeForArithmetic(Type type, int intOpcode) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "long", "Long" -> intOpcode + 1;
                case "float", "Float" -> intOpcode + 2;
                case "double", "Double" -> intOpcode + 3;
                default -> intOpcode;
            };
        }
        return intOpcode;
    }

    private int opcodeForBitwise(Type type, int intOpcode) {
        if (type instanceof Type.PrimitiveType pt && ("long".equals(pt.name()) || "Long".equals(pt.name()))) {
            return intOpcode + 1;
        }
        return intOpcode;
    }

    private int returnOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "void" -> RETURN;
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> IRETURN;
                case "long", "Long" -> LRETURN;
                case "float", "Float" -> FRETURN;
                case "double", "Double" -> DRETURN;
                default -> ARETURN;
            };
        }
        return ARETURN;
    }

    private int arrayTypeForType(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "boolean", "bool", "Bool" -> T_BOOLEAN;
                case "byte", "Byte" -> T_BYTE;
                case "short", "Short" -> T_SHORT;
                case "char", "Char" -> T_CHAR;
                case "int", "Int" -> T_INT;
                case "long", "Long" -> T_LONG;
                case "float", "Float" -> T_FLOAT;
                case "double", "Double" -> T_DOUBLE;
                default -> T_BYTE;
            };
        }
        return T_BYTE;
    }

    private int arrayLoadOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> IALOAD;
                case "long", "Long" -> LALOAD;
                case "float", "Float" -> FALOAD;
                case "double", "Double" -> DALOAD;
                default -> AALOAD;
            };
        }
        return AALOAD;
    }

    private int arrayStoreOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> IASTORE;
                case "long", "Long" -> LASTORE;
                case "float", "Float" -> FASTORE;
                case "double", "Double" -> DASTORE;
                default -> AASTORE;
            };
        }
        return AASTORE;
    }

    private int computeLocals(List<KofOperation> ops) {
        int max = 0;
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal ll) {
                max = Math.max(max, ll.index() + (isDoubleWidth(ll.type()) ? 2 : 1));
            } else if (op instanceof KofStoreLocal sl) {
                max = Math.max(max, sl.index() + (isDoubleWidth(sl.type()) ? 2 : 1));
            } else if (op instanceof KofCatchStart cs) {
                max = Math.max(max, cs.localIndex() + 1);
            }
        }
        return Math.max(max, 1);
    }

    private int computeStack(List<KofOperation> ops) {
        int depth = 0;
        int max = 0;
        for (KofOperation op : ops) {
            if (op instanceof KofLoadLocal ll) {
                depth++;
                if (isDoubleWidth(ll.type())) depth++;
            } else if (op instanceof KofLoadLiteral || op instanceof KofNewObject || op instanceof KofArrayLength || op instanceof KofInstanceOf || op instanceof KofGetStatic) {
                depth++;
            } else if (op instanceof KofDup) {
                depth++;
            } else if (op instanceof KofPop) {
                depth--;
            } else if (op instanceof KofStoreLocal || op instanceof KofStoreField || op instanceof KofPutStatic) {
                depth -= 2;
            } else if (op instanceof KofLoadField || op instanceof KofUnary || op instanceof KofCheckCast) {
            } else if (op instanceof KofBinary) {
                depth--;
            } else if (op instanceof KofReturn kr) {
                if (!Type.isVoid(kr.returnType())) depth--;
            } else if (op instanceof KofReturnVoid) {
            } else if (op instanceof KofNewArray || op instanceof KofArrayLoad) {
                depth--;
            } else if (op instanceof KofArrayStore) {
                depth -= 3;
            } else if (op instanceof KofThrow) {
                depth--;
            } else if (op instanceof KofLabel || op instanceof KofJump) {
            } else if (op instanceof KofConditionalJump) {
                depth -= 2;
            } else if (op instanceof KofCall) {
                depth -= 1;
            }
            max = Math.max(max, depth);
            if (depth < 0) depth = 0;
        }
        return Math.max(max, 1);
    }

    private int loadVarOpcode(Type type) {
        if (type instanceof Type.NullableType nt) return loadVarOpcode(nt.inner());
        if (KofUi.isUiType(type) || KofMedia.isHandleType(type)) return ILOAD;
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> ILOAD;
                case "long", "Long" -> LLOAD;
                case "float", "Float" -> FLOAD;
                case "double", "Double" -> DLOAD;
                default -> ALOAD;
            };
        }
        return ALOAD;
    }

    private int storeVarOpcode(Type type) {
        if (type instanceof Type.NullableType nt) return storeVarOpcode(nt.inner());
        if (KofUi.isUiType(type) || KofMedia.isHandleType(type)) return ISTORE;
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "int", "Int", "boolean", "bool", "Bool", "byte", "Byte", "short", "Short", "char", "Char" -> ISTORE;
                case "long", "Long" -> LSTORE;
                case "float", "Float" -> FSTORE;
                case "double", "Double" -> DSTORE;
                default -> ASTORE;
            };
        }
        return ASTORE;
    }

    private boolean isDoubleWidth(Type type) {
        if (type instanceof Type.NullableType nt) return isDoubleWidth(nt.inner());
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name()) ||
                   "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }

    private Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }
}
