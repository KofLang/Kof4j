package dev.kof.compiler.js;

/**
 * Runtime JS (STDLIB S1/S2a/S2b/S2b.2/S2b.3/S2b.4) — fatia com os exports
 * kofMath* e kofStrings*. Extraída de JsRuntimeUiCrypto (gate ≤500 + limite
 * de 64KB do text block); ordem de append no artifact é livre (ESM).
 */
final class JsRuntimeUiStdlib {

    private JsRuntimeUiStdlib() {}

    static final String STDLIB_RUNTIME = """
            // ── kof.math (STDLIB S1) — Int-only, paridade JVM/Native ─────
            export function kofMathAbs(v) { return v < 0 ? -v : v; }
            export function kofMathSign(v) { return v > 0 ? 1 : (v < 0 ? -1 : 0); }
            export function kofMathClamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }
            export function kofMathMin(a, b) { return a <= b ? a : b; }
            export function kofMathMax(a, b) { return a >= b ? a : b; }
            export function kofMathIsEven(v) { return (v & 1) === 0 ? 1 : 0; }
            export function kofMathIsOdd(v) { return (v & 1) !== 0 ? 1 : 0; }
            export function kofMathIsPositive(v) { return v > 0 ? 1 : 0; }
            export function kofMathIsNegative(v) { return v < 0 ? 1 : 0; }
            export function kofMathIsZero(v) { return v === 0 ? 1 : 0; }

            // ── kof.strings (STDLIB S2a) — predicados de char ───────────
            export function kofStringsIsAlpha(v) {
                if (v == null || v.length === 0) return 0;
                return /^[A-Za-z]+$/.test(v) ? 1 : 0;
            }
            export function kofStringsIsNumeric(v) {
                if (v == null || v.length === 0) return 0;
                return /^[0-9]+$/.test(v) ? 1 : 0;
            }
            export function kofStringsIsAlphaNumeric(v) {
                if (v == null || v.length === 0) return 0;
                return /^[A-Za-z0-9]+$/.test(v) ? 1 : 0;
            }
            export function kofStringsIsAscii(v) {
                if (v == null || v.length === 0) return 0;
                return /^[\\x00-\\x7F]+$/.test(v) ? 1 : 0;
            }
            export function kofStringsIsUpperCase(v) {
                if (v == null || v.length === 0) return 0;
                if (/[a-z]/.test(v)) return 0;
                return /[A-Z]/.test(v) ? 1 : 0;
            }
            export function kofStringsIsLowerCase(v) {
                if (v == null || v.length === 0) return 0;
                if (/[A-Z]/.test(v)) return 0;
                return /[a-z]/.test(v) ? 1 : 0;
            }
            export function kofStringsCount(v, sub) {
                if (v == null || sub == null || sub.length === 0 || v.length === 0) return 0;
                let n = 0, i = 0;
                while ((i = v.indexOf(sub, i)) >= 0) { n++; i += sub.length; }
                return n;
            }
            // capitalize: ASCII-only (paridade travada com Native byte-1).
            export function kofStringsCapitalize(v) {
                if (v == null || v.length === 0) return v;
                const c = v.charCodeAt(0);
                if (c >= 97 && c <= 122) return String.fromCharCode(c - 32) + v.slice(1);
                return v;
            }
            export function kofStringsReverse(v) {
                if (v == null || v.length === 0) return v;
                return [...v].reverse().join('');
            }
            export function kofStringsRepeat(v, n) {
                if (v == null || v.length === 0 || n <= 0) return "";
                return v.repeat(n);
            }
            export function kofStringsTruncate(v, n) {
                if (v == null) return v;
                if (n <= 0) return "";
                return v.length <= n ? v : v.slice(0, n);
            }
            export function kofStringsPadLeft(v, n, pad) {
                if (v == null) return v;
                if (pad == null || pad.length === 0 || v.length >= n) return v;
                const p = pad.charAt(0);
                while (v.length < n) v = p + v;
                return v;
            }
            export function kofStringsPadRight(v, n, pad) {
                if (v == null) return v;
                if (pad == null || pad.length === 0 || v.length >= n) return v;
                const p = pad.charAt(0);
                while (v.length < n) v = v + p;
                return v;
            }
            function kofStringsJoinWords(v, mode) {
                if (v == null) return v;
                let out = "", wc = 0, prev = -1;
                for (let i = 0; i < v.length; i++) {
                    const c = v.charCodeAt(i);
                    const upper = c >= 65 && c <= 90;
                    const alnum = upper || (c >= 97 && c <= 122) || (c >= 48 && c <= 57);
                    if (!alnum) { prev = -1; continue; }
                    let nw = false;
                    if (prev === -1) nw = true;
                    else if (upper) {
                        const pl = prev >= 97 && prev <= 122;
                        const pd = prev >= 48 && prev <= 57;
                        const pu = prev >= 65 && prev <= 90;
                        let nl = false;
                        if (i + 1 < v.length) {
                            const nx = v.charCodeAt(i + 1);
                            nl = nx >= 97 && nx <= 122;
                        }
                        nw = pl || pd || (pu && nl);
                    }
                    const low = ch => (ch >= 65 && ch <= 90) ? ch + 32 : ch;
                    const up = ch => (ch >= 97 && ch <= 122) ? ch - 32 : ch;
                    if (nw) {
                        if (out.length > 0 && mode >= 2) out += String.fromCharCode(mode === 2 ? 95 : 45);
                        const cap = mode === 1 || (mode === 0 && wc > 0);
                        out += String.fromCharCode(cap ? up(c) : low(c));
                        wc++;
                    } else {
                        out += String.fromCharCode(low(c));
                    }
                    prev = c;
                }
                return out;
            }
            export function kofStringsToCamelCase(v) { return kofStringsJoinWords(v, 0); }
            export function kofStringsToPascalCase(v) { return kofStringsJoinWords(v, 1); }
            export function kofStringsToSnakeCase(v) { return kofStringsJoinWords(v, 2); }
            export function kofStringsToKebabCase(v) { return kofStringsJoinWords(v, 3); }
            export function kofStringsSlugify(v) { return kofStringsJoinWords(v, 4); }

    """;
}
