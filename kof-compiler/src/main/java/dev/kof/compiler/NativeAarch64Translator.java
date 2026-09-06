package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * FASE 3 (REFACTOR-500): tradutor riscv64 -> aarch64 do NativeBackend.
 * Extraído verbatim (parseImm/aarch64Reg/aarch64MovImm/aarch64AddSubImm/
 * translateRiscvToAarch64) — bloco estático e autocontido; a saída aarch64
 * é byte-idêntica (prova: diff do .s nos 3 targets).
 */
final class NativeAarch64Translator {

    private NativeAarch64Translator() {}

    // ---- tradutor riscv -> aarch64 (mesmo usado no probe Python) ----
    private static long parseImm(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) return Long.parseUnsignedLong(s.substring(2), 16);
        return Long.parseLong(s);
    }

    private static String aarch64Reg(String r) {
        return switch (r) {
            case "zero" -> "xzr";
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
            case "s10" -> "x16";
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

    private static List<String> aarch64MovImm(String rd, long imm) {
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

    private static List<String> aarch64AddSubImm(String op, String rd, String rs, long imm, String indent) {
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

    static List<String> translateRiscvToAarch64(String line) {
        String indent = line.substring(0, line.length() - line.stripLeading().length());
        // strip trailing comment rest-stripped before anything else
        String s = line.strip();
        if (s.isEmpty()) return List.of(line);
        if (s.startsWith("#")) return List.of(indent + "//" + s.substring(1));
        // remove qualquer comentário inline (riscv ` # ...` → nada) — mas só
        // fora de aspas: `.asciz "# TYPE "` tem '#' dentro da string.
        int ci = -1;
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') inStr = !inStr;
            else if (ch == '#' && !inStr) { ci = i; break; }
        }
        if (ci > 0) s = s.substring(0, ci).stripTrailing();
        if (s.endsWith(":") && !s.contains(" ")) return List.of(line);
        if (s.isEmpty()) return List.of(line);
        // label com diretiva (ex: "kof_alloc_ptr: .quad ...")
        if (s.matches("^\\S+:\\s+\\.\\w+.*")) {
            int colon = s.indexOf(':');
            String label = s.substring(0, colon + 1);
            String rest = s.substring(colon + 1).strip();
            return List.of(label, indent + rest);
        }
        if (s.startsWith(".")) {
            if (s.startsWith(".align")) return List.of(line);
            if (s.startsWith(".option")) return List.of(indent + ".arch armv8.1-a");
            return List.of(line);
        }
        // split mnemonic e resto
        String mn;
        String rest = "";
        int sp = s.indexOf(' ');
        int tab = s.indexOf('\t');
        int cut = -1;
        if (sp != -1 && tab != -1) cut = Math.min(sp, tab);
        else if (sp != -1) cut = sp;
        else if (tab != -1) cut = tab;
        if (cut == -1) { mn = s; }
        else { mn = s.substring(0, cut); rest = s.substring(cut).strip(); }
        // helpers
        java.util.function.Function<String,String> R = NativeBackend::aarch64Reg;
        // casos especiais FP antes dos genéricos
        // fcvt.w.s / fcvt.w.d / fcvt.l.s / fcvt.l.d
        if (mn.startsWith("fcvt.")) {
            // ex: fcvt.w.s f0, t0, rtz  ou fcvt.s.d f0, f0
            String[] parts = mn.split("\\.");
            // parts[0]=fcvt, parts[1]=w/l/s/d, parts[2]=s/d
            if (parts.length == 3 && (parts[1].equals("w") || parts[1].equals("l")) && (parts[2].equals("s") || parts[2].equals("d"))) {
                // fcvt.w.s -> scvtf s0, w9
                String[] args = rest.split(",");
                String fd = args[0].trim(); // f0
                String rs = args[1].trim(); // t0
                String dst = parts[2].equals("s") ? "s" + fd.substring(1) : "d" + fd.substring(1);
                String src = parts[1].equals("w") ? "w" + R.apply(rs).substring(1) : R.apply(rs);
                // scvtf usa w para 32 e x para 64
                if (parts[1].equals("w") && R.apply(rs).startsWith("x")) src = "w" + R.apply(rs).substring(1);
                return List.of(indent + "scvtf " + dst + ", " + src);
            }
            if (parts.length == 3 && (parts[1].equals("s") || parts[1].equals("d")) && (parts[2].equals("s") || parts[2].equals("d"))) {
                // fcvt.s.d f0, f0 -> fcvt d0, s0
                String[] args = rest.split(",");
                String fd = args[0].trim();
                String fs = args[1].trim();
                String dst = parts[2].equals("s") ? "s" + fd.substring(1) : "d" + fd.substring(1);
                String src = parts[1].equals("s") ? "s" + fs.substring(1) : "d" + fs.substring(1);
                return List.of(indent + "fcvt " + dst + ", " + src);
            }
        }
        if (mn.startsWith("fmv.")) {
            // fmv.w.x f0, t0  -> fmov s0, w9
            // fmv.d.x f0, t0  -> fmov d0, x9
            // fmv.x.w t0, f0  -> fmov w9, s0
            // fmv.x.d t0, f0  -> fmov x9, d0
            // também fmv.s.x / fmv.x.s aliases
            String[] parts = mn.split("\\.");
            if (parts.length == 3) {
                String p1 = parts[1]; String p2 = parts[2];
                String[] args = rest.split(",");
                if (args.length == 2) {
                    String a0 = args[0].trim();
                    String a1 = args[1].trim();
                    if ((p1.equals("w") || p1.equals("s")) && p2.equals("x")) {
                        // f0, t0  -> fmov s0, w9
                        String dst = "s" + a0.substring(1);
                        String src = "w" + R.apply(a1).substring(1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                    if (p1.equals("d") && p2.equals("x")) {
                        String dst = "d" + a0.substring(1);
                        String src = R.apply(a1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                    if (p1.equals("x") && (p2.equals("w") || p2.equals("s"))) {
                        String dst = "w" + R.apply(a0).substring(1);
                        String src = "s" + a1.substring(1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                    if (p1.equals("x") && p2.equals("d")) {
                        String dst = R.apply(a0);
                        String src = "d" + a1.substring(1);
                        return List.of(indent + "fmov " + dst + ", " + src);
                    }
                }
            }
            // fell through: try generic fmov
            if (mn.equals("fmv.w.x") || mn.equals("fmv.s.x")) {
                String[] args = rest.split(",");
                return List.of(indent + "fmov s" + args[0].trim().substring(1) + ", w" + R.apply(args[1].trim()).substring(1));
            }
        }
        if (mn.startsWith("fadd.") || mn.startsWith("fsub.") || mn.startsWith("fmul.") || mn.startsWith("fdiv.")) {
            String op = mn.substring(1, 5); // add, sub, mul, div
            String suffix = mn.substring(5); // .s ou .d
            String[] args = rest.split(",");
            String fd = args[0].trim(), fs1 = args[1].trim(), fs2 = args[2].trim();
            String rFD = (suffix.equals(".s") ? "s" : "d") + fd.substring(1);
            String rFS1 = (suffix.equals(".s") ? "s" : "d") + fs1.substring(1);
            String rFS2 = (suffix.equals(".s") ? "s" : "d") + fs2.substring(1);
            return List.of(indent + "f" + op + " " + rFD + ", " + rFS1 + ", " + rFS2);
        }
        if (mn.startsWith("feq.") || mn.startsWith("flt.") || mn.startsWith("fle.") || mn.startsWith("fgt.") || mn.startsWith("fge.")) {
            String condMap = switch (mn.substring(1, 4)) {
                case "eq." -> "eq";
                case "lt." -> "lt";
                case "le." -> "le";
                case "gt." -> "gt";
                case "ge." -> "ge";
                default -> "eq";
            };
            // mn like feq.s  -> suffix .s or .d
            String suffix = mn.substring(4); // s ou d
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String fs1 = args[1].trim(), fs2 = args[2].trim();
            String rFS1 = (suffix.equals("s") ? "s" : "d") + fs1.substring(1);
            String rFS2 = (suffix.equals("s") ? "s" : "d") + fs2.substring(1);
            List<String> out = new ArrayList<>();
            out.add(indent + "fcmp " + rFS1 + ", " + rFS2);
            out.add(indent + "cset " + rd + ", " + condMap);
            return out;
        }
        if (mn.startsWith("fmv.")) {
            // fallback já tratado acima
        }
        // instruções com fmov .x. / .s.x etc já tratadas; para fmv.x.* com rd integer
        // slt / sle / seqz / snez / sext
        if (mn.equals("slt")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs1 = R.apply(args[1].trim()), rs2 = R.apply(args[2].trim());
            return List.of(indent + "cmp " + rs1 + ", " + rs2, indent + "cset " + rd + ", lt");
        }
        if (mn.equals("sle")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs1 = R.apply(args[1].trim()), rs2 = R.apply(args[2].trim());
            return List.of(indent + "cmp " + rs1 + ", " + rs2, indent + "cset " + rd + ", le");
        }
        // genéricos
        if (mn.equals("pop")) {
            String rd = rest.trim();
            return List.of(indent + "pop " + R.apply(rd));
        }
        if (mn.equals("la")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String sym = args[1].trim();
            return List.of(indent + "adrp " + rd + ", " + sym, indent + "add " + rd + ", " + rd + ", :lo12:" + sym);
        }
        if (mn.equals("li")) {
            String[] args = rest.split(",");
            String rdRaw = args[0].trim();
            long imm = parseImm(args[1].trim());
            if (rdRaw.equals("a7")) {
                return List.of(indent + "mov x8, #" + imm);
            }
            String rd = R.apply(rdRaw);
            List<String> out = new ArrayList<>();
            for (String s2 : aarch64MovImm(rd, imm)) out.add(indent + s2);
            return out;
        }
        if (mn.equals("mv")) {
            String[] args = rest.split(",");
            return List.of(indent + "mov " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()));
        }
        if (mn.equals("add") || mn.equals("sub")) {
            String[] args = rest.split(",");
            return List.of(indent + mn + " " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("addi")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            long imm = parseImm(args[2].trim());
            // _start alignment: andi é o problema, mas addi com sp já é ok; andi sp,sp,-16 é o único andi com sp
            return aarch64AddSubImm("add", rd, rs, imm, indent);
        }
        if (mn.equals("andi") && rest.contains("sp, sp, -16")) {
            return List.of(indent + "// andi sp,sp,-16 (skipped, sp already 16-aligned)");
        }
        if (mn.equals("andi") || mn.equals("ori")) {
            String op = mn.equals("andi") ? "and" : "orr";
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            long imm = parseImm(args[2].trim());
            // sempre expande via temp x17 para garantir encodabilidade
            String tmp = "x17";
            List<String> out = new ArrayList<>();
            for (String s2 : aarch64MovImm(tmp, imm)) out.add(indent + s2);
            out.add(indent + op + " " + rd + ", " + rs + ", " + tmp);
            return out;
        }
        if (mn.equals("and") || mn.equals("or") || mn.equals("xor")) {
            String op = mn.equals("and") ? "and" : mn.equals("or") ? "orr" : "eor";
            String[] args = rest.split(",");
            return List.of(indent + op + " " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("slli")) {
            String[] args = rest.split(",");
            return List.of(indent + "lsl " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", #" + args[2].trim());
        }
        if (mn.equals("srli")) {
            String[] args = rest.split(",");
            return List.of(indent + "lsr " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", #" + args[2].trim());
        }
        if (mn.equals("srai")) {
            String[] args = rest.split(",");
            return List.of(indent + "asr " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", #" + args[2].trim());
        }
        if (mn.equals("mul")) {
            String[] args = rest.split(",");
            return List.of(indent + "mul " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("div")) {
            String[] args = rest.split(",");
            return List.of(indent + "sdiv " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()) + ", " + R.apply(args[2].trim()));
        }
        if (mn.equals("rem")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs1 = R.apply(args[1].trim()), rs2 = R.apply(args[2].trim());
            return List.of(indent + "sdiv x17, " + rs1 + ", " + rs2, indent + "msub " + rd + ", x17, " + rs2 + ", " + rs1);
        }
        if (mn.equals("neg")) {
            String[] args = rest.split(",");
            return List.of(indent + "neg " + R.apply(args[0].trim()) + ", " + R.apply(args[1].trim()));
        }
        if (mn.equals("amoadd.d")) {
            // amoadd.d rd, rs2, (rs1)  ->  ldadd rs2, rd, [rs1]
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String rs2 = R.apply(args[1].trim());
            String base = args[2].trim().replaceAll("[()]", "");
            return List.of(indent + "ldadd " + rs2 + ", " + rd + ", [" + R.apply(base) + "]");
        }
        if (mn.equals("amoswap.w")) {
            // amoswap.w rd, rs2, (rs1)  ->  swpal rs2, rd, [rs1] (spinlock)
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String rs2 = R.apply(args[1].trim());
            String base = args[2].trim().replaceAll("[()]", "");
            return List.of(indent + "swpal " + rs2 + ", " + rd + ", [" + R.apply(base) + "]");
        }
        if (mn.equals("seqz")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            return List.of(indent + "cmp " + rs + ", #0", indent + "cset " + rd + ", eq");
        }
        if (mn.equals("snez")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim()), rs = R.apply(args[1].trim());
            return List.of(indent + "cmp " + rs + ", #0", indent + "cset " + rd + ", ne");
        }
        if (mn.equals("sext.w")) {
            String[] args = rest.split(",");
            String rd = R.apply(args[0].trim());
            String rs = R.apply(args[1].trim());
            String rsW = rs.replace("x", "w");
            return List.of(indent + "sxtw " + rd + ", " + rsW);
        }
        if (mn.equals("ld") || mn.equals("lw") || mn.equals("lbu") || mn.equals("lb") || mn.equals("lh")) {
            String[] args = rest.split(",");
            String rdRaw = args[0].trim();
            String mem = args[1].trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)\\((\\w+)\\)$").matcher(mem);
            if (!m.matches()) return List.of(line);
            int off = Integer.parseInt(m.group(1));
            String base = R.apply(m.group(2));
            // sp como destino não é encodável como Rt -> usar temp
            if (rdRaw.equals("sp")) {
                String op = mn.equals("ld") ? "ldr" : mn.equals("lw") ? "ldr" : mn.equals("lbu") ? "ldrb" : mn.equals("lb") ? "ldrsb" : "ldrsh";
                String rtTmp = mn.equals("lbu") || mn.equals("lw") ? "w17" : "x17";
                List<String> out = new ArrayList<>();
                if (off >= -256 && off <= 255) {
                    String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                    out.add(indent + op + " " + rtTmp + ", " + addr);
                } else {
                    out.add(indent + "add x17, " + base + ", #" + off);
                    out.add(indent + op + " " + rtTmp + ", [x17]");
                }
                out.add(indent + "mov sp, x17");
                return out;
            }
            String rt;
            String op;
            if (mn.equals("ld")) { rt = R.apply(rdRaw); op = "ldr"; }
            else if (mn.equals("lw")) { rt = R.apply(rdRaw).replace("x", "w"); op = "ldr"; }
            else if (mn.equals("lbu")) { rt = R.apply(rdRaw).replace("x", "w"); op = "ldrb"; }
            else if (mn.equals("lb")) { rt = R.apply(rdRaw); op = "ldrsb"; }
            else { rt = R.apply(rdRaw); op = "ldrsh"; }
            if (off >= -256 && off <= 255) {
                String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                return List.of(indent + op + " " + rt + ", " + addr);
            } else {
                // fallback via temp
                List<String> out = new ArrayList<>();
                out.add(indent + "add x17, " + base + ", #" + off);
                out.add(indent + op + " " + rt + ", [x17]");
                return out;
            }
        }
        if (mn.equals("sd") || mn.equals("sw") || mn.equals("sb") || mn.equals("sh")) {
            String[] args = rest.split(",");
            String rsRaw = args[0].trim();
            String mem = args[1].trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?\\d+)\\((\\w+)\\)$").matcher(mem);
            if (!m.matches()) return List.of(line);
            int off = Integer.parseInt(m.group(1));
            String base = R.apply(m.group(2));
            // sp como fonte não é encodável como Rt -> mov temp, sp
            if (rsRaw.equals("sp")) {
                List<String> out = new ArrayList<>();
                out.add(indent + "mov x17, sp");
                String rtTmp = mn.equals("sb") || mn.equals("sw") || mn.equals("sh") ? "w17" : "x17";
                // escolher op baseado no tamanho original
                String op = mn.equals("sd") ? "str" : mn.equals("sw") ? "str" : mn.equals("sb") ? "strb" : "strh";
                // para sw/sb/sh o Rt já é w/h, mas temp é w17
                if (off >= -256 && off <= 255) {
                    String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                    out.add(indent + op + " " + rtTmp + ", " + addr);
                } else {
                    // precisa de outro temp para endereço; usar x16 para endereço
                    out.add(indent + "add x16, " + base + ", #" + off);
                    out.add(indent + op + " " + rtTmp + ", [x16]");
                }
                return out;
            }
            String rt; String op;
            if (mn.equals("sd")) { rt = R.apply(rsRaw); op = "str"; }
            else if (mn.equals("sw")) { rt = R.apply(rsRaw).replace("x", "w"); op = "str"; }
            else if (mn.equals("sb")) { rt = R.apply(rsRaw).replace("x", "w"); op = "strb"; }
            else { rt = R.apply(rsRaw).replace("x", "w"); op = "strh"; }
            if (off >= -256 && off <= 255) {
                String addr = off == 0 ? "[" + base + "]" : "[" + base + ", #" + off + "]";
                return List.of(indent + op + " " + rt + ", " + addr);
            } else {
                List<String> out = new ArrayList<>();
                out.add(indent + "add x17, " + base + ", #" + off);
                out.add(indent + op + " " + rt + ", [x17]");
                return out;
            }
        }
        if (mn.equals("beq") || mn.equals("bne") || mn.equals("blt") || mn.equals("bge") || mn.equals("bltu") || mn.equals("bgeu") || mn.equals("bgt") || mn.equals("ble")) {
            String[] args = rest.split(",");
            String rs = R.apply(args[0].trim()), rt = R.apply(args[1].trim());
            String lbl = args[2].trim();
            String cond = switch (mn) {
                case "beq" -> "eq"; case "bne" -> "ne"; case "blt" -> "lt"; case "bge" -> "ge";
                case "bltu" -> "lo"; case "bgeu" -> "hs"; case "bgt" -> "gt"; case "ble" -> "le"; default -> "eq";
            };
            return List.of(indent + "cmp " + rs + ", " + rt, indent + "b." + cond + " " + lbl);
        }
        if (mn.equals("beqz") || mn.equals("bnez") || mn.equals("bltz") || mn.equals("bgez") || mn.equals("bgtz") || mn.equals("blez")) {
            String[] args = rest.split(",");
            String rs = R.apply(args[0].trim());
            String lbl = args[1].trim();
            String cond = switch (mn) {
                case "beqz" -> "eq"; case "bnez" -> "ne"; case "bltz" -> "lt"; case "bgez" -> "ge"; case "bgtz" -> "gt"; case "blez" -> "le"; default -> "eq";
            };
            return List.of(indent + "cmp " + rs + ", #0", indent + "b." + cond + " " + lbl);
        }
        if (mn.equals("j")) return List.of(indent + "b " + rest.trim());
        if (mn.equals("jr")) return List.of(indent + "br " + R.apply(rest.trim()));
        if (mn.equals("jalr")) {
            String[] args = rest.split(",");
            String tgt = args[args.length - 1].trim();
            return List.of(indent + "blr " + R.apply(tgt));
        }
        if (mn.equals("call")) return List.of(indent + "bl " + rest.trim());
        if (mn.equals("ret")) return List.of(indent + "ret");
        if (mn.equals("ecall")) return List.of(indent + "svc #0");
        if (mn.equals("nop")) return List.of(indent + "nop");
        if (mn.equals("fence")) return List.of(indent + "dmb ish");
        // Fallback: linhas desconhecidas (ex: data com aspas) — manter como está para não quebrar o pipeline
        System.err.println("WARN translateRiscvToAarch64 UNHANDLED: " + mn + " | " + line);
        return List.of(line);
    }
