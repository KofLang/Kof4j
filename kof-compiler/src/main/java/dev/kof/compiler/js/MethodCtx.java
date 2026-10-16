package dev.kof.compiler.js;
import dev.kof.compiler.AccessFlags;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.LabelId;
import dev.kof.compiler.js.JsLoweringContext;
import dev.kof.compiler.js.JsTypeMapper;
import dev.kof.compiler.KofTryStart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public final class MethodCtx {
    final JsLoweringContext lc;
    final List<KofOperation> ops;
    final Map<Integer, String> localNames = new HashMap<>();
    final Map<Integer, String> rawLocalNames = new HashMap<>();

    /** DD-01 (bug 45): label do epílogo return-finally do try em parse. */
    LabelId currentReturnFinallyLabel;
    /**
     * §703: true enquanto o CORPO de um try COM return-finally é parseado. Um
     * store em `#retVal` vira `return X` (o try/finally NATIVO do JS então roda
     * o finally no caminho do return), em vez de `_retVal = X` + epílogo inline
     * (que fora de um loop caía no tail do método e DENTRO de um loop rodava a
     * cada iteração, devolvendo `undefined`).
     */
    boolean inReturnFinallyTry = false;
    final Set<Integer> declared = new HashSet<>();
    final Set<String> usedNames = new HashSet<>();
    final List<String> tempDecls = new ArrayList<>();
    final List<LoopCtx> loops = new ArrayList<>();
    /**
     * §701: pilha dos labels de FIM de `switch` em parse. Um `break` dentro de
     * um case (mesmo ANINHADO num if/loop) baixa como um KofJump para esse
     * label; sem registrá-lo, o salto caía no caminho genérico de "region
     * exit" do parseStatements e TRUNCAVA o corpo do case (o resto — incl. o
     * default — vazava para dentro do case anterior). Só labels de LOOP eram
     * registrados antes (`loops`), o que cobria o `break` DIRETO no case (§587)
     * mas não a forma aninhada.
     */
    final List<LabelId> switchEnds = new ArrayList<>();
    /**
     * §701: TODOS os labels estruturais de switches em parse (corpo de case,
     * default, fim). Um `if`/`else` INTERNO não pode consumir nenhum deles como
     * seu "end" — o `parseIfBody` engolia o label do DEFAULT pensando ser o fim
     * do if, e o corpo do default vazava para dentro do case anterior.
     */
    final java.util.Set<LabelId> switchStructLabels = new java.util.HashSet<>();
    /**
     * §267: `if` de STATEMENT (marcados no lowering por KofStatementIf) são
     * identicos em IR a if-expressões ([cond], CJump, Label, ramo, Jump, Label,
     * ramo, Label) — o dispatcher de statements só distingue um do outro pela
     * INTENÇÃO do lowering. Este conjunto guarda o `trueLabel` do CJump ramo
     * de cada `if` de statement: quando a CJump dispatcher bate nele, pula a
     * dobra em ternária (que engoliria o statement seguinte). `LabelId` é
     * monotônico → sem colisões; o dispatcher remove após o uso (uma só vez).
     */
    final Set<LabelId> statementIfLabels = new java.util.HashSet<>();
    /**
     * §380: pilha dos falseLabels dos `if` ATIVOS (parsing em curso). Um label
     * nesta pilha pertence a estrutura ENVOLVENTE — `parseElse`/ramo no-else de
     * um `if` interno NUNCA o consome (o roubo do falseLabel do `if` externo
     * era o §380: epílogo absorvido no then, caminho não-throw = undefined).
     * Labels de loop/try têm guarda própria anterior (§147/§266 intactos).
     */
    final java.util.Deque<LabelId> enclosingIfFalses = new java.util.ArrayDeque<>();
    boolean isEnclosingIfFalse(LabelId label) { return enclosingIfFalses.contains(label); }
    /**
     * §734: labels de FIM (merge) do if ENVOLVENTE — o alvo do Jump de saída do
     * then. Um if ANINHADO branco pode TOMAR esse label como o seu próprio fim
     * (isIfEndLabel é genérico), e então o epílogo do método é absorvido no
     * else externo. Guardar os alvos do then do if envolvente durante o parse do
     * else impede o roubo (§380 é o análogo para o falseLabel).
     */
    final java.util.Set<LabelId> enclosingIfEnds = new java.util.HashSet<>();
    boolean isEnclosingIfEnd(LabelId label) { return enclosingIfEnds.contains(label); }
    final boolean instanceMethod;
    final String kofClassName;
    final String methodName;
    final int paramCount;
    final boolean recordClass;
    final boolean isAsync;
    /** slots of lambda capture fields (come before the real parameters) */
    final Set<Integer> captureSlots = new HashSet<>();
    int tempCounter = 0;

    MethodCtx(JsLoweringContext lc, IRMethod method, IRClass clazz) {
        this.lc = lc;
        this.ops = new ArrayList<>(method.basicBlocks().stream()
                .flatMap(b -> b.operations().stream()).toList());
        this.instanceMethod = clazz != null && !JsLoweringContext.isMainClass(clazz)
                && (method.accessFlags() & AccessFlags.STATIC) == 0;
        this.kofClassName = clazz == null ? null : clazz.name();
        this.methodName = method.name();
        this.paramCount = method.parameterTypes().size();
        // #818: a record-ness vem do lowering (IRClass.isRecord) além da
        // superclasse — um record que estende classe do usuário grava os campos
        // com o mesmo prefixo `_` que os leitores usam (senão o ctor escrevia
        // `this.x` e o toString lia `this._x` → `undefined`).
        this.recordClass = clazz != null && ("java/lang/Record".equals(clazz.superName()) || clazz.isRecord());
        String asyncKey = clazz == null
                ? "#" + method.name() + "/" + method.parameterTypes().size()
                        + dev.kof.compiler.TopLevelOverload.sigTag(method.parameterTypes())
                : JsLoweringContext.asyncMethodKey(clazz, method);
        this.isAsync = lc.asyncMethods.getOrDefault(asyncKey, false);
        // lambda synthetic classes hold captured locals as private final
        // fields at the first slots; the real parameters come after them.
        Set<String> captureFields = new HashSet<>();
        if (clazz != null && clazz.name() != null
                && (clazz.name().startsWith("Lambda") || clazz.name().startsWith("LambdaTask"))) {
            for (IRField f : clazz.fields()) {
                if ((f.accessFlags() & AccessFlags.PRIVATE) != 0
                        && (f.accessFlags() & AccessFlags.FINAL) != 0) {
                    captureFields.add(f.name());
                }
            }
        }
        for (IRLocalVariable lv : method.localVariables()) {
            rawLocalNames.put(lv.index(), lv.name());
            if (instanceMethod && lv.index() == 0) {
                localNames.put(lv.index(), "this");
                continue;
            }
            if (captureFields.contains(lv.name()) && lv.index() < 1 + captureFields.size()
                    && !"<init>".equals(method.name())) {
                // invoke(): the captures are fields copied to locals before
                // the real params — they are NOT the method's parameters.
                // <init>() receives the captures AS its parameters.
                captureSlots.add(lv.index());
            }
            localNames.put(lv.index(), uniqueName(JsTypeMapper.sanitizeName(lv.name())));
        }
    }

    String uniqueName(String base) {
        String name = base;
        int n = 1;
        while (!usedNames.add(name)) {
            name = base + "_" + (n++);
        }
        return name;
    }

    String freshTemp() {
        String name = uniqueName("__kof_t" + (tempCounter++));
        tempDecls.add(name);
        return name;
    }

    LoopCtx currentLoop() {
        return loops.isEmpty() ? null : loops.get(loops.size() - 1);
    }

    /** §701: `label` pertence à ESTRUTURA de um switch em parse. */
    boolean isSwitchStruct(LabelId label) {
        return switchStructLabels.contains(label);
    }

    /** §701: `label` é o fim de ALGUM switch em parse (contexto quebrável). */
    boolean isSwitchEnd(LabelId label) {
        for (LabelId sw : switchEnds) {
            if (sw.equals(label)) return true;
        }
        return false;
    }

    boolean isLoopLabel(LabelId label) {
        for (LoopCtx loop : loops) {
            if (label.equals(loop.start) || label.equals(loop.continueLabel) || label.equals(loop.end)) {
                return true;
            }
        }
        return false;
    }

    boolean isLoopEnd(LabelId label) {
        for (LoopCtx loop : loops) {
            if (label.equals(loop.end)) return true;
        }
        return false;
    }

    /** true se `label` é o endLabel de QUALQUER try no método (try aninhado/outer). */
    boolean isTryEndLabel(LabelId label) {
        for (KofOperation op : ops) {
            if (op instanceof KofTryStart ts && ts.endLabel().equals(label)) return true;
        }
        return false;
    }

    /**
     * true se `label` em {@code pos[0]} é o Label(end) de um if (deve ser
     * consumido). Um label de loop (continue/start) pertence ao loop
     * envolvente e o endLabel de um try envolvente pertence ao try — consumir
     * este último deixa o KofCatchStart solto (COMP002, §174).
     */
    boolean isIfEndLabel(int[] pos, LabelId label) {
        return !isLoopLabel(label) && !isSwitchStruct(label) && !JsLabelParser.isLoopStart(this, pos, label)
                && !isTryEndLabel(label);
    }

    boolean hasClassMethod(String kofClassName, String method) {
        Set<String> names = lc.classMethodNames.get(kofClassName);
        return names != null && names.contains(method);
    }
}
