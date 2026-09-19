package dev.kof.compiler.jvm;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.TypeMetrics;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofNewMultiArray;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPop2;
import dev.kof.compiler.KofProcess;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofContinueLabel;
import dev.kof.compiler.KofStatementIf;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.KofUi;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.KofUnaryOp;
import dev.kof.compiler.Type;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/**
 * Emissão de KofOperation → bytecode JVM (REFACTOR-500 FASE 8 —
 * extraído de JvmBackend.emitOperation). Sem estado próprio; o estado
 * de labels/try-catch e de module vem no contexto.
 */
public final class JvmOpEmitter {

    private JvmOpEmitter() {}

    record OpContext(MethodVisitor mv, String className,
                     IRModule module, IRClass currentClass) {
    }

    static void emit(JvmBackend ctx, OpContext c, KofOperation op) {
        switch (op) {
            case KofLoadLiteral lit -> {
                JvmLiteralEmitter.emitLoadLiteral(c.mv(), lit);
            }
            case KofLoadLocal ll -> {
                c.mv().visitVarInsn(JvmLiteralEmitter.loadVarOpcode(ll.type()), ll.index());
            }
            case KofStoreLocal sl -> {
                c.mv().visitVarInsn(JvmLiteralEmitter.storeVarOpcode(sl.type()), sl.index());
            }
            case KofLoadField lf -> {
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
            }
            case KofStoreField sf -> {
                String owner = JvmTypeMapper.toInternalName(
                        sf.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                        sf.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
                c.mv().visitFieldInsn(PUTFIELD, owner, sf.name(), JvmTypeMapper.toDescriptor(sf.fieldType()));
            }
            case KofGetStatic gs -> {
                String owner = JvmTypeMapper.toInternalName(
                        gs.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                        gs.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
                c.mv().visitFieldInsn(GETSTATIC, owner, gs.name(), JvmTypeMapper.toDescriptor(gs.fieldType()));
            }
            case KofPutStatic ps -> {
                String owner = JvmTypeMapper.toInternalName(
                        ps.ownerType() instanceof Type.ClassType ct ? ct.packageName() : "",
                        ps.ownerType() instanceof Type.ClassType ct ? ct.name() : "?");
                c.mv().visitFieldInsn(PUTSTATIC, owner, ps.name(), JvmTypeMapper.toDescriptor(ps.fieldType()));
            }
            case KofBinary kb -> {
                emitBinary(c.mv(), kb);
            }
            case KofUnary ku -> {
                emitUnary(c.mv(), ku);
            }
            case KofLabel kl -> {
                c.mv().visitLabel(ctx.resolveLabel(kl.label()));
            }
            case KofJump kj -> {
                c.mv().visitJumpInsn(GOTO, ctx.resolveLabel(kj.target()));
            }
            case KofConditionalJump kc -> {
                emitConditionalJump(ctx, c.mv(), kc);
            }
            case KofCall kc -> {
                if (("kof_box".equals(kc.methodName()) || "kof_unbox".equals(kc.methodName()))) {
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
                }
                else if (JvmRuntime.hasRuntimeFn(kc.methodName())) {
                    JvmOpCollections.emitKofRuntimeCall(ctx, c.mv(), kc);
                }
                else if (BuiltinTypes.isString(kc.ownerType())
                && ("kof_string_concat".equals(kc.methodName()) || "kof_string_equals".equals(kc.methodName()))) {
                    JvmOpCollections.emitStringCall(c.mv(), kc);
                }
                else if (BuiltinTypes.isList(kc.ownerType())) {
                    JvmOpCollections.emitListCall(c.mv(), kc);
                }
                else if (BuiltinTypes.isChannel(kc.ownerType())) {
                    JvmOpCollections.emitChannelCall(c.mv(), kc);
                }
                else if (BuiltinTypes.isMap(kc.ownerType())) {
                    JvmOpCollections.emitMapCall(c.mv(), kc);
                }
                else if (BuiltinTypes.isSet(kc.ownerType())) {
                    JvmOpCollections.emitSetCall(c.mv(), kc);
                }
                else {
                    String owner = "";
                    if (kc.ownerType() instanceof Type.ClassType ct) {
                        owner = JvmTypeMapper.toInternalName(ct.packageName(), ct.name());
                    }
                    String desc = JvmTypeMapper.toMethodDescriptor(kc.returnType(), kc.parameterTypes());
                    boolean isInterfaceOwner = false;
                    if (c.module() != null) {
                        for (IRClass irc : c.module().classes()) {
                            if (irc.name().equals(owner)) {
                                isInterfaceOwner = (irc.accessFlags() & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
                                break;
                            }
                        }
                    }
                    switch (kc.kind()) {
                        case INSTANCE -> c.mv().visitMethodInsn(INVOKEVIRTUAL, owner, kc.methodName(), desc, false);
                        case STATIC -> c.mv().visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, isInterfaceOwner);
                        case CONSTRUCTOR -> c.mv().visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                        case FUNCTION -> c.mv().visitMethodInsn(INVOKESTATIC, owner, kc.methodName(), desc, false);
                        case INTERFACE -> c.mv().visitMethodInsn(INVOKEINTERFACE, owner, kc.methodName(), desc, true);
                        case SUPER -> c.mv().visitMethodInsn(INVOKESPECIAL, owner, kc.methodName(), desc, false);
                    }
                }
            }
            case KofNewObject no -> {
                String type = no.type() instanceof Type.ClassType ct
                        ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
                c.mv().visitTypeInsn(NEW, type);
            }
            case KofDup _ -> {
                c.mv().visitInsn(DUP);
            }
            case KofDup2 _ -> {
                c.mv().visitInsn(DUP2);
            }
            case KofDupX1 _ -> {
                c.mv().visitInsn(DUP_X1);
            }
            case KofDupX2 _ -> {
                c.mv().visitInsn(DUP_X2);
            }
            case KofPop _ -> {
                c.mv().visitInsn(POP);
            }
            case KofPop2 _ -> {
                c.mv().visitInsn(POP2);
            }
            case KofReturn kr -> {
                c.mv().visitInsn(JvmLiteralEmitter.returnOpcode(kr.returnType()));
            }
            case KofReturnVoid _ -> {
                c.mv().visitInsn(RETURN);
            }
            case KofThrow _ -> {
                c.mv().visitInsn(ATHROW);
            }
            case KofTryStart kts -> {
                c.mv().visitLabel(ctx.resolveLabel(kts.startLabel()));
                ctx.pushTryRegion(kts.startLabel(), kts.endLabel());
            }
            case KofStatementIf _ -> {
                // §267: marcador de if de statement (uso exclusivo do dispatcher JS) — no-op
            }
            case KofContinueLabel _ -> {
                // §266: marcador estrutural (fronteira corpo/update do for) — no-op
            }
            case KofTryEnd _ -> {
                ctx.popTryRegion();
            }
            case KofCatchStart kcs -> {
                c.mv().visitLabel(ctx.resolveLabel(kcs.handlerLabel()));
                ctx.registerTryCatch(kcs);
                if ("String".equals(kcs.exceptionType())) {
                    c.mv().visitMethodInsn(INVOKEVIRTUAL, "java/lang/RuntimeException", "getMessage",
                            "()Ljava/lang/String;", false);
                }
                c.mv().visitVarInsn(ASTORE, kcs.localIndex());
            }
            case KofCheckCast cc -> {
                Type castT = cc.type() instanceof Type.PrimitiveType pt ? TypeMetrics.boxedTypeFor(pt) : cc.type();
                String type = castT instanceof Type.ClassType ct
                        ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
                c.mv().visitTypeInsn(CHECKCAST, type);
            }
            case KofInstanceOf io -> {
                Type checkT = io.type() instanceof Type.PrimitiveType pt ? TypeMetrics.boxedTypeFor(pt) : io.type();
                String type = checkT instanceof Type.ClassType ct
                        ? JvmTypeMapper.toInternalName(ct.packageName(), ct.name()) : "?";
                c.mv().visitTypeInsn(INSTANCEOF, type);
            }
            case KofNewArray na -> {
                if (na.elementType() instanceof Type.ClassType ct) {
                    // array de referência: ANEWARRAY (NEWARRAY é só primitivo)
                    c.mv().visitTypeInsn(ANEWARRAY, JvmTypeMapper.toInternalName(ct.packageName(), ct.name()));
                } else {
                    c.mv().visitIntInsn(NEWARRAY, JvmLiteralEmitter.arrayTypeForType(na.elementType()));
                }
            }
            case KofNewMultiArray ma -> {
                c.mv().visitMultiANewArrayInsn(JvmTypeMapper.toDescriptor(arrayTypeOf(ma.baseType(), ma.dims())), ma.dims());
            }
            case KofArrayLoad al -> {
                c.mv().visitInsn(JvmLiteralEmitter.arrayLoadOpcode(al.elementType()));
            }
            case KofArrayStore as -> {
                c.mv().visitInsn(JvmLiteralEmitter.arrayStoreOpcode(as.elementType()));
            }
            case KofArrayLength _ -> {
                c.mv().visitInsn(ARRAYLENGTH);
            }
            case null, default -> { }  // no-op p/ null ou tipo nao-casado
        }
    }

    /** Type.ArrayType aninhado n vezes: base Int, n=2 → [[I (descriptor via toDescriptor). */
    static Type arrayTypeOf(Type base, int n) {
        Type t = base;
        for (int i = 0; i < n; i++) t = new Type.ArrayType(t);
        return t;
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
                // UIW050: handle de UI/mídia APAGA para int no runtime — `==`
                // entre dois handles é comparação int (if_icmp*), nunca
                // if_acmp* (que o verifier rejeita sobre int).
                boolean isRef = isRefOperand(kb.operandType());
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
                    // §101: cmpg p/ < e <= (NaN → false, como o javac/IEEE).
                    mv.visitInsn(JvmLiteralEmitter.floatCmpIsG(kb.op()) ? FCMPG : FCMPL);
                    cmpOpcode = JvmLiteralEmitter.intCompareOpcode(kb.op());
                } else if (isDouble) {
                    mv.visitInsn(JvmLiteralEmitter.floatCmpIsG(kb.op()) ? DCMPG : DCMPL);
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
        } else if (ku.op() == KofUnaryOp.BITNOT) {
            if (ku.operandType() == Type.PrimitiveType.LONG) {
                mv.visitLdcInsn(-1L);
                mv.visitInsn(LXOR);
            } else {
                mv.visitInsn(ICONST_M1);
                mv.visitInsn(IXOR);
            }
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
        boolean isRef = isRefOperand(kc.operandType());
        if (isLong) {
            mv.visitInsn(LCMP);
        } else if (isFloat) {
            // §101: cmpg p/ < e <= (NaN → false, como o javac/IEEE).
            mv.visitInsn(JvmLiteralEmitter.condCmpIsG(kc.comparison()) ? FCMPG : FCMPL);
        } else if (isDouble) {
            mv.visitInsn(JvmLiteralEmitter.condCmpIsG(kc.comparison()) ? DCMPG : DCMPL);
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

    /**
     * UIW050: um operando é REFERÊNCIA no bytecode? Handle de UI/mídia
     * (ClassType apagado para int por JvmTypeMapper.toDescriptor) NÃO é —
     * tratá-lo como ref emitia if_acmp* sobre int e o verifier rejeitava.
     */
    private static boolean isRefOperand(Type t) {
        // D-NULL-INTENT: Nullable(primitivo) é referência de verdade agora —
        // usado só para comparação contra o literal `null` (if_acmp*); duas
        // expressões Nullable(primitivo) genuínas NUNCA chegam aqui (o
        // dispatcher em ExpressionBinaryLowerer intercepta antes e usa
        // RecordEqualityLowerer/`.equals()`, senão IF_ACMPEQ pegaria
        // identidade de wrapper, não valor — cache do Integer, I6).
        if (t instanceof Type.NullableType nt && nt.inner() instanceof Type.PrimitiveType pt
                && !Type.isVoid(pt)) {
            return true;
        }
        if (t instanceof Type.NullableType nt) return isRefOperand(nt.inner());
        if (JvmTypeMapper.isHandleErasedToInt(t)) return false;
        return t instanceof Type.ClassType || t instanceof Type.ArrayType || t instanceof Type.TypeVariable;
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
