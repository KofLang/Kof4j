package dev.kof.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClassFileParser {

    public static final class FieldInfo {
        public final int accessFlags;
        public final String name;
        public final String descriptor;

        public FieldInfo(int accessFlags, String name, String descriptor) {
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

        public ExceptionHandler(int startPc, int endPc, int handlerPc, String catchType) {
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

        public CodeAttribute(int maxStack, int maxLocals, byte[] bytecode,
                             List<ExceptionHandler> exceptionHandlers) {
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
            this.bytecode = bytecode;
            this.exceptionHandlers = exceptionHandlers;
        }
    }

    public static final class MethodInfo {
        public final int accessFlags;
        public final String name;
        public final String descriptor;
        public final List<String> exceptions;
        public final CodeAttribute code;
        public final List<Instruction> instructions;
        public final Type returnType;
        public final List<Type> parameterTypes;
        public final int instanceofCount;
        public final int checkcastCount;

        public MethodInfo(int accessFlags, String name, String descriptor,
                          List<String> exceptions, CodeAttribute code) {
            this.accessFlags = accessFlags;
            this.name = name;
            this.descriptor = descriptor;
            this.exceptions = exceptions;
            this.code = code;
            this.instructions = code != null ? decodeInstructions(code.bytecode, exceptions) : List.of();
            
            TypeParseResult types = parseDescriptor(descriptor);
            this.returnType = types.returnType;
            this.parameterTypes = types.parameterTypes;
            this.instanceofCount = countInstanceofCheckcast(code.bytecode);
            this.checkcastCount = countCheckcast(code.bytecode);
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

        private static List<Instruction> decodeInstructions(byte[] bytecode, List<String> exceptions) {
            List<Instruction> instrs = new ArrayList<>();
            for (int pc = 0; pc < bytecode.length; ) {
                int opcode = bytecode[pc] & 0xFF;
                String name = opcode < OPCODES.length && OPCODES[opcode] != null ? OPCODES[opcode] : "unknown_" + opcode;
                instrs.add(new Instruction(pc, name, ""));
                pc++;
            }
            return instrs;
        }
    }

    public static final class ClassFile {
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

        public ClassFile(int magic, int minorVersion, int majorVersion,
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
                case 3: case 4: case 5: case 6: // Number
                    constPool[i] = String.valueOf(bb.getInt());
                    break;
                case 9: case 10: case 11: // Fieldref, Methodref, InterfaceMethodref
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 12: // NameAndType
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF) + "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 15: // MethodHandle
                    constPool[i] = "#" + (bb.getShort() & 0xFFFF);
                    break;
                case 17: // MethodType
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
                            int innerNameIdx = bb.getShort() & 0xFFFF;
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

    public static final class Instruction {
        public final int offset;
        public final String opcode;
        public final String operand;

        public Instruction(int offset, String opcode, String operand) {
            this.offset = offset;
            this.opcode = opcode;
            this.operand = operand;
        }

        @Override
        public String toString() {
            if (operand != null && !operand.isEmpty()) return String.format("%04d: %-20s %s", offset, opcode, operand);
            return offset + ": " + opcode;
        }
    }

    public static final class BasicBlock {
        public final int startPc;
        public final List<Instruction> instructions = new ArrayList<>();
        public final List<Integer> successors = new ArrayList<>();
        public final boolean isTryStart;

        public BasicBlock(int startPc, boolean isTryStart) {
            this.startPc = startPc;
            this.isTryStart = isTryStart;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Block @").append(startPc);
            for (Instruction i : instructions) sb.append("\n  ").append(i);
            return sb.toString();
        }
    }

    private static String[] OPCODES = {
        "nop", "iconst_m1", "iconst_0", "iconst_1", "iconst_2", "iconst_3", "iconst_4", "iconst_5", "iconst_6",
        "lconst_0", "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0", "dconst_1",
        "bip", "sip", "wide", "lconst", "fconst", "dconst",
        "istore", "lstore", "fstore", "dstore", "astore", "istore_0", "istore_1", "istore_2", "istore_3",
        "lstore_0", "lstore_1", "lstore_2", "lstore_3", "fstore_0", "fstore_1", "fstore_2", "fstore_3",
        "dstore_0", "dstore_1", "dstore_2", "dstore_3", "astore_0", "astore_1", "astore_2", "astore_3",
        "iload", "lload", "fload", "dload", "aload", "iload_0", "iload_1", "iload_2", "iload_3",
        "lload_0", "lload_1", "lload_2", "lload_3", "fload_0", "fload_1", "fload_2", "fload_3",
        "dload_0", "dload_1", "dload_2", "dload_3", "aload_0", "aload_1", "aload_2", "aload_3",
        "return", "ireturn", "lreturn", "freturn", "dreturn", "areturn",
        "putstatic", "getstatic", "putfield", "getfield",
        "invokestatic", "invokevirtual", "invokespecial", "invokedynamic", "invokeinterface",
        "athrow", "instancesof", "checkcast", "new", "anewarray", "arraylength",
        "arraystore", "arrayload",
        "iadd", "isub", "imul", "idiv", "irem", "ishl", "ishr", "iushr", "iand", "ior", "ixor", "iinc",
        "ladd", "lsub", "lmul", "ldiv", "lrem", "lshl", "lshr", "lushr", "land", "lor", "lxor", "lconst",
        "fadd", "fsub", "fmul", "fdiv", "frem",
        "dadd", "dsub", "dmul", "ddiv", "drem",
        "i2b", "i2c", "i2s", "i2l", "i2f", "i2d",
        "l2i", "l2f", "l2d",
        "f2i", "f2l", "f2d",
        "d2i", "d2l", "d2f",
        "icmpeq", "icmpne", "icmplt", "icmpge", "icmpgt", "icmple",
        "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle",
        "ifnonnull", "ifnull",
        "goto", "jsr", "ret", "jsr_w", "goto_w",
        "tableswitch", "lookupswitch",
        "invokespecial", "invokestatic", "invokevirtual", "invokeinterface",
        "multianewarray",
        "goto", "jsr", "ret", "athrow",
        "swap", "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1", "dup2_x2", "swap"
    };

    public static List<BasicBlock> analyze(byte[] bytecode, List<ExceptionHandler> handlers) {
        List<BasicBlock> blocks = new ArrayList<>();
        Map<Integer, BasicBlock> blockMap = new HashMap<>();
        
        Set<Integer> branchTargets = new HashSet<>();
        for (ExceptionHandler h : handlers) {
            branchTargets.add(h.handlerPc);
        }
        
        for (int pc = 0; pc < bytecode.length; ) {
            int opcode = bytecode[pc] & 0xFF;
            String name = opcode < OPCODES.length && OPCODES[opcode] != null ? OPCODES[opcode] : "unknown";
            
            if ("goto".equals(name) || "goto_w".equals(name)) {
                if (pc + 3 <= bytecode.length) {
                    int offset = ((bytecode[pc + 1] & 0xFF) << 8) | (bytecode[pc + 2] & 0xFF);
                    if ("goto_w".equals(name)) {
                        int target = pc + 4 + offset;
                        branchTargets.add(target);
                    } else {
                        int target = pc + 3 + offset;
                        branchTargets.add(target);
                    }
                }
            } else if (name.startsWith("if")) {
                if (pc + 3 <= bytecode.length) {
                    int offset = ((bytecode[pc + 1] & 0xFF) << 8) | (bytecode[pc + 2] & 0xFF);
                    offset = offset & 0xFFFF;
                    int target = pc + 3 + offset;
                    branchTargets.add(target);
                }
            } else if ("jsr".equals(name)) {
                if (pc + 3 <= bytecode.length) {
                    int offset = ((bytecode[pc + 1] & 0xFF) << 8) | (bytecode[pc + 2] & 0xFF);
                    int target = pc + 3 + offset;
                    branchTargets.add(target);
                }
            } else if (name.startsWith("invokestatic") || name.startsWith("invokevirtual")) {
                if (pc + 3 <= bytecode.length) {
                    branchTargets.add(pc + 3);
                    pc += 2;
                }
            }
            pc++;
        }
        
        for (int pc = 0; pc < bytecode.length; pc++) {
            if (isBlockStart(pc, bytecode, branchTargets, handlers)) {
                final int curr = pc;
                BasicBlock block = new BasicBlock(curr, handlers.stream().anyMatch(h -> h.startPc <= curr && curr < h.endPc));
                blockMap.put(curr, block);
                blocks.add(block);
            }
        }
        
        return blocks;
    }

    private static boolean isBlockStart(int pc, byte[] bytecode, Set<Integer> branchTargets, List<ExceptionHandler> handlers) {
        if (pc >= bytecode.length) return false;
        if (pc == 0) return true;
        return branchTargets.contains(pc) || handlers.stream().anyMatch(h -> h.handlerPc == pc);
    }

    private static Set<Integer> branchTargets = new HashSet<>();

    public static String disassemble(byte[] bytecode) {
        StringBuilder sb = new StringBuilder();
        for (int pc = 0; pc < bytecode.length; ) {
            int opcode = bytecode[pc] & 0xFF;
            String name = opcode < OPCODES.length ? OPCODES[opcode] : "unknown_" + opcode;
            if (name == null) name = "unknown_" + opcode;
            
            StringBuilder line = new StringBuilder();
            line.append(String.format("%04d: %-12s", pc, name));
            
            if (name.equals("bip")) {
                if (pc + 2 <= bytecode.length) {
                    int val = bytecode[pc + 1];
                    line.append(Integer.toString(val));
                    pc += 2;
                }
            } else if (name.equals("sip")) {
                if (pc + 3 <= bytecode.length) {
                    int val = ((bytecode[pc + 1] & 0xFF) << 8) | (bytecode[pc + 2] & 0xFF);
                    line.append(Integer.toString(val));
                    pc += 3;
                }
            } else if (name.startsWith("goto") || name.startsWith("if")) {
                if (pc + 3 <= bytecode.length) {
                    int offset = ((bytecode[pc] & 0xFF) << 8) | (bytecode[pc + 1] & 0xFF);
                    if (name.startsWith("if")) offset = ~(offset & 0xFFFF);
                    else {
                        offset = ~(offset & 0xFFFF);
                    }
                    int target = pc + 3 + offset;
                    line.append("PC:").append(target);
                    pc += 3;
                }
            } else if (name.equals("invokespecial") || name.equals("invokestatic") || 
                       name.equals("invokevirtual") || name.equals("invokeinterface")) {
                pc += 3;
            } else if (name.equals("return") || name.equals("ireturn") || 
                       name.equals("lreturn") || name.equals("areturn")) {
                pc += 1;
            } else {
                pc += 1;
            }
            
            sb.append(line).append('\n');
        }
        return sb.toString();
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