package dev.kof.compiler.js;

/**
 * Runtime JS do kof.random (STDLIB S10/S10a/S10b).
 * Entropia = kof_platform (SecureRandom no runner Node; browser cai em
 * crypto.getRandomValues). Extraído de JsRuntimeUiStdlib (gate ≤500, regra 7
 * — nome por responsabilidade, sem sufixo).
 */
public final class JsRuntimeUiRandom {

    private JsRuntimeUiRandom() {}

    static final String RANDOM_RUNTIME = """

            // kof.random (STDLIB S10a) — face não-críptográfica. Entropia do
            // SO: kof_platform (SecureRandom no runner Node); no browser o
            // proxy lança, cai em crypto.getRandomValues. bound<=0 -> 0
            // (leniente — contrato do plano; security.randomInt lança).
            function kofRandByte() {
                try {
                    const h = kof_platform.randomBytesHex(1);
                    if (typeof h === "string" && h.length === 2) return parseInt(h, 16);
                } catch (e) { /* browser: proxy — cai no crypto abaixo */ }
                return crypto.getRandomValues(new Uint8Array(1))[0];
            }
            export function kofRandomInt(bound) {
                if (bound <= 0) return 0;
                // rejection: aceita v < 2^32 - (2^32 mod bound) => v%bound uniforme
                const limit = 4294967296 - (4294967296 % bound);
                for (;;) {
                    const v = (kofRandByte() * 16777216) + (kofRandByte() * 65536)
                            + (kofRandByte() * 256) + kofRandByte();
                    if (v < limit) return v % bound;
                }
            }
            export function kofRandomBool() {
                return kofRandByte() & 1;
            }
            // S10b: n chars, cada um uniforme do alfabeto (reusa kofRandomInt).
            // Borda leniente: n<=0 OU alfabeto nulo/vazio => "".
            export function kofRandomString(n, alphabet) {
                if (n <= 0 || alphabet == null || alphabet.length === 0) return "";
                let out = "";
                for (let i = 0; i < n; i++) out += alphabet.charAt(kofRandomInt(alphabet.length));
                return out;
            }
            // ── kof.random face S10 (main, merge 10/09) — mesma entropia ──
            // [0,1): mantissa 53 bits (32b hi * 2^21 + 24b lo / 8) % 2^53 / 2^53;
            // nunca 1.0. Sem estouro de precisão (hi*2^21 < 2^53).
            export function kofRandomDouble() {
                let hi = 0;
                for (let i = 0; i < 4; i++) hi = hi * 256 + kofRandByte();
                let lo = 0;
                for (let i = 0; i < 3; i++) lo = lo * 256 + kofRandByte();
                const v = (hi * 2 ** 21 + lo * 2 ** -3) % 2 ** 53;
                return v / 2 ** 53;
            }
            export function kofRandomBoolean() {
                return kofRandomBool();
            }
            // 2n dígito hex minúsculo; n<=0 => null (mesmo contrato do hex
            // x86 kof_sec_random_hex / riscv B27).
            export function kofRandomHex(n) {
                if (n == null || n <= 0) return null;
                const hexl = "0123456789abcdef";
                let out = "";
                for (let i = 0; i < n; i++) {
                    const b = kofRandByte();
                    out += hexl.charAt(b >> 4) + hexl.charAt(b & 15);
                }
                return out;
            }
            """;
}
