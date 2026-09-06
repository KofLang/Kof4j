package dev.kof.compiler;

import java.util.List;

/**
 * TIER 6 — `kof.workflow` / `kof.batch`: jobs como código (nunca YAML).
 * JVM-first (WF001 nos demais targets até o runtime fechar). O job é uma
 * closure nomeada registrada em runtime; `run` executa; `pipeline` executa
 * uma lista em sequência.
 */
final class KofWorkflow {
    private KofWorkflow() {}

    static final Type WORKFLOW = new Type.ClassType("kof.workflow", "Workflow", List.of());
    private static final Type STR = BuiltinTypes.STRING;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;

    static boolean isWorkflowNamespace(String name) {
        return "workflow".equals(name) || "batch".equals(name) || "wf".equals(name);
    }

    static boolean isWorkflowMethod(String name) {
        return switch (name) {
            case "job", "run", "pipeline" -> true;
            default -> false;
        };
    }

    record WorkflowCall(String function, Type returnType, List<Type> parameterTypes) {}

    static boolean supportedOn(Target target) {
        return target == Target.JVM || target == Target.ANDROID;
    }

    static WorkflowCall staticCall(String name, List<Type> argTypes) {
        return switch (name) {
            case "job" -> argTypes.size() == 2
                    ? new WorkflowCall("kof_workflow_job", STR, List.of(STR, OBJ)) : null;
            case "run" -> argTypes.size() == 1
                    ? new WorkflowCall("kof_workflow_run", STR, List.of(STR)) : null;
            case "pipeline" -> argTypes.size() == 2
                    ? new WorkflowCall("kof_workflow_pipeline", STR, List.of(STR, OBJ)) : null;
            default -> null;
        };
    }
}