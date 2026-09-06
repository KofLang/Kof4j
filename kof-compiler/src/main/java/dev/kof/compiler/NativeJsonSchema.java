package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): schemas JSON nativos (JSN002). Coleta as tabelas
 * de schema por classe (nome+offset+tipo de cada campo, inclusive herdados)
 * e emite os .quad no .data. Extraído verbatim de NativeBackend; estado do
 * backend acessado via campo nb (padrão CompilerClassLowering); jsonSchemas
 * é estado próprio (coleta antes de emitStringData).
 */
final class NativeJsonSchema {

    private final NativeBackend nb;

    private final java.util.List<JsonSchemaTable> jsonSchemas = new java.util.ArrayList<>();

    NativeJsonSchema(NativeBackend nb) { this.nb = nb; }

    /**
     * JSN002: coleta as tabelas de schema JSON por classe (nome+offset+
     * tipo de cada campo, inclusive herdados via ClassLayout). Campos de
     * tipos nao suportados (FP, List, Map) sao omitidos — o gate no
     * CompilerDriver diagnostica essas classes antes delas chegarem aqui.
     * Os nomes dos campos sao internados AQUI (antes de emitStringData).
     */
    record JsonSchemaEntry(String tokenCstr, long offset, long typeCode, String className,
                                    String auxCstr) {}
    record JsonSchemaTable(String tableLabel, String classCstr, long totalSize,
                                   java.util.List<JsonSchemaEntry> entries) {}


    Long jsonFieldTypeCode(Type t) {
        if (t instanceof Type.PrimitiveType pt) {
            return switch (Type.canonicalPrimitiveName(pt.name())) {
                case "int", "char", "byte", "short" -> 1L;
                case "long" -> 2L;
                case "bool" -> 3L;
                default -> null; // float/double: gap FP (JSN001)
            };
        }
        if (BuiltinTypes.isString(t)) return 4L;
        if (t instanceof Type.ClassType ct) {
            String simple = ct.name();
            for (IRClass c : nb.allClassesMap.values()) {
                if (c.name().equals(simple) || c.name().endsWith("/" + simple)) return 5L;
            }
        }
        return null;
    }

    void collectJsonSchemas() {
        jsonSchemas.clear();
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.fields().isEmpty()) continue;
            ClassLayout layout = nb.getLayout(clazz);
            java.util.List<JsonSchemaEntry> entries = new java.util.ArrayList<>();
            boolean any = false;
            boolean allSupported = true;
            for (FieldLayout f : layout.fields()) {
                Long code = jsonFieldTypeCode(f.type());
                if (code == null) { allSupported = false; continue; }
                if (f.type() instanceof Type.ClassType ct) {
                    // campo aninhado: so se a classe alvo tambem tiver tabela
                    boolean has = false;
                    for (IRClass c : nb.allClassesMap.values()) {
                        if (c.name().equals(ct.name()) || c.name().endsWith("/" + ct.name())) {
                            has = !nb.getLayout(c).fields().isEmpty();
                            break;
                        }
                    }
                    if (!has) { allSupported = false; continue; }
                }
                // token '"nome":' serve encode (parte literal) e decode (busca)
                String tokLabel = nb.internString("\"" + f.name() + "\":");
                String auxCstr = null;
                if (code == 5L && f.type() instanceof Type.ClassType ct) {
                    String cn = ct.packageName().isEmpty() ? ct.name()
                            : ct.packageName() + "." + ct.name();
                    auxCstr = nb.internString(cn);
                }
                entries.add(new JsonSchemaEntry(tokLabel, f.offset(), code, f.name(), auxCstr));
                any = true;
            }
            if (!any || !allSupported) continue;
            String tableLabel = ".Lsch_" + nb.sanitizeName(clazz.name());
            String classCstr = nb.internString(clazz.name());
            jsonSchemas.add(new JsonSchemaTable(tableLabel, classCstr,
                    layout.totalSize(), java.util.List.copyOf(entries)));
        }
    }

    /** Emite as tabelas de schema + registro + finder (apos emitStringData). */
    void emitJsonSchemaData(StringBuilder sb) {
        if (jsonSchemas.isEmpty()) return;
        sb.append(".section .data\n");
        for (JsonSchemaTable t : jsonSchemas) {
            sb.append(t.tableLabel()).append(":\n");
            sb.append("    .quad ").append(t.totalSize()).append("\n");
            sb.append("    .quad ").append(t.entries().size()).append("\n");
            for (JsonSchemaEntry e : t.entries()) {
                sb.append("    .quad ").append(e.tokenCstr()).append("\n");
                sb.append("    .quad ").append(e.offset()).append("\n");
                sb.append("    .quad ").append(e.typeCode()).append("\n");
                sb.append("    .quad ").append(e.auxCstr() == null ? 0 : e.auxCstr()).append("\n");
            }
        }
        sb.append(".Lsch_registry:\n");
        for (JsonSchemaTable t : jsonSchemas) {
            sb.append("    .quad ").append(t.classCstr()).append("\n");
            sb.append("    .quad ").append(t.tableLabel()).append("\n");
        }
        sb.append("    .quad 0\n");
        sb.append("""
            .section .text
            .globl kof_json_schema_find
            .type kof_json_schema_find, @function
            kof_json_schema_find:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx             # nome C-string
                leaq .Lsch_registry(%rip), %r9
            .Lschf_loop:
                movq (%r9), %rax            # name cstr da entrada
                testq %rax, %rax
                jz .Lschf_notfound
                xorq %rcx, %rcx
            .Lschf_cmp:
                movzbl (%rbx,%rcx), %edx
                movzbl (%rax,%rcx), %esi
                cmpl %esi, %edx
                jne .Lschf_next
                testl %edx, %edx
                jz .Lschf_found
                incq %rcx
                jmp .Lschf_cmp
            .Lschf_next:
                addq $16, %r9
                jmp .Lschf_loop
            .Lschf_found:
                movq 8(%r9), %rax           # table ptr
                jmp .Lschf_exit
            .Lschf_notfound:
                xorl %eax, %eax
            .Lschf_exit:
                popq %r12
                popq %rbx
                ret
            """);
    }

    /** Tabela de schema para uma classe (ou null se ausente), pelo nome. */
    String schemaLabelFor(String className) {
        for (JsonSchemaTable t : jsonSchemas) {
            if (t.classCstr().equals(className)) return t.tableLabel();
        }
        return null;
    }
}
