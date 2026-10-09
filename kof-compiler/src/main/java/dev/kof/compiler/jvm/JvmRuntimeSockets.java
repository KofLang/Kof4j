package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado — frente de sockets do front
 * `D-KOF-NET` (plan {@code docs/stdlib/network-kofnet-plan.md}, fatia 2).
 *
 * Contrato congelado pela decisão: uma namespace `kof.net`, verbos BLOQUEANTES
 * (o paralelismo é o `spawn` do Kof — nenhuma maquinaria async/await de I/O é
 * introduzida), payload `Byte[]` nas duas mãos nos dois transportes, UDP
 * endereçado por {@code "host:port"} String, limite de 64 KiB com recusa
 * `NET003`, unicast-only no v1.
 *
 * Os handles (`NetListener`/`NetConn`/`NetEndpoint`) são tipos-opacos reais do
 * runtime — não Strings — para que `conn.send(...)` não possa ser confundido com
 * `endpoint.send(...)`: cada handle carrega o seu socket e cada verbo checa o
 * tipo ANTES de tocar no campo (um `close` de handle incompatível não pode
 *这门ar o objeto errado).
 *
 * `java.net` só aparece AQUI dentro do fragmento (mesma higiene do servidor web
 * `JvmRuntimeWebServer`): nenhuma referência a `java.*` escapa para o lowering.
 *
 * Honestidade do gate (medido na fatia 1): `KofNet.supportedOn` só volta a
 * aceitar `kof_net_*` no JVM NO MOMENTO em que este fragmento é emitido — a
 * aceitação e a existência do método andam juntas, senão o class load morre
 * `NoSuchMethodError` (o verde falso que a sonda mediu).
 */
public final class JvmRuntimeSockets {

    private JvmRuntimeSockets() {}

    /** Payload máximo de um datagrama UDP/IPv4: 65535 (IP) − 20 (cabeçalho
     *  IPv4) − 8 (cabeçalho UDP) = 65507. MEDIDO 01/10 (fatia 2): mandar
     *  65535 passa da RECUSA do front (65535 &le; 65535) mas o SO responde
     *  "Mensagem muito longa" — o limite honesto é o do payload, não o do
     *  datagrama inteiro, senão o front aceita e o SO quebra (Q7: exceção
     *  engolida pelo send pareceria sucesso). Espelhado como constante aqui
     *  para os testes poderem citar o número sem duplicar o literal. */
    static final int DATAGRAM_MAX = 65507;

    static String source() {
        return """

                // ── kof.net sockets (D-KOF-NET, fatia 2) ─────────────────────
                /** Handle de listener TCP: guarda o ServerSocket de accept(). */
                public static final class NetListener {
                    final java.net.ServerSocket sock;
                    NetListener(java.net.ServerSocket s) { this.sock = s; }
                }

                /** Handle de conexão TCP (lado servidor E lado cliente). */
                public static final class NetConn {
                    final java.net.Socket sock;
                    NetConn(java.net.Socket s) { this.sock = s; }
                }

                /** Handle de endpoint UDP: guarda o DatagramSocket + o último
                 *  endereço de origem observado por receiveFrom. */
                public static final class NetEndpoint {
                    final java.net.DatagramSocket sock;
                    volatile String lastFrom;
                    NetEndpoint(java.net.DatagramSocket s) { this.sock = s; }
                }

                public static NetListener kof_net_listen(int port) {
                    try {
                        java.net.ServerSocket ss = new java.net.ServerSocket();
                        ss.setReuseAddress(true);
                        ss.bind(new java.net.InetSocketAddress("0.0.0.0", port), 64);
                        return new NetListener(ss);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: cannot listen on port " + port
                                + ": " + e.getMessage(), e);
                    }
                }

                public static NetConn kof_net_accept(NetListener l) {
                    if (l == null) throw new IllegalArgumentException("kof.net: null listener");
                    try {
                        java.net.Socket c = l.sock.accept();
                        return new NetConn(c);
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: accept failed: " + e.getMessage(), e);
                    }
                }

                public static NetConn kof_net_connect(String host, int port) {
                    try {
                        return new NetConn(new java.net.Socket(host, port));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: cannot connect to " + host + ":"
                                + port + ": " + e.getMessage(), e);
                    }
                }

                /** #759 / NET1 (D-MAINT-BATCH-0510): conecta ao endereço
                 *  NUMÉRICO já resolvido/validado pela guarda, mantendo `host`
                 *  para Host/SNI/cert. Um `address` em branco é recusado com
                 *  nome (NET004), nunca uma re-resolução silenciosa. */
                public static NetConn kof_net_connect_addr(String host, int port, String address) {
                    if (address == null || address.isBlank()) {
                        throw new RuntimeException("kof.net: connect to a vetted address needs a"
                                + " non-empty address (NET004)");
                    }
                    try {
                        return new NetConn(new java.net.Socket(address, port));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: cannot connect to " + address + ":"
                                + port + " (for " + host + "): " + e.getMessage(), e);
                    }
                }

                /** #759 / NET1: todos os endereços (A/AAAA) do host, na ordem do
                 *  SO — a guarda valida cada um antes de conectar. Um host
                 *  desconhecido lança String catchável (NET004). */
                public static java.util.ArrayList kof_net_resolve(String host) {
                    if (host == null || host.isBlank()) {
                        throw new RuntimeException("kof.net: resolve needs a host (NET004)");
                    }
                    try {
                        java.net.InetAddress[] all = java.net.InetAddress.getAllByName(host);
                        java.util.ArrayList out = new java.util.ArrayList();
                        for (java.net.InetAddress a : all) out.add(a.getHostAddress());
                        return out;
                    } catch (java.net.UnknownHostException e) {
                        throw new RuntimeException("kof.net: cannot resolve '" + host + "' (NET004)", e);
                    }
                }

                /** Envia pelo fluxo e devolve o total escrito (Int). */
                public static int kof_net_send(NetConn c, byte[] payload) {
                    if (c == null) throw new IllegalArgumentException("kof.net: null connection");
                    byte[] p = payload == null ? new byte[0] : payload;
                    try {
                        java.io.OutputStream out = c.sock.getOutputStream();
                        out.write(p);
                        out.flush();
                        return p.length;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: send failed: " + e.getMessage(), e);
                    }
                }

                /** Lê ATÉ maxBytes numa ÚNICA leitura e devolve o que chegou;
                 *  [] quando o par remoto fecha (EOF) — a forma honesta de
                 *  sinalizar fim-de-stream para um protocolo de framing em Kof.
                 *
                 *  MEDIDO 01/10 (fatia 2): encher maxBytes SEMPRE era um
                 *  deadlock em protocolos interativos — o worker esperava os
                 *  4096 bytes enquanto o cliente esperava o eco dos 4 que
                 *  mandou. A semântica correta de um verbo BLOQUEANTE é a do
                 *  `read` do socket: volta assim que HÁ dado. Quem precisa de N
                 *  bytes exatos acumula em Kof (é a camada de framing, não o
                 *  transporte). */
                public static byte[] kof_net_receive(NetConn c, int maxBytes) {
                    if (c == null) throw new IllegalArgumentException("kof.net: null connection");
                    int cap = maxBytes < 0 ? 0 : maxBytes;
                    if (cap == 0) return new byte[0];
                    try {
                        java.io.InputStream in = c.sock.getInputStream();
                        byte[] buf = new byte[cap];
                        int n = in.read(buf);
                        if (n <= 0) return new byte[0];
                        if (n == cap) return buf;
                        byte[] got = new byte[n];
                        System.arraycopy(buf, 0, got, 0, n);
                        return got;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: receive failed: " + e.getMessage(), e);
                    }
                }

                // ── UDP ───────────────────────────────────────────────────────

                /** Limite do payload UDP/IPv4 (D-KOF-NET): 65507 = 65535 − 20 (IPv4) − 8
                 *  (UDP). Payload maior é RECUSADO com código nomeado — nunca há
                 *  fragmentação transparente e o comportamento é idêntico por
                 *  alvo porque a recusa acontece ANTES do syscall (medido 01/10:
                 *  o SO também rejeita, mas com um erro que o front não
                 *  classificaria). O número é literal (não uma constante do
                 *  fragmento): o texto gerado é compilado isolado, então só
                 *  enxerga o que está dentro dele. */
                static void kof_net_check_datagram(byte[] p) {
                    if (p.length > 65507) {
                        throw new IllegalArgumentException("kof.net: datagram of " + p.length
                                + " bytes exceeds the 65507 byte UDP payload bound (NET003)");
                    }
                }

                /** "host:port" → {host, port}; o contrato v1 não usa colchetes
                 *  IPv6 (mesma escolha do subset RFC 3986 de `net.host`). */
                static java.net.InetSocketAddress kof_net_addr(String hostPort) {
                    if (hostPort == null) {
                        throw new IllegalArgumentException("kof.net: null host:port (NET004)");
                    }
                    int c = hostPort.lastIndexOf(':');
                    if (c < 0) {
                        throw new IllegalArgumentException("kof.net: address needs host:port, got "
                                + hostPort + " (NET004)");
                    }
                    try {
                        return new java.net.InetSocketAddress(hostPort.substring(0, c),
                                Integer.parseInt(hostPort.substring(c + 1)));
                    } catch (RuntimeException e) {
                        throw new IllegalArgumentException("kof.net: bad address "
                                + hostPort + " (NET004)", e);
                    }
                }

                public static NetEndpoint kof_net_bind(int port) {
                    try {
                        return new NetEndpoint(new java.net.DatagramSocket(
                                new java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), port)));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: cannot bind port " + port
                                + ": " + e.getMessage(), e);
                    }
                }

                public static int kof_net_sendTo(NetEndpoint e, String hostPort, byte[] payload) {
                    if (e == null) throw new IllegalArgumentException("kof.net: null endpoint");
                    byte[] p = payload == null ? new byte[0] : payload;
                    kof_net_check_datagram(p);
                    try {
                        e.sock.send(new java.net.DatagramPacket(p, p.length, kof_net_addr(hostPort)));
                        return p.length;
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException("kof.net: sendTo " + hostPort
                                + " failed: " + ex.getMessage(), ex);
                    }
                }

                /** Lê UM datagrama (UDP preserva a fronteira da mensagem) e
                 *  registra o endereço de origem; `kof_net_peer` o devolve depois
                 *  — o contrato é `receive` + `peer()`, sem record novo no v1. */
                public static byte[] kof_net_receiveFrom(NetEndpoint e, int maxBytes) {
                    if (e == null) throw new IllegalArgumentException("kof.net: null endpoint");
                    int cap = maxBytes < 0 ? 0 : maxBytes;
                    try {
                        byte[] buf = new byte[cap];
                        java.net.DatagramPacket dp = new java.net.DatagramPacket(buf, cap);
                        e.sock.receive(dp);
                        byte[] got = new byte[dp.getLength()];
                        System.arraycopy(dp.getData(), dp.getOffset(), got, 0, dp.getLength());
                        java.net.InetAddress from = dp.getAddress();
                        e.lastFrom = (from == null ? "" : from.getHostAddress())
                                + ":" + dp.getPort();
                        return got;
                    } catch (java.io.IOException ex) {
                        throw new RuntimeException("kof.net: receiveFrom failed: " + ex.getMessage(), ex);
                    }
                }

                /** Endereço de origem do ÚLTIMO receiveFrom, na forma "host:port"
                 *  que `sendTo` aceita (mesmo formato, ida e volta). */
                public static String kof_net_peer(NetEndpoint e) {
                    if (e == null) throw new IllegalArgumentException("kof.net: null endpoint");
                    return e.lastFrom == null ? "" : e.lastFrom;
                }

                /** close é o ÚNICO verbo compartilhado pelos três handles; cada
                 *  ramo checa o tipo real antes de fechar (nunca no objeto errado). */
                public static void kof_net_close(Object handle) {
                    if (handle == null) return;
                    try {
                        if (handle instanceof NetListener) {
                            ((NetListener) handle).sock.close();
                        } else if (handle instanceof NetConn) {
                            ((NetConn) handle).sock.close();
                        } else if (handle instanceof NetEndpoint) {
                            ((NetEndpoint) handle).sock.close();
                        } else {
                            throw new IllegalArgumentException(
                                    "kof.net: not a net handle: " + handle.getClass().getName()
                                            + " (NET005)");
                        }
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("kof.net: close failed: " + e.getMessage(), e);
                    }
                }
                """;
    }
}