package dev.kof.c;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Espinha do codegen: percorre a AST do subconjunto e delega cada instrução ao
 * alvo. As ISAs load-store (riscv64/aarch64) e o x86_64 compartilham o MESMO
 * fluxo (globais, if/while, binárias, deref/&, funções com parâmetros/retorno/
 * locais e chamadas), então o esqueleto vive aqui e cada {@link KofCEmitter}
 * implementa só os ganchos de instrução.
 *
 * <p>Modelo de valores: toda expressão deixa o resultado no acumulador do alvo
 * (rax/x0/a0) — que também é o registrador de retorno da ABI C. A pilha guarda
 * temporários de forma balanceada. Cada função tem um frame: um par
 * frame/retorno salvo e {@code frameSlots} slots de 8 bytes para parâmetros e
 * locais. Globais são sempre 8 bytes ({@code .comm}), como no x86.
 */
abstract class KofCEmitterBase implements KofCEmitter {
    protected final KofCAst.Program prog;
    protected final boolean executable;
    protected final StringBuilder sb = new StringBuilder();
    private final AtomicInteger labelSeq = new AtomicInteger(0);
    private static final List<String> PRINT_BUILTINS = List.of("print", "print_int", "kof_print");

    /** Localização de uma variável: slot do frame ou global. */
    protected record Storage(boolean local, int slot, String name) {
        static Storage local(int slot) { return new Storage(true, slot, null); }
        static Storage global(String name) { return new Storage(false, -1, name); }
    }

    private Map<String, Integer> localSlots = Map.of();
    private Map<String, String> varTypes = Map.of();
    private final Map<String, KofCAst.StructDecl> structs = new LinkedHashMap<>();
    private final Map<String, List<String>> calleeParams = new LinkedHashMap<>();
    private int nextSlot;
    private String funcEndLabel = "";

    protected KofCEmitterBase(KofCAst.Program prog, boolean executable) {
        this.prog = prog;
        this.executable = executable;
        for (var s : prog.structs()) structs.put(s.name(), s);
        for (var fn : prog.funcs()) calleeParams.put(fn.name(), fn.params().stream().map(KofCAst.Param::type).toList());
        for (var pt : prog.prototypes()) calleeParams.putIfAbsent(pt.name(), pt.params().stream().map(KofCAst.Param::type).toList());
    }

    /** Bytes do tipo struct (campos int = 4 B); 0 para não-struct. */
    protected int structBytes(String type) {
        if (type == null || !type.startsWith("struct ")) return 0;
        var d = structs.get(type.substring("struct ".length()));
        return d == null ? 0 : 4 * d.fields().size();
    }

    /** eightbytes = ceil(campos/2) — o bloco que atravessa em registradores. */
    protected int structEightbytes(String type) {
        int b = structBytes(type);
        if (b == 0) return 1;
        return (b + 7) / 8;
    }

    private int slotCount(String type) { return structBytes(type) == 0 ? 1 : structEightbytes(type); }

    private KofCAst.FuncDecl findFunc(String name) {
        for (var f : prog.funcs()) if (f.name().equals(name)) return f;
        return null;
    }

    @Override
    public String emit() {
        sb.setLength(0);
        emitDataSection(prog.globals());
        sb.append("    .text\n");
        if (executable) emitStart();
        if (usesPrint()) emitPrintHelpers();
        for (var fn : prog.funcs()) emitFunc(fn);
        return sb.toString();
    }

    /** Whether the program calls the {@code print()} builtin (avoids emitting unused globals in objects). */
    private boolean usesPrint() {
        for (var fn : prog.funcs()) {
            for (var st : fn.body()) if (usesPrint(st)) return true;
        }
        return false;
    }

    private boolean usesPrint(KofCAst.Stmt st) {
        return switch (st) {
            case KofCAst.IfStmt s -> { boolean f = usesPrint(s.cond()); for (var x : s.thenBody()) f |= usesPrint(x); yield f; }
            case KofCAst.WhileStmt s -> { boolean f = usesPrint(s.cond()); for (var x : s.body()) f |= usesPrint(x); yield f; }
            case KofCAst.ExprStmt s -> usesPrint(s.expr());
            case KofCAst.AssignStmt s -> usesPrint(s.value());
            case KofCAst.FieldAssignStmt s -> usesPrint(s.value());
            case KofCAst.ReturnStmt s -> s.value() != null && usesPrint(s.value());
            case KofCAst.AsmStmt _ -> false;
            case KofCAst.LocalDeclStmt _ -> false;
        };
    }

    private boolean usesPrint(KofCAst.Expr e) {
        return switch (e) {
            case KofCAst.CallExpr c -> PRINT_BUILTINS.contains(c.name())
                    || c.args().stream().anyMatch(this::usesPrint);
            case KofCAst.BinaryExpr b -> usesPrint(b.left()) || usesPrint(b.right());
            case KofCAst.ParenExpr p -> usesPrint(p.inner());
            case null, default -> false;
        };
    }

    private void emitFunc(KofCAst.FuncDecl fn) {
        localSlots = new LinkedHashMap<>();
        varTypes = new LinkedHashMap<>();
        nextSlot = 0;
        for (var g : prog.globals()) varTypes.putIfAbsent(g.name(), g.type());
        for (var p : fn.params()) {
            varTypes.put(p.name(), p.type());
            // variável de extensão k precisa de slot ≥ k-1 (extent cresce até rbp, nunca além)
            int k = slotCount(p.type());
            localSlots.put(p.name(), nextSlot + 2 * k - 2);
            nextSlot += k + (k - 1);
        }
        collectLocals(fn.body());
        sb.append("    .globl ").append(fn.name()).append("\n");
        sb.append(fn.name()).append(":\n");
        emitFuncPrologue(nextSlot);
        int r = 0;
        for (var prm : fn.params()) {
            int k = slotCount(prm.type());
            emitStoreParam(r, localSlots.get(prm.name()), k);
            r += k;
        }
        funcEndLabel = label("ret");
        for (var st : fn.body()) emitStmt(st);
        sb.append(funcEndLabel).append(":\n");
        emitFuncEpilogue();
    }

    /** Pré-varre o corpo para dimensionar o frame (declarações em blocos contam). */
    private void collectLocals(List<KofCAst.Stmt> body) {
        for (var st : body) {
            if (st instanceof KofCAst.LocalDeclStmt s) {
                if (!localSlots.containsKey(s.name())) {
                    // variável de extensão k precisa de slot ≥ k-1 (extent cresce até rbp, nunca além)
                    int k = slotCount(s.type());
                    localSlots.put(s.name(), nextSlot + 2 * k - 2);
                    nextSlot += k + (k - 1);
                }
                varTypes.put(s.name(), s.type());
            } else if (st instanceof KofCAst.IfStmt s) {
                collectLocals(s.thenBody());
            } else if (st instanceof KofCAst.WhileStmt s) {
                collectLocals(s.body());
            }
        }
    }

    /** Byte offset of {@code field} inside {@code type} ({@code struct X}); C int = 4 B. */
    protected int fieldOffset(String type, String field) {
        if (type == null || !type.startsWith("struct ")) return 0;
        KofCAst.StructDecl decl = structs.get(type.substring("struct ".length()));
        if (decl == null) return 0;
        for (int i = 0; i < decl.fields().size(); i++) {
            if (decl.fields().get(i).name().equals(field)) return 4 * i;
        }
        return 0;
    }

    protected void emitStmt(KofCAst.Stmt stmt) {
        if (stmt instanceof KofCAst.IfStmt s) {
            String end = label("if_end");
            emitExpr(s.cond());
            emitBranchIfZero(end);
            for (var st : s.thenBody()) emitStmt(st);
            sb.append(end).append(":\n");
        } else if (stmt instanceof KofCAst.WhileStmt s) {
            String start = label("while_start");
            String end = label("while_end");
            sb.append(start).append(":\n");
            emitExpr(s.cond());
            emitBranchIfZero(end);
            for (var st : s.body()) emitStmt(st);
            emitJump(start);
            sb.append(end).append(":\n");
        } else if (stmt instanceof KofCAst.AsmStmt s) {
            sb.append("    .byte ").append(s.value()).append("\n");
        } else if (stmt instanceof KofCAst.ExprStmt s) {
            emitExpr(s.expr());
        } else if (stmt instanceof KofCAst.LocalDeclStmt) {
            // o slot já foi reservado na pré-varredura; nada a emitir
        } else if (stmt instanceof KofCAst.ReturnStmt s) {
            if (s.value() != null) {
                emitExpr(s.value());
                // struct local ≥2 eightbytes: o segundo registro de retorno sai do slot anterior (low-1) — o struct ocupa k slots a partir de low para baixo
                if (s.value() instanceof KofCAst.IdentExpr id && structEightbytes(varTypes.get(id.name())) >= 2) {
                    var src = resolve(id.name());
                    emitLoadSecondReturn(src.local() ? Storage.local(src.slot() - 1) : src);
                }
            }
            emitJump(funcEndLabel);
        } else if (stmt instanceof KofCAst.AssignStmt s) {
            boolean two = twoEightbyteReturn(s.value());
            emitExpr(s.value());
            Storage target = resolve(s.target());
            if (s.deref()) emitDerefStoreStorage(target);
            else {
                emitStoreStorage(target);
                if (two) emitStoreSecondReturn(target.local() ? Storage.local(target.slot() - 1) : target);
            }
        } else if (stmt instanceof KofCAst.FieldAssignStmt s) {
            emitExpr(s.value());
            emitStoreField(resolve(s.target()), fieldOffset(varTypes.get(s.target()), s.field()));
        }
    }

    protected void emitExpr(KofCAst.Expr expr) {
        switch (expr) {
            case KofCAst.IntExpr e -> emitLoadImm(e.value());
            case KofCAst.IdentExpr e -> emitLoadStorage(resolve(e.name()));
            case KofCAst.UnaryAddr e -> emitAddrOfStorage(resolve(e.ident()));
            case KofCAst.UnaryDeref e -> emitLoadThroughStorage(resolve(e.ident()));
            case KofCAst.FieldExpr e -> emitLoadField(resolve(e.base()), fieldOffset(varTypes.get(e.base()), e.field()));
            case KofCAst.ParenExpr e -> emitExpr(e.inner());
            case KofCAst.CallExpr e -> emitCallExpr(e);
            case KofCAst.BinaryExpr e -> {
                // esquerda -> acc -> pilha; direita -> acc -> reg da direita;
                // pilha -> acc (esquerda); op acc, direita
                emitExpr(e.left());
                emitPushAcc();
                emitExpr(e.right());
                emitMoveAccToRight();
                emitPopLeftToAcc();
                emitBinaryOp(e.op());
            }
            case null, default -> { }
        }
    }

    private void emitCallExpr(KofCAst.CallExpr call) {
        if (PRINT_BUILTINS.contains(call.name())) {
            emitCall("kof_print");
            return;
        }
        // args: struct atravessa em seus eightbytes (ordem do ABI), escalar em 1 registro
        var ptypes = calleeParams.getOrDefault(call.name(), List.<String>of());
        int[] width = new int[call.args().size()];
        for (int i = 0; i < call.args().size(); i++) {
            String pt = i < ptypes.size() ? ptypes.get(i) : "int";
            if (pt.startsWith("struct ")) {
                width[i] = structEightbytes(pt);
                var st = resolve(structArg(call.args().get(i)));
                for (int j = 0; j < width[i]; j++) {
                    emitPackEightbyte(st, j, structs.get(pt.substring("struct ".length())).fields().size());
                    emitPushAcc();
                }
            } else {
                emitExpr(call.args().get(i));
                emitPushAcc();
                width[i] = 1;
            }
        }
        int[] pref = new int[call.args().size()];
        int wsum = 0;
        for (int i = 0; i < width.length; i++) { pref[i] = wsum; wsum += width[i]; }
        for (int i = call.args().size() - 1; i >= 0; i--) emitPopArg(pref[i], width[i]);
        emitCall(call.name());
    }

    private String structArg(KofCAst.Expr a) {
        return ((KofCAst.IdentExpr) a).name();
    }

    private boolean twoEightbyteReturn(KofCAst.Expr e) {
        if (!(e instanceof KofCAst.CallExpr c)) return false;
        var callee = findFunc(c.name());
        return callee != null && structEightbytes(callee.retType()) >= 2;
    }

    protected Storage resolve(String name) {
        Integer slot = localSlots.get(name);
        return slot != null ? Storage.local(slot) : Storage.global(name);
    }

    protected String label(String base) {
        return ".L" + base + "_" + labelSeq.getAndIncrement();
    }

    protected boolean hasGlobal(String name) {
        return prog.globals().stream().anyMatch(g -> g.name().equals(name));
    }

    // ---- ganchos de alvo -------------------------------------------------

    protected abstract void emitDataSection(List<KofCAst.VarDecl> globals);

    protected abstract void emitStart();

    protected abstract void emitPrintHelpers();

    protected abstract void emitFuncPrologue(int frameSlots);

    protected abstract void emitFuncEpilogue();

    protected abstract void emitLoadImm(int v);

    protected abstract void emitLoadStorage(Storage storage);

    protected abstract void emitStoreStorage(Storage storage);

    /** Loads a 32-bit C {@code int} struct field at {@code byteOffset}, sign-extended. */
    protected abstract void emitLoadField(Storage storage, int byteOffset);

    /** Stores the accumulator as a 32-bit C {@code int} struct field at {@code byteOffset}. */
    protected abstract void emitStoreField(Storage storage, int byteOffset);

    protected abstract void emitAddrOfStorage(Storage storage);

    protected abstract void emitLoadThroughStorage(Storage storage);

    protected abstract void emitDerefStoreStorage(Storage storage);

    protected abstract void emitPushAcc();

    protected abstract void emitMoveAccToRight();

    protected abstract void emitPopLeftToAcc();

    protected abstract void emitBinaryOp(String op);

    /**
     * Monta no acumulador o eightbyte {@code idx} da variável {@code base} (campos int de 4 B):
     * campo par zero-estendido, ímpar no meio alto ({@code |<<32}).
     */
    protected abstract void emitPackEightbyte(Storage base, int idx, int fields);

    /** Salva o argumento (k eightbytes em {@code ARG_REGS[argIndex..]}) nos slots consecutivos a partir de {@code slot}. */
    protected abstract void emitStoreParam(int argIndex, int slot, int eightbytes);

    /** Desempilha k valores para {@code ARG_REGS[argIndex..]}. */
    protected abstract void emitPopArg(int argIndex, int eightbytes);

    /** Salva o segundo registro de retorno (rdx/x1/a1) em {@code storage}. */
    protected abstract void emitStoreSecondReturn(Storage storage);

    /** Carrega {@code storage} no segundo registro de retorno (rdx/x1/a1). */
    protected abstract void emitLoadSecondReturn(Storage storage);

    protected abstract void emitBranchIfZero(String label);

    protected abstract void emitJump(String label);

    protected abstract void emitCall(String name);
}
