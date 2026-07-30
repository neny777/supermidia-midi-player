package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
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
    void resetInstrumentsSendsGmSystemOnLastSoThePauseProtectsIt() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        receiver.resetInstruments(SynthResetMode.GENERAL_MIDI);

        // O SysEx é o que o aparelho leva mais tempo para digerir, e a pausa vem logo
        // depois dele. Se viesse primeiro, as mensagens de canal seguintes cairiam no
        // intervalo em que o módulo ainda reinicializa — e se perderiam, junto com os
        // Program Changes da música que começa em seguida.
        MidiMessage last = output.message(output.size() - 1);
        assertArrayEquals(
                new byte[] {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7},
                last.getMessage(),
                "última mensagem deveria ser GM System On");
    }

    @Test
    void resetInstrumentsIsLeanToNotFloodTheDeviceInput() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        receiver.resetInstruments(SynthResetMode.GENERAL_MIDI);

        // O reset é uma única mensagem, o GM System On. A rajada de mensagens por canal
        // sobrecarregava o buffer de entrada de módulos de hardware, que descartavam a
        // configuração da música — deixando canais mudos ou com o instrumento errado.
        // O GM System On sozinho já devolve programas, controladores e o canal 10 de
        // percussão ao padrão.
        assertEquals(1, output.size(), "o reset deveria enviar só o GM System On");
        assertArrayEquals(
                new byte[] {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7},
                output.message(0).getMessage());
    }

    @Test
    void resetInstrumentsForgetsNotesLeftSoundingByThePreviousSong()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setTranspose(5);
        receiver.send(message(ShortMessage.NOTE_ON, 0, 60, 100), -1);

        receiver.resetInstruments(SynthResetMode.GENERAL_MIDI);
        receiver.setTranspose(0);
        output.clear();

        // O Note Off que chegar depois não pode reviver o mapeamento antigo: a nota
        // já foi encerrada pelo reset.
        receiver.send(message(ShortMessage.NOTE_OFF, 0, 60, 0), -1);
        assertEquals(60, output.shortMessage(0).getData1());
    }

    @Test
    void blocksForeignVendorSysexByDefault() throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        // Bytes reais de um arquivo do repertório, convertido para teclado Yamaha: XG Reset
        // e um parâmetro de efeito XG. Num módulo Roland eles emudeciam a reprodução por
        // mais de um minuto — e às vezes por completo. É o padrão que evita esse caso.
        receiver.send(sysex(0xF0, 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, 0xF7), -1);
        receiver.send(sysex(0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41, 0xF7), -1);

        assertEquals(0, output.size(), "SysEx de fabricante é barrado por padrão");
    }

    @Test
    void forwardsVendorSysexWhenTheUserAllowsIt() throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setBlockVendorSysex(false);

        // Quem tem o aparelho da marca do arquivo pode querer os ajustes dele de volta.
        receiver.send(sysex(0xF0, 0x43, 0x10, 0x4C, 0x00, 0x00, 0x7E, 0x00, 0xF7), -1);

        assertEquals(1, output.size(), "com o bloqueio desligado, deveria passar");
    }

    @Test
    void neverBlocksUniversalSysexBecauseEveryDeviceUnderstandsIt()
            throws InvalidMidiDataException {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setBlockVendorSysex(true);

        // GM System On usa o ID universal 7E: ele passa mesmo com o bloqueio ligado.
        receiver.send(sysex(0xF0, 0x7E, 0x7F, 0x09, 0x01, 0xF7), -1);

        assertEquals(1, output.size(), "GM System On deveria passar sempre");
    }

    @Test
    void resetModeNoneSendsNothingLeavingInitializationToTheFile() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        receiver.resetInstruments(SynthResetMode.NONE);

        assertEquals(0, output.size(), "o modo Nenhum não deveria enviar reset");
    }

    @Test
    void ourOwnResetIsNeverBlockedByTheUserSysexFilter() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);
        receiver.setBlockVendorSysex(true);

        // O filtro existe para o SysEx que vem do ARQUIVO. O reset escolhido em
        // Configurações precisa chegar ao aparelho mesmo com o bloqueio ligado — do
        // contrário, marcar a caixa desligaria silenciosamente o modo GS ou XG.
        receiver.resetInstruments(SynthResetMode.ROLAND_GS);

        assertEquals(1, output.size(), "o reset do player não passa pelo filtro");
    }

    @Test
    void resetModeRolandGsSendsTheVendorSpecificReset() {
        RecordingReceiver output = new RecordingReceiver();
        MidiTransformReceiver receiver = new MidiTransformReceiver(output);

        receiver.resetInstruments(SynthResetMode.ROLAND_GS);

        assertArrayEquals(
                new byte[] {(byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F,
                        0x00, 0x41, (byte) 0xF7},
                output.message(0).getMessage());
    }

    private static SysexMessage sysex(int... bytes) throws InvalidMidiDataException {
        byte[] data = new byte[bytes.length];
        for (int index = 0; index < bytes.length; index++) {
            data[index] = (byte) bytes[index];
        }
        SysexMessage message = new SysexMessage();
        message.setMessage(data, data.length);
        return message;
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
