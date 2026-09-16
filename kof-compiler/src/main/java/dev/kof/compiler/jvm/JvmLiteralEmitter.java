package dev.kof.compiler.jvm;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofComparison;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofComparison;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofMedia;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofNewMultiArray;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPop2;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofUi;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.Type;

import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Literais, tabelas de opcodes JVM e larguras de frame
 * (REFACTOR-500 FASE 8 — extraído de JvmBackend). Sem estado.
 */
public final class JvmLiteralEmitter {

    private JvmLiteralEmitter() {}

    static void emitLoadLiteral(MethodVisitor mv, KofLoadLiteral lit) {
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

    private static void emitLoadInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) mv.visitInsn(ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) mv.visitIntInsn(BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) mv.visitIntInsn(SIPUSH, value);
        else mv.visitLdcInsn(value);
    }

    private static void emitLoadLong(MethodVisitor mv, long value) {
        if (value == 0L) mv.visitInsn(LCONST_0);
        else if (value == 1L) mv.visitInsn(LCONST_1);
        else mv.visitLdcInsn(value);
    }

    private static void emitLoadFloat(MethodVisitor mv, float value) {
        // §110: `value == 0f` também casa -0.0f (IEEE: -0.0 == 0.0) — sem o
        // guard de raw bits, FCONST_0 colapsava -0.0f em +0.0. O contrato é
        // o literal (JVM run-time: -z de z=0.0 dá -0.0 corretamente).
        if (value == 0f && Float.floatToRawIntBits(value) == 0) mv.visitInsn(FCONST_0);
        else if (value == 1f) mv.visitInsn(FCONST_1);
        else if (value == 2f) mv.visitInsn(FCONST_2);
        else mv.visitLdcInsn(value);
    }

    private static void emitLoadDouble(MethodVisitor mv, double value) {
        // §110 (ver emitLoadFloat): -0.0 literal/foldado virava +0.0.
        if (value == 0.0 && Double.doubleToRawLongBits(value) == 0L) mv.visitInsn(DCONST_0);
        else if (value == 1.0) mv.visitInsn(DCONST_1);
        else mv.visitLdcInsn(value);
    }

    static int intCompareOpcode(KofBinaryOp op) {
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

    /**
     * §101 (D-BACKEND-SEMANTICS #1, opção A — IEEE 754 puro): variante do
     * {@code fcmp}/{@code dcmp} que faz a comparação com NaN dar {@code false}
     * em TODOS os relacionais. A JVM tem duas: {@code cmpg} devolve +1 quando
     * há NaN, {@code cmpl} devolve -1. O javac usa {@code cmpg} para {@code <}
     * e {@code <=} (NaN→+1→{@code iflt}/{@code ifle} = false) e {@code cmpl}
     * para {@code >}/{@code >=} (NaN→-1→{@code ifgt}/{@code ifge} = false);
     * {@code ==} dá false e {@code !=} dá true em ambos. É o contrato do Java
     * (JLS 15.20.1) e a referência riscv/IEEE.
     */
    static boolean floatCmpIsG(KofBinaryOp op) {
        return op == KofBinaryOp.LT || op == KofBinaryOp.LE;
    }

    /** §101: idem para o salto condicional (enum próprio). */
    static boolean condCmpIsG(KofComparison op) {
        return op == KofComparison.LT || op == KofComparison.LE;
    }

    static int returnOpcode(Type type) {
        // D-NULL-INTENT/N1 (mantenedora 15/09): Nullable(primitivo) agora é
        // BOXED no descritor (JvmTypeMapper.toDescriptor) para poder carregar
        // null de verdade — o retorno é sempre referência (ARETURN). Manter a
        // simetria descritor/opcode é o que evita o VerifyError do §241/#259.
        // Nullable(não-primitivo, ex. handle UI/media) continua apagando p/ o inner.
        if (type instanceof Type.NullableType nt) {
            if (nt.inner() instanceof Type.PrimitiveType) return ARETURN;
            type = nt.inner();
        }
        // §176: handles kof.ui/kof.media são Int em runtime — o descriptor
        // apaga para "I" (JvmTypeMapper). Sem isto, `return label` emitia
        // ARETURN com um int na pilha → VerifyError (Bad type on operand stack).
        if (type instanceof Type.ClassType ct && (KofUi.isUiType(ct) || KofMedia.isHandleType(ct))) {
            return IRETURN;
        }
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

    static int arrayTypeForType(Type type) {
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

    // #132: a especificação da JVM amarra o opcode de acesso ao TIPO real do
    // array (JVM Spec §6.5): boolean[]/byte[] usam BALOAD/BASTORE, short[]
    // SALOAD/SASTORE, char[] CALOAD/CASTORE. Emitir IALOAD/IASTORE nesses
    // arrays é bytecode inválido — o verificador aceita em alguns JDKs e o
    // processo morre no boot com sintoma não-relacionado (a mensagem JavaFX
    // engolida, regra do JavaFX no AGENTS.md). A largura na pilha é a mesma
    // (int), só a extensão/sinal na fronteira memory↔pilha muda.
    static int arrayLoadOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "boolean", "bool", "Bool", "byte", "Byte" -> BALOAD;
                case "short", "Short" -> SALOAD;
                case "char", "Char" -> CALOAD;
                case "int", "Int" -> IALOAD;
                case "long", "Long" -> LALOAD;
                case "float", "Float" -> FALOAD;
                case "double", "Double" -> DALOAD;
                default -> AALOAD;
            };
        }
        return AALOAD;
    }

    static int arrayStoreOpcode(Type type) {
        if (type instanceof Type.PrimitiveType pt) {
            return switch (pt.name()) {
                case "boolean", "bool", "Bool", "byte", "Byte" -> BASTORE;
                case "short", "Short" -> SASTORE;
                case "char", "Char" -> CASTORE;
                case "int", "Int" -> IASTORE;
                case "long", "Long" -> LASTORE;
                case "float", "Float" -> FASTORE;
                case "double", "Double" -> DASTORE;
                default -> AASTORE;
            };
        }
        return AASTORE;
    }

    static int computeLocals(List<KofOperation> ops) {
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

    static int computeStack(List<KofOperation> ops) {
        int depth = 0;
        int max = 0;
        for (KofOperation op : ops) {
            switch (op) {
                case KofLoadLocal ll -> {
                    depth++;
                    if (isDoubleWidth(ll.type())) depth++;
                }
                case KofLoadLiteral _, KofNewObject _, KofArrayLength _, KofInstanceOf _, KofGetStatic _ -> {
                    depth++;
                    Type loaded = op instanceof KofLoadLiteral lit ? lit.type()
                            : op instanceof KofGetStatic gs ? gs.fieldType()
                            : Type.UnknownType.UNKNOWN;
                    if (isDoubleWidth(loaded)) depth++;
                }
                case KofDup _ -> depth++;
                case KofDup2 _ -> depth += 2;
                case KofPop _ -> depth--;
                case KofPop2 _ -> depth--; // categoria-2: mesmo efeito no modelo de 1 slot do emitter
                case KofStoreLocal _, KofStoreField _, KofPutStatic _ -> depth -= 2;
                case KofLoadField _, KofUnary _, KofCheckCast _ -> { }
                case KofBinary _ -> depth--;
                case KofReturn kr -> {
                    if (!Type.isVoid(kr.returnType())) depth--;
                }
                case KofReturnVoid _ -> { }
                case KofNewArray _ -> depth--;
                case KofNewMultiArray ma -> depth -= ma.dims() - 1;
                case KofArrayLoad _ -> depth--;
                case KofArrayStore _ -> depth -= 3;
                case KofThrow _ -> depth--;
                case KofLabel _, KofJump _ -> { }
                case KofConditionalJump _ -> depth -= 2;
                case KofCall _ -> depth -= 1;
                case null, default -> { }
            }
            max = Math.max(max, depth);
            if (depth < 0) depth = 0;
        }
        return Math.max(max, 1);
    }

    static int loadVarOpcode(Type type) {
        // D-NULL-INTENT/N1: Nullable(primitivo) é slot de REFERÊNCIA (boxed) —
        // mesma simetria descritor/opcode do returnOpcode acima.
        if (type instanceof Type.NullableType nt) {
            if (nt.inner() instanceof Type.PrimitiveType) return ALOAD;
            return loadVarOpcode(nt.inner());
        }
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

    static int storeVarOpcode(Type type) {
        if (type instanceof Type.NullableType nt) {
            if (nt.inner() instanceof Type.PrimitiveType) return ASTORE;
            return storeVarOpcode(nt.inner());
        }
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

    static boolean isDoubleWidth(Type type) {
        // D-NULL-INTENT/N1: Nullable(Long)/Nullable(Double) agora são
        // referência boxed (1 slot) — NÃO desempacotar mais para o primitivo
        // categoria-2. Nullable(qualquer coisa) cai no `return false` abaixo
        // (nunca é PrimitiveType), que é a resposta certa nos dois casos.
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name()) ||
                   "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }
}
