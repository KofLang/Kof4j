package dev.kof.compiler.parser;

import dev.kof.compiler.Type;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClassFileParser {

    public static class MethodInfo {
        public final int accessFlags;
        public final String name;
        public final String descriptor;
        /** JVMS 4.7.1: assinatura genérica (null quando o .class não a tem). */
        public final String signature;
        public final List<String> exceptions;
        public final CodeAttribute code;
        public final Type returnType;
        public final List<Type> parameterTypes;
        public final int instanceofCount;
        public final int checkcastCount;

        MethodInfo(int accessFlags, String name, String descriptor, String signature,
                   List<String> exceptions, CodeAttribute code) {
            this.accessFlags = accessFlags;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
            this.code = code;

            // Fase D (Type Recovery): o atributo Signature preserva genéricos
            // que o descriptor apagou (erasure). Quando existe, é a fonte
            // EXACT; senão cai no descriptor (sem args de tipo).
            if (signature != null && signature.startsWith("(")) {
                Type.SignatureParseResult sp = Type.parseMethodSignature(signature);
                this.returnType = sp.returnType();
                this.parameterTypes = sp.parameterTypes();
            } else {
                TypeParseResult types = parseDescriptor(descriptor);
                this.returnType = types.returnType();
                this.parameterTypes = types.parameterTypes();
            }
            this.instanceofCount = code != null ? countInstanceofCheckcast(code.bytecode) : 0;
            this.checkcastCount = code != null ? countCheckcast(code.bytecode) : 0;
        }

        public String returnTypeName() {
            return Type.describe(returnType);
        }

        public List<String> parameterTypeNames() {
            return parameterTypes.stream().map(Type::describe).toList();
        }

        private static int countInstanceofCheckcast(byte[] bytecode) {
            return countOp(bytecode, 0xC1) + countOp(bytecode, 0xC0);
        }

        private static int countCheckcast(byte[] bytecode) {
            return countOp(bytecode, 0xC0);
        }

        private static int countOp(byte[] bytecode, int opcode) {
            int count = 0;
            for (int pc = 0; pc < bytecode.length; pc++) {
                if ((bytecode[pc] & 0xFF) == opcode) {
                    count++;
                }
            }
            return count;
        }

        private static TypeParseResult parseDescriptor(String desc) {
            if (desc == null || desc.isEmpty()) return new TypeParseResult(Type.UnknownType.UNKNOWN, List.of());
            if (!desc.startsWith("(")) return new TypeParseResult(Type.UnknownType.UNKNOWN, List.of());

            int end = desc.indexOf(')');
            if (end == -1) return new TypeParseResult(Type.UnknownType.UNKNOWN, List.of());

            String params = desc.substring(1, end);
            String returns = desc.substring(end + 1);

            List<Type> paramTypes = new ArrayList<>();
            int pos = 0;
            while (pos < params.length()) {
                Type.ParseResult pr = Type.parseJvmDescriptorAt(params, pos);
                paramTypes.add(pr.type());
                pos = pr.pos();
            }

            Type retType = Type.fromJvmDescriptor(returns);
            return new TypeParseResult(retType, paramTypes);
        }

        private record TypeParseResult(Type returnType, List<Type> parameterTypes) {}
    }

    public static class FieldInfo {
        public final int accessFlags;
        public final String name;
        public final String descriptor;
        /** JVMS 4.7.1: assinatura genérica (null quando o .class não a tem). */
        public final String signature;

        /** JVMS 4.7.2: valor de constante `static final` (null na maioria). */
        public final String constantValue;

        FieldInfo(int accessFlags, String name, String descriptor, String signature) {
            this(accessFlags, name, descriptor, signature, null);
        }

        FieldInfo(int accessFlags, String name, String descriptor, String signature, String constantValue) {
            this.accessFlags = accessFlags;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.constantValue = constantValue;
        }
    }

    public static final class ExceptionHandler {
        public final int startPc;
        public final int endPc;
        public final int handlerPc;
        public final String catchType;

        ExceptionHandler(int startPc, int endPc, int handlerPc, String catchType) {
            this.startPc = startPc;
            this.endPc = endPc;
            this.handlerPc = handlerPc;
            this.catchType = catchType;
        }
    }

    public static final class CodeAttribute {
        public final int maxStack;
        public final int maxLocals;
        public final byte[] bytecode;
        public final List<ExceptionHandler> exceptionHandlers;

        CodeAttribute(int maxStack, int maxLocals, byte[] bytecode,
                      List<ExceptionHandler> exceptionHandlers) {
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
            this.bytecode = bytecode;
            this.exceptionHandlers = exceptionHandlers;
        }
    }

    public static class ClassFile {
        public final int magic;
        public final int minorVersion;
        public final int majorVersion;
        public final String[] constantPool;
        public final int accessFlags;
        public final String thisClass;
        public final String superClass;
        public final String[] interfaces;
        /** JVMS 4.7.1: assinatura genérica da CLASSE (null se sem genéricos). */
        public final String classSignature;
        public final List<FieldInfo> fields;
        public final List<MethodInfo> methods;
        public final Map<String, Object> attributes;

        ClassFile(int magic, int minorVersion, int majorVersion,
                  String[] constantPool, int accessFlags, String thisClass,
                  String superClass, String[] interfaces, String classSignature,
                  List<FieldInfo> fields, List<MethodInfo> methods,
                  Map<String, Object> attributes) {
            this.magic = magic;
            this.minorVersion = minorVersion;
            this.majorVersion = majorVersion;
            this.constantPool = constantPool;
            this.accessFlags = accessFlags;
            this.thisClass = thisClass;
            this.superClass = superClass;
            this.interfaces = interfaces;
            this.classSignature = classSignature;
            this.fields = fields;
            this.methods = methods;
            this.attributes = attributes;
        }
    }

    public static ClassFile parse(InputStream in) throws IOException {
        byte[] classBytes = in.readAllBytes();
        ByteBuffer bb = ByteBuffer.wrap(classBytes);

        int magic = bb.getInt();
        if (magic != 0xCAFEBABE) {
            throw new IOException("Invalid magic: " + Integer.toHexString(magic));
        }

        int minorVersion = bb.getShort() & 0xFFFF;
        int majorVersion = bb.getShort() & 0xFFFF;

        int constantPoolCount = bb.getShort() & 0xFFFF;
        String[] constPool = new String[constantPoolCount];
        for (int i = 1; i < constantPoolCount; i++) {
            int tag = bb.get() & 0xFF;
            switch (tag) {
                case 1: // UTF8
                    int len = bb.getShort() & 0xFFFF;
                    byte[] bytes = new byte[len];
                    bb.get(bytes);
                    constPool[i] = new String(bytes, StandardCharsets.UTF_8);
                    break;
                case 7: // Class
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 8: // String
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 3: // Integer — 4 bytes
                    constPool[i] = String.valueOf(bb.getInt());
                    break;
                case 4: // Float — 4 bytes, valor float (não os bits crus)
                    constPool[i] = String.valueOf(Float.intBitsToFloat(bb.getInt()));
                    break;
                case 5: // Long — 8 bytes, ocupa 2 slots
                    constPool[i] = String.valueOf(bb.getLong());
                    i++;
                    break;
                case 6: // Double — 8 bytes, valor double, ocupa 2 slots
                    constPool[i] = String.valueOf(Double.longBitsToDouble(bb.getLong()));
                    i++;
                    break;
                case 9: case 10: case 11: // Fieldref, Methodref, InterfaceMethodref
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 12: // NameAndType
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 15: // MethodHandle — 1 byte (ref_kind) + 1 short (reference_index)
                    bb.get();
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 16: // MethodType (JVMS 4.4.8) — u2 descriptor_index
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 17: // Dynamic (JVMS 4.4.10) — u2 class_index + u2 name_and_type_index
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 18: // InvokeDynamic — "#" bootstrap#NameAndType (resolvido
                         // p/ "CONCAT:<receita>" após ler BootstrapMethods)
                    constPool[i] = "IDYN:" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 19: // Module
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 20: // Package
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                default:
                    constPool[i] = "tag=" + tag;
            }
        }

        int accessFlags = bb.getShort() & 0xFFFF;
        String thisClass = resolveClass(constPool, bb.getShort() & 0xFFFF);
        String superClass = resolveClass(constPool, bb.getShort() & 0xFFFF);

        int interfaceCount = bb.getShort() & 0xFFFF;
        String[] interfaces = new String[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            interfaces[i] = resolveClass(constPool, bb.getShort() & 0xFFFF);
        }

        int fieldCount = bb.getShort() & 0xFFFF;
        List<FieldInfo> fields = new ArrayList<>();
        for (int i = 0; i < fieldCount; i++) {
            int fieldAccess = bb.getShort() & 0xFFFF;
            String fieldName = constPool[bb.getShort() & 0xFFFF];
            String fieldDesc = constPool[bb.getShort() & 0xFFFF];
            String fieldSig = null;
            String constantValue = null;
            int attrCount = bb.getShort() & 0xFFFF;
            for (int j = 0; j < attrCount; j++) {
                int attrNameIdx = bb.getShort() & 0xFFFF;
                String attrName = constPool[attrNameIdx];
                int attrLen = bb.getInt();
                if ("Signature".equals(attrName)) {
                    fieldSig = constPool[bb.getShort() & 0xFFFF];
                } else if ("ConstantValue".equals(attrName)) {
                    // JVMS 4.7.2: constantes `static final` NÃO passam pelo
                    // clinit (o valor vem deste attribute) — o decompilador
                    // usa para dobrar o initializer (ADITIVO)
                    int cvIdx = bb.getShort() & 0xFFFF;
                    constantValue = cvIdx > 0 && cvIdx < constPool.length ? constPool[cvIdx] : null;
                } else {
                    bb.position(bb.position() + attrLen);
                }
            }
            fields.add(new FieldInfo(fieldAccess, fieldName, fieldDesc, fieldSig, constantValue));
        }

        int methodCount = bb.getShort() & 0xFFFF;
        List<MethodInfo> methods = new ArrayList<>();
        for (int i = 0; i < methodCount; i++) {
            int methodAccess = bb.getShort() & 0xFFFF;
            String methodName = constPool[bb.getShort() & 0xFFFF];
            String methodDesc = constPool[bb.getShort() & 0xFFFF];
            List<String> exceptions = new ArrayList<>();
            CodeAttribute codeAttr = null;
            String methodSig = null;
            int attrCount = bb.getShort() & 0xFFFF;
            for (int j = 0; j < attrCount; j++) {
                int attrNameIdx = bb.getShort() & 0xFFFF;
                String attrName = constPool[attrNameIdx];
                int attrLen = bb.getInt();
                if ("Signature".equals(attrName)) {
                    methodSig = constPool[bb.getShort() & 0xFFFF];
                } else if ("Exceptions".equals(attrName)) {
                    int exCount = bb.getShort() & 0xFFFF;
                    for (int k = 0; k < exCount; k++) {
                        exceptions.add(resolveClass(constPool, bb.getShort() & 0xFFFF));
                    }
                } else if ("Code".equals(attrName)) {
                    int maxStack = bb.getShort() & 0xFFFF;
                    int maxLocals = bb.getShort() & 0xFFFF;
                    int codeLen = bb.getInt();
                    byte[] bytecode = new byte[codeLen];
                    bb.get(bytecode);
                    int exHandlerCount = bb.getShort() & 0xFFFF;
                    List<ExceptionHandler> handlers = new ArrayList<>();
                    for (int h = 0; h < exHandlerCount; h++) {
                        int startPc = bb.getShort() & 0xFFFF;
                        int endPc = bb.getShort() & 0xFFFF;
                        int handlerPc = bb.getShort() & 0xFFFF;
                        String catchType = resolveClass(constPool, bb.getShort() & 0xFFFF);
                        handlers.add(new ExceptionHandler(startPc, endPc, handlerPc, catchType));
                    }
                    int innerAttrCount = bb.getShort() & 0xFFFF;
                    for (int a = 0; a < innerAttrCount; a++) {
                        bb.getShort();
                        int innerLen = bb.getInt();
                        bb.position(bb.position() + innerLen);
                    }
                    codeAttr = new CodeAttribute(maxStack, maxLocals, bytecode, handlers);
                } else {
                    bb.position(bb.position() + attrLen);
                }
            }
            methods.add(new MethodInfo(methodAccess, methodName, methodDesc, methodSig, exceptions, codeAttr));
        }

        int attrCount = bb.getShort() & 0xFFFF;
        Map<String, Object> attrs = new HashMap<>();
        String classSig = null;
        String[] bootstrapRecipes = new String[0];
        for (int i = 0; i < attrCount; i++) {
            int attrNameIdx = bb.getShort() & 0xFFFF;
            String attrName = constPool[attrNameIdx];
            int attrLen = bb.getInt();
            if ("Signature".equals(attrName)) {
                classSig = constPool[bb.getShort() & 0xFFFF];
            } else if ("BootstrapMethods".equals(attrName)) {
                // JVMS 4.7.23 — receitas de makeConcatWithConstants (String +
                // moderno é invokedynamic; sem isto todo corpo com concat
                // caía em stub honesto, a forma de corpo mais comum em Java).
                bootstrapRecipes = readBootstrapRecipes(bb, constPool, attrLen);
            } else {
                bb.position(bb.position() + attrLen);
            }
            attrs.put(attrName, "size=" + attrLen);
        }
        resolveInvokeDynamics(constPool, bootstrapRecipes);

        return new ClassFile(magic, minorVersion, majorVersion,
                constPool, accessFlags, thisClass, superClass,
                interfaces, classSig, fields, methods, attrs);
    }

    /**
     * Indice de constPool a partir de um slot "#N" AUTO-PRODUZIDO pelo parse.
     * Utf8 arbitrario (tag 1) pode morar em qualquer slot e chegar aqui via
     * this_class/super/refs (#247/#248: parseInt derramava NFE crua em
     * .class corrompido/adversario lido do classpath). -1 = nao resolvivel
     * (mesma saida do no-match: o chamador usa a entrada crua).
     */
    private static int parseCpIndex(String s) {
        if (s == null || s.isEmpty()) return -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1; // "00000000001234567890..." digital mas fora do int
        }
    }

    private static String resolveClass(String[] constPool, int idx) {
        if (idx >= constPool.length) return "INVALID";
        String entry = constPool[idx];
        if (entry != null && entry.startsWith("#")) {
            int cpIdx = parseCpIndex(entry.substring(1));
            if (cpIdx >= 0 && cpIdx < constPool.length && constPool[cpIdx] != null) {
                return constPool[cpIdx];
            }
        }
        return entry;
    }

    /**
     * JVMS 4.7.23 BootstrapMethods: num_bootstrap_methods, e para cada um:
     * bootstrap_method_ref (u2), num_bootstrap_arguments (u2), e os args como
     * ÍNDICES u2 p/ o CP (NÃO cp_info!). O PRIMEIRO arg da StringConcatFactory
     * é a String RECEITA (constante tag-8 → guardada "#<utf8>"; resolvemos o
     * duplo indirecionamento). \u0001 = placeholder de arg. Falha de schema →
     * array vazio → invokedynamic fica IDYN → o decoder recusa → stub honesto.
     */
    private static String[] readBootstrapRecipes(ByteBuffer bb, String[] constPool, int attrLen) {
        int start = bb.position();
        try {
            int n = bb.getShort() & 0xFFFF;
            String[] recipes = new String[n];
            for (int i = 0; i < n; i++) {
                bb.getShort();  // bootstrap_method_ref (avança; não usado — CodeQL #1155)
                int nargs = bb.getShort() & 0xFFFF;
                recipes[i] = null;
                int implHandle = -1;
                int stringArg = -1;
                for (int a = 0; a < nargs; a++) {
                    int idx = bb.getShort() & 0xFFFF;
                    // Fase E (14/10): LAMBDA primeiro — o arg[1] é o
                    // MethodHandle do método de implementação (lambda$X$0).
                    // O arg[0] (a receita do CONCAT ou um MethodType — o
                    // formato "#idx" não distingue tag 8 de 16) só vale
                    // como receita quando NÃO é lambda (medido: o
                    // MethodType do lambda produzia a receita falsa
                    // "(Ljava/lang/Object;)Z" e escondia o LAMBDA).
                    if (a == 1) implHandle = idx;
                    if (a == 0) stringArg = idx;
                }
                if (implHandle > 0) {
                    String nm = implNameFromHandle(constPool, implHandle);
                    if (nm != null) recipes[i] = "LAMBDA:" + nm;
                }
                if (recipes[i] == null && stringArg > 0 && stringArg < constPool.length
                        && constPool[stringArg] != null && constPool[stringArg].startsWith("#")) {
                    // tag-8 String → "#<utf8Idx>" → o Utf8 é a receita
                    int ui = Integer.parseInt(constPool[stringArg].substring(1));
                    if (ui < constPool.length && constPool[ui] != null) recipes[i] = constPool[ui];
                }
                // Fase E (14/10): LAMBDA — sem receita String, o arg[1] é o
                // MethodHandle do MÉTODO de implementação (lambda$X$0);
                // resolve a cadeia MethodHandle→Methodref→NameAndType→nome.
                if (recipes[i] == null && implHandle > 0) {
                    String nm = implNameFromHandle(constPool, implHandle);
                    if (nm != null) recipes[i] = "LAMBDA:" + nm;
                }
            }
            return recipes;
        } catch (RuntimeException e) {
            return new String[0];
        } finally {
            bb.position(start + attrLen);            // robusto ao schema real
        }
    }

    /** MethodHandle → Methodref → NameAndType → nome do método, ou null. */
    private static String implNameFromHandle(String[] constPool, int mhIdx) {
        if (mhIdx >= constPool.length || constPool[mhIdx] == null
                || !constPool[mhIdx].startsWith("#")) return null;
        int refIdx = parseCpIndex(constPool[mhIdx].substring(1));
        if (refIdx < 0 || refIdx >= constPool.length || constPool[refIdx] == null) return null;
        String ref = constPool[refIdx];
        int h1 = ref.indexOf('#', 1);
        if (h1 < 0) return null;
        int natIdx = parseCpIndex(ref.substring(h1 + 1));
        if (natIdx < 0 || natIdx >= constPool.length || constPool[natIdx] == null) return null;
        String nat = constPool[natIdx];
        int h2 = nat.indexOf('#', 1);
        if (h2 < 0) return null;
        int nameIdx = parseCpIndex(nat.substring(1, h2));
        if (nameIdx < 0 || nameIdx >= constPool.length || constPool[nameIdx] == null) return null;
        return constPool[nameIdx];
    }

    /**
     * Reescreve entradas tag-18 (IDYN:bs#nameType) do CP p/ "CONCAT:<receita>"
     * quando o NameAndType é makeConcatWithConstants e a bootstrap tem receita
     * String. Qualquer outro invokedynamic (lambdas, etc.) fica IDYN → o decoder
     * recusa (default) → stub honesto.
     */
    private static void resolveInvokeDynamics(String[] constPool, String[] recipes) {
        if (recipes.length == 0) return;
        for (int i = 1; i < constPool.length; i++) {
            String e = constPool[i];
            if (e == null || !e.startsWith("IDYN:")) continue;
            int hash = e.indexOf('#', 5);
            if (hash < 0) continue;
            int bsIdx;
            int ntIdx;
            try {
                bsIdx = Integer.parseInt(e.substring(5, hash));
                ntIdx = Integer.parseInt(e.substring(hash + 1));
            } catch (NumberFormatException ex) { continue; }
            if (bsIdx < 0 || bsIdx >= recipes.length || recipes[bsIdx] == null) continue;
            if (ntIdx >= constPool.length) continue;
            String nt = constPool[ntIdx];
            if (nt == null || !nt.startsWith("#")) continue;
            int hash2 = nt.indexOf('#', 1);
            if (hash2 < 0) continue;
            int nameIdx = parseCpIndex(nt.substring(1, hash2));
            if (nameIdx < 0) continue; // .class corrompido: "#<utf8>" no slot NameAndType
            if ("makeConcatWithConstants".equals(constPool[nameIdx])) {
                constPool[i] = "CONCAT:" + recipes[bsIdx];
            } else if (recipes[bsIdx] != null && recipes[bsIdx].startsWith("LAMBDA:")) {
                // Fase E (14/10): o invokedynamic do LAMBDA expõe o nome do
                // método de implementação (lambda$X$0) — o decompilador
                // recupera o corpo dele e emite a lambda Kof.
                constPool[i] = recipes[bsIdx];
            }
        }
    }
}
