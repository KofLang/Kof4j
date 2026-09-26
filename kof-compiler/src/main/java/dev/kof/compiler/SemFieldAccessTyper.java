package dev.kof.compiler;

/**
 * §500-gate (regra ≤500, fatia da própria lane): a face `FieldAccessExpr` da
 * semântica de expressões, extraída VERBATIM de {@code SemExpressionTyper}
 * (rule 3 — estrutura, nunca comportamento; prova = a mesma suíte + os mesmos
 * guards §491/§496/§498/§499/§500/bug-99/SG-005/#331/#375). Segue o precedente
 * de {@code SemMethodCallTyper}/{@code SemNewExprTyper}: o dispatcher fica no
 * dono, a face numa peça com nome de responsabilidade (rule 7).
 */
final class SemFieldAccessTyper {

    private SemFieldAccessTyper() {}

    static Type infer(SemanticAnalyzer sa, FieldAccessExpr fa, SymbolTable scope) {

                if (fa.receiver() instanceof IdentifierExpr pId && KofUi.isPalette(pId.name())) {
                    if (KofUi.paletteColor(fa.fieldName()) != null) return KofUi.COLOR;
                    // §498: `Palette.bogus` caía no caminho genérico e o emit
                    // gerava `getfield "?".bogus` (NoClassDefFoundError: "?" no
                    // load, compilado limpo). Paleta é CONSTANTE — membro
                    // desconhecido é SEM079, como nos token namespaces.
                    if (sa.diagnostics() != null) {
                        sa.diagnostics().error(fa,
                                "'Palette." + fa.fieldName() + "' is not a palette color "
                                        + "(use a named color, e.g. Palette.red)",
                                "SEM079");
                    }
                    return KofUi.COLOR;
                }
                if (fa.receiver() instanceof IdentifierExpr tid && KofUiTokens.isTokenNamespace(tid.name())) {
                    if (KofUiTokens.tokenValue(tid.name(), fa.fieldName()) == null && sa.diagnostics() != null) {
                        sa.diagnostics().error(fa,
                                KofUiTokens.unknownMemberMessage(tid.name(), fa.fieldName()), "SEM079");
                    }
                    return Type.PrimitiveType.INT;
                }
                String en = MemberResolver.enumNameOfConstant(sa.unit(), fa);
                if (en != null) return CompilerTypes.enumTypeOf(en, sa); // #445: pkg real via ClassSymbol
                Type recvType = SemExpressionTyper.inferType(sa, fa.receiver(), scope);
                Type nf = Narrowing.narrowedField(scope, Narrowing.pathOf(fa));
                if (nf != null) return nf;
                // bug 99 (R6, nunca silencioso): `Int.MAX_VALUE`/`Long.foo` etc.
                // — acesso a campo num NOME DE TIPO PRIMITIVO. `Int` resolve p/
                // UNKNOWN (a isenção isBuiltinTypeName de SEM011 existe p/ posição
                // de TIPO, não p/ receiver de campo) e o guard SEM025 abaixo só
                // dispara em ClassType → o campo passava SEM diagnóstico e o
                // lowering emitia `getfield "?".field` (NoClassDefFoundError/SIGSEGV
                // nos 3 targets; `var x = Int.MAX_VALUE` ainda CRASHAVA o
                // compilador — ASM visitMaxs NegativeArraySizeException). Não há
                // constante estática de primitivo em Kof (idiom = literal/`as`).
                if (recvType instanceof Type.UnknownType
                        && fa.receiver() instanceof IdentifierExpr rid
                        && MemberResolver.isBuiltinTypeName(rid.name())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(fa,
                            "'" + rid.name() + "' is a primitive type, it has no static field "
                                    + "'" + fa.fieldName() + "' (use the literal, "
                                    + "e.g. 2147483647 for Int; there is no Int.MAX_VALUE in Kof)",
                            "SEM050");
                    return Type.UnknownType.UNKNOWN;
                }
                // §496: acesso a CAMPO em um NAMESPACE builtin (math.bogus, e
                // até `math.PI` — não há constante de namespace). O receiver
                // não é local nem tipo (vira UNKNOWN), então caía no caminho
                // genérico e o emit gerava `getfield "?".<nome>` contra uma
                // classe vazia → NoClassDefFoundError: "?" no load, compilado
                // limpo (escondido atrás da mensagem do launcher JavaFX). É a
                // face FIELD da família §495/#126/§490. Enum/paleta (`Color.Red`
                // via KofUi.isPalette), tokens (KofUiTokens) e constantes de
                // enum já retornaram acima, e o receiver tem de ser UNKNOWN
                // (um local chamado `math` tem tipo próprio) — sem falso positivo.
                if (recvType instanceof Type.UnknownType
                        && fa.receiver() instanceof IdentifierExpr nId
                        && SemUndefinedVarGuard.isBuiltinNamespace(nId.name())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(fa,
                            "'" + nId.name() + "' is a builtin namespace; it has no field "
                                    + "'" + fa.fieldName() + "' (namespaces expose functions, "
                                    + "not properties — use " + nId.name() + ".someFunction(...))",
                            "SEM102");
                    return Type.UnknownType.UNKNOWN;
                }
                // §498: acesso a CAMPO em um TIPO kof.ui (Color/Theme e demais
                // construtores UI). Eles expõem FUNÇÕES (`Color.rgba(...)`,
                // `Theme.light()`), não campos — `Color.bogus` compilava limpo e
                // o emit gerava `getfield "?".bogus`. Só dispara se o receiver
                // não virou tipo declarado (recvType UNKNOWN), preservando uma
                // classe do usuário homônima.
                if (recvType instanceof Type.UnknownType
                        && fa.receiver() instanceof IdentifierExpr uId
                        && KofUi.isConstructor(uId.name())
                        && sa.diagnostics() != null) {
                    sa.diagnostics().error(fa,
                            "'" + uId.name() + "' has no field '" + fa.fieldName()
                                    + "' (UI types expose functions, e.g. Color.rgba(...), "
                                    + "Theme.light())",
                            "SEM079");
                    return Type.UnknownType.UNKNOWN;
                }
                // SG-005: deref de T? sem narrowing é erro (espelha SEM049 de
                // method call) — `s.length` em String? seria NPE em runtime.
                if (recvType instanceof Type.NullableType && sa.diagnostics() != null) {
                    SourcePosition faPos = fa.position();
                    sa.diagnostics().error(faPos != null ? faPos.file() : "",
                            faPos != null ? faPos.line() : 0, faPos != null ? faPos.column() : 0, 0,
                            "receiver is nullable (T?); narrow first: if (x != null) { x.field }",
                            "SEM049");
                }
                if (KofUi.isComponent(recvType) && "state".equals(fa.fieldName())) {
                    return Type.PrimitiveType.INT;
                }
                if (KofProcess.isResult(recvType) && KofProcess.isField(fa.fieldName())) {
                    return KofProcess.fieldType(fa.fieldName());
                }
                if (recvType instanceof Type.ArrayType && "length".equals(fa.fieldName())) {
                    return Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && "length".equals(fa.fieldName())) {
                    return Type.PrimitiveType.INT;
                }
                if (Type.isString(recvType) && ("name".equals(fa.fieldName()) || "path".equals(fa.fieldName()))) {
                    return BuiltinTypes.STRING;
                }
                // §491 (SEM102, R6): pseudo-tipo builtin com ramo próprio de typer
                // (Buffer/Secret/KeyHandle e File/Path/Directory) NÃO tem forma de
                // PROPRIEDADE — os acessores são métodos. Um campo desconhecido
                // caía no caminho genérico de ClassType (não é classe do unit nem
                // external) e o emit gerava `getfield <receiver>.<nome>` contra uma
                // classe INEXISTENTE no runtime (kof/Buffer, kof/Secret,
                // kof/io/File) — NoClassDefFoundError no load, compilado limpo
                // (escondido atrás da mensagem do launcher JavaFX). Mesma família
                // de #617/§490, agora a face FIELD. O acesso válido é o método
                // (`File("x").path()`, `.size()`, `buffer.alloc(n).bytes()`,
                // `secret.reveal()`).
                // §503: Channel<T>/Handle<T> (kof.concurrent) entram na mesma
                // família — nenhum dos dois tem PROPRIEDADE: `c.bogusField` /
                // `h.bogusField` compilavam limpos e o emit emitia
                // `getfield LinkedBlockingQueue.bogusField` / `CompletableFuture...`
                // → NoSuchFieldError no runtime (Handle: o idioma é `await h`).
                if ((KofIo.isIoType(recvType) || KofBuffer.isBufferType(recvType)
                        || KofSecurity.isSecretType(recvType) || KofSecurity.isKeyHandleType(recvType)
                        || BuiltinTypes.isChannel(recvType) || TypeChecker.isConcurrentHandle(recvType))
                        && sa.diagnostics() != null) {
                    boolean handle = TypeChecker.isConcurrentHandle(recvType);
                    String builtinName = KofIo.isDirectory(recvType) ? "Directory"
                            : KofIo.isPath(recvType) ? "Path"
                            : KofIo.isFile(recvType) ? "File"
                            : KofBuffer.isBufferType(recvType) ? "Buffer"
                            : (KofSecurity.isSecretType(recvType) ? "Secret"
                            : BuiltinTypes.isChannel(recvType) ? "Channel"
                            : (KofSecurity.isKeyHandleType(recvType) ? "KeyHandle" : "Handle"));
                    sa.diagnostics().error(fa,
                            "'" + builtinName + "' has no field '" + fa.fieldName() + "'"
                                    + (handle ? "; use `await h` to get the value"
                                            : " (this builtin exposes methods, not properties)"),
                            "SEM102");
                    return Type.UnknownType.UNKNOWN;
                }
                // #375/§355 (rio da erasure): receiver é type-variable COM bound
                // (`item.name` com `item: T: Animal`) — o membro resolve no
                // BOUND, como javac após a erasure. Sem isto o tipo caía em
                // UNKNOWN e o emit saía owner "?" / descritor Object →
                // NoClassDefFoundError: "?".
                if (recvType instanceof Type.TypeVariable tv && tv.bound() != null) {
                    recvType = tv.bound();
                }
                // §500 slice B: campo ESTÁTICO externo pelo NOME da classe
                // (`Integer.MAX_VALUE`/`TimeUnit.SECONDS`; SEM025 honesto,
                // nunca `getfield "?"` morto) — a face vive em
                // StaticClassReceiver (gate ≤500, R6).
                Type s500 = StaticClassReceiver.semInfer(sa, fa, recvType);
                if (s500 != null) return s500;
                if (recvType instanceof Type.ClassType ct) {
                    SymbolTable.Symbol field = MemberResolver.resolveFieldInHierarchy(sa, ct.name(), fa.fieldName());
                    if (field != null) {
                        // #331/#327 (espelha SEM046 dos metodos): acesso a
                        // campo private/protected de fora da declarante
                        // compila e o load estourava IllegalAccessError em
                        // silencio (R6/Q7). so com `this.x`/x nu (owner ==
                        // caller) e dentro da declarante/subclasse passa.
                        if (field instanceof SymbolTable.FieldSymbol fs) {
                            MemberCallTyper.checkFieldAccess(sa, fs);
                        }
                        return CompilerTypes.substituteTypeVariableIn(field.type(), recvType, sa.unit());
                    }
                    if (sa.isExternal(ct)) {
                        String desc = sa.externalTypes().resolveFieldType(ct.internalName(), fa.fieldName());
                        if (desc != null) {
                            return ExternalClasspath.typeFromDescriptor(desc);
                        }
                    }
                    // P0 #3: campo inexistente em classe conhecida nao pode
                    // mascarar com UNKNOWN (mesma regra SEM025 do metodo) —
                    // erro primeiro, depois UNKNOWN p/ error recovery.
                    // Excecoes: constante de enum (Color.Red e FieldAccess)
                    // e metodos de Object (nao sao campos).
                    boolean isKnownReceiver = sa.allClasses().containsKey(ct.name()) || sa.isExternal(ct);
                    boolean isEnumConstant = MemberResolver.enumConstantOfExpr(sa.unit(), fa) != null;
                    if (sa.diagnostics() != null && isKnownReceiver && !isEnumConstant && !MemberResolver.isObjectMethod(fa.fieldName(), 0)) {
                        sa.diagnostics().error(fa,
                                "Cannot resolve field '" + fa.fieldName()
                                        + "' on type '" + ct.name() + "'",
                                "SEM025");
                    }
                }
                return Type.UnknownType.UNKNOWN;
    }
}
