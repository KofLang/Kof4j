package dev.kof.compiler.js;

import java.util.Set;

/**
 * JsMemberNames — o nome JS de um MEMBRO de classe (#1107): campo e método
 * de mesmo nome colidiam (a propriedade de instância do campo usava o nome
 * cru e sombreava o método do prototype — `w.label()` virava
 * `TypeError: w.label is not a function`). A regra: quando a classe declara
 * um MÉTODO com o mesmo nome, o CAMPO ganha o sufixo `$fld` no JS; o método
 * mantém o nome cru (o oráculo JVM/Script imprime "method" e depois
 * "field"). O detector é um lookup em `lc.classMethodNames` (classe →
 * métodos, povoado pelo JsBackend para o dispatch de chamadas).
 *
 * <p>Records são INTACTOS: a convenção `_name` deles é outro caminho (os
 * sites ramificam em recordClass antes de chamar aqui).
 */
final class JsMemberNames {

    private JsMemberNames() {}

    /** O nome JS da PROPRIEDADE de campo para o dono (interno) dado. */
    static String fieldJsName(JsLoweringContext lc, String ownerInternalName, String name) {
        String raw = JsTypeMapper.sanitizeName(name);
        if (ownerInternalName != null) {
            Set<String> methods = lc.classMethodNames.get(ownerInternalName);
            if (methods != null && methods.contains(name)) {
                return raw + "$fld";
            }
        }
        return raw;
    }
}
