package dev.kof.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;

/**
 * Ponte de sockets do host JS para o front {@code kof.net} (D-KOF-NET, fatia 4a).
 *
 * <p>O alvo JS é GraalJS embarcado no JVM, então a paridade é por CONSTRUÇÃO:
 * este bridge usa as MESMAS classes {@code java.net} do runtime JVM
 * ({@link dev.kof.compiler.jvm.JvmRuntimeSockets}) — {@code ServerSocket}/
 * {@code Socket}/{@code DatagramSocket} — sobre a MESMA JVM do host. O
 * JavaScript gerado nunca alcança APIs de Node/browser: ele delega a
 * {@code kof_platform.net*}, exatamente como db/orm/process/ffi.
 *
 * <p>Handles são inteiros (índice no mapa do host), na mesma convenção do
 * {@code process.spawn} do {@link KofJsProcessBridge}. A tag é checada em cada
 * verbo (padrão {@code send} × {@code sendTo}); {@code close} é polimórfico.
 *
 * <p>Sem host (browser) o shim {@code kof_platform} lança erro honesto antes de
 * chegar aqui (R7) — este bridge nunca é um no-op silencioso.
 *
 * <p>Semântica espelhada de {@code JvmRuntimeSockets}: {@code receive} volta
 * assim que HÁ dado (nunca enche {@code maxBytes}), {@code []} no EOF; limite
 * UDP/IPv4 {@code 65507} recusado por nome (NET003) antes do syscall; endereço
 * {@code host:port} malformado recusado (NET004); tag errada no {@code close}
 * (NET005). As mensagens são as MESMAS strings do runtime JVM para que
 * {@code catch (String m)} veja texto idêntico nos dois alvos.
 */
public final class KofJsNetBridge {

    private KofJsNetBridge() {}

    private static final int TAG_LISTENER = 1;
    private static final int TAG_CONN = 2;
    private static final int TAG_ENDPOINT = 3;
    static final int DATAGRAM_MAX = 65507;

    private static final class Handle {
        final int tag;
        final Object sock;
        volatile String lastFrom;

        Handle(int tag, Object sock) {
            this.tag = tag;
            this.sock = sock;
        }
    }

    private static final Map<Integer, Handle> HANDLES = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Registra as faces de socket no mapa {@code kof_platform}. */
    public static void install(Map<String, Object> platform) {
        platform.put("netListen", (ProxyExecutable) args -> listen(args));
        platform.put("netAccept", (ProxyExecutable) args -> accept(args));
        platform.put("netConnect", (ProxyExecutable) args -> connect(args));
        platform.put("netBind", (ProxyExecutable) args -> bind(args));
        platform.put("netSend", (ProxyExecutable) args -> send(args));
        platform.put("netReceive", (ProxyExecutable) args -> receive(args));
        platform.put("netSendTo", (ProxyExecutable) args -> sendTo(args));
        platform.put("netReceiveFrom", (ProxyExecutable) args -> receiveFrom(args));
        platform.put("netPeer", (ProxyExecutable) args -> peer(args));
        platform.put("netClose", (ProxyExecutable) args -> close(args));
    }

    private static int register(int tag, Object sock) {
        int id = SEQ.incrementAndGet();
        HANDLES.put(id, new Handle(tag, sock));
        return id;
    }

    private static Handle require(int handle, int tag, String verb) {
        Handle h = HANDLES.get(handle);
        if (h == null) {
            throw new IllegalArgumentException("kof.net: invalid handle in " + verb + " (NET005)");
        }
        if (h.tag != tag) {
            throw new IllegalArgumentException("kof.net: " + verb + " on the wrong handle type (NET005)");
        }
        return h;
    }

    private static int listen(Value[] args) {
        int port = args[0].asInt();
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress("0.0.0.0", port), 64);
            return register(TAG_LISTENER, ss);
        } catch (IOException e) {
            throw new RuntimeException("kof.net: cannot listen on port " + port
                    + ": " + e.getMessage(), e);
        }
    }

    private static int accept(Value[] args) {
        Handle h = require(args[0].asInt(), TAG_LISTENER, "accept");
        try {
            Socket c = ((ServerSocket) h.sock).accept();
            return register(TAG_CONN, c);
        } catch (IOException e) {
            throw new RuntimeException("kof.net: accept failed: " + e.getMessage(), e);
        }
    }

    private static int connect(Value[] args) {
        String host = args[0].asString();
        int port = args[1].asInt();
        try {
            return register(TAG_CONN, new Socket(host, port));
        } catch (IOException e) {
            throw new RuntimeException("kof.net: cannot connect to " + host + ":"
                    + port + ": " + e.getMessage(), e);
        }
    }

    private static int bind(Value[] args) {
        int port = args[0].asInt();
        try {
            return register(TAG_ENDPOINT, new DatagramSocket(
                    new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port)));
        } catch (IOException e) {
            throw new RuntimeException("kof.net: cannot bind port " + port
                    + ": " + e.getMessage(), e);
        }
    }

    private static int send(Value[] args) {
        Handle h = require(args[0].asInt(), TAG_CONN, "send");
        byte[] p = bytes(args[1]);
        try {
            OutputStream out = ((Socket) h.sock).getOutputStream();
            out.write(p);
            out.flush();
            return p.length;
        } catch (IOException e) {
            throw new RuntimeException("kof.net: send failed: " + e.getMessage(), e);
        }
    }

    private static Object receive(Value[] args) {
        Handle h = require(args[0].asInt(), TAG_CONN, "receive");
        int cap = args[1].asInt();
        if (cap < 0) cap = 0;
        if (cap == 0) return new int[0];
        try {
            InputStream in = ((Socket) h.sock).getInputStream();
            byte[] buf = new byte[cap];
            int n = in.read(buf);
            if (n <= 0) return new int[0];
            return signed(buf, n);
        } catch (IOException e) {
            throw new RuntimeException("kof.net: receive failed: " + e.getMessage(), e);
        }
    }

    private static int sendTo(Value[] args) {
        Handle h = require(args[0].asInt(), TAG_ENDPOINT, "sendTo");
        String hostPort = args[1].asString();
        byte[] p = bytes(args[2]);
        if (p.length > DATAGRAM_MAX) {
            throw new IllegalArgumentException("kof.net: datagram of " + p.length
                    + " bytes exceeds the 65507 byte UDP payload bound (NET003)");
        }
        try {
            DatagramSocket sock = (DatagramSocket) h.sock;
            sock.send(new DatagramPacket(p, p.length, addr(hostPort)));
            return p.length;
        } catch (IOException e) {
            throw new RuntimeException("kof.net: sendTo " + hostPort
                    + " failed: " + e.getMessage(), e);
        }
    }

    private static Object receiveFrom(Value[] args) {
        Handle h = require(args[0].asInt(), TAG_ENDPOINT, "receiveFrom");
        int cap = args[1].asInt();
        if (cap < 0) cap = 0;
        try {
            byte[] buf = new byte[cap];
            DatagramPacket dp = new DatagramPacket(buf, cap);
            ((DatagramSocket) h.sock).receive(dp);
            InetAddress from = dp.getAddress();
            h.lastFrom = (from == null ? "" : from.getHostAddress()) + ":" + dp.getPort();
            return signed(dp.getData(), dp.getLength());
        } catch (IOException e) {
            throw new RuntimeException("kof.net: receiveFrom failed: " + e.getMessage(), e);
        }
    }

    private static String peer(Value[] args) {
        Handle h = require(args[0].asInt(), TAG_ENDPOINT, "peer");
        return h.lastFrom == null ? "" : h.lastFrom;
    }

    private static Object close(Value[] args) {
        int handle = args[0].asInt();
        Handle h = HANDLES.remove(handle);
        if (h == null) {
            return null;
        }
        try {
            if (h.tag == TAG_LISTENER) {
                ((ServerSocket) h.sock).close();
            } else if (h.tag == TAG_CONN) {
                ((Socket) h.sock).close();
            } else if (h.tag == TAG_ENDPOINT) {
                ((DatagramSocket) h.sock).close();
            } else {
                throw new IllegalArgumentException(
                        "kof.net: not a net handle: tag " + h.tag + " (NET005)");
            }
        } catch (IOException e) {
            throw new RuntimeException("kof.net: close failed: " + e.getMessage(), e);
        }
        return null;
    }

    /** {@code "host:port"} → {@link InetSocketAddress}; v1 sem colchetes IPv6
     *  (mesmo subset do {@code net.host}). */
    private static InetSocketAddress addr(String hostPort) {
        if (hostPort == null) {
            throw new IllegalArgumentException("kof.net: null host:port (NET004)");
        }
        int c = hostPort.lastIndexOf(':');
        if (c < 0) {
            throw new IllegalArgumentException("kof.net: address needs host:port, got "
                    + hostPort + " (NET004)");
        }
        try {
            return new InetSocketAddress(hostPort.substring(0, c),
                    Integer.parseInt(hostPort.substring(c + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("kof.net: bad address "
                    + hostPort + " (NET004)", e);
        }
    }

    private static byte[] bytes(Value arrayArg) {
        if (arrayArg == null || arrayArg.isNull()) {
            return new byte[0];
        }
        long n = arrayArg.getArraySize();
        if (n > Integer.MAX_VALUE) {
            throw new RuntimeException("lista excede o limite da ponte JS (" + n + ")");
        }
        byte[] out = new byte[(int) n];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (arrayArg.getArrayElement(i).asInt() & 0xFF);
        }
        return out;
    }

    /** Byte[] do Kof no JS é o array de números assinados do alvo (o JVM
     *  imprime {@code Byte 200} como {@code -56}); devolver 0..255 divergiria
     *  a impressão byte-a-byte. */
    private static int[] signed(byte[] buf, int len) {
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = buf[i];
        }
        return out;
    }
}
