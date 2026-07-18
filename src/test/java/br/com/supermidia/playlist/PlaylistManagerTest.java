package br.com.supermidia.playlist;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistManagerTest {
    @Test
    void avoidsDuplicatesAndTracksCurrentAndNextItems() {
        PlaylistManager playlist = new PlaylistManager();
        Path first = Path.of("musicas", "primeira.mid");
        Path second = Path.of("musicas", "segunda.mid");

        playlist.add(first);
        playlist.add(second);
        playlist.add(first);
        playlist.select(0);

        assertEquals(2, playlist.size());
        assertEquals("primeira.mid", playlist.current().orElseThrow().displayName());
        assertEquals("segunda.mid", playlist.next().orElseThrow().displayName());
        assertTrue(playlist.hasNext());
        assertFalse(playlist.hasPrevious());
    }

    @Test
    void keepsTheCurrentSongWhenItemsAreReordered() {
        PlaylistManager playlist = new PlaylistManager();
        playlist.add(Path.of("a.mid"));
        playlist.add(Path.of("b.mid"));
        playlist.add(Path.of("c.mid"));
        playlist.select(1);

        int selection = playlist.moveDown(1);

        assertEquals(2, selection);
        assertEquals(2, playlist.currentIndex());
        assertEquals("b.mid", playlist.current().orElseThrow().displayName());
    }

    @Test
    void selectsANeighborWhenTheCurrentSongIsRemoved() {
        PlaylistManager playlist = new PlaylistManager();
        playlist.add(Path.of("a.mid"));
        playlist.add(Path.of("b.mid"));
        playlist.add(Path.of("c.mid"));
        playlist.select(1);

        boolean currentRemoved = playlist.removeAt(1);

        assertTrue(currentRemoved);
        assertEquals("c.mid", playlist.current().orElseThrow().displayName());
    }

    @Test
    void hasNoNextSongBeforeAPlaylistItemIsSelected() {
        PlaylistManager playlist = new PlaylistManager();
        playlist.add(Path.of("a.mid"));

        assertTrue(playlist.next().isEmpty());
        assertFalse(playlist.hasNext());
    }
}
