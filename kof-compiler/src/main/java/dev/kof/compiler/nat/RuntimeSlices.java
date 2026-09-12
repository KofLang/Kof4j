package dev.kof.compiler.nat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    /** Uma fatia: classe emissora + método + texto renderizado isolado. */
    public record Slice(int index, String className, String method,
                        String text, Set<String> provides, Set<String> needs) {}

    private static final Pattern GLOBL_KOF =
            Pattern.compile("(?m)^\\s*\\.globl\\s+(kof_\\w+)\\b");
    /** Símbolos kof_* definidos como rótulo de linha (`sym:`) — o asm do
     *  runtime define auxiliares assim sem .globl (ex.: kof_print_dbl_emit);
     *  esses também entram no .symtab (nm mede 627 vs 373 .globl puros). */
    private static final Pattern LABEL_KOF =
            Pattern.compile("(?m)^\\s*(kof_\\w+):");
    private static final Pattern KOF_REF =
            Pattern.compile("(?<![\\w.])kof_\\w+");
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

    /** Símbolos definidos pelo PRÉÂMBULO (raiz do GC) — sempre emitidos,
     *  donos de needs que os referenciam. */
    public static Set<String> preambleProvides() {
        Set<String> s = new LinkedHashSet<>();
        Matcher m = LABEL_KOF.matcher(PREAMBLE);
        while (m.find()) s.add(m.group(1));
        return s;
    }

    /** Mapa símbolo → fatia (índice) que o define (falha se duplicado). */
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

    /** ÍNDICES de fatia = entrypoints obrigatórios de T1a.2 (o plano §T1a:
     *  runtime de suporte que SEMPRE entra, não importa a IR). Derivado do
     *  grafo: fechamento transitivo a partir dos símbolos de print/alloc/
     *  panic — o piso de qualquer programa Kof compilável. */
    public static Set<Integer> mandatoryRoots() {
        Set<Integer> roots = new LinkedHashSet<>();
        Map<String, Slice> bySym = new LinkedHashMap<>();
        for (Slice sl : slices()) for (String p : sl.provides()) bySym.put(p, sl);
        for (String seed : new String[]{"kof_panic", "kof_alloc", "kof_print",
                "kof_println", "kof_print_string", "kof_println_string"}) {
            Slice s = bySym.get(seed);
            if (s != null) closure(s, bySym, roots);
        }
        return roots;
    }

    private static void closure(Slice s, Map<String, Slice> bySym, Set<Integer> seen) {
        if (!seen.add(s.index())) return;
        for (String n : s.needs()) {
            Slice d = bySym.get(n);
            if (d != null) closure(d, bySym, seen);
        }
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
            out.add(new Slice(i, fq, meth,
                    sb.toString(), Collections.unmodifiableSet(provides),
                    Collections.unmodifiableSet(needs)));
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
