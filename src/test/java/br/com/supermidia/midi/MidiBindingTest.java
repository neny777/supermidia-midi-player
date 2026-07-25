package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.ShortMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiBindingTest {
    @Test
    void learnsAndMatchesPitchBendByChannel() {
        MidiBinding binding = MidiBinding.learn(
                new MidiControlMessage(ShortMessage.PITCH_BEND, 3, 0, 45), true)
                .orElseThrow();

        assertTrue(binding.matches(
                new MidiControlMessage(ShortMessage.PITCH_BEND, 3, 0, 80)));
        assertFalse(binding.matches(
                new MidiControlMessage(ShortMessage.PITCH_BEND, 4, 0, 80)));
        assertEquals(MidiBinding.ValueMode.ABSOLUTE, binding.valueMode());
    }

    @Test
    void calculatesRelativeEncoderDeltasWhenAProfileUsesThatMode() {
        MidiBinding binding = new MidiBinding(
                ShortMessage.CONTROL_CHANGE, 0, 16, MidiBinding.ValueMode.RELATIVE);

        assertEquals(MidiBinding.ValueMode.RELATIVE, binding.valueMode());
        assertEquals(-1, binding.relativeDelta(
                new MidiControlMessage(ShortMessage.CONTROL_CHANGE, 0, 16, 65)));
        assertEquals(1, binding.relativeDelta(
                new MidiControlMessage(ShortMessage.CONTROL_CHANGE, 0, 16, 1)));
    }

    @Test
    void learnsAControlChangeAsAnAbsoluteControlByDefault() {
        MidiBinding binding = MidiBinding.learn(
                new MidiControlMessage(ShortMessage.CONTROL_CHANGE, 0, 30, 66), true)
                .orElseThrow();

        assertEquals(MidiBinding.ValueMode.ABSOLUTE, binding.valueMode());
    }

    @Test
    void learnsOnlyThePressOfANoteButton() {
        assertTrue(MidiBinding.learn(
                new MidiControlMessage(ShortMessage.NOTE_ON, 0, 42, 127), false).isPresent());
        assertTrue(MidiBinding.learn(
                new MidiControlMessage(ShortMessage.NOTE_ON, 0, 42, 0), false).isEmpty());
        assertTrue(MidiBinding.learn(
                new MidiControlMessage(ShortMessage.NOTE_OFF, 0, 42, 0), false).isEmpty());
    }

    @Test
    void roundTripsThePersistentFormat() {
        MidiBinding original = new MidiBinding(
                ShortMessage.CONTROL_CHANGE, 7, 23, MidiBinding.ValueMode.RELATIVE);

        MidiBinding restored = MidiBinding.decode(original.encode()).orElseThrow();

        assertEquals(original, restored);
    }
}
