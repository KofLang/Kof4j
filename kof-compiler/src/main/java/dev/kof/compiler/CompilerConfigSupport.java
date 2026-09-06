package dev.kof.compiler;

import java.util.List;

/**
 * Descoberta de chaves de config (kof config) em compile-time.
 */
final class CompilerConfigSupport {

    private CompilerConfigSupport() {}

    static java.util.List<CompilerDriver.ConfigKeyInfo> discoveredConfigKeys(CompilerDriver driver) {
        return List.copyOf(driver.discoveredConfigKeys);
    }

    static void recordConfigKey(CompilerDriver driver, MethodCallExpr mc) {
        List<ExpressionNode> args = mc.arguments();
        if (args.isEmpty()) return;
        if (!(args.get(0) instanceof LiteralExpr le)
                || le.kind() != ConcreteLiteralKind.STRING) {
            return;
        }
        String key = le.value();
        String def = null;
        if (args.size() >= 2 && args.get(1) instanceof LiteralExpr dl) {
            def = switch (dl.kind()) {
                case ConcreteLiteralKind.STRING -> "\"" + dl.value() + "\"";
                case ConcreteLiteralKind.INT, ConcreteLiteralKind.LONG,
                        ConcreteLiteralKind.BOOLEAN, ConcreteLiteralKind.FLOAT,
                        ConcreteLiteralKind.DOUBLE -> dl.value();
                default -> null;
            };
        }
        String method = "required".equals(mc.methodName()) || "get".equals(mc.methodName())
                ? "required" : mc.methodName();
        String dedupe = method + "|" + key + "|" + def;
        if (driver.discoveredConfigKeySet.add(dedupe)) {
            SourcePosition pos = mc.position();
            driver.discoveredConfigKeys.add(new CompilerDriver.ConfigKeyInfo(method, key, def,
                    pos != null ? pos.file() : "", pos != null ? pos.line() : 0));
        }
    }

    static String generateConfigTemplate(CompilerDriver driver) {
        StringBuilder sb = new StringBuilder();
        sb.append("# kof.config — gerado por kof config gen\n");
        sb.append("# Chaves usadas pelo programa (em ordem de primeiro uso).\n");
        sb.append("# Chaves com default estão comentadas — descomente para sobrescrever.\n\n");
        for (CompilerDriver.ConfigKeyInfo k : driver.discoveredConfigKeys) {
            if (k.hasDefault()) {
                sb.append("# ").append(k.key()).append(" = ")
                  .append(k.defaultLiteral().isEmpty() ? "" : k.defaultLiteral())
                  .append("\n");
            } else {
                sb.append("# REQUIRED (sem default no código):\n")
                  .append(k.key()).append(" = \n");
            }
        }
        return sb.toString();
    }
}