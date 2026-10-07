package dev.kof.compiler;

import java.util.List;

/**
 * D-PORTUKOF unidade 3 (07/10) — categorias de receiver cujo tipo determina
 * o mapeamento de aliases de métodos e campos.
 *
 * <p>A classificação espelha a precedência dos tybers reais ({@code MethodCallTyper}
 * e {@code SemMethodCallTyper}): tipos com handles específicos vêm antes de
 * coleções/primitivos; coleções vêm antes de classes genéricas. Classes e
 * módulos do USUÁRIO retornam {@code null} — nenhum alias de superfície se
 * aplica a declarações do usuário (PARTE 5 do contrato PortuKof).
 */
public enum PortuKofMethodCategories {
    STRING,
    LIST,
    MAP,
    SET,
    ARRAY,
    PRIMITIVE,
    ENUM,
    IO,
    BUFFER,
    UI,
    WEB_APP,
    PROCESS_HANDLE,
    PROCESS_RESULT,
    MEDIA,
    SECRET,
    KEY_HANDLE,
    INTEROP_ERROR;

    public static PortuKofMethodCategories of(Type recvType, CompilationUnitNode unit) {
        if (recvType == null || recvType instanceof Type.UnknownType) return null;
        if (recvType instanceof Type.NullableType nt) recvType = nt.inner();

        // Handles especializados do runtime Kof — precedência mais alta
        if (KofProcess.isHandle(recvType)) return PROCESS_HANDLE;
        if (KofProcess.isResult(recvType)) return PROCESS_RESULT;
        if (KofUi.isUiType(recvType)) return UI;
        if (KofWeb.isAppType(recvType)) return WEB_APP;
        if (KofMedia.isHandleType(recvType)) return MEDIA;
        if (KofBuffer.isBufferType(recvType)) return BUFFER;
        if (KofSecurity.isSecretType(recvType)) return SECRET;
        if (KofSecurity.isKeyHandleType(recvType)) return KEY_HANDLE;
        if (KofInteropError.isInteropErrorType(recvType)) return INTEROP_ERROR;
        if (KofIo.isIoType(recvType)) return IO;

        // Tipos de coleção e primitivos
        if (Type.isString(recvType)) return STRING;
        if (BuiltinTypes.isList(recvType)) return LIST;
        if (BuiltinTypes.isMap(recvType)) return MAP;
        if (BuiltinTypes.isSet(recvType)) return SET;
        if (recvType instanceof Type.ArrayType) return ARRAY;

        if (unit != null && CompilerTypes.isEnumType(recvType, unit)) {
            return ENUM;
        }

        if (isPrimitiveLike(recvType)) return PRIMITIVE;

        // Classes de usuário ou desconhecidas: sem alias de receiver
        return null;
    }

    private static boolean isPrimitiveLike(Type t) {
        if (t instanceof Type.PrimitiveType) return true;
        if (t instanceof Type.ClassType ct) {
            String n = ct.name();
            return "Bool".equals(n) || "Int".equals(n) || "Long".equals(n)
                    || "Float".equals(n) || "Double".equals(n) || "Char".equals(n)
                    || "Byte".equals(n) || "Short".equals(n);
        }
        return false;
    }
}
