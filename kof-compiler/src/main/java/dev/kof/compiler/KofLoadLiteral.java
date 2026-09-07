package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofLoadLiteral(Type type, Object value) implements KofOperation {
    static KofLoadLiteral ofInt(int value) {
        return new KofLoadLiteral(Type.PrimitiveType.INT, value);
    }
    static KofLoadLiteral ofLong(long value) {
        return new KofLoadLiteral(Type.PrimitiveType.LONG, value);
    }
    static KofLoadLiteral ofFloat(float value) {
        return new KofLoadLiteral(Type.PrimitiveType.FLOAT, value);
    }
    static KofLoadLiteral ofDouble(double value) {
        return new KofLoadLiteral(Type.PrimitiveType.DOUBLE, value);
    }
    static KofLoadLiteral ofString(String value) {
        return new KofLoadLiteral(BuiltinTypes.STRING, value);
    }
    static KofLoadLiteral ofBool(boolean value) {
        return new KofLoadLiteral(Type.PrimitiveType.BOOL, value ? 1 : 0);
    }
    static KofLoadLiteral ofNull() {
        return new KofLoadLiteral(Type.UnknownType.UNKNOWN, null);
    }
}
