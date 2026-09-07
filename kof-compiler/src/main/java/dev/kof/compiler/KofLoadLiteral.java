package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record KofLoadLiteral(Type type, Object value) implements KofOperation {
    public static KofLoadLiteral ofInt(int value) {
        return new KofLoadLiteral(Type.PrimitiveType.INT, value);
    }
    public static KofLoadLiteral ofLong(long value) {
        return new KofLoadLiteral(Type.PrimitiveType.LONG, value);
    }
    public static KofLoadLiteral ofFloat(float value) {
        return new KofLoadLiteral(Type.PrimitiveType.FLOAT, value);
    }
    public static KofLoadLiteral ofDouble(double value) {
        return new KofLoadLiteral(Type.PrimitiveType.DOUBLE, value);
    }
    public static KofLoadLiteral ofString(String value) {
        return new KofLoadLiteral(BuiltinTypes.STRING, value);
    }
    public static KofLoadLiteral ofBool(boolean value) {
        return new KofLoadLiteral(Type.PrimitiveType.BOOL, value ? 1 : 0);
    }
    public static KofLoadLiteral ofNull() {
        return new KofLoadLiteral(Type.UnknownType.UNKNOWN, null);
    }
}
