package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
final class StatementEnd extends RuntimeException {
    final JsIr.JsExpression call;

    StatementEnd(JsIr.JsExpression call) {
        this.call = call;
    }
}
