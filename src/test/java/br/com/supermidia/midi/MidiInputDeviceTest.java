package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiDevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiInputDeviceTest {
    @Test
    void representsTheDisconnectedOption() {
        MidiInputDevice input = MidiInputDevice.none();

        assertTrue(input.isNone());
        assertEquals("Nenhuma", input.name());
        assertEquals("Nenhuma entrada MIDI", input.toString());
    }

    @Test
    void presentsTheDeviceNameAndDescription() {
        MidiInputDevice input = new MidiInputDevice(
                new TestInfo("SMC-Mixer", "Entrada USB MIDI"));

        assertFalse(input.isNone());
        assertEquals("SMC-Mixer", input.name());
        assertEquals("SMC-Mixer — Entrada USB MIDI", input.toString());
    }

    @Test
    void hidesTheGenericWindowsDescription() {
        MidiInputDevice input = new MidiInputDevice(
                new TestInfo("SMC-Mixer", "No details available"));

        assertEquals("SMC-Mixer", input.toString());
    }

    private static final class TestInfo extends MidiDevice.Info {
        private TestInfo(String name, String description) {
            super(name, "SuperMídia", description, "1.0");
        }
    }
}
