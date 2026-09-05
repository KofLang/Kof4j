package dev.kof.compiler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ExternalClasspath — assinaturas de membros vindas do build tool.
 *
 * O Gradle (Android) disponibiliza .jar/.aar das dependências antes da
 * compilação. Para emitir INVOKESPECIAL correto em chamadas super.metodo()
 * contra superclasses externas (ex.: super.onCreate(Bundle)), o compilador
 * precisa da assinatura real do método — o descritor emitido tem que casar
 * exatamente com o declarado na classe externa.
 *
 * A classe NÃO executa código nem resolve símbolos gerais: ela lê apenas as
 * tabelas de métodos dos .class dentro dos entries (jar/aar/diretório),
 * seguindo a cadeia de superclasses quando o membro é herdado.
 */
final class ExternalClasspath {

    /** Assinatura resolvida: descritores formais de params, retorno e flags. */
    record MethodSignature(List<String> parameterDescriptors, String returnDescriptor,
                           boolean isStatic, boolean ownerIsInterface) {
    }

    private final Map<String, byte[]> classBytes = new HashMap<>();
    private final List<String> loadWarnings = new ArrayList<>();
    private boolean loaded = false;

    /** Entradas que não puderam ser lidas (bytecode novo demais, zip corrompido). */
    public synchronized List<String> loadWarnings() {
        return List.copyOf(loadWarnings);
    }

    /**
     * Registra os entries do classpath externo (.jar, .aar ou diretório de
     * .class). Chamar antes do compile; pode ser vazio (JVM/Kof puro).
     */
    public synchronized void setEntries(List<Path> entries) throws IOException {
        classBytes.clear();
        loadWarnings.clear();
        loaded = false;
        if (entries == null) return;
        for (Path entry : entries) {
            if (!Files.exists(entry)) {
                loadWarnings.add("classpath entry not found: " + entry);
                continue;
            }
            try {
                if (Files.isDirectory(entry)) {
                    scanDirectory(entry, entry);
                } else {
                    String name = entry.getFileName().toString();
                    if (name.endsWith(".jar")) {
                        scanJar(entry, false);
                    } else if (name.endsWith(".aar")) {
                        // .aar: os .class ficam dentro de classes.jar aninhado
                        scanJar(entry, true);
                    }
                }
            } catch (IOException | RuntimeException e) {
                loadWarnings.add("classpath entry could not be read (" + entry + "): " + e.getMessage());
            }
        }
        loaded = true;
    }

    private void scanDirectory(Path root, Path current) throws IOException {
        try (var stream = Files.walk(current)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                if (!p.getFileName().toString().endsWith(".class")) continue;
                String rel = root.relativize(p).toString();
                String internal = rel.substring(0, rel.length() - ".class".length())
                        .replace('\\', '/');
                classBytes.put(internal, Files.readAllBytes(p));
            }
        }
    }

    private void scanJar(Path file, boolean nestedAar) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (nestedAar) {
                // .aar: os .class ficam dentro de classes.jar aninhado
                ZipEntry classes = zip.getEntry("classes.jar");
                if (classes == null) return;
                try (var in = new java.util.zip.ZipInputStream(zip.getInputStream(classes))) {
                    ZipEntry e;
                    while ((e = in.getNextEntry()) != null) {
                        if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                        classBytes.put(e.getName().substring(0, e.getName().length() - ".class".length()),
                                in.readAllBytes());
                    }
                }
                return;
            }
            var en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !e.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(e)) {
                    classBytes.put(e.getName().substring(0, e.getName().length() - ".class".length()),
                            in.readAllBytes());
                }
            }
        }
    }

    /**
     * Resolve a assinatura de um método declarado (ou herdado) numa classe
     * externa. {@param ownerInternalName} é o nome interno JVM
     * ("android/view/View"). Retorna null se não houver classpath externo,
     * a classe não estiver nos entries ou o método não existir.
     */
    public synchronized MethodSignature resolveMethod(String ownerInternalName,
                                                      String methodName,
                                                      int argumentCount) {
        if (!loaded || ownerInternalName == null) return null;
        MethodSignature direct = findDeclared(ownerInternalName, methodName, argumentCount, 0);
        if (direct != null) return direct;
        // membro herdado: segue a cadeia de superclasses nos entries
        String sup = superclassOf(ownerInternalName);
        int hops = 0;
        while (sup != null && !sup.equals("java/lang/Object") && hops++ < 32) {
            if (!classBytes.containsKey(sup)) {
                // bug 23: superclasse intermediária fora dos entries → a cadeia
                // é truncada silenciosamente e membros herdados não resolvem.
                // Avisa em vez de falhar mudo.
                loadWarnings.add("superclass '" + sup + "' of '" + ownerInternalName
                        + "' is not on the external classpath — inherited member '"
                        + methodName + "' may not resolve");
                return null;
            }
            MethodSignature inherited = findDeclared(sup, methodName, argumentCount, 0);
            if (inherited != null) return inherited;
            sup = superclassOf(sup);
        }
        return null;
    }

    /** Classe externa presente nos entries (por nome interno)? */
    public synchronized boolean knows(String internalName) {
        return loaded && internalName != null && classBytes.containsKey(internalName);
    }

    /** A classe externa é enum? */
    public synchronized boolean isEnum(String internalName) {
        byte[] bytes = internalName != null ? classBytes.get(internalName) : null;
        if (bytes == null) return false;
        boolean[] isEnum = new boolean[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    isEnum[0] = (access & org.objectweb.asm.Opcodes.ACC_ENUM) != 0;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            return false;
        }
        return isEnum[0];
    }

    /** Constante existe no enum externo? */
    public synchronized boolean hasEnumConstant(String internalName, String constant) {
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return false;
        boolean[] found = new boolean[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                                 String descriptor,
                                                                 String signature, Object value) {
                    if (name.equals(constant)
                            && (access & org.objectweb.asm.Opcodes.ACC_ENUM) != 0) {
                        found[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            return false;
        }
        return found[0];
    }

    /** A classe externa é interface? */
    public synchronized boolean isInterface(String internalName) {
        byte[] bytes = internalName != null ? classBytes.get(internalName) : null;
        if (bytes == null) return false;
        boolean[] iface = new boolean[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    iface[0] = (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            return false;
        }
        return iface[0];
    }

    /** Método abstrato único de uma interface externa (SAM), ou null. */
    public synchronized Sam resolveSam(String internalName) {
        if (!isInterface(internalName)) return null;
        List<Sam> found = new ArrayList<>();
        collectAbstractMethods(internalName, found, 0);
        // desce na cadeia de superinterfaces se necessário
        int hops = 0;
        String sup = firstSuperinterface(internalName);
        while (found.isEmpty() && sup != null && hops++ < 32) {
            collectAbstractMethods(sup, found, 0);
            sup = firstSuperinterface(sup);
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    private void collectAbstractMethods(String internalName, List<Sam> out, int depth) {
        if (depth > 32) return;
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return;
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    boolean isAbstract = (access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0;
                    boolean isStatic = (access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0;
                    if (isAbstract && !isStatic) {
                        out.add(new Sam(name,
                                toSignature(descriptor, false, true)));
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            // bytecode ilegível: trata como sem métodos
        }
    }

    /** Primeira superinterface da interface externa presente nos entries. */
    private String firstSuperinterface(String internalName) {
        final String[] first = new String[1];
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return null;
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    if (interfaces != null && interfaces.length > 0) first[0] = interfaces[0];
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            return null;
        }
        return first[0] != null && classBytes.containsKey(first[0]) ? first[0] : null;
    }

    /** Método abstrato único (SAM) de uma interface externa. */
    public record Sam(String methodName, MethodSignature signature) {
    }

    /** Superclasse declarada da classe externa (nome interno), ou null. */
    public synchronized String superClassOf(String internalName) {
        return superclassOf(internalName);
    }

    /**
     * Construtor da classe externa com a aridade dada ("<init>").
     */
    public synchronized MethodSignature resolveConstructor(String ownerInternalName,
                                                           int argumentCount) {
        return resolveMethod(ownerInternalName, "<init>", argumentCount);
    }

    /**
     * Campo declarado (ou herdado) numa classe externa. Retorna o
     * descritor do tipo do campo, ou null se não existir.
     */
    public synchronized String resolveFieldType(String ownerInternalName, String fieldName) {
        if (!loaded || ownerInternalName == null) return null;
        String direct = findFieldDeclared(ownerInternalName, fieldName, 0);
        if (direct != null) return direct;
        String sup = superclassOf(ownerInternalName);
        int hops = 0;
        while (sup != null && !sup.equals("java/lang/Object") && hops++ < 32) {
            if (!classBytes.containsKey(sup)) {
                loadWarnings.add("superclass '" + sup + "' of '" + ownerInternalName
                        + "' is not on the external classpath — inherited field '"
                        + fieldName + "' may not resolve");
                return null;
            }
            String inherited = findFieldDeclared(sup, fieldName, 0);
            if (inherited != null) return inherited;
            sup = superclassOf(sup);
        }
        return null;
    }

    private String findFieldDeclared(String internalName, String fieldName, int depth) {
        if (depth > 64) return null;
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return null;
        final String[] hit = new String[1];
        try {
            new ClassReader(bytes).accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                                 String descriptor,
                                                                 String signature, Object value) {
                    if (name.equals(fieldName) && hit[0] == null) hit[0] = descriptor;
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return hit[0];
        } catch (Exception e) {
            loadWarnings.add("class " + internalName + " could not be parsed: " + e.getMessage());
            return null;
        }
    }

    private MethodSignature findDeclared(String internalName, String methodName,
                                         int argumentCount, int recursionDepth) {
        if (recursionDepth > 64) return null;
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            MethodSignature[] hit = new MethodSignature[1];
            boolean[] iface = new boolean[1];
            reader.accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    iface[0] = (access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (name.equals(methodName)
                            && org.objectweb.asm.Type.getMethodType(descriptor)
                                    .getArgumentTypes().length == argumentCount
                            && hit[0] == null) {
                        hit[0] = toSignature(descriptor,
                                (access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0,
                                iface[0]);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return hit[0];
        } catch (Exception e) {
            // bytecode além do suportado pelo ASM embutido (ex.: major novo)
            // — registrado como warning, nunca falha silenciosa
            loadWarnings.add("class " + internalName + " could not be parsed: " + e.getMessage());
            return null;
        }
    }

    private String superclassOf(String internalName) {
        byte[] bytes = classBytes.get(internalName);
        if (bytes == null) return null;
        try {
            ClassReader reader = new ClassReader(bytes);
            String[] sup = new String[1];
            reader.accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    sup[0] = superName;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            return sup[0];
        } catch (Exception e) {
            return null;
        }
    }

    private static MethodSignature toSignature(String methodDescriptor, boolean isStatic,
                                                boolean ownerIsInterface) {
        org.objectweb.asm.Type[] args =
                org.objectweb.asm.Type.getMethodType(methodDescriptor).getArgumentTypes();
        List<String> params = new ArrayList<>();
        for (org.objectweb.asm.Type t : args) params.add(t.getDescriptor());
        return new MethodSignature(params,
                org.objectweb.asm.Type.getMethodType(methodDescriptor).getReturnType().getDescriptor(),
                isStatic, ownerIsInterface);
    }

    /**
     * Converte um descritor de campo/retorno JVM num Type do Kof.
     * Primitivos preservam identidade; classes viram ClassType com pacote.
     */
    static Type typeFromDescriptor(String descriptor) {
        return switch (descriptor) {
            case "V" -> Type.PrimitiveType.VOID;
            case "Z" -> Type.PrimitiveType.BOOL;
            case "B" -> Type.PrimitiveType.BYTE;
            case "S" -> Type.PrimitiveType.SHORT;
            case "C" -> Type.PrimitiveType.CHAR;
            case "I" -> Type.PrimitiveType.INT;
            case "J" -> Type.PrimitiveType.LONG;
            case "F" -> Type.PrimitiveType.FLOAT;
            case "D" -> Type.PrimitiveType.DOUBLE;
            default -> {
                if (descriptor.startsWith("[")) {
                    yield new Type.ArrayType(typeFromDescriptor(descriptor.substring(1)));
                }
                if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
                    String internal = descriptor.substring(1, descriptor.length() - 1);
                    if (internal.equals("java/lang/String")) yield BuiltinTypes.STRING;
                    int slash = internal.lastIndexOf('/');
                    String pkg = slash >= 0 ? internal.substring(0, slash).replace('/', '.') : "";
                    String name = slash >= 0 ? internal.substring(slash + 1) : internal;
                    yield new Type.ClassType(pkg, name, List.of());
                }
                yield Type.UnknownType.UNKNOWN;
            }
        };
    }
}
