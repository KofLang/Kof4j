package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.net} (STDLIB S8, plan §4 — decisão
 * 09/09: 6 escalares em vez de record, pois nenhuma fn de runtime asm devolve
 * objeto estruturado no Native; mesma família do precedente validation).
 *
 * Semântica v1 travada em plan-stdlib-expansion §4 (RFC 3986 subset, escopo
 * honesto): scheme = [A-Za-z][A-Za-z0-9+.-]* antes do 1º ':' (senão "");
 * authority só após "//" (userinfo após o último '@' ignorado; host até o 1º
 * ':' — v1 SEM colchetes IPv6, documentado); path até '?'/'#'; query após o
 * 1º '?' até '#'; fragment após o 1º '#'; campo ausente => ""; null => null;
 * NUNCA lança. queryEncode/Decode = fachada de intenção sobre encoding.url*.
 *
 * NET001 CLOSED 09/09 (padrão SECN000/ENC002-histórico): byte-scan nativo
 * fechado nos 3 nativos — x86 (RuntimeNet S8-B), riscv64 (slice B24) e
 * aarch64 (mesmo asm via tradutor); `net.*` (incl. queryEncode/Decode, que
 * compõem encoding.url* já portado) roda em todos os alvos e nenhum caminho
 * é gated. Prova: `KofNetTest.netOnCrossArch` (17 vetores oracle, qemu) —
 * ver `conformance-matrix.md` §net.
 */
public final class KofNet {

    private KofNet() {}

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;

    /** Handles do front `D-KOF-NET` (mesma padronagem do handle sintético de
     *  {@code KofWeb.APP}): tipos-opacos devolvidos pelos verbos e aceitos
     *  como receiver/1º-argumento pelos membros. */
    static final Type LISTENER =
            new Type.ClassType("dev.kof.runtime", "KofRuntime$NetListener", List.of());
    static final Type CONN =
            new Type.ClassType("dev.kof.runtime", "KofRuntime$NetConn", List.of());
    static final Type ENDPOINT =
            new Type.ClassType("dev.kof.runtime", "KofRuntime$NetEndpoint", List.of());
    private static final Type BYTE_ARR =
            new Type.ArrayType(Type.PrimitiveType.BYTE);
    /** close é o único verbo polimórfico em handle — a JVM resolve por
     *  Object (widening de referência; o runtime guarda o objeto real). */
    private static final Type HANDLE =
            new Type.ClassType("java.lang", "Object", List.of());

    /** Verbs do front (D-KOF-NET) — NÃO são faces URI. `accept/send/receive/
     *  sendTo/peer/close` são membros de handle (precedente web: `app.get` não
     *  está em KofWeb.functions()); `listen/connect/bind` são estáticos e por
     *  isso entram em functions()/catalogo. */
    private static final List<String> SOCKET_VERBS = List.of(
            "listen", "accept", "connect", "bind", "send", "receive", "sendTo", "peer");

    static final List<String> NAMESPACES = List.of("net");

    static boolean isNetNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record NetCall(String function, Type returnType, List<Type> parameterTypes) {}


    /** X10 fatia 1: nomes aceitos pelo staticMethod (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case literals do switch(name) abaixo
     *  (19/09 LSP-A fatia 3: a família `case "scheme", "host", ...` binda os 8
     *  e a lista só tinha o primeiro literal — drift do tipo db/process). */
    static List<String> functions() {
        return List.of("scheme", "host", "port", "path", "query",
                "fragment", "queryEncode", "queryDecode",
                "listen", "connect", "bind");
    }
    static NetCall staticMethod(String namespace, String name, List<Type> argTypes) {
        int argc = argTypes.size();
        return switch (name) {
            case "scheme", "host", "port", "path", "query", "fragment",
                    "queryEncode", "queryDecode" -> argc == 1
                    ? new NetCall("kof_net_" + name, STR, List.of(STR)) : null;
            // D-KOF-NET front (fatia 1 = superfície; runtime por alvo nas fatias
            // 2–5). Aritidades congeladas no plano; handle como receiver vira
            // 1º argumento na rota de membros (padrão web/db).
            case "listen" -> argc == 1 && argTypes.get(0) == INT
                    ? new NetCall("kof_net_listen", LISTENER, List.of(INT)) : null;
            case "connect" -> argc == 2 && argTypes.get(0) == STR && argTypes.get(1) == INT
                    ? new NetCall("kof_net_connect", CONN, List.of(STR, INT)) : null;
            case "bind" -> argc == 1 && argTypes.get(0) == INT
                    ? new NetCall("kof_net_bind", ENDPOINT, List.of(INT)) : null;
            // accept/send/receive/sendTo/peer/close recebem handle => membros
            // de handle (instanceMethod), nao estaticos de namespace (web precedent).
            default -> null;
        };
    }

    /** Membros de handle (§498-arm pattern): receiver-tipado, handle é o 1º argumento. */
    static NetCall instanceMethod(String name, List<Type> argTypes) {
        int argc = argTypes.size();
        Type recv = argTypes.get(0);
        return switch (name) {
            case "accept" -> argc == 1 && LISTENER.equals(recv)
                    ? new NetCall("kof_net_accept", CONN, List.of(LISTENER)) : null;
            case "send" -> argc == 2 && CONN.equals(recv) && BYTE_ARR.equals(argTypes.get(1))
                    ? new NetCall("kof_net_send", INT, List.of(CONN, BYTE_ARR)) : null;
            case "receive" -> argc == 2 && CONN.equals(recv) && argTypes.get(1) == INT
                    ? new NetCall("kof_net_receive", BYTE_ARR, List.of(CONN, INT))
                    : argc == 2 && ENDPOINT.equals(recv) && argTypes.get(1) == INT
                    ? new NetCall("kof_net_receiveFrom", BYTE_ARR, List.of(ENDPOINT, INT)) : null;
            case "sendTo" -> argc == 3 && ENDPOINT.equals(recv) && argTypes.get(1) == STR
                    && BYTE_ARR.equals(argTypes.get(2))
                    ? new NetCall("kof_net_sendTo", INT, List.of(ENDPOINT, STR, BYTE_ARR)) : null;
            case "peer" -> argc == 1 && ENDPOINT.equals(recv)
                    ? new NetCall("kof_net_peer", STR, List.of(ENDPOINT)) : null;
            case "close" -> argc == 1 && (LISTENER.equals(recv) || CONN.equals(recv) || ENDPOINT.equals(recv))
                    ? new NetCall("kof_net_close", VOID, List.of(HANDLE)) : null;
            default -> null;
        };
    }

    /** Seam unico da virada fatia-2 (Q5): `javap` do KofRuntime GERADO nao tem
     *  kof_net_* (medido 01/10) — aceitar em qualquer alvo seria verde falso
     *  (class load morreria NoSuchMethodError). A fatia 2 emite o corpo
     *  java.net real e vira isto para `target == Target.JVM || target == Target.ANDROID`. */
    /** Portão dos verbos de MEMBRO (handle receiver). Espelha
     *  {@link #supportedOn} para os verbos `accept/send/receive/sendTo/peer/
     *  close`, que passam pelo caminho do receiver e não pelo
     *  `staticMethod`. Verdadeiro no JVM desde a fatia 2, quando
     *  `JvmRuntimeSockets` emite o corpo real — os dois portões precisam
     *  concordar, senão um verbo aceito cai num runtime sem método (o verde
     *  falso que a fatia 1 mediu). */
    static boolean socketRuntimeReady(Target target) {
        return target == Target.JVM || target == Target.ANDROID;
    }

    static boolean isNetHandleType(Type t) {
        return LISTENER.equals(t) || CONN.equals(t) || ENDPOINT.equals(t);
    }

    static boolean isSocketVerb(String name) {
        return SOCKET_VERBS.contains(name);
    }

    static boolean supportedOn(String function, Target target) {
        // URI accessors: NET001 fechado 09/09 — rodam em todo alvo (true).
// Socket verbs do front D-KOF-NET: JVM a partir da fatia 2 — medido
        // 01/10, `JvmRuntimeSockets` emite o corpo real (ServerSocket/Socket/
        // DatagramSocket) e `javap KofRuntime.class` passa a listar
        // kof_net_listen/accept/connect/bind/send/receive/sendTo/receiveFrom/
        // peer/close. Antes disso o gate recusava em TODO alvo: a fatia 1 mediu
        // que aceitar sem o método no runtime gerado é um verde falso (Q5) que
        // morre NoSuchMethodError no class load. Native/JS/Script seguem as
        // fatias 3–5 e ainda recusam com NET002 (nunca drop silencioso).
        if (function.startsWith("kof_net_listen") || function.startsWith("kof_net_accept")
                || function.startsWith("kof_net_connect") || function.startsWith("kof_net_bind")
                || function.startsWith("kof_net_send") || function.startsWith("kof_net_receive")
                || function.startsWith("kof_net_peer") || function.equals("kof_net_close")) {
            return target == Target.JVM || target == Target.ANDROID;
        }
        return true;
    }

    static String gapCode(String function) {
        // Vestigial p/ URI: NET001 fechado 09/09. Socket verbs sem runtime =>
        // NET002 (fatia 1=superfície, fatia 2=JVM runtime; ver supportedOn).
        if (function.startsWith("kof_net_listen") || function.startsWith("kof_net_accept")
                || function.startsWith("kof_net_connect") || function.startsWith("kof_net_bind")
                || function.startsWith("kof_net_send") || function.startsWith("kof_net_receive")
                || function.startsWith("kof_net_peer") || function.equals("kof_net_close")) {
            return "NET002";
        }
        return "NET001";
    }
}
