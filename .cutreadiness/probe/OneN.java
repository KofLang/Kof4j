import dev.kof.compiler.*; import java.nio.file.*;
public class OneN { public static void main(String[] a) throws Exception {
  Path p = Paths.get(a[0]); CompilerDriver d = new CompilerDriver();
  CompilationResult r = d.compile(p, Paths.get(a[1]), Target.NATIVE);
  System.out.println("ok=" + r.success() + r.diagnostics().getDiagnostics());
}}
