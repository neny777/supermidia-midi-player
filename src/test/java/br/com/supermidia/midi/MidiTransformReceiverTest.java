package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MidiTransformReceiverTest {
    @Test
    void transposesMelodicNotesButKeepsPercussionUntouched() throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setTranspose(3);

        receiver.send(message(ShortMessage.NOTE_ON, 0, 60, 100), -1);
        receiver.send(message(ShortMessage.NOTE_ON, 9, 36, 100), -1);

        assertEquals(63, output.shortMessage(0).getData1());
        assertEquals(36, output.shortMessage(1).getData1());
    }

    @Test
    void noteOffUsesTheTransposeActiveWhenTheNoteStarted() throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setTranspose(2);
        receiver.send(message(ShortMessage.NOTE_ON, 0, 60, 90), -1);

        receiver.setTranspose(7);
        receiver.send(message(ShortMessage.NOTE_OFF, 0, 60, 0), -1);

        assertEquals(62, output.shortMessage(0).getData1());
        assertEquals(62, output.shortMessage(1).getData1());
    }

    @Test
    void scalesChannelVolumeUsingTheMasterControl() throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setMasterVolume(0.5);
        output.clear();

        receiver.send(message(ShortMessage.CONTROL_CHANGE, 2, 7, 100), -1);

        ShortMessage result = output.shortMessage(0);
        assertEquals(ShortMessage.CONTROL_CHANGE, result.getCommand());
        assertEquals(2, result.getChannel());
        assertEquals(7, result.getData1());
        assertEquals(50, result.getData2());
    }

    private static ShortMessage message(int command, int channel, int data1, int data2)
            throws InvalidMidiDataException {
        ShortMessage message = new ShortMessage();
        message.setMessage(command, channel, data1, data2);
        return message;
    }

    private static final class RecordingReceiver implements Receiver {
        private final List<MidiMessage> messages = new ArrayList<>();

        @Override
        public void send(MidiMessage message, long timeStamp) {
            messages.add(message);
        }

        @Override
        public void close() {
        }

        ShortMessage shortMessage(int index) {
            return (ShortMessage) messages.get(index);
        }

        void clear() {
            messages.clear();
        }
    }
}
