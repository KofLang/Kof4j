package dev.kof.compiler;

import java.util.List;

/**
 * G240 — lowering of {@code lock (target) { body }}: the target is evaluated
 * ONCE into a hidden local, {@code kof_lock_enter(target)} runs, and the body
 * becomes the try body of a {@code try/finally} whose finally is the
 * {@link LockExitStmt}. Reusing the try lowering is what makes the release
 * cover every exit: the normal end, a throw (rethrow path), {@code return}
 * (finally frames) and {@code break}/{@code continue} out of an enclosing loop
 * (exited finally bodies).
 */
final class StatementLowererLock {

    private StatementLowererLock() {}

    static int emitLock(CompilerDriver driver, LockStmt ls, List<KofOperation> ops, String owner,
                        int localIdx, List<IRLocalVariable> locals, Type returnType) {
        Type t = ExpressionTyper.inferExprType(driver, ls.target(), locals);
        Type slotType = t == null || Type.isUnknown(t) || Type.isPrimitive(t) ? LockTargets.OBJECT : t;
        localIdx = ExpressionLowerer.emitExpression(driver, ls.target(), ops, owner, localIdx, locals);
        int slot = localIdx++;
        locals.add(new IRLocalVariable(slot, "#lock$" + slot, slotType));
        ops.add(new KofStoreLocal(slotType, slot));
        ops.add(new KofLoadLocal(slotType, slot));
        ops.add(call(LockTargets.ENTER));
        TryStmt guarded = new TryStmt(ls.position(), List.of(ls.body()), List.of(),
                List.of(new LockExitStmt(ls.position(), slotType, slot)));
        return StatementLowererTry.emitTryStmt(driver, guarded, ops, owner, localIdx, locals, returnType);
    }

    static int emitExit(LockExitStmt exit, List<KofOperation> ops, int localIdx) {
        ops.add(new KofLoadLocal(exit.targetType(), exit.slot()));
        ops.add(call(LockTargets.EXIT));
        return localIdx;
    }

    private static KofCall call(String fn) {
        return new KofCall(LockTargets.OWNER, fn, List.of(LockTargets.OBJECT), Type.PrimitiveType.VOID,
                KofCallKind.FUNCTION);
    }
}
