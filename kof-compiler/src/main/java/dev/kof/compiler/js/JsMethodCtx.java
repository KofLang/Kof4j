package dev.kof.compiler.js;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arquivo de apoio do lowering de UM método (REFACTOR-500 FASE 4).
 * O estado por método vive em {@link MethodCtx}, o estado por módulo em
 * {@link JsLoweringContext}; os marcadores de pilha (NewPending/DupMarker)
 * e a exceção de controle (StatementEnd) acompanham.
 */
/** Região de um loop em lowering: início, continue e fim. */
/**
 * A pending `new T` awaiting its <init> call: [NewPending, args...] or
 * [NewPending, DupMarker, args...] — lowered to `new T(args)`.
 */
/**
 * Thrown when a void call (or a constructor super call) completes the
 * current statement.
 */
