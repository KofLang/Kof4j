package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pilha valor+tipo do caminho linear (Unit B — Fase C/E). A guarda que impede
 * drift de tipo (lição bug 62: opcode "linear" ainda pode driftar). Cada valor
 * carrega o tipo JVM de quem o EMPURROU — o opcode de load/const é a fonte
 * (o verificador da JVM garante consistência; bytecode mal-typed não executa).
 * Ops binários/unários só emitem quando os operandos batem com o opcode; o
 * return só emite quando o tipo bate com o descriptor do método. Tipo
 * desconhecido → recusar → stub UNKNOWN honesto (R6).
 */
final class BytecodeTypes {

    private BytecodeTypes() {
    }

    /** Pilha de {expressão, tipo JVM}. null = tipo desconhecido (recusa ops). */
    static final class TStack {
        private record E(String expr, String type) {}
        private final Deque<E> d = new ArrayDeque<>();

        int size() { return d.size(); }
        boolean isEmpty() { return d.isEmpty(); }
        void push(String expr, String type) { d.push(new E(expr, type)); }
        E pop() { return d.isEmpty() ? null : d.pop(); }
        String topExpr() { return d.isEmpty() ? null : d.peek().expr(); }
        String topType() { return d.isEmpty() ? null : d.peek().type(); }
        void dup() { if (!d.isEmpty()) d.push(d.peek()); }

        String popExpr() { E x = pop(); return x == null ? null : x.expr(); }

        /** Pop n args → "a, b, ..." (ordem original) ou null se faltar. */
        String args(int n) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                E x = pop();
                if (x == null) return null;
                out.add(0, x.expr());
            }
            return String.join(", ", out);
        }

        /** Return guard: expr se o topo é `expected` (ou tipo desconhecido — nunca
         * piora o que hoje passa); null se pilha vazia OU tipo concreto divergente
         * (drift bug 62). */
        String retTyped(String expected) {
            E x = pop();
            if (x == null) return null;
            if (x.type() != null && !x.type().equals(expected)) return null;
            return x.expr();
        }

        /** Binário com AMBOS operandos do tipo `t` → `(a op b)`. */
        boolean bin(String op, String t) {
            if (d.size() < 2) return false;
            E b = d.pop(), a = d.pop();
            if (!t.equals(a.type()) || !t.equals(b.type())) return false;
            d.push(new E("(" + a.expr() + " " + op + " " + b.expr() + ")", t));
            return true;
        }

        /** Unário: pop exige `in`, empurra String.format(fmt, e) com tipo `out`. */
        boolean mono(String fmt, String in, String out) {
            E x = pop();
            if (x == null) return false;
            if (in != null && !in.equals(x.type())) return false;
            d.push(new E(String.format(fmt, x.expr()), out));
            return true;
        }
    }
}
