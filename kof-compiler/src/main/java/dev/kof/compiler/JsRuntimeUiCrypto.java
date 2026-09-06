package dev.kof.compiler;

/** kof-runtime.mjs — AES-GCM + validation + observability + G9. */
final class JsRuntimeUiCrypto {
    private JsRuntimeUiCrypto() {
    }

    static final String UI_CRYPTO_RUNTIME = """
            // ── AES-256-GCM (SECN002 fechado 01/09) ───────────────────
            // Puro JS (roda no GraalJS e no browser). Formato idêntico ao
            // JVM/Native: aesgcm$<ivB64>$<ct||tagB64>, key 32 bytes (64 hex),
            // IV 12 bytes aleatórios, tag 128-bit. S-box FIPS 197; GHASH/CTR
            // NIST SP 800-38D. Validado byte-a-byte contra node:crypto e o
            // vetor NIST (KofSecurityTest.aesGcmJsRoundTrip + paridade JVM).

            const KOF_SEC_AES_SBOX = new Uint8Array([
                0x63,0x7c,0x77,0x7b,0xf2,0x6b,0x6f,0xc5,0x30,0x01,0x67,0x2b,0xfe,0xd7,0xab,0x76,
                0xca,0x82,0xc9,0x7d,0xfa,0x59,0x47,0xf0,0xad,0xd4,0xa2,0xaf,0x9c,0xa4,0x72,0xc0,
                0xb7,0xfd,0x93,0x26,0x36,0x3f,0xf7,0xcc,0x34,0xa5,0xe5,0xf1,0x71,0xd8,0x31,0x15,
                0x04,0xc7,0x23,0xc3,0x18,0x96,0x05,0x9a,0x07,0x12,0x80,0xe2,0xeb,0x27,0xb2,0x75,
                0x09,0x83,0x2c,0x1a,0x1b,0x6e,0x5a,0xa0,0x52,0x3b,0xd6,0xb3,0x29,0xe3,0x2f,0x84,
                0x53,0xd1,0x00,0xed,0x20,0xfc,0xb1,0x5b,0x6a,0xcb,0xbe,0x39,0x4a,0x4c,0x58,0xcf,
                0xd0,0xef,0xaa,0xfb,0x43,0x4d,0x33,0x85,0x45,0xf9,0x02,0x7f,0x50,0x3c,0x9f,0xa8,
                0x51,0xa3,0x40,0x8f,0x92,0x9d,0x38,0xf5,0xbc,0xb6,0xda,0x21,0x10,0xff,0xf3,0xd2,
                0xcd,0x0c,0x13,0xec,0x5f,0x97,0x44,0x17,0xc4,0xa7,0x7e,0x3d,0x64,0x5d,0x19,0x73,
                0x60,0x81,0x4f,0xdc,0x22,0x2a,0x90,0x88,0x46,0xee,0xb8,0x14,0xde,0x5e,0x0b,0xdb,
                0xe0,0x32,0x3a,0x0a,0x49,0x06,0x24,0x5c,0xc2,0xd3,0xac,0x62,0x91,0x95,0xe4,0x79,
                0xe7,0xc8,0x37,0x6d,0x8d,0xd5,0x4e,0xa9,0x6c,0x56,0xf4,0xea,0x65,0x7a,0xae,0x08,
                0xba,0x78,0x25,0x2e,0x1c,0xa6,0xb4,0xc6,0xe8,0xdd,0x74,0x1f,0x4b,0xbd,0x8b,0x8a,
                0x70,0x3e,0xb5,0x66,0x48,0x03,0xf6,0x0e,0x61,0x35,0x57,0xb9,0x86,0xc1,0x1d,0x9e,
                0xe1,0xf8,0x98,0x11,0x69,0xd9,0x8e,0x94,0x9b,0x1e,0x87,0xe9,0xce,0x55,0x28,0xdf,
                0x8c,0xa1,0x89,0x0d,0xbf,0xe6,0x42,0x68,0x41,0x99,0x2d,0x0f,0xb0,0x54,0xbb,0x16
            ]);
            const KOF_SEC_AES_RCON = [0x01,0x02,0x04,0x08,0x10,0x20,0x40,0x80,0x1b,0x36,0x6c,0xd8,0xab,0x4d];

            function kofSecAesKeyExpansion(key) {
                const Nk = 8, Nr = 14;
                const w = new Uint32Array(4 * (Nr + 1));
                for (let i = 0; i < Nk; i++) {
                    w[i] = ((key[4*i]<<24)|(key[4*i+1]<<16)|(key[4*i+2]<<8)|key[4*i+3]) >>> 0;
                }
                for (let i = Nk; i < 4*(Nr+1); i++) {
                    let temp = w[i-1];
                    if (i % Nk === 0) {
                        temp = ((temp << 8) | (temp >>> 24)) >>> 0;
                        temp = ((KOF_SEC_AES_SBOX[(temp>>>24)&0xff]<<24)|(KOF_SEC_AES_SBOX[(temp>>>16)&0xff]<<16)
                                |(KOF_SEC_AES_SBOX[(temp>>>8)&0xff]<<8)|KOF_SEC_AES_SBOX[temp&0xff]) >>> 0;
                        temp = (temp ^ (KOF_SEC_AES_RCON[i/Nk - 1] << 24)) >>> 0;
                    } else if (i % Nk === 4) {
                        temp = ((KOF_SEC_AES_SBOX[(temp>>>24)&0xff]<<24)|(KOF_SEC_AES_SBOX[(temp>>>16)&0xff]<<16)
                                |(KOF_SEC_AES_SBOX[(temp>>>8)&0xff]<<8)|KOF_SEC_AES_SBOX[temp&0xff]) >>> 0;
                    }
                    w[i] = (w[i-Nk] ^ temp) >>> 0;
                }
                return w;
            }

            function kofSecAesXtime(a) { return ((a<<1) ^ ((a & 0x80)?0x1b:0)) & 0xff; }
            function kofSecAesGmul(a,b) { let r=0; for(let i=0;i<8;i++){ if(b&1) r^=a; a=kofSecAesXtime(a); b>>>=1;} return r&0xff; }

            function kofSecAesEncryptBlock(rk, input) {
                const s = new Uint8Array(16);
                s.set(input);
                for (let c = 0; c < 4; c++) {
                    const w = rk[c];
                    s[4*c] ^= (w>>>24)&0xff; s[4*c+1] ^= (w>>>16)&0xff; s[4*c+2] ^= (w>>>8)&0xff; s[4*c+3] ^= w&0xff;
                }
                for (let round = 1; round < 14; round++) {
                    for (let i=0;i<16;i++) s[i]=KOF_SEC_AES_SBOX[s[i]];
                    let t;
                    t=s[1]; s[1]=s[5]; s[5]=s[9]; s[9]=s[13]; s[13]=t;
                    t=s[2]; s[2]=s[10]; s[10]=t; t=s[6]; s[6]=s[14]; s[14]=t;
                    t=s[15]; s[15]=s[11]; s[11]=s[7]; s[7]=s[3]; s[3]=t;
                    for (let c=0;c<4;c++) {
                        const a0=s[4*c],a1=s[4*c+1],a2=s[4*c+2],a3=s[4*c+3];
                        s[4*c]   = kofSecAesGmul(a0,2)^kofSecAesGmul(a1,3)^a2^a3;
                        s[4*c+1] = a0^kofSecAesGmul(a1,2)^kofSecAesGmul(a2,3)^a3;
                        s[4*c+2] = a0^a1^kofSecAesGmul(a2,2)^kofSecAesGmul(a3,3);
                        s[4*c+3] = kofSecAesGmul(a0,3)^a1^a2^kofSecAesGmul(a3,2);
                    }
                    for (let c = 0; c < 4; c++) {
                        const w = rk[4*round + c];
                        s[4*c] ^= (w>>>24)&0xff; s[4*c+1] ^= (w>>>16)&0xff; s[4*c+2] ^= (w>>>8)&0xff; s[4*c+3] ^= w&0xff;
                    }
                }
                for (let i=0;i<16;i++) s[i]=KOF_SEC_AES_SBOX[s[i]];
                let t;
                t=s[1]; s[1]=s[5]; s[5]=s[9]; s[9]=s[13]; s[13]=t;
                t=s[2]; s[2]=s[10]; s[10]=t; t=s[6]; s[6]=s[14]; s[14]=t;
                t=s[15]; s[15]=s[11]; s[11]=s[7]; s[7]=s[3]; s[3]=t;
                for (let c = 0; c < 4; c++) {
                    const w = rk[56 + c];
                    s[4*c] ^= (w>>>24)&0xff; s[4*c+1] ^= (w>>>16)&0xff; s[4*c+2] ^= (w>>>8)&0xff; s[4*c+3] ^= w&0xff;
                }
                return s;
            }

            function kofSecGcmInc32(c) { for (let i=15;i>=12;i--) { c[i]=(c[i]+1)&0xff; if(c[i]!==0) break; } }

            function kofSecGcmGctr(rk, ctr0, data) {
                const out = new Uint8Array(data.length);
                const ctr = ctr0.slice();
                for (let off=0; off<data.length; off+=16) {
                    const ks = kofSecAesEncryptBlock(rk, ctr);
                    const n = Math.min(16, data.length-off);
                    for (let j=0;j<n;j++) out[off+j]=data[off+j]^ks[j];
                    kofSecGcmInc32(ctr);
                }
                return out;
            }

            function kofSecGcmMult(X, Y) {
                const Z = new Uint8Array(16);
                const V = Y.slice();
                for (let i=0;i<128;i++) {
                    if ((X[i>>3] >>> (7-(i&7))) & 1) { for (let j=0;j<16;j++) Z[j]^=V[j]; }
                    const lsb = V[15] & 1;
                    for (let j=15;j>0;j--) V[j] = ((V[j]>>>1) | ((V[j-1]&1)<<7)) & 0xff;
                    V[0] = V[0]>>>1;
                    if (lsb) V[0] ^= 0xe1;
                }
                return Z;
            }

            function kofSecGhashAbsorb(Y, H, data) {
                for (let off=0; off<data.length; off+=16) {
                    const block = new Uint8Array(16);
                    block.set(data.subarray(off, Math.min(off+16, data.length)));
                    for (let j=0;j<16;j++) Y[j]^=block[j];
                    Y = kofSecGcmMult(Y, H);
                }
                return Y;
            }

            function kofSecGhash(H, aad, ct) {
                let Y = new Uint8Array(16);
                Y = kofSecGhashAbsorb(Y, H, aad);
                Y = kofSecGhashAbsorb(Y, H, ct);
                const lenBlock = new Uint8Array(16);
                const dv = new DataView(lenBlock.buffer);
                dv.setUint32(0, Math.floor(aad.length*8 / 4294967296));
                dv.setUint32(4, (aad.length*8) >>> 0);
                dv.setUint32(8, Math.floor(ct.length*8 / 4294967296));
                dv.setUint32(12, (ct.length*8) >>> 0);
                for (let j=0;j<16;j++) Y[j]^=lenBlock[j];
                Y = kofSecGcmMult(Y, H);
                return Y;
            }

            function kofSecGcmCore(key, iv, data, aad, decrypting) {
                const rk = kofSecAesKeyExpansion(key);
                const H = kofSecAesEncryptBlock(rk, new Uint8Array(16));
                const J0 = new Uint8Array(16); J0.set(iv); J0[15] = 1;
                const ctr = J0.slice(); kofSecGcmInc32(ctr);
                const out = kofSecGcmGctr(rk, ctr, data);
                const ctForTag = decrypting ? data : out;
                const S = kofSecGhash(H, aad, ctForTag);
                const encJ0 = kofSecAesEncryptBlock(rk, J0);
                const tag = new Uint8Array(16);
                for (let j=0;j<16;j++) tag[j] = S[j] ^ encJ0[j];
                return { out: out, tag: tag };
            }

            function kofSecRandomBytes(n) {
                if (typeof crypto !== "undefined" && crypto.getRandomValues) {
                    const b = new Uint8Array(n);
                    crypto.getRandomValues(b);
                    return b;
                }
                return kofSecHexToBytes(kof_platform.randomBytesHex(n));
            }

            export function kofSecAesgcmEncrypt(plaintext, keyHex) {
                const key = kofSecHexToBytes(keyHex);
                if (key.length !== 32) throw new Error("AES-GCM key must be 32 bytes (64 hex chars)");
                const iv = kofSecRandomBytes(12);
                const pt = kofSecUtf8(String(plaintext));
                const r = kofSecGcmCore(key, iv, pt, new Uint8Array(0), false);
                const ctTag = new Uint8Array(r.out.length + 16);
                ctTag.set(r.out);
                ctTag.set(r.tag, r.out.length);
                return "aesgcm$" + kofSecB64Encode(iv) + "$" + kofSecB64Encode(ctTag);
            }

            export function kofSecAesgcmDecrypt(ciphertext, keyHex) {
                const key = kofSecHexToBytes(keyHex);
                if (key.length !== 32) throw new Error("AES-GCM key must be 32 bytes (64 hex chars)");
                const parts = String(ciphertext).split("$");
                if (parts.length !== 3 || parts[0] !== "aesgcm") throw new Error("invalid ciphertext format");
                const iv = kofSecB64Decode(parts[1], true);
                const ctTag = kofSecB64Decode(parts[2], true);
                if (ctTag.length < 16) throw new Error("decryption failed: ciphertext too short");
                const ct = ctTag.subarray(0, ctTag.length - 16);
                const tag = ctTag.subarray(ctTag.length - 16);
                const r = kofSecGcmCore(key, iv, ct, new Uint8Array(0), true);
                let diff = 0;
                for (let j=0;j<16;j++) diff |= r.tag[j] ^ tag[j];
                if (diff !== 0) throw new Error("decryption failed: tag mismatch");
                return kofSecUtf8Decode(r.out);
            }

            // ── kof.validation (G4) ───────────────────────────────────

            export function kofValidationRequired(value) {
                return value != null && value.length > 0 ? 1 : 0;
            }

            export function kofValidationNotBlank(value) {
                return value != null && value.trim().length > 0 ? 1 : 0;
            }

            export function kofValidationMinLength(value, min) {
                return value != null && value.length >= min ? 1 : 0;
            }

            export function kofValidationMaxLength(value, max) {
                return value != null && value.length <= max ? 1 : 0;
            }

            export function kofValidationLengthBetween(value, min, max) {
                return value != null && value.length >= min && value.length <= max ? 1 : 0;
            }

            export function kofValidationIsEmail(value) {
                if (value == null) return 0;
                return /^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(value) ? 1 : 0;
            }

            export function kofValidationIsUrl(value) {
                if (value == null) return 0;
                return value.startsWith("http://") || value.startsWith("https://") ? 1 : 0;
            }

            export function kofValidationMatches(value, pattern) {
                if (value == null || pattern == null) return 0;
                try { return new RegExp(pattern).test(value) ? 1 : 0; } catch (e) { return 0; }
            }

            export function kofValidationIsInt(value) {
                if (value == null) return 0;
                return /^-?[0-9]+$/.test(value.trim()) ? 1 : 0;
            }

            export function kofValidationIsLong(value) {
                if (value == null) return 0;
                return /^-?[0-9]+$/.test(value.trim()) ? 1 : 0;
            }

            export function kofValidationInRange(value, min, max) {
                return value >= min && value <= max ? 1 : 0;
            }

            export function kofValidationMin(value, min) {
                return value >= min ? 1 : 0;
            }

            export function kofValidationMax(value, max) {
                return value <= max ? 1 : 0;
            }

            // ── kof.observability (G5) ──────────────────────────────

            const __kofObsCounters = {};
            const __kofObsGauges = {};
            const __kofObsHistograms = {};

            export function kofObservabilityHealth() {
                return "UP";
            }

            export function kofObservabilityReadiness() {
                return 1;
            }

            export function kofObservabilityLiveness() {
                return 1;
            }

            export function kofObservabilityCounter(name) {
                if (name == null) name = "";
                const v = (__kofObsCounters[name] || 0) + 1;
                __kofObsCounters[name] = v;
                return v;
            }

            export function kofObservabilityIncrement(name, delta) {
                if (name == null) name = "";
                const v = (__kofObsCounters[name] || 0) + delta;
                __kofObsCounters[name] = v;
                return v;
            }

            export function kofObservabilityGauge(name, value) {
                if (name == null) name = "";
                __kofObsGauges[name] = value;
            }

            export function kofObservabilityHistogram(name, value) {
                if (name == null) name = "";
                const h = __kofObsHistograms[name] || (__kofObsHistograms[name] = { sum: 0, count: 0 });
                h.sum += value;
                h.count += 1;
            }

            const __kofObsSpans = new Map();
            let __kofObsActiveTrace = null;

            export function kofObservabilitySpanStart(name) {
                const id = kofObservabilityTraceId() + kofObservabilitySpanId();
                __kofObsSpans.set(id, Date.now() * 1000);
                return id;
            }

            export function kofObservabilitySpanEnd(handle) {
                const start = __kofObsSpans.get(handle);
                if (start === undefined) return "{}";
                __kofObsSpans.delete(handle);
                const end = Date.now() * 1000;
                const trace = __kofObsActiveTrace || kofObservabilityTraceId();
                return JSON.stringify({
                    traceId: trace,
                    spanId: handle.substring(32),
                    parentSpanId: "",
                    name: "span",
                    startMicros: start,
                    endMicros: end,
                    durationMicros: end - start
                });
            }

            function __kofPromName(name, suffix) {
                let out = String(name).replace(/[^a-zA-Z0-9_:]/g, "_");
                if (out.length === 0) out = "k";
                return out + suffix;
            }

            export function kofObservabilityMetrics() {
                let sb = "";
                const counters = Object.keys(__kofObsCounters).sort();
                for (const m of counters) {
                    const n = __kofPromName(m, "");
                    sb += "# TYPE " + n + " counter\\n" + n + " " + __kofObsCounters[m] + "\\n";
                }
                const gauges = Object.keys(__kofObsGauges).sort();
                for (const m of gauges) {
                    const n = __kofPromName(m, "");
                    sb += "# TYPE " + n + " gauge\\n" + n + " " + __kofObsGauges[m] + "\\n";
                }
                const hists = Object.keys(__kofObsHistograms).sort();
                for (const m of hists) {
                    const n = __kofPromName(m, "");
                    const h = __kofObsHistograms[m];
                    sb += "# TYPE " + n + "_count counter\\n" + n + "_count " + h.count + "\\n";
                    sb += "# TYPE " + n + "_sum gauge\\n" + n + "_sum " + h.sum + "\\n";
                }
                return sb;
            }

            export function kofObservabilityRequestId() {
                if (typeof crypto !== "undefined" && crypto.randomUUID) return crypto.randomUUID();
                return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => {
                    const r = Math.random() * 16 | 0;
                    const v = c === "x" ? r : (r & 0x3 | 0x8);
                    return v.toString(16);
                });
            }

            export function kofObservabilityCorrelationId() {
                return kofObservabilityRequestId();
            }

            function __kofObsRandomHex(bytes) {
                const out = [];
                for (let i = 0; i < bytes; i++) out.push(Math.floor(Math.random() * 256));
                return out.map(v => v.toString(16).padStart(2, "0")).join("");
            }

            export function kofObservabilityTraceId() {
                return __kofObsRandomHex(16);
            }

            export function kofObservabilitySpanId() {
                return __kofObsRandomHex(8);
            }

            // ── kof.security G9 (rate limiting / sessions / API keys) ──

            const __kofRateLimit = {};
            const __kofSessions = {};
            const __kofApiKeys = {};

            export function kofSecRateLimit(key, limit, windowSeconds) {
                if (key == null) key = "";
                if (limit <= 0 || windowSeconds <= 0) return 0;
                const now = Date.now();
                const windowMillis = windowSeconds * 1000;
                let entry = __kofRateLimit[key];
                if (!entry || now - entry.windowStart >= windowMillis) {
                    __kofRateLimit[key] = { windowStart: now, count: 1 };
                    return 1;
                }
                if (entry.count < limit) {
                    entry.count++;
                    return 1;
                }
                return 0;
            }

            export function kofSecSessionCreate(data) {
                const id = kofSecRandomHex(16);
                __kofSessions[id] = data == null ? "" : data;
                return id;
            }

            export function kofSecSessionGet(id) {
                if (id == null) return null;
                const v = __kofSessions[id];
                return v === undefined ? null : v;
            }

            export function kofSecSessionDestroy(id) {
                if (id == null) return 0;
                if (__kofSessions[id] !== undefined) {
                    delete __kofSessions[id];
                    return 1;
                }
                return 0;
            }

            export function kofSecApiKeyGenerate() {
                const key = kofSecRandomHex(32);
                __kofApiKeys[key] = true;
                return key;
            }

            export function kofSecApiKeyValid(key) {
                if (key == null) return 0;
                return __kofApiKeys[key] ? 1 : 0;
            }
            """;

}
