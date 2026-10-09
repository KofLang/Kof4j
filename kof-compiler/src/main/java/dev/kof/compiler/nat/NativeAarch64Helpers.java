package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers de tradução riscv64 -> aarch64 (parseImm/registros/imediatos)
 * extraídos do NativeAarch64Translator (gate ≤500, REFACTOR-500) —
 * comportamento byte-idêntico, só mudança de casa (regra 3).
 */
final class NativeAarch64Helpers {

    private NativeAarch64Helpers() {}

// ---- tradutor riscv -> aarch64 (mesmo usado no probe Python) ----
    /**
     * Offset de memória riscv ("(-?\\d+)") com seguranca de range: o regex
     * aceita digitos que estouram int ("-99999999999"); em vez de NFE crua
     * no tradutor, null = nao encodable -> chamador faz passthrough da linha
     * (mesma saida do no-match). CodeQL uncaught-number-format-exception #244-246.
     */
    static Integer parseOffInt(String s) {
        try {
            long v = Long.parseLong(s);
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) return null;
            return (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Numero do registrador FP aarch64 (sem prefixo s/d) a partir de um
     * registrador riscv. Cobre `fN` (identidade) e os apelidos ABI `fa0..fa7`
     * (= f10..f17), que o tradutor antes renderizava como "da0" (lixo).
     * FLT001/B45: `strtod` retorna em `fa0` no riscv e em `d0` no aarch64 —
     * o apelido ABI `faN` mapeia para `dN` (regs de arg/retorno), nao para o
     * numero fisico f(10+N). E este mapeamento que faz a MESMA slice valer nos
     * dois alvos (regra 5).
     */
    static String fpNum(String r) {
        r = r.trim();
        if (r.length() > 2 && r.startsWith("fa") && Character.isDigit(r.charAt(2))) {
            return r.substring(2);
        }
        if (r.length() > 1 && r.charAt(0) == 'f' && Character.isDigit(r.charAt(1))) {
            return r.substring(1);
        }
        return r.substring(1);
    }

    static long parseImm(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            try {
                return Long.parseUnsignedLong(s.substring(2), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid immediate: " + s, e);
            }
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid immediate: " + s, e);
        }
    }

    static String aarch64Reg(String r) {
        return switch (r) {
            case "zero" -> "xzr";
            // §352: `x0` no asm riscv É o registrador zero (hardwired); sem
            // este mapa o tradutor emitia `cmp a0, x0` comparando com o X0
            // real — `slt t2, a0, x0` virava sempre false (face ±0.0 do sort).
            case "x0" -> "xzr";
            case "ra" -> "x30";
            case "sp" -> "sp";
            case "gp" -> "x3";
            case "tp" -> "x4";
            case "t0" -> "x9";
            case "t1" -> "x10";
            case "t2" -> "x11";
            case "t3" -> "x12";
            case "t4" -> "x13";
            case "t5" -> "x14";
            case "t6" -> "x15";
            case "s0" -> "x19";
            case "s1" -> "x20";
            case "s2" -> "x21";
            case "s3" -> "x22";
            case "s4" -> "x23";
            case "s5" -> "x24";
            case "s6" -> "x25";
            case "s7" -> "x26";
            case "s8" -> "x27";
            case "s9" -> "x28";
            case "s10" -> "x18";
            case "s11" -> "x29";
            case "a0" -> "x0";
            case "a1" -> "x1";
            case "a2" -> "x2";
            case "a3" -> "x3";
            case "a4" -> "x4";
            case "a5" -> "x5";
            case "a6" -> "x6";
            case "a7" -> "x8";
            default -> r;
        };
    }

    static List<String> aarch64MovImm(String rd, long imm) {
        long u = imm;
        if (u == 0) return List.of("mov " + rd + ", #0");
        List<String> out = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i < 4; i++) {
            int chunk = (int) ((u >> (16 * i)) & 0xFFFF);
            if (chunk == 0 && !first) continue;
            if (chunk == 0 && first) continue;
            if (first) {
                // movz aceita lsl; `mov` (alias) NÃO aceita — ex.: 262144
                // (0x40000) tem primeiro chunk não-zero em i=1.
                out.add((i != 0 ? "movz " : "mov ") + rd + ", #" + chunk + (i != 0 ? ", lsl #" + (16 * i) : ""));
                first = false;
            } else {
                out.add("movk " + rd + ", #" + chunk + ", lsl #" + (16 * i));
            }
        }
        if (first) out.add("mov " + rd + ", #0");
        return out;
    }

    static List<String> aarch64AddSubImm(String op, String rd, String rs, long imm, String indent) {
        if (imm >= 0 && imm <= 4095) return List.of(indent + op + " " + rd + ", " + rs + ", #" + imm);
        if (imm >= -4096 && imm <= -1) {
            String op2 = op.equals("add") ? "sub" : "add";
            return List.of(indent + op2 + " " + rd + ", " + rs + ", #" + (-imm));
        }
        if (imm == 4096 || imm == 8192 || imm == -4096 || imm == -8192) {
            long a = Math.abs(imm);
            long val = a >> 12;
            String op2 = imm > 0 ? op : (op.equals("add") ? "sub" : "add");
            return List.of(indent + op2 + " " + rd + ", " + rs + ", #" + val + ", lsl #12");
        }
        String tmp = "x17";
        List<String> out = new ArrayList<>();
        for (String s : aarch64MovImm(tmp, imm)) out.add(indent + s);
        out.add(indent + op + " " + rd + ", " + rs + ", " + tmp);
        return out;
    }
}
