package dev.kof.compiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;


public class ClassLayout {

    public static final int HEADER_SIZE = 16;
    public static final int ALIGNMENT = 16;
    public static final int METHOD_TABLE_OFFSET = 8;

    private final String className;
    private final List<FieldLayout> fields;
    private final int totalSize;

    private ClassLayout(String className, List<FieldLayout> fields, int totalSize) {
        this.className = className;
        this.fields = Collections.unmodifiableList(fields);
        this.totalSize = totalSize;
    }

    public String className() { return className; }
    public List<FieldLayout> fields() { return fields; }
    public int totalSize() { return totalSize; }


    public int fieldOffset(String name) {
        for (FieldLayout f : fields) {
            if (f.name().equals(name)) return f.offset();
        }
        return -1;
    }


    public int fieldSize(String name) {
        for (FieldLayout f : fields) {
            if (f.name().equals(name)) return f.size();
        }
        return -1;
    }


    public static ClassLayout build(IRClass clazz) {
        List<FieldLayout> fields = new ArrayList<>();
        int offset = HEADER_SIZE;
        for (IRField field : clazz.fields()) {
            if ((field.accessFlags() & 0x0008) != 0) continue; // static: por-classe, não no objeto
            int size = FieldLayout.sizeOf(field.type());
            fields.add(new FieldLayout(field.name(), field.type(), offset, size));
            offset += size;
        }
        int totalSize = align(offset, ALIGNMENT);
        return new ClassLayout(clazz.name(), fields, totalSize);
    }


    public static ClassLayout buildWithSuper(IRClass clazz, Function<String, IRClass> superclassResolver) {
        List<FieldLayout> allFields = new ArrayList<>();
        int offset = HEADER_SIZE;

        List<String> hierarchy = new ArrayList<>();
        String current = clazz.superName();
        while (current != null && !current.isEmpty() && !"java/lang/Object".equals(current)) {
            hierarchy.add(0, current);
            IRClass superClazz = superclassResolver.apply(current);
            if (superClazz == null) break;
            current = superClazz.superName();
        }

        for (String superName : hierarchy) {
            IRClass superClazz = superclassResolver.apply(superName);
            if (superClazz == null) continue;
            for (IRField field : superClazz.fields()) {
                if ((field.accessFlags() & 0x0008) != 0) continue; // static
                int size = FieldLayout.sizeOf(field.type());
                allFields.add(new FieldLayout(field.name(), field.type(), offset, size));
                offset += size;
            }
        }

        for (IRField field : clazz.fields()) {
            if ((field.accessFlags() & 0x0008) != 0) continue; // static
            int size = FieldLayout.sizeOf(field.type());
            allFields.add(new FieldLayout(field.name(), field.type(), offset, size));
            offset += size;
        }

        int totalSize = align(offset, ALIGNMENT);
        return new ClassLayout(clazz.name(), allFields, totalSize);
    }


    public static ClassLayout build(String className, List<IRField> fieldList) {
        List<FieldLayout> fields = new ArrayList<>();
        int offset = HEADER_SIZE;
        for (IRField field : fieldList) {
            if ((field.accessFlags() & 0x0008) != 0) continue; // static
            int size = FieldLayout.sizeOf(field.type());
            fields.add(new FieldLayout(field.name(), field.type(), offset, size));
            offset += size;
        }
        int totalSize = align(offset, ALIGNMENT);
        return new ClassLayout(className, fields, totalSize);
    }


    public static int align(int value, int alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    public static void clearCache() {
    }
}
