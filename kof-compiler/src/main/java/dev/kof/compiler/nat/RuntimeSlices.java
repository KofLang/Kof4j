package dev.kof.compiler.nat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue #97 / T1a.1 (S-2): o mapa de FATIAS do runtime nativo x86-64.
 *
 * <p>O runtime hoje é emitido 100% incondicional
 * ({@code NativeRuntime.generateRuntimeAssembly}: ~113 chamadas de emissão,
 * 183 símbolos {@code kof_*}) — um {@code println("hello")} carrega crypto,
 * web, mq, vk, cache... O degrau T1a.1 precisa de um inventário
 * fatia→(provides,needs) para que T1a.2 ({@code Reachability} sobre a IR)
 * escolha o subconjunto mínimo. Este registro é esse inventário.
 *
 * <p><b>Zero risco por construção:</b> nada aqui toca o caminho de emissão
 * real. As fatias são INVOCADAS POR REFLEXÃO na mesma ordem que
 * {@code NativeRuntime} as chama — {@link #SLICES} é derivada do PRÓPRIO
 * corpo de {@code NativeRuntime.generateRuntimeAssembly} (parse do fonte em
 * build-time de teste), nunca transcrita à mão: se alguém reordena/insere/
 * remove um {@code RuntimeXxx.emitYyy(sb)} e esquece de atualizar o plano, o
 * teste {@code NativeRuntimeSliceRegistryTest} quebra (paridade byte-a-byte
 * do runtime renderizado + parse consistente do fonte).
 *
 * <p>Contrato das fatias (do plano §T1a):
 * <ul>
 *   <li>{@code provides()} — símbolos {@code kof_*} DEFINIDOS pela fatia
 *       (rótulos {@code .globl kof_*} no texto que ela emite).</li>
 *   <li>{@code needs()} — símbolos {@code kof_*} REFERENCIADOS pela fatia
 *       (call/jmp/branch/quad/leaq/movabs) que NÃO são provides seus.
 *       Conservador por design: se uma fatia A referencia um rótulo
 *       {@code .L*} (local) de outra, isso NÃO é aresta (rótulos locais não
 *       cruzam fatia — e se cruzarem o binário já não montaria hoje).</li>
 * </ul>
 *
 * <p>Uso futuro (T1a.2): BFS de {@code provides} a partir do fechamento da
 * IR ({@code KofCall} + entrypoints obrigatórios) → emitir só as fatias
 * alcançadas na MESMA ordem relativa.
 */
public final class RuntimeSlices {

    private RuntimeSlices() {}

    /** Uma fatia: classe emissora + método + texto renderizado isolado.
     *  `provides/needs` = símbolos globais `kof_*` (o contrato público);
     *  `localProvides/localNeeds` = rótulos locais `.L*` definidos/referencia-
     *  dos — a descoberta da S-3: existem 119 arestas `.L` entre fatias que só
     *  funcionam porque tudo é concatenado hoje; a BFS de alcançabilidade PRECISA
     *  uni-las, senão podar a fatia-dona de um `.L` lido por fatia viva quebra
     *  o `as` (undefined label). `localNeeds` já exclui os `localProvides` da
     *  própria fatia (rótulo interno não é aresta). */
    public record Slice(int index, String className, String method,
                        String text, Set<String> provides, Set<String> needs,
                        Set<String> localProvides, Set<String> localNeeds) {}

    private static final Pattern GLOBL_KOF =
            Pattern.compile("(?m)^\\s*\\.globl\\s+(kof_\\w+)\\b");
    /** Símbolos kof_* definidos como rótulo de linha (`sym:`) — o asm do
     *  runtime define auxiliares assim sem .globl (ex.: kof_print_dbl_emit);
     *  esses também entram no .symtab (nm mede 627 vs 373 .globl puros). */
    private static final Pattern LABEL_KOF =
            Pattern.compile("(?m)^\\s*(kof_\\w+):");
    private static final Pattern KOF_REF =
            Pattern.compile("(?<![\\w.])kof_\\w+");
    private static final Pattern LOCAL_DEF =
            Pattern.compile("(?m)^\\s*(\\.L\\w+):");
    private static final Pattern LOCAL_REF =
            Pattern.compile("(?<![\\w.])(\\.L\\w+)\\b");
    /** Comentário asm (#...) — NÃO é código; precisa ser riscado antes do
     *  scan de needs/provides (senão `# reusa kof_b64_*_internal` vira uma
     *  aresta falsa). O ; de fim-de-linha não é usado aqui como comentário. */
    private static final Pattern ASM_COMMENT = Pattern.compile("(?m)#.*$");

    /** Símbolos `kof_*` que NÃO vivem no runtime asm — são emitidos pelo
     *  CAMINHO DE PROGRAMA (Main.s / metadata de classe, ex.: o emissor
     *  NativeClassMeta/NativeArchEmitter), e o runtime os REFERENCIA. Para o
     *  grafo de alcançabilidade (T1a.2) são nós externos permanentes: nunca
     *  são candidatos a poda, e um needs que aponta p/ eles não é órfão.
     *  Fonte: grep por rótulos definidos fora do conjunto Runtime*. */
    public static Set<String> programSideSymbols() {
        return Set.of("kof_super_table");
    }

    /** Rótulos locais `.L*` definidos pelo CAMINHO DE PROGRAMA (Main.s) e
     *  referenciados pelo runtime asm — o programa SEMPRE os emite (raízes de
     *  dados que o runtime consome), então um localNeeds que aponta p/ eles não
     *  é órfão nem aresta de fatia p/ fatia. Medido 12/09 (localNeeds órfãos =
     *  exatamente estes 3, definidos em `NativeClassMeta`): `.Lnewline`,
     *  `.Lkof_str_true`, `.Lkof_str_false`. */
    public static Set<String> programSideLocals() {
        return Set.of(".Lnewline", ".Lkof_str_true", ".Lkof_str_false");
    }
    private static final Pattern SLICE_CALL =
            Pattern.compile("([A-Za-z][A-Za-z0-9_.]*)\\.([A-Za-z0-9_]+)\\(sb\\)");

    private static volatile List<Slice> cached;

    /** Todas as fatias na ordem EXATA de emissão do runtime de produção. */
    public static List<Slice> slices() {
        List<Slice> s = cached;
        if (s == null) {
            synchronized (RuntimeSlices.class) {
                s = cached;
                if (s == null) {
                    s = build();
                    cached = s;
                }
            }
        }
        return s;
    }

    /** Préâmbulo EXATO de {@code NativeRuntime.generateRuntimeAssembly}
     *  (raiz do GC + .text). As fatias seguem na ordem de {@link #slices()}. */
    public static final String PREAMBLE =
            "            .section .data\n"
          + "            .globl kof_heap_root_start\n"
          + "            kof_heap_root_start:\n"
          + "            .quad 0\n"
          + "            .section .text\n";

    /** Concatenação do préâmbulo + texto das fatias de {@link #slices()} —
     *  deve ser byte-a-byte {@code NativeRuntime.generateRuntimeAssembly()}. */
    public static String renderRuntime() {
        StringBuilder sb = new StringBuilder(PREAMBLE);
        for (Slice sl : slices()) sb.append(sl.text());
        return sb.toString();
    }

    /** S-3 (T1a.2): préâmbulo + SÓ as fatias em {@code keep}, na MESMA ordem
     *  da lista da S-2 (derivada do fonte de produção). {@code keep} = todas
     *  → byte-idêntico a {@link #renderRuntime()} (guardado por teste). O
     *  chamador é responsável por manter o estado de seção coerente p/ o tail
     *  (o {@code .section .text} pós-subset é injetado no backend). */
    public static String renderSubset(Set<Integer> keep) {
        StringBuilder sb = new StringBuilder(PREAMBLE);
        for (Slice sl : slices()) {
            if (keep.contains(sl.index())) sb.append(sl.text());
        }
        return sb.toString();
    }

    /** Seeds de TEXTO do programa (a correção de soundness da S-3): o programa
     *  emitido referencia símbolos do runtime como texto raw —
     *  {@code call kof_instanceof} (NativeMethodEmitter:303), arrays, casts —
     *  NÃO só via {@code KofCall}. Varre o texto do programa (head+tail, sem a
     *  região do runtime) por {@code kof_*} (globl) e {@code .L*} (locais, já
     *  filtrando os que o PRÓPRIO programa define e os do programa-side
     *  declarados). Um seed que não existe no mapa (símbolo do próprio
     *  programa, mangle de usuário, {@code kof_db_*} do tail) é ignorado pela
     *  BFS — só os definidos por fatia puxam. */
    public static Set<String> textKofSeeds(String programText) {
        Set<String> s = new LinkedHashSet<>();
        Matcher m = KOF_REF.matcher(ASM_COMMENT.matcher(programText).replaceAll(""));
        while (m.find()) s.add(m.group());
        s.removeAll(programSideSymbols());
        return s;
    }

    /** Seeds `.L` do programa: rótulos locais REFERENCIADOS pelo texto do
     *  programa que são DEFINIDOS por alguma fatia (o programa também define
     *  os seus — e.g. labels de dados — e só os cross-slice importam). */
    public static Set<String> textLocalSeeds(String programText) {
        Map<String, Integer> lp = localProviderIndex();
        Set<String> s = new LinkedHashSet<>();
        Matcher m = LOCAL_REF.matcher(ASM_COMMENT.matcher(programText).replaceAll(""));
        while (m.find()) {
            String l = m.group(1);
            if (lp.containsKey(l)) s.add(l);
        }
        s.removeAll(programSideLocals());
        return s;
    }

    /** KEEP FINAL da poda (S-3): piso obrigatório ∪ fecho unificado dos seeds
     *  de texto do programa. Entrada do {@link #renderSubset}. */
    public static Set<Integer> keepForProgramText(String programText) {
        Set<Integer> keep = new LinkedHashSet<>(mandatoryRoots());
        keep.addAll(reachableFrom(textKofSeeds(programText), textLocalSeeds(programText)));
        return keep;
    }

    /** Símbolos definidos pelo PRÉÂMBULO (raiz do GC) — sempre emitidos,
     *  donos de needs que os referenciam. */
    public static Set<String> preambleProvides() {
        Set<String> s = new LinkedHashSet<>();
        Matcher m = LABEL_KOF.matcher(PREAMBLE);
        while (m.find()) s.add(m.group(1));
        return s;
    }

    /** Mapa `kof_*` → fatia (índice) que o define (falha se duplicado). */
    public static Map<String, Integer> providerIndex() {
        Map<String, Integer> m = new LinkedHashMap<>();
        // -1 = dono é o préâmbulo (raiz do GC), não uma fatia podável
        for (String p : preambleProvides()) m.put(p, -1);
        for (Slice sl : slices()) {
            for (String p : sl.provides()) {
                Integer prev = m.put(p, sl.index());
                if (prev != null && prev >= 0) {
                    throw new IllegalStateException("símbolo definido por 2 fatias: "
                            + p + " [" + prev + "," + sl.index() + "]");
                }
            }
        }
        return m;
    }

    /** Mapa `.L*` → fatia (índice) que o define. Rótulos locais definidos em
     *  MAIS de uma fatia retornam dono = o da PRIMEIRA definição (verdade em
     *  cada fatia: um `.L` só pode ser definido uma vez por TU, mas pode haver
     *  homônimos entre fatias — por isso o assemblador só reclama em runtime se
     *  AMBOS forem emitidos no mesmo `.s`; a BFS cuida disso). */
    public static Map<String, Integer> localProviderIndex() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Slice sl : slices()) {
            for (String p : sl.localProvides()) m.putIfAbsent(p, sl.index());
        }
        return m;
    }

    /** Total de arestas `.L` cross-slice (referência a um `.L` definido em OUTRA
     *  fatia). O número da descoberta S-3: não é zero → o fecho puramente-kof
     *  seria inseguro. */
    public static int crossSliceLocalEdgeCount() {
        Map<String, Integer> lp = localProviderIndex();
        int n = 0;
        for (Slice sl : slices()) {
            for (String l : sl.localNeeds()) {
                Integer d = lp.get(l);
                if (d != null && d != sl.index()) n++;
            }
        }
        return n;
    }

    /** Fechamento de alcançabilidade UNIFICADO (a correção da S-3): parte dos
     *  seeds (`kof_*` e/ou `.L*`) e fecha transitivamente sobre AMBOS os tipos
     *  de aresta (kof-needs ∪ local-needs). É o fecho que a poda de S-3 deve
     *  usar — o kof-only IGNORA as arestas `.L` e cortaria fatia-dona de um
     *  `.L` lido por fatia viva. */
    public static Set<Integer> reachableFrom(Set<String> kofSeeds, Set<String> localSeeds) {
        Map<String, Integer> gp = providerIndex();
        Map<String, Integer> lp = localProviderIndex();
        Set<Integer> seen = new LinkedHashSet<>();
        ArrayDeque<String> kofQ = new ArrayDeque<>(kofSeeds);
        ArrayDeque<String> locQ = new ArrayDeque<>(localSeeds);
        while (!kofQ.isEmpty() || !locQ.isEmpty()) {
            while (!kofQ.isEmpty()) {
                Integer idx = gp.get(kofQ.poll());
                if (idx == null || idx < 0) continue; // préâmbulo/externo
                if (seen.add(idx)) expand(slices().get(idx), kofQ, locQ);
            }
            while (!locQ.isEmpty()) {
                Integer idx = lp.get(locQ.poll());
                if (idx == null) continue;
                if (seen.add(idx)) expand(slices().get(idx), kofQ, locQ);
            }
        }
        return seen;
    }

    private static void expand(Slice s, ArrayDeque<String> kofQ, ArrayDeque<String> locQ) {
        kofQ.addAll(s.needs());
        locQ.addAll(s.localNeeds());
    }

    /** O FECHO kof-only (sem arestas `.L`) — serve só para PROVAR (no teste) que
     *  ele é ESTRITAMENTE menor que {@link #reachableFrom}: a diferença são as
     *  fatias que só dependem de `.L` compartilhado. NÃO usar na poda real. */
    public static Set<Integer> reachableKofOnly(Set<String> kofSeeds) {
        Map<String, Integer> gp = providerIndex();
        Set<Integer> seen = new LinkedHashSet<>();
        ArrayDeque<String> q = new ArrayDeque<>(kofSeeds);
        while (!q.isEmpty()) {
            Integer idx = gp.get(q.poll());
            if (idx == null || idx < 0) continue;
            if (seen.add(idx)) q.addAll(slices().get(idx).needs());
        }
        return seen;
    }

    /** ÍNDICES de fatia = entrypoints obrigatórios de T1a.2 (o plano §T1a:
      *  runtime de suporte que SEMPRE entra, não importa a IR). Derivado do
      *  grafo UNIFICADO (kof-needs ∪ local-needs) a partir dos símbolos de
      *  print/alloc/panic — o piso de qualquer programa Kof compilável. Usa o
      *  fecho .L-aware de propósito: `kof_alloc` referencia o `.Lkof_alloc_count`
      *  definido na fatia memstats, e PODAR memstats emitindo alloc quebra o `as`
      *  (a descoberta S-3; kof-only seria INSEGURO — ver o teste). */
     public static Set<Integer> mandatoryRoots() {
         return reachableFrom(
                 Set.of("kof_panic", "kof_alloc", "kof_print", "kof_println",
                         "kof_print_string", "kof_println_string"),
                 Set.of());
     }

    // ── construção ─────────────────────────────────────────────────────

    private static List<Slice> build() {
        String[] srcAndOrder = readSourceAndOrder();
        Map<String, String> imports = parseImports(srcAndOrder[0]);
        List<String[]> pairs = java.util.Arrays.asList(splitOrder(srcAndOrder[1]));
        List<Slice> out = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            String clsRef = pairs.get(i)[0];
            String meth = pairs.get(i)[1];
            List<String> candidates = candidates(clsRef, imports);
            Method m = null;
            String fq = null;
            for (String cand : candidates) {
                try {
                    Class<?> c = Class.forName(cand);
                    m = c.getDeclaredMethod(meth, StringBuilder.class);
                    m.setAccessible(true);
                    fq = cand;
                    break;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // tenta o próximo candidato de pacote
                }
            }
            if (m == null) {
                throw new IllegalStateException("fatia " + clsRef + "." + meth
                        + " não resolvida por reflexão em " + candidates
                        + " (mudou visibilidade/pacote?)");
            }
            StringBuilder sb = new StringBuilder();
            try {
                m.invoke(null, sb);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("emissor " + fq + "." + meth + " lançou", e.getCause());
            }
            String code = ASM_COMMENT.matcher(sb).replaceAll("");
            Set<String> provides = new LinkedHashSet<>();
            Matcher g = GLOBL_KOF.matcher(code);
            while (g.find()) provides.add(g.group(1));
            Matcher lb = LABEL_KOF.matcher(code);
            while (lb.find()) provides.add(lb.group(1));
            Set<String> needs = new LinkedHashSet<>();
            Matcher r = KOF_REF.matcher(code);
            while (r.find()) {
                String sym = r.group();
                if (!provides.contains(sym)) needs.add(sym);
            }
            Set<String> localProvides = new LinkedHashSet<>();
            Matcher lg = LOCAL_DEF.matcher(code);
            while (lg.find()) localProvides.add(lg.group(1));
            Set<String> localNeeds = new LinkedHashSet<>();
            Matcher lr = LOCAL_REF.matcher(code);
            while (lr.find()) {
                String l = lr.group(1);
                if (!localProvides.contains(l)) localNeeds.add(l);
            }
            out.add(new Slice(i, fq, meth,
                    sb.toString(), Collections.unmodifiableSet(provides),
                    Collections.unmodifiableSet(needs),
                    Collections.unmodifiableSet(localProvides),
                    Collections.unmodifiableSet(localNeeds)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Candidatos de FQN para um nome de classe do fonte (pode ser simples
     *  — resolvido por import, com fallback nos 2 pacotes do runtime — ou
     *  qualified). Ordem: import > dev.kof.compiler.runtime > dev.kof.compiler. */
    private static List<String> candidates(String clsRef, Map<String, String> imports) {
        if (clsRef.contains(".")) return List.of(clsRef);
        List<String> out = new ArrayList<>();
        String imp = imports.get(clsRef);
        if (imp != null) out.add(imp);
        out.add("dev.kof.compiler.runtime." + clsRef);
        out.add("dev.kof.compiler." + clsRef);
        return out;
    }

    private static Map<String, String> parseImports(String src) {
        Map<String, String> m = new LinkedHashMap<>();
        Matcher im = Pattern.compile("^import\\s+([\\w.]+)\\.([A-Z]\\w*)\\s*;", Pattern.MULTILINE)
                .matcher(src);
        while (im.find()) m.put(im.group(2), im.group(1) + "." + im.group(2));
        return m;
    }

    /** Lê o fonte de produção; retorna [fonte, corpoDoMetodo]. A ordem das
     *  fatias É derivada daqui — nunca transcrita à mão. */
    private static String[] readSourceAndOrder() {
        String src;
        try {
            src = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "kof-compiler/src/main/java/dev/kof/compiler/NativeRuntime.java"));
        } catch (Exception e) {
            try {
                src = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/dev/kof/compiler/NativeRuntime.java"));
            } catch (Exception e2) {
                throw new IllegalStateException(
                        "NativeRuntime.java não localizado (rode do módulo kof-compiler)", e2);
            }
        }
        int start = src.indexOf("generateRuntimeAssembly()");
        int end = src.indexOf("return sb.toString", start);
        if (start < 0 || end < 0) throw new IllegalStateException("corpo não localizado");
        return new String[]{src, src.substring(start, end)};
    }

    private static String[][] splitOrder(String body) {
        List<String[]> pairs = new ArrayList<>();
        Matcher c = SLICE_CALL.matcher(body);
        while (c.find()) {
            String cls = c.group(1);
            if (cls.equals("System") || cls.equals("sb")) continue;
            pairs.add(new String[]{cls, c.group(2)});
        }
        if (pairs.size() < 100) {
            throw new IllegalStateException("ordem de fatias sub-derivada: " + pairs.size());
        }
        return pairs.toArray(new String[0][]);
    }
}
