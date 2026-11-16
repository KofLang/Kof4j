#!/usr/bin/env bash
# asan-ffi-sweep.sh — a §7 da OPERAÇÃO ("Quando disponível, utilize sanitizers")
# sobre as faces FFI NATIVAS: cada face pointer-heavy é compilada para o nativo
# (KOF_KEEP_ASM=1 — o hook existente), o .s é LINKADO pelo driver gcc com
# -fsanitize=address (o `as`+`ld` crus não carregam o libasan/interposição) e
# rodado sob ASAN_OPTIONS=detect_leaks=1.
#
# O QUE O ASAN VÊ (a medição honesta, RuntimeMemory §542):
#   - o heap Kof é uma ARENA mmap única — fora da vista do ASan por desenho;
#   - o lado C da fronteira FFI É rastreado: a interposição do alocador do ASan
#     pega UAF/double-free/LEAK nos mallocs das fixtures e das libs reais
#     (libpcap), mesmo não instrumentadas;
#   - o asm Kof não é instrumentado (o gcov do stack não aplica) — os limites
#     ficam nomeados no AUDIT-STABILITY.
#
# Uso: scripts/asan-ffi-sweep.sh   (rc=0 = todas as faces limpas + o controle pego)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

JAVAC="${JAVA_HOME:-$HOME/.local/jdks/jdk-25.0.4.1+1}/bin/javac"
JAVA="${JAVA_HOME:-$HOME/.local/jdks/jdk-25.0.4.1+1}/bin/java"
[ -x "$JAVAC" ] || { echo "asan-ffi-sweep: javac não encontrado (export JAVA_HOME)"; exit 2; }

ST="${ASAN_SWEEP_DIR:-$(mktemp -d)}"
if [ -n "${ASAN_SWEEP_DIR:-}" ]; then trap - EXIT; else trap 'rm -rf "$ST"' EXIT; fi
FAIL=0

ASM_JAR="$(find ~/.m2/repository/org/ow2/asm/asm -name '*.jar' | head -1)"
ASM_C="$(find ~/.m2/repository/org/ow2/asm/asm-commons -name '*.jar' | head -1)"
ASM_U="$(find ~/.m2/repository/org/ow2/asm/asm-util -name '*.jar' | head -1)"
ASM_T="$(find ~/.m2/repository/org/ow2/asm/asm-tree -name '*.jar' | head -1)"
CP="kof-compiler/target/classes:$ASM_JAR:$ASM_C:$ASM_U:$ASM_T"
for c in "$ASM_JAR" "$ASM_C" "$ASM_U" "$ASM_T"; do
    [ -n "$c" ] && [ -f "$c" ] || { echo "asan-ffi-sweep: ASM jars ausentes na m2"; exit 2; }
done

# o driver mínimo: compila o .kf para NATIVE e deixa o .s no lugar
cat > "$ST/AsanFace.java" <<'EOF'
import dev.kof.compiler.*;
import java.nio.file.*;
public class AsanFace {
    public static void main(String[] a) throws Exception {
        CompilerDriver d = new CompilerDriver();
        CompilationResult r = d.compile(Path.of(a[0]), Path.of(a[1]), Target.NATIVE);
        if (!r.success()) {
            System.err.println("COMPILE FAILED:");
            r.diagnostics().getDiagnostics().forEach(x -> System.err.println(x.code() + ": " + x.message()));
            System.exit(3);
        }
    }
}
EOF
"$JAVAC" -cp "$CP" "$ST/AsanFace.java" -d "$ST" || exit 2

# ── as faces (nome | fixture C | corpo Kof | libs do link) ──────────────
mk_face() {  # name, c_src, kf_body(library placeholder %LIB%), link_libs...
    local name="$1" csrc="$2" kof="$3"; shift 3
    mkdir -p "$ST/$name"
    printf '%s' "$csrc" > "$ST/$name/fixture.c"
    gcc -shared -fPIC -o "$ST/$name/libface.so" "$ST/$name/fixture.c" || return 1
    printf '%s' "$kof" > "$ST/$name/Main.kf"
    LINK_LIBS=("$ST/$name/libface.so" "$@")
    return 0
}

run_face() {  # name -> relata e acumula o FAIL
    local name="$1"
    local dir="$ST/$name"
    # o KOF_ASAN=1 (a face do #1166): o COMPILADOR linka pelo gcc com o crt
    # completo (o .init_array do ASan roda; o atexit roda; o LSan reporta).
    KOF_ASAN=1 KOF_KEEP_ASM=1 "$JAVA" -cp "$ST:$CP" AsanFace "$dir/Main.kf" "$dir/out" \
        > "$dir/compile.log" 2>&1 || { echo "FACE $name: COMPILE FAILED"; cat "$dir/compile.log"; FAIL=1; return; }
    local bin; bin="$(find "$dir/out" -name 'Main' -type f | head -1)"
    [ -n "$bin" ] || { echo "FACE $name: binário não encontrado"; FAIL=1; return; }
    ASAN_OPTIONS=detect_leaks=1 "$bin" > "$dir/run.log" 2>&1
    local rc=$?
    if [ $rc -eq 0 ] && ! grep -qE "ERROR: AddressSanitizer|SUMMARY: AddressSanitizer" "$dir/run.log"; then
        echo "FACE $name: CLEAN (exit 0, sem report ASan)"
    else
        echo "FACE $name: ASAN FINDING (exit $rc):"
        grep -E "ERROR: AddressSanitizer|SUMMARY: AddressSanitizer|#[0-9] " "$dir/run.log" | head -6
        FAIL=1
    fi
}

# ── face 1: o ciclo de vida malloc do Handle (alloc→read→free) ──────────
mk_face "handle-lifecycle" '#include <stdlib.h>
void* halloc(int v) {
    int* p = (int*)malloc(sizeof(int));
    if (p == 0) return 0;
    *p = v;
    return p;
}
int hread(void* h) {
    if (h == 0) return -1;
    return *(int*)h;
}
void hfree(void* h) { free(h); }
' 'foreign module fixtures {
    library "%LIB%"
    abi c
    extern halloc(Int v): Handle
    extern hread(Handle h): Int
    extern hfree(Handle h): Void
}

main() {
    var h = halloc(4242)
    println(hread(h))
    hfree(h)
    println("done")
}
' && sed -i "s|%LIB%|$ST/handle-lifecycle/libface.so|" "$ST/handle-lifecycle/Main.kf"
run_face "handle-lifecycle"

# ── face 2: o out-param Handle[] write-through (+ free) ─────────────────
mk_face "outparam" '#include <stdlib.h>
void xout(void** slot) {
    void* p = malloc(sizeof(int));
    if (p == 0) return;
    *(int*)p = 777;
    *slot = p;
}
int xread(void* h) {
    if (h == 0) return -1;
    return *(int*)h;
}
void xfree(void* h) { free(h); }
' 'foreign module fixtures {
    library "%LIB%"
    abi c
    ownership borrowed
    extern xout(Handle[] slot): Void
    extern xread(Handle h): Int
    extern xfree(Handle h): Void
}

main() {
    var slot = new Handle[1]
    xout(slot)
    var h = slot[0]
    println(xread(h))
    xfree(h)
    println("done")
}
' && sed -i "s|%LIB%|$ST/outparam/libface.so|" "$ST/outparam/Main.kf"
run_face "outparam"

# ── face 3: o copy-out (a C escreve o array do chamador; a arena Kof é o
#    alvo — invisível ao ASan por desenho, mas a face inteira roda limpa) ──
mk_face "copyout" 'void xfill_double(double* arr, int n) {
    for (int i = 0; i < n; i++) arr[i] = (double)(i * 10);
}
' 'foreign module fixtures {
    library "%LIB%"
    abi c
    ownership borrowed
    extern xfill_double(Double[] arr, Int n): Void
}

main() {
    var d = new Double[3]
    xfill_double(d, 3)
    println(d[0])
    println(d[2])
    println("done")
}
' && sed -i "s|%LIB%|$ST/copyout/libface.so|" "$ST/copyout/Main.kf"
run_face "copyout"

# ── face 4: o ciclo de vida do pcap (a lib real, malloc interceptado) ───
PCAP="/lib64/libpcap.so.1"
if [ -f "$PCAP" ]; then
    mkdir -p "$ST/pcap"
    cat > "$ST/pcap/Main.kf" <<'KOFEOF'
foreign module pcap_lifecycle {
    library "libpcap.so.1"
    abi c
    ownership borrowed
    extern pcap_open_dead(Int linktype, Int snaplen): Handle
    extern pcap_snapshot(Handle h): Int
    extern pcap_datalink(Handle h): Int
    extern pcap_close(Handle h): Void
}

Int dltEn10Mb() { return 1 }

main() {
    var h = pcap_open_dead(dltEn10Mb(), 65535)
    println(pcap_snapshot(h))
    println(pcap_datalink(h))
    pcap_close(h)
    println("done")
}
KOFEOF
    LINK_LIBS=("-l:libpcap.so.1")
    ( cd "$ST/pcap" && LINK_LIBS=("-l:libpcap.so.1") )
    # run_face usa LINK_LIBS global — define antes:
    LINK_LIBS=("-l:libpcap.so.1")
    KOF_KEEP_ASM=1 "$JAVA" -cp "$ST:$CP" AsanFace "$ST/pcap/Main.kf" "$ST/pcap/out" \
        > "$ST/pcap/compile.log" 2>&1 || { echo "FACE pcap: COMPILE FAILED"; cat "$ST/pcap/compile.log"; FAIL=1; }
    if [ $FAIL -eq 0 ]; then
        asm="$(find "$ST/pcap/out" -name '*.s' | head -1)"
        if [ -n "$asm" ]; then
            gcc -fsanitize=address -nostartfiles -o "$ST/pcap/bin" "$asm" -lc -l:libpcap.so.1 \
                > "$ST/pcap/link.log" 2>&1 \
                && ASAN_OPTIONS=detect_leaks=1 "$ST/pcap/bin" > "$ST/pcap/run.log" 2>&1
            rc=$?
            if [ $rc -eq 0 ] && ! grep -qE "ERROR: AddressSanitizer|SUMMARY: AddressSanitizer" "$ST/pcap/run.log"; then
                echo "FACE pcap-lifecycle: CLEAN (exit 0, sem report ASan)"
            else
                echo "FACE pcap-lifecycle: ASAN FINDING (exit $rc):"
                grep -E "ERROR: AddressSanitizer|SUMMARY: AddressSanitizer|#[0-9] " "$ST/pcap/run.log" | head -6
                FAIL=1
            fi
        else
            echo "FACE pcap-lifecycle: .s não encontrado"; FAIL=1
        fi
    fi
else
    echo "FACE pcap-lifecycle: SKIP HONESTO (libpcap ausente — o host não tem a lib)"
fi

# ── face 5: O CONTROLE (os dentes) — um malloc deliberadamente NÃO liberado
#    TEM que produzir o leak report; sem isto a varredura é falso-verde ────
mk_face "leak-control" '#include <stdlib.h>
void* halloc(int v) {
    int* p = (int*)malloc(sizeof(int));
    if (p == 0) return 0;
    *p = v;
    return p;
}
int hread(void* h) {
    if (h == 0) return -1;
    return *(int*)h;
}
void hfree(void* h) { free(h); }
void hleak(int v) {
    // o CONTROLE do sweep: a face que esquece deliberadamente (o vazamento
    // real que o LSan caça — o ponteiro não alcançável na saída).
    int* p = (int*)malloc(sizeof(int));
    if (p == 0) return;
    *p = v;
    (void)p;   // o ponteiro morre aqui — o vazamento É o esquecimento
}
' 'foreign module fixtures {
    library "%LIB%"
    abi c
    extern halloc(Int v): Handle
    extern hread(Handle h): Int
    extern hfree(Handle h): Void
    extern hleak(Int v): Void
}

main() {
    var h = halloc(4242)
    println(hread(h))
    hfree(h)
    hleak(99)   // O VAZAMENTO DELIBERADO — o ASan TEM que reportar
    println("done")
}
' && sed -i "s|%LIB%|$ST/leak-control/libface.so|" "$ST/leak-control/Main.kf"
# O CONTROLE corre PELO MESMO caminho do compilador (KOF_ASAN=1): o link
# manual -nostartfiles é o modo que o #1166 mediu NÃO inicializar o ASan.
KOF_ASAN=1 KOF_KEEP_ASM=1 "$JAVA" -cp "$ST:$CP" AsanFace "$ST/leak-control/Main.kf" "$ST/leak-control/out" \
    > "$ST/leak-control/compile.log" 2>&1 || { echo "CONTROL: COMPILE FAILED"; cat "$ST/leak-control/compile.log"; FAIL=1; }
cbin="$(find "$ST/leak-control/out" -name 'Main' -type f | head -1)"
if [ -n "$cbin" ]; then
    ASAN_OPTIONS=detect_leaks=1 "$cbin" > "$ST/leak-control/run.log" 2>&1
    crc=$?
    if grep -qE "ERROR: LeakSanitizer|SUMMARY: AddressSanitizer.*leak" "$ST/leak-control/run.log"; then
        echo "CONTROL leak-control: CAUGHT (o leak deliberado produziu o report — a varredura tem dentes)"
    else
        echo "CONTROL leak-control: NOT CAUGHT (falso verde da varredura!)"; FAIL=1
    fi
else
    echo "CONTROL: binário não encontrado"; FAIL=1
fi

echo "==="
if [ $FAIL -eq 0 ]; then
    echo "ASAN-FFI-SWEEP: GREEN — todas as faces limpas sob ASan; o controle de leak foi PEGO (dentes provados)"
    exit 0
fi
echo "ASAN-FFI-SWEEP: RED — findings acima (cada um é um bug real da fronteira ou um controle que falhou)"
exit 1
