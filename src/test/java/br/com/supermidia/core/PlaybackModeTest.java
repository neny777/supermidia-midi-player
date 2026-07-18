package br.com.supermidia.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackModeTest {
    @Test
    void togglesBetweenManualAndAutomaticModes() {
        assertEquals(PlaybackMode.AUTOMATIC, PlaybackMode.MANUAL.toggle());
        assertEquals(PlaybackMode.MANUAL, PlaybackMode.AUTOMATIC.toggle());
    }

    @Test
    void exposesClearPortugueseLabels() {
        assertEquals("Manual", PlaybackMode.MANUAL.label());
        assertEquals("Automático", PlaybackMode.AUTOMATIC.label());
    }
}
