package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.ClassLayout;
import dev.kof.compiler.FfiStructLayout;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.CompilerClassLowering;
import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofArrayLength;
import dev.kof.compiler.KofArrayLoad;
import dev.kof.compiler.KofArrayStore;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCatchStart;
import dev.kof.compiler.KofCheckCast;
import dev.kof.compiler.KofConditionalJump;
import dev.kof.compiler.KofDup;
import dev.kof.compiler.KofDup2;
import dev.kof.compiler.KofDupX1;
import dev.kof.compiler.KofDupX2;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofInstanceOf;
import dev.kof.compiler.KofJump;
import dev.kof.compiler.KofLabel;
import dev.kof.compiler.KofLoadField;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofNewArray;
import dev.kof.compiler.KofNewMultiArray;
import dev.kof.compiler.KofNewObject;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofPop;
import dev.kof.compiler.KofPop2;
import dev.kof.compiler.KofPutStatic;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreField;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.KofThrow;
import dev.kof.compiler.KofContinueLabel;
import dev.kof.compiler.KofStatementIf;
import dev.kof.compiler.KofExcUnlink;
import dev.kof.compiler.KofTryEnd;
import dev.kof.compiler.KofTryStart;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.KofUnaryOp;
import dev.kof.compiler.NativeRuntime;
import dev.kof.compiler.Type;

import java.util.List;

/**
 *  * Emissão cross riscv64 (parte 1): método, vtable, dispatch de ops,
 * Extraído verbatim de NativeBackend (FASE 3, REFACTOR-500); estado do
 * backend acessado via campo `nb` (padrão CompilerClassLowering).
 */
public final class NativeRiscvCrossEmit {

    /** §181: sequência para labels ÚNICOS de saturação (2+ casts por método). */
    private static final java.util.concurrent.atomic.AtomicLong SAT181_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    final NativeBackend nb;

    private final NativeRiscvCrossOps other;

    NativeRiscvCrossEmit(NativeBackend nb) { this.nb = nb; this.other = new NativeRiscvCrossOps(nb, this); }

    void emitCrossMethodRiscv(StringBuilder sb, IRClass clazz, IRMethod method, boolean joinMain) {
        // Mangle idêntico ao x86_64 (vtables referenciam esses símbolos).
        String mangled = NativeSymbolMangling.fnSymbol(clazz.name(), method, nb.allClassesMap);
        int maxSlot = method.localVariables().stream().mapToInt(IRLocalVariable::index).max().orElse(0);
        // §546: o call-site de um extern com `Buffer(U8)` INOUT guarda o OBJ de
        // cada buffer num slot de rascunho do frame (para o release reler sem
        // depender do bloco de args, que o C pode sobrescrever). Reserva o pior
        // caso dos calls do método ABAIXO dos locais (crossScratchOff). Args de
        // pilha (>8) NÃO precisam de rascunho — o emitCrossArgLoads os acessa
        // por offset direto.
        int maxFfiBuf = 0;
        for (IRBasicBlock prec : method.basicBlocks()) {
            for (KofOperation pop : prec.operations()) {
                if (pop instanceof KofCall pkc && NativeFfiCall.isExternCall(pkc)) {
                    int b = 0;
                    for (Type t : pkc.parameterTypes()) if (FfiStructLayout.isBufferPtr(t)) b++;
                    // D-MEM-FFI-CROSS-FULL face 3: um extern com retorno struct
                    // por memória (sret) reserva o slot 0 ao ponteiro do buffer C
                    // (os borrow buffers usam a partir do slot 1 — ver bufBase).
                    if (NativeFfiCallRiscv.usesMemStructReturn(nb, pkc)) b++;
                    maxFfiBuf = Math.max(maxFfiBuf, b);
                }
            }
        }
        int frameSize = Math.max((maxSlot + 1) * 8 + 16, 32) + maxFfiBuf * 8;
        frameSize = (frameSize + 15) & ~15;
        nb.crossFrameSize = frameSize;
        sb.append(".globl ").append(mangled).append("\n");
        sb.append(mangled).append(":\n");
        // Modelo idêntico ao x86_64: `sp` É a pilha de operandos (cresce p/
        // baixo); `s11` = frame pointer. Layout (alto→baixo):
        //   [s11+0]=old s11  [s11-8]=saved ra  [s11-16-idx*8]=local idx  [sp..]=operandos
        sb.append("    addi sp, sp, -16\n");
        sb.append("    sd s11, 0(sp)\n");
        sb.append("    sd ra, 8(sp)\n");
        sb.append("    addi s11, sp, 16\n");
        // §546: frames grandes estouram o imediato de 12 bits do `addi`; usa
        // li+sub (o tradutor aarch64 cobre ambos).
        if (frameSize <= 2032) {
            sb.append("    addi sp, sp, -").append(frameSize).append("\n");
        } else {
            sb.append("    li t0, ").append(frameSize).append("\n");
            sb.append("    sub sp, sp, t0\n");
        }
        // salva args de entrada (this + params) nos slots locais — ABI riscv:
        // a0=this/arg0, a1..a7 = demais args (até 8 registradores).
        String[] argRegs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        // NOTA (§546): o laço percorre TODOS os locais na ordem de índice; os
        // primeiros ocupam os registradores de ABI e os locais ≥ 8 leem a pilha
        // do caller. Locais que NÃO são args de entrada (temporários de índice
        // alto) acabam recebendo um valor de pilha — inofensivo, pois são
        // sempre escritos antes de lidos; reduzir o laço a
        // `parameterTypes().size()` quebra funções geradas com args ocultos
        // (ex.: `zipPairs<A,B>` recebe mais registradores que params Kof). A
        // correção §546 é a LEITURA da pilha (antes: clamp em a7).
        int argIdx = 0;
        for (IRLocalVariable lv : method.localVariables()) {
            if (lv.name().equals("this")) {
                sb.append("    sd a0, ").append(crossLocalOffRiscv(lv.index())).append("(s11)\n");
                argIdx++;
                continue;
            }
            if (argIdx < argRegs.length) {
                sb.append("    sd ").append(argRegs[argIdx]).append(", ")
                  .append(crossLocalOffRiscv(lv.index())).append("(s11)\n");
            } else {
                int stackOff = 8 * (argIdx - argRegs.length);
                sb.append("    ld t0, ").append(stackOff).append("(s11)\n");
                sb.append("    sd t0, ").append(crossLocalOffRiscv(lv.index())).append("(s11)\n");
            }
            argIdx++;
        }
        boolean endsWithReturn = false;
        for (IRBasicBlock block : method.basicBlocks()) {
            for (KofOperation op : block.operations()) {
                // X7-2 (DWARF cross, fatia 1): `.loc` por operação, do mesmo
                // KofDebugInfo do x86 (NativeMethodEmitter.emitOperation) — a
                // line table riscv/aarch espelha a fonte Kof (o tradutor
                // aarch repassa a diretiva verbatim).
                if (nb.debugInfo && method.debugInfo() != null) {
                    SourcePosition dbg = method.debugInfo().positions().get(op);
                    if (dbg != null && dbg.line() > 0) {
                        sb.append("    .loc 1 ").append(dbg.line()).append(" 0\n");
                    }
                }
                if (op instanceof KofReturn || op instanceof KofReturnVoid) endsWithReturn = true;
                emitCrossOpRiscv(sb, op, frameSize, joinMain);
            }
        }
        if (!endsWithReturn) {
            if (joinMain) sb.append("    call kof_spawn_join_all\n");
            sb.append("    mv sp, s11\n");
            sb.append("    addi sp, sp, -16\n");
            sb.append("    ld ra, 8(sp)\n");
            sb.append("    ld s11, 0(sp)\n");
            sb.append("    addi sp, sp, 16\n");
            sb.append("    ret\n");
        }
        if (nb.debugInfo) NativeDwarfCrossRegister.register(this, sb, mangled, method);
    }

    int crossLocalOffRiscv(int idx) { return -(idx + 1) * 8 - 16; }

    void emitMethodTableRiscv(StringBuilder sb, IRClass clazz) {
        List<String> methods = nb.collectVirtualMethods(clazz);
        sb.append(".align 3\n");
        sb.append(".globl ").append(nb.sanitizeName(clazz.name())).append("_vtable\n");
        sb.append(nb.sanitizeName(clazz.name())).append("_vtable:\n");
        if (methods.isEmpty()) {
            sb.append("    .quad 0\n");
            return;
        }
        for (String m : methods) sb.append("    .quad ").append(m).append("\n");
        sb.append("    .quad 0\n");
    }

    void emitCrossOpRiscv(StringBuilder sb, KofOperation op, int frameSize, boolean joinMain) {
        switch (op) {
            case KofGetStatic gs -> {
                // campo estático (bug 41): slot global no .data.
                String sym = nb.staticSymbol(nb.staticKey(gs.ownerType()), gs.name());
                sb.append("    la t0, ").append(sym).append("\n");
                sb.append("    ld t0, 0(t0)\n");
                pushRiscv(sb, "t0");
            }
            case KofPutStatic ps -> {
                String sym = nb.staticSymbol(nb.staticKey(ps.ownerType()), ps.name());
                sb.append("    pop t0\n");
                sb.append("    la t1, ").append(sym).append("\n");
                sb.append("    sd t0, 0(t1)\n");
            }
            case KofLoadLiteral lit -> emitCrossLoadLiteralRiscv(sb, lit);
            case KofLoadLocal ll -> {
                sb.append("    ld t0, ").append(crossLocalOffRiscv(ll.index())).append("(s11)\n");
                pushRiscv(sb, "t0");
            }
            case KofStoreLocal sl -> {
                sb.append("    pop t0\n");
                sb.append("    sd t0, ").append(crossLocalOffRiscv(sl.index())).append("(s11)\n");
            }
            case KofLoadField lf -> {
                sb.append("    pop t0\n");
                if (BuiltinTypes.isString(lf.ownerType()) && "length".equals(lf.name())) {
                    // String.length conta code units UTF-16 (bug 43).
                    sb.append("    mv a0, t0\n");
                    sb.append("    call kof_string_length\n");
                    pushRiscv(sb, "a0");
                    break;
                }
                int offset = nb.resolveFieldOffset(lf.ownerType(), lf.name());
                sb.append("    ld t0, ").append(offset).append("(t0)\n");
                pushRiscv(sb, "t0");
            }
            case KofStoreField sf -> {
                sb.append("    pop t0\n");   // valor
                sb.append("    pop t1\n");   // objeto
                int offset = nb.resolveFieldOffset(sf.ownerType(), sf.name());
                sb.append("    sd t0, ").append(offset).append("(t1)\n");
            }
            case KofBinary kb -> other.emitCrossBinaryRiscv(sb, kb);
            case KofUnary ku -> emitCrossUnaryRiscv(sb, ku);
            case KofConditionalJump kc -> other.emitCrossCondJumpRiscv(sb, kc);
            case KofLabel kl -> sb.append(nb.resolveLabel(kl.label())).append(":\n");
            case KofJump kj -> sb.append("    j ").append(nb.resolveLabel(kj.target())).append("\n");
            case KofCall kc -> other.emitCrossCallRiscv(sb, kc);
            case KofNewObject no -> emitCrossNewObjectRiscv(sb, no);
            case KofDup _ -> {
                sb.append("    ld t0, 0(sp)\n");
                pushRiscv(sb, "t0");
            }
            case KofDup2 _ -> {
                sb.append("    ld t0, 0(sp)\n    ld t1, 8(sp)\n");
                pushRiscv(sb, "t1"); pushRiscv(sb, "t0"); pushRiscv(sb, "t1"); pushRiscv(sb, "t0");
            }
            case KofDupX1 _ -> {
                sb.append("    ld t0, 0(sp)\n    ld t1, 8(sp)\n");
                pushRiscv(sb, "t0"); pushRiscv(sb, "t1"); pushRiscv(sb, "t0");
            }
            case KofDupX2 _ -> {
                sb.append("    ld t0, 0(sp)\n    ld t1, 8(sp)\n    ld t2, 16(sp)\n");
                pushRiscv(sb, "t0"); pushRiscv(sb, "t2"); pushRiscv(sb, "t1"); pushRiscv(sb, "t0");
            }
            case KofPop _ -> sb.append("    addi sp, sp, 8\n");
            // §142 (12/09): nativo empilha TODO valor como 1 qword (Long/Double
            // inclusive — ver pushRiscv). O POP2 herdado do JVM (16) desbalanceava
            // a pilha; descartar 1 qword. aarch64 herda via tradutor.
            case KofPop2 _ -> sb.append("    addi sp, sp, 8\n");
            case KofCheckCast _ -> { }
            case KofInstanceOf io -> {
                int targetTypeId = 0;
                if (BuiltinTypes.isString(io.type())) {
                    targetTypeId = NativeRuntime.KOF_STRING_TYPE_ID;
                } else if (io.type() instanceof Type.ClassType ct) {
                    for (IRClass clazz : nb.allClassesMap.values()) {
                        if (clazz.name().equals(ct.name()) || clazz.name().endsWith("/" + ct.name())
                                || ct.name().endsWith("/" + clazz.name()) || ct.name().equals(nb.sanitizeName(clazz.name()))) {
                            targetTypeId = clazz.typeId();
                            break;
                        }
                    }
                }
                sb.append("    pop a0\n");
                sb.append("    li a1, ").append(targetTypeId).append("\n");
                sb.append("    call kof_instanceof\n");
                pushRiscv(sb, "a0");
            }
            case KofNewArray na -> {
                sb.append("    pop a0\n");
                sb.append("    li a1, ").append(nb.elementTypeSize(na.elementType())).append("\n");
                sb.append("    call kof_array_alloc\n");
                pushRiscv(sb, "a0");
            }
            // §113 (port beta-0.3.0 → 0.4.0): n-D (≥2). Os dims JÁ estão na
            // pilha (d_n no topo); sp é a base dos offsets do helper — o
            // chamador NUNCA os popa, só avança o sp (frame fixo, sem
            // pilha-dinâmica do x86).
            case KofNewMultiArray ma -> {
                sb.append("    mv a0, sp\n");
                sb.append("    li a1, ").append(ma.dims()).append("\n");
                sb.append("    li a2, 1\n");
                sb.append("    li a3, ").append(nb.elementTypeSize(ma.baseType())).append("\n");
                sb.append("    call kof_multi_alloc\n");
                sb.append("    addi sp, sp, ").append(8 * ma.dims()).append("\n");
                pushRiscv(sb, "a0");
            }
            case KofArrayLoad _ -> {
                sb.append("    pop a1\n");   // idx
                sb.append("    pop a0\n");   // arr
                sb.append("    call kof_array_get\n");
                pushRiscv(sb, "a0");
            }
            case KofArrayStore as -> {
                sb.append("    pop a2\n");   // val
                sb.append("    pop a1\n");   // idx
                sb.append("    pop a0\n");   // arr
                // §187: `Char[]` trunca a 16 bits no store (JVM `CASTORE`).
                // O `elementTypeSize` segue 4 (stride/aloc/JSON intactos) e o
                // load `lw` continua correto porque o valor fica em [0,65535].
                if (NativeTypeKinds.isCharType(as.elementType())) {
                    sb.append("    slli a2, a2, 48\n");
                    sb.append("    srli a2, a2, 48\n");
                }
                sb.append("    call kof_array_set\n");
            }
            case KofArrayLength _ -> {
                sb.append("    pop a0\n");
                sb.append("    call kof_array_length\n");
                pushRiscv(sb, "a0");
            }
            case KofThrow _ -> {
                sb.append("    pop a0\n");
                sb.append("    call kof_throw_string\n");
            }
            case KofTryStart kts -> {
                sb.append("    addi sp, sp, -32\n");
                sb.append("    la t0, ").append(nb.resolveLabel(kts.handlerLabel())).append("\n");
                sb.append("    sd t0, 0(sp)\n");
                sb.append("    sd sp, 8(sp)\n");
                sb.append("    sd s11, 16(sp)\n");
                sb.append("    call kof_exc_slot\n");
                sb.append("    ld t2, 0(a0)\n");
                sb.append("    sd t2, 24(sp)\n");
                sb.append("    sd sp, 0(a0)\n");
            }
            case KofStatementIf _ -> {
                // §267: marcador de if de statement (uso exclusivo do dispatcher JS) — no-op
            }
            case KofContinueLabel _ -> {
                // §266: marcador estrutural (fronteira corpo/update do for) — no-op
            }
            case KofExcUnlink _ -> {
                // §549/§551: pop de handler control-flow (caminho normal do try,
                // break/continue/return que atravessam região). A base do frame
                // NÃO é necessariamente `sp` — há emissões (ex.: println cross)
                // que deixam temporário empilhado. Lê a base do TOPO da cadeia
                // (0(a0) = frame base salva no KofTryStart), religa o prev e
                // restaura sp antes de transferir o controle.
                sb.append("    call kof_exc_slot\n");
                sb.append("    ld t2, 0(a0)\n");
                sb.append("    ld t3, 24(t2)\n");
                sb.append("    sd t3, 0(a0)\n");
                sb.append("    mv sp, t2\n");
                sb.append("    addi sp, sp, 32\n");
            }
            case KofTryEnd _ -> {
                sb.append("    call kof_exc_slot\n");
                sb.append("    ld t2, 24(sp)\n");
                sb.append("    sd t2, 0(a0)\n");
                sb.append("    addi sp, sp, 32\n");
            }
            case KofCatchStart kcs -> {
                sb.append(nb.resolveLabel(kcs.handlerLabel())).append(":\n");
                sb.append("    addi sp, sp, 32\n");
                sb.append("    sd a0, ").append(crossLocalOffRiscv(kcs.localIndex())).append("(s11)\n");
            }
            case KofReturn _ -> {
                sb.append("    pop a0\n");
                if (joinMain) sb.append("    call kof_spawn_join_all\n");
                sb.append("    mv sp, s11\n");
                sb.append("    addi sp, sp, -16\n");
                sb.append("    ld ra, 8(sp)\n");
                sb.append("    ld s11, 0(sp)\n");
                sb.append("    addi sp, sp, 16\n");
                sb.append("    ret\n");
            }
            case KofReturnVoid _ -> {
                sb.append("    li a0, 0\n");
                if (joinMain) sb.append("    call kof_spawn_join_all\n");
                sb.append("    mv sp, s11\n");
                sb.append("    addi sp, sp, -16\n");
                sb.append("    ld ra, 8(sp)\n");
                sb.append("    ld s11, 0(sp)\n");
                sb.append("    addi sp, sp, 16\n");
                sb.append("    ret\n");
            }
            default -> sb.append("    # NATIVE002: op fora do caminho feliz riscv64: ").append(op.getClass().getSimpleName()).append("\n");
        }
    }

    void emitCrossNewObjectRiscv(StringBuilder sb, KofNewObject no) {
        ClassLayout layout = null;
        String className = null;
        int typeId = 0;
        if (no.type() instanceof Type.ClassType ct) {
            className = ct.name();
            for (IRClass clazz : nb.allClassesMap.values()) {
                if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                        || className.endsWith("/" + clazz.name()) || className.equals(nb.sanitizeName(clazz.name()))) {
                    layout = nb.getLayout(clazz);
                    className = clazz.name();
                    typeId = clazz.typeId();
                    break;
                }
            }
        }
        int size = layout != null ? layout.totalSize() : ClassLayout.HEADER_SIZE + 64;
        sb.append("    li a0, ").append(size).append("\n");
        sb.append("    call kof_alloc\n");
        if (className != null) {
            String mangled = nb.sanitizeName(className);
            sb.append("    mv a1, a0\n");
            sb.append("    li a2, ").append(typeId).append("\n");
            sb.append("    la a3, ").append(mangled).append("_vtable\n");
            sb.append("    mv a0, a1\n");
            sb.append("    mv a1, a2\n");
            sb.append("    mv a2, a3\n");
            sb.append("    call kof_init_object\n");
        }
        pushRiscv(sb, "a0");
    }

    void emitCrossUnaryRiscv(StringBuilder sb, KofUnary ku) {
        sb.append("    pop t0\n");
        switch (ku.op()) {
            // §181 cross: NEG em float/double era `neg` INTEIRO no bit
            // pattern cru — -inf (0x7FF0...) virava 0x8010... = NaN ->
            // caminho NaN da saturacao imprimia 0 (d2i) em vez de -MAX.
            // Negacao real = XOR do bit de sinal (sem `fneg`: o tradutor
            // aarch64 nao conhece a mnemonic; xor/li sim -- RtB40 idem).
            case NEG -> {
                if (ku.operandType() != null && NativeTypeKinds.isFloatType(ku.operandType())) {
                    sb.append("    li t1, 0x80000000\n");
                    sb.append("    xor t0, t0, t1\n");
                } else if (ku.operandType() != null && NativeTypeKinds.isDoubleType(ku.operandType())) {
                    sb.append("    li t1, 0x8000000000000000\n");
                    sb.append("    xor t0, t0, t1\n");
                } else {
                    sb.append("    neg t0, t0\n");
                }
            }
            case NOT -> sb.append("    seqz t0, t0\n");
            case I2L -> sb.append("    sext.w t0, t0\n");
            case I2C -> sb.append("    sext.w t0, t0\n");
            case L2I -> sb.append("    sext.w t0, t0\n");
            case I2F -> sb.append("    fcvt.s.w f0, t0\n    fmv.x.w t0, f0\n");
            case I2D -> sb.append("    fcvt.d.w f0, t0\n    fmv.x.d t0, f0\n");
            case L2F -> sb.append("    fcvt.s.l f0, t0\n    fmv.x.w t0, f0\n");
            case L2D -> sb.append("    fcvt.d.l f0, t0\n    fmv.x.d t0, f0\n");
            case F2D -> sb.append("    fmv.w.x f0, t0\n    fcvt.d.s f0, f0\n    fmv.x.d t0, f0\n");
            case D2F -> sb.append("    fmv.d.x f0, t0\n    fcvt.s.d f0, f0\n    fmv.x.w t0, f0\n");
            // §181 (13/09): SATURAÇÃO JLS 5.1.3 (NaN => 0; fora de faixa =>
            // limite). fcvt.*.rtz cru devolvia o "indefinite" riscv para
            // NaN/overflow (padrão de divergência do §181/x86). Ordem:
            // NaN PRIMEIRO (feq self => 0 sse NaN), depois clamp hi/lo.
            // FIX 13/09: labels ÚNICOS por emissão (`_<seq>`) — dois casts no
            // MESMO método (ex. `a as Int` + `b as Int`) geravam labels
            // duplicados e o GNU as falhava ("symbol already defined").
            // Float é promovido a double ANTES (NaN/faixa idênticos).
            case D2I, F2I -> {
                boolean isF = ku.op() == KofUnaryOp.F2I;
                String sfx = Long.toString(SAT181_SEQ.incrementAndGet());
                if (isF) sb.append("    fmv.w.x f0, t0\n    fcvt.d.s f0, f0\n");
                else sb.append("    fmv.d.x f0, t0\n");
                sb.append("    feq.d t1, f0, f0\n");              // t1=0 sse NaN
                sb.append("    beqz t1, .Lsat181_nan_").append(sfx).append("\n");
                // FIX 13/09: compara em DOUBLE contra ±2^31 ANTES de converter.
                // `fcvt.w.d` devolve o "indefinite" (0x80000000) p/ fora-de-faixa,
                // então comparar o inteiro já convertido nunca detecta o estouro
                // (ex.: 3.0e9 caía em INT_MIN em vez de INT_MAX). Padrões de bit
                // do double: +2^31=0x41E0000000000000, -2^31=0xC1E0000000000000.
                sb.append("    li t2, 0x41E0000000000000\n");    // +2^31
                sb.append("    fmv.d.x f1, t2\n");
                sb.append("    flt.d t1, f0, f1\n");              // t1=0 => f0 >= 2^31
                sb.append("    beqz t1, .Lsat181_hi_").append(sfx).append("\n");
                sb.append("    li t2, 0xC1E0000000000000\n");    // -2^31
                sb.append("    fmv.d.x f1, t2\n");
                sb.append("    flt.d t1, f0, f1\n");              // t1=1 => f0 < -2^31
                sb.append("    bnez t1, .Lsat181_lo_").append(sfx).append("\n");
                sb.append("    fcvt.w.d t0, f0, rtz\n");
                sb.append("    sext.w t0, t0\n");                  // estende sinal
                sb.append("    j .Lsat181_end_").append(sfx).append("\n");
                sb.append(".Lsat181_hi_").append(sfx).append(":\n");
                sb.append("    li t0, 2147483647\n");
                sb.append("    j .Lsat181_end_").append(sfx).append("\n");
                sb.append(".Lsat181_lo_").append(sfx).append(":\n");
                sb.append("    li t0, -2147483648\n");
                sb.append("    j .Lsat181_end_").append(sfx).append("\n");
                sb.append(".Lsat181_nan_").append(sfx).append(":\n");
                sb.append("    li t0, 0\n");
                sb.append(".Lsat181_end_").append(sfx).append(":\n");
            }
            case D2L, F2L -> {
                boolean isF = ku.op() == KofUnaryOp.F2L;
                String sfx = Long.toString(SAT181_SEQ.incrementAndGet());
                if (isF) sb.append("    fmv.w.x f0, t0\n    fcvt.d.s f0, f0\n");
                else sb.append("    fmv.d.x f0, t0\n");
                sb.append("    feq.d t1, f0, f0\n");              // double (F2L já promoveu)
                sb.append("    beqz t1, .Lsat181L_nan_").append(sfx).append("\n");
                // FIX 13/09: mesma estratégia do D2I — compara em double contra
                // ±2^63 (fcvt.l.d também devolve indefinido p/ fora-de-faixa).
                // Padrões de bit: +2^63=0x43E0000000000000, -2^63=0xC3E0000000000000.
                sb.append("    li t2, 0x43E0000000000000\n");    // +2^63
                sb.append("    fmv.d.x f1, t2\n");
                sb.append("    flt.d t1, f0, f1\n");              // t1=0 => f0 >= 2^63
                sb.append("    beqz t1, .Lsat181L_hi_").append(sfx).append("\n");
                sb.append("    li t2, 0xC3E0000000000000\n");    // -2^63
                sb.append("    fmv.d.x f1, t2\n");
                sb.append("    flt.d t1, f0, f1\n");              // t1=1 => f0 < -2^63
                sb.append("    bnez t1, .Lsat181L_lo_").append(sfx).append("\n");
                sb.append("    fcvt.l.d t0, f0, rtz\n");
                sb.append("    j .Lsat181L_end_").append(sfx).append("\n");
                sb.append(".Lsat181L_hi_").append(sfx).append(":\n");
                sb.append("    li t0, 9223372036854775807\n");
                sb.append("    j .Lsat181L_end_").append(sfx).append("\n");
                sb.append(".Lsat181L_lo_").append(sfx).append(":\n");
                sb.append("    li t0, -9223372036854775808\n");
                sb.append("    j .Lsat181L_end_").append(sfx).append("\n");
                sb.append(".Lsat181L_nan_").append(sfx).append(":\n");
                sb.append("    li t0, 0\n");
                sb.append(".Lsat181L_end_").append(sfx).append(":\n");
            }
        }
        pushRiscv(sb, "t0");
    }

    void pushRiscv(StringBuilder sb, String reg) {
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd ").append(reg).append(", 0(sp)\n");
    }

    void emitCrossLoadLiteralRiscv(StringBuilder sb, KofLoadLiteral lit) {
        switch (lit.value()) {
            case String s -> {
                String label = nb.internString(s);
                int len = s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                sb.append("    la a0, ").append(label).append("\n");
                sb.append("    li a1, ").append(len).append("\n");
                sb.append("    call kof_string_from_literal\n");
                pushRiscv(sb, "a0");
            }
            case Integer i -> {
                sb.append("    li t0, ").append(i).append("\n");
                pushRiscv(sb, "t0");
            }
            case Long l -> {
                sb.append("    li t0, ").append(l).append("\n");
                pushRiscv(sb, "t0");
            }
            case Boolean b -> {
                sb.append("    li t0, ").append(b ? 1 : 0).append("\n");
                pushRiscv(sb, "t0");
            }
            case Float f -> {
                sb.append("    li t0, ").append(Float.floatToIntBits(f)).append("\n");
                pushRiscv(sb, "t0");
            }
            case Double d -> {
                sb.append("    li t0, ").append(Double.doubleToLongBits(d)).append("\n");
                pushRiscv(sb, "t0");
            }
            case null -> {
                sb.append("    li t0, 0\n");
                pushRiscv(sb, "t0");
            }
            default -> {
                sb.append("    # NATIVE002: literal fora do caminho feliz: ").append(lit.value()).append("\n");
                sb.append("    li t0, 0\n");
                pushRiscv(sb, "t0");
            }
        }
    }

}
