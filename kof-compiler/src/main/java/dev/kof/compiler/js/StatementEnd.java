package dev.kof.compiler.js;
import dev.kof.compiler.js.JsIr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public final class StatementEnd extends RuntimeException {
    final JsIr.JsExpression call;

    StatementEnd(JsIr.JsExpression call) {
        this.call = call;
    }
}
