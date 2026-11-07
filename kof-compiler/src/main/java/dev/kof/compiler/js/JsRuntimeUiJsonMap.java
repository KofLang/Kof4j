package dev.kof.compiler.js;

/** kof-runtime.mjs — decode de mapa JSON (§103.1, GitHub #103). */
public final class JsRuntimeUiJsonMap {
    private JsRuntimeUiJsonMap() {
    }

    static  String JSON_MAP_RUNTIME = """
            // §103.1 (#103): decode<Map<String,T>> no JS — monta um Map
            // real (o else dava objeto puro: m.size/m.get quebravam); com
            // decoder, cada valor é bindado à classe.
            export function kofJsonDeep(v) {
                if (Array.isArray(v)) return v.map(kofJsonDeep);
                if (v !== null && typeof v === 'object' && !(v instanceof Map)) {
                    const m = new Map();
                    for (const [k, x] of Object.entries(v)) m.set(k, kofJsonDeep(x));
                    return m;
                }
                return v;
            }

            // #1103 (JSN006, decisao da mantenedora 05/11): shape errada no
            // decode e ERRO NOMEADO, nunca map vazio/manglado ("0","1").
            function kofJsonShapeError(expected, v) {
                const got = Array.isArray(v) ? 'an array'
                        : v === null || v === undefined ? 'null'
                        : typeof v === 'string' ? 'a string'
                        : typeof v === 'boolean' ? 'a bool'
                        : typeof v === 'number' ? 'a number'
                        : typeof v === 'object' ? 'an object' : String(typeof v);
                return new Error('JSN006: json.decode expected ' + expected
                        + ' but the payload is ' + got);
            }
            export function kofJsonRequireObject(v) {
                if (v === null || typeof v !== 'object' || Array.isArray(v)) throw kofJsonShapeError('an object', v);
                return v;
            }
            export function kofJsonRequireArray(v) {
                if (!Array.isArray(v)) throw kofJsonShapeError('an array', v);
                return v;
            }

            export function kofJsonDecodeMap(obj) {
                const o = kofJsonRequireObject(obj);
                const m = new Map();
                for (const [k, v] of Object.entries(o)) m.set(k, kofJsonDeep(v));
                return m;
            }

            export function kofJsonDecodeObjectMap(obj, decoder) {
                const o = kofJsonRequireObject(obj);
                const m = new Map();
                for (const [k, v] of Object.entries(o)) m.set(k, decoder(v));
                return m;
            }

            // §106 residual (13/09): encode<Map<String,T>> no JS. JSON.stringify
            // num `Map` devolve '{}' (Map não tem own enumerable properties) —
            // o §106 declarou "4 backends" mas NUNCA testou o JS (matriz não
            // cobria json.encode(Map)). Aqui montamos o objeto com chaves
            // SORTED (mesma semântica JVM/nativo/interp, decisão 2b) via
            // kofJsonEncode por valor (escapa string/número/bool/null
            // corretamente; `tag` fica na assinatura por simetria).
            export function kofJsonEncodeMap(map, tag) {
                const keys = [...map.keys()].sort();
                const parts = [];
                for (const k of keys) {
                    parts.push(JSON.stringify(String(k)) + ':' + kofJsonEncode(map.get(k)));
                }
                return '{' + parts.join(',') + '}';
            }

            // §659b: um campo Long do Kof é um BigInt do JS, e o JSON.stringify
            // recusa BigInt (TypeError: Do not know how to serialize a BigInt) —
            // o encode JSON de qualquer valor com Long (escalar, campo de record,
            // elemento de lista, valor de map) quebrava o processo. kofJsonEncode
            // serializa BigInt como o INTEIRO DECIMAL cru (paridade JVM/nativo/
            // Script). A pré-checagem kofJsonHasBig mantém byte-idêntico o
            // caminho SEM Long (o JSON.stringify puro), então nenhum programa sem
            // Long muda de saída.
            // §659d: o valor JSON de um Double/Float usa o Double.toString do
            // JDK nos outros três alvos (`1.0`, `-0.0`, `1.0E21`), mas o JS
            // representa os dois como `number` e o JSON.stringify imprime `1`,
            // `0` e `1e+21`. kofNumFmt (§264) é o MESMO formatador do display
            // (já byte-idêntico ao JVM), então o encode JSON reusa ele. NaN/±Inf
            // não existem em JSON: viram o literal `null` (paridade com o
            // kof_json_encode_double do JVM/nativo).
            function kofJsonFpBody(v, isFloat) {
                const s = kofNumFmt(v, isFloat);
                return (s === "NaN" || s === "Infinity" || s === "-Infinity" || s === "null")
                        ? "null" : s;
            }
            export function kofJsonEncodeDouble(v) {
                return kofJsonFpBody(v, 0);
            }
            export function kofJsonEncodeFloat(v) {
                return kofJsonFpBody(v, 1);
            }
            // §659d (List<Double>): o elemento é um `number` e o
            // JSON.stringify imprime `1`; monta o array com o formato do JDK
            // por elemento (tag 3 do JsonDispatch.listTag).
            export function kofJsonEncodeDoubleList(arr) {
                const parts = [];
                for (const x of arr) parts.push(kofJsonFpBody(x, 0));
                return '[' + parts.join(',') + ']';
            }
            // Campo Double/Float de record: o toJSON sintético marca o valor
            // (kofJsonFp) e o kofJsonEncode troca a marca pela forma CRUA — o
            // JSON.stringify não tem como distinguir `1.0` de `1` sozinho.
            // A marca é ASCII cercada por `<<`/`>>` para o replace ser um
            // literal sem escapes; um campo `String` do usuário que contivesse
            // a marca exata INTEIRA seria raro o bastante (mesma classe de
            // risco do sentinel de BigInt acima).
            const KOF_FP_MARK = "<<KOFFP:";
            const KOF_FP_END = ":KOFFP>>";
            export function kofJsonFp(v, isFloat) {
                return KOF_FP_MARK + kofJsonFpBody(v, isFloat) + KOF_FP_END;
            }
            const KOF_FP_RE = new RegExp('"' + '<<KOFFP:([^"]*)' + ':KOFFP>>' + '"', 'g');

            export function kofJsonHasBig(v) {
                if (typeof v === 'bigint') return true;
                if (Array.isArray(v)) {
                    for (const x of v) if (kofJsonHasBig(x)) return true;
                    return false;
                }
                if (v !== null && typeof v === 'object') {
                    for (const k of Object.keys(v)) if (kofJsonHasBig(v[k])) return true;
                    return false;
                }
                return false;
            }

            // §659d (face record): o toJSON de um record com campo Double/Float
            // inteiro emite a SENTINELA (a mesma mecânica do §659b) e o strip
            // abaixo a devolve COM o .0 — paridade JVM/Script/nativo.
            export function kofJsonHasRecord(v) {
                if (Array.isArray(v)) {
                    for (const x of v) if (kofJsonHasRecord(x)) return true;
                    return false;
                }
                if (v !== null && typeof v === 'object' && !(v instanceof Map)) {
                    if (typeof v.toJSON === 'function') return true;
                    for (const k of Object.keys(v)) if (kofJsonHasRecord(v[k])) return true;
                    return false;
                }
                return false;
            }

            export function kofJsonEncode(v) {
                if (!kofJsonHasBig(v)) {
                    const s = JSON.stringify(v);
                    return s.indexOf(KOF_FP_MARK) < 0 ? s : s.replace(KOF_FP_RE, '$1');
                }
                return JSON.stringify(v, (k, x) => typeof x === 'bigint'
                        ? 'KOFBIGINT_SENTINEL_' + x.toString() + '_END' : x)
                    .replace(/"KOFBIGINT_SENTINEL_(-?[0-9]+)_END"/g, '$1')
                    .replace(KOF_FP_RE, '$1');
            }

            """;

}
