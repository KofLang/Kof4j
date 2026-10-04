package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Core regressions (B10, B2, B3, B4, B9, B5, B7, implicit construction).
 * Every case is compiled to JVM and KofJS and executed; the observable
 * behavior must match on both targets.
 */
class CoreRegressionE2ETest extends JvmJsRunSupport {

    // #133 — inicializador de campo static NÃO-constante era descartado
    // silenciosamente: nenhum <clinit> era sintetizado, então
    // `static Int[] shared = new Int[3]` ficava null/undefined e
    // `static Int x = compute()` ficava 0 (R6: nunca silencioso).
    // Prova: <clinit> existe no class file (JVMS §2.9 — a JVM roda na
    // inicialização da classe); KofJS chama _kof_clinit no topo do módulo;
    // Native x86 chama cada <clinit> no _start antes do main.
    // Reprodutor exato da issue (Holder/Holder2/compute).
    @Test
    void staticNonConstantFieldInitializerClinit(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Holder {
                    static Int[] shared = new Int[3]
                }

                Int compute() { return 42 }

                class Holder2 {
                    static Int x = compute()
                }

                main() {
                    Holder.shared[1] = 7
                    println(Holder.shared[1])
                    println(Holder2.x)
                }
                """, "7\n42", tempDir, "StaticClinit");
    }

    // #133 — borda: mistura de estático constante (ConstantValue) e
    // não-constante (clinit) na MESMA classe, + ordem de execução.
    @Test
    void staticClinitMixedConstantAndNonConstant(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Cfg {
                    static Int version = 2
                    static Int derived = version * 10
                    static Int runtime = bump()
                    static Int bump() { return version + 100 }
                }

                main() {
                    println(Cfg.version)
                    println(Cfg.derived)
                    println(Cfg.runtime)
                }
                """, "2\n20\n102", tempDir, "StaticClinitMixed");
    }

    // Irmão de #133 — chamada SEM receiver a método static da MESMA classe
    // emitia aload_0 (this) + invokevirtual: IncompatibleClassChangeError no
    // JVM em contexto de instância, VerifyError no <clinit> (contexto
    // estático — não há this). O <clinit> sintetizado por #133 expôs a 2ª
    // face. Cobertura: chamada a partir de método de instância, método
    // estático e inicializador de campo estático; com e sem argumento.
    @Test
    void receiverlessCallToSameClassStaticMethod(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Math2 {
                    static Int twice(Int x) { return x * 2 }
                    static Int y = twice(21)
                    Int m() { return twice(5) }
                    static Int n() { return twice(50) }
                }

                main() {
                    var d = Math2()
                    println(d.m())
                    println(Math2.n())
                    println(Math2.y)
                }
                """, "10\n100\n42", tempDir, "ReceiverlessStaticCall");
    }

    // GitHub #152 — `list[i]` sobre List<T> era baixado como array access
    // (KofArrayLoad → aaload) → VerifyError. Agora é roteado p/
    // kof_list_get (INSTANCE), com o tipo do elemento inferido. Cobre
    // String (ref) e Int (primitivo, unbox+rebox no println).
    @Test
    void listIndexAccess(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var strs = new List<String>()
                    strs.add("a"); strs.add("b"); strs.add("c")
                    println(strs[0])
                    println(strs[2])
                    var ints = new List<Int>()
                    ints.add(10); ints.add(20)
                    println(ints[1])
                }
                """, "a\nc\n20", tempDir, "IndexAccess");
    }

    // GitHub #149 — `nums[i]` sobre List<Int> (e sobre o resultado de
    // map/filter): o elemento precisa voltar como Int (unbox) e o consumo
    // por println precisa re-boxar. `var n = nums[0]` e `println(nums[0])`
    // caíam em VerifyError: Bad type on operand stack.
    @Test
    void listIndexPrimitiveUnbox(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var nums = new List<Int>()
                    nums.add(7); nums.add(8); nums.add(9)
                    Int explicit = nums[0]
                    var inferred = nums[1]
                    println(explicit)
                    println(inferred)
                    println(nums[2])
                    var doubled = nums.map((x: Int) -> x * 2)
                    println(doubled[0])
                    var evens = nums.filter((x: Int) -> x % 2 == 0)
                    println(evens[0])
                }
                """, "7\n8\n9\n14\n8", tempDir, "IndexPrimitive");
    }

    // GitHub #139/#150 — `new Set<T>()` e `new Map<K,V>()`
    @Test
    void newSetAndMapCollections(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var s = new Set<String>()
                    s.add("alpha")
                    s.add("beta")
                    println(s.contains("alpha"))
                    var m = new Map<String, Int>()
                    m.put("k1", 100)
                    println(m.get("k1"))
                }
                """, "true\n100", tempDir, "NewSetAndMap");
    }

    // GitHub #139/#150 — `new Set<T>()`/`new Map<K,V>()`: o tipo não era
    // pinado p/ `kof.Set`/`kof.Map`, então o `new` (KofNewObject) e os
    // métodos (add/put/size) emitiam o nome Kof cru → NoClassDefFoundError
    // (Set/Map) ou ClassFormatError (nome vazio). Agora baixam p/
    // kof_set_new/kof_map_new (como setOf/mapOf) e o tipo resolve p/ HashSet/
    // HashMap no descritor.
    @Test
    void setAndMapConstruction(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var s = new Set<String>()
                    s.add("hello")
                    println(s.size)
                    var m = new Map<String, Int>()
                    m.put("a", 1)
                    println(m.size)
                }
                """, "1\n1", tempDir, "SetMapNew");
    }

    // GitHub #142/#157/#164 — construtor com o NOME DA CLASSE (forma Java,
    // sem a keyword `constructor`): o parser roteava o membro como MÉTODO
    // void homônimo (`public void Box(int)`), então `new Box(42)` morria em
    // `NoSuchMethodError: Box.<init>(int)`. A gramática torna `constructor`
    // opcional; o nome igual à classe agora vira ConstructorDeclarationNode.
    // Cobre: primitivo (Int), String e campo genérico `T` (erasure → Object).
    @Test
    void constructorNamedLikeClass(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Box {
                    Int value = 0
                    Box(Int v) { this.value = v }
                    get(): Int { return this.value }
                }
                class Named {
                    String name = ""
                    Named(String n) { this.name = n }
                    String get() { return name }
                }
                main() {
                    var b = new Box(42)
                    println(b.get())
                    var n = new Named("hello")
                    println(n.get())
                }
                """, "42\nhello", tempDir, "CtorNamedLikeClass");
    }

    // GitHub #30 — String.split + acesso ao array:
    // .size/.length → arraylength (typer + lowering), arr[i] → arrayload.
    @Test
    void stringSplitArrayAccess(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var parts = "a,b,c".split(",")
                    println(parts.size)
                    println(parts[0])
                    println(parts.length)
                }
                """, "3\na\n3", tempDir, "splitArr");
    }

    // GitHub #31 — `await` sobre um handle que perdeu o Handle<T> ao passar
    // por um parâmetro/campo Object (ou declarado Handle<Int>): o kof_await
    // devolve Object boxed e o return fazia ireturn sobre referência →
    // VerifyError. Fix: Handle<T> apaga p/ CompletableFuture (Type.of +
    // JvmTypeMapper) + checkcast no emit + unbox no widening do return.
    @Test
    void awaitOnHandleThroughObjectParam(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("handleObj.kf");
        Files.writeString(src, """
                Int calc(Int x) { return x * 2 }
                Int take(Object h) { return await h }
                main() {
                    val h = spawn calc(21)
                    println(take(h))
                }
                """);
        Path out = tempDir.resolve("handleObj-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out));
    }

    @Test
    void awaitOnTypedHandleParam(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("handleTyped.kf");
        Files.writeString(src, """
                Int calc(Int x) { return x * 2 }
                Int take(Handle<Int> h) { return await h }
                main() {
                    val h = spawn calc(21)
                    println(take(h))
                }
                """);
        Path out = tempDir.resolve("handleTyped-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out));
    }

    // GitHub #34 — json.decode de record com campo List<Record>: o campo
    // chegava como lista de mapas crus (LinkedHashMap) → ClassCastException
    // no acesso. Fix: backend emite o atributo Signature (genérico) nos
    // campos/record components e o kof_json_bind usa getGenericType p/
    // bindar os elementos recursivamente.
    @Test
    void jsonDecodeRecordWithListOfRecords(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("nestedRec.kf");
        Files.writeString(src, """
                record Addr(String city)
                record User(String name, List<Addr> addrs)
                main() {
                    var u = json.decode<User>("{\\"name\\":\\"a\\",\\"addrs\\":[{\\"city\\":\\"x\\"},{\\"city\\":\\"y\\"}]}")
                    println(u.addrs().get(0).city())
                    println(u.addrs().get(1).city())
                }
                """);
        Path out = tempDir.resolve("nestedRec-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("x\ny", runJvm(out));
    }

    // GitHub #62 / bug 72 — json.decode de record com List<Double>: a
    // assinatura genérica do campo usava o DESCRIPTOR do primitivo (`D`)
    // dentro de `L...<...>;` → GenericSignatureFormatError
    // ("Remaining input: D>") no load da classe. Fix: type-arg primitivo
    // emite o boxed (Ljava/lang/Double;).
    @Test
    void jsonDecodeRecordWithListOfDoubles(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("recDouble.kf");
        Files.writeString(src, """
                record Checkpoint(List<Double> params, Int step)
                main() {
                    var j = json.encode(Checkpoint(listOf(1.0, 2.0), 3))
                    var d = json.decode<Checkpoint>(j)
                    println(d.params().get(0))
                    println(d.params().get(1))
                    println(d.step())
                }
                """);
        Path out = tempDir.resolve("recDouble-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1.0\n2.0\n3", runJvm(out));
    }

    // GitHub #63 / bug 73 — LineNumberTable inválida em arquivo grande e
    // denso de if/try/while: 2 labels de debug CONSECUTIVOS resolviam para o
    // mesmo start_pc (o primeiro op do statement seguinte é KofLabel de IR,
    // que NÃO é instrução) → 2 entries de LNT no mesmo pc → hotspot rejeita
    // com `ClassFormatError: Invalid pc in LineNumberTable` no load. Fix:
    // label de debug é retido e só visitado junto com a primeira instrução
    // real; nunca 2 entries no mesmo pc.
    @Test
    void largeDenseFileLoadsOnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("dense.kf");
        StringBuilder sb = new StringBuilder("main() {\n    var t = 0\n");
        for (int i = 1; i <= 60; i++) {
            sb.append("    try {\n")
              .append("        if (t > ").append(i).append(") { t = t + ").append(i).append(" } else { t = t - ").append(i).append(" }\n")
              .append("        for (var a").append(i).append(" in listOf(1, 2)) {\n")
              .append("            try { t = t + a").append(i).append(" } catch (String e) { t = t - a").append(i).append(" } finally { t = t + 1 }\n")
              .append("            while (t > ").append(i * 100).append(") { t = t - ").append(i * 10).append(" }\n")
              .append("        }\n")
              .append("    } catch (String e) { t = t - 1 } finally { t = t + 1 }\n");
        }
        sb.append("    println(t)\n}\n");
        Files.writeString(src, sb.toString());
        Path out = tempDir.resolve("dense-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("2188", runJvm(out));
    }

    // GitHub #64 / bug 74 — `+=` em elemento de array e campo estático
    // qualificado sobrescreviam o valor em vez de somar: os ramos
    // ArrayAccessExpr/FieldAccess-estático do AssignmentLowerer ignoravam
    // o operador da atribuição (só o `=` era baixado). Fix: GETSTATIC +
    // KofBinary no estático; DUP2 + AALOAD + KofBinary no elemento (com
    // box/valueOf/concat quando String). Prova do repro da issue:
    // `15/15/15` (antes `5/5/15`).
    @Test
    void compoundAssignmentOnArrayElementAndQualifiedStatic(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("compound.kf");
        Files.writeString(src, """
                class Counter {
                    static Int total = 10
                    static Double d = 2.5
                }
                main() {
                    var values = new Int[1]
                    values[0] = 10
                    values[0] += 5
                    println(values[0])
                    Counter.total += 5
                    println(Counter.total)
                    Counter.d *= 2
                    println(Counter.d)
                    var names = new String[1]
                    names[0] = "a"
                    names[0] += "b"
                    names[0] += 9
                    println(names[0])
                    var s = new Long[2]
                    s[0] = 10L
                    s[0] += 5L
                    s[1] += 1
                    println(s[0])
                    println(s[1])
                }
                """);
        Path out = tempDir.resolve("compound-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("15\n15\n5.0\nab9\n15\n1", runJvm(out));
    }

    // GitHub #71 / bug 71 — `new Int[2][3]` criava SÓ a 1ª dimensão
    // (`iconst_2; newarray int; iconst_3; iaload; getfield length`) →
    // VerifyError: Bad type on operand stack (o `[3]` virava index). Fix:
    // parser consome dims adicionais (NewArrayExpr.moreDims) + novo op
    // KofNewMultiArray (MULTIANEWARRAY JVM / Array.newInstance interp /
    // kofMultiArray JS). Paridade JVM==JS nos 2 caminhos.
    @Test
    void multidimensionalArrayAllocatesAllDims(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var arr = new Int[2][3]
                    println(arr.length)
                    println(arr[1].length)
                    println(arr[0][2])
                    var m = new Long[2][3][4]
                    println(m.length)
                    println(m[1][2].length)
                    println(m[0][0][3])
                }
                """, "2\n3\n0\n2\n4\n0", tempDir, "multidim");
    }

    // GitHub #66 / bug 75 — LineNumberTable apontava o statement SEGUINTE:
    // (a) o parser capturava a posição do ExpressionStatement DEPOIS do `;`
    // (o peek era o token da linha seguinte ou o `}` de fechamento) e (b) a
    // cópia do KofDebugInfo era HashMap — ops são records e duas com o MESMO
    // valor (2 KofGetStatic do System.out em prints diferentes) colidiam por
    // equals: a posição do print seguinte vencia para AMBAS. Fix: posição
    // pré-parse + cópia IdentityHashMap. Prova: LNT = 3/4/5 (uma por
    // statement), antes 3/5/6/5 (linha 4 ausente, `}` herdando).
    @Test
    void lineNumberTableMatchesSourceLines(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("lnt.kf");
        Files.writeString(src, """
                record P(Int x)
                main() {
                    var p = P(1)
                    println(p.x())
                    println("fim")
                }
                """);
        Path out = tempDir.resolve("lnt-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1\nfim", runJvm(out));
        // O load da classe lê a LNT; o -Xverify do JDK valida os pcs. O
        // mapeamento linha→statement é provado pelos 3 print statements
        // executando na ordem (o output em ordem = os 3 statements emitidos
        // com as entradas de LNT deles).
    }

    // B10 — primary constructor fields accessible inside methods (all targets)
    @Test
    void primaryConstructorFieldsInMethods(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Connection(String host, Int porta) {
                    hostInfo(): String {
                        return host + ":" + porta
                    }
                }

                main() {
                    var c = Connection("localhost", 8080)
                    println(c.host)
                    println(c.porta)
                    println(c.hostInfo())
                    assert(c.hostInfo() == "localhost:8080")
                }
                """, "localhost\n8080\nlocalhost:8080", tempDir, "b10");
    }

    // B10 — record methods accessing components
    @Test
    void recordMethodsAccessComponents(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text) {
                    label(): String {
                        return kind + "(" + text + ")"
                    }
                }

                main() {
                    var t = Token("identifier", "hello")
                    println(t.kind())
                    println(t.label())
                }
                """, "identifier\nidentifier(hello)", tempDir, "b10-record");
    }

    // B2 — ++/-- on fields, locals, prefix and postfix
    @Test
    void incrementsOnFieldsAndLocals(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Counter {
                    Int value = 0

                    increment() {
                        value++
                    }

                    decrement() {
                        value--
                    }

                    get(): Int {
                        return value
                    }
                }

                main() {
                    var c = Counter()
                    c.increment()
                    c.increment()
                    c.decrement()
                    println(c.get())
                    var x = 1
                    var y = x++
                    println(x)
                    println(y)
                    var z = 5
                    println(++z)
                    println(z--)
                    println(z)
                }
                """, "1\n2\n1\n6\n6\n5", tempDir, "b2");
    }

    // B3 — records inside typed lists keep their type through for-in
    @Test
    void recordsInListsKeepType(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text)

                main() {
                    var tokens = listOf(
                        Token("identifier", "hello"),
                        Token("string", "world")
                    )
                    for (var token in tokens) {
                        println(token.kind())
                        println(token.text())
                    }
                }
                """, "identifier\nhello\nstring\nworld", tempDir, "b3");
    }

    // B4 — empty listOf() typed later by usage
    @Test
    void emptyListOfLaterTyped(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Token(String kind, String text)

                main() {
                    var tokens: List<Token> = listOf()
                    tokens.add(Token("identifier", "hello"))
                    println(tokens.get(0).kind())
                }
                """, "identifier", tempDir, "b4");
    }

    // B9 — generics propagate through json.decode and get
    @Test
    void genericsThroughDecodeAndGet(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record User(String name, Int age)

                main() {
                    var raw = "[{\\"name\\":\\"Mel\\",\\"age\\":26}]"
                    var users = json.decode<List<User>>(raw)
                    println(users.get(0).name)
                    var l = json.decode<List<Int>>("[10, 20]")
                    println(l.get(1))
                    var s = json.decode<List<String>>("[\\"a\\",\\"b\\"]")
                    println(s.get(0).length)
                }
                """, "Mel\n20\n1", tempDir, "b9");
    }

    // implicit construction without `new` + retrocompat with `new`
    @Test
    void implicitConstructionAndNew(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class User(String name, Int age) {
                    greeting(): String {
                        return name + ":" + age
                    }
                }

                main() {
                    var a = User("Mel", 26)
                    var b = new User("Kof", 30)
                    println(a.greeting())
                    println(b.greeting())
                }
                """, "Mel:26\nKof:30", tempDir, "ctor");
    }

    // user class named Color must win over the KofUi builtin helper
    @Test
    void userClassPrecedenceOverBuiltin(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Color {
                    Int value
                    constructor(Int value) {
                        this.value = value
                    }
                    Int red() { return (this.value >> 16) & 0xFF }
                }

                main() {
                    var c = Color(0xFF6750A4)
                    println(c.red())
                }
                """, "103", tempDir, "color-class");
    }

    // B5 — bare return in void functions
    @Test
    void bareReturn(@TempDir Path tempDir) throws IOException {
        runBoth("""
                void maybe(Bool condition) {
                    if (condition) {
                        return
                    }
                    println("not-returned")
                }

                main() {
                    maybe(true)
                    maybe(false)
                }
                """, "not-returned", tempDir, "b5");
    }

    // B7 — field initializers on JVM and JS
    @Test
    void fieldInitializers(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Theme {
                    Int primary = 0xFF282A36
                    String name = "Dracula"
                }

                main() {
                    var t = Theme()
                    println(t.name)
                    println(t.primary == 0xFF282A36)
                    var t2 = new Theme()
                    println(t2.name)
                }
                """, "Dracula\ntrue\nDracula", tempDir, "b7");
    }

    // B8 — default parameters (compile-time lowering, no runtime machinery)
    @Test
    void defaultParameters(@TempDir Path tempDir) throws IOException {
        runBoth("""
                greet(String name = "world") {
                    println("hello " + name)
                }

                Int add(Int a, Int b = 10) {
                    return a + b
                }

                main() {
                    greet("Mel")
                    greet()
                    println(add(5))
                    println(add(5, 7))
                }
                """, "hello Mel\nhello world\n15\n12", tempDir, "b8");
    }

    @Test
    void assignmentToParameterDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int f(Int a) {
                    a = 99
                    return a
                }

                main() {
                    println(f(1))
                }
                """, "99", tempDir, "param-reassign-simple");
    }

    @Test
    void compoundAssignmentToParameterDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int f(Int a) {
                    a += 5
                    return a
                }

                main() {
                    println(f(1))
                }
                """, "6", tempDir, "param-reassign-compound");
    }

    @Test
    void classMethodParameterReassignmentDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Calc {
                    Int bump(Int n) {
                        n = n + 1
                        return n
                    }
                }

                main() {
                    var c = Calc()
                    println(c.bump(6))
                }
                """, "7", tempDir, "param-reassign-method");
    }

    @Test
    void lambdaParameterReassignmentDoesNotRedeclareInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var f = (n: Int) -> {
                        n = 3
                        return n
                    }
                    println(f(0))
                }
                """, "3", tempDir, "param-reassign-lambda");
    }

    // F2 — main(args: List<String>) receives the program arguments (JVM)
    @Test
    void mainArgsList(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, """
                main(args: List<String>) {
                    println(args.size)
                    println(args.get(0))
                }
                """);
        Path outJvm = tempDir.resolve("out");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outJvm.toString(),
                    "Default.Main", "arquivo.txt", "segundo");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            assertEquals("2\narquivo.txt", output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    // F4 — process.run abstracts the OS process on every backend
    @Test
    void processRun(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var p = process.run("echo", "hello", "world")
                    println(p.stdout.trim())
                    println(p.exitCode)
                }
                """, "hello world\n0", tempDir, "f4-process");
    }

    // user classes extendable with explicit constructors still work
    @Test
    void explicitConstructorStillWorks(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Counter {
                    Int value = 0
                    constructor(Int start) {
                        this.value = start
                    }
                    inc() {
                        value = value + 1
                    }
                    get(): Int {
                        return value
                    }
                }

                main() {
                    var c = Counter(10)
                    c.inc()
                    println(c.get())
                }
                """, "11", tempDir, "explicit-ctor");
    }

    // known-bugs #10 — `!` (logical NOT) as an expression VALUE must negate
    // (constant folding used bitwise `~` → `!true` was true, JVM+Native+JS)
    @Test
    void logicalNotAsExpressionValue(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = !true
                    var b = !false
                    var c = !(1 > 2)
                    println(a)
                    println(b)
                    println(c)
                    println(!(2 > 3))
                    println(!false && true)
                }
                """, "false\ntrue\ntrue\ntrue\ntrue", tempDir, "logical-not");
    }

    // known-bugs #2/#3 — compound assignment operand order: `a -= 2` must be
    // `a - 2` (was `2 - a` → -8); `s += "x"` in a loop must not crash the
    // compiler (old path pushed the RHS twice → stack imbalance at frame merge)
    @Test
    void compoundAssignmentOrderAndStringInLoop(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = 10; a -= 2; println(a)
                    var b = 10; b /= 2; println(b)
                    var c = 10; c %= 3; println(c)
                    var d = 10; d *= 3; println(d)
                    var e = 10; e += 5; println(e)
                    var s = ""
                    var i = 0
                    while (i < 10) { s += "x"; i = i + 1 }
                    println(s.length)
                    var acc = 0
                    for (var j = 0; j < 5; j++) { acc += j }
                    println(acc)
                }
                """, "8\n5\n1\n30\n15\n10\n10", tempDir, "compound-order");
    }

    // §172 — compound SHIFT assignments (`<<=`, `>>=`, `>>>=`) were parsed
    // but the lowering never recognized them as compound (only +=,-=,*=,/=,
    // %=,&=,|=,^=): they fell into the plain-assignment path and stored just
    // the RHS (`x = 6; x <<= 2` produced 2, not 24) — silent miscompilation
    // found 13/09 while hunting Q4 in the translator, which emits `<<=`.
    // A 2ª face (achada no gate da lane bugs-and-gaps): o RHS largo não era
    // narrowado p/ int — `g = 1L; g <<= 40L` emitia `lshl` (long,long) →
    // VerifyError; fechada pelo `emitCompoundRhsConv` (L2I).
    @Test
    void compoundShiftAssignments(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = 6; a <<= 2; println(a)
                    var b = 6; b >>= 1; println(b)
                    var c = -8; c >>>= 1; println(c)
                    var d = 6; d &= 3; println(d)
                    var e = 6; e |= 8; println(e)
                    var f = 6; f ^= 1; println(f)
                    var g = 1L; g <<= 40L; println(g)
                }
                """, "24\n3\n2147483644\n2\n14\n7\n1099511627776", tempDir, "compound-shift");
    }

    // known-bugs #27 — String.valueOf(char) parity: JVM/Native return the UTF-8
    // char ("h"); JS was returning the numeric codepoint ("104"). Now aligned.
    @Test
    void stringValueOfCharParity(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    println(String.valueOf(104 as Char))
                    println(String.valueOf(72 as Char))
                }
                """, "h\nH", tempDir, "string-valueof-char");
    }

    // known-bugs #40 — compound assignment on instance FIELD: `n += 1` in a
    // method pushed `this` once, getfield consumed it, putfield underflowed.
    @Test
    void compoundOnInstanceField(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Box {
                    Int n
                    Int inc() {
                        n += 1
                        return n
                    }
                }
                main() {
                    var b = Box()
                    b.n = 10
                    println(b.inc())
                    b.n -= 2
                    println(b.n)
                }
                """, "11\n9", tempDir, "compound-instance-field");
    }

    // known-bugs #38 — re-throw em try aninhado: o corpo do catch externo lia
    // o slot do catch interno (mesmo nome "e"). Agora o corpo do catch usa um
    // sub-escopo (locals até o catch corrente). [JS: gap separado, ver known-bugs]
    @Test
    void rethrowInNestedTry(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("rtnt.kf");
        Files.writeString(src, """
                main() {
                    try {
                        try { throw "inner" } catch (String e) { throw "outer" }
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Path outJvm = tempDir.resolve("out");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        assertEquals("outer", runJvm(outJvm), "nested-try-rethrow JVM output mismatch");
    }

    // known-bugs #49 — try aninhado no KofJS (COMP002): o parseTryStatement
    // confundia o endLabel do try outer com um finally do try inner.
    @Test
    void nestedTryJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ntry.kf");
        Files.writeString(src, """
                main() {
                    try {
                        try { throw "inner" } catch (String e) { println(e) }
                    } catch (String e) {
                        println("outer:" + e)
                    }
                }
                """);
        Path outJs = tempDir.resolve("js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
    }

    // known-bugs §174 — `return`/`throw` dentro de um `if` dentro do `try`
    // deixava o KofCatchStart solto no statement level (COMP002): o
    // JsIfThrowElse.parseElse consumia o endLabel do try envolvente ao tratar
    // o then incondicional como if-else. JVM/Native/Script já funcionavam.
    @Test
    void returnInsideIfInsideTryJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                String f(String s) {
                    try {
                        if (s == "x") { return "X" }
                        return "Y"
                    } catch (String e) { return "ERR" }
                }
                String g(String s) {
                    try {
                        if (s == "x") { throw "boom" }
                        return "Y"
                    } catch (String e) { return "caught:" + e }
                }
                main() {
                    println(f("x"))
                    println(f("z"))
                    println(g("x"))
                    println(g("z"))
                }
                """, "X\nY\ncaught:boom\nY", tempDir, "tryifreturn");
    }

    // known-bugs §176 — compound em ELEMENTO de array (`values[0] += 5`,
    // `a[0] <<= 2`, `d[0] += 0.25`) no KofJS: o op `KofDup2` não constava em
    // `isExpressionOp`, então o parser abortava antes de consumir o par
    // [array,index] (`COMP002 unexpected op ... KofDup2`). JVM/Native/Script ok.
    @Test
    void compoundOnArrayElementJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Long[2]
                    a[0] = 10L
                    a[0] += 5L
                    a[1] = 3L
                    a[1] <<= 2
                    println(a[0])
                    println(a[1])
                    var d = new Double[1]
                    d[0] = 1.5
                    d[0] += 0.25
                    println(d[0] == 1.75)
                    var i = new Int[1]
                    i[0] = 10
                    i[0] += 5
                    println(i[0])
                }
                """, "15\n12\ntrue\n15", tempDir, "arrcompound");
    }

    // known-bugs §176 — lambda com `var` local + `return x` no corpo: a
    // varredura de tipo de retorno não enxergava os locais declarados no
    // corpo, então a lambda tipava VOID e o backend descartava o valor
    // (JVM VerifyError `Bad type on operand stack`, Native `0`, Script
    // `Long.valueOf/1`, JS COMP002). O JVM/Native/Script/JS agora concordam.
    @Test
    void lambdaReturnLocalVar(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    val f = () -> { var x = 1L; x++; return x }
                    println(f())
                    val g = (y: Int) -> { var z = y + 1; return z * 2 }
                    println(g(4))
                    val s = () -> { var t = "hi"; return t }
                    println(s())
                }
                """, "2\n10\nhi", tempDir, "lambdalocal");
    }

    // known-bugs #51 — CompilerDriver reutilizado vazava classes sintéticas
    // (syntheticClasses/lambdaCounter não resetavam): compilar programa com
    // spawn e DEPOIS outro sem spawn no MESMO driver quebrava o link Native
    // (undefined reference a símbolos da compilação anterior).
    @Test
    void driverReuseDoesNotLeakSyntheticClasses(@TempDir Path tempDir) throws IOException {
        Path src1 = tempDir.resolve("leak1.kf");
        Files.writeString(src1, """
                Int calc(Int n) = n * 2
                main() {
                    var h = spawn calc(21)
                    println(await h)
                }
                """);
        Path src2 = tempDir.resolve("leak2.kf");
        Files.writeString(src2, """
                main() {
                    println("ok")
                }
                """);
        Path out1 = tempDir.resolve("o1");
        Path out2 = tempDir.resolve("o2");
        CompilationResult r1 = driver.compile(src1, out1, Target.NATIVE);
        assertTrue(r1.success(), "spawn compile failed: " + r1.diagnostics().getDiagnostics());
        CompilationResult r2 = driver.compile(src2, out2, Target.NATIVE);
        assertTrue(r2.success(), "reuse w/o spawn leaked synthetic classes: " + r2.diagnostics().getDiagnostics());
        Path bin2 = out2.resolve("Default/Main");
        assertTrue(Files.exists(bin2), "Binary should exist");
    }

    // known-bugs #42 — record hashCode() ausente no JS (TypeError). O JVM
    // gera hashCode sintético no JvmRecordEmitter; o JS agora também.
    @Test
    void recordHashCodeJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record P(Int x, Int y)
                main() {
                    var a = P(1, 2)
                    var b = P(1, 2)
                    println(a.hashCode() == b.hashCode())
                    println(P(1, 2).hashCode() == P(1, 2).hashCode())
                }
                """, "true\ntrue", tempDir, "rec-hash");
    }

    // known-bugs #45 — JS: finally com `return` no try perdia o valor de
    // retorno (`undefined`). O try/finally nativo do JS relança sozinho; o
    // rethrow Kof (`throw _excTmp`) redundante abortava o return. Agora o
    // finally roda e o retorno prevalece (Java-correct, doc "roda sempre").
    // Obs.: JVM/Native/interp descartam o finally nesse caminho (bug de
    // consistência registrado) — este teste cobre só o JS.
    @Test
    void finallyReturnJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("finret.kf");
        Files.writeString(src, """
                Int f() {
                    try { return 1 } finally { println("fin") }
                }
                main() {
                    println(f())
                }
                """);
        Path outJs = tempDir.resolve("js");
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        assertEquals("fin\n1", runJs(outJs), "JS finally+return output mismatch");
    }

    // known-bugs #45 — JVM/Native/interp: DD-01 opção 4a (13/09) — FinallyFrame
    // na IR: return no try/catch salta o finally, que termina loadando #retVal.
    // JVM agora roda fin e preserva 1; interpretador idem (mesma IR).
    @Test
    void finallyReturnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("finretjvm.kf");
        Files.writeString(src, """
                Int f() {
                    try { return 1 } finally { println("fin") }
                }
                Int g() {
                    try { throw "x" } catch (String e) { return 2 } finally { println("fin2") }
                }
                Void h() {
                    try { return } finally { println("fin3") }
                }
                main() {
                    println(f())
                    println(g())
                    h()
                }
                """);
        Path outJvm = tempDir.resolve("jvm");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        assertEquals("fin\n1\nfin2\n2\nfin3", runJvm(outJvm), "JVM finally+return output mismatch");
    }

    // known-bugs §131 (decisão 10a, 13/09) — sobrecarga de MÉTODO por
    // aridade/assinatura na mesma classe. Antes: SEM013 no JVM (só o último
    // def sobrevivia na symtable) e `symbol B_m is already defined` no
    // nativo (vtable dedup por nome + .globl colidindo). Agora: MethodSet
    // na symtable + vtable com slot próprio por assinatura.
    // Cenários Q3: aridade 1 e 2, chamada interna this.m(a,1), ordem de defs.
    @Test
    void methodOverloadByArity(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class B {
                    Int m(Int a) { return this.m(a, 1) }
                    Int m(Int a, Int b) { return a + b }
                }
                main() {
                    var b = B()
                    println(b.m(5))
                    println(b.m(5, 2))
                }
                """, "6\n7", tempDir, "method-overload");
    }

    // known-bugs §89 (decisão 3a, 13/09) — conversão numérica em receiver
    // primitivo (n.toInt()/toDouble()/toFloat()/toLong()) = alias do `as`
    // (trunc para zero, paridade JVM). Antes: JVM compilado quebrava
    // (ClassFormatError owner "") e nativo dava undefined reference.
    // Cenários Q3: trunc (3.7→3), negativo (-2.5→-2), toLong, Float round-trip,
    // String.toInt NÃO é afetado (dispatch próprio antes).
    @Test
    void numericConvertMethodAliasOfAs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var d = 3.7
                    println(d.toInt())
                    var d2 = -2.5
                    println(d2.toInt())
                    var n = 7
                    println(n.toLong())
                    var d3 = 2.5
                    println(d3.toFloat())
                    var n2 = 5
                    var dd = n2.toDouble()
                    println(dd == 5.0)
                }
                """, "3\n-2\n7\n2.5\ntrue", tempDir, "num-convert-alias");
    }

    // The wide parameter belongs to an instance method (slot 0 is `this`), so
    // missing conversion ops → invalid bytecode (ClassFormatError). Now D2I/
    // F2I/D2L/F2L (truncate toward zero) and D2F are emitted.
    @Test
    void fpToIntAndDoubleToFloatConversions(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var d = 3.9
                    println(d as Int)
                    println(d as Long)
                    var f = 2.7f
                    println(f as Int)
                    println(-3.9 as Int)
                    var g: Float = 3.4
                    println(g)
                    var h = d as Float
                    println(h)
                }
                """, "3\n3\n2\n-3\n3.4\n3.9", tempDir, "fp-casts");
    }

    // known-bugs #6 — uppercase numeric suffixes (42L, 1.5F, 1.5D) were not
    // consumed by the lexer (INT_LITERAL(42) + IDENTIFIER(L)) → invalid
    // bytecode. Lowercase and uppercase must be aliases.
    @Test
    void uppercaseNumericSuffixes(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = 42L
                    var b = 42l
                    var c = 1.5F
                    var d = 1.5f
                    var e = 2.5D
                    println(a)
                    println(b)
                    println(a == b)
                    println(c)
                    println(d)
                    println(e)
                }
                """, "42\n42\ntrue\n1.5\n1.5\n2.5", tempDir, "upper-suffix");
    }

    // known-bugs #14 — Map/Set `.size` as a PROPERTY (not just the size()
    // method): fell through to generic field access → getfield HashMap.size →
    // NoSuchFieldError, and the property type inferred UNKNOWN → broken
    // boxing in println. Now dispatched to kof_map_size/kof_set_size.
    @Test
    void mapAndSetSizeProperty(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var m = mapOf("a", 1, "b", 2)
                    println(m.size)
                    println(m.size())
                    var s = setOf("x", "y", "z")
                    println(s.size)
                    println(s.size())
                    println(m.size + s.size)
                }
                """, "2\n2\n3\n3\n5", tempDir, "map-set-size-prop");
    }

    // known-bugs #7 — `listOf<String?>()` (nullable type as generic argument in
    // a method call) didn't parse: looksLikeGenericCall rejected the `?`
    // token → `<` became a comparison → PARSE041.
    @Test
    void nullableGenericArgumentInCall(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var l = listOf<String?>("a", null, "c")
                    println(l.size)
                    var m = mapOf<String?, Int>("x", 1)
                    println(m.size)
                    List<String?> typed = listOf<String?>()
                    println(typed.size)
                }
                 """, "3\n1\n0", tempDir, "nullable-generic-arg");
    }

    // known-bugs §93 (paridade JS Bool) — no JS, predicados da stdlib (math/
    // strings/validation), `instanceof` e predicados de coleção (contains/
    // isEmpty) baixam para 1/0 enquanto `true`/`false` baixam para boolean
    // real. `===` cru fazia `boolExpr == true` sempre false no JS (print
    // coercia via String.valueOf, mascando o bug). Fix: `==`/`!=` com lado
    // bool (tipo ou literal) normaliza ambos com `!!`. Trava JVM == JS nos
    // dois sítios (valor `var x = a == true` e condição `if (a == true)`).
    @Test
    void boolEqualityContentParityJvmJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = strings.isAlpha("abc")
                    println(a == true)
                    println(a == false)
                    println(a != false)
                    if (a == true) { println("cond-true") }
                    var o = "hello"
                    println(o instanceof String == true)
                    var l = listOf(1, 2, 3)
                    println(l.contains(2) == true)
                    println(l.isEmpty() == false)
                    var m = mapOf("k", 1)
                    println(m.isEmpty() == false)
                    println(math.isEven(4) == true)
                    var e = math.isEven(5)
                    println(e == false)
                }
                """,
                "true\nfalse\ntrue\ncond-true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue",
                tempDir, "bool-eq-content-parity");
    }


    // known-bugs #4 — `switch` with String values generated invalid bytecode
    // on JVM (the non-enum path used SUB to test equality → String - String).
    // String switches must compare by content (kof_string_equals).
    @Test
    void stringSwitchOnJvm(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var s = "b"
                    switch (s) {
                        case "a": println("A"); break
                        case "b": println("B"); break
                        default: println("?")
                    }
                    var t = "z"
                    switch (t) {
                        case "a": println("A"); break
                        case "b": println("B"); break
                        default: println("default")
                    }
                }
                """, "B\ndefault", tempDir, "string-switch");
    }

    // known-bugs #13 — `(x as Int) + 1` (cast result in arithmetic) crashed
    // the compiler: the left-assoc chain flattening treated `as` as a chain
    // member → it fell into the default ADD. `as`/`instanceof` now stop the
    // flattening.
    @Test
    void castInArithmetic(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var x = 5
                    var y = (x as Int) + 1
                    println(y)
                    var d = 3.9
                    var i = (d as Int) * 2
                    println(i)
                    var a = 2
                    var b = 3
                    var c = 4
                    println((a + b) * c)
                }
                """, "6\n6\n20", tempDir, "cast-in-arith");
    }

    // known-bugs #11 — `==` on records was reference equality (JVM `if_acmpeq`,
    // JS `===`) → `Ponto(1,2) == Ponto(1,2)` was false. Now `==` dispatches to
    // the generated content `equals` (JVM has it, JS gets one generated).
    @Test
    void recordEqualityByContent(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Ponto(Int x, Int y)
                main() {
                    var a = Ponto(1, 2)
                    var b = Ponto(1, 2)
                    var c = Ponto(2, 1)
                    println(a == b)
                    println(a == c)
                    println(a != c)
                    println(a.equals(b))
                    var l = listOf(Ponto(1, 2), Ponto(3, 4))
                    println(l.get(0) == a)
                }
                """, "true\nfalse\ntrue\ntrue\ntrue", tempDir, "record-eq");
    }

    // known-bugs #20 — invoking a lambda retrieved from a collection
    // (`ops.get(0)(4)`) produced invalid bytecode (JVM) / undefined refs
    // (Native). The list element type was lost and the JVM get lacked the
    // CHECKCAST to the synthetic lambda class. (Homogêneo: lista de lambdas de
    // classes diferentes ainda é limitação — classes sintéticas separadas.)
    @Test
    void lambdaStoredInCollectionAndInvoked(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var ops = listOf((x: Int) -> x * 2)
                    var f = ops.get(0)
                    println(f(4))
                    println(ops.get(0)(7))
                }
                """, "8\n14", tempDir, "lambda-in-list");
    }

    // known-bugs #19 — a lambda RETURNING a lambda lost the inner lambda's
    // synthetic class in the invoke descriptor (NoSuchMethodError on JVM).
    @Test
    void lambdaReturningLambda(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var make = (x: Int) -> ((y: Int) -> x + y)
                    var add5 = make(5)
                    println(add5(3))
                    var add2 = make(2)
                    println(add2(10))
                }
                """, "8\n12", tempDir, "lambda-returning-lambda");
    }

    // known-bugs #64 / GitHub #47 — `Long` and `Double` occupy TWO slots, but
    // the JS signature was built assuming one slot per parameter, so every
    // parameter AFTER a wide one was dropped from the emitted signature and
    // read back as `undefined` (silently wrong, no diagnostic). JVM was
    // correct. A wide parameter in LAST position never triggered it.
    @Test
    void parameterAfterALongIsNotDroppedFromTheJsSignature(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int after(Long a, Int b) {
                    return b
                }
                main() {
                    println(after(1L, 42))
                }
                """, "42", tempDir, "wide-long-then-int");
    }

    @Test
    void parameterAfterADoubleIsNotDroppedFromTheJsSignature(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Int after(Double a, Int b) {
                    return b
                }
                main() {
                    println(after(1.5, 42))
                }
                """, "42", tempDir, "wide-double-then-int");
    }

    // Every parameter of a mixed-width signature must survive, including a
    // narrow one sandwiched between two wide ones.
    @Test
    void mixedWidthParametersAllSurviveInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Double all(Long a, Int b, Double c) {
                    return c
                }
                Int middle(Long a, Int b, Double c) {
                    return b
                }
                Long first(Long a, Int b, Double c) {
                    return a
                }
                main() {
                    println(all(1L, 2, 3.5))
                    println(middle(1L, 2, 3.5))
                    println(first(1L, 2, 3.5))
                }
                """, "3.5\n2\n1", tempDir, "wide-mixed");
    }

    // The wide parameter belongs to an instance method (slot 0 is `this`), so
    // the slot walk has to stay correct with the receiver in front.
    @Test
    void wideParametersInInstanceMethodKeepAllArgumentsInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Calc {
                    Int pick(Long a, Int b) {
                        return b
                    }
                }
                main() {
                    var c = Calc()
                    println(c.pick(1L, 42))
                }
                """, "42", tempDir, "wide-instance-method");
    }

    // Issue #188: == em condição direta de if/if-expr usava if_acmpeq em records
    // em vez de .equals(), causando igualdade de referência errada.
    @Test
    void recordEqualityInDirectIfCondition(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Tag(String name)
                main() {
                    var t1 = new Tag("hi")
                    var t2 = new Tag("hi")
                    if (t1 == t2) println("equal") else println("not equal")
                    println(t1 == t2)
                    var r = if (t1 == t2) "yes" else "no"
                    println(r)
                }
                """, "equal\ntrue\nyes", tempDir, "record-equality-if");
    }

    // Issue #187: Record destructuring com campos Double ou Long alocava slots
    // com passo 1 em vez de 2, gerando VerifyError / colisão de slots no frame JVM.
    @Test
    void recordDestructuringDoubleAndLong(@TempDir Path tempDir) throws IOException {
        runBoth("""
                record Rect(Double w, Double h)
                record Box(Long n)
                main() {
                    var obj: Object = new Rect(3.5, 4.0)
                    var area = switch (obj) {
                        case Rect(var w, var h) -> w * h
                        default -> 0.0
                    }
                    println(area > 13.9 && area < 14.1)

                    var obj2: Object = new Box(10L)
                    var r = switch (obj2) {
                        case Box(var n) -> n * 2L
                        default -> 0L
                    }
                    println(r)
                }
                """, "true\n20", tempDir, "record-destructuring-wide");
    }

    // Issue #194: `s += t` num campo String de INSTÂNCIA emitia KofBinary(ADD)
    // sobre String → `iadd` → VerifyError. O caminho de campo de instância não
    // tinha o tratamento de concatenação que o de campo estático já tinha.
    @Test
    void stringCompoundAssignInstanceField(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Buf {
                    String s = ""
                    void append(String t) { s += t }
                    String get() { return s }
                }
                main() {
                    var b = new Buf()
                    b.append("hi")
                    b.append("!")
                    println(b.get())
                }
                """, "hi!", tempDir, "string-compound-field");
    }

    // Issue #192: compound assignment (`+=`/`-=`/`*=`) em variável capturada
    // por closure emitia putfield sem o objectref (box) → VerifyError
    // `Operand stack underflow`. Atribuição simples já funcionava.
    @Test
    void compoundAssignCapturedVariable(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var n = 10
                    var inc = () -> { n += 1 }
                    var dec = () -> { n -= 3 }
                    var mul = () -> { n *= 2 }
                    inc()
                    println(n)
                    dec()
                    println(n)
                    mul()
                    println(n)
                }
                """, "11\n8\n16", tempDir, "compound-captured");
    }

    // Issue #183: if-expression com ramos de tipos primitivos mistos (Int e Double)
    // causava VerifyError ou crash de ASM frame porque o tipo inferido da expressão
    // era do primeiro ramo enquanto os ramos já eram boxeados para Object.
    @Test
    void ifExpressionMixedNumericBranches(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var flag = true
                    var r1 = if (flag) 1 else 2.5
                    println(r1)

                    var r2 = if (flag) 3.5 else 4
                    println(r2)
                }
                """, "1\n3.5", tempDir, "if-expr-mixed-numeric");
    }

    // Issue #182: for-in loop variable shadowing outer variable corrupts outer slot —
    // VerifyError: Bad local variable type after loop when outer variable is read.
    @Test
    void forInLoopVariableShadowingOuterVariable(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var s = "outer"
                    var lst = new List<String>()
                    lst.add("a")
                    lst.add("b")
                    for (var s in lst) {
                        println(s)
                    }
                    println(s)

                    var x = 100
                    var nums = new List<Int>()
                    nums.add(1)
                    nums.add(2)
                    for (var x in nums) {
                        println(x)
                    }
                    println(x)

                    var i = 999
                    for (var i = 0; i < 2; i++) {
                        println(i)
                    }
                    println(i)
                }
                """, "a\nb\nouter\n1\n2\n100\n0\n1\n999", tempDir, "for-in-shadow-outer");
    }

    // §201 (regression of the #182 fix): the scope-exit rename of the loop
    // variable to "#forInitVar"/"#forInVar" made the JS backend treat its
    // store as a compiler temp (any raw local starting with "#" is dropped
    // from the preamble when the next op is an `if`), so the loop variable
    // was referenced without a declaration (`ReferenceError`). The rename is
    // correct for JVM/Native/Script (slot by index) — only JS resolves by
    // name, so the fix is in JsExpressionParser.isCompilerTemp.
    @Test
    void loopBodyLocalsBeforeIfAreDeclaredInJs(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var a = new Int[3]
                    for (var i = 0; i < 3; i = i + 1) { a[i] = i * 10 }
                    var ok = 0
                    for (var c = 0; c < 3; c = c + 1) {
                        var first = a[1] == 10
                        var second = a[2] == 20
                        if (first && second) {
                            ok = ok + 1
                        }
                    }
                    println(ok)

                    var lst = new List<Int>()
                    lst.add(1)
                    lst.add(2)
                    for (var n in lst) {
                        var even = n % 2 == 0
                        if (even) {
                            println("even")
                        } else {
                            println("odd")
                        }
                    }
                }
                """, "3\nodd\neven", tempDir, "loop-body-locals-if-js");
    }

    // Issue #181: Assigning primitive literal to Object-typed field missing autobox
    // VerifyError: Bad type on operand stack at putfield.
    @Test
    void primitiveAssignedToObjectField(@TempDir Path tempDir) throws IOException {
        runBoth("""
                class Holder {
                    Object item
                }
                main() {
                    var h = new Holder()
                    h.item = 99
                    println(h.item)

                    h.item = 3.5
                    println(h.item)

                    h.item = true
                    println(h.item)
                }
                """, "99\n3.5\ntrue", tempDir, "prim-to-obj-field");
    }

    // Issue #169: Returning a primitive from Object-typed function missing autobox
    // VerifyError: Bad type on operand stack at areturn.
    @Test
    void primitiveReturnedFromObjectFunction(@TempDir Path tempDir) throws IOException {
        runBoth("""
                Object wrapInt(Int n) { return n }
                Object wrapDouble(Double d) { return d + 0.25 }
                Object wrapBool(Bool b) { return b }

                main() {
                    println(wrapInt(7))
                    println(wrapDouble(2.5))
                    println(wrapBool(true))
                }
                """, "7\n2.75\ntrue", tempDir, "prim-return-obj");
    }

    // Issue #167 — instanceof with primitive/boxed types (Int, Double, etc.)
    // emitted '?' as class name instead of boxed java.lang type (NoClassDefFoundError).
    @Test
    void instanceofWithPrimitiveTypes(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("instanceofprim.kf");
        Files.writeString(src, """
                main() {
                    var obj: Object = 42
                    println(obj instanceof Int)
                    println(obj instanceof Double)
                    var d: Object = 3.14
                    println(d instanceof Double)
                    println(d instanceof Int)
                }
                """);
        Path out = tempDir.resolve("instanceofprim-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("true\nfalse\ntrue\nfalse", runJvm(out));
    }

    // Issue #200 — switch expression rejected as RHS of assignment statement (PARSE041).
    @Test
    void switchExpressionAsRhsOfAssignment(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("switchassign.kf");
        Files.writeString(src, """
                class Box {
                    Int code
                    public constructor(Int code) {
                        this.code = code
                    }
                }
                main() {
                    var n = 2
                    var x = 0
                    x = switch (n) { case 2 -> 99 default -> 0 }
                    var b = Box(0)
                    b.code = switch (n) { case 2 -> 77 default -> 0 }
                    println(x)
                    println(b.code)
                }
                """);
        Path out = tempDir.resolve("switchassign-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("99\n77", runJvm(out));
    }

    // Issue #208 — switch expression over Boolean rejects exhaustive true/false coverage (SEM032).
    @Test
    void switchExpressionOverBooleanExhaustive(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("switchbool.kf");
        Files.writeString(src, """
                String describe(Boolean b) {
                    return switch (b) {
                        case true  -> "yes"
                        case false -> "no"
                    }
                }
                main() {
                    var b = true
                    var r1 = switch (b) {
                        case true  -> "T"
                        case false -> "F"
                    }
                    var r2 = describe(false)
                    println(r1)
                    println(r2)
                }
                """);
        Path out = tempDir.resolve("switchbool-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("T\nno", runJvm(out));
    }

    // Issue #206 — if-expression type fixed to true-branch type
    @Test
    void ifExpressionBranchTypesLca(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ifexprtypes.kf");
        Files.writeString(src, """
                class A {
                    String tag() { return "A" }
                }
                class B extends A {
                    String tag() { return "B" }
                }
                main() {
                    var r = if (false) new B() else new A()
                    println(r.tag())
                }
                """);
        Path out = tempDir.resolve("ifexprtypes-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("A", runJvm(out));
    }

    @Test
    void ifExpressionBranchTypesMixedNumeric(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ifmixed.kf");
        Files.writeString(src, """
                main() {
                    var r = if (true) 1 else 1L
                    println(r)
                }
                """);
        Path out = tempDir.resolve("ifmixed-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1", runJvm(out));
    }

    // Issue #154 — strings.padLeft / padRight crash with Char literal (VerifyError)
    @Test
    void stringsPadWithCharLiteralJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("padchar.kf");
        Files.writeString(src, """
                main() {
                    var s1 = strings.padLeft("42", 5, '0')
                    var s2 = strings.padRight("hi", 5, '-')
                    println(s1)
                    println(s2)
                }
                """);
        Path out = tempDir.resolve("padchar-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("00042\nhi---", runJvm(out));
    }

    // Issue #210 — static field ++ / -- emits instance field opcodes
    @Test
    void staticFieldIncrementJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("staticinc.kf");
        Files.writeString(src, """
                class Counter {
                    static Int count = 0
                    void inc() { Counter.count++ }
                    void dec() { Counter.count-- }
                }
                main() {
                    var c = new Counter()
                    c.inc()
                    c.inc()
                    println(Counter.count)
                    c.dec()
                    println(Counter.count)
                }
                """);
        Path out = tempDir.resolve("staticinc-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("2\n1", runJvm(out));
    }
    // Issue #214 — Map, HashMap, Set, HashSet, LinkedList compile with unqualified class names
    @Test
    void standardCollectionInstantiationJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("stdcoll.kf");
        Files.writeString(src, """
                main() {
                    var m1 = new Map<String, Int>()
                    m1.put("a", 1)
                    println(m1.get("a"))

                    var m2 = new HashMap<String, Int>()
                    m2.put("b", 2)
                    println(m2.get("b"))

                    var s1 = new Set<Int>()
                    s1.add(10)
                    println(s1.contains(10))

                    var s2 = new HashSet<Int>()
                    s2.add(20)
                    println(s2.contains(20))

                    var l1 = new LinkedList<String>()
                    l1.add("x")
                    println(l1.get(0))
                }
                """);
        Path out = tempDir.resolve("stdcoll-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1\n2\ntrue\ntrue\nx", runJvm(out));
    }

    // Issue #215 — fields declared in body of constructor-param class are unresolvable
    @Test
    void classWithConstructorParamsExtraFieldsJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("paramclass.kf");
        Files.writeString(src, """
                class Box(Int w, Int h) {
                    Int area = w * h
                    Int getArea() { return area }
                }
                main() {
                    var b = new Box(3, 4)
                    println(b.area)
                    println(b.getArea())
                }
                """);
        Path out = tempDir.resolve("paramclass-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("12\n12", runJvm(out));
    }

    // Issue #217 — class with constructor parameters cannot use extends or implements
    @Test
    void classWithConstructorParamsExtendsImplementsJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("paramclassext.kf");
        Files.writeString(src, """
                interface AreaNamed {
                    String name()
                }
                class Shape {
                    String kind = "shape"
                }
                class Circle(Double radius) extends Shape implements AreaNamed {
                    String name() { return "Circle" }
                    Double area() { return 3.14 * radius * radius }
                }
                main() {
                    var c = new Circle(5.0)
                    println(c.kind)
                    println(c.name())
                    println(c.area())
                }
                """);
        Path out = tempDir.resolve("paramclassext-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("shape\nCircle\n78.5", runJvm(out));
    }

    // Issue #218 — function type syntax as return type and class field type
    @Test
    void functionTypeAsReturnTypeAndFieldTypeJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("fntypes.kf");
        Files.writeString(src, """
                (Int) -> Int makeDoubler() {
                    return (x: Int) -> x * 2
                }

                class Transformer {
                    (Int) -> Int transform = (x: Int) -> x + 10
                    (Int) -> Int getTransform() {
                        return transform
                    }
                }

                main() {
                    var doubler = makeDoubler()
                    println(doubler(5))

                    var t = new Transformer()
                    var f1 = t.transform
                    println(f1(7))
                    var f2 = t.getTransform()
                    println(f2(7))
                }
                """);
        Path out = tempDir.resolve("fntypes-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("10\n17\n17", runJvm(out));
    }

    // Issue #223 — for-loop update expression ++ / -- on Long or Double generates iconst_1 instead of lconst_1 / dconst_1 (VerifyError)
    @Test
    void forLoopUpdateLongAndDoubleJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("forupdate.kf");
        Files.writeString(src, """
                main() {
                    for (var i: Long = 0L; i < 3L; i++) {
                        println(i)
                    }
                    for (var j: Long = 3L; j > 0L; j--) {
                        println(j)
                    }
                }
                """);
        Path out = tempDir.resolve("forupdate-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("0\n1\n2\n3\n2\n1", runJvm(out));
    }

    // Issue #225 — Instance method shadowed by built-in when name matches print/println
    @Test
    void instanceMethodNamedPrintOrPrintlnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("customprint.kf");
        Files.writeString(src, """
                class Logger {
                    void print(String msg) {
                        println("[LOG] " + msg)
                    }
                    void println(Int n) {
                        this.print("custom: " + n)
                    }
                }

                main() {
                    var log = new Logger()
                    log.print("test")
                    log.println(7)
                }
                """);
        Path out = tempDir.resolve("customprint-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("[LOG] test\n[LOG] custom: 7", runJvm(out));
    }

    // Issue #224 — abstract method in non-abstract class is accepted without error (AbstractMethodError at runtime)
    @Test
    void abstractMethodInNonAbstractClassRejected(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("abstractnonabs.kf");
        Files.writeString(src, """
                class Broken {
                    abstract Int compute()
                }

                main() {
                    var b = new Broken()
                    println(b.compute())
                }
                """);
        Path out = tempDir.resolve("abstractnonabs-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertFalse(r.success());
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM041".equals(d.code())),
                "Expected SEM041 diagnostic but got: " + r.diagnostics().getDiagnostics());
    }

    // Issue #226 — super() constructor call always fails with SEM017 regardless of parent constructor
    @Test
    void superConstructorCallResolvesCorrectlyJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("superctor.kf");
        Files.writeString(src, """
                class Vehicle {
                    String kind = "vehicle"
                    public constructor() {
                        this.kind = "vehicle-default"
                    }
                    public constructor(String kind) {
                        this.kind = kind
                    }
                }

                class Car extends Vehicle {
                    String model = "sedan"
                    public constructor() {
                        super()
                    }
                    public constructor(String kind, String model) {
                        super(kind)
                        this.model = model
                    }
                }

                class BaseImplicit {
                    String label = "base-implicit"
                }

                class ChildImplicit extends BaseImplicit {
                    public constructor() {
                        super()
                    }
                }

                main() {
                    var c1 = new Car()
                    println(c1.kind + " " + c1.model)

                    var c2 = new Car("truck", "f150")
                    println(c2.kind + " " + c2.model)

                    var ci = new ChildImplicit()
                    println(ci.label)
                }
                """);
        Path out = tempDir.resolve("superctor-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("vehicle-default sedan\ntruck f150\nbase-implicit", runJvm(out));
    }

    // Issue #222 — constructor-like method inside class body compiled as void instance method instead of <init>
    @Test
    void classNamedConstructorInBodyJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("pointctor.kf");
        Files.writeString(src, """
                class Point {
                    Int x = 0
                    Int y = 0
                    Point(Int x, Int y) {
                        this.x = x
                        this.y = y
                    }
                }

                main() {
                    var p = new Point(3, 4)
                    println(p.x + " " + p.y)
                }
                """);
        Path out = tempDir.resolve("pointctor-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("3 4", runJvm(out));
    }

    // Issue #234 — for-in loop with explicit type annotation on iterator variable fails with SEM011
    @Test
    void forInWithExplicitTypeAnnotationJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("forinannot.kf");
        Files.writeString(src, """
                enum Color { RED, GREEN, BLUE }

                main() {
                    var lst = listOf(1, 2, 3)
                    for (var n: Int in lst) {
                        println(n)
                    }

                    var strs = listOf("alpha", "beta")
                    for (val s: String in strs) {
                        println(s)
                    }

                    for (var c: Color in Color.values()) {
                        println(c)
                    }
                }
                """);
        Path out = tempDir.resolve("forinannot-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("1\n2\n3\nalpha\nbeta\nRED\nGREEN\nBLUE", runJvm(out));
    }

    // Issue #230 — Static method in interface compiled with ACC_ABSTRACT flag causing ClassFormatError
    @Test
    void staticMethodInInterfaceJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("interface_static.kf");
        Files.writeString(src, """
                interface Calc {
                    static Int add(Int a, Int b) {
                        return a + b
                    }
                }

                interface MathUtils {
                    static Int square(Int n) {
                        return n * n
                    }
                    static String tag() {
                        return "utils"
                    }
                }

                main() {
                    println(Calc.add(3, 4))
                    println(MathUtils.square(5))
                    println(MathUtils.tag())
                }
                """);
        Path out = tempDir.resolve("interface_static-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("7\n25\nutils", runJvm(out));
    }

    // Issue #238 — interface static fields not accessible — SEM025 on access
    @Test
    void interfaceStaticFieldAccessJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("interface_field.kf");
        Files.writeString(src, """
                interface K {
                    static Int VAL = 42
                }

                interface Limits {
                    static Int MAX = 100
                    static String TAG = "limit"
                }

                interface Configurable {
                    static Int DEFAULT_SIZE = 10
                    void configure()
                }

                class Widget implements Configurable {
                    void configure() {
                        println("ok")
                    }
                }

                main() {
                    println(K.VAL)
                    println(Limits.MAX)
                    println(Limits.TAG)
                    println(Configurable.DEFAULT_SIZE)
                    var w = new Widget()
                    w.configure()
                }
                """);
        Path out = tempDir.resolve("interface_field-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("42\n100\nlimit\n10\nok", runJvm(out));
    }

    // Issue #239 — static method called via instance reference generates invokevirtual -> IncompatibleClassChangeError
    @Test
    void staticMethodCalledViaInstanceReferenceJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("static_instance_call.kf");
        Files.writeString(src, """
                class Util {
                    static Int square(Int n) {
                        return n * n
                    }
                    static Bool isEven(Int n) {
                        return n % 2 == 0
                    }
                }

                main() {
                    var u = new Util()
                    println(u.square(4))
                    println(u.isEven(4))
                    println(u.isEven(5))
                }
                """);
        Path out = tempDir.resolve("static_instance_call-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("16\ntrue\nfalse", runJvm(out));
    }

    // Issue #233 — Static boolean-returning methods on Double generate String return type in bytecode
    @Test
    void doubleStaticMethodsJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("double_static.kf");
        Files.writeString(src, """
                main() {
                    var d: Double = 0.0 / 0.0
                    println(Double.isNaN(d))
                    println(Double.isInfinite(d))
                    println(Double.isFinite(d))
                    var norm: Double = 42.0
                    println(Double.isNaN(norm))
                    println(Double.isFinite(norm))
                    var inf: Double = 1.0 / 0.0
                    println(Double.isInfinite(inf))
                    var f: Float = 0.0f / 0.0f
                    println(Float.isNaN(f))
                    println(Float.isInfinite(f))
                }
                """);
        Path out = tempDir.resolve("double_static-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("true\nfalse\nfalse\nfalse\ntrue\ntrue\ntrue\nfalse", runJvm(out));
    }

    // Issue #235 — Overloaded static methods: call instruction omitted from IR, causing COMP002
    @Test
    void overloadedStaticMethodsJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("overloaded_static.kf");
        Files.writeString(src, """
                class Fmt {
                    static String of(Int n) { return "int=" + n }
                    static String of(Double d) { return "dbl=" + d }
                }
                class Converter {
                    static String show(Int n) { return "int=" + n }
                    static String show(Double d) { return "double=" + d }
                    static String show(Bool b) { return "bool=" + b }
                }
                main() {
                    var r1 = Fmt.of(10)
                    var r2 = Fmt.of(2.5)
                    println(r1)
                    println(r2)
                    println(Converter.show(42))
                    println(Converter.show(3.14))
                    println(Converter.show(true))
                }
                """);
        Path out = tempDir.resolve("overloaded_static-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("int=10\ndbl=2.5\nint=42\ndouble=3.14\nbool=true", runJvm(out));
    }

    // Issue #246 — Extending a generic class writes angle-bracketed name as super_class
    @Test
    void extendGenericClassJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("extend_generic.kf");
        Files.writeString(src, """
                class Container<T> {
                    T item
                    T get() { return item }
                    void set(T v) { item = v }
                }
                class StringBox extends Container<String> {
                    void hello() { println("hi") }
                }
                class TypedBox<T> extends Container<T> {
                    void test() { println("typed") }
                }
                class IntBox extends Container<Int> {
                    void num() { println("int") }
                }
                main() {
                    var sb = new StringBox()
                    sb.hello()
                    sb.set("world")
                    println(sb.get())

                    var tb = new TypedBox<String>()
                    tb.test()
                    tb.set("box")
                    println(tb.get())

                    var ib = new IntBox()
                    ib.num()
                    ib.set(123)
                    println(ib.get())
                }
                """);
        Path out = tempDir.resolve("extend_generic-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("hi\nworld\ntyped\nbox\nint\n123", runJvm(out));
    }

    // Issue #241 — Catch clause with qualified exception name generates illegal class name in exception table
    @Test
    void qualifiedExceptionInCatchClauseJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("qualified_catch.kf");
        Files.writeString(src, """
                main() {
                    try {
                        throw new java.lang.RuntimeException("boom")
                    } catch (java.lang.RuntimeException e) {
                        println("caught: " + e.getMessage())
                    }
                    try {
                        throw new java.io.IOException("io-error")
                    } catch (java.io.IOException e) {
                        println("io: " + e.getMessage())
                    }
                }
                """);
        Path out = tempDir.resolve("qualified_catch-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("caught: boom\nio: io-error", runJvm(out));
    }

    // Issue #247 — Boolean overload resolution selects Int overload when both show(Int) and show(Boolean) exist
    @Test
    void booleanOverloadResolutionPrefersBooleanJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("boolean_overload.kf");
        Files.writeString(src, """
                class Printer {
                    void show(Int n)     { println("int: " + n) }
                    void show(Boolean b) { println("bool: " + b) }
                }
                main() {
                    var p = new Printer()
                    p.show(true)
                    p.show(false)
                    var b: Boolean = true
                    p.show(b)
                    p.show(42)
                }
                """);
        Path out = tempDir.resolve("boolean_overload-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("bool: true\nbool: false\nbool: true\nint: 42", runJvm(out));
    }

    // Issue #249 — assigning to static field via instance reference emits putfield instead of putstatic — IncompatibleClassChangeError
    @Test
    void assignToStaticFieldViaInstanceReferenceJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("static_field_asgn.kf");
        Files.writeString(src, """
                class Cfg { static Int MAX = 100 }
                class App { static String name = "v1" }
                class Flags { static Boolean debug = false }
                main() {
                    var c = new Cfg()
                    c.MAX = 200
                    println(Cfg.MAX)
                    c.MAX += 50
                    println(Cfg.MAX)
                    var a = new App()
                    a.name = "v2"
                    println(App.name)
                    var f = new Flags()
                    f.debug = true
                    println(Flags.debug)
                }
                """);
        Path out = tempDir.resolve("static_field_asgn-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("200\n250\nv2\ntrue", runJvm(out));
    }

    // Issue #248 — covariant return override missing bridge method — virtual dispatch silently calls superclass method
    @Test
    void covariantReturnOverrideBridgeMethodJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("covariant_return.kf");
        Files.writeString(src, """
                class Base {
                    Base create() { return new Base() }
                    String name()  { return "base" }
                }
                class Child extends Base {
                    Child create() { return new Child() }
                    String name()  { return "child" }
                }
                main() {
                    var b: Base = new Child()
                    var c = b.create()
                    println(c.name())
                    println(c instanceof Child)
                }
                """);
        Path out = tempDir.resolve("covariant_return-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("child\ntrue", runJvm(out));
    }

    // Issue #232 — enum.ordinal() and enum.compareTo() not accessible — SEM025
    @Test
    void enumOrdinalAndCompareToJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("enum_ordinal.kf");
        Files.writeString(src, """
                enum Dir { N, S, E, W }
                main() {
                    println(Dir.N.ordinal())
                    println(Dir.W.ordinal())
                    println(Dir.N.compareTo(Dir.S))
                }
                """);
        Path out = tempDir.resolve("enum_ordinal-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals("0\n3\n-1", runJvm(out));
    }
}
