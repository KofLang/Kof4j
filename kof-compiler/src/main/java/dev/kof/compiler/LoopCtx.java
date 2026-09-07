package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
public final class LoopCtx {
    final LabelId start;
    final LabelId continueLabel;
    final LabelId end;

    LoopCtx(LabelId start, LabelId continueLabel, LabelId end) {
        this.start = start;
        this.continueLabel = continueLabel;
        this.end = end;
    }
}
