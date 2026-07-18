package br.com.supermidia.playlist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistFileServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesUtf8RelativePathsAndLoadsThemAgain() throws IOException {
        Path musicDirectory = Files.createDirectories(temporaryDirectory.resolve("Músicas"));
        Path first = Files.createFile(musicDirectory.resolve("Canção 01.mid"));
        Path second = Files.createFile(musicDirectory.resolve("Canção 02.mid"));
        Path playlistFile = temporaryDirectory.resolve("repertório.m3u8");
        PlaylistFileService service = new PlaylistFileService();

        service.save(playlistFile, List.of(new PlaylistItem(first), new PlaylistItem(second)));
        List<PlaylistItem> loaded = service.load(playlistFile);

        assertEquals(List.of(new PlaylistItem(first), new PlaylistItem(second)), loaded);
        String contents = Files.readString(playlistFile);
        assertTrue(contents.contains("Músicas/Canção 01.mid"));
    }
}
