package com.deskink.core;

/** Device-independent, momentary barrel-button state. */
public final class PenInput {
    public enum Tool { PEN, HIGHLIGHTER, ERASER, LASER, VANISHING }
    private Tool selected = Tool.PEN;
    private Tool previous = Tool.PEN;
    private boolean held;
    public void select(Tool tool) {
        selected = java.util.Objects.requireNonNull(tool);
        if (tool != Tool.ERASER) previous = tool;
    }
    public void button(boolean down) { held = down; }
    public void contactEnded() { if (selected == Tool.ERASER) selected = previous; }
    public void cancel() { held = false; contactEnded(); }
    public Tool tool() { return held ? Tool.ERASER : selected; }
}
