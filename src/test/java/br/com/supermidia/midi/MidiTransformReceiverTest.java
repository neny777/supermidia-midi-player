package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void resetInstrumentsSendsGmSystemOnBeforeAnythingElse() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        receiver.resetInstruments();

        // O SysEx padrão precisa vir primeiro: é ele que devolve o dispositivo ao GM,
        // com o canal 10 de novo em percussão.
        MidiMessage first = output.message(0);
        assertArrayEquals(
                new byte[] {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7},
                first.getMessage(),
                "primeira mensagem deveria ser GM System On");
    }

    @Test
    void resetInstrumentsRestoresEveryChannelToTheDefaultProgram() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        receiver.resetInstruments();

        // Sem isto, a bateria da próxima música toca com o instrumento que sobrou
        // no canal de percussão — o arquivo seguinte costuma não reenviar o programa.
        for (int channel = 0; channel < 16; channel++) {
            assertTrue(output.hasShortMessage(ShortMessage.PROGRAM_CHANGE, channel, 0),
                    "faltou Program Change 0 no canal " + (channel + 1));
            assertTrue(output.hasShortMessage(ShortMessage.CONTROL_CHANGE, channel, 121),
                    "faltou Reset All Controllers no canal " + (channel + 1));
            assertTrue(output.hasShortMessage(ShortMessage.CONTROL_CHANGE, channel, 0),
                    "faltou Bank Select MSB no canal " + (channel + 1));
        }
    }

    @Test
    void resetInstrumentsForgetsNotesLeftSoundingByThePreviousSong()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setTranspose(5);
        receiver.send(message(ShortMessage.NOTE_ON, 0, 60, 100), -1);

        receiver.resetInstruments();
        receiver.setTranspose(0);
        output.clear();

        // O Note Off que chegar depois não pode reviver o mapeamento antigo: a nota
        // já foi encerrada pelo reset.
        receiver.send(message(ShortMessage.NOTE_OFF, 0, 60, 0), -1);
        assertEquals(60, output.shortMessage(0).getData1());
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

        MidiMessage message(int index) {
            return messages.get(index);
        }

        boolean hasShortMessage(int command, int channel, int data1) {
            return messages.stream()
                    .filter(ShortMessage.class::isInstance)
                    .map(ShortMessage.class::cast)
                    .anyMatch(candidate -> candidate.getCommand() == command
                            && candidate.getChannel() == channel
                            && candidate.getData1() == data1);
        }

        void clear() {
            messages.clear();
        }

        int size() {
            return messages.size();
        }
    }
}
