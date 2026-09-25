package dev.kof.compiler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

/**
 * §393 (#568) — tabela de construtores PUBLICOS de um .class externo (gate
 * ≤500: a varredura ASM vive aqui, a fachada {@link ExternalClasspath}
 * mantém a API). Casa somente {@code <init>} ACC_PUBLIC com a aridade dada;
 * construtor private/protected/package e classe abstrata/interface NAO
 * resolvem — a recusa honesta (SEM023, nunca "Undefined function"/SEM015)
 * começa na tabela de métodos reais do bytecode, não no palpite do typer.
 */
final class ExternalCtors {

    private ExternalCtors() {}

    /**
     * Varre os bytes do .class e devolve a assinatura do primeiro construtor
     * publico com {@param argumentCount} argumentos, ou null. Bytecode
     * ilegível registra warning (nunca falha silenciosa, R6).
     */
    static ExternalClasspath.MethodSignature publicConstructor(byte[] bytes, String internalName,
                                                               int argumentCount,
                                                               List<String> loadWarnings) {
        ExternalClasspath.MethodSignature[] hit = new ExternalClasspath.MethodSignature[1];
        boolean[] unusable = new boolean[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    unusable[0] = (access & (org.objectweb.asm.Opcodes.ACC_ABSTRACT
                            | org.objectweb.asm.Opcodes.ACC_INTERFACE)) != 0;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (name.equals("<init>") && !unusable[0] && hit[0] == null
                            && (access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0
                            && (access & org.objectweb.asm.Opcodes.ACC_STATIC) == 0
                            && org.objectweb.asm.Type.getMethodType(descriptor)
                                    .getArgumentTypes().length == argumentCount) {
                        org.objectweb.asm.Type mt =
                                org.objectweb.asm.Type.getMethodType(descriptor);
                        List<String> params = new java.util.ArrayList<>();
                        for (org.objectweb.asm.Type t : mt.getArgumentTypes()) {
                            params.add(t.getDescriptor());
                        }
                        hit[0] = new ExternalClasspath.MethodSignature(params,
                                mt.getReturnType().getDescriptor(), false, false,
                                (access & org.objectweb.asm.Opcodes.ACC_VARARGS) != 0);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            loadWarnings.add("class " + internalName + " could not be parsed: " + e.getMessage());
            return null;
        }
        return hit[0];
    }
}
