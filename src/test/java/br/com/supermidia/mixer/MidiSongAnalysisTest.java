package br.com.supermidia.mixer;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiSongAnalysisTest {
    @Test
    void identifiesTrackInstrumentVolumeAndPercussion()
            throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track pianoTrack = sequence.createTrack();
        addTrackName(pianoTrack, "Piano Base");
        addShort(pianoTrack, 0, ShortMessage.PROGRAM_CHANGE, 0, 0, 0);
        addShort(pianoTrack, 0, ShortMessage.CONTROL_CHANGE, 0, 7, 90);
        addShort(pianoTrack, 24, ShortMessage.NOTE_ON, 0, 60, 100);

        Track drumTrack = sequence.createTrack();
        addTrackName(drumTrack, "Bateria");
        addShort(drumTrack, 24, ShortMessage.NOTE_ON, 9, 36, 100);

        MidiSongAnalysis analysis = MidiSongAnalysis.fromSequence(sequence);

        MidiChannelInfo piano = analysis.channel(0);
        assertTrue(piano.used());
        assertEquals("Piano Base", piano.trackName());
        assertEquals("Acoustic Grand Piano", piano.instrumentName());
        assertEquals(90, piano.initialVolume());

        MidiChannelInfo drums = analysis.channel(9);
        assertTrue(drums.percussion());
        assertEquals("Bateria / Percussão", drums.instrumentName());
        assertFalse(analysis.channel(15).used());
        assertEquals(16, analysis.channels().size());
        assertEquals("Gunshot", GeneralMidiInstruments.nameOf(127));
    }

    private static void addTrackName(Track track, String name)
            throws InvalidMidiDataException {
        MetaMessage message = new MetaMessage();
        byte[] data = name.getBytes(StandardCharsets.UTF_8);
        message.setMessage(3, data, data.length);
        track.add(new MidiEvent(message, 0));
    }

    private static void addShort(
            Track track, long tick, int command, int channel, int data1, int data2)
            throws InvalidMidiDataException {
        ShortMessage message = new ShortMessage();
        message.setMessage(command, channel, data1, data2);
        track.add(new MidiEvent(message, tick));
    }
}
