package dev.kof.compiler.nat;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

import java.util.List;

/**
 * kof.web server para o target Nativo (WEB002).
 *
 * T1 (feito): accept loop + resposta "hello" 200.
 * T2 (aqui): parse METHOD+PATH na request line e match contra rotas
 *            registradas; handler só é invocado no T3.
 * T3 (próximo): dispatch do lambda com trampolim (objeto→vtable[0]→invoke).
 *
 * Módulo separado (≤500 linhas — AGENTS.md 03/09). Fragmentos
 * (REFACTOR-500 Fase 8): NativeWebCore (dados + primitivas),
 * NativeWebListen (listen + handle_client), NativeWebResponses (helpers de
 * resposta) e NativeWebQuery (a query string da request — §1159 21/11).
 * A concatenação abaixo preserva o assembly injetado byte-a-byte.
 */
public final class NativeWebRuntime {

    private NativeWebRuntime() {}

    static void emitWebFunctions(StringBuilder sb) {
        sb.append(NativeWebCore.source());
        sb.append(NativeWebListen.source());
        sb.append(NativeWebResponses.source());
        sb.append(NativeWebQuery.source());
    }

    /**
     * §1159 (21/11): o runtime web nativo é emitido SÓ quando o programa o
     * usa.
     *
     * <p>MEDIDO: o runtime web era emitido INCONDICIONALMENTE em todo binário
     * nativo — um programa "hello" carregava ~21 símbolos {@code kof_web_*}
     * (o gate {@code ArtifactSizeTest.helloX86NativeSizeWithinBaseline} exige
     * &lt;110 símbolos {@code kof_*} e estourava em 119). Nenhuma fatia do
     * runtime referencia um símbolo {@code kof_web_*} (o runtime web é
     * autossuficiente), então a emissão condicional é segura.
     *
     * <p>A detecção é pelo IR: toda chamada web é baixada com um nome
     * {@code kof_web_*} (o contexto, o app, as rotas, o TLS, o ws/sse), então
     * o prefixo cobre todas as faces — inclusive o inject FLAT de
     * {@code import kof.web}, cujo {@code pageRequest} chama
     * {@code kof_web_query}.
     */
    static boolean usedBy(IRModule module) {
        if (module == null) return false;
        for (IRClass clazz : module.classes()) {
            for (IRMethod method : clazz.methods()) {
                for (IRBasicBlock block : method.basicBlocks()) {
                    List<KofOperation> ops = block.operations();
                    for (int i = 0; i < ops.size(); i++) {
                        KofOperation op = ops.get(i);
                        if (op instanceof KofCall kc && kc.methodName().startsWith("kof_web_")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
