package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #97 / T0 (PLAN-TREE-SHAKING): harness de tamanho + gate anti-regressão.
 *
 * <p>Trava os NÚMEROS MEDIDOS deste host como linha de largada: hoje o
 * runtime nativo é emitido 100% incondicional, então um `hello` carrega o
 * runtime inteiro (crypto/web/mq/vk/… inalcançáveis). O gate é UNILATERAL
 * (molde `ConformanceMatrixDocTest`): um artefato que INCHA &gt;5% quebra o
 * build; encolher é sempre ok — é exatamente a meta dos degraus T1a/T1b/T2.
 * Quando uma poda por alcançabilidade fechar, atualiza-se o baseline para o
 * número novo (e o gate volta a proteger de regressão a partir dali).
 *
 * <p>Puro-Java (parser ELF64 próprio) — não depende de `nm`/`readelf`; o
 * gate cross (riscv/aarch) usa {@link Assumptions} igual os E2E cross: sem
 * toolchain no host, pula (não é regressão silenciosa). JVM é lazy on-demand
 * (class-loading) — não há artefato único p/ travar; documentado no plano T4.
 */
class ArtifactSizeTest {

    private final CompilerDriver driver = new CompilerDriver();

    // Baseline MEDIDO neste host. Pré-S-3 (12/09 manhã): 138.928B/627 syms —
    // o hello carregava o runtime INTEIRO. Pós-S-3 (poda x86, esta sessão):
    // o gate trava o número NOVO (encolher foi a meta; o gate unilateral
    // volta a proteger de regressão a partir daqui).
    // §284/§284-map (18/09, lane nat): o box de erasure REAL (RuntimeErasureBox
    // — box/unbox/box_to_string/box_equals + soft-unbox) e o walker de Map no
    // kof_map_to_string/kof_json_encode_map entraram no conjunto alcançável do
    // hello via println(Object)/collections: 32.520→37.320B, 37→44 syms.
    // G-6(a)/§260(3) (19/09, lane .18 por ordem da mantenedora "assume
    // native-multiarch e termina"): o gatilho de free-list-exausta virou
    // call kof_gc_collect_now DENTRO de kof_alloc — a máquina mark/sweep
    // inteira passa a ser alcançável de TODO binário que aloca: 44→84 syms
    // (+40: mark/try_mark/transitive/sweep/collect/collect_now + aux).
    // Bytes permanecem DENTRO do gate +5% (medido). O §260(3) previa +19.7%
    // à época do trigger bruto; com o blanket spill no collect_now o salto
    // de símbolos é o link inevitável do coletor — "não é impeditivo", é a
    // decisão tamanho/valor documentada (matriz de aceite G-6 item 4).
    // adde9835 (19/09, lane nat): println de record aninhado por descritor
    // recursivo na x86 = codigo novo legitimo; hello 37.320 -> 39.232 re-medido
    // no host 19/09 (stash-test: byte-identico sem diff desta lane; culpa = codegen
    // do landing, nao o gate). Baseline unilateral como os anteriores.
    // B-0.2/PLAN-BAREMETAL-BOOT (22/09, costura HAL x86_64): o roteamento de
    // print/panic/exit pela costura kof_plat_* acrescentou
    // kof_plat_write/writev/exit/exit_group ao alcancavel do hello —
    // 39.232->39.304B (+0,2%), 84->88 syms. Medido no host pos-seam.
    // B-0.3/PLAN-BAREMETAL-BOOT (22/09, ambiente x86_64 pela costura): o
    // gettid do _start e os sítios de time/log/random/obs/orm passaram a
    // chamar kof_plat_thread_id/time/time_mono/sleep/random (a fatia
    // RuntimePlat linka inteira ao ser referenciada) — 39.304->39.512B
    // (+0,5%), 88->93 syms. Medido no host pós-seam.
    // B-0.3b-i (22/09, futex pela costura): kof_plat_sync entra no alcançável
    // do hello (o lock de alocação espera/acorda por futex) — 39.512->39.544B,
    // 93->94 syms. Medido no host pós-seam.
    // B-0.4a (22/09, net+read/close pela costura): `RuntimePlat` foi dividido
    // por FAMÍLIA (um método = uma fatia; classe+método é a granularidade do
    // podador) — o hello deixa de carregar as famílias que não usa (env, sync,
    // create, io, net) e ENCOLHE: 39.544->39.432B, 94->91 syms (encolher é
    // sempre ok). Medido no host pós-split.
    // B-1c (23/09, PLAN-BAREMETAL-BOOT): o dtoa do host deixou de usar libc e
    // passou a carregar o Schubfach portado do JDK — as tabelas g (617 pares
    // g1,g0) + pow10 são ~10KB de .rodata inerentes ao algoritmo exato. O host
    // não roda `ld --gc-sections` (o freestanding roda e poda a fatia inteira
    // quando não há Double: hello freestanding fica sem kof_schub_*), então o
    // hello x86 host cresceu 39.432→52.096B, 91→100 syms. Medido no host.
    // Follow-up (otimização, não correção): podar a fatia dtoa por uso no host.
    // §114 (face hash de record-aninhado, 24/09): a tabela densa
    // `kof_hashcode_table` (`.quad` por type_id, ao lado de toString/equals)
    // entra no .data do PROGRAMA — +1 símbolo no hello (106; bytes estáveis,
    // 52.096B inalterado). O `kof_obj_hash` que a consome ficou FORA do
    // alcançável do hello (movido da RuntimeList, que o hello puxa, para a
    // fatia RuntimeMath on-demand) justamente para não somar mais símbolos.
    // Baseline re-medido no host (mesmo processo do #104/B-1c).
    private static final long HELLO_X86_BYTES = 52_096L;
    private static final int HELLO_X86_SYMS = 106;
    // Pós-#104 (13/09): o shim globalThis.kof_platform do core JS (erro claro
    // em vez de ReferenceError fora do GraalJS) entrou no préâmbulo always —
    // o hello carrega ~827B a mais. Re-medido neste host: 6.873 → 7.700.
    // §166 (13/09): o shim DOM (#121: dataset/disabled/classList em kofMakeEl)
    // vive no MESMO préâmbulo always do core (é `if (typeof document ===
    // "undefined")`, sem DECL de topo — o chunker o trata como always). +9
    // linhas de API DOM legítima subiram o hello 7.700 → 8.297 (+7,8% > tol).
    // Baseline re-medido (mesmo processo do #104). Prune real = mover o shim
    // p/ unit alcançável por UI (T2 follow-up); aí o baseline volta a cair.
    // 13/09 2a re-medição: o #132 (ui-config no registry) expôs a expansão
    // do shim DOM (setAttribute/getAttribute/querySelector/querySelectorAll,
    // _attrs) no préâmbulo always — 8.297 → 13.007 (+57% > tol; soma de TODOS
    // os .mjs: runtime 11.577 + io 1.143 + Default 287). Mesmo processo; a
    // causa é a lane JS (lane do shim), o gate é universal.
    // 21/09: a frente FFI fechou a superfície do JS no alvo (D6): o slice
    // `kof.buffer` (R57/R58, `JsRuntimeBuffer`) + os helpers de marshal/bridge
    // (R55 array copy-in, R58 Buffer INOUT, R59 retorno de struct) somaram
    // ~827B ao runtime JS — re-medido 13.834B (mesmo processo da suíte).
    // 30/09 (cross-lane drift, measured with this lane's diff STASHED): the JS
    // runtime hello was already 18.098B at the tip before the memory-safety B-03
    // borrow work — i.e. the 5%/1.834B baseline above had gone stale from other
    // lane landings, not from this unit. The B-03 `JsRuntimeBuffer` borrow state
    // adds the remaining ~317B, giving 18.415B re-measured on this host with the
    // budget; the unilateral gate is re-pinned to the measured number.
    private static final long HELLO_JS_BYTES = 18_415L;
    // Hello riscv64 (cross — só medido onde há toolchain). Pós-S-5 (T1b,
    // 12/09): seções .text.<fn> por função do runtime + `ld --gc-sections`
    // derrubaram os irmãos mortos DENTRO das peças mantidas pela S-4:
    // syms 103→18 (a queda dos 103→18 é o gc-sections; 258→103 foi a poda
    // por peça da S-4). Bytes 136.792→133.288 (−2,6%): o .bss do heap bump
    // (~260KB reservado) é fixo sem mark-sweep — a queda real é em SÍMBOLOS.
    // G-4 (15/09): o kof_alloc passou a chamar o coletor (kof_gc_collect na
    // entrada + kof_gc_collect_now no OOM): o hello puxa agora sweep/collect/
    // tick — 18→24 símbolos. O G-4 é o que fecha o vazamento do .bss; o custo
    // (+6 syms no hello) é o preço do coletor alcançável. Bytes estáveis
    // (dentro da tolerância).
    // §284/§284-map (18/09, lane nat): RuntimeErasureBox riscv (B49: caixa real
    // + box_to_string + soft-unbox + box_equals) e o port do
    // kof_json_encode_map (B46) entraram no alcançável do hello:
    // 133.288→136.048B, 24→41 syms. (O hello agora carrega o par
    // put/println-boxed que o contrato Map nativo exige.)
    // B-0/PLAN-BAREMETAL-BOOT (22/09, costura HAL): o roteamento de
    // print/_start/random pela costura kof_plat_* acrescentou os símbolos
    // kof_plat_write/writev/exit_group/random ao alcançável do hello —
    // 136.048→136.824B (+0,6%), 41→45 syms (+4). Medido no host pós-seam.
    // B-1c-3 (23/09, lane 9093, dtoa Schubfach libc-free p/ §448): a fatia
    // schub_* (11 syms) passa a ser definida (antes: refs externas libc U,
    // não contadas) e é puxada por box_to_string→double_to_string (§284)
    // em TODO binário que imprime.
    // §448/§450 (23/09, lane baremetal/gaps-db): baseline do tip MESCLADO
    // re-medido em a148a9557 (55 syms; +10 vs 45) — o valor pré-§448 da B-1c-3
    // (54 syms) fica superseded; a lane re-mede ao pousar o seu código.
    // #643 (27/09, re-baseline COM CAUSA — §260 precedente, lane native):
    // o hello cross passou 55→64 syms (+9) MESMO sem chamar pow. BISECTADO:
    // revertendo as fontes do landing do math.pow (f66314e22/e69ea2bc6) o hello
    // continua 64 → a causa NÃO é o pow (que é podado: `kof_math_pow` ausente do
    // hello, libm só-por-uso). A deriva veio de `d2a41605c` (row 9, log cross —
    // fatia 2a): o B4 (`kof_json_encode_list`) passou a `call kof_json_enc_elem`
    // e o fecho por PEÇA (B3→B42-45 + scheduler/mapset) arrasta a família
    // json-decode (`kof_json_decode_*`/`kof_string_to_{double,float}`) para um
    // programa que só imprime. Bytes estáveis (136832 ≤ 136824×1.05).
    // Follow-up (otimização, não correção): quebrar a aresta log→json para o
    // hello não carregar o decoder. O valor abaixo é o MEDIDO no tip 27/09.
    // 30/09 (cross-lane drift, measured with this lane's diff STASHED): the tip
    // was already 70 syms (64 +6) — the B-03 borrow asm is tree-shaken out of a
    // hello that never touches `Buffer`, so it is NOT this unit's addition. The
    // syms gate is re-pinned to the measured tip; bytes stay under HELLO_RV_BYTES.
    private static final long HELLO_RV_BYTES = 136_832L;
    private static final int HELLO_RV_SYMS = 70;
    // Hello aarch64 (medido 12/09, mesmo caminho: poda S-4 + gc-sections S-5
    // no asm riscv ANTES do tradutor). G-4 (15/09): também 18→24 syms.
    // §284/§284-map (18/09): 133.112→201.408B, 24→41 syms — o TRADUTOR
    // aarch64 expande as fatias novas do riscv (movi/adrp-loops) muito acima
    // da média do binário; mesmo caminho de poda (regra 5), medido pós-port.
    // B-0 (22/09): 201.408→202.168B, 41→45 syms (mesma costura do riscv).
    // B-1c-3 (23/09, lane 9093): o dtoa Schubfach riscv atravessa o tradutor
    // aarch64 e entra no alcançável (aarch herda).
    // §448/§450 (23/09): baseline do tip MESCLADO re-medido (55 syms) —
    // superseded o valor pré-§448 da B-1c-3 (54); a lane re-mede ao pousar.
    // #643 (27/09): mesma deriva do riscv (`d2a41605c`, B4 log→json; NÃO o pow)
    // — re-medido no host: 64 syms, 136664B (o baseline de bytes 202168 era de
    // 18/09, antes da melhoria do tradutor; encolher é sempre ok).
    // 30/09 (cross-lane drift, measured with this lane's diff STASHED): inherits
    // the riscv +6 (70 syms) through the translator — NOT this unit (borrow asm
    // pruned from a hello). Syms gate re-pinned to the measured tip.
    private static final long HELLO_AA_BYTES = 136_664L;
    private static final int HELLO_AA_SYMS = 70;

    private static final double TOL = 0.05; // gate de inchaço >5%

    private Path helloNative(Path tmp, Target t) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, "main() {\n    println(\"hello\")\n}\n");
        Path out = tmp.resolve("out-" + t);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), "hello deve compilar p/ " + t + ": " + r.diagnostics().getDiagnostics());
        return out.resolve("Default/Main");
    }

    private static void assertNoBloat(long bytes, int syms, long baseBytes, int baseSyms, String what) {
        long maxBytes = Math.round(baseBytes * (1 + TOL));
        int maxSyms = (int) Math.round(baseSyms * (1 + TOL));
        assertTrue(bytes <= maxBytes,
                what + " inchou: " + bytes + "B > " + maxBytes + "B (baseline " + baseBytes + "B +5%)");
        assertTrue(syms <= maxSyms,
                what + " ganhou símbolos: " + syms + " > " + maxSyms + " (baseline " + baseSyms + " +5%)");
    }

    @Test
    void helloX86NativeSizeWithinBaseline(@TempDir Path tmp) throws IOException {
        Path bin = helloNative(tmp, Target.NATIVE);
        ArtifactSize.ElfSizes e = ArtifactSize.elf(bin);
        assertEquals(e.fileBytes(), Files.size(bin), "fileBytes deve bater stat");
        // o runtime é .text + .bss (heap bump/roots); o hello NÃO deve ser minúsculo
        // (a meta T1a é derrubar isso — hoje é o inchaço que o gate registra).
        assertTrue(e.sectionBytes(".text") > 0, "deve haver .text de runtime");
        // Pós-S-3: o hello NÃO carrega mais o runtime inteiro — a poda por
        // alcançabilidade derrubou 627→~37 símbolos. O gate agora exige o
        // número BAIXO (era o inverso, pré-poda). Se alguém re-introduzir
        // emissão incondicional, isso estoura.
        assertTrue(e.kofSymbols() < 110,
                "S-3 podou o hello a <110 syms; se estourou, alguém voltou a emitir runtime inteiro — symbs=" + e.kofSymbols());
        assertNoBloat(e.fileBytes(), e.kofSymbols(), HELLO_X86_BYTES, HELLO_X86_SYMS, "hello x86_64");
    }

    /**
     * T1a.4 (issue #97 S-3): "programa que usa X ⇒ família Y ausente". Prova
     * NÃO-vaciosa: os nomes abaixo são símbolos REAIS do mapa da S-2 (verificados
     * presentes no `.symtab` de um bin que usa a família) — não strings
     * inventadas que passariam por nunca existirem. Dois programas mínimos:
     * um puxa crypto (e NÃO deve trazer json/mq/vk/random), outro puxa json
     * (e NÃO deve trazer crypto). O seed é por TEXTO do programa (S-3), então
     * `crypto.sha256` → kof_sec_sha256_* entra e o resto fica fora.
     */
    @Test
    void nativeFamilyAbsenceAfterPrune(@TempDir Path tmp) throws IOException {
        ArtifactSize.ElfSizes sec = elfOf(tmp, "sec", "main() {\n    println(crypto.sha256(\"abc\"))\n}\n");
        // família chamada PRESENTE (anti-vácuo: o nome é real e entra)
        assertTrue(sec.definedKof().contains("kof_sec_sha256"),
                "crypto.sha256 deve puxar kof_sec_sha256; syms=" + sec.definedKof());
        // famílias NÃO-chamadas AUSENTES (a poda)
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_json")),
                "json deve estar PODADO num programa só-crypto: " + sec.definedKof());
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_mq")),
                "mq deve estar PODADO num programa só-crypto: " + sec.definedKof());
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_vk")),
                "vk deve estar PODADO num programa só-crypto: " + sec.definedKof());
        assertTrue(sec.definedKof().stream().noneMatch(s -> s.startsWith("kof_random")),
                "random deve estar PODADO num programa só-crypto: " + sec.definedKof());

        ArtifactSize.ElfSizes jsn = elfOf(tmp, "jsn", "main() {\n    println(json.encode(listOf(1, 2)))\n}\n");
        assertTrue(jsn.definedKof().contains("kof_json_encode_int"),
                "json.encode deve puxar kof_json_encode_int; syms=" + jsn.definedKof());
        assertTrue(jsn.definedKof().stream().noneMatch(s -> s.startsWith("kof_sec_sha256")),
                "sha256 deve estar PODADO num programa só-json: " + jsn.definedKof());
    }

    private ArtifactSize.ElfSizes elfOf(Path tmp, String tag, String source) throws IOException {
        Path src = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        Path out = tmp.resolve("out-" + tag);
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), tag + " deve compilar p/ native: " + r.diagnostics().getDiagnostics());
        return ArtifactSize.elf(out.resolve("Default/Main"));
    }

    /** T1a.4 (issue #97 S-4.2): família-ausência NO RISCV (port do gate x86).
     *  crypto sha256 NÃO existe no runtime riscv (0 símbolos medidos), então as
     *  famílias reais aqui são json/mq/vk/random. Programa só-json puxa a
     *  família json (anti-vácuo: nome real que entra) e as outras ficam PODADAS.
     *  Mesmo mecanismo da S-3: seed por TEXTO → o fecho traz só json ∪ piso. */
    @Test
    void riscvFamilyAbsenceAfterPrune(@TempDir Path tmp) throws IOException {
        assumeCross("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        ArtifactSize.ElfSizes jsn = elfOfTarget(tmp, "rvjsn", Target.NATIVE_RISCV64,
                "main() {\n    println(json.encode(listOf(1, 2, 3)))\n}\n");
        assertTrue(jsn.definedKof().contains("kof_json_encode_int"),
                "json.encode deve puxar kof_json_encode_int no riscv; syms=" + jsn.definedKof());
        assertTrue(jsn.definedKof().stream().noneMatch(s -> s.startsWith("kof_mq")),
                "mq deve estar PODADO num programa só-json no riscv: " + jsn.definedKof());
        assertTrue(jsn.definedKof().stream().noneMatch(s -> s.startsWith("kof_vk")),
                "vk deve estar PODADO num programa só-json no riscv: " + jsn.definedKof());
        assertTrue(jsn.definedKof().stream().noneMatch(s -> s.startsWith("kof_random")),
                "random deve estar PODADO num programa só-json no riscv: " + jsn.definedKof());
    }

    private ArtifactSize.ElfSizes elfOfTarget(Path tmp, String tag, Target t, String source) throws IOException {
        Path src = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source);
        Path out = tmp.resolve("out-" + tag);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), tag + " deve compilar p/ " + t + ": " + r.diagnostics().getDiagnostics());
        return ArtifactSize.elf(out.resolve("Default/Main"));
    }

    @Test
    void helloJsRuntimeSizeWithinBaseline(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, "main() {\n    println(\"hello\")\n}\n");
        Path out = tmp.resolve("out-js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "hello deve compilar p/ JS: " + r.diagnostics().getDiagnostics());
        long js = ArtifactSize.jsBytes(out);
        assertTrue(js < 30_000,
                "a poda por alcançabilidade (T2/S-6) tem de estar ativa — jsBytes=" + js);
        long max = Math.round(HELLO_JS_BYTES * (1 + TOL));
        assertTrue(js <= max, "runtime JS inchou: " + js + "B > " + max + "B (baseline " + HELLO_JS_BYTES + "B +5%)");
    }

    @Test
    void helloRiscvSizeWithinBaseline(@TempDir Path tmp) throws IOException {
        assumeCross("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        Path bin = helloNative(tmp, Target.NATIVE_RISCV64);
        ArtifactSize.ElfSizes e = ArtifactSize.elf(bin);
        // riscv não tem GC mark-sweep: o inchaço é .data (fatias) + .bss (heap).
        assertTrue(e.sectionBytes(".bss") > 100_000,
                "riscv reserva heap/statik por fatia no .bss (~260KB) — bss=" + e.sectionBytes(".bss"));
        // Pós-S-4.2: a poda derrubou 258→103 syms. Os bytes só caem 144k→137k
        // porque o heap bump em .bss é FIXO sem mark-sweep — a queda real do
        // riscv está em SÍMBOLOS, não em bytes (≠ x86, onde o gate trava os
        // dois). O baseline unilateral abaixo é o guard: re-emitir runtime
        // inteiro leva syms p/ ~258 > 103*1.05 → estoura.
        assertNoBloat(e.fileBytes(), e.kofSymbols(), HELLO_RV_BYTES, HELLO_RV_SYMS, "hello riscv64");
    }

    /** S-5 (T1b, 12/09): o aarch herda a poda+gc-sections pelo tradutor —
     *  o mesmo gate unilateral existe p/ ele (a primeira vez que um binário
     *  aarch64 tem baseline travado; antes, só riscv era medido). */
    @Test
    void helloAarch64SizeWithinBaseline(@TempDir Path tmp) throws IOException {
        assumeCross("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        Path bin = helloNative(tmp, Target.NATIVE_AARCH64);
        ArtifactSize.ElfSizes e = ArtifactSize.elf(bin);
        assertNoBloat(e.fileBytes(), e.kofSymbols(), HELLO_AA_BYTES, HELLO_AA_SYMS, "hello aarch64");
    }

    private static void assumeCross(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) {
                    Assumptions.assumeTrue(false, "toolchain cross ausente (" + c + ") — pulando (NATIVE002/#97)");
                }
            } catch (Exception e) {
                Assumptions.assumeTrue(false, "toolchain cross ausente — pulando");
            }
        }
    }
}
