package br.com.supermidia.playlist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PlaylistFileService {
    private static final String HEADER = "#EXTM3U";

    public void save(Path destination, List<PlaylistItem> items) throws IOException {
        Path playlistPath = destination.toAbsolutePath().normalize();
        Path playlistDirectory = playlistPath.getParent();
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        for (PlaylistItem item : items) {
            lines.add("#EXTINF:-1," + item.displayName());
            lines.add(pathForPlaylist(playlistDirectory, item.path()));
        }
        Files.write(playlistPath, lines, StandardCharsets.UTF_8);
    }

    public List<PlaylistItem> load(Path source) throws IOException {
        Path playlistPath = source.toAbsolutePath().normalize();
        Path playlistDirectory = playlistPath.getParent();
        List<PlaylistItem> items = new ArrayList<>();

        boolean firstLine = true;
        for (String originalLine : Files.readAllLines(playlistPath, StandardCharsets.UTF_8)) {
            String line = firstLine && originalLine.startsWith("\uFEFF")
                    ? originalLine.substring(1) : originalLine;
            firstLine = false;
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            Path itemPath = Path.of(line);
            if (!itemPath.isAbsolute() && playlistDirectory != null) {
                itemPath = playlistDirectory.resolve(itemPath);
            }
            PlaylistItem item = new PlaylistItem(itemPath);
            if (!items.contains(item)) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private String pathForPlaylist(Path playlistDirectory, Path itemPath) {
        Path normalizedItem = itemPath.toAbsolutePath().normalize();
        if (playlistDirectory != null
                && playlistDirectory.getRoot() != null
                && playlistDirectory.getRoot().equals(normalizedItem.getRoot())) {
            return playlistDirectory.relativize(normalizedItem).toString().replace('\\', '/');
        }
        return normalizedItem.toString();
    }
}
