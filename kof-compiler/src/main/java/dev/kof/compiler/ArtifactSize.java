package dev.kof.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Issue #97 / T0 (PLAN-TREE-SHAKING): leitura de tamanho de artefato SEM
 * dependência de binutils (`nm`/`readelf` não existem em todo host — a lição
 * da toolchain host-dependente) — parser ELF64 puro-Java dos cabeçalhos de
 * seção + symtab, e medição de bytes p/ artefatos JS (.mjs).
 *
 * <p>É o instrumento que transforma "achei que ficou menor" em prova: os
 * degraus T1a/T1b/bloco JS miram ESTES números (hello x86 ~138 KB / 605+
 * símbolos `kof_*` inalcançáveis; riscv idem com ~260; JS 173 KB de runtime
 * integral). Gate de regressão: um artefato que INCHA >5% quebra o teste;
 * encolher é sempre bem-vindo (é a meta — o gate é unilateral, moldado no
 * `ConformanceMatrixDocTest`: medida travada por teste).
 *
 * <p>Formato lido: ELF64 little-endian (o único que os backends Native
 * emitem: x86_64/riscv64/aarch64). Símbolos `kof_*` contados = DEFINIDOS no
 * `.symtab` (`st_shndx != SHN_UNDEF`), prefixo exato `kof_` (o contrato do
 * mangling do runtime; `Default_Main_main` etc. não contam).
 */
public final class ArtifactSize {

    private ArtifactSize() {}

    /** Resultado da leitura de um binário ELF. `definedKof` = nomes dos
     *  símbolos `kof_*` DEFINIDOS (mesmo critério da contagem) — T1a.4: o
     *  teste "programa que usa X ⇒ família Y ausente" precisa dos NOMBRES,
     *  não só do total. */
    public record ElfSizes(long fileBytes, Map<String, Long> sections, int kofSymbols,
                           java.util.Set<String> definedKof) {
        public long sectionBytes(String name) {
            Long v = sections.get(name);
            return v == null ? 0L : v;
        }
    }

    /** Lê um ELF64: mapa nome→tamanho de seção (sh_size; .bss mesmo sem
     *  espaço em arquivo) + contagem de símbolos definidos `kof_*`. */
    public static ElfSizes elf(Path bin) throws IOException {
        byte[] b = Files.readAllBytes(bin);
        if (b.length < 64 || b[0] != 0x7f || b[1] != 'E' || b[2] != 'L' || b[3] != 'F' || b[4] != 2) {
            throw new IOException("não é ELF64: " + bin);
        }
        long eShoff = u64(b, 0x28);
        int eShentsize = u16(b, 0x3a);
        int eShnum = u16(b, 0x3c);
        int eShstrndx = u16(b, 0x3e);
        Map<String, Long> sections = new LinkedHashMap<>();
        long strOff = secField(b, eShoff, eShentsize, eShstrndx, 0x18); // sh_offset
        String symtabName = null;
        long symOff = 0, symSize = 0, symEnt = 0, strOffForSym = 0;
        for (int i = 0; i < eShnum; i++) {
            long hdr = eShoff + (long) i * eShentsize;
            int nameOff = (int) (strOff + u32(b, hdr));
            String name = cstr(b, nameOff);
            long shType = u32(b, hdr + 4);
            long size = u64(b, hdr + 0x20);
            sections.put(name, size);
            if (shType == 2) { // SHT_SYMTAB — guarda o link p/ a strtab dela
                symtabName = name;
                symOff = u64(b, hdr + 0x18);
                symSize = size;
                symEnt = u64(b, hdr + 0x38);
                int link = (int) u32(b, hdr + 0x28);
                strOffForSym = u64(b, eShoff + (long) link * eShentsize + 0x18);
            }
        }
        int kof = 0;
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (symtabName != null && symEnt > 0) {
            for (long off = symOff; off + symEnt <= symOff + symSize; off += symEnt) {
                int stShndx = u16(b, off + 6);
                if (stShndx == 0) continue; // SHN_UNDEF (import)
                String nm = cstr(b, (int) (strOffForSym + u32(b, off)));
                if (nm.startsWith("kof_")) { kof++; names.add(nm); }
            }
        }
        return new ElfSizes(b.length, sections, kof, names);
    }

    /** Artefatos JS do build: soma de bytes de todos os *.mjs (o runtime
     *  copiado é o que T2 encolhe — `kof-runtime.mjs` integral = 173 KB no
     *  hello; a meta é ~8–20 KB). */
    public static long jsBytes(Path outDir) throws IOException {
        long total = 0;
        try (var s = Files.walk(outDir)) {
            for (Path p : s.filter(f -> f.getFileName().toString().endsWith(".mjs")).toList()) {
                total += Files.size(p);
            }
        }
        return total;
    }

    /** JSON estável (ordem de inserção, sem lib) p/ `--print-sizes`. */
    public static String toJson(ElfSizes e) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"fileBytes\":").append(e.fileBytes())
          .append(",\"kofSymbols\":").append(e.kofSymbols())
          .append(",\"sections\":{");
        boolean first = true;
        for (var en : e.sections().entrySet()) {
            if (en.getValue() == 0) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(esc(en.getKey())).append("\":").append(en.getValue());
        }
        return sb.append("}}").toString();
    }

    private static long secField(byte[] b, long shoff, int entsize, int idx, int field) {
        return field == 0x18 ? u64(b, shoff + (long) idx * entsize + field)
                             : u32(b, shoff + (long) idx * entsize + field);
    }

    private static int u16(byte[] b, long off) {
        return (b[(int) off] & 0xFF) | ((b[(int) off + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, long off) {
        return (b[(int) off] & 0xFFL) | ((b[(int) off + 1] & 0xFFL) << 8)
                | ((b[(int) off + 2] & 0xFFL) << 16) | ((b[(int) off + 3] & 0xFFL) << 24);
    }

    private static long u64(byte[] b, long off) {
        return u32(b, off) | (u32(b, off + 4) << 32);
    }

    private static String cstr(byte[] b, int off) {
        int end = off;
        while (end < b.length && b[end] != 0) end++;
        return new String(b, off, end - off, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
