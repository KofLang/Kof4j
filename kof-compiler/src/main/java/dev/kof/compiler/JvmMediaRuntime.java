package dev.kof.compiler;

/**
 * Runtime de kof.media — imagens (javax.imageio), áudio/WAV e microfone
 * (javax.sound.sampled). Gerado no KofRuntime junto com JvmRuntime;
 * separado em arquivo próprio pelo mesmo motivo do JvmWebRuntime (limite
 * de 65535 bytes por string constant pool).
 *
 * Filosofia: a linguagem NÃO transporta imagem/áudio como String gigante
 * (nem base64 literal no fonte, nem data-URI colado à mão). O app trata o
 * ARQUIVO: abre, manipula, salva. Web server entrega o arquivo do disco
 * com content-type correto (serveDir). O data-URI só existe como opção
 * explícita (img.dataUri()) para o caso em que o destino só aceita URI.
 * REFACTOR-500 Fase 8: o source foi dividido em fragmentos (classes
 * Jvm*Part) no mesmo pacote; a concatenacao preserva byte-a-byte.
 */
final class JvmMediaRuntime {

    private JvmMediaRuntime() {}

    static String source() {
        return JvmMediaCoreRuntime.source() + JvmMediaWebRuntime.source();
    }
}
