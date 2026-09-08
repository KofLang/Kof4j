package dev.kof.compiler.jvm;

import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Diagnóstico rico de crash interno do backend JVM (COMP002 "frame crash").
 * Quando o ASM estoura em COMPUTE_FRAMES (visitMaxs) — stack underflow,
 * tipo incompatível, etc — o desenvolvedor precisa reconstruir a cadeia
 * "código Kof → IR → operação ASM" sem flags. Este helper formata:
 * arquivo:linha do último construto Kof conhecido, a fase do compilador,
 * o tail do IR e o tail do bytecode ASM. O texto vai para a mensagem do
 * diagnostic COMP002 (R6: nunca um crash mudo).
 */
final class JvmFrameDiagnostics {

    private JvmFrameDiagnostics() {}

    private static final int IR_TAIL = 12;
    private static final int ASM_TAIL = 18;

    static String describe(List<KofOperation> ops,
                           Map<KofOperation, SourcePosition> debugPositions,
                           java.util.function.BiConsumer<MethodVisitor, KofOperation> reEmit,
                           Throwable asmError) {
        StringBuilder sb = new StringBuilder();
        SourcePosition last = lastPosition(ops, debugPositions);
        if (last != null) {
            sb.append("último construto Kof: ").append(last.file())
              .append(':').append(last.line()).append(':').append(last.column());
        } else {
            sb.append("sem posição Kof no IR");
        }
        sb.append("\n  fase: JVM backend / ASM COMPUTE_FRAMES (visitMaxs)");
        sb.append("\n  erro ASM: ").append(asmError.getClass().getSimpleName());
        if (asmError.getMessage() != null) {
            sb.append(": ").append(firstLine(asmError.getMessage()));
        }
        appendIrTail(sb, ops, debugPositions);
        appendAsmTail(sb, ops, reEmit);
        return sb.toString();
    }

    private static SourcePosition lastPosition(List<KofOperation> ops,
                                               Map<KofOperation, SourcePosition> debugPositions) {
        SourcePosition last = null;
        for (KofOperation op : ops) {
            SourcePosition p = debugPositions.get(op);
            if (p != null) last = p;
        }
        return last;
    }

    private static void appendIrTail(StringBuilder sb, List<KofOperation> ops,
                                     Map<KofOperation, SourcePosition> debugPositions) {
        sb.append("\n  IR (últimas ").append(IR_TAIL).append(" ops):");
        int from = Math.max(0, ops.size() - IR_TAIL);
        for (int i = from; i < ops.size(); i++) {
            KofOperation op = ops.get(i);
            sb.append("\n    [").append(i).append("] ").append(summarize(op));
            SourcePosition p = debugPositions.get(op);
            if (p != null) sb.append("  @").append(p.line()).append(':').append(p.column());
        }
    }

    private static void appendAsmTail(StringBuilder sb, List<KofOperation> ops,
                                      java.util.function.BiConsumer<MethodVisitor, KofOperation> reEmit) {
        try {
            StringWriter sw = new StringWriter();
            Printer pr = new Textifier();
            MethodVisitor dump = new TraceMethodVisitor(pr);
            dump.visitCode();
            for (KofOperation op : ops) reEmit.accept(dump, op);
            pr.print(new java.io.PrintWriter(sw, true));
            String[] lines = sw.toString().split("\n");
            int from = Math.max(0, lines.length - ASM_TAIL);
            sb.append("\n  bytecode ASM (últimas ").append(ASM_TAIL).append(" linhas):");
            for (int i = from; i < lines.length; i++) {
                sb.append("\n    ").append(lines[i].strip());
            }
        } catch (Throwable t) {
            sb.append("\n  (bytecode ASM indisponível: ").append(t.getClass().getSimpleName()).append(')');
        }
    }

    private static String summarize(KofOperation op) {
        String s = op.toString();
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl);
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }
}
