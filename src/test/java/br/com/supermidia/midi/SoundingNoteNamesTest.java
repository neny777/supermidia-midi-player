package br.com.supermidia.midi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundingNoteNamesTest {
    @Test
    void listsPitchClassesFromTheLowestNoteUp() {
        // Dó maior na posição fundamental: C4, E4, G4.
        boolean[] sounding = notes(60, 64, 67);

        assertEquals("C · E · G", SoundingNoteNames.format(sounding));
    }

    @Test
    void startsWithTheBassSoInversionsAreVisible() {
        // As mesmas três alturas, mas com o mi no baixo: é o que distingue C de C/E,
        // e é a informação que decide o que a mão esquerda faz no violão.
        boolean[] sounding = notes(52, 60, 67);

        assertEquals("E · C · G", SoundingNoteNames.format(sounding));
    }

    @Test
    void collapsesOctavesOfTheSamePitch() {
        // Baixo e piano dobrando a fundamental não devem poluir a lista com repetição.
        boolean[] sounding = notes(36, 48, 60, 64);

        assertEquals("C · E", SoundingNoteNames.format(sounding));
        assertEquals(2, SoundingNoteNames.distinctPitchClassCount(sounding));
    }

    @Test
    void returnsEmptyWhenNothingIsSounding() {
        assertTrue(SoundingNoteNames.format(new boolean[128]).isEmpty());
        assertTrue(SoundingNoteNames.format(null).isEmpty());
    }

    @Test
    void namesTheTwelvePitchClassesWithSharps() {
        // Cifra usa sustenido como padrão; sem tonalidade não há como escolher bemol.
        assertEquals("C", SoundingNoteNames.nameOf(60));
        assertEquals("C#", SoundingNoteNames.nameOf(61));
        assertEquals("B", SoundingNoteNames.nameOf(71));
        assertEquals("C", SoundingNoteNames.nameOf(0));
        assertEquals("A", SoundingNoteNames.nameOf(21));
    }

    private static boolean[] notes(int... midiNotes) {
        boolean[] sounding = new boolean[128];
        for (int note : midiNotes) {
            sounding[note] = true;
        }
        return sounding;
    }
}
