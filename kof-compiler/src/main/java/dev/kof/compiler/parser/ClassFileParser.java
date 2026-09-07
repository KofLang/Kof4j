package dev.kof.compiler.parser;

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

        MethodInfo(int accessFlags, String name, String descriptor, List<String> exceptions) {
            this.accessFlags = accessFlags;
            this.name = name;
            this.descriptor = descriptor;
            this.exceptions = exceptions;
        }
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

    public static class ClassFile {
        public final int magic;
        public final int minorVersion;
        public final int majorVersion;
        public final int[] constantPoolCount;
        public final String[] constantPoolEntries;
        public final int accessFlags;
        public final String thisClass;
        public final String superClass;
        public final String[] interfaces;
        public final List<FieldInfo> fields;
        public final List<MethodInfo> methods;
        public final Map<String, Object> attributes;

        ClassFile(int magic, int minorVersion, int majorVersion,
                  int[] constantPoolCount, String[] constantPoolEntries,
                  int accessFlags, String thisClass, String superClass,
                  String[] interfaces, List<FieldInfo> fields,
                  List<MethodInfo> methods, Map<String, Object> attributes) {
            this.magic = magic;
            this.minorVersion = minorVersion;
            this.majorVersion = majorVersion;
            this.constantPoolCount = constantPoolCount;
            this.constantPoolEntries = constantPoolEntries;
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
                case 3: case 4: case 5: case 6:
                    constPool[i] = String.valueOf(bb.getInt());
                    break;
                case 9: case 10: case 11:
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 12:
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 15:
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 17:
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
                bb.position(bb.position() + 2 + bb.getInt());
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
            int attrCount = bb.getShort() & 0xFFFF;
            for (int j = 0; j < attrCount; j++) {
                int attrNameIdx = bb.getShort() & 0xFFFF;
                String attrName = constPool[attrNameIdx];
                int attrLen = bb.getInt();
                if ("Exceptions".equals(attrName)) {
                    int exCount = bb.getShort() & 0xFFFF;
                    for (int k = 0; k < exCount; k++) {
                        exceptions.add(constPool[bb.getShort() & 0xFFFF]);
                    }
                } else {
                    bb.position(bb.position() + attrLen);
                }
            }
            methods.add(new MethodInfo(methodAccess, methodName, methodDesc, exceptions));
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
                new int[]{constantPoolCount}, constPool,
                accessFlags, thisClass, superClass,
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