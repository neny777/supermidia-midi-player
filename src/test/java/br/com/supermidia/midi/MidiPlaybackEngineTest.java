package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MidiPlaybackEngineTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAndUnloadsASequenceWithoutAnOutputDevice()
            throws InvalidMidiDataException, IOException, MidiUnavailableException {
        Path midiFile = temporaryDirectory.resolve("teste.mid");
        Sequence sequence = new Sequence(Sequence.PPQ, 24);
        Track track = sequence.createTrack();
        MetaMessage endOfTrack = new MetaMessage();
        endOfTrack.setMessage(47, new byte[0], 0);
        track.add(new javax.sound.midi.MidiEvent(endOfTrack, 24));
        MidiSystem.write(sequence, 1, midiFile.toFile());

        try (MidiPlaybackEngine engine = new MidiPlaybackEngine()) {
            engine.load(midiFile.toFile());
            assertTrue(engine.hasSequence());

            engine.unload();
            assertFalse(engine.hasSequence());
        }
    }
}
