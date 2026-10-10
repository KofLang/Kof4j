package dev.kof.compiler.nat;

import java.nio.charset.StandardCharsets;

/**
 * §623: escape de literais de string para o assembler GAS. O frontend resolve
 * {@code "\0"} para um byte NUL real; emitido cru dentro de {@code .asciz "…"}
 * ele quebra o GAS ({@code invalid character}). Aqui todo byte de controle é
 * escapado como octal de 3 dígitos ({@code \000}), que é inambíguo mesmo
 * seguido de outro dígito, e os bytes UTF-8 passam intactos (GAS copia o byte
 * literal), preservando a codificação que o runtime nativo já assume.
 */
final class NativeGasStrings {

    private NativeGasStrings() {}

    static String gasEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 8);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            int b = value & 0xFF;
            switch (b) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '\r' -> out.append("\\r");
                default -> {
                    if (b >= 0x20 && b < 0x7F) {
                        out.append((char) b);
                    } else {
                        out.append('\\')
                           .append((char) ('0' + ((b >> 6) & 7)))
                           .append((char) ('0' + ((b >> 3) & 7)))
                           .append((char) ('0' + (b & 7)));
                    }
                }
            }
        }
        return out.toString();
    }
}
