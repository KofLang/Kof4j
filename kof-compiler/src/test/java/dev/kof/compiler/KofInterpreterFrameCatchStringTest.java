package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §810-family, cycle 15/11 — the `catch (String e)` contract binds a USABLE,
 * non-empty String. The interpreter's {@code handleException} used to bind
 * {@code re.getMessage()} DIRECTLY — and the JDK builds the range-fault
 * messages LAZILY since 9: an {@code ArrayIndexOutOfBoundsException} arriving
 * message-less (fast-throw / allocation pressure under a loaded suite) bound
 * {@code null} into the catch variable, and the program's next {@code
 * e.length()} died with {@code NoSuchMethodError: java.lang.String.length/0}
 * (a null receiver cannot resolve the instance {@code length}). Measured as
 * the 1F of the stamped suite on `5ce6fcd8c` — three consecutive
 * isolation-green runs hid it, because the message-less variant only appears
 * under suite load.
 *
 * <p>The JVM oracle ALWAYS delivers a non-empty message on its range faults
 * (the language runtime guarantees the text), so the script target must match:
 * a message-less fault binds the fault's own TYPE NAME (the honest non-empty
 * text — the same shape as the native fix, whose guard builds the real KofString
 * before {@code kof_throw_string}), never a silent {@code null}.
 */
class KofInterpreterFrameCatchStringTest {

    /** A fault whose message is null — the lazy-message JDK variant. */
    private static Exception messageLessFault() {
        Exception e = new ArrayIndexOutOfBoundsException();
        assertNull(e.getMessage(), "fixture: a message-less fault");
        return e;
    }

    private KofInterpreterFrame frameFor() {
        return new KofInterpreterFrame(null);
    }

    /** Minimal frame with a try region covering {@code at} and a handler. */
    private KofInterpreterFrame.Frame frameWithTry(String catchType) {
        KofInterpreterFrame.Frame f = new KofInterpreterFrame.Frame(List.of());
        f.tryStack.push(new KofInterpreterFrame.TryRegion(0, 100, 50, catchType, 0, 0));
        f.pc = 1;  // o fault "acontece" dentro da região [0..100]
        return f;
    }

    @Test
    void aMessageLessFaultBindsTheTypeNameNotSilentNull() {
        KofInterpreterFrame f = frameFor();
        KofInterpreterFrame.Frame fr = frameWithTry("String");
        Exception fault = messageLessFault();
        assertTrue(f.handleException(fr, fault),
                "o catch (String) casa a fault de faixa (§810)");
        Object bound = fr.stack.pop();
        assertNotNull(bound, "o catch NUNCA liga null (o contrato é uma String usável)");
        String text = (String) bound;
        assertTrue(!text.isEmpty(), "o texto ligado não é vazio: " + text);
        assertEquals("ArrayIndexOutOfBoundsException", text,
                "sem mensagem, o nome do TIPO do fault é o texto honesto");
    }

    @Test
    void aRealMessageStillBindsTheMessage() {
        KofInterpreterFrame f = frameFor();
        KofInterpreterFrame.Frame fr = frameWithTry("String");
        Exception fault = new ArrayIndexOutOfBoundsException("Index 99 out of bounds for length 2");
        assertTrue(f.handleException(fr, fault));
        Object bound = fr.stack.pop();
        assertEquals("Index 99 out of bounds for length 2", bound,
                "com mensagem, o bind segue sendo a mensagem (o golden JVM)");
    }

    @Test
    void aKofThrownStringStillBindsItsText() {
        KofInterpreterFrame f = frameFor();
        KofInterpreterFrame.Frame fr = frameWithTry("String");
        // the KofThrow path wraps the String in a RuntimeException (asThrowable)
        Exception fault = new RuntimeException("bounds-caught text");
        assertTrue(f.handleException(fr, fault));
        Object bound = fr.stack.pop();
        assertEquals("bounds-caught text", bound,
                "o throw de String do programa liga o TEXTO, não o wrapper");
    }

    @Test
    void aNonMatchingCatchDoesNotBind() {
        KofInterpreterFrame f = frameFor();
        KofInterpreterFrame.Frame fr = frameWithTry("FileNotFoundFault");
        Exception fault = messageLessFault();
        assertFalse(f.handleException(fr, fault),
                "um catch de outro tipo não captura a fault de faixa");
    }

    @Test
    void theContractSurvivesARealisticProgramShape() {
        // the exact shape of the flaked E2E: catch (String e) { e.length() }
        // — the bound value must survive a .length() call (never null).
        KofInterpreterFrame f = frameFor();
        KofInterpreterFrame.Frame fr = frameWithTry("String");
        assertTrue(f.handleException(fr, messageLessFault()));
        Object e = fr.stack.pop();
        assertEquals(30, ((String) e).length(),
                "e.length() funciona sobre o texto ligado (o nome do tipo)");
    }
}
