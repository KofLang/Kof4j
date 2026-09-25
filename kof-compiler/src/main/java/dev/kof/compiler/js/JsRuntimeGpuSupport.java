package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — suporte de {@code kof.gpu} no JS (D-FULL-PARITY-050, row 6).
 *
 * Sem Vulkan no runtime JS, o alvo degrada honestamente com o MESMO contrato de
 * {@code JvmVkStubRuntime} (Android) e dos stubs cross/x86: {@code available()}
 * = false e os dispatch devolvem o codigo de fallback (nao-zero) para o caller
 * cair no golden CPU — nunca resultado errado em silencio (R6). As exportacoes
 * usam os nomes camelCase derivados de {@code JsTypeMapper.runtimeJsName}
 * ({@code kof_vk_available} -> {@code kofVkAvailable}).
 */
public final class JsRuntimeGpuSupport {
    private JsRuntimeGpuSupport() {
    }

    static String GPU_RUNTIME = """
            export function kofVkAvailable() {
                return false;
            }
            export function kofVkFailReason() {
                return "gpu: sem Vulkan no JS (fallback CPU)";
            }
            export function kofVkDispatch(a, b, c, m, n, k) {
                return -1;
            }
            export function kofVkDispatch64(a, b, c, m, n, k) {
                return -1;
            }
            export function kofMv64SetShape(m, k) {
                return -1;
            }
            export function kofMv64LoadW(w, m, k) {
                return -1;
            }
            export function kofMv64Matvec(x, y, m, k) {
                return -1;
            }
            export function kofMv64Wput(id, w, m, k) {
                return -1;
            }
            export function kofMv64Wrun(id, x, y, m, k, div) {
                return -1;
            }
            export function kofMv64Wput32(id, w, m, k) {
                return -6;
            }
            export function kofMv64Wrun32(id, x, y, m, k, div) {
                return -6;
            }
            export function kofMv64Wputsp(id, wh, wl, m, k) {
                return -6;
            }
            export function kofMv64Wrunsp(id, x, y, m, k, div) {
                return -6;
            }
            """;
}
