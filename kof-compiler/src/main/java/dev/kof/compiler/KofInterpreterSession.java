package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * #739 — execução incremental do interpretador (REPL do KofScript).
 * <p>
 * Separado de {@link KofInterpreter} (gate 500): avalia um módulo REUSANDO os
 * estáticos de avaliações anteriores (`sharedStatics`) e um {@code workDir}
 * fixo por sessão, para que os globais de topo sobrevivam entre linhas sem
 * reexecutar o histórico. A saída é capturada como em {@link KofInterpreter#run}.
 */
public final class KofInterpreterSession {

    private KofInterpreterSession() {}

    public static KofInterpreter.Result runIncremental(
            IRModule module, String[] args, List<Diagnostic> warnings,
            Path sharedWorkDir, Map<String, Map<String, Object>> sharedStatics) {
        ByteArrayOutputStream so = new ByteArrayOutputStream();
        ByteArrayOutputStream se = new ByteArrayOutputStream();
        PrintStream po = new PrintStream(so, true);
        PrintStream pe = new PrintStream(se, true);
        KofInterpreter interp = new KofInterpreter(module, po, pe, sharedWorkDir, sharedStatics);
        int code = 0;
        try {
            interp.ensureRuntimeForModule();
            interp.execute(args == null ? new String[0] : args);
        } catch (Throwable t) {
            code = 1;
            pe.println(KofInterpreter.kofErrorMessage(t));
            if (System.getProperty("kof.interp.trace") != null) t.printStackTrace(pe);
        } finally {
            po.flush();
            pe.flush();
        }
        return new KofInterpreter.Result(code, so.toString(), se.toString(),
                warnings == null ? List.of() : warnings);
    }
}
