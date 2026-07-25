package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MidiControlMessageTest {
    @Test
    void copiesAControlChangeMessage() throws InvalidMidiDataException {
        ShortMessage source = new ShortMessage();
        source.setMessage(ShortMessage.CONTROL_CHANGE, 2, 16, 65);

        MidiControlMessage message = MidiControlMessage.from(source);

        assertEquals(ShortMessage.CONTROL_CHANGE, message.command());
        assertEquals(2, message.channel());
        assertEquals(16, message.data1());
        assertEquals(65, message.data2());
    }
}
