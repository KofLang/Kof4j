package dev.kof.compiler;
import dev.kof.compiler.runtime.RuntimeArray;
import dev.kof.compiler.runtime.RuntimeCache;
import dev.kof.compiler.runtime.RuntimeEncoding;
import dev.kof.compiler.runtime.RuntimeValidationBr;
import dev.kof.compiler.runtime.RuntimeValidationFmtBr;
import dev.kof.compiler.runtime.RuntimeValidationNet;
import dev.kof.compiler.runtime.RuntimeRandom;
import dev.kof.compiler.runtime.RuntimeUuid;
import dev.kof.compiler.runtime.RuntimeChannel;
import dev.kof.compiler.runtime.RuntimeConcurrency;
import dev.kof.compiler.runtime.RuntimeConfig1;
import dev.kof.compiler.runtime.RuntimeConfig2;
import dev.kof.compiler.runtime.RuntimeEnum;
import dev.kof.compiler.runtime.RuntimeGc;
import dev.kof.compiler.runtime.RuntimeIo1;
import dev.kof.compiler.runtime.RuntimeIo2;
import dev.kof.compiler.runtime.RuntimeIo3;
import dev.kof.compiler.runtime.RuntimeJsonArrayDecode1;
import dev.kof.compiler.runtime.RuntimeJsonArrayDecode2;
import dev.kof.compiler.runtime.RuntimeJsonBuilder;
import dev.kof.compiler.runtime.RuntimeJsonDecode;
import dev.kof.compiler.runtime.RuntimeJsonEncode;
import dev.kof.compiler.runtime.RuntimeJsonUtils;
import dev.kof.compiler.runtime.RuntimeList;
import dev.kof.compiler.runtime.RuntimeLog1;
import dev.kof.compiler.runtime.RuntimeLog2;
import dev.kof.compiler.runtime.RuntimeMap;
import dev.kof.compiler.runtime.RuntimeMemory;
import dev.kof.compiler.runtime.RuntimeMath;
import dev.kof.compiler.runtime.RuntimeStrings;
import dev.kof.compiler.runtime.RuntimeMisc;
import dev.kof.compiler.runtime.RuntimeMq;
import dev.kof.compiler.runtime.RuntimeNet;
import dev.kof.compiler.runtime.RuntimeUri;
import dev.kof.compiler.runtime.RuntimeObservability1;
import dev.kof.compiler.runtime.RuntimeObservability2;
import dev.kof.compiler.runtime.RuntimeObservability3;
import dev.kof.compiler.runtime.RuntimePrint;
import dev.kof.compiler.runtime.RuntimePrintNum;
import dev.kof.compiler.runtime.RuntimeScheduler;
import dev.kof.compiler.runtime.RuntimeSecurity10;
import dev.kof.compiler.runtime.RuntimeSecurity11;
import dev.kof.compiler.runtime.RuntimeSecurity1;
import dev.kof.compiler.runtime.RuntimeSecurity2;
import dev.kof.compiler.runtime.RuntimeSecurity3;
import dev.kof.compiler.runtime.RuntimeSecurity4;
import dev.kof.compiler.runtime.RuntimeSecurity5;
import dev.kof.compiler.runtime.RuntimeSecurity6;
import dev.kof.compiler.runtime.RuntimeSecurity7;
import dev.kof.compiler.runtime.RuntimeSecurity8;
import dev.kof.compiler.runtime.RuntimeSecurity9;
import dev.kof.compiler.runtime.RuntimeSecurityData;
import dev.kof.compiler.runtime.RuntimeSet;
import dev.kof.compiler.runtime.RuntimeStringBase;
import dev.kof.compiler.runtime.RuntimeStringConv;
import dev.kof.compiler.runtime.RuntimeStringEdit;
import dev.kof.compiler.runtime.RuntimeStringOps;
import dev.kof.compiler.runtime.RuntimeStringParse;
import dev.kof.compiler.runtime.RuntimeStringParseFp;
import dev.kof.compiler.runtime.RuntimeStringSearch;
import dev.kof.compiler.runtime.RuntimeTime;
import dev.kof.compiler.runtime.RuntimeTimeIso;
import dev.kof.compiler.runtime.RuntimeUi;
import dev.kof.compiler.runtime.RuntimeValidation;

import java.util.List;

/**
 * Orquestrador do runtime nativo x86-64 (gera o ASM de todo o runtime em
 * string). Cada domínio vive numa classe própria ({@code Runtime*}) — esta
 * classe só ordena as emissões e as constantes de layout compartilhadas.
 */
public final class NativeRuntime {

    private NativeRuntime() {}

    static public String generateRuntimeAssembly() {
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
        RuntimeStringParseFp.emitStringToDouble(sb);
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
        RuntimeTimeIso.emitTimeIsoFunctions(sb);
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
        RuntimeSecurityData.emit(sb);
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
        RuntimeValidationBr.emit(sb);
        RuntimeValidationFmtBr.emit(sb);
        RuntimeValidationNet.emit(sb);
        RuntimeUri.emit(sb);
        RuntimeMath.emit(sb);
        RuntimeStrings.emit(sb);
        RuntimeEncoding.emit(sb);
        RuntimeUuid.emit(sb);
        RuntimeRandom.emit(sb);
        RuntimeObservability1.emit(sb);
        RuntimeObservability2.emit(sb);
        RuntimeObservability3.emit(sb);
        RuntimeEnum.emit(sb);
        RuntimeMap.emit(sb);
        RuntimeSet.emit(sb);
        return sb.toString();
    }

    static public void generateMethodTable(StringBuilder sb, String className, List<String> methodNames) {
        sb.append(".balign 8\n");
        sb.append(".globl ").append(className).append("_vtable\n");
        sb.append(".type ").append(className).append("_vtable, @object\n");
        sb.append(className).append("_vtable:\n");
        for (String methodName : methodNames) {
            sb.append("    .quad ").append(methodName).append("\n");
        }
        sb.append("    .quad 0\n");
    }

    static public final int KOF_STRING_TYPE_ID = 1;
    static final int KOF_STRING_HEADER_SIZE = 24;

    static final int KOF_ARRAY_TYPE_ID = 2;
    static final int KOF_ARRAY_HEADER_SIZE = 24;
}