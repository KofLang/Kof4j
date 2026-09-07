package dev.kof.compiler;

import java.util.List;
import java.util.Map;





/**
 * Annotation preservada na IR: nome interno JVM ("androidx/annotation/NonNull")
 * e valores constantes em compile-time (String/Integer/Long/Float/Double/
 * Boolean/Character/null ou List desses para arrays).
 */
/** Valor Class<?> de annotation (nome interno JVM). */
/** Valor enum constante de annotation (classe interna JVM + constante). */
/**
 * KofDebugInfo — backend-agnostic debug metadata.
 * Maps each IR operation to its source position so backends can emit
 * line tables / source maps that keep the Kof identity. The position is
 * registered before the backend, never synthesized there.
 */
sealed interface KofOperation permits KofArrayLength, KofArrayLoad, KofArrayStore, KofBinary, KofCall, KofCatchStart, KofCheckCast, KofConditionalJump, KofDup, KofDupX1, KofDupX2, KofGetStatic, KofInstanceOf, KofJump, KofLabel, KofLoadField, KofLoadLiteral, KofLoadLocal, KofNewArray, KofNewObject, KofPop, KofPutStatic, KofReturn, KofReturnVoid, KofStoreField, KofStoreLocal, KofThrow, KofTryEnd, KofTryStart, KofUnary {
}

/**
 * SUPER: non-virtual call to a superclass implementation (super.method()).
 * JVM backend lowers it to INVOKESPECIAL; the owner is the direct superclass.
 */
/**
 * Duplicates the top value below the second slot: [A, B] → [B, A, B].
 * Used for postfix field increments (the receiver must survive for the store).
 */
/**
 * Duplicates the top value two slots below: [A, B, C] → [C, A, B, C].
 * Used for prefix array increments (the value survives the array store).
 */
