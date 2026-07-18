package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void channelVolumeOverrideTakesPriorityOverTheFileVolume()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setMasterVolume(0.8);
        receiver.setChannelVolume(3, 50);
        output.clear();

        receiver.send(message(ShortMessage.CONTROL_CHANGE, 3, 7, 110), -1);

        assertEquals(40, output.shortMessage(0).getData2());
    }

    @Test
    void muteBlocksOnlyTheSelectedChannelAndTracksItsActivity()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setChannelMuted(1, true);
        output.clear();

        receiver.send(message(ShortMessage.NOTE_ON, 1, 60, 100), -1);
        receiver.send(message(ShortMessage.NOTE_ON, 2, 64, 100), -1);

        assertEquals(1, output.size());
        assertEquals(2, output.shortMessage(0).getChannel());
        assertTrue(receiver.isChannelActive(1, 1_000));
        assertFalse(receiver.isChannelActive(3, 1_000));
    }

    @Test
    void soloBlocksEveryChannelExceptTheSoloSelection()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setChannelSolo(4, true);
        output.clear();

        receiver.send(message(ShortMessage.NOTE_ON, 3, 60, 100), -1);
        receiver.send(message(ShortMessage.NOTE_ON, 4, 64, 100), -1);

        assertEquals(1, output.size());
        assertEquals(4, output.shortMessage(0).getChannel());
    }

    @Test
    void resetMixerClearsOverridesMuteAndSolo()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setChannelVolume(0, 20);
        receiver.setChannelMuted(0, true);
        receiver.setChannelSolo(3, true);
        int[] originalVolumes = new int[16];
        java.util.Arrays.fill(originalVolumes, 100);
        originalVolumes[0] = 90;

        receiver.resetMixer(originalVolumes);
        output.clear();
        receiver.send(message(ShortMessage.CONTROL_CHANGE, 0, 7, 110), -1);
        receiver.send(message(ShortMessage.NOTE_ON, 0, 60, 100), -1);

        assertEquals(2, output.size());
        assertEquals(110, output.shortMessage(0).getData2());
        assertEquals(ShortMessage.NOTE_ON, output.shortMessage(1).getCommand());
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

        int size() {
            return messages.size();
        }
    }
}
