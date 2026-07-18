package br.com.supermidia.playlist;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public record PlaylistItem(Path path) {
    public PlaylistItem {
        Objects.requireNonNull(path, "path");
        path = path.toAbsolutePath().normalize();
    }

    public String displayName() {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    public boolean exists() {
        return Files.isRegularFile(path);
    }

    @Override
    public String toString() {
        return displayName();
    }
}
