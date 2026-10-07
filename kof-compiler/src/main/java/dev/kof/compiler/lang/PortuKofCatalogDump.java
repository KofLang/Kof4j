package dev.kof.compiler.lang;

import dev.kof.compiler.StdCatalog;

/**
 * DUMP autoritativo do catálogo stdlib (D-PORTUKOF): `namespace\tmembro` por
 * linha, lido direto de {@link StdCatalog} — a MESMA fonte do LSP. O gerador
 * `scripts/gen_portukof_aliases.py` e o gate `scripts/check_portukof_parity.sh`
 * consomem este dump; a tabela de aliases nunca deriva de regex sobre fonte
 * (fonte única = o catálogo real em execução).
 *
 * <p>Uso: `java -cp kof-compiler/target/classes dev.kof.compiler.lang.PortuKofCatalogDump`
 */
public final class PortuKofCatalogDump {

    private PortuKofCatalogDump() {}

    public static void main(String[] args) {
        for (String ns : StdCatalog.namespaces()) {
            for (String m : StdCatalog.membersOf(ns)) {
                System.out.println(ns + "\t" + m);
            }
        }
    }
}
