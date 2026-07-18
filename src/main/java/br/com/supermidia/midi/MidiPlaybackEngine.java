package br.com.supermidia.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Transmitter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MidiPlaybackEngine implements AutoCloseable {
    private static final int END_OF_TRACK = 47;

    private final Sequencer sequencer;
    private final Transmitter transmitter;
    private final int[] channelSourceVolumes = new int[16];
    private final int[] channelVolumeOverrides = new int[16];
    private final boolean[] mutedChannels = new boolean[16];
    private final boolean[] soloChannels = new boolean[16];

    private MidiDevice outputDevice;
    private MidiTransformReceiver transformReceiver;
    private File loadedFile;
    private float tempoFactor = 1.0f;
    private int transpose;
    private double masterVolume = 0.8;
    private Runnable playbackFinishedHandler = () -> { };
    private long sequenceGeneration;
    private long finishedGeneration = -1;

    public MidiPlaybackEngine() throws MidiUnavailableException {
        sequencer = MidiSystem.getSequencer(false);
        if (sequencer == null) {
            throw new MidiUnavailableException("Nenhum sequenciador MIDI está disponível");
        }
        sequencer.open();
        transmitter = sequencer.getTransmitter();
        sequencer.addMetaEventListener(this::handleMetaMessage);
        Arrays.fill(channelSourceVolumes, 100);
        Arrays.fill(channelVolumeOverrides, -1);
    }

    public List<MidiOutputDevice> listOutputDevices() {
        List<MidiOutputDevice> outputs = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                if (!(device instanceof Sequencer) && device.getMaxReceivers() != 0) {
                    outputs.add(new MidiOutputDevice(info));
                }
            } catch (MidiUnavailableException ignored) {
                // Dispositivos temporariamente indisponíveis não devem impedir a lista.
            }
        }
        outputs.sort(Comparator.comparing(MidiOutputDevice::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(outputs);
    }

    public synchronized void selectOutput(MidiOutputDevice selection)
            throws MidiUnavailableException {
        if (sequencer.isRunning()) {
            pause();
        }
        disconnectOutput();
        if (selection == null) {
            return;
        }

        MidiDevice selectedDevice = MidiSystem.getMidiDevice(selection.info());
        selectedDevice.open();
        try {
            Receiver receiver = selectedDevice.getReceiver();
            MidiTransformReceiver transformed = new MidiTransformReceiver(receiver);
            transformed.setTranspose(transpose);
            transformed.setMasterVolume(masterVolume);
            transformed.resetMixer(channelSourceVolumes);
            for (int channel = 0; channel < 16; channel++) {
                if (channelVolumeOverrides[channel] >= 0) {
                    transformed.setChannelVolume(channel, channelVolumeOverrides[channel]);
                }
                transformed.setChannelMuted(channel, mutedChannels[channel]);
                transformed.setChannelSolo(channel, soloChannels[channel]);
            }
            transmitter.setReceiver(transformed);
            outputDevice = selectedDevice;
            transformReceiver = transformed;
        } catch (MidiUnavailableException | RuntimeException exception) {
            selectedDevice.close();
            throw exception;
        }
    }

    public synchronized void load(File file)
            throws IOException, InvalidMidiDataException {
        Objects.requireNonNull(file, "file");
        Sequence sequence = MidiSystem.getSequence(file);
        stop();
        sequencer.setSequence(sequence);
        sequencer.setTempoFactor(tempoFactor);
        loadedFile = file;
        sequenceGeneration++;
        finishedGeneration = -1;
    }

    public synchronized void unload() {
        stop();
        try {
            sequencer.setSequence((Sequence) null);
        } catch (InvalidMidiDataException exception) {
            throw new IllegalStateException("Falha ao descarregar a sequência MIDI", exception);
        }
        loadedFile = null;
        sequenceGeneration++;
        finishedGeneration = -1;
    }

    public synchronized void play() {
        ensureSequenceLoaded();
        if (transformReceiver == null) {
            throw new IllegalStateException("Selecione uma saída MIDI antes de tocar");
        }
        if (sequencer.getTickPosition() >= sequencer.getTickLength()) {
            sequencer.setTickPosition(0);
        }
        finishedGeneration = -1;
        sequencer.start();
    }

    public synchronized void pause() {
        sequencer.stop();
        if (transformReceiver != null) {
            transformReceiver.silence();
        }
    }

    public synchronized void stop() {
        sequencer.stop();
        sequencer.setMicrosecondPosition(0);
        if (transformReceiver != null) {
            transformReceiver.silence();
        }
    }

    public synchronized void panic() {
        if (transformReceiver != null) {
            transformReceiver.panic();
        }
    }

    public synchronized void setTempoFactor(float factor) {
        tempoFactor = Math.max(0.25f, Math.min(4.0f, factor));
        sequencer.setTempoFactor(tempoFactor);
    }

    public synchronized void setTranspose(int semitones) {
        transpose = Math.max(-24, Math.min(24, semitones));
        if (transformReceiver != null) {
            transformReceiver.setTranspose(transpose);
        }
    }

    public synchronized void setMasterVolume(double factor) {
        masterVolume = Math.max(0.0, Math.min(1.0, factor));
        if (transformReceiver != null) {
            transformReceiver.setMasterVolume(masterVolume);
        }
    }

    public synchronized void setChannelVolume(int channel, int volume) {
        requireChannel(channel);
        channelVolumeOverrides[channel] = Math.max(0, Math.min(127, volume));
        if (transformReceiver != null) {
            transformReceiver.setChannelVolume(channel, channelVolumeOverrides[channel]);
        }
    }

    public synchronized void setChannelMuted(int channel, boolean muted) {
        requireChannel(channel);
        mutedChannels[channel] = muted;
        if (transformReceiver != null) {
            transformReceiver.setChannelMuted(channel, muted);
        }
    }

    public synchronized void setChannelSolo(int channel, boolean solo) {
        requireChannel(channel);
        soloChannels[channel] = solo;
        if (transformReceiver != null) {
            transformReceiver.setChannelSolo(channel, solo);
        }
    }

    public synchronized void resetChannelMix(int[] originalVolumes) {
        if (originalVolumes.length != 16) {
            throw new IllegalArgumentException("A mixagem deve conter 16 canais");
        }
        for (int channel = 0; channel < 16; channel++) {
            channelSourceVolumes[channel] = Math.max(0, Math.min(127, originalVolumes[channel]));
        }
        Arrays.fill(channelVolumeOverrides, -1);
        Arrays.fill(mutedChannels, false);
        Arrays.fill(soloChannels, false);
        if (transformReceiver != null) {
            transformReceiver.resetMixer(channelSourceVolumes);
        }
    }

    public synchronized boolean isChannelActive(int channel) {
        requireChannel(channel);
        return transformReceiver != null && transformReceiver.isChannelActive(channel, 220);
    }

    public synchronized void setPositionMicroseconds(long position) {
        if (sequencer.getSequence() == null) {
            return;
        }
        if (transformReceiver != null) {
            transformReceiver.silence();
        }
        sequencer.setMicrosecondPosition(Math.max(0, Math.min(position, getDurationMicroseconds())));
    }

    public synchronized long getPositionMicroseconds() {
        return sequencer.getSequence() == null ? 0 : sequencer.getMicrosecondPosition();
    }

    public synchronized long getDurationMicroseconds() {
        return sequencer.getSequence() == null ? 0 : sequencer.getMicrosecondLength();
    }

    public synchronized long getTickPosition() {
        return sequencer.getSequence() == null ? 0 : sequencer.getTickPosition();
    }

    public synchronized long getTickLength() {
        return sequencer.getSequence() == null ? 0 : sequencer.getTickLength();
    }

    public synchronized boolean hasSequence() {
        return sequencer.getSequence() != null;
    }

    public synchronized boolean hasOutput() {
        return transformReceiver != null;
    }

    public synchronized boolean isRunning() {
        return sequencer.isRunning();
    }

    public synchronized File getLoadedFile() {
        return loadedFile;
    }

    public synchronized void setPlaybackFinishedHandler(Runnable handler) {
        playbackFinishedHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public synchronized void close() {
        sequencer.stop();
        disconnectOutput();
        transmitter.close();
        sequencer.close();
    }

    private synchronized void disconnectOutput() {
        transmitter.setReceiver(null);
        if (transformReceiver != null) {
            transformReceiver.close();
            transformReceiver = null;
        }
        if (outputDevice != null) {
            outputDevice.close();
            outputDevice = null;
        }
    }

    private synchronized void handleMetaMessage(MetaMessage message) {
        if (message.getType() == END_OF_TRACK
                && sequencer.getSequence() != null
                && sequencer.getTickPosition() >= sequencer.getTickLength() - 1
                && finishedGeneration != sequenceGeneration) {
            finishedGeneration = sequenceGeneration;
            playbackFinishedHandler.run();
        }
    }

    private void ensureSequenceLoaded() {
        if (sequencer.getSequence() == null) {
            throw new IllegalStateException("Nenhum arquivo MIDI foi carregado");
        }
    }

    private void requireChannel(int channel) {
        if (channel < 0 || channel >= 16) {
            throw new IllegalArgumentException("Canal MIDI fora do intervalo: " + channel);
        }
    }
}
