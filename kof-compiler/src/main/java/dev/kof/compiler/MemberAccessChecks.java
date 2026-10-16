package dev.kof.compiler;

/**
 * Satélite do REFACTOR-500 (check_500, MemberCallTyper 602→<600): os cheques
 * de ACESSO (SG-013/#327/#331 — private/protected, final field write, hierarquia)
 * movem-se verbatim.
 */
final class MemberAccessChecks {

    private MemberAccessChecks() {}

    /**
     * SG-013 (SEM046): visibilidade em compile-time. `private` só é acessível
     * dentro da própria classe declarante; `protected` dentro da declarante ou
     * subclasses. Chamada fora → erro SEM046 (antes: IllegalAccessError runtime).
     */
    static void checkMemberAccess(SemanticAnalyzer sa, int accessFlags,
                                  String ownerClass,
                                  String memberDesc) {
        if (sa.diagnostics() == null) return;
        boolean isPriv = (accessFlags & AccessFlags.PRIVATE) != 0;
        boolean isProt = (accessFlags & AccessFlags.PROTECTED) != 0;
        if (!isPriv && !isProt) return;
        String caller = sa.currentClassName();
        if (caller == null) {
            // contexto top-level (main/função livre): não é dono de nada —
            // private E protected são inacessíveis
            sa.diagnostics().error("", 0, 0, 0,
                    memberDesc + " is " + (isPriv ? "private" : "protected")
                            + " (declared in '" + ownerClass
                            + "') and cannot be accessed from top-level code",
                    "SEM046");
            return;
        }
        if (isPriv) {
            // private: só a própria classe declarante
            if (!ownerClass.equals(caller)) {
                sa.diagnostics().error("", 0, 0, 0,
                        memberDesc + " is private (declared in '" + ownerClass
                                + "') and cannot be accessed from '" + caller + "'",
                        "SEM046");
            }
        } else {
            // protected: declarante ou subclasse (hierarquia transitiva)
            if (!MemberAccessChecks.isInHierarchy(sa, caller, ownerClass)) {
                sa.diagnostics().error("", 0, 0, 0,
                        memberDesc + " is protected (declared in '" + ownerClass
                                + "') and cannot be accessed from '" + caller + "'",
                        "SEM046");
            }
        }
    }

    /**
     * #331/#327 — face de CAMPO do mesmo contrato SG-013: private só na
     * declarante, protected na declarante/subclasses (JVM aplica em runtime;
     * sem o cheque o compile deixava passar e o load estourava
     * IllegalAccessError — R6/Q7: nunca silencioso). O método-irmão já fazia
     * isso (SEM046); o campo não fazia porque o FieldSymbol perdia os
     * modificadores no SymbolTableBuilder. `this.x`/`x` nu na própria classe
     * passa (owner == caller).
     */
    static void checkFieldAccess(SemanticAnalyzer sa, SymbolTable.FieldSymbol fs) {
        MemberAccessChecks.checkMemberAccess(sa, fs.accessFlags(), fs.ownerClass(),
                "field '" + fs.name() + "'");
    }

    /**
     * #327 — escrita em campo `final`: o JVM so aceita putfield de um campo
     * final no <init> DA CLASSE DECLARANTE (JVMS 4.4). Fora disso o runtime
     * estoura IllegalAccessError ("Update to non-static final field ...
     * attempted from a different class") em silencio no compile (R6/Q7).
     * O inicializador `final Int x = 30` passa pelo fieldInits/<clinit>
     * sintetizado (nao e um statement do usuario) — nunca chega aqui.
     */
    static void checkFinalFieldWrite(SemanticAnalyzer sa, SymbolTable.FieldSymbol fs) {
        if (sa == null || sa.diagnostics() == null) return;
        if ((fs.accessFlags() & AccessFlags.FINAL) == 0) return;
        boolean staticField = (fs.accessFlags() & AccessFlags.STATIC) != 0;
        boolean legal = sa.inConstructor && !staticField
                && fs.ownerClass().equals(sa.currentClassName());
        if (legal) return;
        sa.diagnostics().error("", 0, 0, 0,
                "cannot assign to final field '" + fs.name() + "' (declared in '"
                        + fs.ownerClass() + "') from outside its constructor",
                "SEM065");
    }

    /** caller está na hierarquia de `base` (caller == base ou estende transitivamente)? */
    static boolean isInHierarchy(SemanticAnalyzer sa, String caller, String base) {
        String current = caller;
        int depth = 0;
        while (current != null && depth++ < 32) {
            if (current.equals(base)) return true;
            SymbolTable.ClassSymbol cs = sa.allClasses().get(current);
            if (cs == null) return false;
            current = cs.superClass();
        }
        return false;
    }
}
