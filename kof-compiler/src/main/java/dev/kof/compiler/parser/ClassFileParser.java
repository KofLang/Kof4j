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
        public final List<String> exceptions;
        public final CodeAttribute code;
        public final Type returnType;
        public final List<Type> parameterTypes;
        public final int instanceofCount;
        public final int checkcastCount;

        MethodInfo(int accessFlags, String name, String descriptor,
                   List<String> exceptions, CodeAttribute code) {
            this.accessFlags = accessFlags;
            this.name = name;
            this.descriptor = descriptor;
            this.exceptions = exceptions;
            this.code = code;

            TypeParseResult types = parseDescriptor(descriptor);
            this.returnType = types.returnType();
            this.parameterTypes = types.parameterTypes();
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
                Type t = Type.fromJvmDescriptor(params.substring(pos));
                paramTypes.add(t);
                pos = skipDescriptorLength(params, pos);
            }

            Type retType = Type.fromJvmDescriptor(returns);
            return new TypeParseResult(retType, paramTypes);
        }

        private static int skipDescriptorLength(String desc, int pos) {
            if (pos >= desc.length()) return pos;
            return pos + 1;
        }

        private record TypeParseResult(Type returnType, List<Type> parameterTypes) {}
    }

    public static class FieldInfo {
        public final int accessFlags;
        public final String name;
        public final String descriptor;

        FieldInfo(int accessFlags, String name, String descriptor) {
            this.accessFlags = accessFlags;
            this.name = name;
            this.descriptor = descriptor;
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
        public final List<FieldInfo> fields;
        public final List<MethodInfo> methods;
        public final Map<String, Object> attributes;

        ClassFile(int magic, int minorVersion, int majorVersion,
                  String[] constantPool, int accessFlags, String thisClass,
                  String superClass, String[] interfaces,
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
                case 3: case 4: // Integer, Float
                    constPool[i] = String.valueOf(bb.getInt());
                    break;
                case 5: case 6: // Long, Double — 8 bytes, ocupam 2 slots
                    constPool[i] = String.valueOf(bb.getLong());
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
                case 16: // Dynamic
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 17: // MethodType
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 18: // InvokeDynamic
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
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
            int attrCount = bb.getShort() & 0xFFFF;
            for (int j = 0; j < attrCount; j++) {
                bb.getShort(); // attr_name_index
                int attrLen = bb.getInt();
                bb.position(bb.position() + attrLen);
            }
            fields.add(new FieldInfo(fieldAccess, fieldName, fieldDesc));
        }

        int methodCount = bb.getShort() & 0xFFFF;
        List<MethodInfo> methods = new ArrayList<>();
        for (int i = 0; i < methodCount; i++) {
            int methodAccess = bb.getShort() & 0xFFFF;
            String methodName = constPool[bb.getShort() & 0xFFFF];
            String methodDesc = constPool[bb.getShort() & 0xFFFF];
            List<String> exceptions = new ArrayList<>();
            CodeAttribute codeAttr = null;
            int attrCount = bb.getShort() & 0xFFFF;
            for (int j = 0; j < attrCount; j++) {
                int attrNameIdx = bb.getShort() & 0xFFFF;
                String attrName = constPool[attrNameIdx];
                int attrLen = bb.getInt();
                if ("Exceptions".equals(attrName)) {
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
            methods.add(new MethodInfo(methodAccess, methodName, methodDesc, exceptions, codeAttr));
        }

        int attrCount = bb.getShort() & 0xFFFF;
        Map<String, Object> attrs = new HashMap<>();
        for (int i = 0; i < attrCount; i++) {
            int attrNameIdx = bb.getShort() & 0xFFFF;
            String attrName = constPool[attrNameIdx];
            int attrLen = bb.getInt();
            bb.position(bb.position() + attrLen);
            attrs.put(attrName, "size=" + attrLen);
        }

        return new ClassFile(magic, minorVersion, majorVersion,
                constPool, accessFlags, thisClass, superClass,
                interfaces, fields, methods, attrs);
    }

    private static String resolveClass(String[] constPool, int idx) {
        if (idx >= constPool.length) return "INVALID";
        String entry = constPool[idx];
        if (entry != null && entry.startsWith("#")) {
            int cpIdx = Integer.parseInt(entry.substring(1));
            if (cpIdx < constPool.length && constPool[cpIdx] != null) {
                return constPool[cpIdx];
            }
        }
        return entry;
    }
}
