package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiInputDiagnosticsTest {
    private static MidiControlMessage controlChange(int controller, int value) {
        return new MidiControlMessage(ShortMessage.CONTROL_CHANGE, 0, controller, value);
    }

    @Test
    void classifiesFaderSweepAsAbsoluteContinuousControl() {
        MidiInputDiagnostics diagnostics = new MidiInputDiagnostics();
        for (int value = 0; value <= 127; value += 4) {
            diagnostics.observe(controlChange(20, value));
        }

        MidiInputDiagnostics.Entry entry = diagnostics.entries().getFirst();
        assertEquals(MidiInputDiagnostics.Behaviour.CONTINUOUS_ABSOLUTE, entry.behaviour());
        assertEquals(MidiBinding.ValueMode.ABSOLUTE, entry.suggestedValueMode());
        assertEquals(0, entry.minValue());
        assertEquals(124, entry.maxValue());
    }

    @Test
    void classifiesEncoderStepsAsRelativeContinuousControl() {
        MidiInputDiagnostics diagnostics = new MidiInputDiagnostics();
        diagnostics.observe(controlChange(29, 1));
        diagnostics.observe(controlChange(29, 1));
        diagnostics.observe(controlChange(29, 127));

        MidiInputDiagnostics.Entry entry = diagnostics.entries().getFirst();
        assertEquals(MidiInputDiagnostics.Behaviour.CONTINUOUS_RELATIVE, entry.behaviour());
        assertEquals(MidiBinding.ValueMode.RELATIVE, entry.suggestedValueMode());
    }

    @Test
    void classifiesNotePressAsTrigger() {
        MidiInputDiagnostics diagnostics = new MidiInputDiagnostics();
        diagnostics.observe(new MidiControlMessage(ShortMessage.NOTE_ON, 0, 52, 127));
        diagnostics.observe(new MidiControlMessage(ShortMessage.NOTE_OFF, 0, 52, 0));

        assertEquals(1, diagnostics.controlCount());
        MidiInputDiagnostics.Entry entry = diagnostics.entries().getFirst();
        assertEquals(MidiInputDiagnostics.Behaviour.TRIGGER, entry.behaviour());
        assertEquals(2, entry.messageCount());
    }

    @Test
    void groupsMessagesByControlAndCanBeCleared() {
        MidiInputDiagnostics diagnostics = new MidiInputDiagnostics();
        diagnostics.observe(controlChange(20, 10));
        diagnostics.observe(controlChange(20, 40));
        diagnostics.observe(controlChange(21, 40));

        assertEquals(2, diagnostics.controlCount());
        assertEquals(3, diagnostics.messageCount());
        assertTrue(diagnostics.report("SMC-Mixer").contains("CC 20"));

        diagnostics.clear();
        assertTrue(diagnostics.isEmpty());
        assertEquals(0, diagnostics.messageCount());
    }

    @Test
    void treatsPitchBendAsSingleAbsoluteControlRegardlessOfDataBytes() {
        MidiInputDiagnostics diagnostics = new MidiInputDiagnostics();
        diagnostics.observe(new MidiControlMessage(ShortMessage.PITCH_BEND, 3, 0, 0));
        diagnostics.observe(new MidiControlMessage(ShortMessage.PITCH_BEND, 3, 64, 90));

        assertEquals(1, diagnostics.controlCount());
        assertEquals(MidiInputDiagnostics.Behaviour.CONTINUOUS_ABSOLUTE,
                diagnostics.entries().getFirst().behaviour());
    }
}
