package dev.kof.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static org.objectweb.asm.Opcodes.*;

/**
 * Emissão dos métodos sintéticos de record JVM (toString/equals/hashCode) —
 * REFACTOR-500 FASE 8, extraído de JvmBackend.emitClass. Sem estado.
 */
final class JvmRecordEmitter {

    private JvmRecordEmitter() {}

    static void emitRecordMethods(ClassWriter cw, IRClass clazz) {
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

    private static void emitEqualsComparison(MethodVisitor mv, Type type, String cn) {
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

    private static void emitHashContribution(MethodVisitor mv, Type type) {
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

    private static String appendDescriptor(Type type) {
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
}
