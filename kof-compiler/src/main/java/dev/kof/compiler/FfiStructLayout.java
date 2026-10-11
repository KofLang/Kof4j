package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * FFI struct/array ABI — slice 3.7 (native asm), shared by the gate
 * ({@code CompilerPipeline.nativeExternBound}) and the emitters
 * ({@code nat.NativeFfiCall}). Consumes {@link AbiLayout} (3.8a) — the layout
 * engine is the single source of truth for offsets/classes.
 *
 * <p>A native struct parameter travels as a {@link Type.ClassType} tagged
 * {@code kof.ffi.struct} whose type arguments are the field types (primitive
 * only, no {@code String}/pointer — {@code FfiSignature.structFieldChars} is the
 * gate). The emitter reads the type arguments; no {@code CompilerDriver} is
 * needed at codegen time.
 *
 * <p>This slice binds the **register path** only (x86-64 SysV first): a struct
 * that would go to memory (SysV MEMORY, AAPCS64 BYREF, riscv BYREF, or that does
 * not fit the remaining registers) stays an honest {@code FFI001} — never a
 * partial/silent binding (R6).
 */
public final class FfiStructLayout {

    private FfiStructLayout() {}

    static final String PKG = "kof.ffi";
    static final String NAME = "struct";

    public static boolean isStructType(Type t) {
        return t instanceof Type.ClassType ct
                && PKG.equals(ct.packageName()) && NAME.equals(ct.name());
    }

    public static Type structType(List<Type> fieldTypes) {
        return new Type.ClassType(PKG, NAME, fieldTypes);
    }

    /** D6-2 (3.7): a scalar array `T[]`→C `ptr` parameter travels as a
     *  {@link Type.ClassType} tagged {@code kof.ffi}/{@code array} whose single
     *  type argument is the element. Current cut: element classes whose Kof
     *  slot width equals the C width — {@code Long} (`j`) / {@code Double}
     *  (`d`), both 8 B — copy with a plain memcpy. */
    public static Type arrayPtrType(char elem) {
        return new Type.ClassType(PKG, "array", List.of(primitiveOf(elem)));
    }

    public static boolean isArrayPtr(Type t) {
        return t instanceof Type.ClassType ct
                && PKG.equals(ct.packageName()) && "array".equals(ct.name());
    }

    /** D6-3 / D-R3-BUFFER (fatias A2 + B): `Buffer(U8)` INOUT crosses as a C
     *  `unsigned char*` pointing at the buffer payload (object offset 24). The
     *  marker carries no element (U8 only). Binds on the native targets — x86-64
     *  (A2) and cross riscv64/aarch64 (B, 29/09); Android/Script stay {@code FFI001}. */
    public static Type bufferPtrType() {
        return new Type.ClassType(PKG, "bufferptr", List.of());
    }

    public static boolean isBufferPtr(Type t) {
        return t instanceof Type.ClassType ct
                && PKG.equals(ct.packageName()) && "bufferptr".equals(ct.name());
    }

    /** M1 callbacks on Native (x86-64, INTEGER-only first slice): a
     *  function-typed {@code extern} parameter crosses as a C function POINTER
     *  to a generated trampoline that runs the Kof lambda (vtable[0] = invoke).
     *  The marker carries the callback ABI descriptor in its name
     *  ({@code cbptr_<desc>}). */
    public static Type cbPtrType(String descriptor) {
        return new Type.ClassType(PKG, "cbptr_" + descriptor, List.of());
    }

    public static boolean isCbPtr(Type t) {
        return t instanceof Type.ClassType ct
                && PKG.equals(ct.packageName()) && ct.name() != null && ct.name().startsWith("cbptr_");
    }

    /** Callback ABI descriptor carried by a cb-ptr marker, or null. */
    public static String cbPtrDesc(Type t) {
        if (!isCbPtr(t)) return null;
        return ((Type.ClassType) t).name().substring("cbptr_".length());
    }

    /** M1 callbacks on Native x86-64: o marker cb-ptr para um parâmetro `extern`
     *  de tipo-função INTEGER-only (dado o tipo textual do parâmetro), ou
     *  {@code null} (param não-callback / face Float/Double/String → mantém o
     *  comportamento anterior). */
    public static Type cbMarkerOrNull(String paramType) {
        String desc = FfiSignature.nativeCallbackDesc(FfiSignature.callbackDescriptor(paramType));
        return desc != null ? cbPtrType(desc) : null;
    }

    /** Element char of an array-ptr marker, or null when {@code t} is not one. */
    public static Character arrayPtrElem(Type t) {
        if (!isArrayPtr(t)) return null;
        return FfiSignature.charOfType(((Type.ClassType) t).typeArguments().get(0));
    }

    static Type primitiveOf(char ch) {
        return switch (ch) {
            case 'j' -> Type.PrimitiveType.LONG;
            case 'f' -> Type.PrimitiveType.FLOAT;
            case 'd' -> Type.PrimitiveType.DOUBLE;
            case 'b' -> Type.PrimitiveType.BOOL;
            case 'S' -> BuiltinTypes.STRING;   // D-MEM-FFI-CROSS-FULL face 2
            default -> Type.PrimitiveType.INT;
        };
    }

    /** Struct Type from the field chars of {@code FfiSignature.structFieldChars}. */
    static Type structTypeOfChars(String chars) {
        List<Type> ts = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) ts.add(primitiveOf(chars.charAt(i)));
        return structType(ts);
    }

    public static AbiLayout.Abi abiFor(Target t) {
        return switch (t) {
            case NATIVE_RISCV64 -> AbiLayout.Abi.RISCV64;
            case NATIVE_AARCH64 -> AbiLayout.Abi.AAPCS64;
            default -> AbiLayout.Abi.SYSV_X86_64;
        };
    }

    public static AbiLayout.Scalar scalarOf(Type t) {
        Character ch = FfiSignature.charOfType(t);
        if (ch == null) throw new IllegalArgumentException("non-scalar struct field: " + t);
        return switch (ch) {
            case 'j' -> AbiLayout.Scalar.LONG;
            case 'f' -> AbiLayout.Scalar.FLOAT;
            case 'd' -> AbiLayout.Scalar.DOUBLE;
            case 'b' -> AbiLayout.Scalar.BOOL;
            default -> AbiLayout.Scalar.INT;
        };
    }

    public static AbiLayout.Layout layout(AbiLayout.Abi abi, Type structType) {
        List<AbiLayout.Field> fs = new ArrayList<>();
        List<Type> ts = ((Type.ClassType) structType).typeArguments();
        for (int i = 0; i < ts.size(); i++) {
            fs.add(new AbiLayout.Field("f" + i, scalarOf(ts.get(i))));
        }
        return AbiLayout.of(abi, fs);
    }

    /** Field with its Kof 8-byte slot index and its C offset (from AbiLayout). */
    public record FieldInfo(int kofSlot, int cOffset, AbiLayout.Scalar scalar) {}

    public static List<FieldInfo> fields(Type structType) {
        AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, structType);
        List<Type> ts = ((Type.ClassType) structType).typeArguments();
        List<FieldInfo> out = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            out.add(new FieldInfo(i, l.offsets()[i], scalarOf(ts.get(i))));
        }
        return out;
    }

    // ── x86-64 SysV bindability (register path only) ─────────────────────
    //
    // M1: um eightbyte SSE empacotado (mais de um campo float/double) passou a
    // ser emitido bitwise (shift/or em %rax → movq %xmm) — a forma x86-64 da
    // face HFA (VF: `record VF(Float, Float)`). O corte é o MESMO do cross
    // (`crossHomogeneousFloat`, ≤ 2 campos): um struct homogêneo-flutuante com
    // > 2 campos (HFA>2) fica FFI001 honesto em x86-64 também, para manter
    // x86-64 e cross alinhados (no cross as duas ABIs divergem em HFA>2).

    /** A struct whose SSE eightbyte carries more than one field needs packing
     *  beyond the M1 homogeneous-float form — honest gap. */
    private static boolean sseEightbytesAreSingleField(Type structType) {
        List<FieldInfo> fs = fields(structType);
        AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, structType);
        for (int e = 0; e < l.classes().size(); e++) {
            if (l.classes().get(e) != AbiLayout.ArgClass.SSE) continue;
            int lo = e * 8;
            int count = 0;
            for (FieldInfo f : fs) {
                if (f.cOffset() < lo + 8 && f.cOffset() + f.scalar().size > lo) count++;
            }
            if (count != 1) return false;
        }
        return true;
    }

    /** True when the struct's SSE eightbytes are emittable on the x86-64 register
     *  path: each carries a SINGLE field, or the struct is the homogeneous-float
     *  ≤ 2 form (the x86-64 mirror of the cross HFA face — M1, VF). HFA>2 stays
     *  FFI001 (cross-aligned); a mixed integer+float SSE eightbyte stays FFI001. */
    private static boolean x86SseBindable(Type structType) {
        return crossHomogeneousFloat(structType) || sseEightbytesAreSingleField(structType);
    }

    /** True when a single struct is bindable as an x86-64 RETURN value in the
     *  register path (≤ 16 B, no MEMORY, emittable SSE eightbytes). The sret
     *  path (&gt; 16 B) is a later slice. */
    public static boolean x86RegisterOnly(Type structType) {
        AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, structType);
        if (l.byMemory() || l.size() > 16) return false;
        return x86SseBindable(structType);
    }

    /** True when a single struct is bindable as an x86-64 sret RETURN: larger
     *  than 16 B (SysV MEMORY) — the caller passes a hidden pointer in `rdi`
     *  and the callee fills it (D6-4). */
    public static boolean x86SretReturn(Type structType) {
        return layout(AbiLayout.Abi.SYSV_X86_64, structType).byMemory();
    }

    /** True when a single struct is bindable as a cross (riscv64/aarch64)
     *  RETURN value in the register path. First cut (3.7 fatia 3): all fields
     *  INTEGER-class, ≤ 16 B — the words arrive in `a0`/`a1` (x0/x1 under
     *  AAPCS64, same text). Floats/HFA and the byref path (&gt; 16 B) stay an
     *  honest FFI001 (R6). */
    public static boolean crossIntRegisterOnly(Target t, Type structType) {
        AbiLayout.Layout l = layout(abiFor(t), structType);
        if (l.byMemory() || l.size() > 16 || l.classes().isEmpty()) return false;
        for (AbiLayout.ArgClass c : l.classes()) {
            if (c != AbiLayout.ArgClass.INTEGER) return false;
        }
        return true;
    }

    /**
     * D-MEMORY-SAFETY M1 unidade-1 (06/10): struct HOMOGÊNEO-FLUTUANTE de ≤ 2
     * campos ≤ 16 B — binda no register path FP dos dois cross. As duas ABIs
     * concordam no corte medido ({@code AbiLayoutTest} golden): LP6D achata
     * ≤ 2 campos em SSE por campo ({@code [SSE]} / {@code [SSE,SSE]}); AAPCS64
     * é HFA n ≤ 4 e entrega campo-a-campo em v0..v3 — o MESMO ordinal por
     * campo. Misto (float+int) diverge (achado vs eightword INTEGER único) e
     * 3–4 campos estouram os 16 B (BYREF — face própria) — ambos ficam em
     * FFI001 honesto (R6).
     */
    public static boolean crossHomogeneousFloat(Type structType) {
        List<FieldInfo> fs = fields(structType);
        if (fs.isEmpty() || fs.size() > 2) return false;
        int size = 0;
        for (FieldInfo f : fs) {
            if (f.scalar() != AbiLayout.Scalar.DOUBLE && f.scalar() != AbiLayout.Scalar.FLOAT) {
                return false;
            }
            size += f.scalar() == AbiLayout.Scalar.FLOAT ? 4 : 8;
        }
        return size <= 16;
    }

    /** Float field of a {@link #crossHomogeneousFloat} struct → its scalar char
     *  ({@code 'f'}/{@code 'd'}) at field index {@code fi}. */
    public static char crossHfaFieldChar(Type structType, int fi) {
        return fields(structType).get(fi).scalar() == AbiLayout.Scalar.FLOAT ? 'f' : 'd';
    }

    /** Emit the load of HFA field {@code fi} (float/double bits) from the Kof
     *  object ({@code 16 + 8·slot}) into {@code dst} — the soft-float-safe form
     *  (l.wu/l.d of the raw slot; NEVER an FP load of the object memory). */
    public static void emitRiscvHfaFieldLoad(StringBuilder sb, Type structType, int fi,
                                             String base, String dst) {
        FieldInfo f = fields(structType).get(fi);
        int off = 16 + 8 * f.kofSlot();
        boolean flt = f.scalar() == AbiLayout.Scalar.FLOAT;
        // O slot Kof guarda os bits do Float nos 32 BAIXOS (mesma forma do
        // caminho escalar: `ld t2, slot` → `fmv.w.x fa{n}, t2`); Double = 8 B.
        // Leitura INTEGER (l.w/l.d) — nunca load FP do objeto (soft-float-safe).
        sb.append(flt ? "    lw " : "    ld ").append(dst).append(", ")
          .append(off).append("(").append(base).append(")\n");
    }

    /** True when a single struct is bindable as a cross (riscv64/aarch64) RETURN
     *  in the **memory path** (sret) — larger than 16 B, so the C writes it
     *  through the ABI's indirect-result pointer. Unlike x86-64 sret (a hidden
     *  INTEGER argument in {@code rdi}), the two cross ABIs diverge and the
     *  emitter must branch on the target: RISC-V LP64 passes the pointer in
     *  {@code a0} (first real arg in {@code a1}), AAPCS64 in {@code x8} (first
     *  real arg still {@code x0}). Measured 30/09 with cross-gcc. */
    public static boolean crossMemoryReturn(Target t, Type structType) {
        return crossByMemory(t, structType);
    }

    /** True when a cross struct travels through memory (BYREF) — as a by-value
     *  PARAMETER passed by reference or as a RETURN via the sret pointer. Fields
     *  may be mixed; the emitter copies raw bytes against the C layout. Measured
     *  30/09 with cross-gcc: both riscv64 and aarch64 pass a &gt; 16 B struct
     *  param as a pointer in `a0`/`x0`. */
    public static boolean crossByMemory(Target t, Type structType) {
        return layout(abiFor(t), structType).byMemory();
    }

    /** Number of eightbyte words a cross struct occupies (INTEGER-only, from
     *  {@code crossIntRegisterOnly}). Integer fields are ABI-independent in
     *  size/offset, so the SysV layout is reused for the word count. */
    public static int crossWords(Type structType) {
        return layout(AbiLayout.Abi.RISCV64, structType).classes().size();
    }

    /** True when the whole cross (riscv64/aarch64) parameter list is bindable.
     *  A struct must be INTEGER-only (≤ 16 B) and fit entirely in the integer
     *  registers — unlike scalars, which may spill (the shim handles it).
     *  Simulates LP64/AAPCS64 register counting in formal order. */
    public static boolean crossBindable(List<Type> paramTypes) {
        return crossBindable(paramTypes, 0);
    }

    /** As {@link #crossBindable(List)} but {@code intReserved} INTEGER registers
     *  are already consumed — 1 for the RISC-V LP64 sret pointer (memory-path
     *  return, {@link #crossMemoryReturn}); AAPCS64 uses x8 but reserving
     *  conservatively keeps the single shared gate honest (never over-binds). */
    public static boolean crossBindable(List<Type> paramTypes, int intReserved) {
        int nInt = intReserved, nFlt = 0;
        for (Type t : paramTypes) {
            if (isBufferPtr(t)) {
                // #651 fatia B: Buffer(U8)→ptr é um ponteiro INTEGER (um ordinal),
                // como o array-ptr do x86 — LP64/AAPCS64.
                if (nInt >= 8) return false;
                nInt++;
                continue;
            }
            if (isArrayPtr(t)) {
                // D-MEM-FFI-CROSS-FULL: array escalar `T[]`→ptr é um ponteiro
                // INTEGER (um ordinal) — mesma forma do buffer-ptr no LP64/AAPCS64.
                if (nInt >= 8) return false;
                nInt++;
                continue;
            }
            if (isStructType(t)) {
                // D-MEM-FFI-CROSS-FULL face 3 estendida: struct > 16 B (BYREF)
                // viaja como UM ponteiro INTEGER (medido 30/09: riscv64 e aarch64
                // passam ambos o param por referência em `a0`/`x0`).
                AbiLayout.Layout l = layout(AbiLayout.Abi.RISCV64, t);
                if (l.byMemory()) {
                    if (nInt >= 8) return false;
                    nInt++;
                    continue;
                }
                if (crossHomogeneousFloat(t)) {
                    // D-MEMORY-SAFETY M1 unidade-1 (06/10): cada campo flutuante
                    // = um ordinal FP próprio (LP64D achata ≤ 2 campos em
                    // [SSE(,SSE)]; AAPCS64 HFA n ≤ 4 entrega campo-a-campo em
                    // v0..v3). Medido no golden do AbiLayoutTest; o 3o/4o campo
                    // duplo estouraria os 16 B (BYREF, face própria).
                    int nf = fields(t).size();
                    if (nFlt + nf > 8) return false;
                    nFlt += nf;
                    continue;
                }
                if (l.size() > 16 || l.classes().isEmpty()) return false;
                for (AbiLayout.ArgClass c : l.classes()) {
                    if (c != AbiLayout.ArgClass.INTEGER) return false;
                    if (nInt >= 8) return false;
                    nInt++;
                }
            } else {
                Character ch = FfiSignature.charOfType(t);
                if (ch == null) return false;
                if (ch == 'f' || ch == 'd') nFlt++; else nInt++;
            }
        }
        return true;
    }

    /** True when the whole parameter list is bindable on x86-64 (scalars may
     *  spill; structs must fit entirely in registers — each eightbyte is
     *  INTEGER or an emittable SSE, incl. the packed homogeneous-float form).
     *  Simulates SysV register counting in formal order. */
    public static boolean x86Bindable(List<Type> paramTypes) {
        return x86Bindable(paramTypes, 0);
    }

    /** As {@link #x86Bindable(List)} but {@code intReserved} INTEGER registers
     *  are already taken (1 for the sret hidden pointer). */
    public static boolean x86Bindable(List<Type> paramTypes, int intReserved) {
        int nInt = intReserved, nFlt = 0;
        for (Type t : paramTypes) {
            if (isArrayPtr(t)) {
                // 3.7 D6-2: array→ptr é um ponteiro INTEGER (um ordinal).
                if (nInt >= 6) return false;
                nInt++;
                continue;
            }
            if (isBufferPtr(t)) {
                // D6-3/A2: Buffer(U8)→ptr é um ponteiro INTEGER (como um escalar,
                // pode derramar para a pilha quando passam de 6 ordinais).
                nInt++;
                continue;
            }
            if (isCbPtr(t)) {
                // M1 callbacks (x86-64): ponteiro de função C = um INTEGER.
                nInt++;
                continue;
            }
            if (isStructType(t)) {
                if (!x86SseBindable(t)) return false;
                AbiLayout.Layout l = layout(AbiLayout.Abi.SYSV_X86_64, t);
                if (l.byMemory()) return false;
                for (AbiLayout.ArgClass c : l.classes()) {
                    if (c == AbiLayout.ArgClass.SSE) {
                        if (nFlt >= 8) return false;
                        nFlt++;
                    } else {
                        if (nInt >= 6) return false;
                        nInt++;
                    }
                }
            } else {
                Character ch = FfiSignature.charOfType(t);
                if (ch == null) return false;
                if (ch == 'f' || ch == 'd') nFlt++; else nInt++;
            }
        }
        return true;
    }

    // ── x86-64 emission ──────────────────────────────────────────────────

    /**
     * Builds eightbyte {@code e} of the struct (base register holds the Kof
     * record object pointer) into {@code dst} — an integer register for
     * INTEGER/SSE-upgraded classes, or an xmm register for SSE.
     *
     * <p>Kof field slots are 8 bytes wide ({@code FieldLayout.sizeOf} = 8) in
     * declaration order starting at object offset 16; the C field is written at
     * its natural size. Integer eightbytes are assembled with shift/or (a
     * struct field never crosses an eightbyte boundary under natural
     * alignment).
     */
    public static void emitX86Eightbyte(StringBuilder sb, Type structType, int e,
                                        String base, String dst, boolean sse) {
        List<FieldInfo> fs = fields(structType);
        int lo = e * 8;
        if (sse) {
            List<FieldInfo> in = new ArrayList<>();
            for (FieldInfo f : fs) {
                if (f.cOffset() < lo + 8 && f.cOffset() + f.scalar().size > lo) in.add(f);
            }
            if (in.size() == 1) {
                FieldInfo f = in.get(0);
                int off = 16 + 8 * f.kofSlot();
                if (f.scalar().size == 8) {
                    sb.append("    movq ").append(off).append("(").append(base).append("), %rax\n");
                    sb.append("    movq %rax, ").append(dst).append("\n");
                } else {
                    sb.append("    movd ").append(off).append("(").append(base).append("), ").append(dst).append("\n");
                }
                return;
            }
            // Packed homogeneous-float eightbyte: assemble the raw float/double
            // bits in %rax (shift/or, as the INTEGER path) and move them into the
            // xmm register — SSE is bitwise, so no FP conversion happens.
            sb.append("    xorq %rax, %rax\n");
            for (FieldInfo f : in) {
                int off = 16 + 8 * f.kofSlot();
                int shift = (f.cOffset() - lo) * 8;
                sb.append("    movl ").append(off).append("(").append(base).append("), %r11d\n");
                if (shift > 0) sb.append("    shlq $").append(shift).append(", %r11\n");
                sb.append("    orq %r11, %rax\n");
            }
            sb.append("    movq %rax, ").append(dst).append("\n");
            return;
        }
        sb.append("    xorq %rax, %rax\n");
        for (FieldInfo f : fs) {
            if (f.cOffset() >= lo + 8 || f.cOffset() + f.scalar().size <= lo) continue;
            int off = 16 + 8 * f.kofSlot();
            int shift = (f.cOffset() - lo) * 8;
            switch (f.scalar().size) {
                case 1 -> sb.append("    movzbl ").append(off).append("(").append(base).append("), %r11d\n");
                case 2 -> sb.append("    movzwl ").append(off).append("(").append(base).append("), %r11d\n");
                case 4 -> sb.append("    movl ").append(off).append("(").append(base).append("), %r11d\n");
                default -> sb.append("    movq ").append(off).append("(").append(base).append("), %r11\n");
            }
            if (shift > 0) sb.append("    shlq $").append(shift).append(", %r11\n");
            sb.append("    orq %r11, %rax\n");
        }
        sb.append("    movq %rax, ").append(dst).append("\n");
    }

    // ── riscv64/aarch64 emission ─────────────────────────────────────────

    /**
     * Builds INTEGER eightbyte {@code e} of the struct (base register holds the
     * Kof record object pointer) into {@code dst}, using RISC-V text that the
     * aarch64 translator normalizes (same one-text-two-archs rule as the rest
     * of the cross shim). Each field is zero-extended to its natural width,
     * shifted to its position and OR'd — a field never crosses an eightbyte
     * boundary under natural alignment, so OR is safe. {@code scratch} must
     * differ from {@code base} and {@code dst}.
     */
    public static void emitRiscvIntEightbyte(StringBuilder sb, Type structType, int e,
                                             String base, String dst, String scratch) {
        List<FieldInfo> fs = fields(structType);
        int lo = e * 8;
        sb.append("    li ").append(dst).append(", 0\n");
        for (FieldInfo f : fs) {
            if (f.cOffset() >= lo + 8 || f.cOffset() + f.scalar().size <= lo) continue;
            int off = 16 + 8 * f.kofSlot();
            int shift = (f.cOffset() - lo) * 8;
            switch (f.scalar().size) {
                case 1 -> sb.append("    lbu ").append(scratch).append(", ").append(off).append("(").append(base).append(")\n");
                case 2 -> sb.append("    lhu ").append(scratch).append(", ").append(off).append("(").append(base).append(")\n");
                case 4 -> sb.append("    lw ").append(scratch).append(", ").append(off).append("(").append(base).append(")\n")
                                .append("    slli ").append(scratch).append(", ").append(scratch).append(", 32\n")
                                .append("    srli ").append(scratch).append(", ").append(scratch).append(", 32\n");
                default -> sb.append("    ld ").append(scratch).append(", ").append(off).append("(").append(base).append(")\n");
            }
            if (shift > 0) {
                sb.append("    slli ").append(scratch).append(", ").append(scratch).append(", ").append(shift).append("\n");
            }
            sb.append("    or ").append(dst).append(", ").append(dst).append(", ").append(scratch).append("\n");
        }
    }
}
