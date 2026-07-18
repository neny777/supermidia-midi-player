package br.com.supermidia.mixer;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MidiSongAnalysis {
    private static final int TRACK_NAME = 0x03;
    private static final int CHANNEL_VOLUME = 7;
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private final List<MidiChannelInfo> channels;

    private MidiSongAnalysis(List<MidiChannelInfo> channels) {
        this.channels = List.copyOf(channels);
    }

    public static MidiSongAnalysis empty() {
        List<MidiChannelInfo> channels = new ArrayList<>();
        for (int channel = 0; channel < 16; channel++) {
            channels.add(channelInfo(channel, "Canal " + (channel + 1), 0, 100, false));
        }
        return new MidiSongAnalysis(channels);
    }

    public static MidiSongAnalysis fromFile(File file)
            throws IOException, InvalidMidiDataException {
        Objects.requireNonNull(file, "file");
        return fromSequence(MidiSystem.getSequence(file));
    }

    public static MidiSongAnalysis fromSequence(Sequence sequence) {
        Objects.requireNonNull(sequence, "sequence");
        ChannelBuilder[] builders = new ChannelBuilder[16];
        for (int channel = 0; channel < builders.length; channel++) {
            builders[channel] = new ChannelBuilder();
        }

        for (Track track : sequence.getTracks()) {
            String trackName = findTrackName(track);
            Set<Integer> trackChannels = new LinkedHashSet<>();

            for (int eventIndex = 0; eventIndex < track.size(); eventIndex++) {
                MidiEvent event = track.get(eventIndex);
                if (!(event.getMessage() instanceof ShortMessage message)) {
                    continue;
                }
                int channel = message.getChannel();
                ChannelBuilder builder = builders[channel];
                builder.used = true;
                trackChannels.add(channel);

                if (message.getCommand() == ShortMessage.PROGRAM_CHANGE
                        && event.getTick() < builder.programTick) {
                    builder.program = message.getData1();
                    builder.programTick = event.getTick();
                } else if (message.getCommand() == ShortMessage.CONTROL_CHANGE
                        && message.getData1() == CHANNEL_VOLUME
                        && event.getTick() < builder.volumeTick) {
                    builder.volume = message.getData2();
                    builder.volumeTick = event.getTick();
                }
            }

            if (!trackName.isBlank()) {
                for (int channel : trackChannels) {
                    builders[channel].trackNames.add(trackName);
                }
            }
        }

        List<MidiChannelInfo> result = new ArrayList<>();
        for (int channel = 0; channel < builders.length; channel++) {
            ChannelBuilder builder = builders[channel];
            String trackName = builder.trackNames.isEmpty()
                    ? "Canal " + (channel + 1)
                    : String.join(" / ", builder.trackNames);
            result.add(channelInfo(
                    channel, trackName, builder.program, builder.volume, builder.used));
        }
        return new MidiSongAnalysis(result);
    }

    public MidiChannelInfo channel(int channel) {
        if (channel < 0 || channel >= channels.size()) {
            throw new IndexOutOfBoundsException("Canal MIDI fora do intervalo: " + channel);
        }
        return channels.get(channel);
    }

    public List<MidiChannelInfo> channels() {
        return channels;
    }

    public int[] initialVolumes() {
        int[] volumes = new int[channels.size()];
        for (int channel = 0; channel < channels.size(); channel++) {
            volumes[channel] = channels.get(channel).initialVolume();
        }
        return volumes;
    }

    private static MidiChannelInfo channelInfo(
            int channel, String trackName, int program, int volume, boolean used) {
        String instrument = channel == 9
                ? "Bateria / Percussão"
                : GeneralMidiInstruments.nameOf(program);
        return new MidiChannelInfo(channel, trackName, instrument, program, volume, used);
    }

    private static String findTrackName(Track track) {
        for (int index = 0; index < track.size(); index++) {
            MidiMessage message = track.get(index).getMessage();
            if (message instanceof MetaMessage meta && meta.getType() == TRACK_NAME) {
                return decodeText(meta.getData()).trim();
            }
        }
        return "";
    }

    private static String decodeText(byte[] data) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException exception) {
            return new String(data, WINDOWS_1252);
        }
    }

    private static final class ChannelBuilder {
        private final Set<String> trackNames = new LinkedHashSet<>();
        private int program;
        private int volume = 100;
        private long programTick = Long.MAX_VALUE;
        private long volumeTick = Long.MAX_VALUE;
        private boolean used;
    }
}
