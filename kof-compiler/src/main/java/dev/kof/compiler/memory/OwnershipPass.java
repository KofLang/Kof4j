package dev.kof.compiler.memory;

import dev.kof.compiler.ArrayAccessExpr;
import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.BlockStmt;
import dev.kof.compiler.CatchClause;
import dev.kof.compiler.DiagnosticCollector;
import dev.kof.compiler.DoWhileStmt;
import dev.kof.compiler.ForStmt;
import dev.kof.compiler.IfStmt;
import dev.kof.compiler.SwitchCase;
import dev.kof.compiler.SwitchStmt;
import dev.kof.compiler.TryStmt;
import dev.kof.compiler.WhileStmt;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.ForInStmt;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.SpawnStmt;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.UnaryExpr;
import dev.kof.compiler.VarDeclStmt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * D-MEMORY-SAFETY Fase 3 (fatia 1) — primeiro passe de ANALISE de ownership
 * com emissão: as faces retilíneas de O-01/MEM001 e O-02/MEM002 da spec
 * {@code docs/spec/memory-safety.md} §2.2, resolvidas SEM literal {@code null}
 * conforme {@code DECISIONS.md} §`D-COMPLETE-FIRST` (26/09): a transferencia e
 * um FATO INFERIDO do programa — quando o recurso e reivindicado por um
 * binding ({@code x.close()}) e o programa ainda le ou reivindica um dos
 * bindings-irmaos do mesmo recurso, o programa tratou a posse como implicita.
 * Kof nao tem move implicito; a regra de transferencia explicita (O-02) arde.
 *
 * <p><b>Faces desta fatia (retilineas, escopo declarado):</b>
 * <ul>
 *   <li>{@code MEM001} (O-01, dupla reivindicao): {@code x.close()} sobre um
 *       recurso ja reivindicado por QUALQUER binding do mesmo grupo — o
 *       segundo {@code close} e o segundo escopo-dono sem transferencia.</li>
 *   <li>{@code MEM002} (O-02, use-after-move): leitura de {@code y} (binding
 *       do grupo que NAO foi o reivindicante) depois de {@code x.close()} —
 *       transferencia implicita sem o face explicita da spec.</li>
 *   <li>{@code MEM013} (L-04, fatia 3): escape por {@code return} do proprio
 *       reivindicante depois do close — o handle morto escaparia ao chamador
 *       (escape analysis na fronteira de saida da regiao).</li>
 *   <li>{@code MEM022} (B-05, fatia 4): mutacao mudadora-de-tamanho da
 *       propria colecao iterada por um {@code for-in} ({@code add}/
 *       {@code remove}/{@code clear}/{@code addAll}) — o loop por indice
 *       reavalia {@code size} a cada iteracao; WARNING de postura zero-FP.</li>
 *   <li>{@code MEM021} (B-04/C-03, fatia 3.2): data race — o corpo de um
 *       {@code spawn} chama MUTADOR direto ({@code add}/{@code remove}/
 *       {@code clear}/{@code addAll} do mesmo set da B-05) sobre um binding
 *       compartilhado E o corpo-mae (ou um segundo spawn) muta a MESMA raiz
 *       de aliases sem nenhum {@code await} retilineo entre os dois pontos.
 *       ERROR na mutacao-tarde (ou no segundo spawn, worker×worker) — a
 *       corrida clara da spec. Postura zero-FP por construcao: {@code await}
 *       de QUALQUER handle limpa o pending (sub-reporta, nunca sobre-reporta);
 *       spawn condicional (dentro de braco) e interprocedural
 *       ({@code spawn f()}) ficam silenciosos — faces nomeadas do plano;
 *       leitura compartilhada sem escrita nao e a corrida B-04 da spec.
 *       Fatia 4.3/#660 (`D-MEM021-SCALAR`, decisao da mantenedora): a escrita
 *       do worker passa a incluir a REATRIBUICAO/incremento de um binding
 *       capturado (face ESCALAR, {@code n = ...}/{@code n++}) e a escrita da
 *       mae inclui reatribuicao/incremento do MESMO binding — ERROR, mesma
 *       postura zero-FP (leitura pura capturada baixa por valor, sem corrida).</li>
 * </ul>
 *
 * <p><b>Fatia 2 (26/09) — cruzamento de fluxo sem propagar, anti-falso-
 * positivo por construcao:</b> claim/leitura dentro de braco (then/else, corpo
 * de loop, try/catch/finally, case de switch) vivem uma COPIA snapshot do
 * estado da regiao-mae e o resultado do braco NUNCA volta para a mae — um
 * {@code close} condicional nao pode tornar ilegal o {@code close} seguinte
 * no fluxo retilineo (legitimo hoje). O oposto vale: claim ja CERTO na mae
 * arde MEM002 na leitura de irmao dentro de qualquer braco, e dupla
 * reivindicacao SEQUENCIAL dentro do MESMO braco arde MEM001 (executa duas
 * vezes na mesma iteracao). {@code BlockStmt} e incondicional: propaga.
 * try/catch/finally partem do snapshot PRE-try (o fluxo que abriu o braco de
 * exceção não é determinável no ponto de entrada). spawn/closures (E-/C-),
 * containers (O-03) e fronteiras FFI (O-05/MEM005) seguem fatias nomeadas do
 * plano. O binding reivindicante continua utilisavel (leitura de si mesmo nao
 * e face O-01/O-02; use-after-close do proprio handle e L-02/MEM011, runtime).
 *
 * <p>Nomes: grupos sao raiz-de-cadeia de aliases ({@code var a = r;} amarra
 * {@code a} na cadeia de {@code r}); o estado do recurso vive na raiz. Alias
 * de um binding ainda sem grupo so entra no grupo quando o grupo nasce.
 */
public final class OwnershipPass {

    private OwnershipPass() {
    }

    /** Analisa a regiao retilinea de um corpo; {@code null} diag = no-op. */
    public static void analyze(DiagnosticCollector diag, List<StatementNode> body) {
        analyze(diag, body, Map.of());
    }

    /**
     * @param ffiWriteArgs extern cujo nome mapeia os indices de argumento
     *                     {@code Buffer(U8)} INOUT — a escrita FFI B-03/MEM020
     *                     (#668). Vazio quando o modulo nao declara tais externs.
     */
    public static void analyze(DiagnosticCollector diag, List<StatementNode> body,
                               Map<String, Set<Integer>> ffiWriteArgs) {
        if (diag == null || body == null || body.isEmpty()) {
            return;
        }
        new Region(diag, ffiWriteArgs).walkBody(body);
    }

    /** Grupo de posse: um recurso, seus bindings e a ultima reivindicacao. */
    private static final class Group {
        final String root;
        boolean claimed;
        String claimer;
        SourcePosition claimSite;

        Group(String root) {
            this.root = root;
        }
    }

    private static final class Region {
        /** Mutadores de tamanho/indice — MESMA fonte da B-05 (SpawnCaptureScanner). */
        private static final java.util.Set<String> MUTATORS = SpawnCaptureScanner.MUTATORS;

        private final DiagnosticCollector diag;
        private final Map<String, Group> groups;
        private final Map<String, String> aliasOf;
        private StatementNode stmt;
        /** B-05/MEM022: raiz da colecao de um {@code for-in} que envolve este ponto. */
        private String iteratedBase;
        /**
         * B-04/C-03/MEM021 (fatia 3.2): raizes de bindings com MUTADOR direto
         * dentro de um spawn ainda nao sincronizado por {@code await} retilineo.
         * Qualquer {@code await} no fluxo retilineo limpa o mapa (postura
         * conservadora: sub-reporta, nunca sobre-reporta).
         */
        private final Map<String, SourcePosition> racy = new HashMap<>();
        /**
         * B-03/MEM020 (#668) — raizes de bindings {@code Buffer(U8)} escritos
         * por um {@code extern} dentro de um spawn ainda nao sincronizado por
         * {@code await}. Mapa separado de {@link #racy} para que a face FFI
         * carregue o codigo {@code MEM020} (B-03) e nao o {@code MEM021}.
         */
        private final Map<String, SourcePosition> racyFfi = new HashMap<>();
        /** extern → indices de argumento Buffer(U8) INOUT (B-03/MEM020). */
        private final Map<String, Set<Integer>> ffiWriteArgs;

        Region(DiagnosticCollector diag) {
            this(diag, Map.of());
        }

        Region(DiagnosticCollector diag, Map<String, Set<Integer>> ffiWriteArgs) {
            this.diag = diag;
            this.groups = new HashMap<>();
            this.aliasOf = new HashMap<>();
            this.ffiWriteArgs = ffiWriteArgs;
        }

        /** Copia snapshot do estado da mae (braços/loops/try veem so o CERTO). */
        Region(Region parent) {
            this.diag = parent.diag;
            this.groups = new HashMap<>(parent.groups);
            this.aliasOf = new HashMap<>(parent.aliasOf);
            this.iteratedBase = parent.iteratedBase;
            this.racy.putAll(parent.racy);
            this.racyFfi.putAll(parent.racyFfi);
            this.ffiWriteArgs = parent.ffiWriteArgs;
        }

        void walkBody(List<StatementNode> body) {
            if (body == null) {
                return;
            }
            for (StatementNode s : body) {
                step(s);
            }
        }

        void step(StatementNode s) {
            if (s == null) {
                return;
            }
            stmt = s;
            switch (s) {
                case VarDeclStmt vds -> {
                    ExpressionNode init = vds.initializer();
                    if (init instanceof IdentifierExpr id) {
                        readName(id.name());
                        aliasOf.put(vds.name(), id.name());
                    } else if (init != null) {
                        readExpr(init);
                    }
                }
                case ExpressionStmt es -> readExpr(es.expression());
                case ReturnStmt ret -> {
                    if (ret.value() != null) {
                        readExpr(ret.value());
                        escapeCheck(ret.value());
                    }
                }
                case BlockStmt blk -> {
                    // bloco nu e retilineo: propaga grupos, aliases E racy/racyFfi (#693)
                    Region sub = new Region(this);
                    sub.walkBody(blk.statements());
                    groups.clear();
                    groups.putAll(sub.groups);
                    aliasOf.clear();
                    aliasOf.putAll(sub.aliasOf);
                    racy.clear();
                    racy.putAll(sub.racy);
                    racyFfi.clear();
                    racyFfi.putAll(sub.racyFfi);
                }
                case IfStmt iff -> {
                    readExpr(iff.condition());
                    branch(iff.thenBranch());
                    branch(iff.elseBranch());
                }
                case WhileStmt w -> {
                    readExpr(w.condition());
                    branch(w.body());
                }
                case DoWhileStmt dw -> {
                    branch(dw.body());
                    readExpr(dw.condition());
                }
                case ForStmt fr -> {
                    step(fr.init());
                    readExpr(fr.condition());
                    branch(fr.body());
                    readExpr(fr.update());
                }
                case ForInStmt fi -> {
                    // B-05/MEM022 (fatia 4): o for-in baixa para um loop por
                    // indice que RE-AVALIA kof_list_size a cada iteracao e le
                    // kof_list_get(coll, idx) — mutar a propria colecao
                    // iterada (add/remove/clear/addAll) muda o tamanho/desloca
                    // indices durante a varredura. A colecao e lida na regiao
                    // externa; o corpo roda numa copia que carrega a raiz
                    // iterada, propagada para blocos/braços aninhados.
                    readExpr(fi.collection());
                    Region loop = new Region(this);
                    if (fi.collection() instanceof IdentifierExpr cid) {
                        loop.iteratedBase = base(cid.name());
                    }
                    loop.step(fi.body());
                }
                case SpawnStmt ss -> {
                    // B-04/C-03/MEM021 (fatia 3.2): `spawn <expr>` na posicao
                    // de statement — o corpo (LambdaExpr ou chamada) e uma
                    // EXECUCO_CONCORRENTE; nao e lido como regiao de ownership
                    // (capturas = faces E- do plano), mas o scan de mutadores
                    // diretos registra a corrida potencial no binding-mae.
                    registerSpawn(ss.position(), ss.expression());
                }
                case TryStmt tr -> {
                    // try/catch/finally partem do snapshot PRE-try (face
                    // conservadora sem falso-positivo; claims condicionais nao
                    // voltam para a mae — ver javadoc)
                    Region snap = new Region(this);
                    snap.walkBody(tr.tryBody());
                    if (tr.catchClauses() != null) {
                        for (CatchClause cc : tr.catchClauses()) {
                            new Region(this).walkBody(cc.body());
                        }
                    }
                    new Region(this).walkBody(tr.finallyBody());
                }
                case SwitchStmt sw -> {
                    readExpr(sw.expression());
                    if (sw.cases() != null) {
                        for (SwitchCase c : sw.cases()) {
                            new Region(this).walkBody(c.body());
                        }
                    }
                    new Region(this).walkBody(sw.defaultBody());
                }
                default -> {
                    // statements sem leitura/claim proprio (print lowering etc.)
                    // ou cujo formato este passe ainda nao conhece:
                    // conservador — nada a fazer (nunca heuristica silenciosa).
                }
            }
        }

        /** Regiao de braco: snapshot herdado, resultado NAO propaga. */
        private void branch(StatementNode body) {
            new Region(this).step(body);
        }

        /** Raiz da cadeia de aliases de {@code name} (sem cadeia = name). */
        private String base(String name) {
            String cur = name;
            for (int guard = 0; aliasOf.containsKey(cur) && guard < aliasOf.size() + 1; guard++) {
                cur = aliasOf.get(cur);
            }
            return cur;
        }

        private void claim(String name) {
            String base = base(name);
            Group g = groups.computeIfAbsent(base, Group::new);
            if (g.claimed) {
                diag.error(stmt, "O-01: double ownership claim on '" + base
                        + "' — first claimed via '" + g.claimer + "'"
                        + (g.claimSite != null ? " at line " + g.claimSite.line() : "")
                        + ", second via '" + name + "' (no transfer in between)",
                        "MEM001");
                return;
            }
            g.claimed = true;
            g.claimer = name;
            g.claimSite = stmt == null ? null : stmt.position();
        }

        private void readName(String name) {
            Group g = groups.get(base(name));
            if (g != null && g.claimed && !name.equals(g.claimer)) {
                diag.error(stmt, "O-02: use after move of '" + g.root + "' — ownership was"
                        + " claimed via '" + g.claimer + "'"
                        + (g.claimSite != null ? " at line " + g.claimSite.line() : "")
                        + "; Kof has no implicit transfer, and the source binding '"
                        + name + "' is still read",
                        "MEM002");
            }
        }

        /**
         * L-04/MEM013 (fatia 3) — escape por {@code return} do binding
         * REIVINDICANTE de um grupo ja reivindicado: a vida do recurso
         * terminou no close e o valor escaparia ao chamador como handle morto.
         * O retorno de um IRMAO nao-reivindicante ja arde O-02/MEM002 (face
         * mais precisa, emitida pelo readExpr antes desta checagem). Escapes
         * por campo, container, closure/spawn (Fase 4) e ponteiro interior de
         * FFI (Fase 5) seguem fatias nomeadas do plano — nada heuristico aqui.
         */
        private void escapeCheck(ExpressionNode value) {
            if (!(value instanceof IdentifierExpr id)) {
                return;
            }
            Group g = groups.get(base(id.name()));
            if (g != null && g.claimed && id.name().equals(g.claimer)) {
                diag.error(stmt, "L-04: invalid lifetime escape — '" + id.name()
                        + "' is returned after its resource was released via close()"
                        + (g.claimSite != null ? " at line " + g.claimSite.line() : "")
                        + "; the caller receives a dead handle",
                        "MEM013");
            }
        }

        /**
         * B-05/MEM022 (fatia 4) — mutacao MUDADORA DE TAMANHO da propria
         * colecao iterada por um {@code for-in} que envolve este ponto. O
         * for-in de Kof e por indice com {@code size} reavaliado por iteracao
         * (medido em {@code StatementLowerer}); {@code add}/{@code addAll}
         * podem prolongar/estender a varredura, {@code remove}/{@code clear}
         * a encurtam ou deslocam indices — sempre sem copia explicita.
         * WARNING (nao erro), espelhando a postura zero-falso-positivo de
         * {@link ResourceLeakAnalysis} (MEM014): o padrao worklist (BFS que
         * cresce a fila enquanto a varre) e intencional e continua compilando;
         * mutacoes de OUTRA colecao, de campo de elemento ou dentro de lambda
         * (execucao adiada) nao ardém.
         */
        private void mutationDuringIteration(MethodCallExpr mc) {
            if (iteratedBase == null || !MUTATORS.contains(mc.methodName())) {
                return;
            }
            if (!(mc.receiver() instanceof IdentifierExpr recv)) {
                return;
            }
            if (!base(recv.name()).equals(iteratedBase)) {
                return;
            }
            diag.warning(stmt, "B-05: mutation during iteration — '" + recv.name()
                    + "." + mc.methodName() + "(...)' changes the collection being"
                    + " iterated by 'for (var ... in " + iteratedBase + ")'; the"
                    + " index-based loop re-reads its size every iteration. Iterate"
                    + " a copy or use an explicit index/while loop (MEM022)",
                    "MEM022");
        }

        /**
         * B-04/C-03/MEM021 (fatia 3.2) — varre o corpo de um {@code spawn} e
         * marca cada binding capturado cujo MUTADOR direto (set B-05) aparece
         * la dentro. Se o MESMO binding ja estava pendente de outro spawn nao
         * sincronizado (worker×worker), arde MEM021 no segundo spawn; senao
         * entra no mapa como corrida em espera (a mae pode acender depois).
         * Corpo do lambda NAO vira regiao de ownership (capturas = faces E-).
         */
        private void registerSpawn(SourcePosition pos, ExpressionNode spawnBody) {
            SpawnCaptureScanner.Captures caps =
                    SpawnCaptureScanner.captured(spawnBody, ffiWriteArgs);
            for (String name : caps.mutated()) {
                String root = base(name);
                SourcePosition prior = racy.get(root);
                if (prior != null) {
                    diag.error(stmt, "C-03: data race — '" + root + "' is mutated by two"
                            + " unsynchronized spawns (line "
                            + (prior.line()) + " and here); join the first (await) before"
                            + " a second worker writes the same object (MEM021)",
                            "MEM021");
                    racy.remove(root);
                } else {
                    racy.put(root, pos);
                }
            }
            // B-03/MEM020 (#668): um extern com Buffer(U8) INOUT escreve o
            // buffer capturado; dois workers que escrevem o MESMO buffer sem
            // sincronizacao sao a corrida clara (distinta da MEM021: a escrita
            // vem da C, nao de um mutador Kof).
            for (String name : caps.ffiBuffers()) {
                String root = base(name);
                SourcePosition prior = racyFfi.get(root);
                if (prior != null) {
                    diag.error(stmt, "B-03: data race — '" + root + "' is written by an"
                            + " 'extern' in two unsynchronized spawns (line "
                            + (prior.line()) + " and here); join the first (await) before"
                            + " a second worker writes the same buffer (MEM020)",
                            "MEM020");
                    racyFfi.remove(root);
                } else {
                    racyFfi.put(root, pos);
                }
            }
        }

        /**
         * M-021 face da MAE: um MUTADOR no corpo de fora sobre um binding com
         * spawn pendente, sem {@code await} entre os dois, e a corrida clara —
         * arde MEM021 e remove do mapa (um erro por binding, sem spam).
         */
        private void mutationRacesSpawn(MethodCallExpr mc) {
            if (!MUTATORS.contains(mc.methodName())) {
                return;
            }
            if (!(mc.receiver() instanceof IdentifierExpr recv)) {
                return;
            }
            String root = base(recv.name());
            SourcePosition sp = racy.get(root);
            if (sp != null) {
                diag.error(stmt, "B-04: data race — '" + root + "." + mc.methodName()
                        + "(...)' mutates a shared object the spawn at line "
                        + sp.line() + " also writes, with no 'await' in between;"
                        + " synchronize (await the handle) before the parent write"
                        + " (MEM021)",
                        "MEM021");
                racy.remove(root);
            }
        }

        /**
         * B-03/MEM020 (#668) — face da MAE da escrita FFI: um {@code extern}
         * com parametro {@code Buffer(U8)} INOUT sobre um binding com spawn
         * pendente que TAMBEM escreveu o mesmo buffer, sem {@code await} entre,
         * e a corrida clara. Arde MEM020 e remove do mapa (um erro por binding).
         */
        private void ffiMutationRacesSpawn(MethodCallExpr mc) {
            if (mc.receiver() != null) {
                return;
            }
            Set<Integer> idxs = ffiWriteArgs.get(mc.methodName());
            if (idxs == null) {
                return;
            }
            for (int i : idxs) {
                if (i >= mc.arguments().size()
                        || !(mc.arguments().get(i) instanceof IdentifierExpr buf)) {
                    continue;
                }
                String root = base(buf.name());
                SourcePosition sp = racyFfi.get(root);
                if (sp != null) {
                    diag.error(stmt, "B-03: data race — the 'extern' " + mc.methodName()
                            + "(...) writes '" + root + "' here, and the spawn at line "
                            + sp.line() + " also writes it, with no 'await' in between;"
                            + " synchronize (await the handle) before the parent write"
                            + " (MEM020)",
                            "MEM020");
                    racyFfi.remove(root);
                }
            }
        }

        /**
         * B-04/C-03/MEM021 (fatia 4.3/#660) — face ESCALAR da MAE: uma ESCRITA
         * do corpo-mae sobre um binding com spawn pendente (reatribuicao
         * {@code n = ...} ou {@code n++}/{@code n--}), sem {@code await}
         * retilineo entre, e a corrida clara — o worker que escreve o binding
         * forca o box de representacao, entao mae e worker compartilham o slot
         * (medido: {@code 202}/{@code 101}). Arde MEM021 e remove do mapa (um
         * erro por binding, sem spam).
         */
        private void writeRacesSpawn(String name) {
            String root = base(name);
            SourcePosition sp = racy.get(root);
            if (sp != null) {
                diag.error(stmt, "B-04: data race — '" + root + "' is written by the parent"
                        + " after the spawn at line " + sp.line() + " also writes it, with no"
                        + " 'await' in between; synchronize (await the handle) before the parent"
                        + " write (MEM021)",
                        "MEM021");
                racy.remove(root);
            }
        }

        private void readExpr(ExpressionNode e) {
            switch (e) {
                case IdentifierExpr id -> readName(id.name());
                case MethodCallExpr mc -> {
                    // close() sem argumentos sobre um identificador = reivindicacao.
                    if ("close".equals(mc.methodName()) && mc.arguments().isEmpty()
                            && mc.receiver() instanceof IdentifierExpr recv) {
                        claim(recv.name());
                        return;
                    }
                    // B-04/C-03/MEM021 (fatia 3.2): `spawn { ... }` e `await h`
                    // em posicao de expressao baixam para chamadas sinteticas
                    // __kof_spawn_expr(lambda) / __kof_await(handle) — SEM
                    // receiver. O primeiro registra mutadores capturados; o
                    // segundo SINCRONIZA e limpa o pending conservador.
                    if (mc.receiver() == null && "__kof_spawn_expr".equals(mc.methodName())) {
                        for (ExpressionNode arg : mc.arguments()) {
                            registerSpawn(mc.position(), arg);
                        }
                        return;
                    }
                    if (mc.receiver() == null && "__kof_await".equals(mc.methodName())) {
                        racy.clear();
                        racyFfi.clear();
                        return;
                    }
                    mutationDuringIteration(mc);
                    mutationRacesSpawn(mc);
                    ffiMutationRacesSpawn(mc);
                    if (mc.receiver() != null) {
                        readExpr(mc.receiver());
                    }
                    for (ExpressionNode arg : mc.arguments()) {
                        readExpr(arg);
                    }
                }
                case FieldAccessExpr fa -> readExpr(fa.receiver());
                case ArrayAccessExpr aa -> {
                    readExpr(aa.receiver());
                    readExpr(aa.index());
                }
                case BinaryExpr be -> {
                    readExpr(be.left());
                    readExpr(be.right());
                }
                case UnaryExpr ue -> {
                    // B-04/MEM021 (fatia 4.3/#660): `n++`/`n--` da MAE sobre um
                    // escalar com spawn pendente e escrita concorrente.
                    if (("++".equals(ue.operator()) || "--".equals(ue.operator()))
                            && ue.operand() instanceof IdentifierExpr t) {
                        writeRacesSpawn(t.name());
                    }
                    readExpr(ue.operand());
                }
                case AssignmentExpr ae -> {
                    // B-04/MEM021 (fatia 4.3/#660): reatribuicao da MAE sobre um
                    // binding com spawn pendente, sem await entre, e corrida
                    // clara (o worker que escreve força o box; mae e worker
                    // compartilham o slot). Alvo nao-identifier (campo/indice) e
                    // face E- nomeada do plano. Rebinding nao transfere posse.
                    if (ae.target() instanceof IdentifierExpr t) {
                        writeRacesSpawn(t.name());
                    }
                    if (ae.value() != null) {
                        readExpr(ae.value());
                    }
                }
                case LambdaExpr _ -> {
                    // Capturas = faces E-/C- (fatias subsequentes da fase);
                    // o corpo do lambda e regiao propria, nunca lida aqui.
                }
                default -> {
                    // Expressoes sem leitura de binding (literais etc.) ou cujo
                    // formato este passe nao conhece: conservador, nada a fazer.
                }
            }
        }
    }
}
