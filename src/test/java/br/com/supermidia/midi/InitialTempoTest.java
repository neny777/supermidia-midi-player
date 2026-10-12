package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * O andamento inicial é aplicado à mão porque o sequenciador do Java esquece de lê-lo
 * depois de um {@code setSequence(null)}. Estes testes cobrem a leitura do arquivo.
 */
class InitialTempoTest {
    private static final int SET_TEMPO = 0x51;

    private static MidiEvent tempoEvent(long tick, int bpm) throws InvalidMidiDataException {
        int mpq = (int) (60_000_000L / bpm);
        byte[] data = {
                (byte) ((mpq >> 16) & 0xFF),
                (byte) ((mpq >> 8) & 0xFF),
                (byte) (mpq & 0xFF)};
        return new MidiEvent(new MetaMessage(SET_TEMPO, data, data.length), tick);
    }

    @Test
    void readsTheTempoDeclaredAtTheStart() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        track.add(tempoEvent(0, 98));

        assertEquals(60_000_000f / 98, MidiPlaybackEngine.initialTempoMpq(sequence), 10f);
    }

    @Test
    void ignoresTheTempoChangesThatComeLater() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track track = sequence.createTrack();
        track.add(tempoEvent(0, 98));
        track.add(tempoEvent(5_000, 60));
        track.add(tempoEvent(9_000, 140));

        assertEquals(60_000_000f / 98, MidiPlaybackEngine.initialTempoMpq(sequence), 10f);
    }

    @Test
    void takesTheEarliestEventWhenTracksDisagree() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track atrasada = sequence.createTrack();
        atrasada.add(tempoEvent(240, 60));
        Track inicial = sequence.createTrack();
        inicial.add(tempoEvent(0, 98));

        assertEquals(60_000_000f / 98, MidiPlaybackEngine.initialTempoMpq(sequence), 10f);
    }

    @Test
    void fallsBackToTheMidiDefaultWhenTheFileDeclaresNoTempo() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        sequence.createTrack();

        assertEquals(500_000f, MidiPlaybackEngine.initialTempoMpq(sequence), 0.1f);
    }
}
