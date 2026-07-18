package br.com.supermidia.lyrics;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiLyricsTest {
    @Test
    void buildsLinesFromLyricMarkersAndFindsTheCurrentLine()
            throws InvalidMidiDataException {
        Sequence sequence = sequence();
        Track track = sequence.createTrack();
        addMeta(track, 5, 0, "/Hello ", StandardCharsets.UTF_8);
        addMeta(track, 5, 24, "world", StandardCharsets.UTF_8);
        addMeta(track, 5, 96, "/Next line", StandardCharsets.UTF_8);

        MidiLyrics lyrics = MidiLyrics.fromSequence(sequence);

        assertEquals(2, lyrics.size());
        assertEquals("Hello world", lyrics.lineAt(0).orElseThrow().text());
        assertEquals("Next line", lyrics.lineAt(1).orElseThrow().text());
        assertEquals(-1, lyrics.currentIndex(-1));
        assertEquals(0, lyrics.currentIndex(50));
        assertEquals(1, lyrics.currentIndex(120));
    }

    @Test
    void readsKarTextEventsAndIgnoresMetadataHeaders()
            throws InvalidMidiDataException {
        Sequence sequence = sequence();
        Track track = sequence.createTrack();
        addMeta(track, 1, 0, "@TTitle", StandardCharsets.UTF_8);
        addMeta(track, 1, 24, "/First line", StandardCharsets.UTF_8);
        addMeta(track, 1, 96, "/Second line", StandardCharsets.UTF_8);

        MidiLyrics lyrics = MidiLyrics.fromSequence(sequence);

        assertEquals(2, lyrics.size());
        assertEquals("First line", lyrics.lineAt(0).orElseThrow().text());
        assertEquals("Second line", lyrics.lineAt(1).orElseThrow().text());
    }

    @Test
    void decodesWindows1252AccentsWhenTheDataIsNotUtf8()
            throws InvalidMidiDataException {
        Sequence sequence = sequence();
        Track track = sequence.createTrack();
        Charset windows1252 = Charset.forName("windows-1252");
        addMeta(track, 5, 0, "/Canção", windows1252);

        MidiLyrics lyrics = MidiLyrics.fromSequence(sequence);

        assertEquals("Canção", lyrics.lineAt(0).orElseThrow().text());
    }

    @Test
    void prefersDedicatedLyricEventsOverGenericTextEvents()
            throws InvalidMidiDataException {
        Sequence sequence = sequence();
        Track textTrack = sequence.createTrack();
        addMeta(textTrack, 1, 0, "/Generic text", StandardCharsets.UTF_8);
        Track lyricTrack = sequence.createTrack();
        addMeta(lyricTrack, 5, 0, "/Real lyric", StandardCharsets.UTF_8);

        MidiLyrics lyrics = MidiLyrics.fromSequence(sequence);

        assertEquals(1, lyrics.size());
        assertEquals("Real lyric", lyrics.lineAt(0).orElseThrow().text());
    }

    @Test
    void returnsEmptyWhenTheMidiHasNoLyrics() throws InvalidMidiDataException {
        assertTrue(MidiLyrics.fromSequence(sequence()).isEmpty());
    }

    @Test
    void fallsBackToKarTextWhenDedicatedLyricEventsContainOnlyMetadata()
            throws InvalidMidiDataException {
        Sequence sequence = sequence();
        Track lyricTrack = sequence.createTrack();
        addMeta(lyricTrack, 5, 0, "@TMetadata", StandardCharsets.UTF_8);
        Track textTrack = sequence.createTrack();
        addMeta(textTrack, 1, 24, "/Usable line", StandardCharsets.UTF_8);

        MidiLyrics lyrics = MidiLyrics.fromSequence(sequence);

        assertEquals("Usable line", lyrics.lineAt(0).orElseThrow().text());
    }

    private static Sequence sequence() throws InvalidMidiDataException {
        return new Sequence(Sequence.PPQ, 480);
    }

    private static void addMeta(
            Track track, int type, long tick, String text, Charset charset)
            throws InvalidMidiDataException {
        MetaMessage message = new MetaMessage();
        byte[] data = text.getBytes(charset);
        message.setMessage(type, data, data.length);
        track.add(new MidiEvent(message, tick));
    }
}
