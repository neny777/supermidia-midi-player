package supermidia.midi.player;

import javax.sound.midi.*;
import java.io.File;
import java.util.Objects;

public class MidiPlayer implements AutoCloseable {
    public static final int CHANNEL_COUNT = 16;

    private final Sequencer sequencer;
    private final Synthesizer synthesizer;
    private final Transmitter sequencerTransmitter;
    private final Receiver synthReceiver;
    private final ChannelFilterReceiver channelFilter;

    public MidiPlayer() throws MidiUnavailableException {
        sequencer = MidiSystem.getSequencer(false);
        synthesizer = MidiSystem.getSynthesizer();

        sequencer.open();
        synthesizer.open();

        synthReceiver = synthesizer.getReceiver();
        channelFilter = new ChannelFilterReceiver(synthReceiver);
        sequencerTransmitter = sequencer.getTransmitter();
        sequencerTransmitter.setReceiver(channelFilter);
    }

    // Load any Standard MIDI File (.mid) from disk.
    public void load(File file) throws Exception {
        Objects.requireNonNull(file, "file");
        if (!file.isFile()) {
            throw new IllegalArgumentException("File not found: " + file.getAbsolutePath());
        }

        sequencer.stop();
        channelFilter.allNotesOff();
        channelFilter.resetActiveChannels();

        Sequence seq = MidiSystem.getSequence(file);
        sequencer.setSequence(seq);
        sequencer.setTickPosition(0);
    }

    public void play() {
        if (!hasSequence()) {
            return;
        }

        if (isAtEnd()) {
            channelFilter.allNotesOff();
            channelFilter.resetActiveChannels();
            sequencer.setTickPosition(0);
        }
        sequencer.start();
    }

    // In the MIDI API, stop() just pauses and keeps the position.
    public void pause() {
        sequencer.stop();
        channelFilter.allNotesOff();
        channelFilter.resetActiveChannels();
    }

    // A full stop is: pause, then rewind to the beginning.
    public void stop() {
        sequencer.stop();
        channelFilter.allNotesOff();
        channelFilter.resetActiveChannels();
        sequencer.setTickPosition(0);
    }

    public boolean hasSequence() {
        return sequencer.getSequence() != null;
    }

    public boolean isPlaying() {
        return sequencer.isRunning();
    }

    public double getDurationSeconds() {
        long length = sequencer.getMicrosecondLength();
        return length > 0 ? length / 1_000_000.0 : 0.0;
    }

    public double getPositionSeconds() {
        long position = sequencer.getMicrosecondPosition();
        return Math.max(0, position) / 1_000_000.0;
    }

    public long getTickPosition() {
        return Math.max(0, sequencer.getTickPosition());
    }

    public long getTickLength() {
        return Math.max(0, sequencer.getTickLength());
    }

    public void setPositionSeconds(double seconds) {
        if (!hasSequence()) {
            return;
        }

        long length = sequencer.getMicrosecondLength();
        if (length <= 0) {
            return;
        }

        channelFilter.allNotesOff();
        channelFilter.resetActiveChannels();

        long target = Math.round(seconds * 1_000_000.0);
        target = Math.max(0, Math.min(target, length));
        sequencer.setMicrosecondPosition(target);
    }

    public boolean isChannelMuted(int channel) {
        return channelFilter.isMuted(channel);
    }

    public void setChannelMuted(int channel, boolean muted) {
        channelFilter.setMuted(channel, muted);
    }

    public boolean isChannelSolo(int channel) {
        return channelFilter.isSolo(channel);
    }

    public void setChannelSolo(int channel, boolean solo) {
        channelFilter.setSolo(channel, solo);
    }

    public boolean[] getActiveChannels() {
        return channelFilter.getActiveChannels();
    }

    public void clearChannelControls() {
        channelFilter.clearControls();
    }

    @Override
    public void close() {
        sequencer.stop();
        channelFilter.allNotesOff();
        sequencerTransmitter.close();
        sequencer.close();
        synthReceiver.close();
        synthesizer.close();
    }

    private boolean isAtEnd() {
        double duration = getDurationSeconds();
        return duration > 0 && getPositionSeconds() >= duration - 0.05;
    }

    private static void validateChannel(int channel) {
        if (channel < 0 || channel >= CHANNEL_COUNT) {
            throw new IllegalArgumentException("MIDI channel must be 0-15: " + channel);
        }
    }

    private static final class ChannelFilterReceiver implements Receiver {
        private final Receiver downstream;
        private final boolean[] muted = new boolean[CHANNEL_COUNT];
        private final boolean[] solo = new boolean[CHANNEL_COUNT];
        private final int[] activeNotes = new int[CHANNEL_COUNT];
        private boolean closed = false;

        private ChannelFilterReceiver(Receiver downstream) {
            this.downstream = downstream;
        }

        @Override
        public synchronized void send(MidiMessage message, long timeStamp) {
            if (closed) {
                return;
            }

            if (message instanceof ShortMessage shortMessage) {
                int channel = shortMessage.getChannel();
                if (shortMessage.getStatus() < 0xF0 && channel >= 0 && channel < CHANNEL_COUNT) {
                    updateActivity(shortMessage, channel);
                    if (!isAudible(channel)) {
                        return;
                    }
                }
            }

            downstream.send(message, timeStamp);
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                allNotesOff();
                closed = true;
                downstream.close();
            }
        }

        private synchronized boolean isMuted(int channel) {
            validateChannel(channel);
            return muted[channel];
        }

        private synchronized void setMuted(int channel, boolean value) {
            validateChannel(channel);
            muted[channel] = value;
            activeNotes[channel] = 0;
            sendAllNotesOff(channel);
        }

        private synchronized boolean isSolo(int channel) {
            validateChannel(channel);
            return solo[channel];
        }

        private synchronized void setSolo(int channel, boolean value) {
            validateChannel(channel);
            solo[channel] = value;
            resetActiveChannels();
            allNotesOff();
        }

        private synchronized boolean[] getActiveChannels() {
            boolean[] active = new boolean[CHANNEL_COUNT];
            for (int i = 0; i < CHANNEL_COUNT; i++) {
                active[i] = activeNotes[i] > 0;
            }
            return active;
        }

        private synchronized void clearControls() {
            for (int i = 0; i < CHANNEL_COUNT; i++) {
                muted[i] = false;
                solo[i] = false;
                activeNotes[i] = 0;
            }
            allNotesOff();
        }

        private synchronized void resetActiveChannels() {
            for (int i = 0; i < CHANNEL_COUNT; i++) {
                activeNotes[i] = 0;
            }
        }

        private synchronized void allNotesOff() {
            for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
                sendAllNotesOff(channel);
            }
        }

        private void updateActivity(ShortMessage message, int channel) {
            int command = message.getCommand();
            int data1 = message.getData1();
            int data2 = message.getData2();

            if (command == ShortMessage.NOTE_ON && data2 > 0) {
                activeNotes[channel]++;
            } else if (command == ShortMessage.NOTE_OFF || command == ShortMessage.NOTE_ON) {
                if (activeNotes[channel] > 0) {
                    activeNotes[channel]--;
                }
            } else if (command == ShortMessage.CONTROL_CHANGE && (data1 == 120 || data1 == 123)) {
                activeNotes[channel] = 0;
            }
        }

        private boolean isAudible(int channel) {
            if (muted[channel]) {
                return false;
            }

            boolean anySolo = false;
            for (boolean value : solo) {
                anySolo |= value;
            }
            return !anySolo || solo[channel];
        }

        private void sendAllNotesOff(int channel) {
            try {
                ShortMessage sustainOff = new ShortMessage();
                sustainOff.setMessage(ShortMessage.CONTROL_CHANGE, channel, 64, 0);
                downstream.send(sustainOff, -1);

                ShortMessage notesOff = new ShortMessage();
                notesOff.setMessage(ShortMessage.CONTROL_CHANGE, channel, 123, 0);
                downstream.send(notesOff, -1);

                ShortMessage soundOff = new ShortMessage();
                soundOff.setMessage(ShortMessage.CONTROL_CHANGE, channel, 120, 0);
                downstream.send(soundOff, -1);
            } catch (InvalidMidiDataException ignored) {
                // Channel numbers are validated by the caller.
            }
        }
    }

    // ---- Terminal test harness ----
    public static void main(String[] args) throws Exception {
        String filename = (args.length > 0) ? args[0] : "test.mid";
        File file = new File(filename);
        if (!file.exists()) {
            System.out.println("File not found: " + file.getAbsolutePath());
            return;
        }

        try (MidiPlayer player = new MidiPlayer()) {
            player.load(file);

            System.out.printf("Loaded: %s%n", file.getName());
            System.out.printf("Duration: %.1f seconds%n", player.getDurationSeconds());
            System.out.println("Playing...");
            player.play();

            // Live position readout, a preview of the Swing scrubber.
            while (player.isPlaying()) {
                System.out.printf("\r  %.1f / %.1f s   ",
                        player.getPositionSeconds(), player.getDurationSeconds());
                Thread.sleep(200);
            }
            System.out.println("\nDone.");
        }
    }
}
