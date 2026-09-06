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
        if (op instanceof KofLoadLiteral lit) {
            JvmLiteralEmitter.emitLoadLiteral(mv, lit);
        } else if (op instanceof KofLoadLocal ll) {
            mv.visitVarInsn(JvmLiteralEmitter.loadVarOpcode(ll.type()), ll.index());
        } else if (op instanceof KofStoreLocal sl) {
            mv.visitVarInsn(JvmLiteralEmitter.storeVarOpcode(sl.type()), sl.index());
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
                    boolean isLong = JvmOpCollections.isPrimitiveOf(kb.operandType(), "long");
                    boolean isFloat = JvmOpCollections.isPrimitiveOf(kb.operandType(), "float");
                    boolean isDouble = JvmOpCollections.isPrimitiveOf(kb.operandType(), "double");
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
                        cmpOpcode = JvmLiteralEmitter.intCompareOpcode(kb.op());
                    } else if (isFloat) {
                        mv.visitInsn(FCMPL);
                        cmpOpcode = JvmLiteralEmitter.intCompareOpcode(kb.op());
                    } else if (isDouble) {
                        mv.visitInsn(DCMPL);
                        cmpOpcode = JvmLiteralEmitter.intCompareOpcode(kb.op());
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
            boolean isLong = JvmOpCollections.isPrimitiveOf(kc.operandType(), "long");
            boolean isFloat = JvmOpCollections.isPrimitiveOf(kc.operandType(), "float");
            boolean isDouble = JvmOpCollections.isPrimitiveOf(kc.operandType(), "double");
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
                mv.visitMethodInsn(INVOKEVIRTUAL, boxedName, JvmOpCollections.unboxMethodName(boxed),
                        "()" + JvmTypeMapper.toDescriptor(kc.returnType()), false);
            }
        } else if (op instanceof KofCall kc && JvmRuntime.hasRuntimeFn(kc.methodName())) {
            JvmOpCollections.emitKofRuntimeCall(this, mv, kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isString(kc.ownerType())
                && ("kof_string_concat".equals(kc.methodName()) || "kof_string_equals".equals(kc.methodName()))) {
            JvmOpCollections.emitStringCall(mv, kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isList(kc.ownerType())) {
            JvmOpCollections.emitListCall(mv, kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isChannel(kc.ownerType())) {
            JvmOpCollections.emitChannelCall(mv, kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isMap(kc.ownerType())) {
            JvmOpCollections.emitMapCall(mv, kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isSet(kc.ownerType())) {
            JvmOpCollections.emitSetCall(mv, kc);
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
            mv.visitInsn(JvmLiteralEmitter.returnOpcode(kr.returnType()));
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
                mv.visitIntInsn(NEWARRAY, JvmLiteralEmitter.arrayTypeForType(na.elementType()));
            }
        } else if (op instanceof KofArrayLoad al) {
            mv.visitInsn(JvmLiteralEmitter.arrayLoadOpcode(al.elementType()));
        } else if (op instanceof KofArrayStore as) {
            mv.visitInsn(JvmLiteralEmitter.arrayStoreOpcode(as.elementType()));
        } else if (op instanceof KofArrayLength) {
            mv.visitInsn(ARRAYLENGTH);
        }
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

    private Type listElementType(Type listType) {
        if (listType instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return ct.typeArguments().get(0);
        }
        return Type.UnknownType.UNKNOWN;
    }
}
