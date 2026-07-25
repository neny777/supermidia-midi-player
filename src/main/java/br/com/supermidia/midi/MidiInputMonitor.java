package br.com.supermidia.midi;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class MidiInputMonitor implements AutoCloseable {
    private final Consumer<MidiControlMessage> messageHandler;
    private final Receiver monitorReceiver = new MonitorReceiver();
    private MidiDevice inputDevice;
    private Transmitter transmitter;

    public MidiInputMonitor(Consumer<MidiControlMessage> messageHandler) {
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
    }

    public List<MidiInputDevice> listInputDevices() {
        List<MidiInputDevice> inputs = new ArrayList<>();
        for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
            try {
                MidiDevice device = MidiSystem.getMidiDevice(info);
                if (!(device instanceof Sequencer) && device.getMaxTransmitters() != 0) {
                    inputs.add(new MidiInputDevice(info));
                }
            } catch (MidiUnavailableException ignored) {
                // Uma porta temporariamente indisponível não deve impedir a listagem das demais.
            }
        }
        inputs.sort(Comparator.comparing(MidiInputDevice::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(inputs);
    }

    public synchronized void selectInput(MidiInputDevice selection)
            throws MidiUnavailableException {
        disconnectInput();
        if (selection == null || selection.isNone()) {
            return;
        }

        MidiDevice selectedDevice = MidiSystem.getMidiDevice(selection.info());
        selectedDevice.open();
        try {
            Transmitter selectedTransmitter = selectedDevice.getTransmitter();
            selectedTransmitter.setReceiver(monitorReceiver);
            inputDevice = selectedDevice;
            transmitter = selectedTransmitter;
        } catch (MidiUnavailableException | RuntimeException exception) {
            selectedDevice.close();
            throw exception;
        }
    }

    public synchronized boolean hasInput() {
        return inputDevice != null && inputDevice.isOpen();
    }

    @Override
    public synchronized void close() {
        disconnectInput();
        monitorReceiver.close();
    }

    private void disconnectInput() {
        if (transmitter != null) {
            transmitter.close();
            transmitter = null;
        }
        if (inputDevice != null) {
            inputDevice.close();
            inputDevice = null;
        }
    }

    private final class MonitorReceiver implements Receiver {
        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (message instanceof ShortMessage shortMessage) {
                messageHandler.accept(MidiControlMessage.from(shortMessage));
            }
        }

        @Override
        public void close() {
            // O ciclo de vida é controlado pelo MidiInputMonitor.
        }
    }
}
