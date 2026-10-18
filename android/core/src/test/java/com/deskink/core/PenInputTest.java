package com.deskink.core;

import org.junit.Test;
import static org.junit.Assert.*;

/** Public input state: momentary S Pen eraser must not behave like a toggle. */
public class PenInputTest {
    @Test public void toolbarEraserReturnsToPreviousToolAfterOneContact() {
        PenInput input = new PenInput();
        input.select(PenInput.Tool.HIGHLIGHTER);
        input.select(PenInput.Tool.ERASER);
        assertEquals(PenInput.Tool.ERASER, input.tool());
        input.contactEnded();
        assertEquals(PenInput.Tool.HIGHLIGHTER, input.tool());
    }
    @Test public void holdingTheButtonErasesAndReleasingRestoresTheSelectedPen() {
        PenInput input = new PenInput();
        input.select(PenInput.Tool.PEN);
        input.button(true);
        assertEquals(PenInput.Tool.ERASER, input.tool());
        input.button(true); // repeated hover/move event, still held
        input.button(false);
        assertEquals(PenInput.Tool.PEN, input.tool());
    }

    @Test public void liftingWhileHeldKeepsEraserButCancellationCannotLeaveItStuck() {
        PenInput input = new PenInput();
        input.select(PenInput.Tool.HIGHLIGHTER);
        input.button(true);
        input.contactEnded();
        assertEquals(PenInput.Tool.ERASER, input.tool());
        input.cancel();
        assertEquals(PenInput.Tool.HIGHLIGHTER, input.tool());
    }
}
