package dev.kof.cli;

import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import dev.kof.compiler.TargetMatrix;
import dev.kof.compiler.nat.NativeProfile;

/**
 * B-1 (PLAN-BAREMETAL-BOOT): a flag {@code --profile host|freestanding}.
 *
 * <p>Traduz o valor do CLI no {@link NativeProfile} e valida a combinação
 * com o alvo <b>antes</b> de compilar. O perfil só tem efeito em
 * {@code --target native} (x86_64) — nos demais alvos seria um no-op
 * silencioso, então é recusado com mensagem nomeada (R6). Um valor fora de
 * {@code host|freestanding} também é recusado, nunca ignorado.
 *
 * <p>M-1 (#1135, D-NATIVE-MACOS 17/11): o perfil {@code macos} abre o alvo
 * Mach-O (arm64) — o formato do objeto e a linha de link mudam; o código
 * emitido é o mesmo.
 */
final class BuildProfileFlag {

    private BuildProfileFlag() {
    }

    /** Mapeia {@code host|freestanding|macos}; {@code null} = valor inválido (R6).
     *  M-1 (#1135): o perfil {@code macos} abre o alvo Mach-O (a face do
     *  emissor: x86-64). */
    static NativeProfile parse(String value) {
        return switch (value) {
            case "host" -> NativeProfile.HOST;
            case "freestanding" -> NativeProfile.FREESTANDING;
            // M-1 (#1135, D-NATIVE-MACOS): o alvo macOS (Mach-O + libSystem).
            case "macos" -> NativeProfile.MACOS;
            default -> null;
        };
    }

    /** Valida o par (target, profile); devolve a mensagem de erro (R6) ou
     *  {@code null} se aceito. Extraído para teste — o caminho de erro do CLI
     *  faz {@code System.exit}. {@code command} é o prefixo da mensagem
     *  ({@code build}/{@code run}). */
    static String error(String command, Target target, String profileArg) {
        if (target != Target.NATIVE) {
            return command + ": --profile only applies to --target native (x86_64) (target: "
                    + TargetMatrix.name(target) + ")";
        }
        if (parse(profileArg) == null) {
            return command + ": --profile invalid: '" + profileArg
                    + "' (expected host|freestanding|macos)";
        }
        return null;
    }

    /** Valida e aplica a flag no driver. Devolve a mensagem de erro (R6) ou
     *  {@code null}; {@code profileArg == null} (flag ausente) é no-op. */
    static String apply(CompilerDriver driver, String command, Target target, String profileArg) {
        if (profileArg == null) return null;
        String err = error(command, target, profileArg);
        if (err != null) return err;
        driver.setNativeProfile(parse(profileArg));
        return null;
    }
}
