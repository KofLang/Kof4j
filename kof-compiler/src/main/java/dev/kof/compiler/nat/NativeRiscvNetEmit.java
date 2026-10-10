package dev.kof.compiler.nat;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.Set;

// D-KOF-NET fatia 4b (plan docs/stdlib/network-kofnet-plan.md): selecao e
// emissao do front `kof.net` no riscv64 (aarch64 herda pelo tradutor). Port do
// front x86-64 (NativeNetFront*) — handles opacos de 48 bytes e os dez verbos.
// Só entra no binario quando o programa chama um verbo de socket; as faces URI
// do mesmo namespace continuam vindo do runtime base. Extraido de NativeBackend
// para respeitar o limite de 500 linhas (check_500).
public final class NativeRiscvNetEmit {

    private NativeRiscvNetEmit() {}

    private static final Set<String> NET_VERBS = Set.of(
            "kof_net_listen", "kof_net_accept", "kof_net_connect", "kof_net_connect_addr",
            "kof_net_resolve", "kof_net_bind",
            "kof_net_send", "kof_net_sendTo", "kof_net_receive",
            "kof_net_receiveFrom", "kof_net_peer", "kof_net_close");

    static boolean usesNet(IRModule module) {
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc && NET_VERBS.contains(kc.methodName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static void emit(StringBuilder sb) {
        sb.append(NativeRiscvAsmNetCore.RISCV_RUNTIME_ASM_NET_CORE);
        sb.append(NativeRiscvAsmNetTcp.RISCV_RUNTIME_ASM_NET_TCP);
        sb.append(NativeRiscvAsmNetUdp.RISCV_RUNTIME_ASM_NET_UDP);
    }
}
