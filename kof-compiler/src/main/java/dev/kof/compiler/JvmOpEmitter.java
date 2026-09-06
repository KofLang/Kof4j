package dev.kof.compiler;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * Emissão de KofOperation → bytecode JVM (REFACTOR-500 FASE 8 —
 * extraído de JvmBackend.emitOperation). Sem estado próprio; o estado
 * de labels/try-catch e de module vem no contexto.
 */
final class JvmOpEmitter {

    private JvmOpEmitter() {}

    record OpContext(MethodVisitor mv, String className,
                     IRModule module, IRClass currentClass) {
    }

    static void emit(JvmBackend ctx, OpContext c, KofOperation op) {
        if (op instanceof KofLoadLiteral lit) {
            JvmLiteralEmitter.emitLoadLiteral(c.mv(), lit);
        } else if (op instanceof KofLoadLocal ll) {
            c.mv().visitVarInsn(JvmLiteralEmitter.loadVarOpcode(ll.type()), ll.index());
        } else if (op instanceof KofStoreLocal sl) {
            c.mv().visitVarInsn(JvmLiteralEmitter.storeVarOpcode(sl.type()), sl.index());
        } else if (op instanceof KofLoadField lf) {
            if (BuiltinTypes.isString(lf.ownerType()) && "length".equals(lf.name())) {
                c.mv().visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
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
                    if (c.module() != null) {
                        for (IRClass k : c.module().classes()) {
                            if (k.name().equals(internal) && "java/lang/Record".equals(k.superName())) {
                                isRecord = true;
                                // If we are inside the record's own accessor, use GETFIELD directly to avoid recursion
                                if (c.currentClass() != null && c.currentClass().name().equals(internal)) {
                                    isSelfRecordAccess = true;
                                }
                                break;
                            }
                        }
                    }
                }
                if (isRecord && !isSelfRecordAccess) {
                    c.mv().visitMethodInsn(INVOKEVIRTUAL, owner, lf.name(), "()" + JvmTypeMapper.toDescriptor(lf.fieldType()), false);
                } else {
                    c.mv().visitFieldInsn(GETFIELD, owner, lf.name(), JvmTypeMapper.toDescriptor(lf.fieldType()));
                }
            }
        } else if (op instanceof KofStoreField sf) {
            String owner = JvmTypeMapper.toInternalName(
                    sf.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    sf.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            c.mv().visitFieldInsn(PUTFIELD, owner, sf.name(), JvmTypeMapper.toDescriptor(sf.fieldType()));
        } else if (op instanceof KofGetStatic gs) {
            String owner = JvmTypeMapper.toInternalName(
                    gs.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    gs.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            c.mv().visitFieldInsn(GETSTATIC, owner, gs.name(), JvmTypeMapper.toDescriptor(gs.fieldType()));
        } else if (op instanceof KofPutStatic ps) {
            String owner = JvmTypeMapper.toInternalName(
                    ps.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                    ps.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
            c.mv().visitFieldInsn(PUTSTATIC, owner, ps.name(), JvmTypeMapper.toDescriptor(ps.fieldType()));
        } else if (op instanceof KofBinary kb) {
            emitBinary(c.mv(), kb);
        } else if (op instanceof KofUnary ku) {
            emitUnary(c.mv(), ku);
        } else if (op instanceof KofLabel kl) {
            c.mv().visitLabel(ctx.resolveLabel(kl.label()));
        } else if (op instanceof KofJump kj) {
            c.mv().visitJumpInsn(GOTO, ctx.resolveLabel(kj.target()));
        } else if (op instanceof KofConditionalJump kc) {
            emitConditionalJump(ctx, c.mv(), kc);
        } else if (op instanceof KofCall kc && ("kof_box".equals(kc.methodName()) || "kof_unbox".equals(kc.methodName()))) {
            if ("kof_box".equals(kc.methodName())) {
                Type boxed = kc.ownerType();
                String boxedName = JvmTypeMapper.toInternalName(
                        boxed instanceof Type.ClassType ct ? ct.packageName() : "java.lang",
                        boxed instanceof Type.ClassType ct ? ct.name() : "Object");
                c.mv().visitMethodInsn(INVOKESTATIC, boxedName, "valueOf",
                        "(" + JvmTypeMapper.toDescriptor(kc.parameterTypes().get(0)) + ")L" + boxedName + ";", false);
            } else {
                Type boxed = kc.parameterTypes().get(0);
                String boxedName = JvmTypeMapper.toInternalName(
                        boxed instanceof Type.ClassType ct ? ct.packageName() : "java.lang",
                        boxed instanceof Type.ClassType ct ? ct.name() : "Object");
                c.mv().visitTypeInsn(CHECKCAST, boxedName);
                c.mv().visitMethodInsn(INVOKEVIRTUAL, boxedName, JvmOpCollections.unboxMethodName(boxed),
                        "()" + JvmTypeMapper.toDescriptor(kc.returnType()), false);
            }
        } else if (op instanceof KofCall kc && JvmRuntime.hasRuntimeFn(kc.methodName())) {
            JvmOpCollections.emitKofRuntimeCall(ctx, c.mv(), kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isString(kc.ownerType())
                && ("kof_string_concat".equals(kc.methodName()) || "kof_string_equals".equals(kc.methodName()))) {
            JvmOpCollections.emitStringCall(c.mv(), kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isList(kc.ownerType())) {
            JvmOpCollections.emitListCall(c.mv(), kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isChannel(kc.ownerType())) {
            JvmOpCollections.emitChannelCall(c.mv(), kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isMap(kc.ownerType())) {
            JvmOpCollections.emitMapCall(c.mv(), kc);
        } else if (op instanceof KofCall kc && BuiltinTypes.isSet(kc.ownerType())) {
            JvmOpCollections.emitSetCall(c.mv(), kc);
        } else if (op instanceof KofCall kc) {
            String owner = "";
            if (kc.ownerType() instanceof Type.ClassType ct) {
                owner = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
            }
            String desc = JvmTypeMapper.toMethodDescriptor(kc.returnType(), kc.parameterTypes());
            switch (kc.kind()) {
                case INSTANCE -> c.mv().visitMethodInsn(INVOKEVIRTUAL, owner, kc.methodName(), desc, false);
                case STATIC -> c.mv().visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                case CONSTRUCTOR -> c.mv().visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                case FUNCTION -> c.mv().visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                case INTERFACE -> c.mv().visitMethodInsn(INVOKEINTERFACE, owner, kc.methodName(), desc, true);
                case SUPER -> c.mv().visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
            }
        } else if (op instanceof KofNewObject no) {
            String type = no.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            c.mv().visitTypeInsn(NEW, type);
        } else if (op instanceof KofDup) {
            c.mv().visitInsn(DUP);
        } else if (op instanceof KofDupX1) {
            c.mv().visitInsn(DUP_X1);
        } else if (op instanceof KofDupX2) {
            c.mv().visitInsn(DUP_X2);
        } else if (op instanceof KofPop) {
            c.mv().visitInsn(POP);
        } else if (op instanceof KofReturn kr) {
            c.mv().visitInsn(JvmLiteralEmitter.returnOpcode(kr.returnType()));
        } else if (op instanceof KofReturnVoid) {
            c.mv().visitInsn(RETURN);
        } else if (op instanceof KofThrow) {
            c.mv().visitInsn(ATHROW);
        } else if (op instanceof KofTryStart kts) {
            c.mv().visitLabel(ctx.resolveLabel(kts.startLabel()));
            ctx.pushTryRegion(kts.startLabel(), kts.endLabel());
        } else if (op instanceof KofTryEnd) {
            ctx.popTryRegion();
        } else if (op instanceof KofCatchStart kcs) {
            c.mv().visitLabel(ctx.resolveLabel(kcs.handlerLabel()));
            ctx.registerTryCatch(kcs);
            if ("String".equals(kcs.exceptionType())) {
                c.mv().visitMethodInsn(INVOKEVIRTUAL, "java/lang/RuntimeException", "getMessage",
                        "()Ljava/lang/String;", false);
            }
            c.mv().visitVarInsn(ASTORE, kcs.localIndex());
        } else if (op instanceof KofCheckCast cc) {
            String type = cc.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            c.mv().visitTypeInsn(CHECKCAST, type);
        } else if (op instanceof KofInstanceOf io) {
            String type = io.type() instanceof Type.ClassType ct
                    ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
            c.mv().visitTypeInsn(INSTANCEOF, type);
        } else if (op instanceof KofNewArray na) {
            if (na.elementType() instanceof Type.ClassType ct) {
                // array de referência: ANEWARRAY (NEWARRAY é só primitivo)
                c.mv().visitTypeInsn(ANEWARRAY, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
            } else {
                c.mv().visitIntInsn(NEWARRAY, JvmLiteralEmitter.arrayTypeForType(na.elementType()));
            }
        } else if (op instanceof KofArrayLoad al) {
            c.mv().visitInsn(JvmLiteralEmitter.arrayLoadOpcode(al.elementType()));
        } else if (op instanceof KofArrayStore as) {
            c.mv().visitInsn(JvmLiteralEmitter.arrayStoreOpcode(as.elementType()));
        } else if (op instanceof KofArrayLength) {
            c.mv().visitInsn(ARRAYLENGTH);
        }
    }

    private static void emitBinary(MethodVisitor mv, KofBinary kb) {
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
    }

    private static void emitUnary(MethodVisitor mv, KofUnary ku) {
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
    }

    private static void emitConditionalJump(JvmBackend ctx, MethodVisitor mv, KofConditionalJump kc) {
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
        mv.visitJumpInsn(opcode, ctx.resolveLabel(kc.trueLabel()));
        mv.visitJumpInsn(GOTO, ctx.resolveLabel(kc.falseLabel()));
    }

    private static int opcodeForArithmetic(Type type, int intOpcode) {
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

    private static int opcodeForBitwise(Type type, int intOpcode) {
        if (type instanceof Type.PrimitiveType pt && ("long".equals(pt.name()) || "Long".equals(pt.name()))) {
            return intOpcode + 1;
        }
        return intOpcode;
    }
}
