package dev.kof.compiler;

/** kof-runtime.mjs — kof.security (hash/JWT). */
final class JsRuntimeUiSecurity {
    private JsRuntimeUiSecurity() {
    }

    static final String UI_SECURITY_RUNTIME = """
            // ── kof.security (docs/security.md §5) ────────────────────

            function kofSecBytesToHex(bytes) {
                let hex = '';
                for (let i = 0; i < bytes.length; i++) {
                    hex += bytes[i].toString(16).padStart(2, '0');
                }
                return hex;
            }

            function kofSecUtf8(str) {
                const bytes = [];
                for (let i = 0; i < str.length; i++) {
                    let code = str.codePointAt(i);
                    if (code > 0xFFFF) i++;
                    if (code < 0x80) {
                        bytes.push(code);
                    } else if (code < 0x800) {
                        bytes.push(0xC0 | (code >> 6), 0x80 | (code & 0x3F));
                    } else if (code < 0x10000) {
                        bytes.push(0xE0 | (code >> 12), 0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
                    } else {
                        bytes.push(0xF0 | (code >> 18), 0x80 | ((code >> 12) & 0x3F),
                                0x80 | ((code >> 6) & 0x3F), 0x80 | (code & 0x3F));
                    }
                }
                return Uint8Array.from(bytes);
            }

            function kofSecUtf8Decode(bytes) {
                let out = '';
                for (let i = 0; i < bytes.length;) {
                    const b = bytes[i];
                    if (b < 0x80) {
                        out += String.fromCharCode(b);
                        i += 1;
                    } else if (b < 0xE0) {
                        out += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F));
                        i += 2;
                    } else if (b < 0xF0) {
                        out += String.fromCharCode(((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F));
                        i += 3;
                    } else {
                        const cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3F) << 12)
                                | ((bytes[i + 2] & 0x3F) << 6) | (bytes[i + 3] & 0x3F);
                        out += String.fromCodePoint(cp);
                        i += 4;
                    }
                }
                return out;
            }

            function kofSecSha256Bytes(msg) {
                const K = [
                    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
                    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
                    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
                    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
                    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
                    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
                    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
                    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
                ];
                const len = msg.length;
                const bitLen = len * 8;
                const padded = new Uint8Array(((len + 8) >>> 6 << 6) + 64);
                padded.set(msg);
                padded[len] = 0x80;
                const view = new DataView(padded.buffer);
                view.setUint32(padded.length - 8, Math.floor(bitLen / 0x100000000));
                view.setUint32(padded.length - 4, bitLen >>> 0);
                let h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
                let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;
                const w = new Uint32Array(64);
                for (let off = 0; off < padded.length; off += 64) {
                    for (let i = 0; i < 16; i++) {
                        w[i] = view.getUint32(off + i * 4);
                    }
                    for (let i = 16; i < 64; i++) {
                        const s0 = ((w[i - 15] >>> 7) | (w[i - 15] << 25)) ^ ((w[i - 15] >>> 18) | (w[i - 15] << 14)) ^ (w[i - 15] >>> 3);
                        const s1 = ((w[i - 2] >>> 17) | (w[i - 2] << 15)) ^ ((w[i - 2] >>> 19) | (w[i - 2] << 13)) ^ (w[i - 2] >>> 10);
                        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
                    }
                    let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
                    for (let i = 0; i < 64; i++) {
                        const S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
                        const ch = (e & f) ^ (~e & g);
                        const t1 = (h + S1 + ch + K[i] + w[i]) >>> 0;
                        const S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
                        const maj = (a & b) ^ (a & c) ^ (b & c);
                        const t2 = (S0 + maj) >>> 0;
                        h = g; g = f; f = e; e = (d + t1) >>> 0; d = c; c = b; b = a; a = (t1 + t2) >>> 0;
                    }
                    h0 = (h0 + a) >>> 0; h1 = (h1 + b) >>> 0; h2 = (h2 + c) >>> 0; h3 = (h3 + d) >>> 0;
                    h4 = (h4 + e) >>> 0; h5 = (h5 + f) >>> 0; h6 = (h6 + g) >>> 0; h7 = (h7 + h) >>> 0;
                }
                const out = new Uint8Array(32);
                const outView = new DataView(out.buffer);
                outView.setUint32(0, h0); outView.setUint32(4, h1); outView.setUint32(8, h2); outView.setUint32(12, h3);
                outView.setUint32(16, h4); outView.setUint32(20, h5); outView.setUint32(24, h6); outView.setUint32(28, h7);
                return out;
            }

            function kofSecHmacRaw(keyBytes, dataBytes) {
                const blockSize = 64;
                let key = keyBytes;
                if (key.length > blockSize) {
                    key = kofSecSha256Bytes(key);
                }
                const ipad = new Uint8Array(blockSize);
                const opad = new Uint8Array(blockSize);
                for (let i = 0; i < blockSize; i++) {
                    ipad[i] = (key[i] || 0) ^ 0x36;
                    opad[i] = (key[i] || 0) ^ 0x5c;
                }
                const inner = new Uint8Array(ipad.length + dataBytes.length);
                inner.set(ipad);
                inner.set(dataBytes, ipad.length);
                const innerHash = kofSecSha256Bytes(inner);
                const outer = new Uint8Array(opad.length + innerHash.length);
                outer.set(opad);
                outer.set(innerHash, opad.length);
                return kofSecSha256Bytes(outer);
            }

            export function kofSecSha256(data) {
                return kofSecBytesToHex(kofSecSha256Bytes(kofSecUtf8(data)));
            }

            export function kofSecSha512(data) {
                return kofSecBytesToHex(kofSecSha512Bytes(kofSecUtf8(data)));
            }

            function kofSecSha512Bytes(msg) {
                return kofSecSha512Impl(msg);
            }

            function kofSecSha512Impl(msg) {
                // compact re-entry point so hmac can reuse it
                const K = [
                    0x428a2f98d728ae22n, 0x7137449123ef65cdn, 0xb5c0fbcfec4d3b2fn, 0xe9b5dba58189dbbcn,
                    0x3956c25bf348b538n, 0x59f111f1b605d019n, 0x923f82a4af194f9bn, 0xab1c5ed5da6d8118n,
                    0xd807aa98a3030242n, 0x12835b0145706fben, 0x243185be4ee4b28cn, 0x550c7dc3d5ffb4e2n,
                    0x72be5d74f27b896fn, 0x80deb1fe3b1696b1n, 0x9bdc06a725c71235n, 0xc19bf174cf692694n,
                    0xe49b69c19ef14ad2n, 0xefbe4786384f25e3n, 0x0fc19dc68b8cd5b5n, 0x240ca1cc77ac9c65n,
                    0x2de92c6f592b0275n, 0x4a7484aa6ea6e483n, 0x5cb0a9dcbd41fbd4n, 0x76f988da831153b5n,
                    0x983e5152ee66dfabn, 0xa831c66d2db43210n, 0xb00327c898fb213fn, 0xbf597fc7beef0ee4n,
                    0xc6e00bf33da88fc2n, 0xd5a79147930aa725n, 0x06ca6351e003826fn, 0x142929670a0e6e70n,
                    0x27b70a8546d22ffcn, 0x2e1b21385c26c926n, 0x4d2c6dfc5ac42aedn, 0x53380d139d95b3dfn,
                    0x650a73548baf63den, 0x766a0abb3c77b2a8n, 0x81c2c92e47edaee6n, 0x92722c851482353bn,
                    0xa2bfe8a14cf10364n, 0xa81a664bbc423001n, 0xc24b8b70d0f89791n, 0xc76c51a30654be30n,
                    0xd192e819d6ef5218n, 0xd69906245565a910n, 0xf40e35855771202an, 0x106aa07032bbd1b8n,
                    0x19a4c116b8d2d0c8n, 0x1e376c085141ab53n, 0x2748774cdf8eeb99n, 0x34b0bcb5e19b48a8n,
                    0x391c0cb3c5c95a63n, 0x4ed8aa4ae3418acbn, 0x5b9cca4f7763e373n, 0x682e6ff3d6b2b8a3n,
                    0x748f82ee5defb2fcn, 0x78a5636f43172f60n, 0x84c87814a1f0ab72n, 0x8cc702081a6439ecn,
                    0x90befffa23631e28n, 0xa4506cebde82bde9n, 0xbef9a3f7b2c67915n, 0xc67178f2e372532bn,
                    0xca273eceea26619cn, 0xd186b8c721c0c207n, 0xeada7dd6cde0eb1en, 0xf57d4f7fee6ed178n,
                    0x06f067aa72176fban, 0x0a637dc5a2c898a6n, 0x113f9804bef90daen, 0x1b710b35131c471bn,
                    0x28db77f523047d84n, 0x32caab7b40c72493n, 0x3c9ebe0a15c9bebcn, 0x431d67c49c100d4cn,
                    0x4cc5d4becb3e42b6n, 0x597f299cfc657e2an, 0x5fcb6fab3ad6faecn, 0x6c44198c4a475817n
                ];
                const len = msg.length;
                const bitLen = BigInt(len) * 8n;
                const paddedLen = (((len + 16) >>> 7 << 7) + 128);
                const padded = new Uint8Array(paddedLen);
                padded.set(msg);
                padded[len] = 0x80;
                const view = new DataView(padded.buffer);
                view.setBigUint64(paddedLen - 8, bitLen, false);
                let h0 = 0x6a09e667f3bcc908n, h1 = 0xbb67ae8584caa73bn, h2 = 0x3c6ef372fe94f82bn, h3 = 0xa54ff53a5f1d36f1n;
                let h4 = 0x510e527fade682d1n, h5 = 0x9b05688c2b3e6c1fn, h6 = 0x1f83d9abfb41bd6bn, h7 = 0x5be0cd19137e2179n;
                const w = new Array(80);
                for (let off = 0; off < padded.length; off += 128) {
                    for (let i = 0; i < 16; i++) {
                        w[i] = view.getBigUint64(off + i * 8, false);
                    }
                    for (let i = 16; i < 80; i++) {
                        const s0 = ((w[i - 15] >> 1n) | (w[i - 15] << 63n)) ^ ((w[i - 15] >> 8n) | (w[i - 15] << 56n)) ^ (w[i - 15] >> 7n);
                        const s1 = ((w[i - 2] >> 19n) | (w[i - 2] << 45n)) ^ ((w[i - 2] >> 61n) | (w[i - 2] << 3n)) ^ (w[i - 2] >> 6n);
                        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) & 0xffffffffffffffffn;
                    }
                    let a = h0, b = h1, c = h2, d = h3, e = h4, f = h5, g = h6, h = h7;
                    for (let i = 0; i < 80; i++) {
                        const S1 = ((e >> 14n) | (e << 50n)) ^ ((e >> 18n) | (e << 46n)) ^ ((e >> 41n) | (e << 23n));
                        const ch = (e & f) ^ (~e & g);
                        const t1 = (h + S1 + ch + K[i] + w[i]) & 0xffffffffffffffffn;
                        const S0 = ((a >> 28n) | (a << 36n)) ^ ((a >> 34n) | (a << 30n)) ^ ((a >> 39n) | (a << 25n));
                        const maj = (a & b) ^ (a & c) ^ (b & c);
                        const t2 = (S0 + maj) & 0xffffffffffffffffn;
                        h = g; g = f; f = e; e = (d + t1) & 0xffffffffffffffffn; d = c; c = b; b = a; a = (t1 + t2) & 0xffffffffffffffffn;
                    }
                    h0 = (h0 + a) & 0xffffffffffffffffn; h1 = (h1 + b) & 0xffffffffffffffffn; h2 = (h2 + c) & 0xffffffffffffffffn; h3 = (h3 + d) & 0xffffffffffffffffn;
                    h4 = (h4 + e) & 0xffffffffffffffffn; h5 = (h5 + f) & 0xffffffffffffffffn; h6 = (h6 + g) & 0xffffffffffffffffn; h7 = (h7 + h) & 0xffffffffffffffffn;
                }
                const out = new Uint8Array(64);
                const outView = new DataView(out.buffer);
                const hs = [h0, h1, h2, h3, h4, h5, h6, h7];
                for (let i = 0; i < 8; i++) outView.setBigUint64(i * 8, hs[i], false);
                return out;
            }

            export function kofSecHmacSha256(key, data) {
                return kofSecBytesToHex(kofSecHmacRaw(kofSecUtf8(key), kofSecUtf8(data)));
            }

            export function kofSecRandomHex(bytes) {
                if (bytes < 0 || bytes > 4096) {
                    throw new Error("invalid length: " + bytes);
                }
                return kof_platform.randomBytesHex(bytes);
            }

            export function kofSecRandomInt(bound) {
                if (bound <= 0) {
                    throw new Error("bound must be positive");
                }
                return kof_platform.randomInt(bound);
            }

            export function kofSecConstantTimeEquals(a, b) {
                if (a === null || b === null) {
                    return a === b ? 1 : 0;
                }
                const ab = kofSecUtf8(String(a));
                const bb = kofSecUtf8(String(b));
                if (ab.length !== bb.length) {
                    return 0;
                }
                let diff = 0;
                for (let i = 0; i < ab.length; i++) {
                    diff |= ab[i] ^ bb[i];
                }
                return diff === 0 ? 1 : 0;
            }

            export function kofSecRedact(value) {
                if (value === null) {
                    return null;
                }
                if (value.length <= 8) {
                    return "********";
                }
                return value.substring(0, 4) + "********" + value.substring(value.length - 4);
            }

            export function kofSecSecretGet(name) {
                return kof_platform.getenv(name);
            }

            export function kofSecSecretGetDefault(name, fallback) {
                const v = kof_platform.getenv(name);
                return v === null || v === undefined ? fallback : v;
            }

            // password hashing — pbkdf2$sha256$<iterations>$<saltB64>$<hashB64>

            function kofSecConcatBytes(a, b) {
                const out = new Uint8Array(a.length + b.length);
                out.set(a);
                out.set(b, a.length);
                return out;
            }

            function kofSecPbkdf2Raw(passwordBytes, saltBytes, iterations, dkLen) {
                const block1 = kofSecConcatBytes(saltBytes, new Uint8Array([0, 0, 0, 1]));
                let u = kofSecHmacRaw(passwordBytes, block1);
                const t = new Uint8Array(u);
                for (let i = 1; i < iterations; i++) {
                    u = kofSecHmacRaw(passwordBytes, u);
                    for (let j = 0; j < t.length; j++) {
                        t[j] ^= u[j];
                    }
                }
                return t;
            }

            export function kofSecPasswordHash(password) {
                const saltHex = kof_platform.randomBytesHex(16);
                const saltBytes = kofSecHexToBytes(saltHex);
                // The embedded runner delegates PBKDF2 to the platform (fast);
                // standalone JS engines fall back to the pure-JS implementation.
                const dkHex = (typeof kof_platform.pbkdf2Hex === "function")
                        ? kof_platform.pbkdf2Hex(password, saltHex, 600000)
                        : kofSecBytesToHex(kofSecPbkdf2Raw(kofSecUtf8(password), saltBytes, 600000, 32));
                const dk = kofSecHexToBytes(dkHex);
                return "pbkdf2$sha256$600000$" + kofSecB64Encode(saltBytes) + "$" + kofSecB64Encode(dk);
            }

            function kofSecHexToBytes(hex) {
                const out = new Uint8Array(hex.length / 2);
                for (let i = 0; i < out.length; i++) {
                    out[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                return out;
            }

            export function kofSecPasswordVerify(password, hash) {
                if (hash === null || hash === undefined) {
                    return 0;
                }
                const parts = hash.split("$");
                if (parts.length !== 5 || parts[0] !== "pbkdf2" || parts[1] !== "sha256") {
                    return 0;
                }
                try {
                    const iterations = parseInt(parts[2], 10);
                    const saltBytes = kofSecB64Decode(parts[3]);
                    const expected = kofSecB64Decode(parts[4]);
                    const saltHex = kofSecBytesToHex(saltBytes);
                    const actualHex = (typeof kof_platform.pbkdf2Hex === "function")
                            ? kof_platform.pbkdf2Hex(password, saltHex, iterations)
                            : kofSecBytesToHex(kofSecPbkdf2Raw(kofSecUtf8(password), saltBytes, iterations, expected.length));
                    const actual = kofSecHexToBytes(actualHex);
                    let diff = 0;
                    for (let i = 0; i < expected.length; i++) {
                        diff |= expected[i] ^ actual[i];
                    }
                    return diff === 0 ? 1 : 0;
                } catch (e) {
                    return 0;
                }
            }

            export function kofSecPasswordNeedsRehash(hash) {
                if (hash === null || hash === undefined) {
                    return 1;
                }
                const parts = hash.split("$");
                if (parts.length !== 5 || parts[0] !== "pbkdf2" || parts[1] !== "sha256") {
                    return 1;
                }
                const iterations = parseInt(parts[2], 10);
                return Number.isNaN(iterations) || iterations < 600000 ? 1 : 0;
            }

            // JWT — HS256 only; the algorithm is never taken from the token.

            const KOF_SEC_B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

            function kofSecB64Encode(bytes) {
                let out = '';
                for (let i = 0; i < bytes.length; i += 3) {
                    const b0 = bytes[i];
                    const b1 = i + 1 < bytes.length ? bytes[i + 1] : -1;
                    const b2 = i + 2 < bytes.length ? bytes[i + 2] : -1;
                    out += KOF_SEC_B64_CHARS[b0 >> 2];
                    out += KOF_SEC_B64_CHARS[((b0 & 3) << 4) | (b1 >= 0 ? b1 >> 4 : 0)];
                    out += b1 >= 0 ? KOF_SEC_B64_CHARS[((b1 & 15) << 2) | (b2 >= 0 ? b2 >> 6 : 0)] : '=';
                    out += b2 >= 0 ? KOF_SEC_B64_CHARS[b2 & 63] : '=';
                }
                return out;
            }

            // strict=true rejeita tamanho não múltiplo de 4 (paridade java.util.Base64).
            // strict=false (default) tolera b64-url sem padding (JWT).
            function kofSecB64Decode(s, strict) {
                if (strict && s.length % 4 !== 0) throw new Error("invalid base64 length");
                const out = [];
                let buffer = 0;
                let bits = 0;
                for (let i = 0; i < s.length; i++) {
                    const c = s.charAt(i);
                    if (c === '=') break;
                    const v = KOF_SEC_B64_CHARS.indexOf(c);
                    if (v < 0) continue;
                    buffer = (buffer << 6) | v;
                    bits += 6;
                    if (bits >= 8) {
                        bits -= 8;
                        out.push((buffer >> bits) & 0xFF);
                    }
                }
                return Uint8Array.from(out);
            }

            function kofSecB64Url(bytes) {
                let b64 = kofSecB64Encode(bytes);
                b64 = b64.split("+").join("-").split("/").join("_");
                return b64.indexOf("=") >= 0 ? b64.substring(0, b64.indexOf("=")) : b64;
            }

            function kofSecB64UrlDecode(s) {
                const b64 = s.split("-").join("+").split("_").join("/");
                return kofSecB64Decode(b64);
            }

            export function kofSecJwtSecret() {
                const env = kof_platform.getenv("KOF_JWT_SECRET");
                if (env !== null && env !== undefined && env !== "") {
                    return env;
                }
                return kof_platform.randomBytesHex(32);
            }

            export function kofSecJwtCreate(claimsJson, secret) {
                return kofSecJwtCreateTtl(claimsJson, secret, 3600);
            }

            export function kofSecJwtCreateTtl(claimsJson, secret, ttlSeconds) {
                const parsed = JSON.parse(claimsJson);
                if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
                    throw new Error("JWT claims must be a JSON object");
                }
                const now = Math.floor(Date.now() / 1000);
                parsed.iat = now;
                parsed.exp = now + ttlSeconds;
                const headerB64 = kofSecB64Url(kofSecUtf8('{"alg":"HS256","typ":"JWT"}'));
                const payloadB64 = kofSecB64Url(kofSecUtf8(JSON.stringify(parsed)));
                return headerB64 + "." + payloadB64 + "." + kofSecJwtSign(headerB64, payloadB64, secret);
            }

            function kofSecJwtSign(headerB64, payloadB64, secret) {
                return kofSecB64Url(kofSecHmacRaw(kofSecUtf8(secret),
                        kofSecUtf8(headerB64 + "." + payloadB64)));
            }

            export function kofSecJwtVerify(token, secret) {
                return kofSecJwtVerifyIssAud(token, secret, null, null);
            }

            export function kofSecJwtVerifyIssAud(token, secret, issuer, audience) {
                if (token === null || token === undefined || secret === null || secret === undefined) {
                    throw new Error("invalid token or secret");
                }
                const parts = token.split(".");
                if (parts.length !== 3) {
                    throw new Error("malformed token");
                }
                const headerJson = kofSecUtf8Decode(kofSecB64UrlDecode(parts[0]));
                if (!headerJson.includes('"HS256"')) {
                    throw new Error("algorithm not allowed");
                }
                const expected = kofSecJwtSign(parts[0], parts[1], secret);
                if (kofSecConstantTimeEquals(expected, parts[2]) !== 1) {
                    throw new Error("invalid signature");
                }
                const payloadJson = kofSecUtf8Decode(kofSecB64UrlDecode(parts[1]));
                const claims = JSON.parse(payloadJson);
                if (typeof claims !== "object" || claims === null) {
                    throw new Error("invalid payload");
                }
                if (typeof claims.exp === "number" && claims.exp * 1000 <= Date.now()) {
                    throw new Error("token expired");
                }
                if (issuer !== null && claims.iss !== issuer) {
                    throw new Error("issuer mismatch");
                }
                if (audience !== null && claims.aud !== audience) {
                    throw new Error("audience mismatch");
                }
                return payloadJson;
            }

            """;

}
