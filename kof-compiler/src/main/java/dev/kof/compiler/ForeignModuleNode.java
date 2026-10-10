package dev.kof.compiler;

import java.util.List;

/**
 * Connector ecosystem (`D-CONNECTORS`, plan §9.16 slice A): o cabeçalho
 * {@code foreign module <name> { ... }} agrupa declarações {@code extern} sob
 * uma biblioteca (e ABI/ownership declarados). É o degrau mínimo que põe o
 * construto na gramática sem novo motor de ABI (regra 54): o parser o desdobra
 * em {@link ExternalFunctionNode} normais, ligados à {@code CompilerFfiBinding}
 * já existente. O desenho continua sendo o já decidido em `D-CONNECTORS`
 * (library + symbols + ABI + ownership); nenhuma sintaxe nova além do bloco.
 */
public record ForeignModuleNode(SourcePosition position, String name, String library, String abi,
                                String ownership, List<ExternalFunctionNode> externs) implements AstNode {
}
