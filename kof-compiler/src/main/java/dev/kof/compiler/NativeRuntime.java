package dev.kof.compiler;

import java.util.List;

/**
 * Orquestrador do runtime nativo x86-64 (gera o ASM de todo o runtime em
 * string). Cada domínio vive numa classe própria ({@code Runtime*}) — esta
 * classe só ordena as emissões e as constantes de layout compartilhadas.
 */
final class NativeRuntime {

    private NativeRuntime() {}

    static String generateRuntimeAssembly() {
        StringBuilder sb = new StringBuilder();
        // marcador de início da área de raízes estáticas: o GC mark conservador
        // precisa varrer .data (cache/config/mq além de .bss). As emissões de
        // .data acontecem abaixo; o primeiro rótulo fica aqui (antes de tudo).
        // IMPORTANTE: voltar pra .text — senão emitPrint grava kof_print em .data
        // e o executável inteiro quebra (visto: SIGSEGV em println "a").
        sb.append("            .section .data\n");
        sb.append("            .globl kof_heap_root_start\n");
        sb.append("            kof_heap_root_start:\n");
        sb.append("            .quad 0\n");
        sb.append("            .section .text\n");
        RuntimePrint.emitPrint(sb);
        RuntimePrint.emitPrintln(sb);
        RuntimePrintNum.emitPrintInt(sb);
        RuntimePrintNum.emitPrintFloat(sb);
        RuntimePrintNum.emitPrintDouble(sb);
        RuntimeStringConv.emitFloatToString(sb);
        RuntimeStringConv.emitDoubleToString(sb);
        RuntimeStringConv.emitIntToString(sb);
        RuntimeStringConv.emitCharToString(sb);
        RuntimeStringConv.emitLongToString(sb);
        RuntimeStringConv.emitBoolToString(sb);
        RuntimeList.emitListFunctions(sb);
        RuntimeJsonBuilder.emitJsonBuilder(sb);
        RuntimeJsonEncode.emitJsonEncode(sb);
        RuntimeJsonDecode.emitJsonDecode(sb);
        RuntimeJsonArrayDecode1.emit(sb);
        RuntimeJsonArrayDecode2.emit(sb);
        RuntimeJsonUtils.emitJsonQuote(sb);
        RuntimeJsonUtils.emitJsonFindValue(sb);
        RuntimeMemory.emitAlloc(sb);
        RuntimeMemory.emitFree(sb);
        RuntimeGc.emitGc(sb);
        RuntimeConcurrency.emitConcurrency(sb);
        RuntimeChannel.emitChannel(sb);
        RuntimeScheduler.emitScheduler(sb);
        RuntimeMq.emitMq(sb);
        RuntimeGc.emitProcessExit(sb);
        RuntimeGc.emitPanic(sb);
        RuntimeGc.emitNullError(sb);
        RuntimeGc.emitBoundsError(sb);
        RuntimeStringBase.emitMemcpy(sb);
        RuntimeStringBase.emitStringFromLiteral(sb);
        RuntimeStringBase.emitStringLength(sb);
        RuntimeStringBase.emitStringConcat(sb);
        RuntimeStringBase.emitStringEquals(sb);
        RuntimeStringParse.emitStringToInt(sb);
        RuntimeStringParse.emitStringToLong(sb);
        RuntimeStringParse.emitStringToDouble(sb);
        RuntimeStringParse.emitStringToFloat(sb);
        RuntimeStringBase.emitPrintString(sb);
        RuntimeStringBase.emitPrintlnString(sb);
        RuntimeStringOps.emitStringCharAt(sb);
        RuntimeStringOps.emitStringSubstring(sb);
        RuntimeStringSearch.emitStringContains(sb);
        RuntimeStringSearch.emitStringStartsWith(sb);
        RuntimeStringSearch.emitStringEndsWith(sb);
        RuntimeStringSearch.emitStringIndexOf(sb);
        RuntimeStringSearch.emitStringLastIndexOf(sb);
        RuntimeStringOps.emitStringTrim(sb);
        RuntimeStringOps.emitStringCase(sb);
        RuntimeStringEdit.emitStringReplace(sb);
        RuntimeStringOps.emitStringEqualsIgnoreCase(sb);
        RuntimeStringEdit.emitStringSplit(sb);
        RuntimeArray.emitArrayAlloc(sb);
        RuntimeArray.emitArrayLength(sb);
        RuntimeArray.emitArrayGet(sb);
        RuntimeArray.emitArraySet(sb);
        RuntimeMemory.emitMemstats(sb);
        RuntimeTime.emitIoTimeFunctions(sb);
        RuntimeTime.emitKofTimeFunctions(sb);
        RuntimeCache.emitCacheFunctions(sb);
        RuntimeVk.emitVkStubs(sb);
        RuntimeLog1.emit(sb);
        RuntimeLog2.emit(sb);
        RuntimeConfig1.emit(sb);
        RuntimeConfig2.emit(sb);
        RuntimeIo1.emit(sb);
        RuntimeIo2.emit(sb);
        RuntimeIo3.emit(sb);
        RuntimeUi.emitUiColorFunctions(sb);
        RuntimeUi.emitUiWindowFunctions(sb);
        RuntimeNet.emitNetSocket(sb);
        RuntimeNet.emitNetBind(sb);
        RuntimeNet.emitNetListen(sb);
        RuntimeNet.emitNetAccept(sb);
        RuntimeNet.emitNetRead(sb);
        RuntimeNet.emitNetWrite(sb);
        RuntimeNet.emitNetClose(sb);
        RuntimeMisc.emitInstanceof(sb);
        RuntimeSecurity1.emit(sb);
        RuntimeSecurity2.emit(sb);
        RuntimeSecurity3.emit(sb);
        RuntimeSecurity4.emit(sb);
        RuntimeSecurity5.emit(sb);
        RuntimeSecurity6.emit(sb);
        RuntimeSecurity7.emit(sb);
        RuntimeSecurity8.emit(sb);
        RuntimeSecurity9.emit(sb);
        RuntimeSecurity10.emit(sb);
        RuntimeSecurity11.emit(sb);
        RuntimeValidation.emit(sb);
        RuntimeObservability1.emit(sb);
        RuntimeObservability2.emit(sb);
        RuntimeObservability3.emit(sb);
        RuntimeEnum.emit(sb);
        RuntimeMap.emit(sb);
        RuntimeSet.emit(sb);
        return sb.toString();
    }

    static void generateMethodTable(StringBuilder sb, String className, List<String> methodNames) {
        sb.append(".balign 8\n");
        sb.append(".globl ").append(className).append("_vtable\n");
        sb.append(".type ").append(className).append("_vtable, @object\n");
        sb.append(className).append("_vtable:\n");
        for (String methodName : methodNames) {
            sb.append("    .quad ").append(methodName).append("\n");
        }
        sb.append("    .quad 0\n");
    }

    static final int KOF_STRING_TYPE_ID = 1;
    static final int KOF_STRING_HEADER_SIZE = 24;

    static final int KOF_ARRAY_TYPE_ID = 2;
    static final int KOF_ARRAY_HEADER_SIZE = 24;
}