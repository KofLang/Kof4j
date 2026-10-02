package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * app.security() (D-SEC C18) — config do middleware composto.
 * Concatenacao preserva byte-a-byte.
 */
public final class JvmWebSecurityRuntime {

    private JvmWebSecurityRuntime() {}

    static String source() {
        return """
                /**
                 * C18 (D-SEC): {@code app.security([opts])} — middleware
                 * composto de ordem fixa ratificada no DECISIONS §D-SEC:
                 * rate-limit → cors → headers → session → csrf → auth → RBAC.
                 * Os opts (Map) alimentam campos no WebApp; a APLICAÇÃO
                 * acontece no dispatch (JvmRuntimeWebDispatch), que já tem a
                 * WebRequest em mão — nada de handler-reflect.
                 *
                 * Opts documentados (união das duas lanes que fizeram C18):
                 * headers (Bool), cors (String CSV/`*`), corsOrigin (String),
                 * rateLimit (String "n/janelaSeg" ou Number de requests),
                 * csrf (Bool), sessionHeader (String), publicPaths (String CSV),
                 * auth (Bool), roles (String CSV ou List),
                 * responses (Map: unauthorized/forbidden/tooManyRequests/notFound
                 * -> corpo de rejeicao cru; ausente = corpo embutido).
                 *
                 * §5 (Spring model): `permitAll` é alias de `publicPaths`
                 * (allow-list de matchers; todo o resto exige autenticação).
                 * CSRF é ON por padrão (métodos seguros emitem o cookie;
                 * métodos de mutação exigem o double-submit) — pode desligar
                 * com csrf:false.
                 */
                public static final class Policy {
                    boolean headers = true;
                    int rateLimit = 0;
                    int rateWindow = 60;
                    String cors = null;
                    boolean csrf = false;
                    String authHeader = null;
                    final java.util.List<String> publicPaths = new java.util.ArrayList<>();
                    boolean requireAuth = false;
                    final java.util.List<String> roles = new java.util.ArrayList<>();
                    final java.util.Map<String, String> responses = new java.util.HashMap<>();
                    // F2: marcadores de chave DECLARADA — permitem que o merge
                    // deixe o escopo filho herdar o que ele nao declarou (o
                    // default do filho jamais sobrescreve o pai).
                    boolean hasHeaders;
                    boolean hasRateLimit;
                    boolean hasCors;
                    boolean hasCsrf;
                    boolean hasAuthHeader;
                    boolean hasRequireAuth;

                    /** F1 (D-HTTP-POLICIES): parser unico dos opts — mesmas
                     *  chaves/defaults de hoje. {@code csrfDefault} = true so no
                     *  escopo global (app.security() liga CSRF por padrao). */
                    static Policy parse(java.util.Map<?, ?> opts, boolean csrfDefault) {
                        Policy p = new Policy();
                        p.csrf = csrfDefault;
                        if (opts == null) return p;
                        Object headers = opts.get("headers");
                        if (headers != null) {
                            p.headers = kof_web_sec_bool(headers);
                            p.hasHeaders = true;
                        }
                        Object cors = opts.get("cors");
                        if (cors == null) cors = opts.get("corsOrigin");
                        if (cors != null) {
                            p.cors = String.valueOf(cors);
                            p.hasCors = true;
                        }
                        Object rate = opts.get("rateLimit");
                        if (rate instanceof Number n) {
                            p.rateLimit = n.intValue();
                            p.hasRateLimit = true;
                        } else if (rate != null) {
                            String spec = String.valueOf(rate);
                            int slash = spec.indexOf('/');
                            if (slash <= 0) {
                                throw new IllegalArgumentException(
                                        "rateLimit must be \\"limit/windowSeconds\\", got: " + spec);
                            }
                            p.rateLimit = Integer.parseInt(spec.substring(0, slash).trim());
                            p.rateWindow = Integer.parseInt(spec.substring(slash + 1).trim());
                            p.hasRateLimit = true;
                        }
                        Object csrf = opts.get("csrf");
                        if (csrf != null) {
                            p.csrf = kof_web_sec_bool(csrf);
                            p.hasCsrf = true;
                        }
                        Object sessionHeader = opts.get("sessionHeader");
                        if (sessionHeader instanceof String s && !s.isBlank()) {
                            p.authHeader = s.toLowerCase();
                            p.hasAuthHeader = true;
                        }
                        Object publicPaths = opts.get("publicPaths");
                        if (publicPaths == null) publicPaths = opts.get("permitAll");
                        if (publicPaths instanceof String csv && !csv.isBlank()) {
                            for (String x : csv.split(",")) {
                                if (!x.isBlank()) p.publicPaths.add(x.trim());
                            }
                        }
                        Object auth = opts.get("auth");
                        if (auth != null) {
                            p.requireAuth = kof_web_sec_bool(auth);
                            p.hasRequireAuth = true;
                        }
                        Object roles = opts.get("roles");
                        if (roles instanceof java.util.List<?> list) {
                            for (Object r : list) {
                                if (r != null) p.roles.add(String.valueOf(r));
                            }
                        } else if (roles != null) {
                            for (String r : String.valueOf(roles).split(",")) {
                                if (!r.trim().isEmpty()) p.roles.add(r.trim());
                            }
                        }
                        Object responses = opts.get("responses");
                        if (responses instanceof java.util.Map<?, ?> map) {
                            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                                if (e.getKey() == null || e.getValue() == null) continue;
                                String key = String.valueOf(e.getKey());
                                if (kof_web_sec_response_key(key)) {
                                    p.responses.put(key, String.valueOf(e.getValue()));
                                }
                            }
                        }
                        return p;
                    }

                    /** F2: copia rasa (listas/map novos) — base do merge. */
                    Policy copy() {
                        Policy r = new Policy();
                        r.headers = headers;
                        r.rateLimit = rateLimit;
                        r.rateWindow = rateWindow;
                        r.cors = cors;
                        r.csrf = csrf;
                        r.authHeader = authHeader;
                        r.requireAuth = requireAuth;
                        r.publicPaths.addAll(publicPaths);
                        r.roles.addAll(roles);
                        r.responses.putAll(responses);
                        return r;
                    }

                    /** F2 (D-HTTP-POLICIES §4.3): merge do escopo filho sobre
                     *  este. Escalares: filho vence SO se declarado (heranca
                     *  preservada). Listas (`publicPaths`/`roles`): uniao,
                     *  allow-list so cresce. `responses`: chave mais profunda
                     *  vence. */
                    Policy merge(Policy child) {
                        if (child == null) return this;
                        Policy r = copy();
                        if (child.hasHeaders) r.headers = child.headers;
                        if (child.hasRateLimit) {
                            r.rateLimit = child.rateLimit;
                            r.rateWindow = child.rateWindow;
                        }
                        if (child.hasCors) r.cors = child.cors;
                        if (child.hasCsrf) r.csrf = child.csrf;
                        if (child.hasAuthHeader) r.authHeader = child.authHeader;
                        if (child.hasRequireAuth) r.requireAuth = child.requireAuth;
                        for (String x : child.publicPaths) {
                            if (!r.publicPaths.contains(x)) r.publicPaths.add(x);
                        }
                        for (String x : child.roles) {
                            if (!r.roles.contains(x)) r.roles.add(x);
                        }
                        r.responses.putAll(child.responses);
                        return r;
                    }
                }

                /** F2: policy de recurso — prefixo simples + policy parseada. */
                public static final class ScopedPolicy {
                    final String prefix;
                    final Policy policy;

                    ScopedPolicy(String prefix, Policy policy) {
                        this.prefix = prefix;
                        this.policy = policy;
                    }

                    /** Prefixo simples: `"*"`/vazio = todos; senao prefixo do
                     *  caminho (igual ou comeca com). Sem glob/regex em v1. */
                    boolean appliesTo(String path) {
                        if (prefix == null || prefix.isEmpty() || "*".equals(prefix)) {
                            return true;
                        }
                        return path != null
                                && (path.equals(prefix) || path.startsWith(prefix));
                    }
                }

                public static void kof_web_security(String appId) {
                    kof_web_security_opts(appId, null);
                }

                public static void kof_web_security_opts(String appId, java.util.Map<?, ?> opts) {
                    WebApp app = kof_web_app(appId);
                    app.securityConfigured = true;
                    // F1: o escopo global vira um Policy (defaults de hoje:
                    // headers ON; CSRF ON quando app.security() e declarado).
                    app.globalPolicy = Policy.parse(opts, true);
                }

                /**
                 * D-HTTP-POLICIES (F2): {@code app.policy(prefix, opts)} —
                 * policy de recurso aplicada a toda rota sob o prefixo.
                 * Parse com csrfDefault=false (herda o global quando nao
                 * declara). Marca securityConfigured para nao disparar o warn
                 * de "sem middleware" em producao.
                 */
                public static void kof_web_policy(String appId, String prefix,
                        java.util.Map<?, ?> opts) {
                    // R6 (§8): prefixo desconhecido falha no build, nunca vira
                    // no-op silencioso. `"*"`/`""` = todos; senao exige `/` inicial.
                    if (prefix != null && !prefix.isEmpty() && !"*".equals(prefix)
                            && !prefix.startsWith("/")) {
                        throw new IllegalArgumentException(
                                "policy prefix must start with '/', or be \\"*\\" / \\"\\""
                                        + " for all: " + prefix);
                    }
                    WebApp app = kof_web_app(appId);
                    app.securityConfigured = true;
                    app.policies.add(new ScopedPolicy(prefix, Policy.parse(opts, false)));
                }

                /**
                 * F2 §4.3: politica efetiva do request = global + escopos que
                 * casam o path, do menor para o maior prefixo (mais longo por
                 * ultimo = vence nos escalares). Sem match = global.
                 */
                private static Policy kof_web_effective_policy(WebApp app, WebRequest req) {
                    Policy effective = app.globalPolicy;
                    if (app.policies.isEmpty()) return effective;
                    java.util.List<ScopedPolicy> matches = new java.util.ArrayList<>();
                    for (ScopedPolicy sp : app.policies) {
                        if (sp.appliesTo(req.path)) matches.add(sp);
                    }
                    if (matches.isEmpty()) return effective;
                    matches.sort(java.util.Comparator.comparingInt(
                            sp -> sp.prefix == null ? 0 : sp.prefix.length()));
                    for (ScopedPolicy sp : matches) {
                        effective = effective.merge(sp.policy);
                    }
                    return effective;
                }

                private static boolean kof_web_sec_response_key(String key) {
                    return "unauthorized".equals(key) || "forbidden".equals(key)
                            || "tooManyRequests".equals(key) || "notFound".equals(key);
                }

                private static boolean kof_web_sec_bool(Object value) {
                    if (value instanceof Boolean b) return b;
                    String s = String.valueOf(value);
                    return !("false".equalsIgnoreCase(s) || "0".equals(s));
                }

                /**
                 * D-SEC C18: security by default — `listen` em produção
                 * (KOF_ENV=production) exige `app.security()` explícito; sem
                 * ele, avisa (nunca falha silenciosamente).
                 */
                private static void kof_web_warn_security_default(WebApp app) {
                    if (!app.securityConfigured
                            && "production".equalsIgnoreCase(System.getenv("KOF_ENV"))) {
                        System.err.println("kof.web: production mode without app.security() — "
                                + "no security middleware configured (D-SEC C18)");
                    }
                }

""";
    }
}
