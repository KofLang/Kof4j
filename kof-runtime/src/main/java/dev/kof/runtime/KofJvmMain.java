package dev.kof.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * KofJvmMain — launcher de diagnóstico para `kof run --target jvm` (§556).
 *
 * O launcher da JVM ({@code sun.launcher.LauncherHelper}) não imprime a causa
 * real quando a classe principal falha ao carregar/verificar: ele tenta
 * {@code MethodFinder.findMainMethod}, o {@code getDeclaredMethods()} dispara
 * o {@code VerifyError}/{@code NoClassDefFoundError} e a JDK cai no ramo
 * JavaFX, imprimindo "os componentes de runtime do JavaFX não foram
 * encontrados" — uma mensagem FALSA (produtos Kof não exigem JavaFX) que
 * destrói o diagnóstico de qualquer defeito de codegen/interop.
 *
 * Esta classe é carregada ANTES da classe do usuário e invoca o {@code main}
 * por reflexão dentro de um {@code try}, então a causa real
 * ({@code Throwable} + stack trace) é impressa em stderr. É um wrapper de
 * tooling, sem semântica nova: o programa do usuário continua sendo o
 * {@code main([String])}.
 */
public final class KofJvmMain {

    private KofJvmMain() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("kof run: missing main class name");
            System.exit(1);
            return;
        }
        String className = args[0];
        String[] programArgs = Arrays.copyOfRange(args, 1, args.length);
        Class<?> mainClass;
        try {
            mainClass = Class.forName(className, true, KofJvmMain.class.getClassLoader());
        } catch (Throwable t) {
            fail("could not load main class " + className, t);
            return;
        }
        Method main;
        try {
            main = mainClass.getDeclaredMethod("main", String[].class);
        } catch (Throwable t) {
            fail("could not resolve main([String]) in " + className, t);
            return;
        }
        try {
            main.invoke(null, (Object) programArgs);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            cause.printStackTrace(System.err);
            System.exit(1);
        } catch (Throwable t) {
            fail("could not invoke main in " + className, t);
        }
    }

    private static void fail(String what, Throwable t) {
        System.err.println("kof run: " + what);
        t.printStackTrace(System.err);
        System.exit(1);
    }
}
