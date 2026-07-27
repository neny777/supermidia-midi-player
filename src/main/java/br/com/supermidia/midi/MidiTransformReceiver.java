package br.com.supermidia.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import java.util.Arrays;
import java.util.Objects;

public final class MidiTransformReceiver implements Receiver {
    private static final int MIDI_CHANNELS = 16;
    private static final int MIDI_NOTES = 128;
    private static final int PERCUSSION_CHANNEL = 9;
    private static final int BANK_SELECT_MSB = 0;
    private static final int CHANNEL_VOLUME = 7;
    private static final int BANK_SELECT_LSB = 32;
    private static final int ALL_SOUND_OFF = 120;
    private static final int RESET_ALL_CONTROLLERS = 121;
    private static final int ALL_NOTES_OFF = 123;

    /**
     * "GM System On": devolve o dispositivo ao padrão General MIDI — programas em 0,
     * controladores zerados e, sobretudo, o canal 10 de volta a percussão.
     */
    private static final byte[] GM_SYSTEM_ON =
            {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7};

    private final Receiver delegate;
    private final int[][] activeOutputNotes = new int[MIDI_CHANNELS][MIDI_NOTES];
    private final int[] sourceVolumes = new int[MIDI_CHANNELS];
    private final int[] volumeOverrides = new int[MIDI_CHANNELS];
    private final boolean[] mutedChannels = new boolean[MIDI_CHANNELS];
    private final boolean[] soloChannels = new boolean[MIDI_CHANNELS];
    private final long[] lastActivityNanos = new long[MIDI_CHANNELS];

    private int transpose;
    private double masterVolume = 1.0;
    private boolean closed;

    public MidiTransformReceiver(Receiver delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        for (int[] channel : activeOutputNotes) {
            Arrays.fill(channel, -1);
        }
        Arrays.fill(sourceVolumes, 100);
        Arrays.fill(volumeOverrides, -1);
    }

    @Override
    public synchronized void send(MidiMessage message, long timeStamp) {
        if (closed) {
            return;
        }
        if (!(message instanceof ShortMessage shortMessage)) {
            delegate.send(message, timeStamp);
            return;
        }

        int command = shortMessage.getCommand();
        int channel = shortMessage.getChannel();
        int data1 = shortMessage.getData1();
        int data2 = shortMessage.getData2();

        if (command == ShortMessage.NOTE_ON && data2 > 0) {
            lastActivityNanos[channel] = System.nanoTime();
            if (!isEffectivelyMuted(channel)) {
                sendNoteOn(channel, data1, data2, timeStamp);
            }
            return;
        }
        if (command == ShortMessage.NOTE_OFF
                || (command == ShortMessage.NOTE_ON && data2 == 0)) {
            sendNoteOff(command, channel, data1, data2, timeStamp);
            return;
        }
        if (command == ShortMessage.CONTROL_CHANGE && data1 == CHANNEL_VOLUME) {
            sourceVolumes[channel] = data2;
            sendChannelVolume(channel, timeStamp);
            return;
        }

        delegate.send(message, timeStamp);
    }

    public synchronized void setTranspose(int semitones) {
        transpose = Math.max(-24, Math.min(24, semitones));
    }

    public synchronized int getTranspose() {
        return transpose;
    }

    public synchronized void setMasterVolume(double factor) {
        masterVolume = Math.max(0.0, Math.min(1.0, factor));
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendChannelVolume(channel, -1);
        }
    }

    public synchronized void setChannelVolume(int channel, int volume) {
        requireChannel(channel);
        volumeOverrides[channel] = Math.max(0, Math.min(127, volume));
        sendChannelVolume(channel, -1);
    }

    public synchronized void setChannelMuted(int channel, boolean muted) {
        requireChannel(channel);
        boolean[] previousState = effectiveMuteState();
        mutedChannels[channel] = muted;
        silenceNewlyMutedChannels(previousState);
    }

    public synchronized void setChannelSolo(int channel, boolean solo) {
        requireChannel(channel);
        boolean[] previousState = effectiveMuteState();
        soloChannels[channel] = solo;
        silenceNewlyMutedChannels(previousState);
    }

    public synchronized boolean isChannelActive(int channel, long activityWindowMillis) {
        requireChannel(channel);
        long lastActivity = lastActivityNanos[channel];
        return lastActivity > 0
                && System.nanoTime() - lastActivity <= activityWindowMillis * 1_000_000L;
    }

    public synchronized void resetMixer(int[] originalVolumes) {
        if (originalVolumes.length != MIDI_CHANNELS) {
            throw new IllegalArgumentException("A mixagem deve conter 16 canais");
        }
        silence();
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sourceVolumes[channel] = Math.max(0, Math.min(127, originalVolumes[channel]));
        }
        Arrays.fill(volumeOverrides, -1);
        Arrays.fill(mutedChannels, false);
        Arrays.fill(soloChannels, false);
        Arrays.fill(lastActivityNanos, 0);
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendChannelVolume(channel, -1);
        }
    }

    public synchronized void silence() {
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            silenceChannel(channel);
        }
    }

    /**
     * Devolve o sintetizador ao estado padrão antes de carregar outra música.
     *
     * <p>Sem isso o dispositivo guarda o que a música anterior deixou: instrumentos,
     * bancos, pitch bend, sustain. O sintoma mais visível é a bateria da música
     * seguinte tocando com o som melódico que sobrou no canal de percussão, porque
     * a maioria dos arquivos só envia Program Change para os canais que usa e conta
     * com o dispositivo nos valores de fábrica.</p>
     *
     * <p>Manda o SysEx padrão e, em seguida, repete a limpeza canal a canal em
     * mensagens comuns — alguns dispositivos ignoram SysEx, e o custo de repetir
     * entre duas músicas é irrelevante.</p>
     */
    public synchronized void resetInstruments() {
        if (closed) {
            return;
        }
        try {
            delegate.send(new SysexMessage(GM_SYSTEM_ON, GM_SYSTEM_ON.length), -1);
        } catch (InvalidMidiDataException exception) {
            throw new IllegalStateException("SysEx de GM System On inválido", exception);
        }
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_SOUND_OFF, 0, -1);
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_NOTES_OFF, 0, -1);
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, RESET_ALL_CONTROLLERS, 0, -1);
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, BANK_SELECT_MSB, 0, -1);
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, BANK_SELECT_LSB, 0, -1);
            sendShortMessage(ShortMessage.PROGRAM_CHANGE, channel, 0, 0, -1);
            Arrays.fill(activeOutputNotes[channel], -1);
        }
    }

    public synchronized void panic() {
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_SOUND_OFF, 0, -1);
            sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_NOTES_OFF, 0, -1);
            Arrays.fill(activeOutputNotes[channel], -1);
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            panic();
            closed = true;
            delegate.close();
        }
    }

    private void sendNoteOn(int channel, int inputNote, int velocity, long timeStamp) {
        int outputNote = transposedNote(channel, inputNote);
        activeOutputNotes[channel][inputNote] = outputNote;
        sendShortMessage(ShortMessage.NOTE_ON, channel, outputNote, velocity, timeStamp);
    }

    private void sendNoteOff(int command, int channel, int inputNote, int velocity, long timeStamp) {
        int mappedNote = activeOutputNotes[channel][inputNote];
        if (mappedNote < 0 && isEffectivelyMuted(channel)) {
            return;
        }
        int outputNote = mappedNote >= 0 ? mappedNote : transposedNote(channel, inputNote);
        activeOutputNotes[channel][inputNote] = -1;
        sendShortMessage(command, channel, outputNote, velocity, timeStamp);
    }

    private void sendChannelVolume(int channel, long timeStamp) {
        int sourceVolume = volumeOverrides[channel] >= 0
                ? volumeOverrides[channel] : sourceVolumes[channel];
        int scaledVolume = (int) Math.round(sourceVolume * masterVolume);
        sendShortMessage(ShortMessage.CONTROL_CHANGE, channel,
                CHANNEL_VOLUME, Math.max(0, Math.min(127, scaledVolume)), timeStamp);
    }

    private int transposedNote(int channel, int inputNote) {
        if (channel == PERCUSSION_CHANNEL) {
            return inputNote;
        }
        return Math.max(0, Math.min(127, inputNote + transpose));
    }

    private boolean isEffectivelyMuted(int channel) {
        if (mutedChannels[channel]) {
            return true;
        }
        boolean anySolo = false;
        for (boolean solo : soloChannels) {
            anySolo |= solo;
        }
        return anySolo && !soloChannels[channel];
    }

    private boolean[] effectiveMuteState() {
        boolean[] state = new boolean[MIDI_CHANNELS];
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            state[channel] = isEffectivelyMuted(channel);
        }
        return state;
    }

    private void silenceNewlyMutedChannels(boolean[] previousState) {
        for (int channel = 0; channel < MIDI_CHANNELS; channel++) {
            if (!previousState[channel] && isEffectivelyMuted(channel)) {
                silenceChannel(channel);
            }
        }
    }

    private void silenceChannel(int channel) {
        sendShortMessage(ShortMessage.CONTROL_CHANGE, channel, ALL_NOTES_OFF, 0, -1);
        Arrays.fill(activeOutputNotes[channel], -1);
    }

    private void requireChannel(int channel) {
        if (channel < 0 || channel >= MIDI_CHANNELS) {
            throw new IllegalArgumentException("Canal MIDI fora do intervalo: " + channel);
        }
    }

    private void sendShortMessage(int command, int channel, int data1, int data2, long timeStamp) {
        try {
            ShortMessage transformed = new ShortMessage();
            transformed.setMessage(command, channel, data1, data2);
            delegate.send(transformed, timeStamp);
        } catch (InvalidMidiDataException exception) {
            throw new IllegalStateException("Falha ao transformar mensagem MIDI", exception);
        }
    }
}
