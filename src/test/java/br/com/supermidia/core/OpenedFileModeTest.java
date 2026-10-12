package br.com.supermidia.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenedFileModeTest {
    @Test
    void readsTheStoredPreference() {
        assertEquals(OpenedFileMode.APPEND, OpenedFileMode.fromPreference("APPEND"));
        assertEquals(OpenedFileMode.REPLACE, OpenedFileMode.fromPreference("REPLACE"));
    }

    @Test
    void fallsBackToReplaceWhenTheStoredValueIsUnusable() {
        assertEquals(OpenedFileMode.REPLACE, OpenedFileMode.fromPreference(null));
        assertEquals(OpenedFileMode.REPLACE, OpenedFileMode.fromPreference(""));
        assertEquals(OpenedFileMode.REPLACE, OpenedFileMode.fromPreference("APPEND_ANTIGO"));
    }

    @Test
    void exposesClearPortugueseLabels() {
        assertEquals("Substituir", OpenedFileMode.REPLACE.label());
        assertEquals("Acrescentar", OpenedFileMode.APPEND.label());
    }
}
