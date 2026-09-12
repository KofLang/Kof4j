package dev.kof.compiler.nat;

import java.lang.reflect.Field;
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
 * Issue #97 / S-4.1 (T1a.3): o mapa de PEÇAS do runtime nativo riscv64
 * (espelho riscv de {@link RuntimeSlices}, que cobre o x86 — S-2).
 *
 * <p>O runtime riscv NÃO é uma sequência de chamadas de emissão como o x86:
 * são 48 constantes {@code String} ({@code NativeRiscvAsmRt0.RISCV_RUNTIME_ASM_0}
 * … {@code NativeRiscvAsmRtB39.RISCV_RUNTIME_ASM_B_39}, Strn0/1, Mapset0/1/2)
 * concatenadas em {@code NativeRiscvAsm} e anexadas como bloco único pelo
 * {@code NativeArchEmitter} (riscv direto; aarch64 traduz a concatenação
 * inteira — podar aqui vale p/ os 2 alvos). A ORDEM é derivada do fonte de
 * produção (parse de {@code NativeRiscvAsm.java} na ordem das ocorrências
 * {@code NativeRiscvAsmXxx.CONST}), nunca transcrita: reordenar as concatenações
 * sem atualizar nada → o teste de paridade byte-a-byte quebra.
 *
 * <p>Diferenças riscv medidas (12/09) — o modelo é MAIS simples que o x86:
 * <ul>
 *   <li>0 homônimos {@code .L} entre peças (cada fatia B* namespaceia os
 *       rótulos próprios — a disciplina dos ports);</li>
 *   <li>33 arestas {@code .L} cross-peça distintas (o fecho UNIFICADO kof∪.L continua
 *       obrigatório — a lição da S-2.5 vale aqui também);</li>
 *   <li>1 externo program-side ({@code kof_super_table}), 0 locais do programa.</li>
 * </ul>
 *
 * <p>DRY com {@link RuntimeSlices}: os padrões/BFS são copiados, não extraídos,
 * para NÃO tocar no modelo x86 byte-provado das S-2/S-3 (fila ≤500 tem a
 * extração dum engine compartilhado como follow-up).
 */
public final class RiscvSlices {

    private RiscvSlices() {}

    /** Uma peça: classe dona + campo-constante + texto. Mesma forma do
     *  {@link RuntimeSlices.Slice} (method = nome do campo). */
    public record Piece(int index, String className, String field,
                        String text, Set<String> provides, Set<String> needs,
                        Set<String> localProvides, Set<String> localNeeds) {}

    private static final Pattern GLOBL_KOF =
            Pattern.compile("(?m)^\\s*\\.globl\\s+(kof_\\w+)\\b");
    private static final Pattern LABEL_KOF =
            Pattern.compile("(?m)^\\s*(kof_\\w+):");
    private static final Pattern KOF_REF =
            Pattern.compile("(?<![\\w.])kof_\\w+");
    private static final Pattern LOCAL_DEF =
            Pattern.compile("(?m)^\\s*(\\.L\\w+):");
    private static final Pattern LOCAL_REF =
            Pattern.compile("(?<![\\w.])(\\.L\\w+)\\b");
    private static final Pattern ASM_COMMENT = Pattern.compile("(?m)#.*$");
    /** Ocorrências de {@code NativeRiscvAsmXxx.CONST} no fonte agregador,
     *  na ordem em que aparecem — É a ordem de concatenação de produção. */
    private static final Pattern PIECE_REF =
            Pattern.compile("NativeRiscvAsm(\\w+)\\.(RISCV_[A-Z0-9_]+)\\b");

    /** Externos definidos pelo caminho de programa (medido 12/09: o runtime
     *  riscv referencia exatamente este; ver teste de needs órfãos). */
    public static Set<String> programSideSymbols() {
        return Set.of("kof_super_table");
    }

    /** O runtime riscv NÃO referencia rótulos `.L` do programa (medido 12/09:
     *  órfãos = ∅). Mantido p/ simetria com o modelo x86. */
    public static Set<String> programSideLocals() {
        return Set.of();
    }

    private static volatile List<Piece> cached;

    public static List<Piece> pieces() {
        List<Piece> s = cached;
        if (s == null) {
            synchronized (RiscvSlices.class) {
                s = cached;
                if (s == null) {
                    s = build();
                    cached = s;
                }
            }
        }
        return s;
    }

    /** Concatenação de TODAS as peças — byte-a-byte o bloco que o
     *  NativeArchEmitter anexa hoje. */
    public static String renderRuntime() {
        StringBuilder sb = new StringBuilder();
        for (Piece p : pieces()) sb.append(p.text());
        return sb.toString();
    }

    /** Só as peças em {@code keep}, na mesma ordem (S-4.2). */
    public static String renderSubset(Set<Integer> keep) {
        StringBuilder sb = new StringBuilder();
        for (Piece p : pieces()) {
            if (keep.contains(p.index())) sb.append(p.text());
        }
        return sb.toString();
    }

    public static Map<String, Integer> providerIndex() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Piece p : pieces()) {
            for (String s : p.provides()) {
                Integer prev = m.put(s, p.index());
                if (prev != null) {
                    throw new IllegalStateException("símbolo definido por 2 peças: "
                            + s + " [" + prev + "," + p.index() + "]");
                }
            }
        }
        return m;
    }

    public static Map<String, Integer> localProviderIndex() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Piece p : pieces()) for (String s : p.localProvides()) m.putIfAbsent(s, p.index());
        return m;
    }

    public static int crossPieceLocalEdgeCount() {
        Map<String, Integer> lp = localProviderIndex();
        int n = 0;
        for (Piece p : pieces()) {
            for (String l : p.localNeeds()) {
                Integer d = lp.get(l);
                if (d != null && d != p.index()) n++;
            }
        }
        return n;
    }

    /** Fecho UNIFICADO kof ∪ .L (a regra da S-2.5 vale no riscv: arestas
     *  cross-peça medidas). */
    public static Set<Integer> reachableFrom(Set<String> kofSeeds, Set<String> localSeeds) {
        Map<String, Integer> gp = providerIndex();
        Map<String, Integer> lp = localProviderIndex();
        Set<Integer> seen = new LinkedHashSet<>();
        ArrayDeque<String> kofQ = new ArrayDeque<>(kofSeeds);
        ArrayDeque<String> locQ = new ArrayDeque<>(localSeeds);
        while (!kofQ.isEmpty() || !locQ.isEmpty()) {
            while (!kofQ.isEmpty()) {
                Integer idx = gp.get(kofQ.poll());
                if (idx == null) continue;
                if (seen.add(idx)) { kofQ.addAll(pieces().get(idx).needs()); locQ.addAll(pieces().get(idx).localNeeds()); }
            }
            while (!locQ.isEmpty()) {
                Integer idx = lp.get(locQ.poll());
                if (idx == null) continue;
                if (seen.add(idx)) { kofQ.addAll(pieces().get(idx).needs()); locQ.addAll(pieces().get(idx).localNeeds()); }
            }
        }
        return seen;
    }

    /** Piso obrigatório do riscv (print/panic/alloc — medido 7/48). */
    public static Set<Integer> mandatoryRoots() {
        return reachableFrom(
                Set.of("kof_panic", "kof_alloc", "kof_print", "kof_println",
                        "kof_print_string", "kof_println_string"),
                Set.of());
    }

    public static Set<String> textKofSeeds(String programText) {
        Set<String> s = new LinkedHashSet<>();
        Matcher m = KOF_REF.matcher(ASM_COMMENT.matcher(programText).replaceAll(""));
        while (m.find()) s.add(m.group());
        s.removeAll(programSideSymbols());
        return s;
    }

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

    public static Set<Integer> keepForProgramText(String programText) {
        Set<Integer> keep = new LinkedHashSet<>(mandatoryRoots());
        keep.addAll(reachableFrom(textKofSeeds(programText), textLocalSeeds(programText)));
        return keep;
    }

    // ── construção ─────────────────────────────────────────────────────

    private static List<Piece> build() {
        List<String[]> order = readOrderFromSource();
        List<Piece> out = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            String cls = "dev.kof.compiler.nat.NativeRiscvAsm" + order.get(i)[0];
            String fld = order.get(i)[1];
            String text;
            try {
                Class<?> c = Class.forName(cls);
                Field f = c.getDeclaredField(fld);
                f.setAccessible(true);
                text = (String) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("peça " + cls + "." + fld
                        + " não resolvida por reflexão (mudou visibilidade/nome?)", e);
            }
            String code = ASM_COMMENT.matcher(text).replaceAll("");
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
            out.add(new Piece(i, cls, fld, text,
                    Collections.unmodifiableSet(provides),
                    Collections.unmodifiableSet(needs),
                    Collections.unmodifiableSet(localProvides),
                    Collections.unmodifiableSet(localNeeds)));
        }
        return Collections.unmodifiableList(out);
    }

    /** Ordem DERIVADA do fonte de produção: as ocorrências de
     *  {@code NativeRiscvAsmXxx.CONST} na ordem em que aparecem em
     *  {@code NativeRiscvAsm.java} (campos agregadores + cadeia B). Uma
     *  constante por (classe,campo) — repetições são dedupadas (não há hoje). */
    private static List<String[]> readOrderFromSource() {
        String src;
        try {
            src = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "kof-compiler/src/main/java/dev/kof/compiler/nat/NativeRiscvAsm.java"));
        } catch (Exception e) {
            try {
                src = java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/dev/kof/compiler/nat/NativeRiscvAsm.java"));
            } catch (Exception e2) {
                throw new IllegalStateException(
                        "NativeRiscvAsm.java não localizado (rode do módulo kof-compiler)", e2);
            }
        }
        List<String[]> pairs = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet();
        Matcher m = PIECE_REF.matcher(src);
        while (m.find()) {
            String key = m.group(1) + "#" + m.group(2);
            if (seen.add(key)) pairs.add(new String[]{m.group(1), m.group(2)});
        }
        if (pairs.size() < 40) {
            throw new IllegalStateException("ordem de peças sub-derivada: " + pairs.size());
        }
        return pairs;
    }
}
