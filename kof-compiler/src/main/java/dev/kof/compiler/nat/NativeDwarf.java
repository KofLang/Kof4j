package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frente 4 (Debugger nativo), fatia 1: CU DWARF propria do Kof no ELF x86.
 *
 * O `.file`/`.loc` ja gera a line table (.debug_line, fase 5/bug82); aqui
 * acrescenta-se .debug_info/.debug_abbrev com DW_TAG_compile_unit + um
 * DW_TAG_subprogram por funcao Kof (DW_AT_name = nome-fonte da funcao,
 * low_pc = rotulo mangled, high_pc = offset ate o rotulo .Lfe_ emitido no
 * fim de cada funcao, decl_file/decl_line + frame_base DW_OP_reg6(rbp)).
 * E o DIE que gdb/LLDB/DAP usam p/ "info functions" e breakpoints por nome
 * sem mangle manual.
 *
 * Fatia 2 (17/09): DW_TAG_formal_parameter/DW_TAG_variable filhos de cada
 * subprogram com DW_AT_location = DW_OP_fbreg no MESMO deslocamento que o
 * prologue usa (`-(index+1)*8(%rbp)` — layout do NativeMethodEmitter); o
 * debugger le assim os args/locals sem entender o ABI do Kof.
 *
 * Fatia 3 (17/09): DW_AT_type nos params/locals/retorno apontando p/
 * DW_TAG_base_type filhos do CU (Int=signed4, Long=signed8, Double=float8,
 * Float=float4, Bool=boolean1, Char=unsigned2, Void=size0 sem encoding,
 * demais=unsigned8 opaco). Validado no proto typed2.s + gdb real:
 * `ptype fn` -> `Int (Int, Double)`; no ELF do Kof, gdb passa a mostrar
 * `y = (Int) 20` em vez de generic pointer.
 *
 * Medido (prototipos 17/09, as/ld + readelf/objdump/gdb): ao o arquivo .s
 * declarar .debug_info/.debug_abbrev explicitamente, o GAS SUPRIME a CU
 * automatica dele (so a do .loc/.line permanece) — a CU Kof fica univoca no
 * ELF. Codigos validados contra os BYTES do a.o do GCC (nunca chutados):
 * TAG compile_unit=0x11 subprogram=0x2e formal_parameter=0x05
 * variable=0x34 base_type=0x24; AT name=0x03 location=0x02 low_pc=0x11
 * high_pc=0x12 language=0x13 stmt_list=0x10 decl_file=0x3a decl_line=0x3b
 * frame_base=0x40 type=0x49 byte_size=0x0b encoding=0x3e; FORM string=0x08
 * addr=0x01 data1=0x0b data4=0x06 udata=0x0f exprloc=0x18 ref4=0x13;
 * OP reg6=0x56 fbreg=0x91; ATE boolean=0x02 float=0x04 signed=0x05
 * unsigned=0x07. DWARF v4: high_pc data4 e OFFSET (na v3 era endereco e o
 * gdb descartava a CU), header = version(2)->abbrev_off(4)->addr_size(1).
 */
final class NativeDwarf {

    /** X7-2 fatia 2: o frame_base depende da ABI do alvo (rbp/x27/x29). O
     *  resto do DIE (tags/attr/expr fbreg) e identico nos tres — so o
     *  registrador da moldura muda, e os offsets de slot vem do chamador
     *  (x86 `slotOffset`, cross `crossLocalOffRiscv`). */
    enum Arch { X86_64, RISCV64, AARCH64 }

    Arch arch = Arch.X86_64;

    /** Expressao DWARF do frame_base por ABI: x86 DW_OP_reg6(rbp); riscv64
     *  DW_OP_regx x27 (s11 — regs >=16 exigem regx+ULEB); aarch64
     *  DW_OP_reg29 (fp=x29, single-byte 0x50+29). */
    byte[] frameBaseExpr() {
        return switch (arch) {
            case X86_64 -> new byte[]{0x56};
            case AARCH64 -> new byte[]{0x6D};
            case RISCV64 -> new byte[]{(byte) 0x90, 0x1b};
        };
    }

    record Local(String name, int offset, String kind) {}

    record Fn(String label, String kofName, int declLine, String retKind,
              List<Local> params, List<Local> locals) {}

    final List<Fn> fns = new ArrayList<>();

    /** Slots do prologue: Kof guarda todo local/arg `i` em -(i+1)*8(%rbp). */
    static int slotOffset(int index) {
        return -(index + 1) * 8;
    }

    /** The DWARF type kind for a Kof type (a scalar kind, or {@code Opaque} for
     *  String/classes/arrays — an 8-byte handle without a DW_TAG_structure for now). */
    static String dwarfKindOf(dev.kof.compiler.Type t) {
        if (t instanceof dev.kof.compiler.Type.PrimitiveType pt) {
            return switch (dev.kof.compiler.Type.canonicalPrimitiveName(pt.name())) {
                case "int" -> "Int";
                case "long" -> "Long";
                case "short" -> "Short";
                case "byte" -> "Byte";
                case "float" -> "Float";
                case "double" -> "Double";
                case "bool", "boolean" -> "Bool";
                case "char" -> "Char";
                case "void" -> "Void";
                default -> "Opaque";
            };
        }
        return "Opaque";
    }

    void add(String label, String kofName, int declLine, String retKind,
             List<Local> params, List<Local> locals) {
        fns.add(new Fn(label, kofName, declLine, retKind, params, locals));
    }

    /** name -> [byteSize, encoding| -1 p/ Void, voidFlag]. */
    private static int[] descOf(String kind) {
        switch (kind) {
            case "Int": return new int[]{4, 5, 0};
            case "Long": return new int[]{8, 5, 0};
            case "Short": return new int[]{2, 5, 0};
            case "Byte": return new int[]{1, 5, 0};
            case "Double": return new int[]{8, 4, 0};
            case "Float": return new int[]{4, 4, 0};
            case "Bool": return new int[]{1, 2, 0};
            case "Char": return new int[]{2, 7, 0};
            case "Void": return new int[]{0, 0, 1};
            default: return new int[]{8, 7, 0}; // String/classes/arrays: handle opaco
        }
    }

    void emit(StringBuilder sb, String sourceFile) {
        if (fns.isEmpty()) return;
        Map<String, Integer> typeIndex = new LinkedHashMap<>();
        for (Fn f : fns) {
            intern(typeIndex, f.retKind());
            for (Local l : f.params()) intern(typeIndex, l.kind());
            for (Local l : f.locals()) intern(typeIndex, l.kind());
        }
        sb.append("\n# --- DWARF Kof: DW_TAG_subprogram por funcao (frente 4) ---\n");
        sb.append("    .section .debug_abbrev\n");
        sb.append(".Lkof_abbrev:\n");
        // 1: compile_unit (com filhos). low_pc/high_pc: o gdb associa a CU ao
        // codigo do programa APENAS via ranges — sem elas a CU e ignorada no
        // "info locals" (medido 17/09, gdb 15 no ELF do DwarfProbe2).
        sb.append("    .byte 1\n    .byte 0x11\n    .byte 1\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x13, 0x0b\n    .byte 0x10, 0x06\n");
        sb.append("    .byte 0x11, 0x01\n    .byte 0x12, 0x06\n");
        sb.append("    .byte 0, 0\n");
        // 2: subprogram (com filhos: params/locals) + type do retorno
        sb.append("    .byte 2\n    .byte 0x2e\n    .byte 1\n");
        sb.append("    .byte 0x11, 0x01\n    .byte 0x12, 0x06\n    .byte 0x03, 0x08\n");
        sb.append("    .byte 0x3a, 0x0b\n    .byte 0x3b, 0x0f\n    .byte 0x40, 0x18\n");
        sb.append("    .byte 0x49, 0x13\n");
        sb.append("    .byte 0, 0\n");
        // 3: formal_parameter (sem filhos) + type
        sb.append("    .byte 3\n    .byte 0x05\n    .byte 0\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x02, 0x18\n    .byte 0x49, 0x13\n");
        sb.append("    .byte 0, 0\n");
        // 4: variable (sem filhos) + type
        sb.append("    .byte 4\n    .byte 0x34\n    .byte 0\n");
        sb.append("    .byte 0x03, 0x08\n    .byte 0x02, 0x18\n    .byte 0x49, 0x13\n");
        sb.append("    .byte 0, 0\n");
        // 5: base_type c/ encoding (primitivos numericos/bool/char/opacos)
        sb.append("    .byte 5\n    .byte 0x24\n    .byte 0\n");
        sb.append("    .byte 0x0b, 0x0b\n    .byte 0x3e, 0x0b\n    .byte 0x03, 0x08\n");
        sb.append("    .byte 0, 0\n");
        // 6: base_type Void (byte_size 0, SEM encoding)
        sb.append("    .byte 6\n    .byte 0x24\n    .byte 0\n");
        sb.append("    .byte 0x0b, 0x0b\n    .byte 0x03, 0x08\n");
        sb.append("    .byte 0, 0\n");
        sb.append("    .byte 0\n");
        sb.append("\n    .section .debug_info\n");
        sb.append(".Lkof_info:\n");
        sb.append("    .4byte .Lkof_info_end - .Lkof_info - 4\n");
        // v4: so a partir da DWARF4 DW_AT_high_pc FORM_data4 = OFFSET de
        // low_pc (na v3 o gdb interpretava como endereco absoluto -> a CU
        // inteira era descartada como "non-debugging", medido 17/09).
        // Header v4 = version(2) -> abbrev_offset(4) -> address_size(1)
        // (unit_type so existe na v5; ordem errada = CU ignorada).
        sb.append("    .2byte 4\n");
        sb.append("    .4byte .Lkof_abbrev\n");
        sb.append("    .byte 8\n");
        sb.append("    .byte 1\n");
        sb.append("    .asciz \"").append(escape(sourceFile)).append("\"\n");
        sb.append("    .byte 0x0c\n");
        sb.append("    .4byte 0\n");
        sb.append("    .quad ").append(fns.get(0).label()).append("\n");
        sb.append("    .4byte .Lfe_").append(fns.get(fns.size() - 1).label())
          .append(" - ").append(fns.get(0).label()).append("\n");
        for (Fn f : fns) {
            sb.append("    .byte 2\n");
            sb.append("    .quad ").append(f.label()).append("\n");
            sb.append("    .4byte .Lfe_").append(f.label()).append(" - ").append(f.label()).append("\n");
            sb.append("    .asciz \"").append(escape(f.kofName())).append("\"\n");
            sb.append("    .byte 1\n");
            sb.append("    .uleb128 ").append(Math.max(1, f.declLine())).append("\n");
            byte[] fb = frameBaseExpr();
            sb.append("    .byte ").append(fb.length).append("\n");
            for (byte b : fb) {
                sb.append("    .byte 0x").append(String.format("%02x", b)).append("\n");
            }
            sb.append("    .4byte .Lbty").append(typeIndex.get(f.retKind())).append(" - .Lkof_info\n");
            for (Local p : f.params()) {
                emitLoc(sb, 3, p, typeIndex);
            }
            for (Local v : f.locals()) {
                emitLoc(sb, 4, v, typeIndex);
            }
            sb.append("    .byte 0\n"); // null: fim dos filhos do subprogram
        }
        for (Map.Entry<String, Integer> e : typeIndex.entrySet()) {
            int[] d = descOf(e.getKey());
            sb.append(".Lbty").append(e.getValue()).append(":\n");
            if (d[2] == 1) {
                sb.append("    .byte 6\n");
                sb.append("    .byte ").append(d[0]).append("\n");
            } else {
                sb.append("    .byte 5\n");
                sb.append("    .byte ").append(d[0]).append("\n");
                sb.append("    .byte ").append(d[1]).append("\n");
            }
            sb.append("    .asciz \"").append(escape(e.getKey())).append("\"\n");
        }
        sb.append("    .byte 0\n"); // null: fim dos filhos do CU
        sb.append(".Lkof_info_end:\n");
    }

    private static void intern(Map<String, Integer> idx, String kind) {
        if (kind != null && !idx.containsKey(kind)) idx.put(kind, idx.size() + 1);
    }

    private static void emitLoc(StringBuilder sb, int abbrev, Local loc, Map<String, Integer> idx) {
        byte[] fbreg = sleb128(loc.offset());
        sb.append("    .byte ").append(abbrev).append("\n");
        sb.append("    .asciz \"").append(escape(loc.name())).append("\"\n");
        sb.append("    .byte ").append(1 + fbreg.length).append("\n");
        sb.append("    .byte 0x91");
        for (byte b : fbreg) {
            sb.append(", 0x").append(String.format("%02x", b));
        }
        sb.append("\n");
        sb.append("    .4byte .Lbty").append(idx.get(loc.kind())).append(" - .Lkof_info\n");
    }

    static byte[] sleb128(long value) {
        List<Byte> out = new ArrayList<>();
        boolean more = true;
        while (more) {
            byte b = (byte) (value & 0x7f);
            value >>= 7;
            boolean signBitSet = (b & 0x40) != 0;
            boolean negative = value < 0;
            if ((negative && signBitSet) || (!negative && !signBitSet)) {
                more = false;
            } else {
                b |= 0x80;
            }
            out.add(b);
        }
        byte[] r = new byte[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
