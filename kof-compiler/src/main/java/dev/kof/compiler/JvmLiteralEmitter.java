package dev.kof.compiler;

import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Literais, tabelas de opcodes JVM e larguras de frame
 * (REFACTOR-500 FASE 8 — extraído de JvmBackend). Sem estado.
 */
final class JvmLiteralEmitter {

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
        if (value == 0f) mv.visitInsn(FCONST_0);
        else if (value == 1f) mv.visitInsn(FCONST_1);
        else if (value == 2f) mv.visitInsn(FCONST_2);
        else mv.visitLdcInsn(value);
    }

    private static void emitLoadDouble(MethodVisitor mv, double value) {
        if (value == 0.0) mv.visitInsn(DCONST_0);
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

    static int returnOpcode(Type type) {
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

    static int arrayLoadOpcode(Type type) {
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

    static int arrayStoreOpcode(Type type) {
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

    static int loadVarOpcode(Type type) {
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

    static int storeVarOpcode(Type type) {
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

    static boolean isDoubleWidth(Type type) {
        if (type instanceof Type.NullableType nt) return isDoubleWidth(nt.inner());
        if (type instanceof Type.PrimitiveType pt) {
            return "long".equals(pt.name()) || "Long".equals(pt.name()) ||
                   "double".equals(pt.name()) || "Double".equals(pt.name());
        }
        return false;
    }
}
